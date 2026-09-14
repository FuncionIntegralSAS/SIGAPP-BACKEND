# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

---

# SIGAPP Backend

Backend del módulo **Conteo Físico Móvil** perteneciente al proyecto SIGAPP.

## Objetivo del proyecto

SIGAPP es una aplicación móvil desarrollada para ejecutar el proceso de inventario físico del sistema financiero.

El backend es responsable de:

- administrar usuarios temporales
- asignar artículos a usuarios
- recibir conteos realizados desde la aplicación móvil
- validar reglas de negocio
- comunicarse con Oracle Database
- consumir procedimientos PL/SQL existentes del ERP

Toda la lógica de negocio nueva debe implementarse en Spring Boot.

PL/SQL únicamente se utiliza para procesos existentes del sistema financiero.

---

## Common Commands

### Build and Run
```bash
# Build the project (using Maven wrapper)
./mvnw clean install

# Run the application
./mvnw spring-boot:run

# Run on Windows
mvnw.cmd spring-boot:run
```

### Testing
```bash
# Run all tests
./mvnw test

# Run specific test
./mvnw test -Dtest=ClassNameTest
```

### Development
```bash
# Skip tests during build
./mvnw clean install -DskipTests

# Package JAR
./mvnw package
```

The application runs on port **8082** by default.

API Documentation available at: `http://localhost:8082/swagger-ui.html`

---

## Stack tecnológico

- Java 17
- Spring Boot 3.4.2
- Spring Security + JWT (jjwt 0.13.0)
- Oracle Database (ojdbc11)
- Spring Data JPA
- Lombok
- Maven
- SpringDoc OpenAPI (Swagger)

---

## Arquitectura

El proyecto utiliza una arquitectura por capas estándar de Spring Boot:

```
Controller → Service → Service Implementation → Repository → Oracle Database
```

### Principios arquitectónicos:

- **Controllers**: Solo mapeo de HTTP requests/responses. No lógica de negocio. Delegación directa a Services.
- **Services** (interfaces): Contratos del dominio de negocio.
- **Service Implementations**: Toda la lógica de negocio reside aquí. Lanzar excepciones (no retornar ResponseEntity).
- **Repositories**: Únicamente acceso a datos. Queries JPA o ejecución de procedimientos Oracle.
- **Entities**: Mapeo de tablas Oracle usando JPA annotations.
- **DTOs**: Request/Response objects para la capa de presentación.
- **Mappers**: Conversión entre Entities y DTOs.

---

## Organización de paquetes

```
com.finte.sigapp.
├── controller     # Controllers REST
├── service        # Interfaces de servicios
├── service.impl   # Implementaciones de servicios
├── repository     # Repositorios JPA
├── entity         # Entidades JPA (mapeo tablas Oracle)
├── dto            # Data Transfer Objects (request/response)
├── mapper         # Conversores Entity ↔ DTO
├── config         # Configuraciones Spring
├── security       # JWT, filters, security config
├── exception      # Excepciones y GlobalExceptionHandler
└── utils          # Utilidades (ProcedureExecutor, etc.)
```

---

## Manejo de excepciones

El proyecto usa un **GlobalExceptionHandler** (@RestControllerAdvice) centralizado en `com.finte.sigapp.exception.GlobalExceptionHandler`.

### Reglas:

1. **Services NUNCA retornan ResponseEntity**: Solo lanzan excepciones.
2. **Usar excepciones tipadas**:
   - `BussinessException` para errores de negocio
   - `UnauthorizedException` para errores de autorización
   - El GlobalExceptionHandler mapea estas excepciones a respuestas HTTP apropiadas

3. **Todos los errores provienen del enum ErrorCode** (`com.finte.sigapp.exception.catalog.ErrorCode`)

### Ejemplo de uso:

```java
if (bodegaEnConteo) {
    throw new BussinessException(ErrorCode.SIGAPP_001, "Mensaje descriptivo");
}
```

### ErrorCodes comunes:
- `SIGAPP_001`: Error de negocio general
- `SIGAPP_400`: Validación
- `SIGAPP_401`: No autenticado
- `SIGAPP_402`: Token expirado
- `SIGAPP_403`: Token inválido
- `SIGAPP_404`: Recurso no encontrado
- `SIGAPP_405`: Sin permisos
- `SIGAPP_500`: Error interno del servidor

