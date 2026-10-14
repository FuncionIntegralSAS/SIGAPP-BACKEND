# Pruebas de carga, estrés y concurrencia

Scripts de [k6](https://k6.io) contra un backend vivo. Cubren los tres módulos (conteo
físico, traspasos y requisiciones), la autenticación y los endpoints públicos.

Miden dos cosas distintas:

- **Rendimiento.** Si el backend aguanta muchos usuarios a la vez y dónde se rompe.
- **Corrección bajo concurrencia.** Si sigue haciendo lo correcto cuando varias peticiones
  tocan el **mismo** recurso en el mismo instante: doble toque en la app, reintento por mala
  señal, dos almacenistas sobre la misma requisición.

Para comprobar que cada endpoint funciona de punta a punta está `functional-tests/`.

## Orden de ejecución

Desde esta carpeta, con el backend arriba:

| Paso | Script | Qué hace | Escribe |
|---|---|---|---|
| 1 | `01-baseline.js` | 1 usuario, todos los endpoints de lectura. Mide el piso de latencia y deja `resultados/baseline.json` con un umbral por endpoint | No |
| 2 | `02-carga.js` | Contadores, administradores y tráfico público **a la vez**, cada uno en su escenario. Exige los umbrales del baseline | Solo con `ESCRIBIR=1` |
| 3 | `03-estres.js` | Sube el ritmo por escalones hasta romper, o simula un pico y mide si se recupera | No |
| 4 | `04-concurrencia.js` | Carreras: N peticiones idénticas en vuelo a la vez sobre el mismo recurso | Solo con banderas |
| — | `login-contador.js` | Llegada masiva al login, el refresh y el login de administrador | No |

```bash
k6 run -e ADMIN_USER=... -e ADMIN_PASS=... 01-baseline.js
k6 run -e ADMIN_USER=... -e ADMIN_PASS=... 02-carga.js
k6 run -e ADMIN_USER=... -e ADMIN_PASS=... -e MAX_RPS=60 03-estres.js
k6 run -e ADMIN_USER=... -e ADMIN_PASS=... 04-concurrencia.js
```

En Windows, k6 está en `C:\Program Files\k6\k6.exe`. Desde WSL se llama con esa ruta y
`localhost` es el de Windows.

**Sin `ADMIN_USER`/`ADMIN_PASS`** traspasos y requisiciones exigen el rol `ADMIN` y
responden 401. Los scripts lo detectan, avisan y omiten esos endpoints en vez de contarlos
como fallos. Para medirlos sin credenciales hay que levantar el backend con
`SECURITY_DEVELOPER_MODE=true`. El login de administrador solo se mide con credenciales.

**No correr 02 y 03 con valores altos contra la base de desarrollo compartida** sin avisar
al equipo: la saturan para todos.

## Variables

| Variable | Defecto | Para qué |
|---|---|---|
| `BASE_URL` | `http://localhost:8082` | Backend a probar |
| `EMPRESA` | `01` | Empresa de todas las consultas |
| `ADMIN_USER` / `ADMIN_PASS` | — | Usuario de `USUABADA`. **No se versiona** |
| `CONTADOR_DOC` / `CONTADOR_COD` | `666` / `846166` | Usuario temporal de `FI_COFIUSCO` con su código vigente |
| `BODEGA_CONTEO` / `DIVISION` | `FMIA` / `16023` | Bodega y división de las consultas del contador |
| `BODEGA_PE` / `PERSONA_FUENTE` | `1111` / `PV11011` | Persona con activos libres en una bodega personal |
| `REQ_BODEGA` | `F11` | Bodega de la bandeja de requisiciones |
| `EXCLUIR` | — | Endpoints fuera de la mezcla, separados por coma. Aísla uno roto para que no tape la medida del resto |

Por script:

| Script | Variables |
|---|---|
| `01-baseline.js` | `DURACION` (`1m`) |
| `02-carga.js` | `CONTADORES` (30), `ADMINS` (10), `PUBLICO_RPS` (2), `RAMPA` (`1m`), `MESETA` (`3m`), `UMBRAL_P95` (fija uno para todos), `ERRORES_MAX` (0), `ESCRIBIR`, `ESCRITORES` (2), `MAX_CICLOS` (10) |
| `03-estres.js` | `PERFIL` (`escalera` o `pico`), `MAX_RPS` (60), `ESCALONES` (6), `SUBIDA_S` (30), `SOSTENER_S` (60), `P95_ACEPTABLE` (3000), `VALIDAS_ACEPTABLE` (0.99) |
| `04-concurrencia.js` | `N` (6), `N_POOL` (25) y las banderas de escritura de abajo |
| `login-contador.js` | `USUARIOS` (50), `UMBRAL_P95` (1500) |

## Cómo se clasifica cada respuesta

Los módulos de traspasos y requisiciones devuelven **HTTP 500 con `code -1`** tanto para un
rechazo de negocio («ya fue procesado») como para una caída. Contar todo 500 como fallo haría
imposible una prueba de concurrencia, donde perder la carrera es lo esperado. Por eso
`lib/sigapp.js` clasifica:

