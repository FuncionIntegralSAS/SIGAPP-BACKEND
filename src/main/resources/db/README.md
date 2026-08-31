# Entregable de base de datos — Módulo de traspasos

Objetos Oracle que el backend necesita para operar el módulo de traspasos
(`/api/v1/traspasos`). Se versionan junto al código para que el entregable de
base de datos salga con el mismo tag que el backend.

**No hay ejecución automática al arranque.** Estos scripts se aplican a mano (o
desde el pipeline de BD) antes de desplegar la versión del backend que los
necesita. La aplicación mantiene `spring.jpa.hibernate.ddl-auto=none` y no lleva
Flyway ni Liquibase.

## Orden de ejecución

| # | Archivo | Contenido |
|---|---------|-----------|
| 1 | `01_FI_MOVITRAS.sql` | Tabla `FI_MOVITRAS`, secuencia, índices y foráneas |
| 2 | `02_PKG_FI_MOVITRAS.pks` | Especificación del paquete |
| 3 | `03_PKG_FI_MOVITRAS.pkb` | Cuerpo del paquete |
| 4 | `04_SINONIMOS.sql` | Permisos y sinónimos para `ARQGUIPNACUA` |
| 5 | `05_PARAOPER.sql` | Parámetros de configuración en `PARAOPER` |
| 6 | `06_MIGRA_FIRMAS_BLOB.sql` | **Solo ambientes existentes**: pasa las firmas de `CLOB` a `BLOB` |

Del `01` al `04` son reejecutables: `01` envuelve cada objeto en un bloque PL/SQL
que ignora los errores de "ya existe", el paquete usa `CREATE OR REPLACE`, y
`GRANT` / `CREATE OR REPLACE SYNONYM` son idempotentes por naturaleza. El `05`
**no**: son `INSERT`, se ejecuta una sola vez y después se corrige con `UPDATE`.

El `06` también es reejecutable, pero **no aplica a instalaciones nuevas**: solo
hace algo si la tabla ya existe con las firmas en `CLOB`. Ver *Firmas* más abajo.

```bash
sqlplus SRFPNACUA/clave@servicio @01_FI_MOVITRAS.sql
sqlplus SRFPNACUA/clave@servicio @02_PKG_FI_MOVITRAS.pks
sqlplus SRFPNACUA/clave@servicio @03_PKG_FI_MOVITRAS.pkb
sqlplus SRFPNACUA/clave@servicio @04_SINONIMOS.sql
sqlplus SRFPNACUA/clave@servicio @05_PARAOPER.sql
```

Y solo si la tabla ya existía con las firmas en `CLOB`, **antes** de recompilar el
paquete:

```bash
sqlplus SRFPNACUA/clave@servicio @06_MIGRA_FIRMAS_BLOB.sql
sqlplus SRFPNACUA/clave@servicio @02_PKG_FI_MOVITRAS.pks
sqlplus SRFPNACUA/clave@servicio @03_PKG_FI_MOVITRAS.pkb
```

Antes de ejecutar el `05` hay que reemplazar los valores marcados con
`>>> DEFINIR <<<` en el bloque `DEFINE` de la cabecera.

## Esquemas

⚠️ **El paquete `PKG_FI_MOVITRAS` se compila en el esquema de recursos físicos
`SRF` (`SRFPNACUA`), no en `ARQGUIPNACUA`.** Es el esquema dueño de
`FI_MOVITRAS` y con el que se conecta el backend; ahí es donde resuelven sin
calificar las tablas del ERP que usa el paquete (`REQUSUMI`, `ACTIFIJO`,
`PARAOPER`, `TIMOOPER`, `TIPODOCU`). Compilarlo en otro esquema deja el cuerpo
inválido con `PL/SQL: ORA-00942`.

Antes de aplicar `02` y `03`, confirmar la conexión:

```sql
SELECT USER FROM DUAL;   -- debe devolver SRFPNACUA
```

**`ARQGUIPNACUA`** alcanza los objetos por sinónimo privado (`04_SINONIMOS.sql`);
no tiene copia propia del paquete.

`04_SINONIMOS.sql` trae dos rutas para crear el sinónimo; hay que ejecutar una
sola:

- **Ruta A** (activa por defecto): se corre como `SRFPNACUA` y crea el sinónimo
  dentro del otro esquema. Requiere `CREATE ANY SYNONYM`.
- **Ruta B** (comentada): se corre conectado como `ARQGUIPNACUA` y solo requiere
  su propio `CREATE SYNONYM`.

