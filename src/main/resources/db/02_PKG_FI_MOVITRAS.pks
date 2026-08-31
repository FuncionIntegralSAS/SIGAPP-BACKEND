CREATE OR REPLACE PACKAGE PKG_FI_MOVITRAS AS
/*******************************************************************************
  Ciclo de vida de los traspasos de activos SIGAPP (FI_MOVITRAS).

  COMPILAR EN EL ESQUEMA DE RECURSOS FISICOS SRF (SRFPNACUA), dueno de
  FI_MOVITRAS. Es ahi donde resuelven sin calificar las tablas del ERP que usa el
  paquete (REQUSUMI, ACTIFIJO, PARAOPER, TIMOOPER, TIPODOCU, DOCUINVE, MOVIINVE,
  ARTICULO, PERSCECO, TIDOEMNU). Los demas esquemas lo alcanzan por sinonimo, no
  con una copia propia.

  Reemplaza a PKGFISIGAPPTRASPASO, que operaba sobre FI_SIGATRAS.

  Todo traspaso cuelga de un documento REQUSUMI: pro_crear_solicitud inserta
  primero la requisicion y solo despues el registro en FI_MOVITRAS, de modo que
  la FK FI_MOVITRAS_REQUSUMI_FK siempre se cumple.

  Flujo de estados:

      crear (pe) -> aprobar (ap) -> firma fuente (af) -> firma destino (ad)
                        \             + DOCUINVE                |
                         \            + MOVIINVE                v
                          -> rechazar (na)                recibir (re)

  Los valores de configuracion (tipo de movimiento, estados, centro de
  informacion, concepto de movimiento y bodegas logicas) se leen del maestro de
  parametros del sistema (PARAOPER) con FunBuscPara. Las claves siguen la
  convencion del ERP: maximo 10 bytes, sin separadores (TIMOTRIN, ESTATRAS...).
  Ver las constantes del BODY y el script 05_PARAOPER.sql, que las da de alta.

  El tipo de documento NO se parametriza: se deriva del tipo de movimiento
  (TIMOOPER -> TIPODOCU) en pro_crear_solicitud.
*******************************************************************************/

   /*
     Crea la requisicion en REQUSUMI y el traspaso en FI_MOVITRAS.

     El numero del documento lo asigna el trigger TRGBINR_REQUSUMI cuando el tipo
     de documento esta configurado como automatico; se devuelve al llamador junto
     con el tipo, porque el backend los necesita para volver a ubicar el
     documento.

     p_tipo_movi es el tipo de movimiento y es opcional: si llega NULL se resuelve
     con FunBuscPara('TIMOTRIN'). Sea cual sea su origen, se valida que tenga
     habilitada la operacion TRASPASO en TIMOOPER y de ahi se deriva el tipo de
     documento (TIPODOCU.TIDOTIDO), que se devuelve en p_motrdrtd.
   */
   PROCEDURE pro_crear_solicitud(p_motrdrem  IN  FI_MOVITRAS.MOTRDREM%TYPE,
                                 p_motrpefu  IN  FI_MOVITRAS.MOTRPEFU%TYPE,
                                 p_motrpede  IN  FI_MOVITRAS.MOTRPEDE%TYPE,
                                 p_motrarti  IN  FI_MOVITRAS.MOTRARTI%TYPE,
                                 p_motrplac  IN  FI_MOVITRAS.MOTRPLAC%TYPE,
                                 p_motrobse  IN  FI_MOVITRAS.MOTROBSE%TYPE,
                                 p_tipo_movi IN  VARCHAR2,
                                 p_motruscr  IN  FI_MOVITRAS.MOTRUSCR%TYPE,
                                 p_motridmt  OUT FI_MOVITRAS.MOTRIDMT%TYPE,
                                 p_motrdrtd  OUT FI_MOVITRAS.MOTRDRTD%TYPE,
                                 p_motrdrnd  OUT FI_MOVITRAS.MOTRDRND%TYPE);

   /*
     Aprueba ('ap') o rechaza ('na') un traspaso que este en estado 'pe'.
     Al rechazar exige motivo. Sincroniza el estado del documento REQUSUMI.

     Al APROBAR, ademas, materializa el traspaso en el inventario del ERP:
     inserta la cabecera en DOCUINVE y su unica linea de detalle en MOVIINVE.
     El numero del documento lo entrega el numerador del tipo de documento
     (TIDOEMNU -> pkgDocu_General.funProxi_Nume), no un trigger.

     Al RECHAZAR no se inserta nada en DOCUINVE ni en MOVIINVE: el documento se
     anula y el tramite termina ahi.

     Todo ocurre en la transaccion del llamador: o queda el tramite aprobado con
     su documento de inventario, o no queda nada.
   */
   PROCEDURE pro_aprobar_rechazar(p_motridmt   IN FI_MOVITRAS.MOTRIDMT%TYPE,
                                  p_estado_nvo IN FI_MOVITRAS.MOTRESTA%TYPE,
                                  p_observa    IN FI_MOVITRAS.MOTRMORE%TYPE,
                                  p_usuario    IN FI_MOVITRAS.MOTRUSAP%TYPE);

   /*
     Registra la firma de aceptacion y sella la fecha correspondiente.

     p_tipo_firma: 'FU' persona fuente (MOTRFIFU / MOTRFAFU -> estado 'af')
                   'DE' persona destino (MOTRFIDE / MOTRFADE -> estado 'ad')

     p_firma son los BYTES de la imagen (BLOB), no su base64. El backend recibe
     el base64 del front, lo valida (prefijo data:, tamano, cabecera PNG/JPEG) y
     lo decodifica antes de llamar aqui. El paquete solo comprueba que no llegue
     vacio: validar el formato de la imagen en PL/SQL no aporta nada.
   */
   PROCEDURE pro_registrar_firma(p_motridmt   IN FI_MOVITRAS.MOTRIDMT%TYPE,
                                 p_tipo_firma IN VARCHAR2,
                                 p_firma      IN BLOB,
                                 p_usuario    IN VARCHAR2);

   /*
     Sella MOTRFERE y deja el traspaso en 're'. Cierra el ciclo.
     Exige que ambas partes hayan firmado.
   */
   PROCEDURE pro_recibir(p_motridmt IN FI_MOVITRAS.MOTRIDMT%TYPE,
                         p_usuario  IN VARCHAR2);

END PKG_FI_MOVITRAS;
/
