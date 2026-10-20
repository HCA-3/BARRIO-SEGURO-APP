#!/bin/bash
# Despliegue de Barrio Seguro (backend + Ollama) en una VM Oracle Cloud
# Always Free (Ubuntu 24.04 ARM, VM.Standard.A1.Flex).
#
# Uso: conectarse por SSH a la VM y correr este script:
#   ssh -i tu_llave.pem ubuntu@<IP_PUBLICA_DE_LA_VM>
#   curl -fsSL <url_donde_subiste_este_script> -o desplegar_oracle.sh
#   bash desplegar_oracle.sh
#
# O simplemente pegar el contenido en un archivo nuevo en la VM.

set -e

echo "=== 1. Actualizar sistema e instalar dependencias base ==="
sudo apt-get update -y
sudo apt-get install -y git python3-pip python3-venv curl

echo "=== 2. Instalar Ollama (build ARM oficial, detecta la arquitectura solo) ==="
curl -fsSL https://ollama.com/install.sh | sh
sudo systemctl enable ollama
sudo systemctl start ollama
sleep 3
ollama pull llama3.2:3b

echo "=== 3. Clonar el repo (ajusta la URL si usas otro remoto/rama) ==="
if [ ! -d "BARRIO-SEGURO-APP" ]; then
    git clone https://github.com/HCA-3/BARRIO-SEGURO-APP.git
fi
cd BARRIO-SEGURO-APP

echo "=== 4. Entorno virtual + dependencias del backend ==="
python3 -m venv venv
source venv/bin/activate
pip install --upgrade pip
pip install -r Agente/requirements.txt
pip install fastapi uvicorn

echo "=== 5. Generar el indice de riesgo (zonas_riesgo.json) ==="
echo "    OJO: necesitas copiar aqui los datasets de origen (Agente/*.geojson,"
echo "    *.ods, manzanaestratificacion/, etc.) -- no estan en git por su tamano."
echo "    Sube esa carpeta por scp antes de correr esto, o copia solo el"
echo "    Agente/output/zonas_riesgo.json ya generado en tu maquina local."
# python3 Agente/ProcesarRiesgo.py

echo "=== 6. Levantar el backend como servicio systemd (sobrevive reinicios) ==="
sudo tee /etc/systemd/system/barrio-seguro-api.service > /dev/null <<EOF
[Unit]
Description=Barrio Seguro API
After=network.target ollama.service

[Service]
Type=simple
User=ubuntu
WorkingDirectory=/home/ubuntu/BARRIO-SEGURO-APP
Environment="OLLAMA_URL=http://localhost:11434/api/chat"
ExecStart=/home/ubuntu/BARRIO-SEGURO-APP/venv/bin/uvicorn Api.backend_riesgo:app --host 0.0.0.0 --port 8000
Restart=always

[Install]
WantedBy=multi-user.target
EOF

sudo systemctl daemon-reload
sudo systemctl enable barrio-seguro-api
sudo systemctl start barrio-seguro-api

echo "=== 7. Abrir el puerto 8000 en el firewall interno de la VM ==="
sudo iptables -I INPUT -p tcp --dport 8000 -j ACCEPT
sudo netfilter-persistent save 2>/dev/null || sudo apt-get install -y iptables-persistent && sudo netfilter-persistent save

echo ""
echo "=== LISTO ==="
echo "Verifica desde tu PC (no desde la VM):"
echo "  curl http://<IP_PUBLICA_DE_LA_VM>:8000/health"
echo ""
echo "Recuerda TAMBIEN abrir el puerto 8000 en la consola de Oracle Cloud:"
echo "  Networking -> Virtual Cloud Networks -> tu VCN -> Security Lists ->"
echo "  Default Security List -> Add Ingress Rule (0.0.0.0/0, TCP, puerto 8000)"
echo "Sin ESE paso en la consola, el servicio no sera visible desde fuera aunque"
echo "el iptables interno ya este abierto."
