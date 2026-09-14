--------------------------------------------------------------------------------
-- Sinonimos y permisos del modulo de requisiciones
--
-- Cubre los dos objetos propios: la tabla FI_REQUFIRM y el paquete
-- PKG_FI_REQUISICION. No hay secuencias: la clave de la firma es la terna de la
-- requisicion mas la posicion, todo dato que ya existe.
--
-- Los objetos del modulo se crean en SRFPNACUA (dueno), pero ARQGUIPNACUA debe
-- poder verlos y operarlos con el nombre corto, sin calificar el esquema.
--
-- Este script va aparte de 01_FI_REQUFIRM.sql porque exige privilegios que el
-- dueno no necesariamente tiene: crear un sinonimo dentro de otro esquema
-- requiere CREATE ANY SYNONYM. Por eso se ofrecen las dos rutas (A y B): basta
-- con ejecutar una.
--
-- GRANT y CREATE OR REPLACE SYNONYM son idempotentes: reejecutar no falla.
--
-- ESTE SCRIPT NO TOCA NINGUN OBJETO EXISTENTE. Solo concede permisos sobre los
-- objetos nuevos y crea sinonimos que apuntan a ellos.
--------------------------------------------------------------------------------

--------------------------------------------------------------------------------
-- 1) Permisos  (ejecutar como SRFPNACUA)
--
-- En Oracle cada verbo se concede por separado: SELECT habilita unicamente la
-- lectura. Sin INSERT/UPDATE explicitos, ARQGUIPNACUA recibe ORA-01031
-- "insufficient privileges" al intentar escribir.
--
-- Sin el GRANT el sinonimo se crea igual, pero al usarlo ARQGUIPNACUA recibe
-- ORA-00942 "table or view does not exist": el sinonimo resuelve el nombre, no
-- concede el acceso.
--
-- No se concede DELETE sobre FI_REQUFIRM: una firma registrada no se borra. Las
-- filas se crean vacias al entregar y se actualizan al firmar, nada mas.
--------------------------------------------------------------------------------
GRANT SELECT, INSERT, UPDATE ON SRFPNACUA.FI_REQUFIRM TO ARQGUIPNACUA;

-- El paquete es la unica via de escritura del modulo: aplica el bloqueo, las
-- validaciones de estado y el sellado de la cantidad recibida. Quien opere
-- requisiciones debe hacerlo por aqui y no con DML suelto sobre MOVIRESU.
GRANT EXECUTE ON SRFPNACUA.PKG_FI_REQUISICION TO ARQGUIPNACUA;

--------------------------------------------------------------------------------
-- 2) Sinonimos - RUTA A  (ejecutar como SRFPNACUA o con un usuario administrador)
--
-- Requiere CREATE ANY SYNONYM. Es la ruta comoda: todo el entregable se aplica
-- con la misma conexion.
--------------------------------------------------------------------------------
CREATE OR REPLACE SYNONYM ARQGUIPNACUA.FI_REQUFIRM        FOR SRFPNACUA.FI_REQUFIRM;
CREATE OR REPLACE SYNONYM ARQGUIPNACUA.PKG_FI_REQUISICION FOR SRFPNACUA.PKG_FI_REQUISICION;

--------------------------------------------------------------------------------
-- 2) Sinonimos - RUTA B  (ejecutar CONECTADO COMO ARQGUIPNACUA)
--
-- Si no se dispone de CREATE ANY SYNONYM, cada esquema crea los suyos. Requiere
-- solo CREATE SYNONYM, que es un privilegio habitual.
--
-- Descomentar y ejecutar en una sesion de ARQGUIPNACUA:
--
-- CREATE OR REPLACE SYNONYM FI_REQUFIRM        FOR SRFPNACUA.FI_REQUFIRM;
-- CREATE OR REPLACE SYNONYM PKG_FI_REQUISICION FOR SRFPNACUA.PKG_FI_REQUISICION;
--------------------------------------------------------------------------------

--------------------------------------------------------------------------------
-- 3) Verificacion
--
-- Conectado como ARQGUIPNACUA, las dos consultas deben responder sin error:
--
--   SELECT COUNT(*) FROM FI_REQUFIRM;
--   SELECT synonym_name, table_owner, table_name FROM all_synonyms
--    WHERE synonym_name IN ('FI_REQUFIRM','PKG_FI_REQUISICION');
--
-- OJO CON EQUIVALE: hay dos tablas con ese nombre en la base, una en SRFPNACUA
-- y otra en FPLPNACUA, y el sinonimo de ARQGUIPNACUA apunta a la de FPLPNACUA.
-- El paquete la referencia sin calificar desde SRFPNACUA, asi que la
-- equivalencia COBO/REQU que resuelve el tipo de documento de salida tiene que
-- estar cargada en SRFPNACUA.EQUIVALE. Comprobarlo antes de la primera prueba:
--
--   SELECT equicodi, equicoeq, equiesta FROM srfpnacua.equivale WHERE equitipo = 'REQU';
--------------------------------------------------------------------------------
