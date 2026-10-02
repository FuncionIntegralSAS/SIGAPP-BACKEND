--------------------------------------------------------------------------------
-- Validacion: el usuario de base de datos del backend ve todos los objetos
--
-- SOLO LEE. No crea, no modifica y no concede nada: se puede ejecutar en
-- cualquier ambiente, incluido productivo.
--
-- Ejecutar conectado con el usuario con que se conecta el backend
-- (DB_USERNAME), que es el usuario de interfaz del ERP: ARQGUI<empresa>.
--
-- Por que hace falta. El backend nombra los objetos SIN esquema, con el nombre
-- con que los ve el usuario de interfaz:
--
--     SRF_<tabla>   tablas y paquetes del ERP que viven en el esquema SRF
--                   (SRF_BODEGA, SRF_REQUSUMI, SRF_PKGCONTARBO...)
--     FPL_ROLEFORM  la unica tabla que se lee del FPL (sinonimo publico)
--     FI_*, SEQ_*,  objetos propios de los modulos, por su nombre; los crean
--     PKG_FI_*      los scripts de sinonimos de cada modulo
--
-- Si un sinonimo falta, el modulo que lo usa falla con ORA-00942. Si apunta a
-- otro esquema (hay tablas con el mismo nombre en el SRF y en el FPL), la
-- consulta funciona y devuelve datos de otro modulo, que es peor.
--
-- Que comprueba, por cada objeto que usa el backend:
--
--     RESUELVE A   a que objeto real llega ese nombre para este usuario, y si
--                  es el esperado
--     LECTURA      si de verdad puede leerlo (SELECT ... WHERE 1 = 0)
--     PERMISOS     INSERT / UPDATE / EXECUTE que el backend necesita ademas de
--                  leer. Se consultan en el diccionario: NO se ejecuta ningun
--                  DML ni se consume ninguna secuencia
--
-- Si se anade al backend una consulta sobre un objeto nuevo, hay que anadirlo
-- a la lista de abajo con el nombre que use la consulta.
--------------------------------------------------------------------------------

--------------------------------------------------------------------------------
-- Esquemas de la empresa. Es lo unico a tocar.
--------------------------------------------------------------------------------
DEFINE v_srf = 'SRFPNACUA'
DEFINE v_fpl = 'FPLPNACUA'

SET PAGESIZE 200 LINESIZE 200 TRIMSPOOL ON FEEDBACK OFF VERIFY OFF TAB OFF
COLUMN resultado FORMAT A195
VARIABLE rc REFCURSOR

