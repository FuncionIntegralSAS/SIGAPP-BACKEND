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
| 1 | `01_FI_MOVITRAS.sql` | Tabla `FI_MOVITRAS`, secuencias, índices y foráneas |
| 2 | `02_FI_MOTRFIRM.sql` | Tabla `FI_MOTRFIRM` (firmas), índice y foránea |
| 3 | `03_PKG_FI_MOVITRAS.pks` | Especificación del paquete |
| 4 | `04_PKG_FI_MOVITRAS.pkb` | Cuerpo del paquete |
| 5 | `05_SINONIMOS.sql` | Permisos y sinónimos para `ARQGUIPNACUA` |
| 6 | `06_PARAOPER.sql` | Parámetros de configuración en `PARAOPER` |

Del `01` al `05` son reejecutables: `01` y `02` envuelven cada objeto en un
bloque PL/SQL que ignora los errores de "ya existe", el paquete usa
`CREATE OR REPLACE`, y `GRANT` / `CREATE OR REPLACE SYNONYM` son idempotentes por
naturaleza. El `06` **no**: son `INSERT`, se ejecuta una sola vez y después se
corrige con `UPDATE`.

⚠️ El `04` no compila si el `01` y el `02` no se aplicaron antes: el cuerpo del
paquete referencia `FI_MOVITRAS.MOTRNUTR` y la tabla `FI_MOTRFIRM`. Y recordar el
`/` final de los archivos de paquete — sin él *sqlplus* no ejecuta el
`CREATE OR REPLACE` y no avisa de nada.

```bash
sqlplus SRFPNACUA/clave@servicio @01_FI_MOVITRAS.sql
sqlplus SRFPNACUA/clave@servicio @02_FI_MOTRFIRM.sql
sqlplus SRFPNACUA/clave@servicio @03_PKG_FI_MOVITRAS.pks
sqlplus SRFPNACUA/clave@servicio @04_PKG_FI_MOVITRAS.pkb
sqlplus SRFPNACUA/clave@servicio @05_SINONIMOS.sql
sqlplus SRFPNACUA/clave@servicio @06_PARAOPER.sql
```

Antes de ejecutar el `06` hay que reemplazar los valores marcados con
`>>> DEFINIR <<<` en el bloque `DEFINE` de la cabecera.

## Esquemas

⚠️ **El paquete `PKG_FI_MOVITRAS` se compila en el esquema de recursos físicos
`SRF` (`SRFPNACUA`), no en `ARQGUIPNACUA`.** Es el esquema dueño de
`FI_MOVITRAS` y con el que se conecta el backend; ahí es donde resuelven sin
calificar las tablas del ERP que usa el paquete (`REQUSUMI`, `ACTIFIJO`,
`PARAOPER`, `TIMOOPER`, `TIPODOCU`). Compilarlo en otro esquema deja el cuerpo
inválido con `PL/SQL: ORA-00942`.

Antes de aplicar `03` y `04`, confirmar la conexión:

```sql
SELECT USER FROM DUAL;   -- debe devolver SRFPNACUA
```

**`ARQGUIPNACUA`** alcanza los objetos por sinónimo privado (`05_SINONIMOS.sql`);
no tiene copia propia del paquete.

`05_SINONIMOS.sql` trae dos rutas para crear el sinónimo; hay que ejecutar una
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
  trámite ni tapar el error real. `procrear` corre en transacción autónoma con
  `COMMIT` propio —comprobado en el fuente el 2026-09-21— así que el registro
  sobrevive al rollback de la app.

  Las filas caen en **`SRFPNACUA.ERROINTE`**, no en la `ERROINTE` a la que
  apunta el sinónimo, y esa tabla **no tiene columna de fecha**. El detalle está
  en `requisiciones/README.md`, sección «Dónde queda el log»; aplica igual a
  este módulo cambiando el filtro a `ERININTE = 'FI_MOVITRAS'`.
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

## Un trámite, varios artículos

`FI_MOVITRAS` guarda **una fila por artículo**. Lo que las agrupa es `MOTRNUTR`,
el número de trámite, que sale de `FI_TRASPASO_SEQ` y es idéntico en todas las
líneas del mismo traspaso:

```
REQUSUMI  1 ──── N  FI_MOVITRAS      una línea por activo
MOTRNUTR  1 ──── 2  FI_MOTRFIRM      firma fuente y firma destino
```

`MOTRIDMT` sigue siendo la clave técnica de la fila, pero **no** es el
identificador de negocio: el endpoint, las firmas y el paquete trabajan siempre
con `MOTRNUTR`. Son dos secuencias distintas a propósito — reutilizar una sola
dejaría los números de trámite salteados y dejarían de ser legibles.

