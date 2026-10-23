#!/usr/bin/env bash
# Arranca el backend de Barrio Seguro en este PC (Linux) sin interfaz gráfica.
#
# Uso:
#   ./iniciar_servidor.sh            -> API en el puerto 8000
#   ./iniciar_servidor.sh --tunel    -> además abre un túnel Cloudflare público
#
# Las claves de acceso (opcionales) se leen de .env en la raíz del proyecto:
#   BARRIO_SEGURO_API_KEY=...
#   BARRIO_SEGURO_ADMIN_KEY=...
set -euo pipefail

DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$DIR"
PUERTO="${PUERTO:-8000}"
PY="$DIR/venv/bin/python"
export PATH="$HOME/.local/bin:$PATH"

[ -f "$DIR/.env" ] && { set -a; . "$DIR/.env"; set +a; }

if curl -s "http://127.0.0.1:$PUERTO/health" >/dev/null 2>&1; then
    echo "✔ El servidor ya está corriendo en el puerto $PUERTO."
    exit 0
fi

# Ollama (motor de IA). Si no responde, se intenta levantar en segundo plano.
if ! curl -s http://127.0.0.1:11434/api/tags >/dev/null 2>&1; then
    if command -v ollama >/dev/null; then
        echo "→ Iniciando Ollama..."
        nohup ollama serve >/tmp/barrio_seguro_ollama.log 2>&1 &
    else
        echo "⚠ Ollama no está instalado: el chat usará el respaldo sin IA."
    fi
fi

if [ "${1:-}" = "--tunel" ] && command -v cloudflared >/dev/null; then
    echo "→ Abriendo túnel Cloudflare (la URL pública aparece en /tmp/barrio_seguro_tunel.log)..."
    nohup cloudflared tunnel --url "http://127.0.0.1:$PUERTO" >/tmp/barrio_seguro_tunel.log 2>&1 &
    ( sleep 8; grep -o 'https://[a-z0-9-]*\.trycloudflare\.com' /tmp/barrio_seguro_tunel.log | head -1 | sed 's/^/🌐 URL pública: /' ) &
fi

IP_LAN="$(hostname -I 2>/dev/null | awk '{print $1}')"
echo "→ Iniciando API en http://127.0.0.1:$PUERTO  (red local: http://$IP_LAN:$PUERTO)"
exec "$PY" run_api.py --no-reload --port "$PUERTO"