**Nunca escribir mensajes hardcodeados**: usar ErrorCode.getMessage() o pasar mensaje descriptivo al constructor.

---

## Convenciones de Base de Datos

### Tablas del módulo SIGAPP:
Todas las tablas del módulo comienzan con el prefijo **FI_**

Ejemplos:
- `FI_COFIUSCO` - Usuarios de conteo
- `FI_COFIARAS` - Asignaciones y registros de conteo (histórico)

### Tablas del ERP (NO modificar):
Las siguientes tablas pertenecen al ERP existente y **NO deben modificarse**:

- `CONTARBO` - Snapshot de artículos para conteo
- `ARTICULO` - Catálogo de artículos
- `PERSONA` - Información de personas
- `BODEGA` - Catálogo de bodegas
- `DOCUINVE` - Documentos de inventario

### Convención de columnas:

**Máximo 8 caracteres por nombre de columna** (limitación Oracle legacy)

Ejemplos:
- `arasidus` - ID usuario
- `arasidar` - ID artículo
- `arasesta` - Estado

---

## Flujo principal del conteo

El proceso de asignación de artículos funciona de la siguiente manera:

1. **Bloqueo de bodega**: El sistema financiero bloquea la bodega
2. **Generación de snapshot**: PL/SQL genera el snapshot en la tabla `CONTARBO`
3. **Consulta de artículos**: Spring Boot consulta `CONTARBO`
4. **Ordenamiento**: Los artículos se ordenan alfabéticamente
5. **División en bloques**: Se dividen en bloques lo más equilibrados posible

   Ejemplo con 101 artículos:
   - Usuario A: 50 artículos
   - Usuario B: 51 artículos

6. **Asignación aleatoria**: Los bloques se asignan aleatoriamente entre usuarios para evitar favoritismos
7. **Registro de asignaciones**: Se registra cada asignación en `FI_COFIARAS`
8. **Creación de usuarios temporales**: Se generan o actualizan usuarios en `FI_COFIUSCO`
9. **Notificación**: Se envía un código temporal por correo

---

## Usuario temporal

Los usuarios **no existen en Spring Security**, se crean dinámicamente en la base de datos.

### Flujo de creación:

1. Buscar documento en tabla `PERSONA` del ERP
2. Si existe:
   - Crear o actualizar registro en `FI_COFIUSCO`
   - Generar nuevo código temporal
   - Establecer `estado = 'ac'` (activo)
   - Establecer `fecha creación = NOW()`
3. Enviar código temporal por correo

---

## Historial de conteos

La tabla `FI_COFIARAS` es un **histórico**.

⚠️ **NUNCA eliminar registros**: Cada conteo debe quedar registrado permanentemente para auditoría.

---

## Niveles de conteo

Se manejan **únicamente 3 niveles** de conteo:

- **Conteo 1** - Primer conteo
- **Conteo 2** - Segundo conteo (reconteo)
- **Conteo 3** - Tercer conteo (validación final)

No existen más niveles de conteo.

---

## Estados de asignaciones

Los registros de asignación pueden tener los siguientes estados:

- `PENDIENTE` - Artículo asignado pero no contado
- `CONTADO` - Artículo ya contado por el usuario
- `VALIDADO` - Conteo validado y confirmado

---

## Reglas importantes

⚠️ **Lógica de negocio en Java**: Toda la lógica nueva debe implementarse en Spring Boot (Java).

⚠️ **No crear procedimientos PL/SQL nuevos**: Salvo que sea estrictamente necesario para integrarse con el ERP.

✅ **Reutilizar procedimientos existentes**: Usar los procedimientos PL/SQL del ERP cuando sea posible.

---

## Ejecución de Procedimientos Oracle

Utilizar **SIEMPRE** el componente `ProcedureExecutor` (`com.finte.sigapp.utils.ProcedureExecutor`) para ejecutar código PL/SQL.

### Métodos disponibles:

```java
// Procedimiento sin retorno
procedureExecutor.ejecutarProcedimiento(String procedureName, List<ProcedureParam> params)

// Procedimiento con parámetros OUT
Map<String, Object> resultados = procedureExecutor.ejecutarProcedimientoConSalida(String procedureName, List<ProcedureParam> params)

// Función con retorno tipado
T resultado = procedureExecutor.ejecutarFuncion(String functionName, Class<T> returnType, List<ProcedureParam> params)

// Bloque anónimo PL/SQL
procedureExecutor.ejecutarBloqueAnonimo(String bloquePlsql, Map<String, Object> params)

// Bloque con parámetros OUT
Map<String, Object> resultados = procedureExecutor.ejecutarBloqueConSalida(String bloquePlsql, List<ProcedureParam> paramsIn, List<ProcedureParam> paramsOut)
```

### Uso de ProcedureParam:

```java
List<ProcedureParam> params = List.of(
    new ProcedureParam("p_empresa", empresaId, String.class, ParameterMode.IN),
    new ProcedureParam("p_resultado", null, String.class, ParameterMode.OUT)
);
```

**Nunca ejecutar procedimientos directamente desde JPA o EntityManager en Services**: siempre usar ProcedureExecutor.

---

## Envío de Correos

**NO utilizar Spring Mail**: El envío de correos se realiza mediante procedimientos Oracle.

### Procedimiento utilizado:
```sql
INT_PKMAILAPI.prc_EnviaCorreo
```

### Feature flag:
```properties
app.features.envio-correo=false  # Deshabilitado en desarrollo
```

Toda la mensajería debe pasar por Oracle para mantener consistencia con el sistema financiero existente.

---

## Seguridad y Autenticación

El proyecto usa **JWT (JSON Web Tokens)** con Spring Security.

### Componentes clave:

- `SecurityConfig`: Configuración de Spring Security
- `JwtTokenProvider`: Generación y validación de tokens JWT
- `JwtAuthenticationFilter`: Filtro para autenticar requests con JWT

### Tokens:
- **Access Token**: 28800000 ms (8 horas)
- **Refresh Token**: 86400000 ms (24 horas)

### Modo Desarrollador:

La propiedad `security.developer-mode=true` en application.properties **desactiva la seguridad** para permitir pruebas sin autenticación.

**En producción**: `security.developer-mode=false`

### Arquitectura:
- **Stateless**: No se almacenan sesiones en el servidor
- **Bearer Token**: Los requests incluyen `Authorization: Bearer <token>`
- Endpoints públicos: `/api/v1/auth/**`, `/api/v1/health/**`, `/swagger-ui/**`, `/v3/api-docs/**`
- Resto de endpoints requieren JWT válido (cuando developer-mode=false)

---

## Principios de desarrollo

### Clean Code:
- Métodos pequeños y con responsabilidad única (SRP)
- Nombres descriptivos para variables, métodos y clases
- Código legible y autoexplicativo
- Seguir principios SOLID

### Logging:
- Usar **@Slf4j** de Lombok en todas las clases
- Registrar inicio de métodos importantes: `log.info("Asignando artículos para bodega {}", bodegaId);`
- Registrar errores con contexto: `log.error("Error ejecutando procedimiento {}: {}", nombreProc, e.getMessage(), e);`
- Niveles de log:
  - `log.debug()` - Detalles técnicos para debugging
  - `log.info()` - Flujo normal de la aplicación
  - `log.warn()` - Advertencias (ej: token expirado)
  - `log.error()` - Errores y excepciones

### Estilo de código:
- Preferir streams únicamente cuando mejoren la legibilidad
- No abusar de Optional
- Evitar lógica compleja dentro de lambdas
- Preferir claridad sobre brevedad

### Transacciones:
- Usar `@Transactional(rollbackFor = Exception.class)` en Services que modifican datos
- Las transacciones se manejan a nivel de Service, no de Controller

---

## Desacoplamiento del ERP

**Filosofía arquitectónica**:

- Spring Boot es el **núcleo de la lógica de negocio**
- Oracle proporciona **únicamente**:
  - Persistencia de datos
  - Procedimientos heredados del ERP existente

El proyecto debe mantenerse lo más independiente posible del sistema financiero para facilitar:
- Mantenimiento
- Testing
- Evolución independiente
- Migración futura si es necesario

---

# Módulo de Traspasos de Activos