DECLARE
   sb_srf   CONSTANT VARCHAR2(128) := UPPER('&v_srf');
   sb_fpl   CONSTANT VARCHAR2(128) := UPPER('&v_fpl');

   TYPE t_lista IS TABLE OF VARCHAR2(100);
   -- nombre que usa el backend : esquema.objeto esperado : tipo (T tabla, S secuencia, P paquete) : privilegios ademas de leer
   objetos t_lista := t_lista(
      'SRF_ACTIFIJO:'    || sb_srf || '.ACTIFIJO:T:',
      'SRF_ARTICULO:'    || sb_srf || '.ARTICULO:T:',
      'SRF_ASPLMORS:'    || sb_srf || '.ASPLMORS:T:',
      'SRF_BODEGA:'      || sb_srf || '.BODEGA:T:',
      'SRF_CONTARBO:'    || sb_srf || '.CONTARBO:T:UPDATE',
      'SRF_EMPRESA:'     || sb_srf || '.EMPRESA:T:',
      'SRF_EQUIVALE:'    || sb_srf || '.EQUIVALE:T:',
      'SRF_MOVIRESU:'    || sb_srf || '.MOVIRESU:T:',
      'SRF_PARAOPER:'    || sb_srf || '.PARAOPER:T:',
      'SRF_PERSONAL:'    || sb_srf || '.PERSONAL:T:',
      'SRF_REQUSUMI:'    || sb_srf || '.REQUSUMI:T:',
      'SRF_TIPODOCU:'    || sb_srf || '.TIPODOCU:T:',
      'SRF_USUABADA:'    || sb_srf || '.USUABADA:T:',
      'FPL_ROLEFORM:'    || sb_fpl || '.ROLEFORM:T:',
      'FI_COFIARAS:'     || sb_srf || '.FI_COFIARAS:T:INSERT,UPDATE',
      'FI_COFIUSCO:'     || sb_srf || '.FI_COFIUSCO:T:INSERT,UPDATE',
      'FI_MOTRFIRM:'     || sb_srf || '.FI_MOTRFIRM:T:',
      'FI_MOVITRAS:'     || sb_srf || '.FI_MOVITRAS:T:',
      'FI_REQUFIRM:'     || sb_srf || '.FI_REQUFIRM:T:',
      'SEQ_COFIARAS:'    || sb_srf || '.SEQ_COFIARAS:S:SELECT',
      'SEQ_COFIASIG:'    || sb_srf || '.SEQ_COFIASIG:S:SELECT',
      'SEQ_COFIUSCO:'    || sb_srf || '.SEQ_COFIUSCO:S:SELECT',
      'SRF_PKGCONTARBO:' || sb_srf || '.PKGCONTARBO:P:EXECUTE',
      'SRF_PKGENCRIPTAR:'|| sb_srf || '.PKGENCRIPTAR:P:EXECUTE',
      'PKG_FI_MOVITRAS:' || sb_srf || '.PKG_FI_MOVITRAS:P:EXECUTE',
      'PKG_FI_REQUISICION:' || sb_srf || '.PKG_FI_REQUISICION:P:EXECUTE');

   salida       sys.odcivarchar2list := sys.odcivarchar2list();
   sb_nombre    VARCHAR2(40);
   sb_esperado  VARCHAR2(80);
   sb_tipo      VARCHAR2(1);
   sb_privs     VARCHAR2(40);
   sb_priv      VARCHAR2(20);
   sb_real      VARCHAR2(80);
   sb_resuelve  VARCHAR2(90);
   sb_lectura   VARCHAR2(30);
   sb_faltan    VARCHAR2(60);
   nu_n         NUMBER;
   nu_malos     NUMBER := 0;

   PROCEDURE anota(sb_linea IN VARCHAR2) IS
   BEGIN
      salida.EXTEND;
      salida(salida.COUNT) := sb_linea;
   END;

   -- El dueno lo tiene todo. Para los demas: privilegio ANY, o concesion directa,
   -- a PUBLIC o a un rol activo en la sesion.
   FUNCTION tiene(sb_dueno IN VARCHAR2, sb_obj IN VARCHAR2, sb_clase IN VARCHAR2, sb_privilegio IN VARCHAR2)
      RETURN BOOLEAN IS
      sb_any   VARCHAR2(40);
      nu_c     NUMBER;
   BEGIN
      IF USER = sb_dueno THEN
         RETURN TRUE;
      END IF;
      sb_any := CASE sb_clase WHEN 'T' THEN sb_privilegio || ' ANY TABLE'
                              WHEN 'S' THEN 'SELECT ANY SEQUENCE'
                              ELSE 'EXECUTE ANY PROCEDURE' END;
      SELECT COUNT(*) INTO nu_c FROM session_privs WHERE privilege = sb_any;
      IF nu_c > 0 THEN
         RETURN TRUE;
      END IF;
      SELECT COUNT(*) INTO nu_c
        FROM all_tab_privs p
       WHERE p.table_schema = sb_dueno
         AND p.table_name   = sb_obj
         AND p.privilege    = sb_privilegio
         AND (p.grantee IN (USER, 'PUBLIC') OR p.grantee IN (SELECT role FROM session_roles));
      RETURN nu_c > 0;
   END;
