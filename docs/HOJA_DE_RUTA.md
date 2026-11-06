# WiFi Time Control · decisiones y hoja de ruta

Lo acordado hasta el 6 de noviembre de 2026, para no perderlo. Las funciones nuevas llegarán como
actualizaciones después de publicar.

## Decisiones tomadas

| Tema | Decisión |
|---|---|
| Nombre | **WiFi Time Control** (antes «WiFi Casa»). Paquete `com.makirfer.wifitimecontrol`: no se vuelve a cambiar. |
| «Leira» | No aparece en ningún sitio de la app ni del código. |
| Licencia | **GPL-3.0**. El nombre y el logo no se pueden usar en copias. |
| Repositorio | Público en GitHub (`Makirfer/wificontrol`); es lo que permite el catálogo compartido. |
| Precio | App **gratis** en Google Play con **compra dentro de la app** (pago único 1,99–2,99 €). Gratis: ver todos los aparatos y cortar uno a uno. De pago: pausa del grupo, horarios, varias ubicaciones. |
| Remoto | Más adelante, como **suscripción** (1–2 €/mes): puente en un servidor + «agente» en casa (Android viejo, Android TV o una cajita propia), con órdenes cifradas de punta a punta. |
| Idiomas | Castellano, catalán e inglés; preparada para añadir más con un archivo por idioma (`docs/TRADUCIR.md`). |
| Web y privacidad | GitHub Pages en `docs/` (es/ca/en). Correo de contacto: uno propio de la app (pendiente de crear). |
| Material de la tienda | Textos, capturas y vídeo con el **modo demostración** (`docs/TIENDA.md`). |
| Orden | 1) conocidos con otros routers (APK a mano) → 2) cuenta de Play Console y prueba cerrada (12 personas, 14 días) → 3) compra dentro de la app. |

## Aparato puente en casa (decidido: dejar las dos opciones preparadas)

Una sola app con **modo agente**, que sirve para:
1. **Un móvil Android viejo** enchufado en casa.
2. **Un Android TV / Google TV** (Xiaomi Mi Box, Mi TV Stick, teles con Android TV): la misma app con una
   pantalla adaptada al mando a distancia.

Por fases:
- **Fase 1 (sin servidor):** el agente ejecuta los horarios y vigila las conexiones 24 h desde casa, aunque el
  móvil de los padres esté fuera o apagado.
- **Fase 2 (suscripción):** puente para pausar y recibir avisos desde fuera, con emparejamiento por QR y
  órdenes cifradas de punta a punta. **Decidido: Firebase desde el principio**, en un único proyecto de la
  app (cuenta de Google del proyecto), conectado desde la propia app sin que el usuario configure nada.
  Datos en Europa (europe-west). Telegram queda como alternativa opcional.
- Más adelante, si hay volumen: cajita propia con Raspberry Pi Zero 2 W (el motor Java se reutiliza).

## Próxima versión: identificar los aparatos

- **Iconos por tipo**: 📱 móvil · 💻 ordenador · 🎮 consola · 📺 tele · 🔊 altavoz · 🖨 impresora · ⌚ reloj · 📷 cámara · ❔ desconocido.
- **Detección por el nombre** que da el router (p. ej. «iPhone», «android-…», «PS5-…», «LGwebOSTV», «DESKTOP-…»).
- **Marca en texto pequeño** sacada de la MAC (lista pública del IEEE, sin conexión). **Sin logos de marca** (son marcas registradas).
- Si la MAC es **privada** (aleatoria): «📱 probablemente un móvil (dirección privada)».
- Siempre «probablemente» cuando es una suposición, y el tipo se puede **cambiar a mano**.
- Formato: icono + nombre puesto por el usuario + «Marca · IP · conectado» debajo.
- Más adelante: preguntar a los aparatos (Bonjour/mDNS, UPnP, NetBIOS) para saber marca y modelo exactos.

## Ideas para diferenciarse (actualizaciones)

**Fáciles y muy visibles** (candidatas para la primera versión pública)
1. **Pausa con temporizador**: «30 minutos», «hasta mañana a las 8»; vuelve sola.
2. **Widget y botón de ajustes rápidos** para pausar sin abrir la app.
3. **Calendario escolar y festivos** por comunidad autónoma: los horarios se suspenden solos en vacaciones.

**Pequeñas y prácticas**
4. **QR del WiFi** para las visitas (la clave ya se lee de la pegatina).
5. **Reiniciar el router** desde la app (receta a receta).

**Para la versión de pago**
6. **Diario de conexiones**: cuándo estuvo conectado cada aparato (p. ej. la tablet de 1:10 a 2:30).
7. **¿Quién está en casa?** por los móviles de la familia, con reglas («cuando llegue papá, devuelve Internet a la consola»).
8. **Control compartido** entre los dos padres (con el puente remoto).
9. **Voz**: «Ok Google, pausa la consola».
10. **Página «¿Funciona con tu router?»** en la web, a partir del catálogo.

Otras pendientes: horarios guardados en el propio router (empezando por el ZTE F6640P), guía de Tailscale para
acceso remoto sin desarrollo, icono 512 px y gráfico destacado 1024×500 para la tienda.

## Frente a Fing (referencia)

Fing es un escáner de red (50 M de descargas). Bloquear, pausar y horarios son de pago (~9 $/mes u 80 $/año) y
necesitan un aparato encendido 24 h en casa. **Nuestro posicionamiento:** «control parental del WiFi de casa:
pausa, horarios y avisos, sin suscripción y sin aparatos extra». No competir como escáner de red.
