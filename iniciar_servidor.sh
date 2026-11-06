#!/usr/bin/env bash
# ==============================================================================
# Barrio Seguro - Lanzador del Servidor API
# Diseñado para ejecutarse con doble clic desde el Escritorio o por terminal.
# ==============================================================================
set -euo pipefail

DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$DIR"
PY="$DIR/venv/bin/python"
export PATH="$HOME/.local/bin:/home/hca3/Android/Sdk/platform-tools:$PATH"

[ -f "$DIR/.env" ] && { set -a; . "$DIR/.env"; set +a; }

echo "============================================================"
echo "🛡️  INICIANDO SERVIDOR DE BARRIO SEGURO..."
echo "============================================================"

# 1. Asegurar Ollama (motor de IA)
if ! curl -s -m 1 http://127.0.0.1:11434/api/tags >/dev/null 2>&1; then
    echo "→ Iniciando motor de IA Ollama..."
    systemctl --user start ollama.service 2>/dev/null || nohup ollama serve >/tmp/barrio_seguro_ollama.log 2>&1 &
fi

# 2. Asegurar API Backend
PUERTO_ACTIVO=""
for p in 8001 8000; do
    if curl -s -m 1 "http://127.0.0.1:$p/health" 2>/dev/null | grep -q '"status":"ok"'; then
        PUERTO_ACTIVO="$p"
        break
    fi
done

if [ -z "$PUERTO_ACTIVO" ]; then
    echo "→ Levantando servicio del Servidor API..."
    if systemctl --user start barrio-seguro-api.service 2>/dev/null; then
        for i in {1..10}; do
            sleep 1
            for p in 8001 8000; do
                if curl -s -m 1 "http://127.0.0.1:$p/health" 2>/dev/null | grep -q '"status":"ok"'; then
                    PUERTO_ACTIVO="$p"
                    break 2
                fi
            done
        done
    fi
fi

# 3. Asegurar Túnel Cloudflare para conexión remota del celular
if ! systemctl --user is-active barrio-seguro-tunnel.service >/dev/null 2>&1; then
    echo "→ Iniciando Túnel Cloudflare para acceso remoto..."
    systemctl --user start barrio-seguro-tunnel.service 2>/dev/null || true
fi

# 4. Obtener URL del túnel Cloudflare
TUNEL_URL=""
for i in {1..5}; do
    TUNEL_URL="$(journalctl --user -u barrio-seguro-tunnel.service -n 50 --no-pager 2>/dev/null | grep -o 'https://[a-z0-9-]*\.trycloudflare\.com' | tail -1 || true)"
    [ -n "$TUNEL_URL" ] && break
    sleep 1
done
if [ -z "$TUNEL_URL" ] && [ -f /tmp/barrio_seguro_tunel.log ]; then
    TUNEL_URL="$(grep -o 'https://[a-z0-9-]*\.trycloudflare\.com' /tmp/barrio_seguro_tunel.log | tail -1 || true)"
fi

IP_LAN="$(hostname -I 2>/dev/null | awk '{print $1}')"
[ -z "$IP_LAN" ] && IP_LAN="127.0.0.1"
P="${PUERTO_ACTIVO:-8001}"

clear 2>/dev/null || true
echo "=================================================================="
echo "🛡️   BARRIO SEGURO — SERVIDOR ACTIVO Y LISTO PARA EL CELULAR"
echo "=================================================================="
echo "  ● Estado:              🟢 EN LÍNEA (FastAPI + Ollama IA)"
echo "  ● API Localhost:       http://127.0.0.1:$P"
echo "  ● Documentación API:   http://127.0.0.1:$P/docs"
echo "------------------------------------------------------------------"
echo "  📱 CELULAR (MISMA RED WI-FI):"
echo "     URL Completa:       http://$IP_LAN:$P"
echo "     👉 En la app escribe en Servidor: $IP_LAN:$P"
echo "------------------------------------------------------------------"
if [ -n "$TUNEL_URL" ]; then
echo "  🌐 CELULAR (INTERNET REMOTO / DATOS MÓVILES):"
echo "     URL Completa:       $TUNEL_URL"
echo "     👉 En la app escribe en Servidor: $TUNEL_URL"
echo "------------------------------------------------------------------"
fi
echo "  🔌 CELULAR (CABLE USB / ADB):"
echo "     Comando:            adb reverse tcp:$P tcp:$P"
echo "=================================================================="
echo "Registros en vivo (presiona Ctrl+C para opciones de salida)..."
echo ""

salir_menu() {
    echo ""
    echo "=================================================================="
    echo "  ¿Qué deseas hacer?"
    echo "  [Enter] Salir y dejar el servidor corriendo para el celular."
    echo "  [s]     Apagar el servidor y salir."
    echo "=================================================================="
    read -r -p "Opción [Enter/s]: " opcion || true
    if [ "$opcion" = "s" ] || [ "$opcion" = "S" ]; then
        echo "→ Deteniendo servicios..."
        systemctl --user stop barrio-seguro-api.service 2>/dev/null || true
        systemctl --user stop barrio-seguro-tunnel.service 2>/dev/null || true
        echo "✔ Servidor detenido."
    else
        echo "✔ El servidor sigue activo en segundo plano para tu celular."
    fi
    exit 0
}

trap salir_menu INT

# Si corre en systemd, mostrar logs en tiempo real:
if systemctl --user is-active barrio-seguro-api.service >/dev/null 2>&1; then
    journalctl --user -u barrio-seguro-api.service -f -n 25 --no-pager
else
    # Fallback si no está en systemd
    exec "$PY" run_api.py --no-reload --port "$P"
fi
