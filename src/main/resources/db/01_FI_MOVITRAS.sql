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
-- UN TRAMITE, VARIOS ARTICULOS
--
-- La tabla guarda UNA FILA POR ARTICULO. Lo que las agrupa es MOTRNUTR, el
-- numero de tramite, que sale de FI_TRASPASO_SEQ y es identico en todas las
-- lineas del mismo traspaso. MOTRIDMT sigue siendo la clave tecnica de la fila
-- y no se usa como identificador de negocio: el endpoint, las firmas y el
-- paquete trabajan siempre con MOTRNUTR.
--
--     REQUSUMI  1 ---- N  FI_MOVITRAS      (una linea por activo)
--     MOTRNUTR  1 ---- 2  FI_MOTRFIRM      (firma fuente y firma destino)
--
-- Consecuencia a tener presente: de MOTRDREM a MOTRFERE el contenido se repite
-- en las N lineas. Solo se mantiene coherente si TODO UPDATE filtra por
-- MOTRNUTR y nunca por MOTRIDMT. El paquete lo respeta; cualquier DML manual
-- tambien debe hacerlo.
--
-- FIRMAS
--
-- No estan aqui: viven en FI_MOTRFIRM (02_FI_MOTRFIRM.sql), una sola vez por
-- tramite en vez de repetidas en cada linea. Por eso esta tabla ya no tiene
-- MOTRFIFU, MOTRFIDE, MOTRFAFU ni MOTRFADE.
--
-- El script es idempotente: cada objeto se crea dentro de un bloque PL/SQL que
-- ignora los errores de "ya existe", de modo que puede reejecutarse sin riesgo.
--------------------------------------------------------------------------------

--------------------------------------------------------------------------------
-- Tabla
--
-- MOTRESTA solo admite cuatro estados. af (aceptado fuente) y ad (aceptado
-- destino) desaparecieron: al no haber orden entre las firmas, el avance de
-- firma son dos booleanos independientes y una sola columna no puede
-- representarlos. Quien firmo y cuando se consulta en FI_MOTRFIRM.
--
-- MOTRPLAC es NOT NULL con valor '.' para los elementos sin placa, igual que
-- ACTIFIJO. Un NULL no deduplica en un indice unico, y sin eso el mismo activo
-- sin placa podria cargarse dos veces en el mismo tramite. El DEFAULT solo
-- actua cuando la columna se omite, asi que quien inserte debe enviar
-- NVL(placa,'.').
--------------------------------------------------------------------------------
DECLARE
    objeto_ya_existe EXCEPTION;
    PRAGMA EXCEPTION_INIT(objeto_ya_existe, -955);
BEGIN
    EXECUTE IMMEDIATE q'[
        CREATE TABLE FI_MOVITRAS (
            MOTRIDMT NUMBER(12)      NOT NULL,
            MOTRNUTR NUMBER(12)      NOT NULL,
            MOTRDREM VARCHAR2(6)     NOT NULL,
            MOTRDRTD VARCHAR2(4)     NOT NULL,
            MOTRDRND NUMBER(9,0)     NOT NULL,
            MOTRPEFU VARCHAR2(20)    NOT NULL,
            MOTRPEDE VARCHAR2(20)    NOT NULL,
            MOTRARTI VARCHAR2(20)    NOT NULL,
            MOTRPLAC VARCHAR2(20)    DEFAULT '.' NOT NULL,
            MOTRESTA VARCHAR2(2)     DEFAULT 'pe' NOT NULL,
            MOTROBSE VARCHAR2(500),
            MOTRMORE VARCHAR2(500),
            MOTRFECR TIMESTAMP       DEFAULT SYSTIMESTAMP NOT NULL,
            MOTRUSCR VARCHAR2(30)    NOT NULL,
            MOTRFEAP TIMESTAMP,
            MOTRUSAP VARCHAR2(30),
            MOTRFERE TIMESTAMP,
            MOTRCRAT TIMESTAMP       DEFAULT SYSTIMESTAMP NOT NULL,
            MOTRCRBY VARCHAR2(30),
            MOTRUPAT TIMESTAMP,
            MOTRUPBY VARCHAR2(30),
            CONSTRAINT FI_MOVITRAS_PK PRIMARY KEY (MOTRIDMT),
            CONSTRAINT FI_MOVITRAS_UK_LINE UNIQUE (MOTRNUTR, MOTRARTI, MOTRPLAC),
            CONSTRAINT FI_MOVITRAS_CK_ESTA
                CHECK (MOTRESTA IN ('pe','ap','na','re'))
        )
    ]';
EXCEPTION
    WHEN objeto_ya_existe THEN NULL;
END;
/

