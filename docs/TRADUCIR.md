# Traducir WiFi Casa

Todos los textos de la app están en `app/src/main/res/values/strings.xml` (castellano, el idioma
principal). Cada traducción es una copia de ese archivo en su carpeta:

| Idioma    | Archivo                                   |
|-----------|-------------------------------------------|
| Castellano| `app/src/main/res/values/strings.xml`     |
| Inglés    | `app/src/main/res/values-en/strings.xml`  |
| Catalán   | `app/src/main/res/values-ca/strings.xml`  |

El móvil elige solo el idioma: si no hay traducción para el suyo, la app sale en castellano.

## Añadir un idioma

1. Copia `values/strings.xml` a `values-XX/strings.xml`, donde `XX` es el código del idioma
   (`fr` francés, `pt` portugués, `it` italiano, `de` alemán, `eu` euskera, `gl` gallego…).
2. Traduce **solo el texto** entre `>` y `<`. Puedes pasar el archivo entero a una IA o a un
   traductor automático con esta instrucción:
   > Traduce al <idioma> el texto de este archivo de Android. No cambies los `name="…"`, ni los
   > `%1$s`, `%2$d`…, ni los `\n`, ni los emojis. Escapa los apóstrofos como `\'`.
3. Borra la línea de `app_name` (el nombre de la app no se traduce).
4. Genera la APK y prueba con el móvil en ese idioma.

## Qué no se traduce (a propósito)

- **El catálogo** (fichas de routers): cada ficha está en el idioma de quien la aportó; los
  routers dependen de cada país.
- **El informe de pruebas** de las recetas y el aviso «Comprobado con el modo aprendizaje»: los
  lee el robot de GitHub que procesa las aportaciones.
- **Las instrucciones para la IA** dentro de la grabación: las IA las entienden en castellano.
- Algunos mensajes de error técnicos del motor de recetas.