Segundo módulo del backend (`/api/v1/traspasos`), independiente del conteo físico.
Permite que una persona entregue **varios activos** a otra con un solo documento.

📄 **El detalle completo está en `src/main/resources/db/README.md`.** Leerlo antes
de tocar la base de datos o el paquete. Esta sección resume lo que es fácil de
romper sin darse cuenta.

## Modelo de datos

```
REQUSUMI  1 ──── N  FI_MOVITRAS      una fila POR ARTÍCULO
MOTRNUTR  1 ──── 2  FI_MOTRFIRM      firma fuente y firma destino
```

`FI_MOVITRAS` guarda **una fila por artículo**. Hay dos números y no significan
lo mismo:

| Columna | Secuencia | Qué es |
|---|---|---|
| `MOTRIDMT` | `FI_MOVITRAS_SEQ` | Clave técnica de la línea. **No sale en ninguna API** |
| `MOTRNUTR` | `FI_TRASPASO_SEQ` | **Número de trámite**. Identificador de negocio, igual en las N líneas |

El `id` de todos los endpoints es `MOTRNUTR`. No confundirlo con
`numeroDocumento`, que es el `RESUNUME` de la requisición en el ERP.

## Invariantes que no se pueden romper

⚠️ **Todo `UPDATE` sobre `FI_MOVITRAS` filtra por `MOTRNUTR`, nunca por
`MOTRIDMT`.** De `MOTRDREM` a `MOTRFERE` el contenido se repite en las N líneas y
nada estructural impide que se desvíen: la coherencia depende de esta regla.
Consulta de control:

```sql
SELECT motrnutr, COUNT(DISTINCT motresta) FROM fi_movitras
 GROUP BY motrnutr HAVING COUNT(DISTINCT motresta) > 1;   -- debe dar cero filas
```

⚠️ **Ningún procedimiento hace `SELECT INTO` por el identificador del trámite**:
devolvería N filas. Se usa `pro_bloquea_tramite`, que bloquea las líneas con un
cursor `FOR UPDATE` y luego lee la cabecera con `ROWNUM = 1`.

⚠️ **`FI_MOTRFIRM` no puede llevar FK hacia el trámite.** `MOTRNUTR` se repite y
Oracle exige unicidad en la columna referenciada (`ORA-02270`). La integridad la
sostiene el paquete.

## Ciclo de vida

```
 pe pendiente ──aprobar──▶ ap aprobado ──[dos firmas]──▶ re recibido
      │                                                   + DOCUINVE (1 fila)
      └──rechazar──▶ na rechazado                         + MOVIINVE (N líneas)
```

**Solo cuatro estados: `pe`, `ap`, `na`, `re`.** No existen `af` ni `ad`: al no
haber orden entre las firmas, el avance es **dos booleanos independientes** y una
sola columna no puede representarlos. Se consulta en `FI_MOTRFIRM.MOFIFEFI`
(nulo = sin firmar).

🔒 **REGLA DE ORO: el documento de inventario se genera al RECIBIR, nunca al
aprobar.** Aprobar autoriza el trámite; recibir es lo que mueve el activo, y solo
se puede recibir con las dos firmas. Un traspaso aprobado que nadie firma no deja
rastro en `DOCUINVE` ni en `MOVIINVE`.

🔒 **El trámite se aprueba o se rechaza COMPLETO.** No existe aprobación por
artículo. Por eso `pro_agregar_articulo` exige estado `pe`: el aprobador autorizó
un conjunto concreto y colar uno más después saltaría esa autorización.

🔒 **Un activo no puede estar en dos traspasos abiertos a la vez.** Lo cierran
`na` y `re`; en `pe` o `ap` sigue comprometido. Tres capas, y cada una cubre algo
distinto:

| Capa | Qué cubre |
|---|---|
| `TransferServiceImpl.buscarComprometidos` | El caso real. Lista **todos** los conflictos con su placa y su trámite en un solo mensaje, antes de abrir el documento |
| `pro_valida_activo_libre` | Quien llame el paquete directo desde SQL. Corta en el primer conflicto |
| Índice `FI_MOVITRAS_UK_ABIERTO` | La carrera entre dos sesiones creando a la vez |

