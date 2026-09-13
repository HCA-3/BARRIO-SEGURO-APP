#!/bin/bash
# Script de inicio para la Aplicación de Escritorio de Control Barrio Seguro

SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )"
ROOT_DIR="$( dirname "$SCRIPT_DIR" )"

echo "🛡️ Iniciando Barrio Seguro - Aplicación de Control y Monitoreo..."

if [ -f "$ROOT_DIR/venv/bin/python" ]; then
    PYTHON_BIN="$ROOT_DIR/venv/bin/python"
else
    PYTHON_BIN="python3"
fi

export DISPLAY=${DISPLAY:-:0}

"$PYTHON_BIN" "$SCRIPT_DIR/main.py" "$@"