La unicidad de la línea es `(MOTRNUTR, MOTRARTI, MOTRPLAC)`, no solo el artículo:
un mismo `ACFIARTI` puede aparecer varias veces con placas distintas, porque son
elementos físicos diferentes. Por eso `MOTRPLAC` es `NOT NULL` con `'.'` para los
que no tienen placa, igual que `ACTIFIJO` — un `NULL` no deduplica en un índice
único.

### Un activo, un traspaso abierto

`FI_MOVITRAS_UK_LINE` solo impide repetir un elemento **dentro** del mismo
trámite. Para que no aparezca en dos traspasos abiertos a la vez hay un índice
único **parcial**:

```sql
CREATE UNIQUE INDEX FI_MOVITRAS_UK_ABIERTO ON FI_MOVITRAS (
    CASE WHEN MOTRESTA IN ('pe','ap') THEN MOTRARTI END,
    CASE WHEN MOTRESTA IN ('pe','ap') THEN MOTRPLAC END);
```

Las dos expresiones dan `NULL` cuando el trámite está cerrado (`na` o `re`), y
Oracle **no indexa las entradas con todas las columnas nulas**: los cerrados no
ocupan el índice y liberan el activo. Entre los abiertos, el par
(artículo, placa) es único.

`pro_valida_activo_libre` comprueba lo mismo antes de insertar, para responder
*"ya está en el traspaso N, que sigue abierto"* en vez de un `ORA-00001`. El
índice es la red de seguridad ante dos sesiones creando a la vez, que es la única
ventana que la validación en PL/SQL no puede cerrar.

⚠️ Si al reejecutar `01` el índice falla con `ORA-01452`, hay datos que ya violan
la regla. Localizarlos con:

```sql
SELECT motrarti, motrplac, COUNT(DISTINCT motrnutr)
  FROM fi_movitras WHERE motresta IN ('pe','ap')
 GROUP BY motrarti, motrplac HAVING COUNT(DISTINCT motrnutr) > 1;
```

⚠️ **De `MOTRDREM` a `MOTRFERE` el contenido se repite en las N líneas.** Es el
costo del modelo, y solo se mantiene coherente si **todo `UPDATE` filtra por
`MOTRNUTR`** y nunca por `MOTRIDMT`. El paquete lo respeta; cualquier DML manual
también debe hacerlo. Consulta de control para verificarlo:

```sql
SELECT motrnutr, COUNT(DISTINCT motresta)
  FROM fi_movitras
 GROUP BY motrnutr
HAVING COUNT(DISTINCT motresta) > 1;   -- debe devolver cero filas
```

Y por la misma razón los procedimientos ya no pueden hacer
`SELECT * INTO ... WHERE motridmt = :id FOR UPDATE`: filtrando por `MOTRNUTR` eso
sería `TOO_MANY_ROWS`. Usan un cursor que bloquea las N líneas y lee de paso los
datos comunes.

## Relación con el documento

Todo traspaso cuelga de un `REQUSUMI`. La llave viaja en tres columnas que
siguen la convención del ERP (`DR` = Documento Requisición, igual que
`MOSORSEM/RSTD/RSNU` en `MOVISOCO`):

| `FI_MOVITRAS` | `REQUSUMI` | |
|---------------|------------|---|
| `MOTRDREM` | `RESUTDEM` | empresa |
| `MOTRDRTD` | `RESUTIDO` | tipo de documento |
| `MOTRDRND` | `RESUNUME` | número |

## Movimiento de inventario al recibir

> **Cambio 2026-09-11 — el tercero del documento.** `DOINTERC` y `MOINTERC` se llenaban con
> `ACTIFIJO.ACFITERC`, que es el **proveedor** al que se compró el activo. Ahora llevan
> `EMPRESA.EMPRNIT`, que es lo que el ERP pone en sus propios documentos de salida y
> traslado. Con ello desapareció la validación que exigía que todos los artículos
> compartieran tercero: bloqueaba traspasos legítimos entre activos de distinto proveedor.
> Hay que **recompilar `04_PKG_FI_MOVITRAS.pkb`** en los ambientes donde ya estuviera
> aplicado.


**Regla de oro del módulo: el documento de inventario se genera al RECIBIR, nunca
al aprobar.** Aprobar autoriza el trámite; recibir es lo que mueve el activo, y
solo se puede recibir con las dos firmas registradas. Un traspaso aprobado que
nadie llega a firmar no deja rastro en `DOCUINVE` ni en `MOVIINVE`.

`pro_recibir` valida las firmas y genera el documento de inventario del ERP:

| Tabla | Qué se inserta |
|-------|----------------|
| `DOCUINVE` | Cabecera, una fila. Se emite contra la división de la persona **destino** |
| `MOVIINVE` | Detalle, **una línea por artículo** del trámite, con `MOINSECU` de 1 a N |

