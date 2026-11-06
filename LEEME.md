# WiFi Casa 2.6.2

## Novedades de la 2.6.2

- Nueva ficha del **Orange Livebox 7** sacada de la ayuda oficial de Orange (camino exacto para
  bloquear un aparato). Las fichas pueden indicar ahora que vienen de **documentación oficial**.
- El formulario «Ficha de router» de GitHub admite un enlace a la ayuda oficial.

## Novedades de la 2.6.1

- El catálogo trae **13 fichas** de los routers que instalan ahora Movistar/O2, Digi, Orange/Jazztel,
  Vodafone/Lowi y MasOrange (Yoigo, MásMóvil, Pepephone, Parlem), con sus fuentes.
- Cada ficha dice si está **comprobada** con la app o es **información de Internet** sin confirmar.
  Un «no se puede bloquear» sin confirmar no impide grabar: la app invita a comprobarlo.

## Novedades de la 2.6

- **Fichas de router** en el catálogo: si un modelo permite bloquear aparatos desde su panel,
  dónde está la opción y qué no tocar. Antes de grabar, la app consulta la ficha:
  - si el router ya tiene receta, ofrece instalarla;
  - si no permite bloquear (p. ej. el Askey RTF8316VW de Movistar), lo avisa antes de empezar;
  - si sí, guía el paso 3 con el camino exacto de menús.
- **«No lo encuentro»** en la grabación: termina sin perder el tiempo, deja la grabación lista para
  una receta de solo consulta y permite **avisar al catálogo** con el formulario ya rellenado.
- **Guía en fichas ilustradas**: seis fichas con dibujo y una frase, que se pasan deslizando.
- Se pueden publicar recetas de **solo consulta** (listar aparatos) si la prueba de lectura sale bien.

## Novedades de la 2.5

- **Guía paso a paso** del recetario (sale la primera vez y en «📖 Cómo funciona»): buscar en el
  catálogo, grabar el router, pasárselo a una IA, importar y probar la receta y publicarla.
- **Archivos con el nombre del router**: al empezar a grabar se piden marca, modelo y compañía
  (de la pegatina). La grabación se llama `grabacion-marca-modelo-fecha.txt` y la receta
  `receta-marca-modelo.json`.
- **La grabación se explica sola**: empieza con unas líneas para la persona (qué hacer con ella)
  y unas instrucciones completas para cualquier IA (Claude, ChatGPT, Gemini…).
- **«Compartir con una IA»** envía la grabación como archivo a la app de la IA, con el mensaje
  ya escrito. Las recetas instaladas también se pueden enviar como archivo.

## Novedades de la 2.4.2

- Modo aprendizaje más seguro: también borra las contraseñas y claves WiFi que algunos paneles
  llevan escritas dentro de su propio código (p. ej. los Askey de Movistar).
- La grabación anota también las peticiones que el panel hace mientras carga cada página y
  guarda las páginas más completas.
- Motor de recetas v2: nueva función `xorChars` (la usan los routers Askey de Movistar para
  ofuscar usuario y contraseña).

## Novedades de la 2.4.1

