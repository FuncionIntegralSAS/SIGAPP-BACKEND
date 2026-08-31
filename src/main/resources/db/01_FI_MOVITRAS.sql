--------------------------------------------------------------------------------
-- FI_MOVITRAS - Movimientos de traspaso SIGAPP
--
-- Reemplaza a FI_SIGATRAS. Prefijo de columnas: MOTR (MOvimiento TRaspaso).
--
-- Un traspaso siempre cuelga de un documento REQUSUMI (requisicion de
-- suministro). La llave del documento se nombra siguiendo la convencion del ERP
-- (DR = Documento Requisicion, igual que MOSORSEM/RSTD/RSNU en MOVISOCO):
--
--     MOTRDREM  ->  REQUSUMI.RESUTDEM   empresa
--     MOTRDRTD  ->  REQUSUMI.RESUTIDO   tipo de documento
--     MOTRDRND  ->  REQUSUMI.RESUNUME   numero
--
-- No existe traspaso sin documento: la FK es obligatoria.
--
-- Las firmas (MOTRFIFU / MOTRFIDE) se guardan como BLOB con los bytes de la
-- imagen, no como el base64 en un CLOB: ocupa ~33% menos, se puede ver desde
-- SQL Developer/Toad con "View as image" y evita el ORA-01460 que aparece al
-- pasar un String largo a un parametro CLOB desde el driver. El backend recibe
-- el base64 del front, lo valida y lo decodifica antes de llamar al paquete.
--
-- El script es idempotente: cada objeto se crea dentro de un bloque PL/SQL que
-- ignora los errores de "ya existe", de modo que puede reejecutarse sin riesgo.
--------------------------------------------------------------------------------

--------------------------------------------------------------------------------
-- Tabla
--------------------------------------------------------------------------------
DECLARE
    objeto_ya_existe EXCEPTION;
    PRAGMA EXCEPTION_INIT(objeto_ya_existe, -955);
BEGIN
    EXECUTE IMMEDIATE q'[
        CREATE TABLE FI_MOVITRAS (
            MOTRIDMT NUMBER(12)      NOT NULL,
            MOTRDREM VARCHAR2(6)     NOT NULL,
            MOTRDRTD VARCHAR2(4)     NOT NULL,
            MOTRDRND NUMBER(9,0)     NOT NULL,
            MOTRPEFU VARCHAR2(20)    NOT NULL,
            MOTRPEDE VARCHAR2(20)    NOT NULL,
            MOTRARTI VARCHAR2(20)    NOT NULL,
            MOTRPLAC VARCHAR2(20),
            MOTRESTA VARCHAR2(2)     DEFAULT 'pe' NOT NULL,
            MOTROBSE VARCHAR2(500),
            MOTRMORE VARCHAR2(500),
            MOTRFECR TIMESTAMP       DEFAULT SYSTIMESTAMP NOT NULL,
            MOTRUSCR VARCHAR2(30)    NOT NULL,
            MOTRFEAP TIMESTAMP,
            MOTRUSAP VARCHAR2(30),
            MOTRFAFU TIMESTAMP,
            MOTRFADE TIMESTAMP,
            MOTRFERE TIMESTAMP,
            MOTRFIFU BLOB,
            MOTRFIDE BLOB,
            MOTRCRAT TIMESTAMP       DEFAULT SYSTIMESTAMP NOT NULL,
            MOTRCRBY VARCHAR2(30),
            MOTRUPAT TIMESTAMP,
            MOTRUPBY VARCHAR2(30),
            CONSTRAINT FI_MOVITRAS_PK PRIMARY KEY (MOTRIDMT),
            CONSTRAINT FI_MOVITRAS_CK_ESTA
                CHECK (MOTRESTA IN ('pe','ap','na','af','ad','re'))
        )
    ]';
EXCEPTION
    WHEN objeto_ya_existe THEN NULL;
END;
/

--------------------------------------------------------------------------------
-- Secuencia del identificador
--------------------------------------------------------------------------------
DECLARE
    objeto_ya_existe EXCEPTION;
    PRAGMA EXCEPTION_INIT(objeto_ya_existe, -955);
BEGIN
    EXECUTE IMMEDIATE 'CREATE SEQUENCE FI_MOVITRAS_SEQ START WITH 1 INCREMENT BY 1 NOCACHE NOCYCLE';
