--------------------------------------------------------------------------------
-- FI_REQUFIRM - Firmas digitales de una requisicion de suministro
--
-- Prefijo de columnas: REFI (REquisicion FIrma).
--
-- Dos filas por requisicion, creadas VACIAS la primera vez que una de sus
-- lineas pasa a entregado ('en'):
--
--     REFIPOSI = 1   firma de SALIDA  (quien entrega, el almacen)
--     REFIPOSI = 2   firma de RECIBE  (quien solicito)
--
-- La firma es una por requisicion, no una por articulo. Por eso vive en su
-- propia tabla y no en MOVIRESU, que tiene una fila por articulo.
--
-- POR QUE LAS FILAS SE CREAN AL ENTREGAR
--
-- A diferencia del modulo de traspasos, SIGAPP no crea la requisicion: nace en
-- el ERP en estado 'in' y llega aqui ya formada. El momento en que el modulo
-- toma el control y necesita las firmas es la entrega, que es donde el diagrama
-- funcional situa la peticion de firma. Crearlas vacias en ese punto permite
-- que registrar una firma sea un UPDATE y que sea la PK la que impida
-- fisicamente una segunda firma sobre la misma posicion.
--
-- SIN ORDEN ENTRE LAS FIRMAS
--
-- Cualquiera de las dos puede registrarse primero, y ninguna mueve el estado de
-- MOVIRESU, que se queda en 'en' mientras se firma. Son dos booleanos
-- independientes y una sola columna de estado no puede representarlos. Quien ya
-- firmo es simplemente quien tiene REFIFEFI no nulo. Generar la salida exige
-- que las dos posiciones esten firmadas.
--
-- AQUI SI HAY LLAVE FORANEA
--
-- Es la diferencia con FI_MOTRFIRM, donde fue imposible declararla: alli
-- MOTRNUTR se repetia en las N lineas del tramite y Oracle exige unicidad en la
-- columna referenciada (ORA-02270). La terna (empresa, tipo, numero) SI es
-- unica en REQUSUMI -lo demuestra que MOVIRESU_REQUSUMI_FK ya la referencia-,
-- asi que la integridad la sostiene la base de datos y no solo el paquete.
--
-- La FK no altera REQUSUMI: la restriccion vive en esta tabla.
--
-- La coherencia entre REFIFEFI y REFIFIPE (o las dos nulas, o las dos con
-- valor) no va como CHECK porque Oracle restringe el uso de columnas LOB en
-- constraints. La aplica pro_registrar_firma, que escribe ambas a la vez.
--
-- El script es idempotente: cada objeto se crea dentro de un bloque PL/SQL que
-- ignora los errores de "ya existe", de modo que puede reejecutarse sin riesgo.
--------------------------------------------------------------------------------

--------------------------------------------------------------------------------
-- Tabla
--
-- REFIFIPE es BLOB con los bytes de la imagen, no el base64 en un CLOB: ocupa
-- ~33% menos, se abre con "View as image" desde SQL Developer o Toad y evita el
-- ORA-01460 al pasar un String largo a un parametro CLOB desde el driver. El
-- backend recibe el base64 del front, lo valida y lo decodifica antes de llamar
-- al paquete.
--
-- REFIFEFI es TIMESTAMP y no DATE. El requisito funcional pide guardar el
-- instante exacto de la firma, y las columnas de fecha del ERP son DATE, que
-- en Oracle conserva la hora pero no la fraccion de segundo. Para una firma
-- manuscrita que puede recogerse en rafaga, la precision importa.
--
-- REFICODI admite nulos, al contrario que MOFICODI en traspasos: cuando la
-- fila se crea todavia no se sabe quien va a firmar. Se llena al firmar.
--------------------------------------------------------------------------------
DECLARE
    objeto_ya_existe EXCEPTION;
    PRAGMA EXCEPTION_INIT(objeto_ya_existe, -955);
BEGIN
    EXECUTE IMMEDIATE q'[
        CREATE TABLE FI_REQUFIRM (
            REFIEMPR VARCHAR2(6)     NOT NULL,
            REFITIDO VARCHAR2(4)     NOT NULL,
            REFINUME NUMBER(9,0)     NOT NULL,
            REFIPOSI NUMBER(1)       NOT NULL,
            REFICODI VARCHAR2(20),
            REFIFEFI TIMESTAMP,
            REFIFIPE BLOB,
            REFICRAT TIMESTAMP       DEFAULT SYSTIMESTAMP NOT NULL,
            REFICRBY VARCHAR2(30),
            REFIUPAT TIMESTAMP,
            REFIUPBY VARCHAR2(30),
            CONSTRAINT FI_REQUFIRM_PK PRIMARY KEY (REFIEMPR, REFITIDO, REFINUME, REFIPOSI),
            CONSTRAINT FI_REQUFIRM_CK_POSI CHECK (REFIPOSI IN (1,2))
        )
    ]';