- Modo aprendizaje: se muestran los avisos del panel del router ("¿Seguro que quieres aplicar
  los cambios?"). Antes se respondían solos como Cancelar y el cambio no llegaba a guardarse.

## Novedades de la 2.4

- **⏸ Pausar / ▶ Reanudar Internet** sustituyen a "Modo estudio". Debajo se indica a cuántos
  dispositivos afecta. En Ajustes puedes darle un nombre a la pausa ("Deberes", "Hora de cenar"…).
- **Recetario universal.** En *Ajustes → Modelo de router → "Mi router no está…"*:
  - **Buscar en el catálogo**: descarga la receta de tu router desde GitHub, comprueba su huella
    y la prueba (se activa cuando el catálogo esté publicado).
  - **Aprender un router nuevo**: abre el panel del router dentro de la app, te guía en 5 pasos
    y graba lo que hace. La grabación sale sin contraseñas ni claves WiFi, lista para pasársela
    a Claude u otra IA junto con las instrucciones.
  - **Importar una receta**: pegas la receta que te da la IA; la app la valida, la prueba
    leyendo y, si quieres, cortando y devolviendo Internet a un aparato. Si funciona, se instala
    y puedes **publicarla** en el catálogo para otros usuarios.

## Novedades de la 2.3

- La app recuerda todos los dispositivos que el router le ha mostrado alguna vez en cada
  ubicación. Los que el router ya no lista siguen apareciendo, al final, con "visto por última
  vez dd/MM HH:mm" y su último estado conocido.
- Al abrir la app se ve enseguida la última lista conocida, aunque el router no responda.
- Para quitar uno que ya no interesa: tócalo → "Olvidar" (solo si el router no lo muestra ahora).

## Novedades de la 2.2

- Arreglado el error "SessionTimeout al listar los dispositivos" después de cortar o devolver
  Internet. El ZTE invalida la lista en la misma sesión tras guardar un cambio; ahora la app
  refresca la lista en una sesión nueva. Si aun así no puede refrescarla, avisa de que el
  cambio sí está hecho.

## Novedades de la 2.1

- Si el router cierra la sesión a mitad (error "SessionTimeout"), la app vuelve a iniciar sesión
  sola y repite la operación una vez.
- Los errores dicen en qué paso han ocurrido (listar, leer la regla, guardar el cambio).

App Android para cortar o devolver Internet a dispositivos concretos desde el móvil.
Primer router compatible: **ZTE Livebox 6s (ZXHN F6640P)** de Parlem.
Solo funciona con el móvil conectado al WiFi de la ubicación que controlas.

## Novedades de la 2.0 (Fase 1 de "WiFi Casa Universal")

- **Varias ubicaciones** (casa, segunda residencia…): botón 📍 arriba a la derecha. Cada una
  tiene su router, su contraseña, sus nombres de dispositivos y su grupo de modo estudio.
- **Contraseña cifrada** con el almacén seguro de Android (Keystore). En la 1.0 se guardaba en claro.
- **Al actualizar no pierdes nada**: la configuración de la 1.0 pasa sola a la ubicación "Casa".
- **Motor de routers separado de la pantalla**: añadir un router nuevo ya no obliga a tocar la interfaz.
- **Recetas**: formato JSON para describir routers sin código (ver `docs/RECETAS.md`).
  En *Ajustes → Modelo de router* aparece "ZTE … · receta 1.0.0 (experimental)": hace lo mismo
  que el adaptador normal pero a través del motor de recetas. Pruébala cuando quieras; si algo
  falla, vuelve a elegir el modelo normal.

## Cómo generar el APK (en tu ordenador, una sola vez)

1. Instala **Android Studio** desde https://developer.android.com/studio (opciones por defecto).
2. Descomprime `WifiCasa.zip` en una carpeta, por ejemplo `Documentos\WifiCasa`.
3. Abre Android Studio → **Open** → elige la carpeta `WifiCasa`.
   La primera vez tarda (descarga Gradle y el SDK de Android): espera a que la barra inferior termine.
   Si te propone actualizar el "Android Gradle Plugin", acepta.
4. Menú **Build → Build App Bundle(s) / APK(s) → Build APK(s)**
   (en versiones nuevas: **Build → Generate App Bundles or APKs → Generate APKs**).
5. Cuando termine, pulsa **locate** en el aviso. El archivo es
   `app\build\outputs\apk\debug\app-debug.apk`.

## Instalarlo en los móviles

1. Pasa `app-debug.apk` al móvil (cable USB, Google Drive, o envíatelo por correo).
2. Ábrelo desde el móvil. Android pedirá permitir "instalar apps desconocidas" para esa app
   (Drive, Gmail o Archivos): acéptalo.
3. Si Play Protect avisa de que es una app desconocida, elige **Instalar de todas formas**.

## Primer uso

1. Escribe la contraseña del router (la misma que usas en 192.168.1.1). Usuario: `admin`.
   Si vienes de la 1.0, este paso no aparece: ya está todo configurado.
2. Pulsa **Actualizar**: aparecen los dispositivos con su estado.
3. Toca un dispositivo para ponerle nombre (p. ej. "Portátil del cole") y marcar
   **Incluir en el modo estudio**.
4. Uso diario:
   - **Modo estudio**: corta Internet a todos los marcados.
   - **Fin del estudio**: lo devuelve.
   - El interruptor de cada dispositivo lo controla por separado.

## Bueno saber

- La app hace lo mismo que la página del router en *mi red local → dispositivo →
  "Bloquear Internet de forma permanente" / "Permitir Internet de forma permanente"*.
  Si te resulta más cómodo, puedes comprobar o deshacer cualquier cambio desde allí.
- Si un dispositivo usa "MAC aleatoria", el router lo ve como otro aparato distinto
  y el bloqueo deja de funcionar. Desactívalo en ese dispositivo
  (Windows: Configuración → Red → WiFi → tu red → Direcciones de hardware aleatorias: Desactivado).
- Si Parlem actualiza el router, la app podría dejar de funcionar y habría que ajustarla.
- Para añadir otra ubicación: 📍 → *＋ Nueva ubicación*. Para borrarla: ⚙ Ajustes → *Eliminar*
  (solo borra los datos del móvil; no toca el router).

## Cómo está organizado el código

```
app/src/main/java/com/leira/wificasa/
├── MainActivity.java          Pantalla (solo interfaz)
├── core/                      Motor común (Java puro, sin Android)
│   ├── RouterAdapter.java     Contrato que implementa cada familia de routers
│   ├── RouterSession.java     Sesión abierta: listar, leer estado, bloquear
│   ├── RouterEngine.java      listarDispositivos · obtenerEstado · bloquear/desbloquearDispositivo
│   ├── Capability.java        Qué admite cada router (la app no da nada por hecho)
│   ├── NetDevice, AccessState, RouterCredentials, RouterException, AdapterRegistry
│   └── recipe/                Motor de recetas: Json, Recipe (validación), RecipeAdapter (ejecución)
├── adapters/zte/              Adaptador nativo del ZTE (el protocolo de la 1.0, intacto)
└── data/                      Ubicaciones (LocationStore) y cifrado (SecretBox)
app/src/main/assets/recetas/   Recetas incluidas en la app
docs/                          RECETAS.md (formato y reglas), CATALOGO.md y receta.schema.json
catalogo/                      Catálogo público: recetas, index.json y herramientas de validación
.github/                       Formulario "Nueva receta" y validación automática (GitHub Actions)
pruebas/                       Simulador del ZTE y pruebas que se ejecutan en un ordenador
```

## Pruebas (en un ordenador con Java 17+ y Python 3)

```
python3 pruebas/simulador_zte.py 8765 &
javac -d /tmp/wc $(find app/src/main/java -path '*core*' -o -path '*adapters*' | grep java$) pruebas/*.java
java -cp /tmp/wc PruebaMotor http://127.0.0.1:8765                                   # adaptador nativo
java -cp /tmp/wc PruebaMotor http://127.0.0.1:8765 app/src/main/assets/recetas/zte-zxhn-f6640p.json   # receta
java -cp /tmp/wc PruebaRecetas app/src/main/assets/recetas/zte-zxhn-f6640p.json      # validador
java -cp /tmp/wc PruebaCatalogo catalogo http://127.0.0.1:8765                       # catálogo y pruebas de recetas
java -cp /tmp/wc PruebaCaptura pruebas/panel_falso/eventos_grabados.txt              # saneado de grabaciones
java -cp /tmp/wc PruebaHorario                                                       # horarios
java -cp /tmp/wc PruebaPegatina                                                      # lectura de la pegatina
```

El simulador imita el panel del ZTE; no sustituye a probar con el router real.

## Horarios

Botón **⏰ Horarios**: por ubicación, franjas con aparatos, días y horas (pueden cruzar la
medianoche). Los ejecuta el móvil (`HorarioRunner`): a la hora de cada cambio entra en el router y
corta o devuelve Internet. Si el móvil no está en el WiFi de esa ubicación, reintenta cada 5 minutos
(con WiFi) hasta el siguiente cambio. Solo actúa en los cambios de franja, así que lo que se haga a
mano entre medias se respeta. Pendiente: usar el horario propio del router cuando la receta lo sepa
usar (funcionaría con el móvil apagado o fuera de casa).

## Avisos de conexión

En la ficha de cada aparato, **🔔 Avisarme cuando se conecte**. Cada 15 minutos (lo mínimo que
Android permite en segundo plano) `AvisoJob` consulta el router y lanza una notificación con los
aparatos vigilados que han pasado de desconectados a conectados. En Ajustes de cada ubicación,
**Avisarme si se conecta un aparato nuevo** avisa de cualquier MAC que el router nunca había mostrado.
Solo funciona con el móvil en el
WiFi de esa ubicación.

## Hoja de ruta

| Fase | Estado |
|---|---|
| 1. Base: motor separado, adaptador ZTE, ubicaciones, formato de recetas | **Hecha (esta versión)** |
| 2. Catálogo público en GitHub: índice, descarga con huella, publicación por formulario y validación automática | **Hecha en la app y en el repositorio** (falta publicarlo en tu cuenta de GitHub) |
| 3. Aprendizaje con IA: grabación saneada del panel, importar receta, pruebas controladas | **Hecha** (la IA se usa copiando y pegando) |
| 4. Acceso remoto: agente para Windows con WebRTC directo y relay TURN | Pendiente |
| 5. Ampliación: macOS, routers con acceso remoto nativo, IA por API | Pendiente |
