--------------------------------------------------------------------------------
-- Sinonimos y permisos del modulo de conteo fisico para el usuario de interfaz
--
-- El backend se conecta como el usuario de interfaz del ERP (ARQGUIPNACUA) y
-- nombra los objetos sin esquema. Las tablas del ERP las alcanza con los
-- sinonimos SRF_<tabla> que ese usuario ya tiene (SRF_BODEGA, SRF_CONTARBO...),
-- pero los objetos propios del conteo fisico son nuevos y nadie se los creo:
--
--     FI_COFIUSCO     usuarios temporales de conteo
--     FI_COFIARAS     asignaciones y registros de conteo
--     SEQ_COFIUSCO    } secuencias con que el backend genera los ids
--     SEQ_COFIARAS    }
--     SEQ_COFIASIG    }
--
-- Sin esto, conectado como ARQGUIPNACUA el login de contadores y la asignacion
-- de articulos fallan con ORA-00942.
--
-- Mismo criterio que 05_SINONIMOS.sql de traspasos: los objetos los crea y posee
-- SRFPNACUA, y ARQGUIPNACUA los ve con su nombre corto. Los FI_ no llevan prefijo
-- SRF_ porque solo existen en el SRF y no hay con que confundirlos; asi quedaron
-- tambien FI_MOVITRAS y FI_REQUFIRM.
--
-- GRANT y CREATE OR REPLACE SYNONYM son idempotentes: reejecutar no falla.
--------------------------------------------------------------------------------

--------------------------------------------------------------------------------
-- 1) Permisos  (ejecutar como SRFPNACUA)
--
-- El backend inserta y actualiza directamente en las dos tablas (JPA); no borra.
-- Sobre una secuencia el privilegio de uso es SELECT.
--------------------------------------------------------------------------------
GRANT SELECT, INSERT, UPDATE ON SRFPNACUA.FI_COFIUSCO TO ARQGUIPNACUA;
GRANT SELECT, INSERT, UPDATE ON SRFPNACUA.FI_COFIARAS TO ARQGUIPNACUA;

GRANT SELECT ON SRFPNACUA.SEQ_COFIUSCO TO ARQGUIPNACUA;
GRANT SELECT ON SRFPNACUA.SEQ_COFIARAS TO ARQGUIPNACUA;
GRANT SELECT ON SRFPNACUA.SEQ_COFIASIG TO ARQGUIPNACUA;

--------------------------------------------------------------------------------
-- 2) Sinonimos. Basta con una de las dos rutas.
--
-- Ruta A: conectado como SRFPNACUA, si tiene CREATE ANY SYNONYM.
-- Ruta B: conectado como ARQGUIPNACUA, en su propio esquema.
--------------------------------------------------------------------------------

-- Ruta A (como SRFPNACUA)
CREATE OR REPLACE SYNONYM ARQGUIPNACUA.FI_COFIUSCO  FOR SRFPNACUA.FI_COFIUSCO;
CREATE OR REPLACE SYNONYM ARQGUIPNACUA.FI_COFIARAS  FOR SRFPNACUA.FI_COFIARAS;
CREATE OR REPLACE SYNONYM ARQGUIPNACUA.SEQ_COFIUSCO FOR SRFPNACUA.SEQ_COFIUSCO;
CREATE OR REPLACE SYNONYM ARQGUIPNACUA.SEQ_COFIARAS FOR SRFPNACUA.SEQ_COFIARAS;
CREATE OR REPLACE SYNONYM ARQGUIPNACUA.SEQ_COFIASIG FOR SRFPNACUA.SEQ_COFIASIG;

-- Ruta B (como ARQGUIPNACUA)
-- CREATE OR REPLACE SYNONYM FI_COFIUSCO  FOR SRFPNACUA.FI_COFIUSCO;
-- CREATE OR REPLACE SYNONYM FI_COFIARAS  FOR SRFPNACUA.FI_COFIARAS;
-- CREATE OR REPLACE SYNONYM SEQ_COFIUSCO FOR SRFPNACUA.SEQ_COFIUSCO;
-- CREATE OR REPLACE SYNONYM SEQ_COFIARAS FOR SRFPNACUA.SEQ_COFIARAS;
-- CREATE OR REPLACE SYNONYM SEQ_COFIASIG FOR SRFPNACUA.SEQ_COFIASIG;

--------------------------------------------------------------------------------
-- Verificacion (como ARQGUIPNACUA). Las cinco filas deben salir, y la lectura
-- no debe dar ORA-00942:
--
--   SELECT synonym_name, table_owner, table_name
--     FROM user_synonyms
--    WHERE synonym_name IN ('FI_COFIUSCO','FI_COFIARAS','SEQ_COFIUSCO','SEQ_COFIARAS','SEQ_COFIASIG');
--
--   SELECT COUNT(*) FROM FI_COFIUSCO;
--
-- La comprobacion completa de todos los objetos que usa el backend esta en
-- db/validacion/VALIDAR_ACCESO_USUARIO_BD.sql.
--------------------------------------------------------------------------------