El cruce es por el par **artículo + placa**, no solo por el código: el mismo
artículo con otra placa es otro elemento físico. La placa vacía es `'.'` en los
dos lados. `GET /traspasos/activos` lo expone como `enTramite` para que el front
lo bloquee en el selector — se **marca**, no se excluye, para que el funcionario
entienda por qué no puede usarlo.

🔒 **Las firmas no tienen orden y no mueven el estado.** Cualquiera de las dos
puede llegar primero; el trámite se queda en `ap` hasta que se recibe.

## Alta en dos procedimientos

`pro_crear_solicitud` abre el trámite con el **primer** artículo (documento,
primera línea y las dos firmas vacías) y `pro_agregar_articulo` añade el resto.

No es una llamada con lista porque `REQUSUMI.RESUCEIN` sale del activo y el
documento no se puede crear antes de conocer al menos uno. Como la cabecera se
copia en cada línea, esto se resuelve con parámetros escalares, sin tipos
colección ni tocar `ProcedureExecutor`. Las N llamadas van en el mismo
`@Transactional` del service.

Todos los artículos de un trámite deben compartir **centro de información**
(`ACFICEIN`): va a `REQUSUMI.RESUCEIN`, una sola columna para todo el documento, y con
valores distintos no hay uno correcto que poner. Se valida dos veces —en
`TransferServiceImpl.buscarIncompatibles` antes de abrir el documento, y en el paquete al
agregar— por la misma razón que los activos comprometidos: el rechazo previo lista todos
los implicados de golpe, el del paquete corta en el primero y ya con el documento creado.

⚠️ **`ACFITERC` NO se valida, y no debe volver a validarse.** Es "el tercero al que se
compra" —el **proveedor** del activo—, no su responsable ni su propietario. Hubo una regla
que exigía que coincidiera entre artículos y bloqueaba traspasos legítimos: no se podían
mover juntos un aire acondicionado y un medidor por haberse comprado a proveedores
distintos, algo sin relación alguna con un cambio de responsable interno.

Esa regla solo existía para sostener un error de fondo: `DOCUINVE.DOINTERC` se llenaba con
`ACFITERC`. **El ERP pone ahí el NIT de la propia empresa** (`EMPRESA.EMPRNIT`), como hace
`PRO_GENERA_SALIREQU` —que declara su variable como `Empresa.EmprNit%TYPE`— y como
confirman sus datos: los documentos de salida y de traslado entre bodegas llevan siempre el
NIT de la compañía. Corregido en `pro_generar_movimiento`, que ahora lee `EMPRNIT` para
`DOINTERC` y `MOINTERC`.

El responsable de un activo es **`ACFIPERS`**, que es otra columna y no interviene en esto:
la fuente y el destino salen del trámite, que es la versión aprobada de quién entrega.

## Firmas

- **Se guardan como `BLOB`** (bytes), no base64 en `CLOB`: ocupa ~33 % menos, se
  abre con *View as image* desde SQL Developer y evita el `ORA-01460` al pasar un
  String largo a un parámetro `CLOB`.
- **El base64 es formato de transporte**, no de almacenamiento. `FirmaUtil` quita
  el prefijo `data:image/...;base64,`, tolera saltos de línea, valida los magic
  bytes PNG/JPEG y aplica `app.traspasos.firma.max-bytes`.
- **`ProcedureExecutor` enlaza `byte[]` como `java.sql.Blob`**, no como
  `byte[]` directo: registrado como `VARBINARY` el driver lo manda como `RAW`, que
  en PL/SQL topa en 32 KB igual que el `CLOB`. El caso está en
  `configurarParametros`.
- Las dos filas de `FI_MOTRFIRM` se crean **vacías al abrir el trámite**, así
  `pro_registrar_firma` es un `UPDATE` y la PK impide una segunda firma.

## Contrato de la API

Ninguna ruta cambió de forma; `{id}` es el número de trámite.

