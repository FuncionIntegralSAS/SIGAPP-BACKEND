# Prueba del flujo de requisiciones

Recorrido completo de `/api/v1/requisiciones`, en orden, con datos **reales** del entorno de
desarrollo. Los comandos son ejecutables tal cual salvo el login.

> 💡 **Si usas Postman, importa `docs/SIGAPP_Requisiciones.postman_collection.json`** y te
> ahorras todo esto: el token se guarda solo al hacer login, los datos van en variables de
> colección y cada petición lleva su explicación. Este documento sigue siendo útil para las
> consultas SQL de verificación y para entender qué hace cada paso.

---

## Antes de empezar: dos cosas que hacen fallar la prueba

### 1. Solo hay 10 tipos gestionables

Un tipo de requisición se gestiona si tiene equivalencia `COBO/REQU` en `SRFPNACUA.EQUIVALE`.
Hoy están cargados **solo los de agua**: `2601`→`261` … `2610`→`260`. Los tipos `199`, `201`,
`2523`, `2574` y demás **no aparecen** en la bandeja ni se pueden aprobar. No es un fallo.

### 2. La bodega tiene control de responsable

El parámetro `CONREBEQ` está activo, así que **entregar** y **registrar** exigen que el
usuario del token sea responsable de la bodega. Aprobar no lo exige.

Los usuarios de login salen de `USUABADA` (`USBDCODI`), y deben estar en estado `ac` con
`USBDINFA` ≤ 2. Todos los de abajo cumplen.

⚠️ **El login distingue mayúsculas.** La consulta es `WHERE USBDCODI = :username`, sin
normalizar, así que `dflorez` va en minúscula y `JJROJAS` en mayúscula. Escribirlo distinto
da "Usuario no existe".

### Con cuál probar

| Usuario | Persona | Cubre | Bodegas |
|---|---|---|---|
| **`dflorez`** | Diva Liliana Flórez Micolta | **31 requisiciones** | F11, F571, F602, F613, F623, F635, F641, F642, F643, F661, F671, F681, F693 |
| `fmartinez` | Faisuly Martínez Sánchez | 25 | F571, F602, F613, F623, F642, F643, F661, F671, F681, F693 |
| `FPLPNACUA` | FPL Financiero | 9 | F11, F571 |
| `MGLOPEZ` | Mario Germán López Rodríguez | 6 | F571 |
| `ndelgado` | Natalia Delgado Rojas | 6 | F613, F641, F642, F643 |
| `JJROJAS` | Jhon Jairo Rojas Triviño | 4 | F671 |

**`dflorez` es el más versátil**: responsable de las 13 bodegas que tienen requisiciones
gestionables, así que con esa cuenta puedes recorrer el flujo completo de cualquiera de
ellas sin cambiar de usuario.

En la bodega `F671` de esta guía también sirven `OCARABALI`, `lcanas` y `czuluaga`.

**`aarias` no sirve para entregar**: solo es responsable de `F632`, donde no hay ninguna
requisición. Con él podrás listar, consultar y aprobar, pero el paso 6 fallará con *"Usuario
no autorizado Para realizar el Tramite"*.

### Las contraseñas

No se escriben en este archivo a propósito: va versionado y quedarían en el historial del
repositorio para siempre, y son credenciales de personas reales. Sácalas cuando las
necesites con el mismo decode que usa el login (`UsabadaRepository.buscarPorUsername`):

```sql
SELECT u.usbdcodi                              AS login,
       pkgEncriptar.funDesencriptar(u.usbdcont) AS clave,
       p.persnomb || ' ' || p.persapel          AS persona
  FROM usuabada u
  JOIN personal p ON p.persusua = u.usbdcodi
 WHERE u.usbdcodi IN ('dflorez', 'fmartinez', 'FPLPNACUA',
                      'MGLOPEZ', 'ndelgado', 'JJROJAS')
 ORDER BY u.usbdcodi;
```

> El login compara la clave descifrada en texto plano (`passFront.equals(...)`), así que lo
> que devuelva esa consulta es literalmente lo que hay que poner en la variable `clave` de
> Postman. **No la pegues en la colección ni en un commit**: Postman guarda las variables de
> colección en el archivo exportado.

Si vas a probar a menudo, lo más limpio es pedir un **usuario de pruebas propio** dado de
alta como responsable de la bodega que uses. Así nadie comparte credenciales personales y el
`MORSUSAP` de las requisiciones de prueba no queda a nombre de otra persona.

### Comprobar quién puede entregar otra requisición

```sql
SELECT u.usbdcodi AS login, p.persnomb, p.persapel
  FROM equivale e
  JOIN personal p ON p.perscodi = e.equicoeq
  JOIN usuabada u ON u.usbdcodi = p.persusua
 WHERE e.equicleq = 'COBO' AND e.equitipo = 'COBO'
   AND e.equicodi = :bodega          -- la bodega de la linea (MORSBODE)
   AND e.equiesta = 'ac'
   AND u.usbdesta = 'ac' AND NVL(u.usbdinfa, 0) <= 2;
```