EXCEPTION
    WHEN objeto_ya_existe THEN NULL;
END;
/

--------------------------------------------------------------------------------
-- Indice de auditoria
--
-- No hace falta uno sobre la terna: la PK ya la lleva como columnas iniciales y
-- en el mismo orden, y todo acceso del modulo es "las firmas de esta
-- requisicion". Tampoco hace falta el indice de la FK por la misma razon.
--
-- El de la persona cubre "que firmo esta persona", que es el otro camino de
-- entrada previsible cuando alguien audita.
--------------------------------------------------------------------------------
DECLARE
    objeto_ya_existe EXCEPTION;
    PRAGMA EXCEPTION_INIT(objeto_ya_existe, -955);
BEGIN
    EXECUTE IMMEDIATE 'CREATE INDEX FI_REQUFIRM_IX_CODI ON FI_REQUFIRM (REFICODI, REFIFEFI)';
EXCEPTION
    WHEN objeto_ya_existe THEN NULL;
END;
/

--------------------------------------------------------------------------------
-- Llave foranea hacia el documento de requisicion
--
-- Ata cada firma a una requisicion que existe. Si el ERP borrara la requisicion
-- -cosa que no hace- la firma quedaria bloqueada, que es el comportamiento
-- deseado: una firma huerfana no significa nada.
--------------------------------------------------------------------------------
DECLARE
    nombre_ya_usado EXCEPTION;
    PRAGMA EXCEPTION_INIT(nombre_ya_usado, -2264);

    fk_ya_existe EXCEPTION;
    PRAGMA EXCEPTION_INIT(fk_ya_existe, -2275);
BEGIN
    EXECUTE IMMEDIATE 'ALTER TABLE FI_REQUFIRM ADD CONSTRAINT FI_REQUFIRM_REQUSUMI_FK
                           FOREIGN KEY (REFIEMPR, REFITIDO, REFINUME)
                           REFERENCES REQUSUMI (RESUTDEM, RESUTIDO, RESUNUME)';
EXCEPTION
    WHEN nombre_ya_usado THEN NULL;
    WHEN fk_ya_existe    THEN NULL;
END;
/

--------------------------------------------------------------------------------
-- Documentacion de columnas
--------------------------------------------------------------------------------
COMMENT ON TABLE FI_REQUFIRM IS 'Firmas digitales de una requisicion de suministro, dos por documento';

COMMENT ON COLUMN FI_REQUFIRM.REFIEMPR IS 'Empresa del documento (REQUSUMI.RESUTDEM)';
COMMENT ON COLUMN FI_REQUFIRM.REFITIDO IS 'Tipo de documento de la requisicion (REQUSUMI.RESUTIDO)';
COMMENT ON COLUMN FI_REQUFIRM.REFINUME IS 'Numero de la requisicion (REQUSUMI.RESUNUME)';
COMMENT ON COLUMN FI_REQUFIRM.REFIPOSI IS 'Posicion: 1 firma de salida (entrega), 2 firma de recibe';
COMMENT ON COLUMN FI_REQUFIRM.REFICODI IS 'Persona que firma (PERSONAL.PERSCODI). Nulo mientras no se firma';
COMMENT ON COLUMN FI_REQUFIRM.REFIFEFI IS 'Instante exacto de la firma. Nulo mientras esa parte no haya firmado';
COMMENT ON COLUMN FI_REQUFIRM.REFIFIPE IS 'Firma manuscrita (imagen PNG/JPEG, bytes)';

COMMENT ON COLUMN FI_REQUFIRM.REFICRAT IS 'Auditoria: instante de insercion del registro';
COMMENT ON COLUMN FI_REQUFIRM.REFICRBY IS 'Auditoria: usuario/proceso que inserto el registro';
COMMENT ON COLUMN FI_REQUFIRM.REFIUPAT IS 'Auditoria: instante de la ultima modificacion';
COMMENT ON COLUMN FI_REQUFIRM.REFIUPBY IS 'Auditoria: usuario/proceso de la ultima modificacion';