EXCEPTION
    WHEN objeto_ya_existe THEN NULL;
END;
/

--------------------------------------------------------------------------------
-- Indices
--
-- Cubren los accesos de la bandeja (estado, persona destino, persona fuente) y
-- el historial de un activo (elemento + placa).
--
-- El indice del documento no es opcional: una FK sin indice en la hija provoca
-- lock de tabla en los DELETE/UPDATE de REQUSUMI. Lo mismo aplica a las FK de
-- PERSONAL y EMPRESA.
--------------------------------------------------------------------------------
DECLARE
    objeto_ya_existe EXCEPTION;
    PRAGMA EXCEPTION_INIT(objeto_ya_existe, -955);

    TYPE t_sentencias IS TABLE OF VARCHAR2(400);

    sentencias t_sentencias := t_sentencias(
        'CREATE INDEX FI_MOVITRAS_IX_DOCU ON FI_MOVITRAS (MOTRDREM, MOTRDRTD, MOTRDRND)',
        'CREATE INDEX FI_MOVITRAS_IX_ESTA ON FI_MOVITRAS (MOTRESTA, MOTRFECR)',
        'CREATE INDEX FI_MOVITRAS_IX_PEDE ON FI_MOVITRAS (MOTRPEDE, MOTRESTA)',
        'CREATE INDEX FI_MOVITRAS_IX_PEFU ON FI_MOVITRAS (MOTRPEFU, MOTRESTA)',
        'CREATE INDEX FI_MOVITRAS_IX_ARTI ON FI_MOVITRAS (MOTRARTI, MOTRPLAC)'
    );
BEGIN
    FOR i IN 1 .. sentencias.COUNT LOOP
        BEGIN
            EXECUTE IMMEDIATE sentencias(i);
        EXCEPTION
            WHEN objeto_ya_existe THEN NULL;
        END;
    END LOOP;
END;
/

--------------------------------------------------------------------------------
-- Llaves foraneas
--
-- Se crean en un bloque propio y posterior a la tabla: si alguna falla porque el
-- catalogo destino no esta alineado, FI_MOVITRAS ya quedo creada y solo hay que
-- reintentar esta seccion.
--
-- Se ignoran ORA-02264 (nombre de constraint ya usado) y ORA-02275 (ya existe
-- una FK equivalente sobre las mismas columnas) para mantener la idempotencia.
--------------------------------------------------------------------------------
DECLARE
    nombre_ya_usado EXCEPTION;
    PRAGMA EXCEPTION_INIT(nombre_ya_usado, -2264);

    fk_ya_existe EXCEPTION;
    PRAGMA EXCEPTION_INIT(fk_ya_existe, -2275);

    TYPE t_sentencias IS TABLE OF VARCHAR2(400);

    sentencias t_sentencias := t_sentencias(
        'ALTER TABLE FI_MOVITRAS ADD CONSTRAINT FI_MOVITRAS_REQUSUMI_FK
             FOREIGN KEY (MOTRDREM, MOTRDRTD, MOTRDRND)
             REFERENCES REQUSUMI (RESUTDEM, RESUTIDO, RESUNUME)',
        'ALTER TABLE FI_MOVITRAS ADD CONSTRAINT FI_MOVITRAS_EMPRESA_FK
             FOREIGN KEY (MOTRDREM) REFERENCES EMPRESA (EMPRCODI)',
        'ALTER TABLE FI_MOVITRAS ADD CONSTRAINT FI_MOVITRAS_PERSFU_FK
             FOREIGN KEY (MOTRPEFU) REFERENCES PERSONAL (PERSCODI)',
        'ALTER TABLE FI_MOVITRAS ADD CONSTRAINT FI_MOVITRAS_PERSDE_FK
             FOREIGN KEY (MOTRPEDE) REFERENCES PERSONAL (PERSCODI)'
    );
BEGIN
    FOR i IN 1 .. sentencias.COUNT LOOP
        BEGIN
            EXECUTE IMMEDIATE sentencias(i);
        EXCEPTION
            WHEN nombre_ya_usado THEN NULL;
            WHEN fk_ya_existe    THEN NULL;
        END;
    END LOOP;
END;
/