El sinónimo resuelve el nombre pero **no** concede acceso: sin los `GRANT`,
`ARQGUIPNACUA` recibe `ORA-00942`. Y en Oracle cada verbo se concede aparte —
`GRANT SELECT` es solo lectura; escribir exige `INSERT`/`UPDATE`/`DELETE`
explícitos.

## Dependencias del ERP

El paquete no compila si falta alguno de estos objetos:

- Tablas: `REQUSUMI`, `ACTIFIJO`, `PERSONAL`, `BODEGA`, `EMPRESA`, `PARAOPER`,
  `TIMOOPER`, `TIPODOCU`, `ARTICULO`, `PERSCECO`, `TIDOEMNU`, `DOCUINVE`,
  `MOVIINVE`
- Paquetes: `Pkgerror`, `pkgerrointe`, `Pkgtbactifijo`, `Pkgtbarticulo`,
  `Pkgtbtidoemnu`, `PKGTBDOCUINVE`, `PKGTBMOVIINVE`, `pkgDocu_General`
- Funciones: `FunBuscPara`

Las cinco últimas tablas y los cinco paquetes `Pkgtb*` / `pkgDocu_General` entran
con el movimiento de inventario que genera `pro_aprobar_rechazar`.

## Manejo de errores

Todo error pasa por dos helpers privados del paquete:

- `pro_log(mensaje)` → `pkgerrointe.procrear('FI_MOVITRAS', USER, SYSTIMESTAMP || ' ' || mensaje)`.
  Si el log falla (permisos, tabla ausente) se ignora: no puede tumbar el
  trámite ni tapar el error real. Para que el registro sobreviva al rollback de
  la app, `procrear` debe correr en transacción autónoma.
- `pro_error(mensaje)` → `pro_log` + `Pkgerror.proiniterrtxt`. Los dos hacen
  falta: el primero deja rastro para soporte, el segundo es el que lleva el texto
  al `RAISE_APPLICATION_ERROR(-20008, ...)` que termina viendo el backend.

Los `WHEN OTHERS` registran `$$PLSQL_LINE` y `SQLERRM` antes de propagar.

## Parámetros a configurar antes del primer traspaso

Viven en `PARAOPER` (`PARANOPR` = clave, `PARACOVA` = valor) y el paquete los lee
con `FunBuscPara`. Las claves respetan el límite del ERP: **máximo 10 bytes** y
sin separadores, igual que `ESTAANUL`. Si alguno está sin valor, `fun_parametro`
falla con un mensaje explícito en vez de dejar que reviente el `INSERT` a
`REQUSUMI` con un `ORA-01400` ilegible.

| Clave | Para qué sirve | Columna destino |
|-------|----------------|-----------------|
| `TIMOTRIN` | Tipo de movimiento del traspaso | — (deriva `RESUTIDO`) |
| `ESTATRAS` | Estado del documento al crearlo | `RESUESTA` |
| `ESAPTRAS` | Estado del documento al aprobar | `RESUESTA` |
| `CEINTRAS` | Centro de información | `RESUCEIN` |
| `COCMTRAS` | Concepto de movimiento | `RESUCOCM` |
| `BLFUTRAS` | Bodega lógica fuente | `RESUBLFU` |
| `BLDETRAS` | Bodega lógica destino | `RESUBLDE` |
| `BODEPERS` | Tipo de bodega con que se resuelve la bodega de una división | — |

Además de esas ocho, el paquete lee tres claves que **ya existen en el sistema** y
no hay que dar de alta: se reutilizan en vez de duplicarlas con nombre propio.

| Clave | Para qué sirve | Dónde se usa |
|-------|----------------|--------------|
| `ESTAANUL` | Estado de documento anulado (lo usa `TRGBUPT_REQUSUMI`) | `RESUESTA` al rechazar |
| `ESTAACTI` | Estado activo | `DOINESTA`, numerador |
| `ESTAREGI` | Estado registrado | `MOINESTA` del movimiento recién creado |
| `CONCDEVO` | Concepto de movimiento por defecto para devolutivos | `RESUCOCM`, `MOINCOMV` |

## Tipo de documento: se deriva, no se parametriza

`pro_crear_solicitud` no lee el tipo de documento de un parámetro propio, porque
el ERP ya lo tiene amarrado al tipo de movimiento y dos parámetros
independientes se pueden configurar inconsistentes entre sí. La resolución la
hace `fun_tipodocu` en dos pasos:

1. El tipo de movimiento (`TIMOTRIN`, o el que envíe el backend en
   `p_tipo_movi`) debe tener habilitada la operación de traspaso:

   ```sql
   SELECT * FROM TIMOOPER WHERE TMOPTIMO = '04' AND TMOPOPER = 'TRASPASO';
   ```

   Sin fila, el traspaso se rechaza con *"El tipo de movimiento [04] no existe o
   no tiene configurada la operación [TRASPASO]"*.

2. Con ese mismo valor sale el tipo de documento, que es el que se setea en
   `RESUTIDO` / `MOTRDRTD`:

   ```sql
   SELECT TIDOTIDO FROM TIPODOCU WHERE TIDOTIMO = '04';
   ```

   Si no hay fila —o hay más de una— también se corta con mensaje propio.

El tipo de documento así resuelto **debe tener numerador automático**.
`pro_crear_solicitud` no envía `RESUNUME`: lo asigna el trigger
`TRGBINR_REQUSUMI` y se recupera con `RETURNING`. Si `num_automatico` devuelve
falso, el `INSERT` falla por `NOT NULL`.

## Relación con el documento

Todo traspaso cuelga de un `REQUSUMI`. La llave viaja en tres columnas que
siguen la convención del ERP (`DR` = Documento Requisición, igual que
`MOSORSEM/RSTD/RSNU` en `MOVISOCO`):

| `FI_MOVITRAS` | `REQUSUMI` | |
|---------------|------------|---|
| `MOTRDREM` | `RESUTDEM` | empresa |
| `MOTRDRTD` | `RESUTIDO` | tipo de documento |
| `MOTRDRND` | `RESUNUME` | número |

## Movimiento de inventario al aprobar

`pro_aprobar_rechazar` no se limita a cambiar estados. Cuando el trámite se
**aprueba**, además genera el documento de inventario del ERP:

| Tabla | Qué se inserta |
|-------|----------------|
| `DOCUINVE` | Cabecera, una fila. Se emite contra la división de la persona **destino** |
| `MOVIINVE` | Detalle, una sola línea (`MOINSECU = 1`): un traspaso mueve un elemento |

Cuando se **rechaza** no se inserta nada en ninguna de las dos: el `REQUSUMI` se
anula con `ESTAANUL` y el trámite termina ahí.

Decisiones de mapeo que no son obvias leyendo el código:

- **Tipo de documento**: se reutiliza `MOTRDRTD`, el mismo con que se creó la
  requisición. No hay un parámetro aparte.
- **Tipo de movimiento** (`MOINTIMO`): se deriva hacia atrás desde ese tipo de
  documento (`TIPODOCU.TIDOTIMO`), no se vuelve a leer `TIMOTRIN`. Es la consulta
  inversa a la de `fun_tipodocu`, no una revalidación: `FI_MOVITRAS` no guarda el
  tipo de movimiento y `MOVIINVE` lo exige. Releer el parámetro dejaría
  `MOININTD` y `MOINTIMO` en tipos distintos si alguien lo reconfigura mientras
  el trámite espera en `pe`, y además ignoraría el `p_tipo_movi` que el backend
  pueda haber enviado al crear.

  Esto asume que **`TIDOTIDO` es único en `TIPODOCU`**. Confirmar al desplegar:

  ```sql
  SELECT tidotido, COUNT(*) FROM TIPODOCU GROUP BY tidotido HAVING COUNT(*) > 1;
  ```
- **Numeración**: la entrega el numerador del tipo de documento
  (`Pkgtbtidoemnu.ProSel` + `pkgDocu_General.funProxi_Nume`). Aquí **no** hay
  trigger que numere, a diferencia de `REQUSUMI`.
- **Bodega fuente** (`MOINBOFU`): `ACTIFIJO.ACFIBODE`, la bodega donde el
  inventario cree que está hoy el activo — no la bodega de la persona fuente.
- **Bodegas lógicas** (`MOINBLFU` / `MOINBLDE`): la constante `'.'`, la misma que
  ya se guardó en `RESUBLFU` / `RESUBLDE`.
- **Costos** (`MOINCOST`, `MOINCPNA`, `MOINCOTO`, `MOINCOAJ`, `MOINCOTR`): todos
  salen de `ACTIFIJO.ACFICOAC`. Un traspaso cambia de responsable, no revalúa.
