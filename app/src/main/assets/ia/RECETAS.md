# Recetas de router — formato `wificasa-recipe/1`

Una receta le explica a WiFi Casa cómo hablar con el panel web de un modelo de router:
qué páginas pedir, qué datos enviar y cómo leer las respuestas. Es un archivo JSON que
**describe** pasos; no contiene código. Así una receta escrita por otra persona o generada
con una IA se puede revisar, validar y compartir sin riesgo de que ejecute nada en el móvil.

- Esquema formal: [`receta.schema.json`](receta.schema.json) (JSON Schema 2020-12).
- Ejemplo completo y probado: [`app/src/main/assets/recetas/zte-zxhn-f6640p.json`](../app/src/main/assets/recetas/zte-zxhn-f6640p.json).
- Intérprete: `core/recipe/Recipe.java` (validación) y `core/recipe/RecipeAdapter.java` (ejecución).

## Reglas de seguridad

Estas reglas las aplica la app al cargar la receta. Si una sola falla, la receta se rechaza entera.

1. **Solo el router configurado.** Todas las peticiones van a la dirección que el usuario ha
   escrito en Ajustes. Las rutas deben empezar por `/`; se rechazan `//`, `://`, `\` y `@`.
   Una receta no puede hablar con ningún otro servidor.
2. **Vocabulario cerrado.** Solo existen los pasos, funciones, transformaciones y filtros
   listados abajo. Cualquier campo desconocido se rechaza (no se ignora).
3. **La contraseña no se mueve libremente.** `${cred.password}` solo puede aparecer en valores
   de formulario y en argumentos de funciones; nunca en rutas, cabeceras, mensajes de error ni `set`.
4. **Cabeceras sensibles reservadas.** `Host`, `Cookie`, `Content-Length` y `Proxy-*` las gestiona la app.
5. **Límites.** Archivo ≤ 256 KB, ≤ 60 pasos por operación, expresiones regulares ≤ 500
   caracteres, respuestas ≤ 2 MB, ≤ 300 dispositivos, `repeat` ≤ 10.000 caracteres,
   `call` solo desde `setBlocked` y con profundidad ≤ 2.
6. **Versión del motor.** Si `minEngine` es mayor que el motor de la app, se rechaza.
7. **Huella.** La app calcula el SHA-256 del archivo. El catálogo lo guardará con cada versión
   para detectar cualquier modificación.
8. **Comprobación tras escribir.** Después de cortar o devolver Internet, el motor vuelve a
   leer el estado (si la receta declara `READ_ACCESS`) y avisa si el router no lo aplicó.

Lo que el esquema JSON no puede expresar (reglas 3, 5 en parte, 6 y que las claves RSA usadas
existan) solo lo comprueba la app.

## Estructura

```json
{
  "format": "wificasa-recipe/1",
  "id": "fabricante-modelo",
  "version": "1.0.0",
  "minEngine": 1,
  "router": { "vendor": "ZTE", "model": "ZXHN F6640P", "commercialName": "Livebox 6s",
              "isps": ["Parlem"], "firmware": ["*"] },
  "defaults": { "host": "192.168.1.1", "user": "admin" },
  "capabilities": ["LIST_DEVICES", "READ_ACCESS", "BLOCK", "UNBLOCK"],
  "limitations": ["…"],
  "validation": { "date": "2026-10-28", "status": "simulator", "notes": "…" },
  "changelog": [{ "version": "1.0.0", "date": "2026-10-28", "changes": "…" }],
  "http": { "sessionCookie": "SID", "fixedCookies": "…", "headers": { "X-Requested-With": "XMLHttpRequest" } },
  "keys": { "router": "-----BEGIN PUBLIC KEY-----…" },
  "operations": { "login": {…}, "logout": {…}, "listDevices": {…}, "readAccess": {…}, "setBlocked": {…} }
}
```

`router.detect` (opcional): expresiones regulares que reconocen la página de inicio del router sin
iniciar sesión (p. ej. el título de la página). El catálogo las usa para proponer la receta adecuada.

`validation.status`: `untested` → `simulator` → `tested-device` → `community-verified`.
Mientras no sea `tested-device` o superior, la app la muestra como *experimental*.

Cada capacidad exige su operación: `LIST_DEVICES` → `listDevices`, `READ_ACCESS` → `readAccess`,
`BLOCK`/`UNBLOCK` → `setBlocked`. `login` es siempre obligatoria; `logout` es opcional.

## Operaciones

| Operación | Cuándo se ejecuta | Qué debe dejar |
|---|---|---|
| `login` | Al abrir sesión | Variables que necesiten las demás (tokens) |
| `logout` | Al cerrar, siempre; los errores se ignoran | — |
| `listDevices` | Listar | `devices`: cómo convertir la respuesta en dispositivos |
| `readAccess` | Leer el estado de un dispositivo | `state`: cómo convertir la respuesta en un estado |
| `setBlocked` | Cortar (`blocked`=true) o devolver | Nada; la comprobación la hace el motor |

Las variables guardadas con `save` duran toda la sesión, así que un token capturado en
`readAccess` puede usarse luego en `setBlocked`.

### `devices`
```json
"devices": {
  "from": "devXml",
  "within": { "regex": "<LISTA>(.*?)</LISTA>", "error": "El router no ha devuelto la lista." },
  "each": "<Item>(.*?)</Item>",
  "fields": {
    "mac":      { "regex": "<Mac>([^<]*)</Mac>", "transform": ["lower"] },
    "hostName": { "regex": "<Name>([^<]*)</Name>", "transform": ["xmlUnescape"] },
    "ip":       { "regex": "<Ip>([^<]*)</Ip>" },
    "active":   { "regex": "<Active>([^<]*)</Active>", "trueIf": "1" },
    "extra.devType": { "regex": "<Type>([^<]*)</Type>", "default": "1" }
  }
}
```
`mac` es obligatorio (se guarda en minúsculas). `extra.*` guarda datos propios del router que
luego se pueden usar como `${device.extra.devType}`.

### `state`
```json
"state": { "from": "rule", "regex": "<Mode>([^<]*)</Mode>",
           "map": { "0": "ALLOWED", "1": "BLOCKED", "2": "SCHEDULED" }, "default": "ALLOWED" }
