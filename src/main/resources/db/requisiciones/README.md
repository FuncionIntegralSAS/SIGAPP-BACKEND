# Entregable de base de datos — Módulo de requisiciones

Objetos que hay que aplicar para que funcione `/api/v1/requisiciones`. Van en carpeta
propia para no mezclarse con los seis scripts del módulo de traspasos, que viven un nivel
más arriba y son independientes de estos.

**Este entregable no modifica ningún objeto existente.** Crea una tabla y un paquete
nuevos, concede permisos sobre ellos y crea sus sinónimos. Nada más. En particular **no da
de alta parámetros en `PARAOPER`**: los que el módulo necesita ya existen en el ERP y solo
se leen.

---

## Orden de ejecución

Se aplican a mano, con *sqlplus* o SQL Developer, conectado como **`SRFPNACUA`**. No hay
Flyway ni Liquibase, y `spring.jpa.hibernate.ddl-auto=none`: la aplicación no crea nada por
su cuenta.

| # | Archivo | Qué crea | Reejecutable |
|---|---|---|---|
| 01 | `01_FI_REQUFIRM.sql` | Tabla `FI_REQUFIRM`, índice, FK a `REQUSUMI`, comments | Sí |
| 02 | `02_PKG_FI_REQUISICION.pks` | Especificación del paquete | Sí |
| 03 | `03_PKG_FI_REQUISICION.pkb` | Cuerpo del paquete | Sí |
| 04 | `04_SINONIMOS.sql` | GRANT y sinónimos para `ARQGUIPNACUA` | Sí |

```bash
sqlplus SRFPNACUA/clave@servicio @01_FI_REQUFIRM.sql
sqlplus SRFPNACUA/clave@servicio @02_PKG_FI_REQUISICION.pks
sqlplus SRFPNACUA/clave@servicio @03_PKG_FI_REQUISICION.pkb
sqlplus SRFPNACUA/clave@servicio @04_SINONIMOS.sql
```

El 03 no compila sin el 01: el cuerpo referencia `FI_REQUFIRM.REFIFEFI%TYPE`.

⚠️ **Los `.pks` y `.pkb` necesitan un `/` solo en su última línea.** Sin él *sqlplus* no
ejecuta el `CREATE OR REPLACE` y **no avisa de nada**: parece que compiló y en realidad no
hizo nada. Verificar siempre después.

---

## Por qué en `SRFPNACUA` y no en otro esquema

El paquete referencia sin calificar `REQUSUMI`, `MOVIRESU`, `ASPLMORS`, `EQUIVALE`,
`PARAOPER` y el paquete `PKGFNMOVIRESU`. Todos resuelven en el esquema de
recursos físicos. Compilarlo en otro sitio da `ORA-00942` aunque existan los sinónimos,
porque un sinónimo resuelve el nombre pero no concede el acceso.

---

## Dependencias del ERP

Objetos que ya deben existir. Ninguno se modifica.

**Tablas:** `REQUSUMI`, `MOVIRESU`, `ASPLMORS`, `EQUIVALE`, `PARAOPER`, `TIPODOCU`.

**Paquetes:** `pkgerror`, `pkgerrointe`, `pkgfnPersonal` y **`pkgfnmoviresu`**, cuyo
`proSalida` genera el documento de salida. El módulo lo invoca, no lo reimplementa.
Y la función `funBuscPara`.

**Parámetros que se leen** (todos existen ya): `ESTAINIC` = `in`, `ESTAAPRO` = `ap`,
`ESTAENTR` = `en`, `ESTAANEN` = `ae`, `ESTAREGI` = `rg`, `ESTAACTI` = `ac`.

---

## La equivalencia que hay que tener cargada

El tipo de documento de la **salida** se resuelve por equivalencia a partir del tipo de la
**requisición**:

```sql
SELECT equicoeq FROM equivale
 WHERE equicleq = 'COBO' AND equitipo = 'REQU'
   AND equicodi = <tipo de la requisicion> AND equiesta = 'ac';
```

Esa fila **no la crea este entregable**: la carga el área funcional. Al 2026-09-11 están
cargadas **10 filas en `SRFPNACUA.EQUIVALE`**, las de las requisiciones de agua:

```
2601 → 261    2602 → 262    2603 → 263    2604 → 264    2605 → 265
2606 → 266    2607 → 267    2608 → 268    2609 → 269    2610 → 260
```

Los demás tipos (`199` consumo, `201` administrativo, `2523` lab. calidad, `2574`
mantenimiento…) **no tienen equivalencia y por tanto no se gestionan todavía**. Si hacen
falta, hay que pedir el alta.

Tiene dos consecuencias que conviene conocer antes de la primera prueba:

- **Es también el filtro de tipos gestionables.** Un tipo sin equivalencia no aparece en
  `GET /tipos`, no sale en la bandeja y no se puede ni aprobar. Así es como quedan fuera las
  requisiciones de traslado, que son integración con el módulo comercial.
