--------------------------------------------------------------------------------
-- Registrar una requisicion a mano, con diagnostico
--
-- Ejecutar COMO SCRIPT (F5 en SQL Developer), no sentencia por sentencia y
-- NO en ventanas distintas: todo tiene que correr en la MISMA sesion.
--
-- Por que importa la sesion: pro_set_contexto deja el usuario en una variable
-- de PAQUETE de la sesion (pkgfnPersonal). En otra conexion vale nulo.
--
--------------------------------------------------------------------------------
-- DONDE ESTA EL LOG  (verificado contra la BD de desarrollo el 2026-09-21)
--
-- HAY DOS TABLAS ERROINTE Y EL SINONIMO APUNTA A LA QUE NO ES. Esta es la
-- causa habitual de "ejecute el procedimiento y no veo nada en ERROINTE":
--
--   SRFPNACUA.ERROINTE   <- AQUI escribe este modulo. ~640.000 filas.
--   FPLPNACUA.ERROINTE   <- otra cosa (comercial). ~750 filas.
--   ARQGUIPNACUA.ERROINTE es un SINONIMO y apunta a FPLPNACUA, la equivocada.
--
-- El paquete resuelve 'pkgerrointe' a SRFPNACUA.PKGERROINTE porque se compila
-- en ese esquema y el objeto propio gana sobre el sinonimo. Si consultas
-- conectado con otro usuario, o a traves del sinonimo, estas mirando otra tabla.
-- Por eso las consultas de este script CALIFICAN el esquema: srfpnacua.errointe.
--
-- SRFPNACUA.ERROINTE NO TIENE COLUMNA DE FECHA. Solo ERININTE, ERINUSUA y
-- ERINERRO (VARCHAR2(2000)). La de FPLPNACUA si tiene ERINFESI, de ahi la
-- confusion. Por eso pro_log antepone SYSTIMESTAMP al mensaje COMO TEXTO, y por
-- eso aqui no hay ORDER BY cronologico: no existe por donde ordenar. Ordenar
-- por ROWID tampoco sirve, devuelve filas de hace meses.
--
-- Se filtra por ERININTE = 'FI_REQUFIRM', que es como este modulo marca sus
-- filas (traspasos usa 'FI_MOVITRAS'), y ERINUSUA es el usuario ORACLE
-- (SRFPNACUA), nunca el codigo de persona.
--
-- pkgerrointe.procrear SI es autonomo y hace COMMIT propio: el log sobrevive
-- al rollback de la operacion. Comprobado en el fuente de los dos esquemas.
--------------------------------------------------------------------------------
--
-- Ajustar las tres variables de sustitucion del principio y nada mas.
--------------------------------------------------------------------------------

SET SERVEROUTPUT ON SIZE UNLIMITED
SET LINESIZE 200
SET PAGESIZE 100

DEFINE v_empresa = '01'
DEFINE v_tipo    = '2601'
DEFINE v_numero  = 202500676
DEFINE v_usuario = 'agomez'


PROMPT
PROMPT ============================================================
PROMPT PASO 0 - Estado del entorno
PROMPT ============================================================

-- Quien soy realmente. El log se marca con este usuario (USER), no con
-- '&v_usuario': ese es el codigo de persona del ERP, no el usuario Oracle.
SELECT USER AS usuario_oracle, SYS_CONTEXT('USERENV','SESSION_USER') AS session_user FROM dual;

-- El paquete tiene que estar VALID y ser el de la ultima version del repo.
SELECT object_name, object_type, status, last_ddl_time
  FROM user_objects
 WHERE object_name IN ('PKG_FI_REQUISICION', 'FI_REQUFIRM')
 ORDER BY object_name, object_type;

-- Las dos tablas de log y el sinonimo que enganha. La de este modulo es la de
-- SRFPNACUA; si el sinonimo apunta a FPLPNACUA, consultar 'errointe' a secas
-- desde otro esquema no muestra nada de SIGAPP.
SELECT owner, table_name FROM all_tables WHERE table_name = 'ERROINTE' ORDER BY owner;

SELECT owner, synonym_name, table_owner, table_name
  FROM all_synonyms WHERE synonym_name = 'ERROINTE';

-- Es autonoma la escritura del log? (verificado: SI, en los dos esquemas)
--   Sin filas  -> NO lo es -> sin COMMIT el log no sobrevive al rollback.
--   Con filas  -> SI lo es -> el log persiste aunque revierta la operacion.
SELECT owner, COUNT(*) AS tiene_pragma_autonomo
  FROM all_source
 WHERE name = 'PKGERROINTE'
   AND type = 'PACKAGE BODY'
   AND UPPER(text) LIKE '%AUTONOMOUS_TRANSACTION%'
 GROUP BY owner;


