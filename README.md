# WiFi Casa

App Android para **pausar y reanudar Internet a dispositivos concretos** de casa desde el móvil,
sin cortar el WiFi a todos, hablando directamente con el router.

- Funciona hoy con el **ZTE Livebox 6s (ZXHN F6640P)** de Parlem/Orange.
- Es **universal por diseño**: cada modelo de router se describe con una *receta* (un JSON
  declarativo y validado, sin código). Si tu router no está, la app **graba su panel web**,
  te ayuda a obtener la receta con una IA, **la prueba en tu router** y te deja **publicarla**
  en este catálogo para el resto de usuarios.
- Varias ubicaciones (casa, segunda residencia…), contraseñas cifradas con el Android Keystore,
  recuerda todos los dispositivos que han pasado por la red.

| Carpeta | Qué hay |
|---|---|
| `app/` | La app Android (Java, sin dependencias externas) |
| `catalogo/` | Catálogo público de recetas: `index.json`, `recetas/`, herramientas de validación |
| `docs/` | [Formato de recetas](docs/RECETAS.md), [cómo funciona el catálogo](docs/CATALOGO.md) y el esquema JSON |
| `pruebas/` | Simuladores de router y pruebas que se ejecutan en un ordenador |
| `.github/` | Formulario **Nueva receta** y validación automática con GitHub Actions |

**[Ver todos los routers del catálogo](catalogo/ROUTERS.md)** (compañía, si se puede bloquear un aparato y dónde).

¿Tienes un router que no está en el catálogo? Abre la app → ⚙ Ajustes → Modelo de router →
**Mi router no está** → *Aprender un router nuevo*.

Instrucciones para generar el APK e instalarlo: [LEEME.md](LEEME.md).

## Licencia

Copyright (C) 2026 Makirfer.

Este programa es software libre: puedes redistribuirlo y modificarlo según los términos de la
[GNU General Public License versión 3](LICENSE) o posterior. Quien publique una versión modificada
debe publicar también su código con la misma licencia.

La licencia cubre el código y el catálogo. **No** cubre el nombre ni el logotipo de la app:
las copias o versiones modificadas deben publicarse con otro nombre.