BEGIN
   anota('Usuario: ' || USER || '     SRF: ' || sb_srf || '     FPL: ' || sb_fpl);
   anota(' ');
   anota(RPAD('NOMBRE EN EL BACKEND',22) || RPAD('RESUELVE A',58) || RPAD('LECTURA',22) || 'PERMISOS');
   anota(RPAD('-',21,'-') || ' ' || RPAD('-',57,'-') || ' ' || RPAD('-',21,'-') || ' ' || RPAD('-',30,'-'));

   FOR i IN 1 .. objetos.COUNT LOOP
      sb_nombre   := REGEXP_SUBSTR(objetos(i), '[^:]+', 1, 1);
      sb_esperado := REGEXP_SUBSTR(objetos(i), '[^:]+', 1, 2);
      sb_tipo     := REGEXP_SUBSTR(objetos(i), '[^:]+', 1, 3);
      sb_privs    := REGEXP_SUBSTR(objetos(i), '[^:]+', 1, 4);

      -- A que llega el nombre: objeto propio, sinonimo propio o sinonimo publico,
      -- en ese orden, que es el de Oracle.
      SELECT COUNT(*) INTO nu_n FROM user_objects
       WHERE object_name = sb_nombre AND object_type NOT IN ('SYNONYM', 'PACKAGE BODY', 'INDEX');
      IF nu_n > 0 THEN
         sb_real := USER || '.' || sb_nombre;
      ELSE
         BEGIN
            SELECT table_owner || '.' || table_name INTO sb_real
              FROM user_synonyms WHERE synonym_name = sb_nombre;
         EXCEPTION WHEN NO_DATA_FOUND THEN
            BEGIN
               SELECT table_owner || '.' || table_name INTO sb_real
                 FROM all_synonyms WHERE owner = 'PUBLIC' AND synonym_name = sb_nombre;
            EXCEPTION WHEN NO_DATA_FOUND THEN
               sb_real := NULL;
            END;
         END;
      END IF;

      IF sb_real IS NULL THEN
         sb_resuelve := 'NO EXISTE';
      ELSIF sb_real = sb_esperado THEN
         sb_resuelve := sb_real;
      ELSE
         sb_resuelve := 'OTRO OBJETO: ' || sb_real || ' (se esperaba ' || sb_esperado || ')';
      END IF;

      IF sb_tipo = 'T' THEN
         BEGIN
            EXECUTE IMMEDIATE 'SELECT COUNT(*) FROM ' || sb_nombre || ' WHERE 1 = 0' INTO nu_n;
            sb_lectura := 'lee';
         EXCEPTION WHEN OTHERS THEN
            sb_lectura := 'NO LEE: ORA-' || LPAD(ABS(SQLCODE), 5, '0');
         END;
      ELSE
         sb_lectura := '-';
      END IF;

      sb_faltan := NULL;
      IF sb_privs IS NOT NULL AND sb_real IS NOT NULL THEN
         FOR j IN 1 .. REGEXP_COUNT(sb_privs, '[^,]+') LOOP
            sb_priv := REGEXP_SUBSTR(sb_privs, '[^,]+', 1, j);
            IF NOT tiene(SUBSTR(sb_esperado, 1, INSTR(sb_esperado, '.') - 1),
                         SUBSTR(sb_esperado, INSTR(sb_esperado, '.') + 1), sb_tipo, sb_priv) THEN
               sb_faltan := sb_faltan || CASE WHEN sb_faltan IS NOT NULL THEN ',' END || sb_priv;
            END IF;
         END LOOP;
      END IF;

      IF sb_real IS NULL OR sb_real <> sb_esperado OR sb_lectura LIKE 'NO LEE%' OR sb_faltan IS NOT NULL THEN
         nu_malos := nu_malos + 1;
      END IF;

      anota(RPAD(sb_nombre,22) || RPAD(sb_resuelve,58) || RPAD(sb_lectura,22)
            || CASE WHEN sb_privs IS NULL THEN '-'
                    WHEN sb_real IS NULL THEN '?'
                    WHEN sb_faltan IS NULL THEN 'ok (' || sb_privs || ')'
                    ELSE 'FALTA: ' || sb_faltan END);
   END LOOP;

   anota(' ');
   IF nu_malos = 0 THEN
      anota('RESULTADO: ' || USER || ' puede usar los ' || objetos.COUNT || ' objetos del backend.');
   ELSE
      anota('RESULTADO: ' || nu_malos || ' de ' || objetos.COUNT
            || ' objetos NO estan bien para ' || USER || '. Revisar los scripts de sinonimos de cada modulo.');
   END IF;

   OPEN :rc FOR SELECT column_value AS resultado FROM TABLE(salida);
END;
/

PRINT rc