--------------------------------------------------------------------------------
-- Secuencias
--
-- FI_MOVITRAS_SEQ  ->  MOTRIDMT, un valor por linea (por articulo)
-- FI_TRASPASO_SEQ  ->  MOTRNUTR, un valor por tramite (compartido por sus lineas)
--
-- Son dos secuencias distintas a proposito: si se reutilizara una sola, los
-- numeros de tramite quedarian salteados y dejarian de ser legibles para el
-- usuario, que es justamente para lo que sirve MOTRNUTR.
--------------------------------------------------------------------------------
DECLARE
    objeto_ya_existe EXCEPTION;
    PRAGMA EXCEPTION_INIT(objeto_ya_existe, -955);

    TYPE t_sentencias IS TABLE OF VARCHAR2(400);

    sentencias t_sentencias := t_sentencias(
        'CREATE SEQUENCE FI_MOVITRAS_SEQ START WITH 1 INCREMENT BY 1 NOCACHE NOCYCLE',
        'CREATE SEQUENCE FI_TRASPASO_SEQ START WITH 1 INCREMENT BY 1 NOCACHE NOCYCLE'
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
-- Indices
--
-- Cubren los accesos de la bandeja (estado, persona destino, persona fuente) y
-- el historial de un activo (elemento + placa).
--
-- NO hay indice suelto sobre MOTRNUTR: la constraint FI_MOVITRAS_UK_LINE ya crea
-- uno unico con MOTRNUTR como primera columna, y toda consulta por numero de
-- tramite lo aprovecha. Crear otro seria mantener dos estructuras para lo mismo.
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
COMMENT ON TABLE FI_MOVITRAS IS 'Movimientos de traspaso de activos SIGAPP, una fila por articulo';

COMMENT ON COLUMN FI_MOVITRAS.MOTRIDMT IS 'Identificador tecnico de la linea (FI_MOVITRAS_SEQ)';
COMMENT ON COLUMN FI_MOVITRAS.MOTRNUTR IS 'Numero de tramite (FI_TRASPASO_SEQ): agrupa las N lineas de un traspaso';
COMMENT ON COLUMN FI_MOVITRAS.MOTRDREM IS 'Documento requisicion: empresa (REQUSUMI.RESUTDEM)';
COMMENT ON COLUMN FI_MOVITRAS.MOTRDRTD IS 'Documento requisicion: tipo de documento (REQUSUMI.RESUTIDO)';
COMMENT ON COLUMN FI_MOVITRAS.MOTRDRND IS 'Documento requisicion: numero (REQUSUMI.RESUNUME)';
COMMENT ON COLUMN FI_MOVITRAS.MOTRPEFU IS 'Persona fuente: responsable actual del elemento (PERSONAL.PERSCODI)';
COMMENT ON COLUMN FI_MOVITRAS.MOTRPEDE IS 'Persona destino: responsable que recibe (PERSONAL.PERSCODI)';
COMMENT ON COLUMN FI_MOVITRAS.MOTRARTI IS 'Articulo trasladado en esta linea (ACTIFIJO.ACFIARTI)';
COMMENT ON COLUMN FI_MOVITRAS.MOTRPLAC IS 'Placa del elemento (ACTIFIJO.ACFIPLAC). Punto cuando no tiene';
COMMENT ON COLUMN FI_MOVITRAS.MOTRESTA IS 'Estado: pe pendiente, ap aprobado, na rechazado, re recibido. Las firmas se consultan en FI_MOTRFIRM';
COMMENT ON COLUMN FI_MOVITRAS.MOTROBSE IS 'Observacion / motivo del traspaso';
COMMENT ON COLUMN FI_MOVITRAS.MOTRMORE IS 'Motivo de rechazo';

COMMENT ON COLUMN FI_MOVITRAS.MOTRFECR IS 'Fecha de creacion del tramite (dato de negocio)';
COMMENT ON COLUMN FI_MOVITRAS.MOTRUSCR IS 'Usuario que crea el tramite';
COMMENT ON COLUMN FI_MOVITRAS.MOTRFEAP IS 'Fecha de aprobacion';
COMMENT ON COLUMN FI_MOVITRAS.MOTRUSAP IS 'Usuario que aprueba';
COMMENT ON COLUMN FI_MOVITRAS.MOTRFERE IS 'Fecha en que el destino recibe fisicamente los elementos';

COMMENT ON COLUMN FI_MOVITRAS.MOTRCRAT IS 'Auditoria: instante de insercion del registro';
COMMENT ON COLUMN FI_MOVITRAS.MOTRCRBY IS 'Auditoria: usuario/proceso que inserto el registro';
COMMENT ON COLUMN FI_MOVITRAS.MOTRUPAT IS 'Auditoria: instante de la ultima modificacion';
COMMENT ON COLUMN FI_MOVITRAS.MOTRUPBY IS 'Auditoria: usuario/proceso de la ultima modificacion';