| Endpoint | Devuelve |
|---|---|
| `POST /crear` | Body con `articulos[]` (`articulo`, `placa`). Responde `id` = número de trámite |
| `GET /list` | Mínimo: `id`, `tipoDocumento`, `numeroDocumento`, `fechaCreacion` |
| `GET get/{id}` | Cabecera + `articulos[]` + `firmas[]` (posición, tipo, fechaFirma, firmada, firma en base64) |
| `POST process/{id}` | Aprueba o rechaza el trámite completo |
| `PUT sign/{id}` | `tipoFirma` (`FU`/`DE`) + `firma` en base64 |
| `GET sign/{id}/{tipo}` | La imagen, con su `Content-Type`. Para soporte y navegador |
| `PUT /{id}/recibir` | Exige las dos firmas y genera el inventario |
| `GET /activos?persona&empresa` | Activos de una persona para el selector. Trae `centroInformacion`, `tercero` y `enTramite` |
| `GET /personas?bodega&empresa` | Personas cuya división corresponde a esa bodega. `empresa` es obligatoria |

**La bandeja y el detalle no duplican datos.** Lo que está en el detalle no se
repite en la lista, y dentro del detalle cada dato vive en un solo sitio: las
firmas están completas en `firmas[]`, no repartidas entre el array y campos
sueltos de la cabecera.

## Operación

Orden de los scripts en `src/main/resources/db/`: `01` tabla → `02` firmas →
`03` spec → `04` body → `05` sinónimos → `06` parámetros. Se aplican como
`SRFPNACUA`.

⚠️ **Los archivos de paquete necesitan un `/` solo en su última línea.** Sin él
*sqlplus* no ejecuta el `CREATE OR REPLACE` y **no avisa de nada**: parece que
compiló y en realidad no hizo nada. Verificar siempre después:

```sql
SELECT object_type, status FROM user_objects WHERE object_name = 'PKG_FI_MOVITRAS';
```

**Pendiente de confirmar con el área funcional:** `06_PARAOPER.sql` da de alta
`ESAPTRAS` ("estado al aprobar traspaso") pero el paquete lee `ESTAAPRO`, que ese
script no crea. Una de las dos sobra. Está marcado en un comentario del body.

---

# Módulo de Requisiciones

Tercer módulo del backend (`/api/v1/requisiciones`), independiente de los otros dos.
Lleva a la app el ciclo de vida de una **requisición de suministro**: aprobar, entregar,
firmar y generar la salida de inventario.

📄 **El detalle de BD está en `src/main/resources/db/requisiciones/README.md`.** Esta
sección resume lo que es fácil de romper sin darse cuenta.

## Lo que este módulo NO hace

**No crea la requisición.** Nace en el ERP en estado `in` y SIGAPP solo la hace avanzar.
**No genera el documento de inventario por su cuenta**: lo hace `PRO_GENERA_SALIREQU`, el
procedimiento del ERP que está detrás de la forma PSSR. Y **no toca la cabecera**:
`REQUSUMI.RESUESTA` se queda en `ac` siempre.

Esta es la diferencia de fondo con traspasos, donde el documento lo armaba nuestro paquete.

## Modelo de datos

```
REQUSUMI  1 ──── N  MOVIRESU     una fila por artículo, bodega y secuencia
   │                    │
   │                    └── N  ASPLMORS      placas, solo artículos devolutivos
   └── 2  FI_REQUFIRM (nueva)                firma de salida y firma de recibe
```

La requisición se identifica por la **terna** `(MORSTDEM, MORSTIDO, MORSRENU)`, que es la
llave de `REQUSUMI`. No hay identificador propio y no se inventa uno. Dentro de ella, una
línea es el trío `(MORSARTI, MORSBODE, MORSSECU)`: el mismo artículo puede repetirse en
otra bodega o secuencia, así que ninguna de las tres sobra.

## Ciclo de vida

```
 in ──aprobar──▶ ap ──entregar──▶ en ──[dos firmas]──▶ registrar ──▶ rg
                                   │                                 + DOCUINVE (1)
                                   └──anular──▶ ae                   + MOVIINVE (N)
```

Aprobar y entregar admiten **cantidades parciales por línea**. Las líneas que no vengan en
la petición no se tocan.

## Invariantes que no se pueden romper

⚠️ **`pro_set_contexto` va primero en toda transacción de escritura.** El parámetro
`CONREBEQ` está activo, así que el trigger `TRGBUPR_MOVIRESU` exige que quien entrega o
registra sea responsable de la bodega, y lo comprueba leyendo `pkgfnPersonal.getUsuario()`,
una **variable de paquete de la sesión Oracle**. Con un pool de conexiones no se establece
sola y una conexión reciclada conserva el valor anterior. Si queda nula, un `NVL` del
trigger la iguala al responsable de la bodega y **el control se salta en silencio**. Hay un
test que verifica el orden de las llamadas, y está ahí por esto.

