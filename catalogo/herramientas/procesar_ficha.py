#!/usr/bin/env python3
"""
Convierte un issue "Ficha de router" en una ficha del catálogo (catalogo/routers/<id>.json).
Si la ficha ya existe, suma la confirmación y añade lo nuevo; si contradice lo que había,
lo deja anotado para que lo revise el responsable. Escribe resultado.md y ok=/id= para Actions.
    CUERPO="$(cat issue.md)" python3 catalogo/herramientas/procesar_ficha.py
"""
import datetime
import json
import os
import pathlib
import re
import sys
import unicodedata

import jsonschema

RAIZ = pathlib.Path(__file__).resolve().parents[2]
sys.path.insert(0, str(RAIZ / "catalogo" / "herramientas"))
import generar_indice  # noqa: E402
from procesar_issue import seccion  # noqa: E402

SOPORTES = {"panel", "app-only", "none", "unknown"}
NO_RESPUESTA = {"", "_no response_", "_sin respuesta_"}
# Webs de ayuda de compañías y fabricantes: lo que documentan cuenta como información oficial.
OFICIALES = ("ayuda.orange.es", "movistar.es", "vodafone.es", "digimobil.es", "masmovil.es", "yoigo.com",
             "pepephone.com", "lowi.es", "o2online.es", "parlem.com", "avatel.es", "finetwork.com",
             "tp-link.com", "asus.com", "fritz.com", "avm.de", "zyxel.com", "mi.com", "netgear.com", "huawei.com")


def slug(s: str) -> str:
    s = unicodedata.normalize("NFD", s).encode("ascii", "ignore").decode().lower()
    return re.sub(r"[^a-z0-9]+", "-", s).strip("-")[:60] or "router"


def valor(cuerpo, titulo):
    v = seccion(cuerpo, titulo).strip()
    return "" if v.lower() in NO_RESPUESTA else v


def lineas(texto):
    return [l.strip(" -•\t") for l in texto.splitlines() if l.strip(" -•\t")][:10]


def terminar(ok, mensaje, fid=""):
    (RAIZ / "resultado.md").write_text(mensaje + "\n", encoding="utf-8")
    print(f"ok={'true' if ok else 'false'}")
    print(f"id={fid}")
    sys.exit(0)


def main():
    c = os.environ.get("CUERPO", "")
    marca, modelo = valor(c, "Marca"), valor(c, "Modelo")
    soporte = valor(c, "¿Se puede bloquear un aparato?").split()[0].lower() if valor(c, "¿Se puede bloquear un aparato?") else ""
    if not marca or not modelo:
        terminar(False, "❌ Faltan la marca o el modelo del router.")
    if soporte not in SOPORTES:
        terminar(False, "❌ En «¿Se puede bloquear un aparato?» escribe panel, app-only, none o unknown.")
    texto_completo = c.lower()
    if re.search(r"contrase(ñ|n)a\s*[:=]\s*\S{6,}|password\s*[:=]\s*\S{6,}", texto_completo):
        terminar(False, "❌ Parece que el formulario incluye una contraseña. Quítala y edita el issue.")

    fid = slug(f"{marca} {modelo}")
    destino = RAIZ / "catalogo" / "routers" / f"{fid}.json"
    hoy = datetime.date.today().isoformat()
    nueva = {
        "format": "wificasa-router/1", "id": fid, "vendor": marca[:60], "model": modelo[:60],
        "block": {"support": soporte}, "updated": hoy, "reports": 1, "confidence": "confirmed",
    }
    if valor(c, "Compañía"):
        nueva["isps"] = [valor(c, "Compañía")[:40]]
    if valor(c, "Versión del firmware"):
        nueva["firmware"] = [valor(c, "Versión del firmware")[:60]]
    if lineas(valor(c, "Dónde está el bloqueo")):
        nueva["block"]["where"] = [l[:200] for l in lineas(valor(c, "Dónde está el bloqueo"))]
    fuente = valor(c, "Enlace a la ayuda oficial")
    if fuente.startswith("https://") and len(fuente) <= 300:
        nueva["sources"] = [fuente]
    if valor(c, "Notas"):
        nueva["block"]["notes"] = valor(c, "Notas")[:500]
    if lineas(valor(c, "Qué no tocar")):
        nueva["avoid"] = [l[:300] for l in lineas(valor(c, "Qué no tocar"))]

    # Desde la app (botón «No lo encuentro» → Avisar) la persona lo acaba de comprobar en su router.
    desde_app = "Comprobado con el modo aprendizaje" in valor(c, "Notas")
    oficial = any(d in u for u in nueva.get("sources", []) for d in OFICIALES)

    aviso = ""
    if destino.exists():
        f = json.loads(destino.read_text(encoding="utf-8"))
        f["reports"] = f.get("reports", 1) + 1
        conf_antes = f.get("confidence", "web")
        f["updated"] = hoy
        for v in nueva.get("sources", []):
            if v not in f.setdefault("sources", []):
                f["sources"].append(v)
        for k in ("isps", "firmware", "avoid"):
            for v in nueva.get(k, []):
                if v not in f.setdefault(k, []):
                    f[k].append(v)
        antes = f["block"]["support"]
        nivel = "confirmed" if desde_app else ("official" if oficial else "web")
        orden = ["probable", "web", "official", "confirmed"]
        if soporte == antes:
            if orden.index(nivel) > orden.index(conf_antes if conf_antes in orden else "web"):
                f["confidence"] = nivel  # más fiable que lo que había
        elif antes == "unknown" or (orden.index(nivel) > orden.index(conf_antes if conf_antes in orden else "web")
                                    and soporte != "unknown"):
            # Lo comprobado con la app manda sobre lo deducido o leído en Internet
            f["block"]["support"] = soporte
            f["confidence"] = nivel
            f["block"]["notes"] = (f["block"].get("notes", "") + f" Corregido por un usuario ({hoy}): antes decía {antes}.").strip()[:500]
        elif soporte not in (antes, "unknown"):
            aviso = (f"\n\n⚠️ Esta ficha decía `{antes}` y ahora se informa `{soporte}`. "
                     "Se mantiene lo anterior hasta que el responsable lo revise.")
            nota = f"Un usuario informa ({hoy}): {soporte}. " + nueva["block"].get("notes", "")
            f["block"]["notes"] = (f["block"].get("notes", "") + " " + nota).strip()[:500]
        for v in nueva["block"].get("where", []):
            if v not in f["block"].setdefault("where", []):
                f["block"]["where"].append(v)
        nueva = f
    if not desde_app and not destino.exists():
        # Informada a mano (no desde la app): oficial si hay enlace de una web de ayuda, si no, web.
        nueva["confidence"] = "official" if oficial else "web"
    try:
        jsonschema.Draft202012Validator(json.loads((RAIZ / "docs" / "ficha.schema.json").read_text())).validate(nueva)
    except jsonschema.ValidationError as e:
        terminar(False, f"❌ La ficha no es válida: {e.message}")
    destino.write_text(json.dumps(nueva, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    (RAIZ / "catalogo" / "index.json").write_text(generar_indice.generar(), encoding="utf-8")
    terminar(True, f"✅ Gracias. Ficha **{marca} {modelo}** preparada (bloqueo: `{nueva['block']['support']}`, "
                   f"{nueva.get('reports', 1)} confirmación(es)). Estará en la app cuando se apruebe." + aviso, fid)


if __name__ == "__main__":
    main()
