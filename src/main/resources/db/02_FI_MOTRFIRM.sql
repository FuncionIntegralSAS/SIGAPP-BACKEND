--------------------------------------------------------------------------------
-- FI_MOTRFIRM - Firmas de un traspaso SIGAPP
--
-- Prefijo de columnas: MOFI (MOvimiento FIrma).
--
-- Dos filas por tramite, creadas vacias en el momento en que se abre el
-- traspaso:
--
--     MOFIPOSI = 1   la persona que ENTREGA  (fuente)
--     MOFIPOSI = 2   la persona que RECIBE   (destino)
--
-- La firma se guarda UNA VEZ por tramite, no una por articulo, que era el
-- motivo de separarla de FI_MOVITRAS.
--
-- SIN ORDEN ENTRE LAS FIRMAS
--
-- Cualquiera de las dos puede registrarse primero. El avance no se refleja en
-- FI_MOVITRAS.MOTRESTA, que se queda en 'ap' mientras se firma: son dos
-- booleanos independientes y una sola columna de estado no puede
-- representarlos. Quien ya firmo es simplemente quien tiene MOFIFEFI no nulo.
-- La recepcion exige que las dos posiciones esten firmadas.
--
-- POR QUE NO HAY LLAVE FORANEA
--
-- MOFINUTR apunta a FI_MOVITRAS.MOTRNUTR, que se repite en las N lineas del
-- tramite. Oracle exige PRIMARY KEY o UNIQUE en la columna referenciada
-- (ORA-02270), y no hay ninguna tabla donde el numero de tramite sea unico. La
-- integridad la sostiene PKG_FI_MOVITRAS, que valida la existencia del tramite
-- antes de escribir aqui. Lo que si queda declarado es la PK compuesta, que
-- impide dos firmas para la misma posicion del mismo tramite.
--
-- La coherencia entre MOFIFEFI y MOFIFIPE (o las dos nulas, o las dos con
-- valor) tampoco va como CHECK: Oracle restringe el uso de columnas LOB en
-- constraints. La aplica pro_registrar_firma, que escribe ambas a la vez.
--
-- El script es idempotente: cada objeto se crea dentro de un bloque PL/SQL que
-- ignora los errores de "ya existe", de modo que puede reejecutarse sin riesgo.
--------------------------------------------------------------------------------

--------------------------------------------------------------------------------
-- Tabla
--
-- MOFIFIPE es BLOB con los bytes de la imagen, no el base64 en un CLOB: ocupa
-- ~33% menos, se abre con "View as image" desde SQL Developer o Toad y evita el
-- ORA-01460 al pasar un String largo a un parametro CLOB desde el driver. El
-- backend recibe el base64 del front, lo valida y lo decodifica antes de llamar
-- al paquete.
--------------------------------------------------------------------------------
DECLARE
    objeto_ya_existe EXCEPTION;
    PRAGMA EXCEPTION_INIT(objeto_ya_existe, -955);
BEGIN
    EXECUTE IMMEDIATE q'[
        CREATE TABLE FI_MOTRFIRM (
            MOFINUTR NUMBER(12)      NOT NULL,
            MOFIPOSI NUMBER(1)       NOT NULL,
            MOFICODI VARCHAR2(20)    NOT NULL,
            MOFIFEFI TIMESTAMP,
            MOFIFIPE BLOB,
            MOFICRAT TIMESTAMP       DEFAULT SYSTIMESTAMP NOT NULL,
            MOFICRBY VARCHAR2(30),
            MOFIUPAT TIMESTAMP,
            MOFIUPBY VARCHAR2(30),
            CONSTRAINT FI_MOTRFIRM_PK PRIMARY KEY (MOFINUTR, MOFIPOSI),
            CONSTRAINT FI_MOTRFIRM_CK_POSI CHECK (MOFIPOSI IN (1,2))
        )
    ]';
EXCEPTION
    WHEN objeto_ya_existe THEN NULL;
END;
/

--------------------------------------------------------------------------------
-- Indices
--
-- No hace falta uno sobre MOFINUTR: la PK ya lo lleva como primera columna, y
-- todo acceso al modulo es "las firmas de este tramite".
--
-- El de la persona cubre la consulta de auditoria "que firmo esta persona", que
-- es el otro camino de entrada previsible.
--------------------------------------------------------------------------------
DECLARE
    objeto_ya_existe EXCEPTION;
    PRAGMA EXCEPTION_INIT(objeto_ya_existe, -955);
BEGIN
    EXECUTE IMMEDIATE 'CREATE INDEX FI_MOTRFIRM_IX_CODI ON FI_MOTRFIRM (MOFICODI, MOFIFEFI)';
EXCEPTION
    WHEN objeto_ya_existe THEN NULL;
END;
/

--------------------------------------------------------------------------------
-- Llave foranea de la persona
--
-- Esta si se puede declarar: PERSONAL.PERSCODI es unico. La del tramite no,
-- segun lo explicado en la cabecera.
--------------------------------------------------------------------------------
DECLARE
    nombre_ya_usado EXCEPTION;
    PRAGMA EXCEPTION_INIT(nombre_ya_usado, -2264);

    fk_ya_existe EXCEPTION;
    PRAGMA EXCEPTION_INIT(fk_ya_existe, -2275);
BEGIN
    EXECUTE IMMEDIATE 'ALTER TABLE FI_MOTRFIRM ADD CONSTRAINT FI_MOTRFIRM_PERSONAL_FK
                           FOREIGN KEY (MOFICODI) REFERENCES PERSONAL (PERSCODI)';
EXCEPTION
    WHEN nombre_ya_usado THEN NULL;
    WHEN fk_ya_existe    THEN NULL;
END;
/

--------------------------------------------------------------------------------
-- Documentacion de columnas
--------------------------------------------------------------------------------
COMMENT ON TABLE FI_MOTRFIRM IS 'Firmas de aceptacion de un traspaso SIGAPP, dos por tramite';

COMMENT ON COLUMN FI_MOTRFIRM.MOFINUTR IS 'Numero de tramite (FI_MOVITRAS.MOTRNUTR). Sin FK: la columna destino se repite en las lineas';
COMMENT ON COLUMN FI_MOTRFIRM.MOFIPOSI IS 'Posicion: 1 entrega (persona fuente), 2 recibe (persona destino)';
COMMENT ON COLUMN FI_MOTRFIRM.MOFICODI IS 'Persona que firma (PERSONAL.PERSCODI)';
COMMENT ON COLUMN FI_MOTRFIRM.MOFIFEFI IS 'Instante de la firma. Nulo mientras esa parte no haya firmado';
COMMENT ON COLUMN FI_MOTRFIRM.MOFIFIPE IS 'Firma de la persona (imagen PNG/JPEG, bytes)';

COMMENT ON COLUMN FI_MOTRFIRM.MOFICRAT IS 'Auditoria: instante de insercion del registro';
COMMENT ON COLUMN FI_MOTRFIRM.MOFICRBY IS 'Auditoria: usuario/proceso que inserto el registro';
COMMENT ON COLUMN FI_MOTRFIRM.MOFIUPAT IS 'Auditoria: instante de la ultima modificacion';
COMMENT ON COLUMN FI_MOTRFIRM.MOFIUPBY IS 'Auditoria: usuario/proceso de la ultima modificacion';
