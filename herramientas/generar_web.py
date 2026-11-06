import os, sys, html
OUT = sys.argv[1]
REPO = "https://github.com/Makirfer/wificontrol"
CONTACT = "<a href='https://github.com/Makirfer/wificontrol/issues'>GitHub (issues)</a>"  # cambiar por el correo de la app
L = {
"es": dict(dir="", lang="es", home="index.html", priv="privacidad.html",
  nav=("Inicio","Privacidad"), title="WiFi Time Control: pausa Internet a cada aparato",
  desc="App Android para pausar y reanudar Internet a aparatos concretos de casa, con horarios y avisos, hablando directamente con tu router.",
  h1="Pausa Internet a cada aparato de casa, desde el móvil",
  lead="Sin apagar el WiFi a todos: la consola, la tablet o el portátil, con un botón, con horarios o con avisos cuando se conectan.",
  feats=[("⏸️","Pausa con un botón","Marca los aparatos y córtales Internet. Otro botón y vuelve."),
         ("⏰","Horarios automáticos","«De lunes a viernes, de 21:00 a 8:00, sin Internet»: la app lo hace sola."),
         ("🔔","Avisos de conexión","Te avisa cuando se conecta un aparato concreto o uno nuevo que no conoces."),
         ("🧾","Tu router, el de todos","Habla directamente con el router. Si el tuyo no está, puedes enseñárselo y compartirlo."),
         ("🔒","Privada","Las contraseñas se guardan cifradas en tu móvil. Sin cuentas, sin publicidad, sin servidores."),
         ("🎬","Modo demostración","Pruébala sin tocar tu router, con aparatos de ejemplo.")],
  how="Cómo funciona", steps=["Instala la app y elige el modelo de tu router (o léelo de la pegatina con una foto).",
        "Escribe la contraseña de acceso al router. Se guarda cifrada solo en tu móvil.",
        "Pon nombre a los aparatos, márcalos para la pausa y crea los horarios que quieras."],
  routers="Routers", routers_t="La app incluye un catálogo abierto de routers de las compañías españolas, que los propios usuarios completan.",
  see_routers="Ver los routers", code="Código en GitHub",
  shots="Capturas", foot="WiFi Time Control · software libre (GPL-3.0)"),
"en": dict(dir="en/", lang="en", home="index.html", priv="privacy.html",
  nav=("Home","Privacy"), title="WiFi Time Control: pause Internet per device",
  desc="Android app to pause and resume Internet for specific home devices, with schedules and alerts, talking directly to your router.",
  h1="Pause Internet for each device at home, from your phone",
  lead="Without turning WiFi off for everyone: the console, the tablet or the laptop, with one button, with schedules or with alerts when they connect.",
  feats=[("⏸️","One-button pause","Pick the devices and cut their Internet. Another button brings it back."),
         ("⏰","Automatic schedules","«Monday to Friday, 21:00 to 8:00, no Internet»: the app does it by itself."),
         ("🔔","Connection alerts","It alerts you when a specific device connects, or a new one you don't know."),
         ("🧾","Your router, everyone's","It talks directly to the router. If yours isn't supported, you can teach it and share it."),
         ("🔒","Private","Passwords are stored encrypted on your phone. No accounts, no ads, no servers."),
         ("🎬","Demo mode","Try it without touching your router, with sample devices.")],
  how="How it works", steps=["Install the app and choose your router model (or read it from the sticker with a photo).",
        "Enter the router's admin password. It's stored encrypted, only on your phone.",
        "Name the devices, add them to the pause and create the schedules you want."],
  routers="Routers", routers_t="The app includes an open catalog of routers from Spanish providers, completed by the users themselves.",
  see_routers="See the routers", code="Code on GitHub",
  shots="Screenshots", foot="WiFi Time Control · free software (GPL-3.0)"),
"ca": dict(dir="ca/", lang="ca", home="index.html", priv="privacitat.html",
  nav=("Inici","Privacitat"), title="WiFi Time Control: pausa Internet a cada aparell",
  desc="App Android per pausar i reprendre Internet a aparells concrets de casa, amb horaris i avisos, parlant directament amb el teu router.",
  h1="Pausa Internet a cada aparell de casa, des del mòbil",
  lead="Sense apagar el WiFi a tothom: la consola, la tauleta o el portàtil, amb un botó, amb horaris o amb avisos quan es connecten.",
  feats=[("⏸️","Pausa amb un botó","Marca els aparells i talla'ls Internet. Un altre botó i torna."),
         ("⏰","Horaris automàtics","«De dilluns a divendres, de 21:00 a 8:00, sense Internet»: l'app ho fa sola."),
         ("🔔","Avisos de connexió","T'avisa quan es connecta un aparell concret o un de nou que no coneixes."),
         ("🧾","El teu router, el de tothom","Parla directament amb el router. Si el teu no hi és, li pots ensenyar i compartir-lo."),
         ("🔒","Privada","Les contrasenyes es desen xifrades al teu mòbil. Sense comptes, sense publicitat, sense servidors."),
         ("🎬","Mode demostració","Prova-la sense tocar el teu router, amb aparells d'exemple.")],
  how="Com funciona", steps=["Instal·la l'app i tria el model del teu router (o llegeix-lo de l'adhesiu amb una foto).",
        "Escriu la contrasenya d'accés al router. Es desa xifrada només al teu mòbil.",
        "Posa nom als aparells, marca'ls per a la pausa i crea els horaris que vulguis."],
  routers="Routers", routers_t="L'app inclou un catàleg obert de routers de les companyies espanyoles, que completen els mateixos usuaris.",
  see_routers="Veure els routers", code="Codi a GitHub",
  shots="Captures", foot="WiFi Time Control · programari lliure (GPL-3.0)"),
}
PRIV = {
"es": ("Política de privacidad", "Última actualización: 6 de noviembre de 2026", [
 ("Quién la publica", f"WiFi Time Control es una app de software libre publicada por Makirfer. Contacto: {CONTACT}. Código: <a href='{REPO}'>{REPO}</a>."),
 ("Resumen", "La app no tiene cuentas, publicidad, analítica propia ni servidores propios. No vendemos ni compartimos datos con nadie. La única excepción son los datos técnicos de diagnóstico del lector de texto de Google, explicados más abajo."),
 ("Datos que se guardan en tu móvil", "<ul><li>Dirección, usuario y contraseña de acceso de tu router: la contraseña se guarda cifrada con el almacén de claves de Android.</li><li>Nombres de las ubicaciones, de los aparatos (con su dirección MAC e IP), la pausa, los horarios y los avisos que configures.</li><li>El PIN del bloqueo, si lo activas: solo se guarda una huella irreversible (SHA-256 con sal), nunca el PIN.</li></ul>Estos datos no salen del móvil. Se borran al desinstalar la app."),
 ("Conexiones que hace la app", "<ul><li><b>Tu router</b>, en tu red local, para ver los aparatos, cortarles o devolverles Internet.</li><li><b>GitHub</b> (raw.githubusercontent.com), para descargar el catálogo público de routers y recetas. No se envía ningún dato tuyo; GitHub puede registrar la dirección IP como cualquier web (ver su política de privacidad).</li></ul>"),
 ("Cámara y fotos", "Si usas «Leer la pegatina con una foto», la imagen se procesa en el propio móvil con el reconocimiento de texto de Google ML Kit, sin conexión, y se borra al terminar. La app no guarda ni envía fotos ni el texto leído. Al usar el lector, Google ML Kit envía a Google datos técnicos de diagnóstico y uso (modelo y sistema del móvil, nombre y versión de la app, un identificador de instalación que no identifica a la persona, tiempos y errores), cifrados, según su <a href='https://developers.google.com/ml-kit/android-data-disclosure'>política de datos</a>."),
 ("Modo aprendizaje y grabaciones", "Al enseñar un router nuevo, la app graba lo que hace su panel. Antes de guardar la grabación se borran la contraseña, las claves WiFi y otros datos secretos. La grabación se queda en tu móvil: solo sale si tú decides compartirla (por ejemplo, con una IA para crear la receta)."),
 ("Publicar en el catálogo", "Si decides publicar una receta o avisar de un router, se abre GitHub con un formulario que envías tú, con tu cuenta de GitHub. Lo que publiques será público; no incluye contraseñas."),
 ("Notificaciones y permisos", "La app pide permiso para notificaciones (avisos de conexión), alarmas exactas (horarios), arranque del móvil (volver a programar los horarios) y huella o cara (bloqueo opcional). Ninguno se usa para recoger datos."),
 ("Menores", "La app la usan adultos para gestionar su red. No recoge datos de nadie, tampoco de menores."),
 ("Cambios", "Si esta política cambia, se publicará aquí con la fecha nueva."),
]),
"en": ("Privacy policy", "Last updated: 6 November 2026", [
 ("Who publishes it", f"WiFi Time Control is a free-software app published by Makirfer. Contact: {CONTACT}. Code: <a href='{REPO}'>{REPO}</a>."),
 ("Summary", "The app has no accounts, ads, analytics or servers of its own. We don't sell or share data with anyone. The only exception is technical diagnostic data from Google's text reader, explained below."),
 ("Data stored on your phone", "<ul><li>Your router's address, username and admin password: the password is stored encrypted with the Android Keystore.</li><li>Names of locations and devices (with their MAC and IP address), the pause, schedules and alerts you set up.</li><li>The lock PIN, if you enable it: only an irreversible salted SHA-256 hash is stored, never the PIN.</li></ul>This data never leaves the phone and is deleted when you uninstall the app."),
 ("Connections the app makes", "<ul><li><b>Your router</b>, on your local network, to list devices and cut or restore their Internet.</li><li><b>GitHub</b> (raw.githubusercontent.com), to download the public catalog of routers and recipes. None of your data is sent; GitHub may log the IP address like any website (see its privacy policy).</li></ul>"),
 ("Camera and photos", "If you use «Read the sticker with a photo», the image is processed on the phone with Google ML Kit text recognition, offline, and deleted afterwards. The app doesn't store or send photos or the text read. When the reader is used, Google ML Kit sends Google technical diagnostic and usage data (phone model and OS, app name and version, a per-installation identifier that doesn't identify the person, timings and errors), encrypted, as described in its <a href='https://developers.google.com/ml-kit/android-data-disclosure'>data disclosure</a>."),
 ("Learning mode and recordings", "When teaching a new router, the app records what its panel does. Before saving, passwords, WiFi keys and other secrets are removed. The recording stays on your phone and only leaves it if you choose to share it (for example, with an AI to create the recipe)."),
 ("Publishing to the catalog", "If you choose to publish a recipe or report a router, GitHub opens with a form that you submit with your own GitHub account. What you publish is public; it contains no passwords."),
 ("Notifications and permissions", "The app asks for notifications (connection alerts), exact alarms (schedules), boot completed (to reschedule) and fingerprint/face (optional lock). None is used to collect data."),
 ("Children", "The app is used by adults to manage their network. It collects no data from anyone, including children."),
 ("Changes", "If this policy changes, it will be published here with the new date."),
]),
"ca": ("Política de privacitat", "Darrera actualització: 6 de novembre de 2026", [
 ("Qui la publica", f"WiFi Time Control és una app de programari lliure publicada per Makirfer. Contacte: {CONTACT}. Codi: <a href='{REPO}'>{REPO}</a>."),
 ("Resum", "L'app no té comptes, publicitat, analítica pròpia ni servidors propis. No venem ni compartim dades amb ningú. L'única excepció són les dades tècniques de diagnòstic del lector de text de Google, explicades més avall."),
 ("Dades que es desen al teu mòbil", "<ul><li>Adreça, usuari i contrasenya d'accés del teu router: la contrasenya es desa xifrada amb el magatzem de claus d'Android.</li><li>Noms de les ubicacions i dels aparells (amb la seva adreça MAC i IP), la pausa, els horaris i els avisos que configuris.</li><li>El PIN del bloqueig, si l'actives: només se'n desa una empremta irreversible (SHA-256 amb sal), mai el PIN.</li></ul>Aquestes dades no surten del mòbil i s'esborren en desinstal·lar l'app."),
 ("Connexions que fa l'app", "<ul><li><b>El teu router</b>, a la xarxa local, per veure els aparells i tallar-los o tornar-los Internet.</li><li><b>GitHub</b> (raw.githubusercontent.com), per descarregar el catàleg públic de routers i receptes. No s'hi envia cap dada teva; GitHub pot registrar l'adreça IP com qualsevol web (vegeu la seva política de privacitat).</li></ul>"),
 ("Càmera i fotos", "Si fas servir «Llegeix l'adhesiu amb una foto», la imatge es processa al mateix mòbil amb el reconeixement de text de Google ML Kit, sense connexió, i s'esborra en acabar. L'app no desa ni envia fotos ni el text llegit. En fer servir el lector, Google ML Kit envia a Google dades tècniques de diagnòstic i ús (model i sistema del mòbil, nom i versió de l'app, un identificador d'instal·lació que no identifica la persona, temps i errors), xifrades, segons la seva <a href='https://developers.google.com/ml-kit/android-data-disclosure'>política de dades</a>."),
 ("Mode aprenentatge i gravacions", "En ensenyar un router nou, l'app grava el que fa el seu panell. Abans de desar-ho s'esborren la contrasenya, les claus WiFi i altres secrets. La gravació es queda al mòbil: només en surt si decideixes compartir-la (per exemple, amb una IA per crear la recepta)."),
 ("Publicar al catàleg", "Si decideixes publicar una recepta o avisar d'un router, s'obre GitHub amb un formulari que envies tu, amb el teu compte de GitHub. El que publiquis serà públic; no inclou contrasenyes."),
 ("Notificacions i permisos", "L'app demana permís per a notificacions (avisos de connexió), alarmes exactes (horaris), inici del mòbil (tornar a programar els horaris) i empremta o cara (bloqueig opcional). Cap no s'usa per recollir dades."),
 ("Menors", "L'app la fan servir adults per gestionar la seva xarxa. No recull dades de ningú, tampoc de menors."),
 ("Canvis", "Si aquesta política canvia, es publicarà aquí amb la data nova."),
]),
}
def langnav(code, page):
    out=[]
    for c,n in (("es","ES"),("ca","CA"),("en","EN")):
        d=L[c]; rel=("../" if L[code]["dir"] else "")+d["dir"]+(d["home"] if page=="home" else d["priv"])
        out.append(f'<a href="{rel}"{" aria-current=\"true\"" if c==code else ""}>{n}</a>')
    return " ".join(out)