`pro_aprobar_rechazar` solo mueve estados: el del trámite en sus N líneas y el
del documento `REQUSUMI`. Cuando se **rechaza**, el `REQUSUMI` se anula con
`ESTAANUL` y el trámite termina ahí.

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
- **Un solo documento por trámite**: `DOCUINVE` es una fila aunque el traspaso
  mueva cuatro activos. Lo que se repite es `MOVIINVE`, recorriendo las líneas de
  `FI_MOVITRAS` del mismo `MOTRNUTR`. `MOINSECU` dejó de ser la constante `1` y
  pasó a ser el contador del bucle.
- **Todo en la transacción de `pro_recibir`**: o queda el traspaso recibido con su
  documento completo —las N líneas—, o no queda nada.
- **Campos de cabecera que salían del activo**: `RESUCEIN` en la requisición y
  `DOINTERC` en el documento de inventario se tomaban de `ACTIFIJO`. Con varios
  artículos pueden no coincidir, así que el trámite **se rechaza** al crearlo
  nombrando los artículos en conflicto, en vez de tomar el del primero y mandar el
  documento a un centro equivocado sin que nadie se entere. Se valida al agregar
  cada artículo, no al recibir, para que el error salga cuando aún se puede
  corregir.
- **Numeración**: la entrega el numerador del tipo de documento
  (`Pkgtbtidoemnu.ProSel` + `pkgDocu_General.funProxi_Nume`). Aquí **no** hay
  trigger que numere, a diferencia de `REQUSUMI`.
- **Bodega fuente** (`MOINBOFU`): `ACTIFIJO.ACFIBODE`, la bodega donde el
  inventario cree que está hoy el activo — no la bodega de la persona fuente.
  **Tiene que ser personal (`PE`)**: el trigger de `MOVIINVE` llama a
  `PKGFNTRASPASOS.protraspaso`, que rechaza el movimiento si alguna bodega es
  física. Por eso `pro_crear_solicitud` y `pro_agregar_articulo` llaman a
  `pro_valida_bodega_activo` antes de aceptar el activo, en vez de dejar que el
  error salga al recibir con las dos firmas ya puestas.
- **Cambio de responsable**: lo hace el mismo `protraspaso` del ERP al insertar
  cada `MOVIINVE` (`ACFIPERS`, `ACFIBODE`, `ACFICEIN` y los componentes de
  `REGICOMP`). Verificado el 2026-09-24 con el trámite 12.
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

## Firmas: tabla propia y `BLOB`

Las firmas no viven en `FI_MOVITRAS` sino en `FI_MOTRFIRM`, **dos filas por
trámite** creadas vacías al abrirlo:

| `MOFIPOSI` | Quién |
|---|---|
| `1` | la persona que **entrega** (fuente) |
| `2` | la persona que **recibe** (destino) |

Antes la firma se guardaba en la fila del traspaso. Con varios artículos por
trámite eso significaba repetir la misma imagen en cada línea; separarla la deja
una sola vez.

**No hay orden entre las dos.** Cualquiera puede firmar primero. Por eso el
avance de firma no se refleja en `MOTRESTA` —que se queda en `ap` mientras se
firma— sino en `MOFIFEFI`: nulo es "aún no firmó". Son dos booleanos
independientes y una sola columna de estado no puede representarlos, que es la
razón por la que `af` y `ad` desaparecieron del `CHECK`. `pro_recibir` exige las
dos posiciones firmadas.

**No hay llave foránea hacia el trámite.** `MOFINUTR` apunta a
`FI_MOVITRAS.MOTRNUTR`, que se repite en las N líneas, y Oracle exige `PRIMARY
KEY` o `UNIQUE` en la columna referenciada (`ORA-02270`). La integridad la
sostiene el paquete, que valida la existencia del trámite antes de escribir. Lo
que sí queda declarado es la PK `(MOFINUTR, MOFIPOSI)`, que impide dos firmas
para la misma posición.

`MOFIFIPE` es `BLOB` con los **bytes** de la imagen, no su base64:

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

## Pendiente

- La foránea de `FI_MOVITRAS` hacia `ACTIFIJO (ACFIARTI, ACFIPLAC)` queda
  comentada en `01_FI_MOVITRAS.sql`: exige una constraint `UNIQUE` sobre ese par
  que no está confirmada.
- `FI_MOTRFIRM` no puede llevar foránea hacia el trámite, por lo explicado en
  *Firmas*. Si alguna vez `MOTRNUTR` pasara a vivir en una tabla donde sea único,
  esa foránea se podría declarar.
- **Tope de artículos por trámite**: pendiente de fijar. Cada artículo es una
  línea de `MOVIINVE` dentro de la misma transacción de aprobación.
- `05_SINONIMOS.sql` cubre los cinco objetos propios del módulo: las dos tablas,
  las dos secuencias y el paquete. Las tablas y paquetes nuevos del
  ERP los resuelve `SRFPNACUA` directamente, que es donde compila el paquete.