- **Si no está cargada, todo sale vacío.** No es un fallo de la aplicación. El paquete falla
  con un mensaje que nombra la clase, el tipo y el código que hay que dar de alta.

⚠️ **Hay dos tablas `EQUIVALE` en la base**: `SRFPNACUA.EQUIVALE` y `FPLPNACUA.EQUIVALE`, y
el sinónimo de `ARQGUIPNACUA` apunta a la segunda. **La carga tiene que estar en
`SRFPNACUA`**, que es donde el paquete la busca — ahí es donde está hoy, y `FPLPNACUA` tiene
cero filas `REQU`. Si algún día la bandeja sale vacía sin motivo, comprobar esto primero:

```sql
SELECT 'SRF' origen, equicodi, equicoeq, equiesta FROM srfpnacua.equivale WHERE equitipo = 'REQU'
UNION ALL
SELECT 'FPL',        equicodi, equicoeq, equiesta FROM fplpnacua.equivale WHERE equitipo = 'REQU';
```

Un falso positivo que despista al diagnosticar: `COBO/BOCC` tiene filas con códigos `2523`,
`2574`, `2601`… que parecen tipos de requisición. No lo son — ahí `EQUICODI` es una bodega y
`EQUICOEQ` un centro de costo. Bodegas y tipos de documento comparten numeración.

---

## Verificación después de aplicar

```sql
-- 1. Nada inválido
SELECT object_name, object_type, status FROM user_objects
 WHERE object_name IN ('FI_REQUFIRM','PKG_FI_REQUISICION');
SELECT line, position, text FROM user_errors WHERE name = 'PKG_FI_REQUISICION';   -- 0 filas

-- 2. Estructura de la tabla
SELECT constraint_name, constraint_type FROM user_constraints WHERE table_name = 'FI_REQUFIRM';
-- FI_REQUFIRM_PK (P), FI_REQUFIRM_CK_POSI (C), FI_REQUFIRM_REQUSUMI_FK (R)

-- 3. Parámetros que el módulo lee
SELECT paracodi, paracova FROM paraoper
 WHERE paracodi IN ('ESTAINIC','ESTAAPRO','ESTAENTR','ESTAANEN','ESTAREGI','ESTAACTI');

-- 4. Sinónimos, como ARQGUIPNACUA
SELECT COUNT(*) FROM FI_REQUFIRM;
```

---

## Dónde queda el log, y por qué parece que no queda

⚠️ **Hay dos tablas `ERROINTE` y el sinónimo apunta a la que no es.** Verificado sobre la
BD de desarrollo el 2026-09-21:

| Objeto | Qué es |
|---|---|
| `SRFPNACUA.ERROINTE` | **Aquí escribe este módulo.** ~640.000 filas |
| `FPLPNACUA.ERROINTE` | Otra cosa, del comercial. ~750 filas |
| `ARQGUIPNACUA.ERROINTE` | **Sinónimo hacia `FPLPNACUA`**, la equivocada |

El paquete resuelve `pkgerrointe` contra `SRFPNACUA.PKGERROINTE` porque se compila en ese
esquema y el objeto propio gana sobre el sinónimo. Consultar `errointe` sin calificar desde
otro usuario mira otra tabla y no encuentra nada. **Calificar siempre el esquema.**

⚠️ **`SRFPNACUA.ERROINTE` no tiene columna de fecha**: solo `ERININTE`, `ERINUSUA` y
`ERINERRO` (`VARCHAR2(2000)`). La de `FPLPNACUA` sí tiene `ERINFESI`, de ahí la confusión.
Por eso `pro_log` antepone `SYSTIMESTAMP` al mensaje **como texto**, y por eso no se puede
ordenar cronológicamente. Ordenar por `ROWID` no es un sustituto: devuelve filas de hace
meses.

```sql
SELECT erininte, erinusua, erinerro
  FROM srfpnacua.errointe
 WHERE erininte = 'FI_REQUFIRM';     -- traspasos usa 'FI_MOVITRAS'
```

`ERINUSUA` es el usuario **Oracle** (`SRFPNACUA`), nunca el código de persona del token:
ese va dentro del texto del mensaje.

✅ **`pkgerrointe.procrear` es autónomo y hace su propio `COMMIT`** en los dos esquemas. Era
un supuesto del diseño y ahora está comprobado en el fuente: el log sobrevive al rollback de
la operación, que es justo lo que se necesita para soporte.

El script `docs/registrar-requisicion-diagnostico.sql` trae todo esto resuelto.

---

## Notas de diseño que no se ven en el DDL

**La cabecera nunca se toca.** `REQUSUMI.RESUESTA` se queda en `ac` siempre. El estado de la
requisición es una función de sus líneas (`fun_estado`), no una columna.