---

## Los datos de la prueba

| Dato | Valor |
|---|---|
| Requisición | **`01` / `2607` / `202500459`** (REQUISICION AGUA 7) |
| Estado inicial | `in` |
| Línea única | artículo `1380205500`, bodega `F671`, secuencia `1` |
| Descripción | RESMA PAPEL BOND 75 GMS |
| Cantidad solicitada | `120` (saldo en bodega: 150, hay de sobra) |
| Clase | `1` = **consumo**, así que no lleva placas |
| Tipo de salida que generará | `267`, por equivalencia |

> **El FIFO de placas no se ejercita aquí.** No hay ninguna requisición gestionable con
> artículo devolutivo en estado `in`/`ap` en este entorno, y los de consumo no llevan placa.
> Para probarlo hace falta crear una requisición de un artículo devolutivo desde el ERP.

---

## 0. Comprobar que la aplicación tiene el módulo

`/v3/api-docs` es público, así que no hace falta token. Debe listar las nueve rutas; si no
aparecen, la instancia está corriendo una versión anterior y hay que reiniciarla.

```bash
curl -s http://localhost:8082/v3/api-docs | grep -o '"/api/v1/requisiciones[^"]*"' | sort -u
```

> Ojo con el 401: la seguridad rechaza **cualquier** ruta no pública antes de resolverla, así
> que una ruta inexistente también responde 401. Un 401 no confirma que el endpoint exista.

---

## 1. Login: configurar `BASE` y `TOKEN`

Elige una de las dos vías. **Los comandos del resto del documento están en sintaxis bash**,
así que si usas PowerShell tendrás que traducirlos con el ayudante de la opción B.

### Opción A — Git Bash (los comandos funcionan tal cual)

Lo tienes instalado en `D:\Git`. Ábrelo con:

```
D:\Git\git-bash.exe
```

O desde PowerShell: `& "D:\Git\git-bash.exe"`. Dentro, `cd` al proyecto y:

```bash
BASE=http://localhost:8082

TOKEN=$(curl -s -X POST "$BASE/api/v1/auth/login" \
  -H "Content-Type: application/json" \
  -d '{"username":"aarias","password":"TU_CLAVE"}' \
  | grep -o '"token":"[^"]*' | head -1 | cut -d'"' -f4)

echo "${TOKEN:0:40}..."
```

Las variables duran lo que dure la ventana. Si `TOKEN` sale vacío, repite el `curl` sin el
pipe para ver el error.

### Opción B — PowerShell

⚠️ **En PowerShell, `curl` NO es curl**: es un alias de `Invoke-WebRequest` y los flags
`-s -H -d` fallan con un error confuso. Habría que escribir `curl.exe`, pero además pasarle
JSON desde PowerShell 5.1 es frágil por el escapado de comillas. Sale mejor usar los cmdlets
nativos.

Pega esto una vez para dejar el entorno listo:

```powershell
$BASE = "http://localhost:8082"

$login = @{ username = "aarias"; password = "TU_CLAVE" } | ConvertTo-Json
$auth  = Invoke-RestMethod -Uri "$BASE/api/v1/auth/login" -Method Post `
                           -ContentType "application/json" -Body $login
$TOKEN = $auth.token

"Token: " + $TOKEN.Substring(0, 30) + "..."

# Ayudante: llama a la API y MUESTRA EL CUERPO incluso cuando la respuesta es un error.
# Hace falta porque Invoke-RestMethod lanza excepcion ante un 4xx/5xx y se perderia el
# mensaje del ERP, que es justo lo que interesa leer.
function Req {
    param(
        [string] $Ruta,
        [string] $Metodo = "Get",
        $Cuerpo
    )
    $par = @{
        Uri     = "$BASE$Ruta"
        Method  = $Metodo
        Headers = @{ Authorization = "Bearer $TOKEN" }
    }
    if ($null -ne $Cuerpo) {
        $par.ContentType = "application/json"
        $par.Body        = ($Cuerpo | ConvertTo-Json -Depth 6)
    }
    try {
        Invoke-RestMethod @par | ConvertTo-Json -Depth 6
    } catch {
        $resp = $_.Exception.Response
        if ($resp) {
            (New-Object System.IO.StreamReader($resp.GetResponseStream())).ReadToEnd()
        } else {
            $_.Exception.Message
        }
    }
}
```

Con eso, cada paso del documento se traduce así:

```powershell
# GET  (pasos 2, 3, 4, 7, 13)
Req "/api/v1/requisiciones/tipos"
Req "/api/v1/requisiciones?estado=in&empresa=01&desde=2025-01-01"
Req "/api/v1/requisiciones/01/2607/202500459"

