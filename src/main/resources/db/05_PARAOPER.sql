--------------------------------------------------------------------------------
-- PARAOPER - Parametros de configuracion del modulo de traspasos
--
-- Ejecutar en el esquema de recursos fisicos SRF (SRFPNACUA), el mismo donde se
-- compila PKG_FI_MOVITRAS: FunBuscPara lee la PARAOPER que resuelve ese usuario.
--
-- PKG_FI_MOVITRAS los lee con FunBuscPara(clave):
--
--     PARANOPR  clave del parametro (lo que busca el paquete)
--     PARACOVA  valor que devuelve FunBuscPara
--
-- Las claves van sin separadores y con maximo 10 bytes, igual que las que ya
-- usa el ERP (ESTAANUL). Si se cambia una clave aqui hay que cambiar tambien la
-- constante correspondiente en el BODY del paquete: los dos nombres tienen que
-- coincidir exactamente.
--
-- ATENCION: los valores marcados con >>> DEFINIR <<< dependen de la
-- configuracion de cada empresa y hay que reemplazarlos antes de ejecutar. El
-- script no los inventa: si quedan en blanco, fun_parametro falla al primer
-- traspaso con un mensaje explicito en vez de dejar pasar un dato invalido.
--
-- El tipo de documento NO se parametriza. Se deriva del tipo de movimiento
-- (TIMOTRIN): el paquete valida que ese tipo tenga la operacion TRASPASO en
-- TIMOOPER y toma TIPODOCU.TIDOTIDO de TIDOTIMO.
--------------------------------------------------------------------------------

--------------------------------------------------------------------------------
-- Valores a configurar
--
-- Solo hay que tocar este bloque: los INSERT de abajo no cambian.
--------------------------------------------------------------------------------
DEFINE v_timotrin = '04'                -- Tipo de movimiento del traspaso
DEFINE v_estatras = 'pe'                -- Estado del documento al crearlo
DEFINE v_esaptras = '>>> DEFINIR <<<'   -- Estado del documento al aprobar
DEFINE v_ceintras = '>>> DEFINIR <<<'   -- Centro de informacion
DEFINE v_cocmtras = '>>> DEFINIR <<<'   -- Concepto de movimiento
DEFINE v_blfutras = '>>> DEFINIR <<<'   -- Bodega logica fuente
DEFINE v_bldetras = '>>> DEFINIR <<<'   -- Bodega logica destino
DEFINE v_bodepers = '>>> DEFINIR <<<'   -- Tipo de bodega con que se resuelve la bodega de una division

--------------------------------------------------------------------------------
-- Parametros
--------------------------------------------------------------------------------
INSERT INTO PARAOPER (PARACONP,PARANOPR,PARACONV,PARAGRUP,PARACOVA,PARADESC,PARAVIGO,PARACRBY,PARACRAT,PARAUSGR,PARAUTGR,PARACTAB)
     VALUES ('TRAS','TIMOTRIN','A','2','&v_timotrin','TIPO DE MOVIMIENTO TRASPASO DE ACTIVOS','N',USER,SYSDATE,NULL,NULL,'N');

INSERT INTO PARAOPER (PARACONP,PARANOPR,PARACONV,PARAGRUP,PARACOVA,PARADESC,PARAVIGO,PARACRBY,PARACRAT,PARAUSGR,PARAUTGR,PARACTAB)
     VALUES ('TRAS','ESTATRAS','A','2','&v_estatras','ESTADO INICIAL TRASPASO DE ACTIVOS','N',USER,SYSDATE,NULL,NULL,'N');

INSERT INTO PARAOPER (PARACONP,PARANOPR,PARACONV,PARAGRUP,PARACOVA,PARADESC,PARAVIGO,PARACRBY,PARACRAT,PARAUSGR,PARAUTGR,PARACTAB)
     VALUES ('TRAS','ESAPTRAS','A','2','&v_esaptras','ESTADO AL APROBAR TRASPASO DE ACTIVOS','N',USER,SYSDATE,NULL,NULL,'N');

INSERT INTO PARAOPER (PARACONP,PARANOPR,PARACONV,PARAGRUP,PARACOVA,PARADESC,PARAVIGO,PARACRBY,PARACRAT,PARAUSGR,PARAUTGR,PARACTAB)
     VALUES ('TRAS','CEINTRAS','A','2','&v_ceintras','CENTRO DE INFORMACION TRASPASO DE ACTIVOS','N',USER,SYSDATE,NULL,NULL,'N');

INSERT INTO PARAOPER (PARACONP,PARANOPR,PARACONV,PARAGRUP,PARACOVA,PARADESC,PARAVIGO,PARACRBY,PARACRAT,PARAUSGR,PARAUTGR,PARACTAB)
     VALUES ('TRAS','COCMTRAS','A','2','&v_cocmtras','CONCEPTO DE MOVIMIENTO TRASPASO DE ACTIVOS','N',USER,SYSDATE,NULL,NULL,'N');

INSERT INTO PARAOPER (PARACONP,PARANOPR,PARACONV,PARAGRUP,PARACOVA,PARADESC,PARAVIGO,PARACRBY,PARACRAT,PARAUSGR,PARAUTGR,PARACTAB)
     VALUES ('TRAS','BLFUTRAS','A','2','&v_blfutras','BODEGA LOGICA FUENTE TRASPASO DE ACTIVOS','N',USER,SYSDATE,NULL,NULL,'N');

INSERT INTO PARAOPER (PARACONP,PARANOPR,PARACONV,PARAGRUP,PARACOVA,PARADESC,PARAVIGO,PARACRBY,PARACRAT,PARAUSGR,PARAUTGR,PARACTAB)
     VALUES ('TRAS','BLDETRAS','A','2','&v_bldetras','BODEGA LOGICA DESTINO TRASPASO DE ACTIVOS','N',USER,SYSDATE,NULL,NULL,'N');

INSERT INTO PARAOPER (PARACONP,PARANOPR,PARACONV,PARAGRUP,PARACOVA,PARADESC,PARAVIGO,PARACRBY,PARACRAT,PARAUSGR,PARAUTGR,PARACTAB)
     VALUES ('TRAS','BODEPERS','A','2','&v_bodepers','TIPO DE BODEGA DE LA DIVISION DE LA PERSONA','N',USER,SYSDATE,NULL,NULL,'N');

COMMIT;

--------------------------------------------------------------------------------
-- Verificacion
--
-- Las 8 claves deben aparecer con valor, y ninguna con >>> DEFINIR <<<:
--
--   SELECT PARANOPR, PARACOVA, PARADESC
--     FROM PARAOPER
--    WHERE PARANOPR IN ('TIMOTRIN','ESTATRAS','ESAPTRAS','CEINTRAS',
--                       'COCMTRAS','BLFUTRAS','BLDETRAS','BODEPERS')
--    ORDER BY PARANOPR;
--
-- El tipo de movimiento configurado debe resolver la operacion y el documento:
--
--   SELECT * FROM TIMOOPER WHERE TMOPTIMO = '&v_timotrin' AND TMOPOPER = 'TRASPASO';
--   SELECT TIDOTIDO FROM TIPODOCU WHERE TIDOTIMO = '&v_timotrin';
--
-- Reejecucion: este script no es idempotente, un segundo INSERT duplica la
-- clave. Para corregir un valor ya cargado va un UPDATE:
--
--   UPDATE PARAOPER SET PARACOVA = 'nuevo' WHERE PARANOPR = 'ESAPTRAS';
--------------------------------------------------------------------------------