⚠️ **`MORSCARE` es una ENTRADA del registro, no un resultado.** `PRO_GENERA_SALIREQU` la
lee como la cantidad que lleva a `MOVIINVE`, con ella calcula el costo y **si vale cero no
genera movimiento para esa línea**. Nunca la escribe: lo único que escribe en `MOVIRESU` es
`MORSESTA = rg`. Por eso `pro_generar_salida` la sella justo antes de llamarlo. Sin ese
paso la línea pasa a `rg` sin mover inventario y nadie avisa — de las 101 313 líneas
históricas en `rg`, unas 33 400 están así.

🔒 **El documento de salida se genera al REGISTRAR, nunca al entregar**, y solo con las dos
firmas. Misma regla de oro que en traspasos.

🔒 **Las firmas no tienen orden y no mueven el estado.** Cualquiera puede llegar primero; la
requisición se queda en `en` hasta que se registra. Quien firmó es quien tiene `REFIFEFI`
no nulo.

🔒 **Solo se gestiona lo que tenga equivalencia.** El tipo de documento de salida sale de
`EQUIVALE` (`EQUICLEQ='COBO'`, `EQUITIPO='REQU'`), y esa misma fila es el filtro de tipos
gestionables: sin ella el tipo no se lista, no sale en la bandeja y no se puede aprobar. Así
quedan fuera las requisiciones de traslado, que son integración con el comercial. **Hay dos
tablas `EQUIVALE`** —`SRFPNACUA` y `FPLPNACUA`— y la carga tiene que estar en la primera,
que es donde el paquete la busca.

🔒 **Un artículo devolutivo exige tantas placas en `ASPLMORS` como unidades se mueven.** Al
entregar se contrasta con la cantidad entregada y al sellar la recibida, con esa. En una
recepción parcial hay que **soltar** las placas que no se reciben o el ERP rechaza el
registro.

## Las placas se eligen solas, por orden de entrada

`pro_asignar_placas_fifo` asigna las placas al entregar ordenando por
`ACTIFIJO.ACFIFEEN` — **lo primero que entró al almacén es lo primero que sale** — con la
placa como desempate, porque los activos entran por lotes con la misma fecha y sin un
segundo criterio el resultado sería arbitrario entre ejecuciones.

Con esto quien despacha recibe **la lista ya resuelta** en vez de tener que elegir, y la
rotación del inventario deja de depender de que cada quien se acuerde. El detalle devuelve
las placas en ese mismo orden (`buscarPorRequisicion` hace `JOIN` con `ACTIFIJO` y ordena
por fecha de entrada), así que la pantalla es literalmente el orden de despacho.

Tres detalles que sostienen la selección:

| Filtro | Por qué |
|---|---|
| `ACFIBODE = MORSBODE` | El activo sigue en la bodega **fuente** (tipo `FI`, física) hasta que se registra la salida; ahí el ERP lo pasa a la bodega destino (tipo `PE`). Comprobado sobre los datos: las 12 filas históricas de `ASPLMORS` tienen el activo ya en `MORSBODS` |
| `ACFIESTA = 'ac'` | No se despacha algo dado de baja |
| `NOT EXISTS` sobre `ASPLMORS` de requisiciones en `in`/`ap`/`en` | Evita que dos requisiciones abiertas a la vez se lleven la misma placa. Las `rg` y `ae` no bloquean: su activo ya cambió de bodega |

**El override manual se respeta.** Si el front envía placas para una línea, el FIFO solo
completa la diferencia: cubre el caso real de que la más antigua esté dañada o no aparezca
en el estante, sin obligar a salirse del sistema. La clasificación devolutivo/consumo la
decide el ERP con `funValiClas` sobre `ARTICULO.ARTICLAS` (consumo = `ARTICONS` = `1`); no
se replica en Java porque duplicarla la haría divergir.