| Resultado | Cuándo |
|---|---|
| `ok` | 2xx y, si va envuelta, `code 0` |
| `negocio` | Rechazo controlado: 500 + `code -1`, 400 del `GlobalExceptionHandler`, 404 |
| `auth` | 401 o 403 |
| `pool` | Hikari no entregó conexión a tiempo |
| `tecnico` | Todo lo demás: timeout del cliente, 5xx sin envoltura, error de infraestructura de Oracle |

Métricas propias: `errores_tecnicos`, `pool_agotado` y `respuestas_validas`.

## Concurrencia

Cada carrera dispara N peticiones a la vez con `http.batch` y verifica que **gane
exactamente la que debe**, que las demás sean rechazos de negocio y que ninguna termine en
error técnico. Después lee el estado final y comprueba que sea el de la ganadora.

**Siempre, sin escribir:**

| Carrera | Invariante |
|---|---|
| Login simultáneo del mismo contador | Todos reciben token y cada token sirve |
| Refresh simultáneo | Todos responden |
| Mismo detalle leído N veces | N respuestas idénticas |
| Pool saturado: `N_POOL` consultas pesadas con 5 conexiones | Todas terminan haciendo cola, ninguna muere por el timeout de Hikari |

**`-e ESCRIBIR=1`** — traspasos. Todo termina en `na`, sin movimiento de inventario, pero
deja documentos `REQUSUMI`. Necesita dos activos libres de `PERSONA_FUENTE`.

| Carrera | Invariante |
|---|---|
| N altas con el mismo activo | Gana una. Lo corta el índice `FI_MOVITRAS_UK_ABIERTO` |
| N altas con dos activos en orden inverso | Gana una aunque Oracle tenga que resolver un interbloqueo |
| N rechazos del mismo trámite | Gana uno y queda en `na` |

**`-e TRASPASO_CICLO=1`** — ciclo completo. **Mueve un activo** a `PERSONA_DESTINO` y lo
devuelve con un segundo traspaso; quedan dos `DOCUINVE` que se compensan. Exige
`PERSONA_DESTINO` y `BODEGA_DESTINO`, la bodega personal del destino.

| Carrera | Invariante |
|---|---|
| Aprobar y rechazar a la vez | Gana una y el trámite queda en su estado |
| N firmas `FU` y N `DE` a la vez | Ganan dos, una por posición, y el estado sigue en `ap` |
| N recepciones a la vez | Gana una: un solo documento de inventario |
| Llegada del activo | Aparece una sola vez en la bodega de destino |

**`-e REQ_CICLO=tipo/numero`** — requisiciones, **irreversible**: registra la salida de una
unidad en el ERP. Si la requisición tiene más líneas, las aprueba con 1 y **las anula** para
que no frenen el documento. Usar una de prueba con una sola línea. El administrador tiene
que ser el responsable de la bodega.

| Carrera | Invariante |
|---|---|
| N aprobaciones de la misma línea | Gana una y la aprobada es 1, no N |
| N entregas de 1 sobre 1 aprobada | Gana una y la entregada es 1 |
| N firmas `SA` y N `RE` | Gana una por tipo |
| N registros | Uno solo trae tipo de salida; los demás responden «ya fue registrada» |

**Conteo físico** — escriben en `CONTARBO`, tabla del ERP:

| Bandera | Carrera |
|---|---|
| `CONTEO_REPORTAR=1` | El contador reporta N veces a la vez el mismo artículo pendiente de `BODEGA_CONTEO`, conteo `NUMERO_CONTEO` |
| `CONTEO_GENERAR='{json}'` | N veces `/conteo-fisico/registrar` con ese cuerpo |
| `CONTEO_ASIGNAR='{json}'` | N veces `/asignar_articulos` con ese cuerpo. Crea usuarios temporales |
| `CONTEO_CERRAR=1` | Cierra `BODEGA_CONTEO` N veces a la vez |

⚠️ **Se espera que las tres primeras fallen hoy.** El conteo físico no tiene bloqueos ni
control de versión: `reportarConteo`, `generarConteoFisico` y `asignarArticulos` leen,
validan y escriben sin bloquear, así que N peticiones simultáneas pueden pasar todas la
validación. Si la prueba falla ahí, está mostrando ese defecto, no un falso positivo.

## Resultados de referencia

Corrida corta del 2026-10-14, backend local en Windows contra la base de desarrollo,
`security.developer-mode=true` y sin credenciales de administrador:

| Prueba | Resultado |
|---|---|
| Baseline | 23 de 24 endpoints medidos. El más lento, `GET /traspasos/activos`, con p95 de 1,9 s |
| Carga, 15 contadores + 5 administradores | Todos los umbrales cumplidos salvo `GET /bodegas/division/{d}` |
| Estrés en escalera | Aguanta 10 peticiones/s. A 20 el p95 sube a 4,5 s y k6 descarta iteraciones |
| Concurrencia de lectura | Todo correcto. 25 consultas sobre 5 conexiones: la más lenta tarda unos 2 s |

Esas corridas destaparon que `GET /bodegas/division/{d}` respondía 500 siempre: su consulta
leía `BODENOMB`, que no existe en `BODEGA` (`ORA-00904`). Corregido a `BODEDESC` el mismo
día; repetida la carga con 15 contadores, el endpoint pasa con 100 % de respuestas válidas.

Las cifras son de un equipo de desarrollo con la base en otra región. Sirven para comparar
corridas entre sí, no como capacidad de producción.
