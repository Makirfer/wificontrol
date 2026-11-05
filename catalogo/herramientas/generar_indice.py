#!/usr/bin/env python3
"""
Valida todas las recetas de catalogo/recetas con el esquema y genera catalogo/index.json,
que es lo que descarga la app. Cada entrada lleva la huella SHA-256 del archivo exacto:
la app rechaza cualquier receta cuyo contenido no coincida.

Uso:  python3 catalogo/herramientas/generar_indice.py [--comprobar]
      --comprobar  falla si index.json no está al día (para la validación automática).
"""
import hashlib
import json
import pathlib
import sys

import jsonschema

RAIZ = pathlib.Path(__file__).resolve().parents[2]
CATALOGO = RAIZ / "catalogo"
ESQUEMA = json.loads((RAIZ / "docs" / "receta.schema.json").read_text(encoding="utf-8"))
ESQUEMA_FICHA = json.loads((RAIZ / "docs" / "ficha.schema.json").read_text(encoding="utf-8"))


def entrada(ruta: pathlib.Path) -> dict:
    texto = ruta.read_text(encoding="utf-8")
    r = json.loads(texto)
    jsonschema.Draft202012Validator(ESQUEMA).validate(r)
    if ruta.stem != r["id"]:
        raise ValueError(f"{ruta.name}: el nombre del archivo debe ser <id>.json ({r['id']}.json)")
    router = r["router"]
    return {
        "id": r["id"],
        "version": r["version"],
        "vendor": router["vendor"],
        "model": router["model"],
        "commercialName": router.get("commercialName", ""),
        "isps": router.get("isps", []),
        "detect": router.get("detect", []),
        "capabilities": r["capabilities"],
        "status": r.get("validation", {}).get("status", "untested"),
        "sha256": hashlib.sha256(texto.encode("utf-8")).hexdigest(),
        "path": f"recetas/{ruta.name}",
    }


def ficha(ruta: pathlib.Path, recetas: set) -> dict:
    f = json.loads(ruta.read_text(encoding="utf-8"))
    jsonschema.Draft202012Validator(ESQUEMA_FICHA).validate(f)
    if ruta.stem != f["id"]:
        raise ValueError(f"{ruta.name}: el nombre del archivo debe ser <id>.json ({f['id']}.json)")
    if "recipe" in f and f["recipe"] not in recetas:
        raise ValueError(f"{ruta.name}: la receta {f['recipe']} no existe en el catálogo")
    return f


def generar() -> str:
    entradas = [entrada(p) for p in sorted((CATALOGO / "recetas").glob("*.json"))]
    ids = [e["id"] for e in entradas]
    if len(ids) != len(set(ids)):
        raise ValueError("Hay dos recetas con el mismo id")
    fichas = [ficha(p, set(ids)) for p in sorted((CATALOGO / "routers").glob("*.json"))]
    indice = {"format": "wificasa-catalog/1", "recipes": entradas, "routers": fichas}
    return json.dumps(indice, ensure_ascii=False, indent=2) + "\n"


SOPORTE = {"panel": "✅ Sí, desde el panel", "app-only": "⛔ Solo desde la app de la compañía",
           "none": "⛔ No", "unknown": "❔ Sin datos"}
CONFIANZA = {"confirmed": "Comprobado con la app", "official": "Ayuda oficial", "web": "Según Internet", "probable": "Deducido"}


def tabla() -> str:
    """Resumen legible del catálogo (catalogo/ROUTERS.md), generado a partir de las fichas."""
    d = json.loads(generar())
    filas = []
    for f in sorted(d["routers"], key=lambda f: (", ".join(f.get("isps", [])) or "~", f["vendor"], f["model"])):
        nombre = f"{f['vendor']} {f['model']}" + (f" ({f['commercialName']})" if f.get("commercialName") else "")
        donde = " → ".join(f["block"].get("where", [])[:2])
        filas.append(f"| {', '.join(f.get('isps', [])) or '—'} | {nombre} | {SOPORTE[f['block']['support']]} | "
                     f"{donde or '—'} | {'✅' if f.get('recipe') else '—'} | {CONFIANZA.get(f.get('confidence', 'web'))} |")
    return ("# Routers en el catálogo de WiFi Casa\n\n"
            "Generado automáticamente a partir de `catalogo/routers/`. ¿Falta el tuyo o algo no es correcto? "
            "Usa el modo aprendizaje de la app o el formulario [Ficha de router](../../../issues/new?template=ficha-router.yml).\n\n"
            "| Compañía | Router | ¿Bloquear un aparato? | Dónde | Receta | Fuente |\n|---|---|---|---|---|---|\n"
            + "\n".join(filas) + "\n")


if __name__ == "__main__":
    nuevo = generar()
    destino = CATALOGO / "index.json"
    resumen = CATALOGO / "ROUTERS.md"
    if "--comprobar" in sys.argv:
        actual = destino.read_text(encoding="utf-8") if destino.exists() else ""
        if resumen.exists() and resumen.read_text(encoding="utf-8") != tabla():
            print("ROUTERS.md no está al día: ejecuta catalogo/herramientas/generar_indice.py")
            sys.exit(1)
        if actual != nuevo:
            print("index.json no está al día: ejecuta catalogo/herramientas/generar_indice.py")
            sys.exit(1)
        print("Catálogo correcto")
    else:
        destino.write_text(nuevo, encoding="utf-8")
        resumen.write_text(tabla(), encoding="utf-8")
        print("index.json generado")
