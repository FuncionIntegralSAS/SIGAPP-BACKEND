CREATE OR REPLACE PACKAGE PKG_FI_REQUISICION AS
/*******************************************************************************
  Ciclo de vida de las requisiciones de suministro desde SIGAPP movil.

  COMPILAR EN EL ESQUEMA DE RECURSOS FISICOS SRF (SRFPNACUA), dueno de
  FI_REQUFIRM. Es ahi donde resuelven sin calificar las tablas del ERP que usa
  el paquete (REQUSUMI, MOVIRESU, ASPLMORS, EQUIVALE, PARAOPER, ARTICULO) y el
  procedimiento PRO_GENERA_SALIREQU. Los demas esquemas lo alcanzan por
  sinonimo, no con una copia propia.

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
  PRO_GENERA_SALIREQU -la logica de la forma PSSR-, que este paquete invoca. No
  se reimplementa la salida.

  MORSCARE ES UNA ENTRADA DEL REGISTRO, NO UN RESULTADO

  PRO_GENERA_SALIREQU lee MORSCARE y nunca la escribe: es la cantidad que lleva
  a MOVIINVE, con la que calcula el costo, y si vale cero NO genera movimiento
  para esa linea. Por eso pro_generar_salida sella la cantidad recibida ANTES de
  llamarlo. Sin ese paso la linea pasaria a 'rg' sin mover inventario y sin que
  nadie avise: en los datos historicos hay decenas de miles de lineas asi.

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

     p_usuario es el codigo de persona (PERSONAL.PERSCODI) que sale del token.
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
     menos una en 'en'. Completa con MORSCAEN las lineas cuya cantidad recibida
     no se haya sellado, resuelve el tipo de documento de salida por
     equivalencia y llama a PRO_GENERA_SALIREQU, que crea una cabecera en
     DOCUINVE y una linea por articulo en MOVIINVE.

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