```

## Pasos

Cada paso es un objeto con **una sola** clave. Las expresiones regulares usan sintaxis Java
con `DOTALL` (el punto también abarca saltos de línea); `group` por defecto es 1.

| Paso | Campos | Qué hace |
|---|---|---|
| `request` | `method` (GET/POST), `path`, `form` [[nombre, valor]…], `headers`, `save`, `expectStatus` (200) | Petición al router. Los valores de `form` se codifican automáticamente. |
| `extract` | `from`, `regex`, `save`, `group`, `occurrence` (first/last), `transform`, `required` (true), `default`, `error` | Saca un dato de una variable. |
| `fail` | `from`, `regex`, `error` | Error si la expresión **encuentra** algo. `${match}` = grupo 1. |
| `require` | `from`, `regex`, `error` | Error si la expresión **no** encuentra nada. |
| `set` | `save`, `value` | Guarda un texto. |
| `compute` | `fn`, `args`, `save` | Aplica una función permitida. |
| `choose` | `if`, `equals` ("true"), `then`, `else`, `save` | Elige un valor según una variable (p. ej. `blocked`). |
| `call` | `"readAccess"` o `"listDevices"` | Solo dentro de `setBlocked`. |

**Funciones (`compute`)**: `sha256Hex(texto)`, `sha1Hex(texto)`, `md5Hex(texto)`, `base64(texto)`
(p. ej. para la cabecera `Authorization: Basic …`: `base64("${cred.user}:${cred.password}")` guardado en una
variable y usado en `headers`), `rsaPkcs1Base64(nombreClave, texto)`,
`hexEscapeDecode(texto)` (`\x41` → `A`), `xmlUnescape(texto)`, `lower`, `upper`,
`repeat(texto, veces)`.

**Transformaciones (`transform`)**: `xmlUnescape`, `hexEscapeDecode`, `lower`, `upper`, `trim`.

## Plantillas `${…}`

| Variable | Valor |
|---|---|
| `base` | `http://192.168.1.1` (sin barra final) |
| `counter` | Número que aumenta en cada uso (anti-caché) |
| `cred.user`, `cred.password` | Credenciales (contraseña: ver regla 3) |
| `device.mac`, `device.hostName`, `device.ip`, `device.extra.X` | Dispositivo de la operación |
| `blocked` | `true` / `false` en `setBlocked` |
| `body` | Cuerpo ya codificado de la petición en curso (solo en sus `headers`) |
| `match` | Último grupo encontrado por `fail`/`require` |
| cualquier nombre guardado con `save` | |

**Filtros**, en cadena: `${device.hostName|url}`, `${token|or:}` (vacío si no existe),
`${body|sha256Hex|rsa:router}` (firma el cuerpo con la clave `router`), `lower`, `upper`.

## Cómo se ha probado la receta del ZTE

`pruebas/simulador_zte.py` imita el panel del ZTE. `pruebas/PruebaMotor.java` ejecuta los
flujos de la app (actualizar, cortar, devolver, contraseña errónea, router inalcanzable) con el
adaptador nativo y con la receta, y se comparan las peticiones con las de la versión 1:
**las tres secuencias son idénticas** (56 peticiones). `pruebas/PruebaRecetas.java` comprueba
que el validador rechaza 21 tipos de recetas peligrosas o mal formadas.

Probada en un ZTE Livebox 6s real el 31/10/2026 (listar, cortar y devolver). Se elige en *Ajustes → Modelo de router* como
"ZTE Livebox 6s (ZXHN F6640P) · receta 1.0.1".
