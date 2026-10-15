# Entregable de base de datos — Configuración de la app móvil

Un solo parámetro en `PARAOPER`: la **URL pública del backend**. No pertenece a ningún
módulo; lo usa `GET /api/v1/health/info` para que la app móvil se configure sola.

| # | Archivo | Qué hace | Reejecutable |
|---|---|---|---|
| 01 | `01_PARAOPER_URLSIGAPP.sql` | Da de alta `URLSIGAPP` | Sí. Si ya existe **no lo modifica** y avisa |

---

## ⚠️ Este valor es distinto en cada ambiente

`URLSIGAPP` es la URL **pública** con la que la app llega al backend de **ese** ambiente,
la que entrega infraestructura. No es la interna del cluster ni la de otro ambiente.

| Ambiente | Valor |
|---|---|
| Desarrollo | `https://sigapp-api-dev.saas.arqbs.com` |
| Empresa | `https://sigapp-api-<empresa>.saas.arqbs.com` |

El script trae el valor como `>>> DEFINIR <<<` y **se niega a insertar** si no se edita, si
no es `https`, o si trae ruta o barra final. No hay valor por defecto a propósito: un
default sería la URL de desarrollo cargada en producción.

### Texto para el minutograma

> **Paso N — Parámetro `URLSIGAPP` (URL pública del backend SIGAPP)**
>
> 1. Confirmar con infraestructura la URL pública del backend de **este** ambiente.
> 2. Editar en `01_PARAOPER_URLSIGAPP.sql` la línea `DEFINE v_urlsigapp` con esa URL, con
>    la forma `https://host` (sin barra final ni ruta).
> 3. Ejecutarlo conectado como el esquema **SRF** (el dueño), igual que los demás
>    entregables. **No** con el usuario de interfaz: para él `PARAOPER` es otra tabla.
> 4. Verificar **desde fuera de la red interna**:
>    `curl https://<url>/api/v1/health/info` debe responder `code 0`, con `urlPublica`
>    igual a la URL cargada.
>
> ⚠️ El valor cambia en cada ambiente. No copiar el de desarrollo.
>
> ⚠️ Si la base de este ambiente se clona o se refresca desde otro, `URLSIGAPP` queda con
> la URL del ambiente de origen: corregirlo con el `UPDATE` antes de usar la app.

---

## Cómo se usa

La app es **un único APK** para todos los ambientes. No trae ninguna URL compilada:

1. Lee un QR con la URL base del backend (o se digita).
2. Llama a `GET {url}/api/v1/health/info`, que es público.
3. Comprueba que `code` sea `0` y `app` sea `SIGAPP`, y guarda la URL.

```json
{
  "code": 0,
  "msg": "Consulta exitosa",
  "object": {
    "app": "SIGAPP",
    "urlPublica": "https://sigapp-api-dev.saas.arqbs.com"
  }
}
```

Si el parámetro falta o está mal escrito responde `code -1` con el motivo, y no devuelve
URL: un valor inválido no se reparte a los dispositivos.

## Cambiar la URL de un ambiente en uso

```sql
UPDATE PARAOPER SET PARACOVA = 'https://nueva-url' WHERE PARACODI = 'URLSIGAPP';
COMMIT;
```

Tiene efecto inmediato: el backend lee el parámetro en cada consulta, sin caché ni
reinicio. Mientras la URL anterior siga respondiendo, los dispositivos ya configurados ven
el valor nuevo en su siguiente consulta; si se apaga antes, hay que volver a leer el QR.

## ⚠️ Después de clonar o refrescar una base

Toda la configuración vive en la base y nada en el despliegue: es lo que permite corregir la
URL al instante, sin redesplegar ni reiniciar el pod. La contrapartida es que una base
clonada de otro ambiente trae su `PARAOPER`, y con él **la URL del ambiente de origen**. El
backend no tiene cómo detectarlo.

Tras clonar o refrescar una base, corregir `URLSIGAPP` con el `UPDATE` de arriba **antes**
de que alguien abra la app contra ese ambiente. Si no, los dispositivos que lo consulten
recibirán la URL del otro ambiente.

## Cosas que conviene saber

- **Hay dos tablas `PARAOPER`**, la del esquema SRF y la del FPL, con columnas distintas.
  El parámetro va en la del **SRF**, y el backend la lee por el sinónimo `SRF_PARAOPER`
  del usuario de interfaz. Si el endpoint dice que el parámetro no existe y "está cargado",
  se cargó en la del FPL (pasa si el script se ejecuta como `ARQGUI`, para quien `PARAOPER`
  a secas es la del FPL). Se comprueba con `db/validacion/VALIDAR_ACCESO_USUARIO_BD.sql`.
- **`PARACOVA` mide 70 caracteres.** Con el patrón `https://sigapp-api-<empresa>.saas.arqbs.com`
  quedan 36 para el nombre de la empresa.
- **El backend lee la tabla, no usa `FunBuscPara`.** La función resuelve el valor según el
  usuario y la empresa de la sesión Oracle, y este endpoint se llama sin usuario.
- **La lista blanca de hosts va en la app, no aquí.** Una lista servida por el backend no
  protege de nada: un servidor falso serviría la suya.
