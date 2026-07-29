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