⚠️ **Que falten placas no es un caso de negocio.** El trigger ya validó la disponibilidad
contra `ARTIBODE` antes de llegar ahí, así que si el FIFO no encuentra suficientes hay un
desajuste entre el saldo de la bodega y los activos de `ACTIFIJO`. El mensaje lo dice con
esas palabras para que se corrija, en vez de quedar en un error opaco del trigger.

## El ERP hace el trabajo pesado

El trigger `TRGBUPR_MOVIRESU` (516 líneas) se dispara en cada `UPDATE` de una línea: valida
cantidades enteras para devolutivos, impide modificar cantidades ya comprometidas, valida
disponibilidad contra `ARTIBODE`, valida y limpia las placas, y propaga a `ARTIBODE`,
`SALDINLA` y `REQUSUMI.RESUFERE`.

**El paquete no replica nada de eso.** Si un `UPDATE` de `PKG_FI_REQUISICION` parece
demasiado simple, es por esto y no por descuido.

Consecuencia práctica: el orden importa. El trigger rechaza modificar `MORSCAAP` si ya hay
`MORSCARE` distinta de cero, así que aprobar va siempre antes de entregar.

## Errores

Los rechazos de negocio de este módulo vienen del ERP como `ORA-20008` ("no hay saldo
suficiente", "faltan placas por asignar"). `OracleErrorUtil.mensajeNegocio` extrae ese texto
y descarta la pila `ORA-06512`, que no le dice nada a quien está en el almacén.

En el paquete, el bloque `EXCEPTION` de los procedimientos públicos **re-lanza** en vez de
envolver cuando `pkgerror.funnivelerror` no es cero. Eso es lo que hace que el mensaje del
ERP llegue intacto: sustituirlo por un `raise_application_error` plano lo perdería.

**Los errores del ERP no se capturan en el service**, a propósito: convertirlos ahí en
`code -1` dejaría la transacción sin marcar para rollback y las líneas ya procesadas
quedarían confirmadas mientras la respuesta dice que falló. Se dejan propagar y el
controller los traduce después de que Spring haya revertido.

## Contrato de la API

`{empresa}/{tipo}/{numero}` es la terna. `numero` se declara `Long` por seguridad, no solo
por tipado: `PRO_GENERA_SALIREQU` filtra las líneas con `LIKE` sobre ese valor, así que un
comodín barrería otras requisiciones. Exigir un entero lo hace imposible desde la API.

| Endpoint | Devuelve |
|---|---|
| `GET /tipos` | Tipos gestionables. Vacío = falta la equivalencia |
| `GET /?estado&empresa&tipoDocumento&bodega&desde` | Bandeja. Una fila por documento |
| `GET /{e}/{t}/{n}` | Cabecera + `lineas[]` (con placas) + `firmas[]` |
| `PUT /{e}/{t}/{n}/aprobar` | `lineas[]` con `cantidad` |
| `PUT /{e}/{t}/{n}/entregar` | `lineas[]` con `cantidad` y `placas[]` |
| `PUT /{e}/{t}/{n}/anular` | `lineas[]` sin cantidad: anula el saldo aprobado |
| `PUT /{e}/{t}/{n}/firmar` | `tipo` (`SA`/`RE`), `persona`, `firma` en base64 |
| `GET /{e}/{t}/{n}/firmas/{tipo}` | La imagen, con su `Content-Type` |
| `PUT /{e}/{t}/{n}/registrar` | Punto de no retorno. Cuerpo opcional para recibir parcial |

La bandeja lleva ventana temporal y tope por defecto: `MOVIRESU` tiene más de cien mil
líneas y sin cota una consulta por `rg` traería media tabla.

**Registrar es reentrante**: si la requisición ya está en `rg` devuelve el aviso y no vuelve
a llamar al ERP. Eso es lo que impide que un reintento genere un segundo documento, que
sería irreversible.

## Pendientes

- **Quién entregó y quién anuló no se guarda en el ERP.** `MOVIRESU` solo tiene
  `MORSUSAP` para el aprobador y añadir una columna exigiría modificar una tabla del
  sistema financiero. Va al log vía `pro_log`.
- **Recepción parcial:** lo entregado y no recibido no vuelve al inventario ni queda como
  anulado. Es la consecuencia aceptada de permitir recibir menos; conviene que el área
  funcional lo tenga presente.