def page(code, kind, body, title, desc):
    d=L[code]; up="../" if d["dir"] else ""
    return f'''<!doctype html>
<html lang="{d["lang"]}"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1">
<title>{html.escape(title)}</title><meta name="description" content="{html.escape(desc)}">
<link rel="stylesheet" href="{up}estilo.css"></head><body><main>
<header><a class="logo" href="{d["home"]}">WiFi <span>Time</span> Control</a>
<nav><a href="{d["home"]}">{d["nav"][0]}</a><a href="{d["priv"]}">{d["nav"][1]}</a> · {langnav(code, kind)}</nav></header>
{body}
<footer>{d["foot"]} · <a href="{REPO}">GitHub</a> · <a href="{d["priv"]}">{d["nav"][1]}</a></footer>
</main></body></html>
'''
for code,d in L.items():
    os.makedirs(os.path.join(OUT,d["dir"]),exist_ok=True)
    feats="".join(f'<div class="card"><div class="ico">{i}</div><h3>{html.escape(t)}</h3><p>{html.escape(x)}</p></div>' for i,t,x in d["feats"])
    steps="".join(f"<li>{html.escape(s)}</li>" for s in d["steps"])
    up="../" if d["dir"] else ""
    shots_dir=os.path.join(OUT,"capturas")
    imgs=sorted(f for f in os.listdir(shots_dir) if f.endswith(".png")) if os.path.isdir(shots_dir) else []
    shots=(f'<h2>{d["shots"]}</h2><div class="shots">'+"".join(f'<img src="{up}capturas/{f}" alt="" loading="lazy">' for f in imgs)+"</div>") if imgs else ""
    body=f'''<h1>{html.escape(d["h1"])}</h1><p class="lead">{html.escape(d["lead"])}</p>
<a class="btn" href="{REPO}/releases">Android</a><a class="btn alt" href="{REPO}">{d["code"]}</a>
<div class="grid" style="margin-top:32px">{feats}</div>
{shots}
<h2>{d["how"]}</h2><ol class="steps">{steps}</ol>
<h2>{d["routers"]}</h2><p>{html.escape(d["routers_t"])}</p><a class="btn alt" href="{REPO}/blob/main/catalogo/ROUTERS.md">{d["see_routers"]}</a>'''
    open(os.path.join(OUT,d["dir"],d["home"]),"w").write(page(code,"home",body,d["title"],d["desc"]))
    t,date,secs=PRIV[code]
    body=f'<div class="legal"><h1>{t}</h1><p>{date}</p>'+"".join(f"<h2>{h}</h2><p>{x}</p>" if not x.startswith("<ul>") else f"<h2>{h}</h2>{x}" for h,x in secs)+"</div>"
    open(os.path.join(OUT,d["dir"],d["priv"]),"w").write(page(code,"priv",body,t+" · WiFi Time Control",t))
print("web generada")
