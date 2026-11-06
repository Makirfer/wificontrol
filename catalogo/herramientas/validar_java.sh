#!/bin/bash
# Compila el validador de la app y valida las recetas indicadas (o todas las del catálogo).
set -e
RAIZ="$(cd "$(dirname "$0")/../.." && pwd)"
OUT="$(mktemp -d)"
javac -nowarn -d "$OUT" $(find "$RAIZ/app/src/main/java/com/makirfer/wifitimecontrol/core" -name '*.java' -not -path '*/learn/*') \
  "$RAIZ/app/src/main/java/com/makirfer/wifitimecontrol/adapters/zte/"*.java "$RAIZ/catalogo/herramientas/ValidarReceta.java"
if [ "$#" -eq 0 ]; then set -- "$RAIZ"/catalogo/recetas/*.json; fi
java -cp "$OUT" ValidarReceta "$@"