PROMPT
PROMPT ============================================================
PROMPT PASO 1 - Precondiciones de la requisicion
PROMPT ============================================================

-- La cabecera existe?
SELECT resutdem, resutido, resunume, resuesta, resufere
  FROM requsumi
 WHERE resutdem = '&v_empresa'
   AND resutido = '&v_tipo'
   AND resunume = &v_numero;

-- Estado de las lineas. Para registrar hacen falta lineas en 'en' y NINGUNA
-- fuera de 'en'/'ae'. Si ya salen todas en 'rg', el procedimiento devuelve
-- "ya fue registrada" y NO deja log: es la reentrada, no un fallo.
SELECT morsesta, COUNT(*) AS lineas, SUM(NVL(morscaen,0)) AS entregada,
       SUM(NVL(morscare,0)) AS recibida
  FROM moviresu
 WHERE morstdem = '&v_empresa'
   AND morstido = '&v_tipo'
   AND morsrenu = &v_numero
 GROUP BY morsesta
 ORDER BY morsesta;

-- Las dos firmas. Tienen que ser 2 filas y las dos con REFIFEFI no nulo.
SELECT refiposi,
       refiposi || CASE refiposi WHEN 1 THEN ' salida/entrega' ELSE ' recibe' END AS posicion,
       reficodi, refifefi,
       CASE WHEN refifefi IS NULL THEN 'SIN FIRMAR' ELSE 'firmada' END AS estado
  FROM fi_requfirm
 WHERE refiempr = '&v_empresa'
   AND refitido = '&v_tipo'
   AND refinume = &v_numero
 ORDER BY refiposi;

-- La equivalencia que resuelve el tipo del documento de salida. Sin esta fila
-- el tipo no es gestionable y el registro falla.
SELECT equicleq, equitipo, equicodi, equicoeq, equiesta
  FROM equivale
 WHERE equicleq = 'COBO'
   AND equitipo = 'REQU'
   AND equicodi = '&v_tipo';


PROMPT
PROMPT ============================================================
PROMPT PASO 2 - Prueba aislada del log
PROMPT ============================================================
--
-- pro_log se traga cualquier error con "when others then null", a proposito:
-- el log no puede tumbar el tramite ni tapar el error real. El efecto lateral
-- es que un fallo de permisos o un mensaje demasiado largo para la columna
-- desaparece sin rastro. Aqui se llama a procrear SIN esa red, de modo que si
-- algo falla se ve el ORA- de verdad.
--
DECLARE
   sb_marca VARCHAR2(200);
BEGIN
   sb_marca := 'DIAGNOSTICO ' || TO_CHAR(SYSDATE, 'YYYYMMDD-HH24MISS') ||
               ' requisicion &v_empresa-&v_tipo-&v_numero';

   pkgerrointe.procrear('FI_REQUFIRM', USER, SYSTIMESTAMP || ' ' || sb_marca);

   DBMS_OUTPUT.PUT_LINE('OK - el log acepta escrituras. Marca: ' || sb_marca);
EXCEPTION
   WHEN OTHERS THEN
      DBMS_OUTPUT.PUT_LINE('FALLA LA ESCRITURA DEL LOG -> ' || SQLERRM);
      DBMS_OUTPUT.PUT_LINE('Esta es la causa de que no veas nada en ERROINTE.');
END;
/


PROMPT
PROMPT ============================================================
PROMPT PASO 3 - Registrar la requisicion
PROMPT ============================================================

DECLARE
   -- p_tidosali es VARCHAR2 y NO es un codigo de error: es el TIPO DE
   -- DOCUMENTO de la salida que genera el ERP (p.ej. '261'). Declararlo NUMBER
   -- funciona mientras el valor sea numerico y revienta con ORA-06502 en
   -- cuanto no lo sea.
   v_tidosali VARCHAR2(4);
   v_mensaje  VARCHAR2(4000);
