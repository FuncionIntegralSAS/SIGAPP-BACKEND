--------------------------------------------------------------------------------
-- PARAOPER - URL publica del backend SIGAPP (parametro URLSIGAPP)
--
-- ##############################################################################
-- ##  ATENCION: ESTE VALOR ES DISTINTO EN CADA AMBIENTE                       ##
-- ##                                                                          ##
-- ##  Aqui va la URL PUBLICA con la que la app movil llega al backend de      ##
-- ##  ESTE ambiente, la que entrega infraestructura. No es la URL interna     ##
-- ##  del cluster, ni localhost, ni la de otro ambiente.                      ##
-- ##                                                                          ##
-- ##  Aplicar este script con la URL de otro ambiente hace que los            ##
-- ##  dispositivos que se configuren aqui queden apuntando alla.              ##
-- ##                                                                          ##
-- ##  Lo mismo pasa si la base se CLONA o se REFRESCA desde otro ambiente:    ##
-- ##  el parametro llega con la URL del origen y hay que corregirlo con el    ##
-- ##  UPDATE del final antes de usar la app.                                  ##
-- ##############################################################################
--
-- La app movil es un unico APK para todos los ambientes. Se configura leyendo un
-- QR con la URL del backend y la valida contra GET /api/v1/health/info, que
-- devuelve este parametro. Si no coincide con la URL real del ambiente, la app
-- rechaza la configuracion o migra los dispositivos a una direccion equivocada.
--
-- Ejecutar conectado como el esquema SRF (el dueno), igual que el resto de los
-- entregables. En la base hay DOS tablas PARAOPER, la del SRF y la del FPL, con
-- columnas distintas: este script esta escrito para la del SRF y falla en la
-- otra. Para el usuario de interfaz (ARQGUI) el nombre PARAOPER apunta a la del
-- FPL, asi que NO sirve ejecutarlo con ese usuario.
--
-- El backend, conectado como ARQGUI, la lee a traves del sinonimo SRF_PARAOPER,
-- que llega a esta misma tabla. Se comprueba con
-- db/validacion/VALIDAR_ACCESO_USUARIO_BD.sql.
--
-- Formato exigido, que tambien valida el backend al leerlo:
--     https://host            sin barra final, sin ruta, en minusculas
-- Ejemplo de desarrollo:  https://sigapp-api-dev.saas.arqbs.com
-- PARACOVA mide 70 caracteres: la URL completa tiene que caber ahi.
--
-- El script NO sobreescribe. Si el parametro ya existe lo deja como esta y avisa,
-- para que reaplicar los entregables nunca cambie la URL de un ambiente que ya
-- esta en uso. Un cambio de URL se hace a proposito, con el UPDATE del final.
--------------------------------------------------------------------------------

--------------------------------------------------------------------------------
-- Valor a configurar. Es lo unico que hay que tocar.
--------------------------------------------------------------------------------
DEFINE v_urlsigapp = '>>> DEFINIR <<<'

SET VERIFY OFF
WHENEVER SQLERROR EXIT FAILURE ROLLBACK

-- El resultado sale por PRINT y no por DBMS_OUTPUT: hay clientes en los que
-- DBMS_OUTPUT no se muestra, y el aviso de "ya existe con otro valor" es justo
-- lo que no puede pasar desapercibido.
VARIABLE resultado VARCHAR2(600)

DECLARE
   sb_url      VARCHAR2(200) := '&v_urlsigapp';
   sb_actual   VARCHAR2(70);
   nu_existe   NUMBER;
BEGIN
   -- Si se aplica sin editar el DEFINE, o con http, una ruta o una barra final,
   -- se corta aqui: no se inserta nada.
   IF NOT REGEXP_LIKE(sb_url, '^https://[a-z0-9]([a-z0-9.-]*[a-z0-9])?(:[0-9]{1,5})?$') THEN
      RAISE_APPLICATION_ERROR(-20001,
         'URLSIGAPP invalida [' || sb_url || ']. Editar el DEFINE con la URL publica '
         || 'de ESTE ambiente, con la forma https://host');
   END IF;

   IF LENGTH(sb_url) > 70 THEN
      RAISE_APPLICATION_ERROR(-20001,
         'URLSIGAPP mide ' || LENGTH(sb_url) || ' caracteres y PARACOVA admite 70');
   END IF;

   SELECT COUNT(*), MAX(PARACOVA)
     INTO nu_existe, sb_actual
     FROM PARAOPER
    WHERE PARACODI = 'URLSIGAPP';

   IF nu_existe = 0 THEN
      INSERT INTO PARAOPER (PARACODI, PARADESC, PARATIVA, PARALOVA, PARACOVA,
                            PARAGRUP, PARAVESE, PARASELE, PARAMOUS)
           VALUES ('URLSIGAPP', 'URL PUBLICA DEL BACKEND SIGAPP PARA LA APP MOVIL',
                   'A', 70, sb_url, 'SIST', 'N', NULL, 'N');
      :resultado := 'URLSIGAPP creado con valor: ' || sb_url;
   ELSIF sb_actual = sb_url THEN
      :resultado := 'URLSIGAPP ya existia con ese valor. Sin cambios.';
   ELSE
      :resultado := '*** ATENCION: URLSIGAPP ya existe con OTRO valor y NO se modifico.' || CHR(10)
                 || '***   en la base  : ' || sb_actual || CHR(10)
                 || '***   en el script: ' || sb_url || CHR(10)
                 || '*** Si el cambio es intencional, usar el UPDATE del final del script.';
   END IF;
END;
/

COMMIT;

PRINT resultado

--------------------------------------------------------------------------------
-- Verificacion
--
--   SELECT PARACODI, PARACOVA FROM PARAOPER WHERE PARACODI = 'URLSIGAPP';
--
-- Y desde fuera de la red interna, que es lo que de verdad prueba que la URL es
-- la publica:
--
--   curl https://<la-url>/api/v1/health/info
--
-- Debe responder code 0 y traer en urlPublica el mismo valor que se cargo.
--
-- Cambio de URL en un ambiente que ya esta en uso. El backend lo lee en cada
-- consulta: no hay que reiniciar ni redesplegar nada.
--
--   UPDATE PARAOPER SET PARACOVA = 'https://nueva-url' WHERE PARACODI = 'URLSIGAPP';
--   COMMIT;
--
-- Mientras la URL anterior siga respondiendo, los dispositivos ya configurados
-- se enteran del cambio solos. Si se apaga antes, hay que volver a leer el QR.
--------------------------------------------------------------------------------
