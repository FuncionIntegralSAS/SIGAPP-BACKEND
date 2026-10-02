# Prueba funcional de los módulos

`modulos.js` recorre con [k6](https://k6.io) todas las APIs de lectura de los tres módulos
contra un backend vivo y, si se pide, un ciclo de escritura acotado por módulo. Un VU, una
iteración, y termina con código distinto de cero si algún check falla.

Sirve para confirmar que el backend sigue funcionando de punta a punta después de un cambio
transversal: usuario de base de datos, sinónimos, renombre de tablas, versión de Spring. Los
tests unitarios (`./mvnw test`) no llegan a Oracle; esto sí.

No es una prueba de carga. Para eso está `stress-tests/`.

## Ejecutar

Con el backend arriba (`./mvnw spring-boot:run`, o la imagen desplegada):

```bash
# Solo lectura. Sin credenciales de administrador exige security.developer-mode=true,
# porque las lecturas no abren el token pero el filtro de seguridad sí lo exigiría.
k6 run -e BASE_URL=http://localhost:8082 functional-tests/modulos.js

# Lectura con login real (funciona con la seguridad activa)
k6 run -e BASE_URL=http://localhost:8082 -e ADMIN_USER=dflorez -e ADMIN_PASS=... functional-tests/modulos.js

# Con escrituras
k6 run -e BASE_URL=http://localhost:8082 -e ADMIN_USER=dflorez -e ADMIN_PASS=... \
       -e ESCRIBIR=1 -e REQUISICION=2601/202500664 functional-tests/modulos.js
```

| Variable | Default | Para qué |
|---|---|---|
| `BASE_URL` | `http://localhost:8082` | Backend a probar |
| `EMPRESA` | `01` | Empresa de todas las consultas |
| `ADMIN_USER` / `ADMIN_PASS` | — | Usuario de `USUABADA`. Cómo sacar la clave: `docs/pruebas-requisiciones.md`. **No se versiona** |
| `CONTADOR_DOC` / `CONTADOR_COD` | `666` / `846166` | Usuario temporal de conteo (`FI_COFIUSCO`) con su código vigente |
| `BODEGA_PE` / `PERSONA_FUENTE` | `1111` / `PV11011` | Persona con activos libres en una bodega personal, para traspasos |
| `REQ_BODEGA` | `F11` | Bodega de la bandeja de requisiciones |
| `ESCRIBIR` | — | `1` crea un traspaso y lo **rechaza**. Queda en `na`, sin movimiento de inventario |
| `REQUISICION` | — | `tipo/numero` de una requisición en `in`: **aprueba** una unidad de su primera línea. Pasa a `ap` y no hay vuelta atrás por API |

## Qué cubre

| Módulo | Lectura | Escritura (opcional) |
|---|---|---|
| Conteo físico | login de contador, health, health/info, empresas, bodegas, bodegas con conteo pendiente, artículos asignados, pendientes del contador, personal | Ninguna: asignar artículos exige una bodega bloqueada con snapshot en `CONTARBO` |
| Traspasos | bandejas `pe` y `ap`, personas de una bodega, activos de una persona, detalle | crear → detalle (estado `pe`, artículos, dos firmas vacías) → rechazar → detalle (`na`) |
| Requisiciones | tipos, bandeja `in` y `rg`, detalle con líneas y firmas | aprobar una unidad → detalle (`ap`, aprobada = 1) |

Si se añade un endpoint al backend, se añade aquí su check.
