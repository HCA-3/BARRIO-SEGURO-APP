#!/usr/bin/env bash
# watch_and_build.sh
# Este script vigila cambios en app/src/ y re-compila el APK automáticamente.

PROJECT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$PROJECT_DIR"

echo "=================================================="
echo " Observador de cambios activado para Barrio Seguro"
echo " La APK se compilará y versionará en: $PROJECT_DIR/apk"
echo "=================================================="

# Compilación inicial si no existe ninguna versión en apk/
if [ ! -f "$PROJECT_DIR/apk/app-latest.apk" ]; then
    echo "[!] Generando versión inicial del APK..."
    ./gradlew assembleDebug
fi

HAS_INOTIFY=false
if command -v inotifywait &> /dev/null; then
    HAS_INOTIFY=true
fi

if [ "$HAS_INOTIFY" = true ]; then
    echo "[+] Usando inotifywait para monitoreo directo..."
    while true; do
        inotifywait -r -e modify,create,delete,move "$PROJECT_DIR/app/src" "$PROJECT_DIR/app/build.gradle.kts" 2>/dev/null
        echo "[!] Cambio detectado. Iniciando compilación de APK..."
        ./gradlew assembleDebug
        echo "[✓] Esperando el próximo cambio..."
    done
else
    echo "[+] Usando sondeo de tiempo de modificación (polling)..."
    LAST_CHECK=$(date +%s)
    while true; do
        sleep 3
        # Buscar archivos en app/src o build.gradle.kts modificados después de LAST_CHECK
        MODIFIED=$(find "$PROJECT_DIR/app/src" "$PROJECT_DIR/app/build.gradle.kts" -type f -newermt "@$LAST_CHECK" 2>/dev/null)
        if [ -n "$MODIFIED" ]; then
            echo "[!] Cambio detectado en:"
            echo "$MODIFIED" | head -n 3
            LAST_CHECK=$(date +%s)
            echo "[!] Iniciando compilación de APK..."
            ./gradlew assembleDebug
            echo "[✓] Esperando el próximo cambio..."
        fi
    done
fi
