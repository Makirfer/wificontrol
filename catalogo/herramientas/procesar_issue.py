#!/usr/bin/env python3
"""
Convierte un issue "Nueva receta" en una incorporación al catálogo.

Lee el cuerpo del issue (variable de entorno CUERPO), extrae la receta y el informe,
la valida con el esquema y con el validador de la app, la guarda en catalogo/recetas/<id>.json
y regenera el índice. Escribe en resultado.md un mensaje para el issue y, por la salida
estándar, líneas clave=valor para GitHub Actions (ok, id, version).

Se ejecuta en GitHub Actions; también se puede probar en local:
    CUERPO="$(cat issue.md)" python3 catalogo/herramientas/procesar_issue.py
"""
import json
import os
import pathlib
import re
import subprocess
import sys

import jsonschema

RAIZ = pathlib.Path(__file__).resolve().parents[2]
sys.path.insert(0, str(RAIZ / "catalogo" / "herramientas"))
import generar_indice  # noqa: E402

PRUEBA_ESCRITURA_OK = "✓ Comprobado en una sesión nueva: con Internet"
PRUEBA_LECTURA_OK = ("✓ Sesión iniciada", "✓ Lista de dispositivos")


def seccion(cuerpo: str, titulo: str) -> str:
    m = re.search(r"^###\s+" + re.escape(titulo) + r"\s*$(.*?)(?=^###\s|\Z)", cuerpo, re.M | re.S)
    if not m:
        return ""
    t = m.group(1).strip()
    t = re.sub(r"^```[a-zA-Z]*\s*\n", "", t)
    t = re.sub(r"\n```\s*$", "", t)
    return t.strip()


def version_tupla(v: str):
    return tuple(int(x) for x in v.split("."))


def terminar(ok: bool, mensaje: str, rid: str = "", version: str = ""):
    (RAIZ / "resultado.md").write_text(mensaje + "\n", encoding="utf-8")
    print(f"ok={'true' if ok else 'false'}")
    print(f"id={rid}")
    print(f"version={version}")
    sys.exit(0)


def main():
    cuerpo = os.environ.get("CUERPO", "")
    texto = seccion(cuerpo, "Receta")
    informe = seccion(cuerpo, "Informe de pruebas")
    if not texto:
        terminar(False, "❌ No encuentro la receta en el apartado **Receta**. Pégala y edita el issue.")
    try:
        r = json.loads(texto)
    except json.JSONDecodeError as e:
        terminar(False, f"❌ La receta no es JSON válido: {e}")

    try:
        jsonschema.Draft202012Validator(generar_indice.ESQUEMA).validate(r)
    except jsonschema.ValidationError as e:
        terminar(False, f"❌ La receta no cumple el esquema: {e.message} (en `{'/'.join(map(str, e.path))}`)")

    rid, version = r["id"], r["version"]
    destino = RAIZ / "catalogo" / "recetas" / f"{rid}.json"
    if destino.exists():
        actual = json.loads(destino.read_text(encoding="utf-8"))
        if version_tupla(version) <= version_tupla(actual["version"]):
            terminar(False, f"❌ Ya existe `{rid}` en la versión {actual['version']}. "
                            "Para actualizarla, sube el número de `version`.", rid, version)

    # Si el informe de la app demuestra la prueba completa en un router real, se indica.
    solo_lectura = "BLOCK" not in r.get("capabilities", [])
    probada = PRUEBA_ESCRITURA_OK in informe or (solo_lectura and all(x in informe for x in PRUEBA_LECTURA_OK))
    if probada and r.get("validation", {}).get("status") in (None, "untested", "simulator"):
        fecha = re.search(r"Prueba del (\d{2})/(\d{2})/(\d{4})", informe)
        r["validation"] = {
            "date": f"{fecha.group(3)}-{fecha.group(2)}-{fecha.group(1)}" if fecha else "",
            "status": "tested-device",
            "notes": "Probada con la app WiFi Casa en un router real ("
                     + ("listar dispositivos)." if solo_lectura else "listar, cortar y devolver Internet)."),
        }
        if not r["validation"]["date"]:
            del r["validation"]["date"]

    texto_final = json.dumps(r, ensure_ascii=False, indent=2) + "\n"
    destino.write_text(texto_final, encoding="utf-8")

    java = subprocess.run([str(RAIZ / "catalogo" / "herramientas" / "validar_java.sh"), str(destino)],
                          capture_output=True, text=True)
    if java.returncode != 0:
        destino.unlink()
        salida = [l for l in java.stdout.splitlines() if l.startswith("ERROR")]
        terminar(False, "❌ El validador de la app rechaza la receta: "
                 + (salida[0].split("·", 1)[-1].strip() if salida else java.stderr[-500:]), rid, version)

    # La ficha del router enlaza la receta (se crea si no existía).
    ficha_ruta = RAIZ / "catalogo" / "routers" / f"{rid}.json"
    if ficha_ruta.exists():
        ficha = json.loads(ficha_ruta.read_text(encoding="utf-8"))
    else:
        ro = r["router"]
        ficha = {"format": "wificasa-router/1", "id": rid, "vendor": ro["vendor"], "model": ro["model"],
                 "block": {"support": "unknown" if solo_lectura else "panel"}, "reports": 1}
        for k in ("commercialName", "isps", "firmware"):
            if ro.get(k):
                ficha[k] = ro[k]
    ficha["recipe"] = rid
    ficha["updated"] = __import__("datetime").date.today().isoformat()
    ficha_ruta.write_text(json.dumps(ficha, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")

    (RAIZ / "catalogo" / "index.json").write_text(generar_indice.generar(), encoding="utf-8")
    estado = r.get("validation", {}).get("status", "untested")
    terminar(True, f"✅ Receta **{rid} {version}** válida (estado: `{estado}`). He preparado su incorporación al "
                   "catálogo; estará disponible en la app en cuanto se apruebe.", rid, version)


if __name__ == "__main__":
    main()
