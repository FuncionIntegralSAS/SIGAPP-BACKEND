--------------------------------------------------------------------------------
-- Sinonimos y permisos de FI_MOVITRAS
--
-- Los objetos del modulo se crean en SRFPNACUA (dueno), pero ARQGUIPNACUA debe
-- poder verlos y operarlos con el nombre corto FI_MOVITRAS, sin calificar el
-- esquema.
--
-- Este script va aparte de 01_FI_MOVITRAS.sql porque exige privilegios que el
-- dueno no necesariamente tiene: crear un sinonimo dentro de otro esquema
-- requiere CREATE ANY SYNONYM. Por eso se ofrecen las dos rutas (A y B): basta
-- con ejecutar una.
--
-- GRANT y CREATE OR REPLACE SYNONYM son idempotentes: reejecutar no falla.
--------------------------------------------------------------------------------

--------------------------------------------------------------------------------
-- 1) Permisos  (ejecutar como SRFPNACUA)
--
-- En Oracle cada verbo se concede por separado: SELECT habilita unicamente la
-- lectura. Sin INSERT/UPDATE/DELETE explicitos, ARQGUIPNACUA recibe
-- ORA-01031 "insufficient privileges" al intentar escribir.
--
-- Sin el GRANT el sinonimo se crea igual, pero al usarlo ARQGUIPNACUA recibe
-- ORA-00942 "table or view does not exist": el sinonimo resuelve el nombre, no
-- concede el acceso.
--------------------------------------------------------------------------------
GRANT SELECT, INSERT, UPDATE, DELETE ON SRFPNACUA.FI_MOVITRAS TO ARQGUIPNACUA;

-- La secuencia se necesita para poder insertar: MOTRIDMT se toma de
-- FI_MOVITRAS_SEQ.NEXTVAL y sobre secuencias el privilegio de uso es SELECT.
GRANT SELECT ON SRFPNACUA.FI_MOVITRAS_SEQ TO ARQGUIPNACUA;

-- El paquete es la via normal de escritura del modulo (crea el REQUSUMI y aplica
-- las validaciones de estado). Si ARQGUIPNACUA va a operar traspasos, deberia
-- hacerlo por aqui y no con DML suelto sobre la tabla.
GRANT EXECUTE ON SRFPNACUA.PKG_FI_MOVITRAS TO ARQGUIPNACUA;

--------------------------------------------------------------------------------
-- 2) Sinonimos - RUTA A  (ejecutar como SRFPNACUA o con un usuario administrador)
--
-- Requiere CREATE ANY SYNONYM. Es la ruta comoda: todo el entregable se aplica
-- con la misma conexion.
--------------------------------------------------------------------------------
CREATE OR REPLACE SYNONYM ARQGUIPNACUA.FI_MOVITRAS     FOR SRFPNACUA.FI_MOVITRAS;
CREATE OR REPLACE SYNONYM ARQGUIPNACUA.FI_MOVITRAS_SEQ FOR SRFPNACUA.FI_MOVITRAS_SEQ;
CREATE OR REPLACE SYNONYM ARQGUIPNACUA.PKG_FI_MOVITRAS FOR SRFPNACUA.PKG_FI_MOVITRAS;

--------------------------------------------------------------------------------
-- 2) Sinonimos - RUTA B  (ejecutar CONECTADO COMO ARQGUIPNACUA)
--
-- Alternativa cuando SRFPNACUA no tiene CREATE ANY SYNONYM. Solo requiere el
-- privilegio CREATE SYNONYM del propio usuario. Ejecutar esto O la ruta A, no
-- ambas.
--------------------------------------------------------------------------------
-- CREATE OR REPLACE SYNONYM FI_MOVITRAS     FOR SRFPNACUA.FI_MOVITRAS;
-- CREATE OR REPLACE SYNONYM FI_MOVITRAS_SEQ FOR SRFPNACUA.FI_MOVITRAS_SEQ;
-- CREATE OR REPLACE SYNONYM PKG_FI_MOVITRAS FOR SRFPNACUA.PKG_FI_MOVITRAS;

--------------------------------------------------------------------------------
-- Verificacion
--
-- Conectado como ARQGUIPNACUA, esto debe devolver una fila y no fallar:
--
--   SELECT COUNT(*) FROM FI_MOVITRAS;
--
-- Para confirmar a donde apunta el sinonimo:
--
--   SELECT synonym_name, table_owner, table_name
--     FROM all_synonyms
--    WHERE synonym_name IN ('FI_MOVITRAS','FI_MOVITRAS_SEQ','PKG_FI_MOVITRAS');
--
-- Para revisar que privilegios quedaron concedidos:
--
--   SELECT grantee, table_name, privilege
--     FROM all_tab_privs
--    WHERE table_name = 'FI_MOVITRAS';
--------------------------------------------------------------------------------
