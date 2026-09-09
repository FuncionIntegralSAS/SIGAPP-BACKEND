CREATE OR REPLACE PACKAGE PKG_FI_MOVITRAS AS
/*******************************************************************************
  Ciclo de vida de los traspasos de activos SIGAPP (FI_MOVITRAS / FI_MOTRFIRM).

  COMPILAR EN EL ESQUEMA DE RECURSOS FISICOS SRF (SRFPNACUA), dueno de las dos
  tablas. Es ahi donde resuelven sin calificar las tablas del ERP que usa el
  paquete (REQUSUMI, ACTIFIJO, PARAOPER, TIMOOPER, TIPODOCU, DOCUINVE, MOVIINVE,
  ARTICULO, PERSCECO, TIDOEMNU). Los demas esquemas lo alcanzan por sinonimo, no
  con una copia propia.

  UN TRAMITE, VARIOS ARTICULOS

  Un traspaso mueve N articulos entre las mismas dos personas, con un solo
  documento REQUSUMI. FI_MOVITRAS guarda una fila por articulo y MOTRNUTR -el
  numero de tramite, tomado de FI_TRASPASO_SEQ- es lo que las agrupa.

      REQUSUMI  1 ---- N  FI_MOVITRAS      una linea por activo
      MOTRNUTR  1 ---- 2  FI_MOTRFIRM      firma fuente y firma destino

  TODOS los procedimientos publicos identifican el tramite por MOTRNUTR, nunca
  por MOTRIDMT, que es solo la clave tecnica de la linea. Y todos los UPDATE
  filtran por MOTRNUTR, de modo que el estado y los datos comunes se muevan en
  bloque: la coherencia entre las N lineas depende de eso.

  ALTA EN DOS TIEMPOS

  El documento necesita datos que salen del activo (RESUCEIN), asi que no se
  puede crear antes de conocer al menos un articulo. Por eso el alta va en dos
  procedimientos y no en uno con lista:

      pro_crear_solicitud   crea el documento, la primera linea y las dos filas
                            de firma vacias. Devuelve el numero de tramite.
      pro_agregar_articulo  anade cada articulo restante a ese tramite.

  El llamador ejecuta las N llamadas dentro de una sola transaccion: si falla el
  tercer articulo no queda nada, ni siquiera el documento.

  Flujo de estados:

      crear (pe) -> aprobar (ap) -> [firma fuente y firma destino] -> recibir (re)
                       \                                                 + DOCUINVE
                        -> rechazar (na)                                 + N lineas de MOVIINVE

  Las firmas NO mueven el estado. Un tramite en 'ap' puede tener cero, una o dos
  firmas, y eso se consulta en FI_MOTRFIRM (MOFIFEFI nulo = aun no firma). No hay
  orden entre ellas: fuente y destino firman cuando quieran. pro_recibir exige
  que las dos esten registradas. Por eso desaparecieron los estados 'af' y 'ad':
  el avance de firma son dos booleanos independientes y una sola columna de
  estado no puede representarlos.

  REGLA DE ORO: el documento de inventario se genera al RECIBIR, nunca al
  aprobar. Aprobar autoriza el tramite; recibir es lo que mueve el activo, y solo
  se puede recibir con las dos firmas.

  Los valores de configuracion (tipo de movimiento, estados, centro de
  informacion, concepto de movimiento y bodegas logicas) se leen del maestro de
  parametros del sistema (PARAOPER) con FunBuscPara. Las claves siguen la
  convencion del ERP: maximo 10 bytes, sin separadores (TIMOTRIN, ESTATRAS...).
  Ver las constantes del BODY y el script 06_PARAOPER.sql, que las da de alta.

  El tipo de documento NO se parametriza: se deriva del tipo de movimiento
  (TIMOOPER -> TIPODOCU) en pro_crear_solicitud.
*******************************************************************************/

   /*
     Abre un traspaso con su PRIMER articulo: crea la requisicion en REQUSUMI, la
     primera linea de FI_MOVITRAS y las dos filas de FI_MOTRFIRM con la firma
     vacia. Los articulos restantes entran con pro_agregar_articulo.

     El numero del documento lo asigna el trigger TRGBINR_REQUSUMI cuando el tipo
     de documento esta configurado como automatico; se devuelve al llamador junto
     con el tipo, porque el backend los necesita para volver a ubicar el
     documento.

     p_tipo_movi es el tipo de movimiento y es opcional: si llega NULL se resuelve
     con FunBuscPara('TIMOTRIN'). Sea cual sea su origen, se valida que tenga
     habilitada la operacion TRASPASO en TIMOOPER y de ahi se deriva el tipo de
     documento (TIPODOCU.TIDOTIDO), que se devuelve en p_motrdrtd.

     p_motrplac admite NULL: se guarda como '.' igual que ACTIFIJO, para que la
     unicidad de la linea funcione con los elementos sin placa.

     p_motrnutr es el NUMERO DE TRAMITE. Es el identificador con el que se opera
     el traspaso de aqui en adelante.
   */
   PROCEDURE pro_crear_solicitud(p_motrdrem  IN  FI_MOVITRAS.MOTRDREM%TYPE,
                                 p_motrpefu  IN  FI_MOVITRAS.MOTRPEFU%TYPE,
                                 p_motrpede  IN  FI_MOVITRAS.MOTRPEDE%TYPE,
                                 p_motrarti  IN  FI_MOVITRAS.MOTRARTI%TYPE,
                                 p_motrplac  IN  FI_MOVITRAS.MOTRPLAC%TYPE,
                                 p_motrobse  IN  FI_MOVITRAS.MOTROBSE%TYPE,
                                 p_tipo_movi IN  VARCHAR2,
                                 p_motruscr  IN  FI_MOVITRAS.MOTRUSCR%TYPE,
                                 p_motrnutr  OUT FI_MOVITRAS.MOTRNUTR%TYPE,
                                 p_motrdrtd  OUT FI_MOVITRAS.MOTRDRTD%TYPE,
                                 p_motrdrnd  OUT FI_MOVITRAS.MOTRDRND%TYPE);

   /*
     Anade un articulo a un tramite ya abierto, copiando de la linea existente
     todos los datos comunes (documento, personas, estado, observacion, fechas).

     Exige que el tramite siga en 'pe'. El aprobador autorizo un conjunto
     concreto de articulos: colar uno mas despues saltaria esa autorizacion.

     Rechaza el articulo si su centro de informacion o su tercero no coinciden
     con los del tramite. REQUSUMI.RESUCEIN y DOCUINVE.DOINTERC son datos de
     cabecera y salen del activo, asi que con varios articulos tienen que ser el
     mismo; tomar el del primero mandaria el documento a un centro equivocado sin
     que nadie se entere.

     Rechaza tambien el articulo repetido con un mensaje propio, en vez de dejar
     que reviente la constraint FI_MOVITRAS_UK_LINE con un ORA-00001 ilegible.
   */
   PROCEDURE pro_agregar_articulo(p_motrnutr IN FI_MOVITRAS.MOTRNUTR%TYPE,
                                  p_motrarti IN FI_MOVITRAS.MOTRARTI%TYPE,
                                  p_motrplac IN FI_MOVITRAS.MOTRPLAC%TYPE,
                                  p_usuario  IN VARCHAR2);

   /*
     Aprueba ('ap') o rechaza ('na') un traspaso que este en estado 'pe'.
     Al rechazar exige motivo. Sincroniza el estado del documento REQUSUMI.
     El cambio de estado se aplica a TODAS las lineas del tramite.

     NO toca el inventario. Aprobar es autorizar el tramite; el documento de
     inventario lo genera pro_recibir, cuando las dos partes ya firmaron. Un
     traspaso aprobado que nadie llega a firmar no deja rastro en DOCUINVE ni en
     MOVIINVE.

     Al RECHAZAR el documento REQUSUMI se anula y el tramite termina ahi.
   */
   PROCEDURE pro_aprobar_rechazar(p_motrnutr   IN FI_MOVITRAS.MOTRNUTR%TYPE,
                                  p_estado_nvo IN FI_MOVITRAS.MOTRESTA%TYPE,
                                  p_observa    IN FI_MOVITRAS.MOTRMORE%TYPE,
                                  p_usuario    IN FI_MOVITRAS.MOTRUSAP%TYPE);

   /*
     Registra la firma de aceptacion en FI_MOTRFIRM y sella su fecha.

     p_tipo_firma: 'FU' persona fuente  -> MOFIPOSI = 1 (entrega)
                   'DE' persona destino -> MOFIPOSI = 2 (recibe)

     NO hay orden: cualquiera de las dos puede registrarse primero. Y NO se toca
     MOTRESTA: el tramite se queda en 'ap' mientras se firma. Lo unico que se
     exige es que el tramite este aprobado y que esa posicion no este ya firmada.

     p_firma son los BYTES de la imagen (BLOB), no su base64. El backend recibe
     el base64 del front, lo valida (prefijo data:, tamano, cabecera PNG/JPEG) y
     lo decodifica antes de llamar aqui. El paquete solo comprueba que no llegue
     vacio: validar el formato de la imagen en PL/SQL no aporta nada.
   */
   PROCEDURE pro_registrar_firma(p_motrnutr   IN FI_MOVITRAS.MOTRNUTR%TYPE,
                                 p_tipo_firma IN VARCHAR2,
                                 p_firma      IN BLOB,
                                 p_usuario    IN VARCHAR2);

   /*
     Cierra el ciclo: sella MOTRFERE, deja el traspaso en 're' y GENERA EL
     DOCUMENTO DE INVENTARIO.

     Exige que las dos posiciones de FI_MOTRFIRM esten firmadas, sin importar en
     que orden llegaron.

     Es aqui, y no al aprobar, donde se inserta UNA cabecera en DOCUINVE y UNA
     LINEA POR ARTICULO en MOVIINVE, con MOINSECU de 1 a N. El numero del
     documento lo entrega el numerador del tipo de documento
     (TIDOEMNU -> pkgDocu_General.funProxi_Nume), no un trigger.

     La razon es de negocio: el movimiento de inventario dice que el elemento
     cambio de manos, y eso solo es cierto cuando las dos partes firmaron. Todo
     ocurre en la transaccion del llamador: o queda el traspaso recibido con su
     documento completo, o no queda nada.
   */
   PROCEDURE pro_recibir(p_motrnutr IN FI_MOVITRAS.MOTRNUTR%TYPE,
                         p_usuario  IN VARCHAR2);

END PKG_FI_MOVITRAS;
/
