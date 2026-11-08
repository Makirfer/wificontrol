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
| Remoto | Más adelante, como **suscripción** (1,99 €/mes; ver «Precios»): puente en un servidor + «agente» en casa (Android viejo, Android TV o una cajita propia), con órdenes cifradas de punta a punta. |
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

## Versión 3.3.0 (para la prueba cerrada con 12 personas)

Hecho el 8 de noviembre de 2026:
- **Leer el código QR** de la pegatina en la misma foto: red y clave del WiFi exactas, y así no se confunde la clave del WiFi con la contraseña del router.
- **Iconos por tipo y marca** (abajo), con el tipo cambiable a mano.
- **Pausa con temporizador**: 15 min, 30 min, 1 h, 2 h o hasta mañana a las 8; Internet vuelve sola (la devuelve el móvil, como los horarios).
- **Enviar informe** con un botón cuando falla una receta, y aviso «¿Ha cambiado tu router?» si una receta que funcionaba deja de hacerlo.
- **Enviar comentario** desde Ajustes (versión de la app, móvil y router; nunca contraseñas).
- Durante la prueba todo está desbloqueado (aún no hay compras dentro de la app).

Pendiente: poner el correo de la app en `Feedback.EMAIL` cuando exista.

## Identificar los aparatos (hecho en 3.3.0)

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

## Precios (propuesta del 6 de noviembre de 2026)

| Nivel | Precio | Qué incluye |
|---|---|---|
| Gratis | 0 € | Ver todos los aparatos, cortar uno a uno, avisos en casa |
| Pago único | 1,99–2,99 € | Pausa del grupo, horarios, varias ubicaciones |
| Suscripción «Remoto» | **1,99 €/mes** o **14,99–17,99 €/año** | Todo lo del pago único + pausar desde fuera, avisos fuera de casa, control compartido entre los padres |

- La suscripción incluye lo del pago único (nadie paga dos veces). No bajar de 1,49 €/mes.
- Neto por suscriptor tras la comisión de Google (15 %): ~1,69 €/mes. Coste de Firebase por casa: ~0 €
  (Cloud Functions gratis hasta 2 M de invocaciones/mes ≈ 2.000 casas; después 0,40 $ por millón).
- Vigilar las cuotas diarias de Firestore (20.000 escrituras, 50.000 lecturas): guardar solo la orden cifrada
  y borrarla al ejecutarse. Alerta de presupuesto de 1 € en el plan Blaze.

## Cobertura en España (objetivo: las principales compañías)

El catálogo ya tiene **32 fichas** de los routers de Movistar/O2, Vodafone/Lowi, Orange/Jazztel,
grupo MásMóvil (Yoigo, Pepephone), Digi, Parlem y Mundo R. Con **recetas probadas: 2** (ZTE F6640P y
Askey RTF8316VW). Una ficha dice qué permite el router; la receta es lo que lo hace funcionar.

Prioridad para conseguir recetas (con conocidos de cada compañía y el modo aprendizaje):
1. **Movistar / O2:** Mitrastar GPT-2541GNAC y Askey RTF3505VW (HGU), Smart WiFi 6 (Mitrastar GPT-2841GX4X5, Askey RTF8225VW).
2. **Orange / Jazztel / MásMóvil / Yoigo / Pepephone:** Livebox 6s Arcadyan, Livebox Fibra (Arltfibra6), ZTE H367A, Sagemcom Fast 5670, Livebox 6+ y 7.
3. **Vodafone / Lowi:** Vodafone Station (Technicolor CGA6444VFES, Sercomm RHG3006). El H500-s no permite cortar: solo vigilar.
4. **Digi:** ZTE H3600P, ZTE F6705, TP-Link XGB430V.

Unos **15 modelos** cubren la gran mayoría de casas con fibra en España. Los Movistar WiFi 7 solo se
gestionan bien desde la app del operador: ahí la app vigila y avisa.

## Frente a Fing (referencia)

| | Fing | WiFi Time Control |
|---|---|---|
| Qué es | Escáner de red | Control parental del WiFi de casa |
| Gratis | Ver aparatos, velocidad, puertos | Ver aparatos y cortar uno a uno |
| Pago único | No hay | 1,99–2,99 €: grupo, horarios, varias casas |
| Suscripción | ~9 $/mes u 80 $/año | 1,99 €/mes: remoto, avisos fuera, control compartido |
| Cómo corta | Aparato suyo (Fingbox o agente) en medio de la red | Se lo ordena al propio router |
| Aparato extra | Imprescindible para bloquear | No para lo básico; para lo remoto, un móvil viejo o Android TV |
| Privacidad | Cuenta y datos en sus servidores | Sin cuenta; remoto cifrado de punta a punta |
| Plataformas | Android, iOS, escritorio | Solo Android |
| Compatibilidad | Cualquier red | Solo routers con receta (punto débil) |
| Reconocer aparatos | Excelente | Básico (mejora en la próxima versión) |

Mensaje para web y redes (sin nombrar a Fing; en Google Play no se puede citar a la competencia):
«Lo que otros cobran a 9 $ al mes, aquí es gratis o por un pago único. Y desde fuera, 1,99 €.»
Riesgo a cubrir: si el operador actualiza el firmware y una receta deja de ir, avisar claro
(«tu router ha cambiado, vuelve a aprender la receta»).


**Nuestro posicionamiento:** «control parental del WiFi de casa:
pausa, horarios y avisos, sin suscripción y sin aparatos extra». No competir como escáner de red.
