# Catálogo de recetas

El catálogo vive en este mismo repositorio, en `catalogo/`:

```
catalogo/
├── index.json          Lo que descarga la app: una entrada por receta con su huella SHA-256
├── recetas/<id>.json   Las recetas
├── routers/<id>.json   Fichas de router: si permite bloquear, dónde y qué no tocar (docs/ficha.schema.json)
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

## Fichas de router

Antes de grabar un router, la app busca su ficha por marca y modelo (también por nombre
comercial y alias). La ficha dice si se puede bloquear un aparato desde el panel (`panel`),
solo desde la app de la compañía (`app-only`), de ninguna forma (`none`) o si aún no se sabe
(`unknown`), el camino de menús hasta el bloqueo y qué apartados no tocar.

Cada ficha indica de dónde sale su información (`confidence`) y sus fuentes (`sources`):
`confirmed` (comprobada con la app en un router real), `official` (documentada por la compañía
o el fabricante en su web de ayuda), `web` (publicada en otras webs) o `probable` (deducida, p. ej.
porque usa el mismo panel que otro modelo comprobado). La app solo
da por cerrado un «no se puede bloquear» si está `confirmed`; en los demás casos invita a
comprobarlo. Cuando alguien lo comprueba con la app, la ficha pasa a `confirmed`, y si
contradice algo que no estaba confirmado, manda lo comprobado.

Fichas iniciales (noviembre de 2026): routers de Movistar/O2 (Askey y MitraStar), Digi (ZTE
H3600P, H3600, F8648P), Orange/Jazztel (Livebox 6 Arcadyan), Vodafone/Lowi (Sercomm H500-s) y
MasOrange/Parlem (ZTE F6640P Livebox 6s).

Las fichas llegan igual que las recetas: desde la app («No lo encuentro» → Avisar) se abre el
formulario **Ficha de router** ya rellenado; la acción **Ficha desde issue** la valida y propone
la incorporación. Si alguien informa algo distinto de lo que ya decía la ficha, se mantiene lo
anterior y se anota para revisarlo. Al publicar una receta, su ficha se crea o se enlaza sola.

## Puesta en marcha (una sola vez)

- Crear las etiquetas `nueva-receta` y `ficha-router` en el repositorio.
- *Settings → Actions → General → Workflow permissions*: **Read and write permissions** y
  marcar **Allow GitHub Actions to create and approve pull requests**.
- Poner el usuario y el repositorio en `CatalogConfig.java` y generar la app.

## Comprobar en local

```
python3 catalogo/herramientas/generar_indice.py           # regenera index.json
python3 catalogo/herramientas/generar_indice.py --comprobar
catalogo/herramientas/validar_java.sh                      # validador de la app sobre todas las recetas
```

## Cómo aportar un router sin tener que programar

- **Con la app** (lo más fiable): Ajustes → Modelo de router → Mi router no está → Aprender. Si el
  panel no permite bloquear, «No lo encuentro» → Avisar. La ficha queda como `confirmed`.
- **Con la ayuda oficial**: abre el formulario
  [Ficha de router](../../issues/new?template=ficha-router.yml), rellena marca, modelo, si se puede
  bloquear y el camino de menús, y pega el **enlace a la ayuda oficial**. Si el enlace es de una web
  de ayuda de una compañía o fabricante conocido, la ficha queda como `official`.
- **Con un PDF o capturas de un manual**: abre un issue normal y adjúntalo; el responsable del
  catálogo preparará la ficha.