BEGIN
   -----------------------------------------------------------------------------
   -- OBLIGATORIO Y PRIMERO.
   --
   -- El parametro CONREBEQ esta activo, asi que el trigger TRGBUPR_MOVIRESU
   -- exige que quien registra sea responsable de la bodega, y lo comprueba
   -- leyendo pkgfnPersonal.getUsuario(), una variable de paquete de la SESION.
   -- Si queda nula, un NVL del trigger la iguala al responsable de la bodega y
   -- EL CONTROL SE SALTA EN SILENCIO: la prueba pasaria por un camino que no es
   -- el de produccion.
   -----------------------------------------------------------------------------
   pkg_fi_requisicion.pro_set_contexto(p_usuario => '&v_usuario');

   DBMS_OUTPUT.PUT_LINE('Contexto establecido para &v_usuario');

   pkg_fi_requisicion.pro_generar_salida(
      p_morstdem => '&v_empresa',
      p_morstido => '&v_tipo',
      p_morsrenu => &v_numero,
      p_usuario  => '&v_usuario',
      p_tidosali => v_tidosali,
      p_mensaje  => v_mensaje);

   DBMS_OUTPUT.PUT_LINE('---------------------------------------------');
   IF (v_tidosali IS NULL) THEN
      -- Camino de reentrada: ya estaba en 'rg'. No genera documento nuevo
      -- -que seria irreversible- y tampoco deja log.
      DBMS_OUTPUT.PUT_LINE('SIN EFECTO (reentrada): ' || v_mensaje);
   ELSE
      DBMS_OUTPUT.PUT_LINE('REGISTRADA. Tipo de salida: ' || v_tidosali);
      DBMS_OUTPUT.PUT_LINE('Mensaje del ERP: ' || v_mensaje);
   END IF;
   DBMS_OUTPUT.PUT_LINE('---------------------------------------------');

EXCEPTION
   WHEN OTHERS THEN
      -- Se imprime y NO se hace rollback aqui: el paso 4 necesita leer el log,
      -- que si procrear no es autonomo muere con el rollback.
      DBMS_OUTPUT.PUT_LINE('---------------------------------------------');
      DBMS_OUTPUT.PUT_LINE('ERROR ' || SQLCODE || ': ' || SQLERRM);
      DBMS_OUTPUT.PUT_LINE(DBMS_UTILITY.FORMAT_ERROR_BACKTRACE);
      DBMS_OUTPUT.PUT_LINE('---------------------------------------------');
END;
/


PROMPT
PROMPT ============================================================
PROMPT PASO 4 - El log, desde esta misma sesion
PROMPT ============================================================
--
-- Esquema CALIFICADO y filtro por ERININTE: ver la cabecera del archivo.
-- Sin ORDER BY porque esta tabla no tiene columna de fecha; el instante va
-- dentro del texto, al principio de ERINERRO.
--
COLUMN erininte FORMAT A14
COLUMN erinusua FORMAT A12
COLUMN erinerro FORMAT A160
SELECT erininte, erinusua, erinerro
  FROM srfpnacua.errointe
 WHERE erininte = 'FI_REQUFIRM'
   AND erinerro LIKE '%&v_empresa-&v_tipo-&v_numero%';

-- Y todo el log del modulo, por si el mensaje no lleva la terna (los errores
-- de linea solo llevan $$PLSQL_LINE y SQLERRM).
SELECT erininte, erinusua, erinerro
  FROM srfpnacua.errointe
 WHERE erininte IN ('FI_REQUFIRM', 'FI_MOVITRAS');

-- Como quedaron las lineas despues de la llamada.
SELECT morsesta, COUNT(*) AS lineas, SUM(NVL(morscare,0)) AS recibida
  FROM moviresu
 WHERE morstdem = '&v_empresa'
   AND morstido = '&v_tipo'
   AND morsrenu = &v_numero
 GROUP BY morsesta
 ORDER BY morsesta;

-- El documento de inventario que deberia haber generado el ERP.
SELECT d.dointdem, d.dointido, d.doinnume, d.doinfech, d.dointerc, d.doinesta
  FROM docuinve d
 WHERE d.dointdem = '&v_empresa'
   AND d.doinfech >= TRUNC(SYSDATE)
 ORDER BY d.doinnume DESC
 FETCH FIRST 10 ROWS ONLY;


PROMPT
PROMPT ============================================================
PROMPT PASO 5 - Decidir. Descomentar UNA de las dos.
PROMPT ============================================================
--
-- COMMIT: confirma el registro.
-- ROLLBACK: revierte el registro. El log NO se pierde: procrear es autonomo y
--           hace su propio COMMIT, asi que el rastro queda igualmente.
--
-- Cuidado con sqlplus: al salir hace COMMIT por defecto. Usar EXIT ROLLBACK
-- si no se quiere confirmar.
--
-- Atencion: registrar es el punto de no retorno del modulo. Con el documento
-- de salida ya generado y confirmado, deshacerlo es trabajo manual en el ERP.
--
-- COMMIT;
-- ROLLBACK;
