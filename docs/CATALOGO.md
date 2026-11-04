# Catálogo de recetas

El catálogo vive en este mismo repositorio, en `catalogo/`:

```
catalogo/
├── index.json          Lo que descarga la app: una entrada por receta con su huella SHA-256
├── recetas/<id>.json   Las recetas
└── herramientas/       generar_indice.py, procesar_issue.py, ValidarReceta.java, validar_java.sh
```

La app lee `https://raw.githubusercontent.com/<usuario>/<repositorio>/main/catalogo/index.json`
(se configura en `core/catalog/CatalogConfig.java`). Antes de instalar una receta comprueba que
su huella coincide con la del índice y la pasa por el validador.

## Cómo llega una receta nueva al catálogo

1. Un usuario aprende su router con la app, consigue la receta con una IA, la prueba en la app
   (lectura + corte y devolución) y pulsa **Publicar**.
2. La app copia la receta y abre el formulario **Nueva receta** de GitHub con el informe de
   pruebas ya rellenado. El usuario pega la receta y envía.
3. La acción **Receta desde issue** la valida con el esquema y con el mismo validador que usa la
   app. Si hay errores, los explica en el propio issue. Si es correcta, crea una pull request
   que añade la receta y actualiza `index.json`. Si el informe demuestra la prueba completa en
   un router real, la receta queda marcada como `tested-device`.
4. El responsable del repositorio revisa la pull request y la acepta. Desde ese momento la
   receta aparece en la app de todos los usuarios.

## Puesta en marcha (una sola vez)

- Crear la etiqueta `nueva-receta` en el repositorio.
- *Settings → Actions → General → Workflow permissions*: **Read and write permissions** y
  marcar **Allow GitHub Actions to create and approve pull requests**.
- Poner el usuario y el repositorio en `CatalogConfig.java` y generar la app.

## Comprobar en local

```
python3 catalogo/herramientas/generar_indice.py           # regenera index.json
python3 catalogo/herramientas/generar_indice.py --comprobar
catalogo/herramientas/validar_java.sh                      # validador de la app sobre todas las recetas
```