- **Fuente y destino** (`MOINPEFU` / `MOINPEDE`): salen del trámite, donde
  quedaron validados, no de `ACFIPERS`.
- **Trazabilidad**: `DOINDOSO` guarda `ID TRAMITE SIGAPP: <MOTRIDMT>`, que es lo
  único que permite volver del documento del ERP al trámite que lo originó.

No hay control propio de duplicados: el procedimiento solo llega a esta parte con
el trámite en `pe` y bajo el `FOR UPDATE` de la fila, así que una segunda
aprobación se corta antes por estado. Todo va en la transacción del llamador: o
queda el trámite aprobado **con** su documento, o no queda nada.

## Firmas: `BLOB`, no base64 en `CLOB`

`MOTRFIFU` y `MOTRFIDE` guardan los **bytes** de la imagen, no su base64:

- Ocupa ~33 % menos que el texto base64.
- Desde SQL Developer / Toad la firma se abre con *View as image*, que es lo que
  necesita soporte para auditar un trámite. Sobre un `CLOB` eso no existe.
- Un base64 de más de ~32 KB pasado como `String` a un parámetro `CLOB` desde
  `StoredProcedureQuery` revienta con `ORA-01460` según la versión de `ojdbc`.
  Con `BLOB` el backend enlaza un `java.sql.Blob` y el tamaño deja de importar.

El front sigue enviando y recibiendo **base64**: el backend lo valida y decodifica
antes de llamar a `pro_registrar_firma`, y lo vuelve a codificar en el detalle.
`GET /api/v1/traspasos/sign/{id}/{tipo}` devuelve los bytes tal cual, con su
`Content-Type` de imagen.

`pro_registrar_firma` solo verifica que la firma no llegue vacía
(`dbms_lob.getlength`); el formato lo valida Java, que es donde vive la regla.

### Migrar un ambiente que ya tenía las columnas en `CLOB`

Oracle no deja convertir el tipo en sitio: `ALTER TABLE ... MODIFY (MOTRFIFU BLOB)`
responde `ORA-22858`. Y `DBMS_LOB.CONVERTTOBLOB` tampoco sirve, porque copia los
bytes del **texto** base64 en vez de decodificarlo. `06_MIGRA_FIRMAS_BLOB.sql` hace
el rodeo completo:

| Paso | Qué hace |
|------|----------|
| 0 | Copia el base64 a `FI_MOVITRAS_FIBAK`, solo si hay firmas guardadas |
| 1 | Agrega `MOTRFIF2` / `MOTRFID2` (`BLOB`) |
| 2 | Recorre las filas decodificando el base64 con `UTL_ENCODE.BASE64_DECODE`, en trozos múltiplo de 4 |
| 3 | Verifica que ninguna firma se haya quedado sin convertir |
| 4 | Suelta las columnas viejas y renombra las nuevas |

Los pasos 3 y 4 van en el mismo bloque: si alguna firma no se pudo decodificar, el
script corta con la lista de `MOTRIDMT` y **no** suelta nada.

El paso 2 limpia lo que el backend antiguo guardaba sin normalizar: prefijo
`data:image/...;base64,` y saltos de línea del base64 MIME.

Después de validar el resultado, `DROP TABLE FI_MOVITRAS_FIBAK PURGE;`.

⚠️ **Orden**: el `06` va antes que el `02` y el `03`. El cuerpo actual del paquete
asigna un `CLOB` a `MOTRFIFU`; recompilarlo con `p_firma IN BLOB` contra la tabla
sin migrar lo deja inválido. El `DROP COLUMN` del paso 4 invalida el paquete de
todos modos (usa `%ROWTYPE`), y por eso se recompila justo después.

## Pendiente

- La foránea de `FI_MOVITRAS` hacia `ACTIFIJO (ACFIARTI, ACFIPLAC)` queda
  comentada en `01_FI_MOVITRAS.sql`: exige una constraint `UNIQUE` sobre ese par
  que no está confirmada.
- `pro_recibir` deja marcado el punto donde va el **cambio de responsable en
  `ACTIFIJO`** (`ACFIPERS` / `ACFIBODE`). El movimiento de inventario ya no está
  pendiente: se genera al aprobar, según la sección anterior.
- `04_SINONIMOS.sql` no cambió: solo cubre los objetos propios del módulo
  (`FI_MOVITRAS`, su secuencia y el paquete). Las tablas y paquetes nuevos del
  ERP los resuelve `SRFPNACUA` directamente, que es donde compila el paquete.