# PUT con cuerpo (pasos 5, 6, 11)
Req "/api/v1/requisiciones/01/2607/202500459/aprobar" -Metodo Put -Cuerpo @{
    lineas = @( @{ articulo = "1380205500"; bodega = "F671"; secuencia = 1; cantidad = 100 } )
}

# Firmas (pasos 8 y 9)
$FIRMA = 'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNkYPhfDwAChwGA60e6kgAAAABJRU5ErkJggg=='
Req "/api/v1/requisiciones/01/2607/202500459/firmar" -Metodo Put -Cuerpo @{
    tipo = "SA"; persona = "16822134"; firma = $FIRMA
}

# Registrar recibiendo todo (paso 11 con cuerpo vacio)
Req "/api/v1/requisiciones/01/2607/202500459/registrar" -Metodo Put -Cuerpo @{}

# La imagen de la firma (paso 10) va aparte, porque no es JSON
Invoke-WebRequest -Uri "$BASE/api/v1/requisiciones/01/2607/202500459/firmas/SA" `
                  -Headers @{ Authorization = "Bearer $TOKEN" } -OutFile firma-salida.png
```

### Comprobar que quedó bien (cualquiera de las dos)

Si esto devuelve los 10 tipos de agua, el token y la URL están bien:

```bash
curl -s "$BASE/api/v1/requisiciones/tipos" -H "Authorization: Bearer $TOKEN"
```
```powershell
Req "/api/v1/requisiciones/tipos"
```

Un `401` significa token ausente, caducado o mal copiado. El access token dura **8 horas**.

---

## 2. Tipos gestionables

Debe devolver 10 tipos de agua. **Si sale vacío**, falta la equivalencia y el resto del flujo
no funcionará.

```bash
curl -s "$BASE/api/v1/requisiciones/tipos" \
  -H "Authorization: Bearer $TOKEN"
```

---

## 3. Bandeja: requisiciones pendientes de aprobar

Una fila por documento, no por artículo.

```bash
curl -s "$BASE/api/v1/requisiciones?estado=in&empresa=01&desde=2025-01-01" \
  -H "Authorization: Bearer $TOKEN"
```

La ventana por defecto son 90 días; `desde` la amplía. Sin él no verías las de 2025.

---

## 4. Detalle antes de tocar nada

```bash
curl -s "$BASE/api/v1/requisiciones/01/2607/202500459" \
  -H "Authorization: Bearer $TOKEN"
```

Fíjate en `estado: "in"`, `solicitada: 120`, el resto de cantidades en cero, y `firmas: []`
—todavía no existen—.

---

## 5. Aprobar 100 de 120 (parcial)

```bash
curl -s -X PUT "$BASE/api/v1/requisiciones/01/2607/202500459/aprobar" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
        "lineas": [
          { "articulo": "1380205500", "bodega": "F671", "secuencia": 1, "cantidad": 100 }
        ]
      }'
```

Tras esto: estado `ap`, `aprobada: 100`, `pendiente: 20`, y `usuarioAprueba` con tu usuario.

---

## 6. Entregar las 100

**Aquí actúa el control de responsable de bodega.**

```bash
curl -s -X PUT "$BASE/api/v1/requisiciones/01/2607/202500459/entregar" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
        "lineas": [
          { "articulo": "1380205500", "bodega": "F671", "secuencia": 1, "cantidad": 100 }
        ]
      }'
```

Efectos: estado `en`, `entregada: 100`, y **se crean las dos filas de firma vacías**.

Si el artículo fuera devolutivo, aquí el paquete habría asignado solas las placas más
antiguas del almacén; al ser de consumo, no hay ninguna.

---

## 7. Detalle: ya hay firmas pendientes

```bash
curl -s "$BASE/api/v1/requisiciones/01/2607/202500459" \
  -H "Authorization: Bearer $TOKEN"
```

`firmas[]` trae dos entradas con `firmada: false`: posición 1 (`SA`, salida) y 2 (`RE`, recibe).

---

## 8. Firma de salida (quien entrega)

El base64 es un PNG de 1×1 válido, suficiente para pasar la validación de `FirmaUtil`.

```bash
FIRMA='iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNkYPhfDwAChwGA60e6kgAAAABJRU5ErkJggg=='

curl -s -X PUT "$BASE/api/v1/requisiciones/01/2607/202500459/firmar" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d "{\"tipo\":\"SA\",\"persona\":\"16822134\",\"firma\":\"$FIRMA\"}"
```

---

## 9. Firma de recibe

No hay orden entre las dos: esta podría ir primero.

```bash
curl -s -X PUT "$BASE/api/v1/requisiciones/01/2607/202500459/firmar" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d "{\"tipo\":\"RE\",\"persona\":\"16801364\",\"firma\":\"$FIRMA\"}"
```