**La cantidad que mueve inventario es `MORSCAEN`.** `proSalida` hace
`MOINCANT := rg.MorsCaen` y no lee `MORSCARE` en ningún momento. Por eso una recepción
parcial se aplica **bajando la entregada** antes de llamar al ERP: sale del inventario lo
que de verdad se recibió y el resto **no llega a salir**. `MORSCARE` queda como dato de
auditoría, sellado a la par.

⚠️ No es que nada lo devuelva. **`ARTIBODE.ARBOCAFI` se descuenta al REGISTRAR, no al
entregar**, por la cantidad del movimiento — comprobado el 2026-09-22 sobre
`01-2609-202501249`: las líneas llevaban días en `en` y el saldo no se movió hasta el
registro. El trigger solo toca `ARBOCASB`, que va de `MORSCASO` y `MORSCAAN`.

**Por qué no `PRO_GENERA_SALIREQU`.** Es el otro camino del ERP para lo mismo —la lógica
de la forma PSSR— y tiene un defecto comprobado sobre la BD el 2026-09-21: antes de decidir
si inserta el movimiento reasigna la cantidad con el saldo por concepto de `SALDINLA`
(`rg_moviresu_Ins.morscare := nuSaldoR`). Con saldo cero genera el `DOCUINVE`, no genera
ningún `MOVIINVE` y **aun así marca la línea `rg`**, porque su `UPDATE` está fuera del
`IF`. Requisición registrada, inventario intacto y ningún aviso. En desarrollo las 99 387
filas de `SALDINLA` tienen `SAILCASF = 0`, así que ahí no movía nada nunca.

`proSalida` trae ese bloque **comentado por el propio ERP** —*"El traslado al hacerse en la
salida, solo debe consultar el saldo fisico del concepto"*—, ata el paso a `rg` a su
bandera `boCrea`, genera la contabilidad con `pkgFnContabiliza`, escribe por la capa
`Pkgtb*` y filtra el número con `BETWEEN` en vez de `LIKE`. Es además la vía que usa
`PKG_FAPI_REQUISICION`, la API de integración del propio ERP.

⚠️ **`proSalida` hace `COMMIT` propio** cuando genera movimientos, y `ROLLBACK` en sus
manejadores. El `COMMIT` solo dispara dentro de su `IF (boCrea)`, es decir cuando ya cruzó
el punto de no retorno; si no genera nada no commitea y el rollback sigue limpiando. Por eso
**toda validación de `pro_generar_salida` va antes de la llamada**.

⚠️ **`P_sbTitr` tiene que ser `REQUSUMI.RESUSAME`** (`T` traslado, `N`/`S` no). Su cursor
exige que coincida y si no casa **no hace nada, sin error**: ni documento, ni movimiento, ni
aviso. Con el valor nulo pasaría lo mismo, así que el paquete corta antes.

**El usuario de sesión es obligatorio.** El parámetro `CONREBEQ` está activo, así que el
trigger `TRGBUPR_MOVIRESU` comprueba que quien entrega o registra sea responsable de la
bodega, y lo hace leyendo `pkgfnPersonal.getUsuario()`, una variable de paquete de la
sesión. Con un pool de conexiones no se establece sola. Si queda nula, un `NVL` del trigger
la iguala al responsable y **el control se salta en silencio**. Por eso `pro_set_contexto`
se llama al principio de toda transacción de escritura.

**Las placas se asignan solas por FIFO.** `pro_asignar_placas_fifo` elige, al entregar, las
placas más antiguas del almacén (`ACTIFIJO.ACFIFEEN`, desempate por placa) para que quien
despacha reciba la lista resuelta y la rotación no dependa de nadie. Filtra por la bodega
**fuente** —el activo no se mueve al destino hasta que se registra la salida— y excluye las
placas comprometidas en requisiciones abiertas. Si el almacenista manda placas a mano, se
respetan y el FIFO solo completa lo que falte.

**El trigger hace el trabajo pesado.** Valida cantidades, disponibilidad contra `ARTIBODE` y
placas de `ASPLMORS`, y propaga a `ARTIBODE`, `SALDINLA` y `REQUSUMI.RESUFERE`. El paquete
no replica nada de eso: si un `UPDATE` parece demasiado simple, es por esto.

**Aquí sí hay FK hacia el documento**, al contrario que en `FI_MOTRFIRM` de traspasos. Allí
era imposible (`ORA-02270`) porque el número de trámite se repetía en las N líneas. La terna
de `REQUSUMI` es su llave, así que la integridad la sostiene la base.

---

## Pendiente

**Quién entregó y quién anuló no queda registrado en el ERP.** `MOVIRESU` solo tiene columna
de usuario para el aprobador (`MORSUSAP`), y añadir una exigiría modificar una tabla del
sistema financiero. Mientras tanto esa trazabilidad va al log del ERP vía `pro_log`, con la
terna, la línea, la operación y el usuario. Si hiciera falta consultarla desde la
aplicación, habría que valorar una tabla propia.
