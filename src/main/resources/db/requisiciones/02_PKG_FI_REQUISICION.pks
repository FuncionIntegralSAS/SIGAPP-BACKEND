CREATE OR REPLACE PACKAGE PKG_FI_REQUISICION AS
/*******************************************************************************
  Ciclo de vida de las requisiciones de suministro desde SIGAPP movil.

  COMPILAR EN EL ESQUEMA DE RECURSOS FISICOS SRF (SRFPNACUA), dueno de
  FI_REQUFIRM. Es ahi donde resuelven sin calificar las tablas del ERP que usa
  el paquete (REQUSUMI, MOVIRESU, ASPLMORS, EQUIVALE, PARAOPER, ARTICULO) y el
  paquete PKGFNMOVIRESU. Los demas esquemas lo alcanzan por sinonimo, no con una
  copia propia.

  ESTE MODULO NO CREA LA REQUISICION

  La requisicion nace en el ERP y llega aqui en estado inicial ('in'). SIGAPP
  solo la hace avanzar. Por eso no hay ningun procedimiento de alta, y por eso
  todos identifican el documento por la terna que ya existe:

      REQUSUMI  1 ---- N  MOVIRESU     una fila por articulo, bodega y secuencia
         terna  1 ---- N  ASPLMORS     placas, solo articulos devolutivos
         terna  1 ---- 2  FI_REQUFIRM  firma de salida y firma de recibe

  La terna (MORSTDEM, MORSTIDO, MORSRENU) identifica la requisicion. Dentro de
  ella, una linea se identifica por (MORSARTI, MORSBODE, MORSSECU): la PK de
  MOVIRESU son esas seis columnas y el mismo articulo puede aparecer dos veces
  en distinta bodega o secuencia.

  Flujo de estados de MOVIRESU.MORSESTA:

      in -> aprobar -> ap -> entregar -> en -> [dos firmas] -> registrar -> rg
                                          \                                 + DOCUINVE
                                           -> anular -> ae                  + N MOVIINVE

  LA CABECERA NO SE TOCA. REQUSUMI.RESUESTA se queda en 'ac' siempre. El estado
  de la requisicion es una funcion de sus lineas, no una columna.

  EL ERP HACE CASI TODO EL TRABAJO

  El trigger TRGBUPR_MOVIRESU se dispara en cada UPDATE de una linea y valida
  cantidades, disponibilidad contra ARTIBODE y placas de ASPLMORS, ademas de
  propagar a ARTIBODE, SALDINLA y REQUSUMI.RESUFERE. Este paquete NO replica
  nada de eso: se limita a bloquear, validar la transicion y escribir. Si un
  UPDATE de aqui parece "demasiado simple", es porque el trabajo pesado esta en
  el trigger.

  REGLA DE ORO: el documento de inventario se genera al REGISTRAR, nunca al
  entregar, y solo con las dos firmas presentes. Lo genera el ERP con
  PKGFNMOVIRESU.PROSALIDA, que este paquete invoca. No se reimplementa la
  salida.

  NO SE USA PRO_GENERA_SALIREQU

  Es el otro camino del ERP para lo mismo -la logica de la forma PSSR- y tiene
  un defecto comprobado el 2026-09-21: antes de decidir si inserta el movimiento
  reasigna la cantidad con el saldo por concepto de SALDINLA, de modo que con
  saldo cero genera el DOCUINVE, no genera ningun MOVIINVE y aun asi marca la
  linea 'rg' -su UPDATE esta fuera del IF-. Requisicion registrada, inventario
  intacto y ningun aviso. proSalida trae ese bloque comentado por el propio ERP
  y ata el paso a 'rg' a que haya creado movimiento.

  LA CANTIDAD QUE MUEVE INVENTARIO ES MORSCAEN

  proSalida hace "MOINCANT := rg.MorsCaen" y no lee MORSCARE en ningun momento.
  Por eso una recepcion parcial se aplica BAJANDO MORSCAEN antes de llamar al
  ERP: asi sale del inventario lo que de verdad se recibio y el resto NO SALE de
  la bodega. No es que algo lo devuelva -ARTIBODE.ARBOCAFI no se descuenta al
  entregar sino al registrar, por la cantidad del movimiento-, simplemente nunca
  llega a salir. MORSCARE queda como dato de auditoria, sellado a la par.

  EL USUARIO DE SESION ES OBLIGATORIO

  El parametro CONREBEQ esta activo, de modo que el trigger comprueba que quien
  entrega o registra sea responsable de la bodega, y para saber quien es lee
  pkgfnPersonal.getUsuario(), una variable de paquete de la SESION. SIGAPP se
  conecta con un unico usuario Oracle y un pool de conexiones, asi que esa
  variable no se establece sola y una conexion reciclada conserva el valor
  anterior. Si queda nula, un NVL del trigger la iguala al responsable de la
  bodega y el control se salta EN SILENCIO.

  Por eso pro_set_contexto debe invocarse al principio de toda transaccion de
  escritura, antes que cualquier otro procedimiento de este paquete.

  CONFIGURACION

  Los estados se leen del maestro de parametros (PARAOPER) con FunBuscPara:
  ESTAAPRO, ESTAENTR, ESTAREGI, ESTAACTI. Todos existen ya en el ERP; este
  modulo NO da de alta parametros nuevos. Los dos valores propios -la clase y el
  tipo de la equivalencia que resuelve el tipo de documento de salida- van como
  constantes del BODY, no como filas nuevas en el maestro.
*******************************************************************************/

   -- Tipos de firma que acepta pro_registrar_firma.
   c_firma_salida CONSTANT VARCHAR2(2) := 'SA';   -- posicion 1, quien entrega
   c_firma_recibe CONSTANT VARCHAR2(2) := 'RE';   -- posicion 2, quien recibe

   /*
     Fija la identidad del usuario en la sesion Oracle.

     DEBE llamarse al principio de cada transaccion que vaya a escribir, antes
     de cualquier otro procedimiento de este paquete. No es opcional ni una
     optimizacion: sin esto el control de responsable de bodega del trigger
     TRGBUPR_MOVIRESU no puede identificar a nadie y deja pasar la operacion.

     p_usuario es el LOGIN de la persona (PERSONAL.PERSUSUA), que es lo que
     lleva el token, no PERSONAL.PERSCODI. Es la columna contra la que compara
     pkgPersonal.fun_Usuario, y pasarle el codigo numerico hace fallar TODA
     llamada posterior del paquete con ORA-20008 "Usuario [X] no registrado":
     pkgerror.proSetError valida el usuario de sesion en cada entrada.
   */
   PROCEDURE pro_set_contexto(p_usuario  IN VARCHAR2,
                              p_terminal IN VARCHAR2 DEFAULT 'SIGAPP');

   /*
     Aprueba una linea con la cantidad indicada: 'in' -> 'ap'.

     La cantidad puede ser parcial (menor que la solicitada) o total, pero nunca
     cero ni mayor que MORSCASO. Escribe MORSCAAP, el usuario aprobador y el
     sello de fecha, hora y minuto, que en MOVIRESU van en columnas separadas.

     Aprobar no toca el inventario: solo autoriza.
   */
   PROCEDURE pro_aprobar_linea(p_morstdem IN MOVIRESU.MORSTDEM%TYPE,
                               p_morstido IN MOVIRESU.MORSTIDO%TYPE,
                               p_morsrenu IN MOVIRESU.MORSRENU%TYPE,
                               p_morsarti IN MOVIRESU.MORSARTI%TYPE,
                               p_morsbode IN MOVIRESU.MORSBODE%TYPE,
                               p_morssecu IN MOVIRESU.MORSSECU%TYPE,
                               p_cantidad IN MOVIRESU.MORSCAAP%TYPE,
                               p_usuario  IN VARCHAR2);

   /*
     Entrega una linea con la cantidad indicada: 'ap' -> 'en'.

     La cantidad acumulada entregada no puede superar la aprobada. Escribe
     MORSCAEN y NO toca MORSCARE, que se sella al registrar.

     La primera linea de la requisicion que llega a 'en' crea ademas las dos
     filas vacias de FI_REQUFIRM: a partir de ahi se pueden pedir las firmas.

     Para articulos devolutivos el trigger del ERP exige que ASPLMORS tenga
     tantas placas como unidades se entregan, asi que pro_asignar_placa debe
     haberse llamado antes.
   */
   PROCEDURE pro_entregar_linea(p_morstdem IN MOVIRESU.MORSTDEM%TYPE,
                                p_morstido IN MOVIRESU.MORSTIDO%TYPE,
                                p_morsrenu IN MOVIRESU.MORSRENU%TYPE,
                                p_morsarti IN MOVIRESU.MORSARTI%TYPE,
                                p_morsbode IN MOVIRESU.MORSBODE%TYPE,
                                p_morssecu IN MOVIRESU.MORSSECU%TYPE,
                                p_cantidad IN MOVIRESU.MORSCAEN%TYPE,
                                p_usuario  IN VARCHAR2);

   /*
     Asigna una placa concreta a una linea (ASPLMORS). Solo aplica a articulos
     devolutivos y debe llamarse antes de entregar. Repetir la misma placa no es
     error: se ignora.
   */
   PROCEDURE pro_asignar_placa(p_morstdem IN MOVIRESU.MORSTDEM%TYPE,
                               p_morstido IN MOVIRESU.MORSTIDO%TYPE,
                               p_morsrenu IN MOVIRESU.MORSRENU%TYPE,
                               p_morsarti IN MOVIRESU.MORSARTI%TYPE,
                               p_morssecu IN MOVIRESU.MORSSECU%TYPE,
                               p_placa    IN ASPLMORS.ASPLPLAC%TYPE);

   /*
     Suelta una placa que no se recibe, para el caso de recepcion parcial de un
     devolutivo. Sin esto el ERP falla al sellar MORSCARE porque el trigger
     compara el numero de placas con la cantidad recibida.
   */
   PROCEDURE pro_soltar_placa(p_morstdem IN MOVIRESU.MORSTDEM%TYPE,
                              p_morstido IN MOVIRESU.MORSTIDO%TYPE,
                              p_morsrenu IN MOVIRESU.MORSRENU%TYPE,
                              p_morsarti IN MOVIRESU.MORSARTI%TYPE,
                              p_morssecu IN MOVIRESU.MORSSECU%TYPE,
                              p_placa    IN ASPLMORS.ASPLPLAC%TYPE);

   /*
     Anula una linea al entregar: 'ap' -> 'ae'.

     Solo se puede anular lo que aun no se ha entregado. Escribe MORSCAAN con la
     cantidad aprobada. Una linea anulada no llega al inventario y el registro
     la ignora.
   */
   PROCEDURE pro_anular_linea(p_morstdem IN MOVIRESU.MORSTDEM%TYPE,
                              p_morstido IN MOVIRESU.MORSTIDO%TYPE,
                              p_morsrenu IN MOVIRESU.MORSRENU%TYPE,
                              p_morsarti IN MOVIRESU.MORSARTI%TYPE,
                              p_morsbode IN MOVIRESU.MORSBODE%TYPE,
                              p_morssecu IN MOVIRESU.MORSSECU%TYPE,
                              p_usuario  IN VARCHAR2);

   /*
     Registra una de las dos firmas. p_tipo_firma es c_firma_salida o
     c_firma_recibe; no hay orden entre ellas y ninguna mueve el estado.

     Guarda el instante exacto en REFIFEFI. La PK de FI_REQUFIRM impide una
     segunda firma sobre la misma posicion, y el procedimiento la rechaza antes
     con un mensaje legible.

     La firma llega como BLOB con los bytes de la imagen. El backend ya valido
     que sea PNG o JPEG y que no exceda el tamano maximo.
   */
   PROCEDURE pro_registrar_firma(p_morstdem   IN MOVIRESU.MORSTDEM%TYPE,
                                 p_morstido   IN MOVIRESU.MORSTIDO%TYPE,
                                 p_morsrenu   IN MOVIRESU.MORSRENU%TYPE,
                                 p_tipo_firma IN VARCHAR2,
                                 p_persona    IN FI_REQUFIRM.REFICODI%TYPE,
                                 p_firma      IN BLOB,
                                 p_usuario    IN VARCHAR2);

   /*
     Sella la cantidad recibida de una linea. Debe ser mayor que cero y no
     superar la entregada.

     Se llama solo para las lineas que se reciben parciales; las que se reciben
     completas las resuelve pro_generar_salida por defecto. Si la linea es
     devolutiva hay que haber soltado antes las placas que no se reciben.
   */
   PROCEDURE pro_recibir_linea(p_morstdem IN MOVIRESU.MORSTDEM%TYPE,
                               p_morstido IN MOVIRESU.MORSTIDO%TYPE,
                               p_morsrenu IN MOVIRESU.MORSRENU%TYPE,
                               p_morsarti IN MOVIRESU.MORSARTI%TYPE,
                               p_morsbode IN MOVIRESU.MORSBODE%TYPE,
                               p_morssecu IN MOVIRESU.MORSSECU%TYPE,
                               p_cantidad IN MOVIRESU.MORSCARE%TYPE,
                               p_usuario  IN VARCHAR2);

   /*
     Cierra el ciclo: genera la salida de inventario y deja las lineas en 'rg'.

     Exige las dos firmas y que todas las lineas esten en 'en' o 'ae', con al
     menos una en 'en'. Ajusta las cantidades de la recepcion parcial, resuelve
     el tipo de documento de salida por equivalencia y llama a
     PKGFNMOVIRESU.PROSALIDA, que crea una cabecera en DOCUINVE, una linea por
     articulo en MOVIINVE y la contabilidad asociada.

     Comprueba despues que el documento no haya salido vacio. OJO: proSalida
     hace COMMIT propio cuando genera movimientos, asi que a partir de ese punto
     no hay vuelta atras; toda validacion de este procedimiento va antes.

     Es reentrante: si la requisicion ya esta registrada devuelve el aviso y no
     vuelve a llamar al ERP. Esa comprobacion es lo que impide que un reintento
     genere un segundo documento de salida, que seria irreversible.

     p_tidosali devuelve el tipo de documento de salida que se uso y p_mensaje
     el texto que el ERP produce durante el proceso.
   */
   PROCEDURE pro_generar_salida(p_morstdem IN  MOVIRESU.MORSTDEM%TYPE,
                                p_morstido IN  MOVIRESU.MORSTIDO%TYPE,
                                p_morsrenu IN  MOVIRESU.MORSRENU%TYPE,
                                p_usuario  IN  VARCHAR2,
                                p_tidosali OUT VARCHAR2,
                                p_mensaje  OUT VARCHAR2);

END PKG_FI_REQUISICION;
/