**Repite este mismo comando** para comprobar que la segunda firma en la misma posición se
rechaza con el instante de la primera.

---

## 10. Ver la firma como imagen

```bash
curl -s "$BASE/api/v1/requisiciones/01/2607/202500459/firmas/SA" \
  -H "Authorization: Bearer $TOKEN" \
  -o firma-salida.png -D -

file firma-salida.png    # debe decir PNG image data
```

---

## 11. Registrar la salida recibiendo 90 de 100 (parcial)

**Punto de no retorno**: genera el documento en el ERP y no se deshace desde la aplicación.

```bash
curl -s -X PUT "$BASE/api/v1/requisiciones/01/2607/202500459/registrar" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
        "lineas": [
          { "articulo": "1380205500", "bodega": "F671", "secuencia": 1, "cantidad": 90 }
        ]
      }'
```

Devuelve `tipoSalida: "267"` y el mensaje del ERP. Para recibir **todo** lo entregado, manda
el cuerpo vacío: `-d '{}'`.

Recuerda la consecuencia acordada: las 10 unidades entregadas y no recibidas **no vuelven al
inventario ni quedan anuladas**; la línea se registra habiendo sacado solo 90.

---

## 12. Reintento: no debe generar un segundo documento

```bash
curl -s -X PUT "$BASE/api/v1/requisiciones/01/2607/202500459/registrar" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" -d '{}'
```

Responde *"La requisicion … ya fue registrada"* sin volver a llamar al ERP.

---

## 13. Detalle final

```bash
curl -s "$BASE/api/v1/requisiciones/01/2607/202500459" \
  -H "Authorization: Bearer $TOKEN"
```

Estado `rg`, `recibida: 90`, las dos firmas con su `fechaFirma`.

---

## Verificación en base de datos

```sql
-- 1. La linea quedo registrada, y con cantidad recibida distinta de cero
SELECT morsesta, morscaso, morscaap, morscaen, morscare, morscape
  FROM moviresu
 WHERE morstdem = '01' AND morstido = '2607' AND morsrenu = 202500459;

-- 2. Las dos firmas, con su instante exacto
SELECT refiposi, reficodi, refifefi, DBMS_LOB.GETLENGTH(refifipe) bytes
  FROM fi_requfirm
 WHERE refiempr = '01' AND refitido = '2607' AND refinume = 202500459
 ORDER BY refiposi;

-- 3. El documento de salida que genero el ERP (tipo 267)
SELECT d.dointido, d.doinnume, d.doinfech, d.dointerc, COUNT(m.moinsecu) lineas
  FROM docuinve d
  JOIN moviinve m ON m.moinempr = d.doinempr
                 AND m.mointido = d.dointido
                 AND m.moinnume = d.doinnume
 WHERE d.dointido = '267'
 GROUP BY d.dointido, d.doinnume, d.doinfech, d.dointerc
 ORDER BY d.doinnume DESC
 FETCH FIRST 3 ROWS ONLY;

-- 4. La cabecera NO se toco: debe seguir en 'ac'
SELECT resuesta FROM requsumi
 WHERE resutdem = '01' AND resutido = '2607' AND resunume = 202500459;
```

---

## Casos de error que conviene probar

| Qué | Cómo | Respuesta esperada |
|---|---|---|
| Tipo sin equivalencia | `GET /01/201/499` | `code -1`, "no tiene equivalencia … no esta habilitado" |
| Número no numérico | `GET /01/2607/abc` | 400 de Spring, no llega al service |
| Entregar sin aprobar | Repetir el paso 6 sobre una requisición en `in` | `code -1`, "no se puede entregar en estado [in]" |
| Registrar sin las dos firmas | Paso 11 antes del 8 y 9 | `code -1`, "no tiene las dos firmas" |
| Aprobar más de lo solicitado | `cantidad: 999` en el paso 5 | `code -1`, "No se puede aprobar mas de lo solicitado" |
| Línea repetida | Dos veces la misma línea en el array | `code -1`, "viene repetida en la peticion" |

---

## Otras requisiciones disponibles en `in`

Por si quieres repetir el flujo:

| Requisición | Bodega | Artículo | Solicitada |
|---|---|---|---|
| `01/2603/202500605` | `F635` | `1440500130` CLORURO FERRICO | 45 |
| `01/2601/202500664` | `F571` | `1480701374` COLLARIN 1/2" PVC | 6 |

Comprueba antes quién puede entregar en esa bodega:

```sql
SELECT e.equicoeq, p.persusua, p.persnomb
  FROM equivale e LEFT JOIN personal p ON p.perscodi = e.equicoeq
 WHERE e.equicleq = 'COBO' AND e.equitipo = 'COBO'
   AND e.equicodi = 'F635' AND e.equiesta = 'ac';
```