-- FK del elemento hacia ACTIFIJO: queda pendiente porque la referencia natural
-- es compuesta (ACFIARTI, ACFIPLAC) y exige una constraint UNIQUE/PK sobre ese
-- par en ACTIFIJO. Activar solo despues de confirmar que existe:
--
-- ALTER TABLE FI_MOVITRAS ADD CONSTRAINT FI_MOVITRAS_ACTIFIJO_FK
--     FOREIGN KEY (MOTRARTI, MOTRPLAC) REFERENCES ACTIFIJO (ACFIARTI, ACFIPLAC);

--------------------------------------------------------------------------------
-- Documentacion de columnas
--------------------------------------------------------------------------------
COMMENT ON TABLE FI_MOVITRAS IS 'Movimientos de traspaso de activos SIGAPP';

COMMENT ON COLUMN FI_MOVITRAS.MOTRIDMT IS 'Identificador del traspaso (FI_MOVITRAS_SEQ)';
COMMENT ON COLUMN FI_MOVITRAS.MOTRDREM IS 'Documento requisicion: empresa (REQUSUMI.RESUTDEM)';
COMMENT ON COLUMN FI_MOVITRAS.MOTRDRTD IS 'Documento requisicion: tipo de documento (REQUSUMI.RESUTIDO)';
COMMENT ON COLUMN FI_MOVITRAS.MOTRDRND IS 'Documento requisicion: numero (REQUSUMI.RESUNUME)';
COMMENT ON COLUMN FI_MOVITRAS.MOTRPEFU IS 'Persona fuente: responsable actual del elemento (PERSONAL.PERSCODI)';
COMMENT ON COLUMN FI_MOVITRAS.MOTRPEDE IS 'Persona destino: responsable que recibe (PERSONAL.PERSCODI)';
COMMENT ON COLUMN FI_MOVITRAS.MOTRARTI IS 'Elemento / articulo trasladado (ACTIFIJO.ACFIARTI)';
COMMENT ON COLUMN FI_MOVITRAS.MOTRPLAC IS 'Placa del elemento (ACTIFIJO.ACFIPLAC)';
COMMENT ON COLUMN FI_MOVITRAS.MOTRESTA IS 'Estado: pe pendiente, ap aprobado, na rechazado, af aceptado fuente, ad aceptado destino, re recibido';
COMMENT ON COLUMN FI_MOVITRAS.MOTROBSE IS 'Observacion / motivo del traspaso';
COMMENT ON COLUMN FI_MOVITRAS.MOTRMORE IS 'Motivo de rechazo';

COMMENT ON COLUMN FI_MOVITRAS.MOTRFECR IS 'Fecha de creacion del tramite (dato de negocio)';
COMMENT ON COLUMN FI_MOVITRAS.MOTRUSCR IS 'Usuario que crea el tramite';
COMMENT ON COLUMN FI_MOVITRAS.MOTRFEAP IS 'Fecha de aprobacion';
COMMENT ON COLUMN FI_MOVITRAS.MOTRUSAP IS 'Usuario que aprueba';
COMMENT ON COLUMN FI_MOVITRAS.MOTRFAFU IS 'Fecha de aceptacion de la persona fuente';
COMMENT ON COLUMN FI_MOVITRAS.MOTRFADE IS 'Fecha de aceptacion de la persona destino';
COMMENT ON COLUMN FI_MOVITRAS.MOTRFERE IS 'Fecha en que el destino recibe fisicamente el elemento';
COMMENT ON COLUMN FI_MOVITRAS.MOTRFIFU IS 'Firma de la persona fuente (imagen PNG/JPEG, bytes)';
COMMENT ON COLUMN FI_MOVITRAS.MOTRFIDE IS 'Firma de la persona destino (imagen PNG/JPEG, bytes)';

COMMENT ON COLUMN FI_MOVITRAS.MOTRCRAT IS 'Auditoria: instante de insercion del registro';
COMMENT ON COLUMN FI_MOVITRAS.MOTRCRBY IS 'Auditoria: usuario/proceso que inserto el registro';
COMMENT ON COLUMN FI_MOVITRAS.MOTRUPAT IS 'Auditoria: instante de la ultima modificacion';
COMMENT ON COLUMN FI_MOVITRAS.MOTRUPBY IS 'Auditoria: usuario/proceso de la ultima modificacion';
