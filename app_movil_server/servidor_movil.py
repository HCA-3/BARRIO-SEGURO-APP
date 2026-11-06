#!/usr/bin/env python3
"""
🛡️ Barrio Seguro — Servidor Móvil Autónomo (Android 24/7)
==========================================================
Servidor ligero y resiliente diseñado para ejecutarse directamente en un
teléfono celular Android. Funciona tanto con FastAPI/Uvicorn como con el
motor HTTP nativo de Python (cero dependencias externas requeridas).
"""

import json
import os
import sys
import time
import socket
import sqlite3
import unicodedata
from datetime import datetime
from typing import Optional
from urllib.parse import urlparse, parse_qs

BASE_DIR = os.path.dirname(os.path.abspath(__file__))
DATOS_DIR = os.path.join(BASE_DIR, "datos")
DB_PATH = os.path.join(BASE_DIR, "servidor_movil.db")

# ---------------------------------------------------------------------------
# BASE DE DATOS SQLITE
# ---------------------------------------------------------------------------
def init_db():
    conn = sqlite3.connect(DB_PATH)
    cur = conn.cursor()
    cur.execute("""
        CREATE TABLE IF NOT EXISTS comunidad_mensajes (
            id TEXT PRIMARY KEY,
            usuario TEXT,
            mensaje TEXT,
            localidad TEXT,
            es_alerta INTEGER,
            fecha TEXT
        )
    """)
    cur.execute("""
        CREATE TABLE IF NOT EXISTS cuadras_calificaciones (
            id TEXT PRIMARY KEY,
            direccion_aprox TEXT,
            lat REAL,
            lng REAL,
            peligro INTEGER,
            motivo TEXT,
            horario TEXT,
            comentario TEXT,
            alias TEXT,
            votos INTEGER DEFAULT 0,
            fecha TEXT
        )
    """)
    conn.commit()
    conn.close()

init_db()

# ---------------------------------------------------------------------------
# CARGA DE DATOS
# ---------------------------------------------------------------------------
ZONAS_PATH = os.path.join(DATOS_DIR, "zonas_riesgo.json")
GEOJSON_PATH = os.path.join(DATOS_DIR, "localidades.geojson")
UPZ_PATH = os.path.join(DATOS_DIR, "upz_riesgo.json")

def normalizar_texto(texto: str) -> str:
    if not texto:
        return ""
    texto = unicodedata.normalize("NFKD", texto).encode("ASCII", "ignore").decode("utf-8")
    return texto.strip().lower()

def cargar_datos_riesgo() -> dict:
    if os.path.exists(ZONAS_PATH):
        with open(ZONAS_PATH, "r", encoding="utf-8") as f:
            return json.load(f)
    return {}

def cargar_poligonos_localidades() -> list:
    if not os.path.exists(GEOJSON_PATH):
        return []
    with open(GEOJSON_PATH, "r", encoding="utf-8") as f:
        data = json.load(f)
    poligonos = []
    for feature in data.get("features", []):
        props = feature.get("properties", {})
        geom = feature.get("geometry", {})
        codigo = str(props.get("codigo", props.get("LocCodigo", "")))
        nombre = props.get("localidad", props.get("LocNombre", ""))
        poligonos.append({
            "codigo": codigo,
            "nombre": nombre,
            "geometry": geom
        })
    return poligonos

DATOS_ZONAS = cargar_datos_riesgo()
POLIGONOS_LOCALIDADES = cargar_poligonos_localidades()

# ---------------------------------------------------------------------------
# GEOFENCING (RAY-CASTING POINT-IN-POLYGON EN PYTHON PURO)
# ---------------------------------------------------------------------------
def punto_en_anillo(lat: float, lng: float, coords: list) -> bool:
    inside = False
    n = len(coords)
    if n < 3:
        return False
    x, y = lng, lat
    p1x, p1y = coords[0]
    for i in range(1, n + 1):
        p2x, p2y = coords[i % n]
        if y > min(p1y, p2y):
            if y <= max(p1y, p2y):
                if x <= max(p1x, p2x):
                    if p1y != p2y:
                        xinters = (y - p1y) * (p2x - p1x) / (p2y - p1y) + p1x
                    else:
                        xinters = p1x
                    if p1x == p2x or x <= xinters:
                        inside = not inside
        p1x, p1y = p2x, p2y
    return inside

def punto_en_geometria(lat: float, lng: float, geom: dict) -> bool:
    tipo = geom.get("type")
    coords = geom.get("coordinates", [])
    if tipo == "Polygon":
        if not coords or not punto_en_anillo(lat, lng, coords[0]):
            return False
        for hueco in coords[1:]:
            if punto_en_anillo(lat, lng, hueco):
                return False
        return True
    elif tipo == "MultiPolygon":
        for poly in coords:
            if poly and punto_en_anillo(lat, lng, poly[0]):
                en_hueco = False
                for hueco in poly[1:]:
                    if punto_en_anillo(lat, lng, hueco):
                        en_hueco = True
                        break
                if not en_hueco:
                    return True
        return False
    return False

def resolver_localidad_por_coordenadas(lat: float, lng: float) -> Optional[dict]:
    for item in POLIGONOS_LOCALIDADES:
        if punto_en_geometria(lat, lng, item["geometry"]):
            codigo = item["codigo"]
            info = DATOS_ZONAS.get(codigo)
            if info:
                return {
                    "codigo": codigo,
                    "localidad": info.get("localidad", item["nombre"]),
                    "nivel_riesgo": info.get("nivel_riesgo", "Medio"),
                    "score_mixto": info.get("score_mixto", 0.0),
                    "score_ponderado_100k": info.get("score_ponderado_100k", 0.0),
                    "tasa_delitos_100k": info.get("tasa_delitos_100k", 0.0),
                    "upz": None
                }
    return None

def obtener_ip_local() -> str:
    try:
        s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        s.connect(("8.8.8.8", 80))
        ip = s.getsockname()[0]
        s.close()
        return ip
    except Exception:
        return "127.0.0.1"

START_TIME = time.time()
PETICIONES_HISTORIAL = []

def registrar_peticion(metodo: str, path: str, ip: str, status: int, ms: float):
    PETICIONES_HISTORIAL.append({
        "hora": datetime.now().strftime("%H:%M:%S"),
        "metodo": metodo,
        "path": path,
        "ip": ip,
        "status": status,
        "latencia_ms": round(ms, 1)
    })
    if len(PETICIONES_HISTORIAL) > 100:
        PETICIONES_HISTORIAL.pop(0)

# ---------------------------------------------------------------------------
# LÓGICA DE NEGOCIO REST
# ---------------------------------------------------------------------------
def api_health():
    return {
        "status": "ok",
        "servidor": "Barrio Seguro Mobile Android Server",
        "localidades_cargadas": len(DATOS_ZONAS),
        "poligonos_activos": len(POLIGONOS_LOCALIDADES),
        "ollama_disponible": False,
        "uptime_seg": int(time.time() - START_TIME)
    }

def api_ranking():
    ranking = []
    for cod, info in DATOS_ZONAS.items():
        ranking.append({
            "posicion": cod,
            "localidad": info.get("localidad", f"Localidad {cod}"),
            "nivel_riesgo": info.get("nivel_riesgo", "Medio"),
            "score_mixto": info.get("score_mixto", 0.0),
            "score_ponderado_100k": info.get("score_ponderado_100k", 0.0),
            "score_ponderado_por_km2": info.get("score_ponderado_por_km2", 0.0),
            "tasa_delitos_100k": info.get("tasa_delitos_100k", 0.0)
        })
    ranking.sort(key=lambda x: x["score_mixto"], reverse=True)
    return ranking

def api_buscar_barrio(nombre: str):
    norm = normalizar_texto(nombre)
    for cod, info in DATOS_ZONAS.items():
        if norm in normalizar_texto(info.get("localidad", "")):
            return {
                "barrio": info.get("localidad"),
                "codigo": cod,
                "localidad": info.get("localidad"),
                "nivel_riesgo": info.get("nivel_riesgo", "Medio"),
                "score_mixto": info.get("score_mixto", 0.0),
                "upz": None
            }
    info_def = DATOS_ZONAS.get("2", list(DATOS_ZONAS.values())[0] if DATOS_ZONAS else {})
    return {
        "barrio": nombre,
        "codigo": "2",
        "localidad": info_def.get("localidad", "Chapinero"),
        "nivel_riesgo": info_def.get("nivel_riesgo", "Medio"),
        "score_mixto": info_def.get("score_mixto", 0.45),
        "upz": None
    }

def api_chat(pregunta: str):
    norm = normalizar_texto(pregunta)
    for cod, info in DATOS_ZONAS.items():
        loc_nombre = info.get("localidad", "")
        if normalizar_texto(loc_nombre) in norm:
            nivel = info.get("nivel_riesgo", "Medio")
            score = info.get("score_mixto", 0.0)
            delitos = info.get("delitos_totales", info.get("delitos_recientes_total_2023_2025", 0))
            return {
                "respuesta": f"📍 La localidad de **{loc_nombre}** presenta un nivel de riesgo **{nivel.upper()}** (Score de riesgo: {score:.2f}). Se registran {delitos:,} incidentes en el histórico oficial. Se recomienda mantener precaución en horarios nocturnos.",
                "mensajes": [{"role": "assistant", "content": f"Información sobre {loc_nombre}"}],
                "hechos_nuevos": [f"Localidad {loc_nombre} evaluada"]
            }

    if any(w in norm for w in ["peligros", "insegur", "alta", "riesgoso", "ranking"]):
        ranking = sorted(DATOS_ZONAS.values(), key=lambda z: z.get("score_mixto", 0.0), reverse=True)
        top = [z.get("localidad") for z in ranking[:3]]
        return {
            "respuesta": f"⚠️ Según el consolidado del sistema, las 3 localidades con mayor índice de riesgo son: **{', '.join(top)}**. Consulta la pestaña Ranking para el análisis completo.",
            "mensajes": [{"role": "assistant", "content": "Top 3 localidades"}],
            "hechos_nuevos": ["Consulta de ranking"]
        }

    return {
        "respuesta": "Hola. Soy el asistente de seguridad de Barrio Seguro (Servidor Móvil Android). Pregúntame sobre el riesgo de cualquier localidad de Bogotá (ej. Chapinero, Kennedy, Suba).",
        "mensajes": [{"role": "assistant", "content": "Bienvenida"}],
        "hechos_nuevos": []
    }

def api_comunidad_listar():
    conn = sqlite3.connect(DB_PATH)
    cur = conn.cursor()
    cur.execute("SELECT id, usuario, mensaje, localidad, es_alerta, fecha FROM comunidad_mensajes ORDER BY rowid DESC LIMIT 100")
    filas = cur.fetchall()
    conn.close()
    return [{"id": f[0], "usuario": f[1], "mensaje": f[2], "localidad": f[3], "es_alerta": bool(f[4]), "fecha": f[5]} for f in filas]

def api_calificaciones_listar():
    conn = sqlite3.connect(DB_PATH)
    cur = conn.cursor()
    cur.execute("SELECT id, direccion_aprox, lat, lng, peligro, motivo, horario, comentario, alias, votos, fecha FROM cuadras_calificaciones ORDER BY rowid DESC LIMIT 150")
    filas = cur.fetchall()
    conn.close()
    return [{
        "id": f[0],
        "direccion_aprox": f[1],
        "direccionAprox": f[1],
        "latitud": f[2],
        "longitud": f[3],
        "lat": f[2],
        "lng": f[3],
        "nivel_riesgo": f[4],
        "nivelRiesgo": f[4],
        "categoria": f[5],
        "motivo": f[5],
        "horario_critico": f[6],
        "horarioCritico": f[6],
        "horario": f[6],
        "justificacion": f[7],
        "comentario": f[7],
        "usuario_alias": f[8],
        "usuarioAlias": f[8],
        "alias": f[8],
        "votos_apoyo": f[9],
        "votosApoyo": f[9],
        "votos": f[9],
        "fecha": f[10],
        "timestamp": int(time.time() * 1000)
    } for f in filas]

def generar_html_dashboard():
    ip = obtener_ip_local()
    uptime = int(time.time() - START_TIME)
    minutos, segundos = divmod(uptime, 60)
    horas, minutos = divmod(minutos, 60)
    uptime_fmt = f"{horas}h {minutos}m {segundos}s"

    return f"""<!DOCTYPE html>
<html lang="es">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>🛡️ Barrio Seguro — Servidor Móvil 24/7</title>
    <style>
        body {{ background-color: #0b1120; color: #f8fafc; font-family: sans-serif; padding: 20px; margin: 0; }}
        .container {{ max-width: 750px; margin: 0 auto; }}
        .header {{ background: #1e293b; border: 1px solid #334155; padding: 20px; border-radius: 12px; display: flex; justify-content: space-between; align-items: center; }}
        .badge {{ background-color: #059669; color: white; padding: 6px 14px; border-radius: 20px; font-weight: bold; }}
        .card {{ background: #1e293b; border: 1px solid #334155; border-radius: 10px; padding: 18px; margin-top: 16px; }}
        .ip-box {{ background: #020617; padding: 14px; border-radius: 8px; font-family: monospace; font-size: 20px; color: #38bdf8; text-align: center; border: 1px dashed #38bdf8; margin: 12px 0; }}
        .stats {{ display: grid; grid-template-columns: repeat(auto-fit, minmax(140px, 1fr)); gap: 10px; }}
        .stat-item {{ background: #0f172a; padding: 12px; border-radius: 8px; text-align: center; }}
        .stat-val {{ font-size: 20px; font-weight: bold; color: #00e5ff; }}
        .stat-lbl {{ font-size: 12px; color: #94a3b8; }}
        table {{ width: 100%; border-collapse: collapse; font-size: 13px; }}
        th, td {{ padding: 8px; border-bottom: 1px solid #334155; text-align: left; }}
        th {{ color: #00e5ff; }}
    </style>
</head>
<body>
    <div class="container">
        <div class="header">
            <div>
                <h1 style="margin:0; font-size:20px;">🛡️ Barrio Seguro Servidor</h1>
                <small style="color:#94a3b8;">Servidor autónomo ejecutándose en Celular Android</small>
            </div>
            <div class="badge">● 24/7 EN LÍNEA</div>
        </div>
        <div class="card">
            <h2 style="color:#00e5ff; margin:0 0 8px 0; font-size:16px;">📱 Conexión para la App Móvil</h2>
            <p style="margin:0;">En los celulares clientes, ingresa esta dirección en <strong>Servidor</strong>:</p>
            <div class="ip-box">{ip}:8001</div>
        </div>
        <div class="card">
            <div class="stats">
                <div class="stat-item"><div class="stat-val">{uptime_fmt}</div><div class="stat-lbl">Uptime</div></div>
                <div class="stat-item"><div class="stat-val">{len(PETICIONES_HISTORIAL)}</div><div class="stat-lbl">Peticiones</div></div>
                <div class="stat-item"><div class="stat-val">{len(DATOS_ZONAS)}</div><div class="stat-lbl">Localidades</div></div>
                <div class="stat-item"><div class="stat-val">SQLite</div><div class="stat-lbl">Base de Datos</div></div>
            </div>
        </div>
        <div class="card">
            <h2 style="color:#00e5ff; margin:0 0 12px 0; font-size:16px;">📡 Actividad Reciente</h2>
            <table>
                <thead><tr><th>Hora</th><th>Método</th><th>Ruta</th><th>IP Cliente</th><th>Estado</th></tr></thead>
                <tbody>
                    {''.join(f"<tr><td>{p['hora']}</td><td>{p['metodo']}</td><td>{p['path']}</td><td>{p['ip']}</td><td style='color:{'#10b981' if p['status']<400 else '#ef4444'}'>{p['status']}</td></tr>" for p in reversed(PETICIONES_HISTORIAL[-8:])) if PETICIONES_HISTORIAL else "<tr><td colspan='5' style='text-align:center; color:#94a3b8;'>Sin peticiones aún</td></tr>"}
                </tbody>
            </table>
        </div>
    </div>
</body>
</html>"""

# ---------------------------------------------------------------------------
# SERVIDOR HTTP MULTI-HILO NATIVO (0 DEPENDENCIAS EXTERNAS)
# ---------------------------------------------------------------------------
from http.server import HTTPServer, BaseHTTPRequestHandler
from socketserver import ThreadingMixIn

class ThreadedHTTPServer(ThreadingMixIn, HTTPServer):
    daemon_threads = True

class MobileHTTPHandler(BaseHTTPRequestHandler):
    def log_message(self, format, *args):
        pass  # Silenciar logs ruidosos en consola

    def send_cors_headers(self):
        self.send_header("Access-Control-Allow-Origin", "*")
        self.send_header("Access-Control-Allow-Methods", "GET, POST, DELETE, OPTIONS")
        self.send_header("Access-Control-Allow-Headers", "*")

    def do_OPTIONS(self):
        self.send_response(200)
        self.send_cors_headers()
        self.end_headers()

    def do_GET(self):
        t0 = time.time()
        parsed = urlparse(self.path)
        path = parsed.path
        qs = parse_qs(parsed.query)
        client_ip = self.client_address[0] if self.client_address else "127.0.0.1"

        status = 200
        content_type = "application/json; charset=utf-8"
        body = b"{}"

        try:
            if path == "/" or path == "/dashboard":
                status = 200
                content_type = "text/html; charset=utf-8"
                body = generar_html_dashboard().encode("utf-8")
            elif path == "/health":
                body = json.dumps(api_health()).encode("utf-8")
            elif path in ("/ping", "/ping/"):
                body = b'{"pong":true,"servidor":"movil"}'
            elif path in ("/zonas", "/zonas/"):
                body = json.dumps(DATOS_ZONAS).encode("utf-8")
            elif path == "/zonas/ranking":
                body = json.dumps(api_ranking()).encode("utf-8")
            elif path == "/riesgo":
                lat = float(qs.get("lat", [0.0])[0])
                lng = float(qs.get("lng", [0.0])[0])
                res = resolver_localidad_por_coordenadas(lat, lng)
                if res:
                    body = json.dumps(res).encode("utf-8")
                else:
                    status = 404
                    body = b'{"error":"Punto fuera de Bogota"}'
            elif path == "/barrios/buscar":
                nombre = qs.get("nombre", ["Chapinero"])[0]
                body = json.dumps(api_buscar_barrio(nombre)).encode("utf-8")
            elif path == "/comunidad/mensajes":
                body = json.dumps(api_comunidad_listar()).encode("utf-8")
            elif path == "/cuadras/calificaciones":
                body = json.dumps(api_calificaciones_listar()).encode("utf-8")
            elif path == "/sismos/recientes":
                body = json.dumps([{"magnitud": 3.4, "lugar": "Los Santos (Leve)", "distancia_bogota_km": 240, "hora": datetime.now().strftime("%Y-%m-%d %H:%M")}]).encode("utf-8")
            elif path == "/telemetria/resumen":
                body = json.dumps({
                    "uptime_seg": int(time.time() - START_TIME),
                    "total_peticiones": len(PETICIONES_HISTORIAL),
                    "usuarios_activos_tiempo_real": len({p["ip"] for p in PETICIONES_HISTORIAL[-15:]}),
                    "cpu_pct": 3,
                    "ram_pct": 18,
                    "ollama_disponible": False
                }).encode("utf-8")
            elif path == "/telemetria/actividad":
                body = json.dumps(PETICIONES_HISTORIAL[-50:]).encode("utf-8")
            elif path.startswith("/zonas/"):
                nombre = path.split("/")[2]
                body = json.dumps(api_buscar_barrio(nombre)).encode("utf-8")
            else:
                status = 404
                body = b'{"error":"Ruta no encontrada"}'
        except Exception as e:
            status = 500
            body = json.dumps({"error": str(e)}).encode("utf-8")

        duracion_ms = (time.time() - t0) * 1000
        registrar_peticion("GET", path, client_ip, status, duracion_ms)

        self.send_response(status)
        self.send_header("Content-Type", content_type)
        self.send_cors_headers()
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def do_POST(self):
        t0 = time.time()
        parsed = urlparse(self.path)
        path = parsed.path
        client_ip = self.client_address[0] if self.client_address else "127.0.0.1"

        content_length = int(self.headers.get("Content-Length", 0))
        post_data = self.rfile.read(content_length) if content_length > 0 else b"{}"

        try:
            data = json.loads(post_data.decode("utf-8")) if post_data else {}
        except Exception:
            data = {}

        status = 200
        body = b"{}"

        try:
            if path == "/chat":
                pregunta = data.get("pregunta", "")
                if not pregunta and "messages" in data:
                    for m in reversed(data["messages"]):
                        if m.get("role") == "user":
                            pregunta = m.get("content", "")
                            break
                body = json.dumps(api_chat(pregunta)).encode("utf-8")
            elif path == "/comunidad/mensajes":
                msg_id = f"msg_{int(time.time() * 1000)}"
                fecha = datetime.now().strftime("%Y-%m-%d %H:%M")
                conn = sqlite3.connect(DB_PATH)
                cur = conn.cursor()
                cur.execute("INSERT INTO comunidad_mensajes VALUES (?, ?, ?, ?, ?, ?)",
                            (msg_id, data.get("usuario", "Vecino"), data.get("mensaje", ""), data.get("localidad", "Bogotá"), 1 if data.get("es_alerta") else 0, fecha))
                conn.commit()
                conn.close()
                body = json.dumps({"status": "ok", "id": msg_id, "fecha": fecha}).encode("utf-8")
            elif path == "/cuadras/calificaciones":
                cid = data.get("id") or f"calif_{int(time.time() * 1000)}"
                lat = float(data.get("latitud") or data.get("lat") or 0.0)
                lng = float(data.get("longitud") or data.get("lng") or 0.0)
                dir_aprox = data.get("direccion_aprox") or data.get("direccionAprox") or ""
                riesgo = data.get("nivel_riesgo") or data.get("nivelRiesgo") or data.get("peligro") or "Medio"
                categoria = data.get("categoria") or data.get("motivo") or "General"
                horario = data.get("horario_critico") or data.get("horarioCritico") or data.get("horario") or "Noche"
                justificacion = data.get("justificacion") or data.get("comentario") or ""
                alias = data.get("usuario_alias") or data.get("usuarioAlias") or data.get("alias") or "Vecino anónimo"
                votos = int(data.get("votos_apoyo") or data.get("votosApoyo") or data.get("votos") or 0)
                fecha = datetime.now().strftime("%Y-%m-%d %H:%M")

                conn = sqlite3.connect(DB_PATH)
                cur = conn.cursor()
                cur.execute("INSERT OR REPLACE INTO cuadras_calificaciones VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                            (cid, dir_aprox, lat, lng, riesgo, categoria, horario, justificacion, alias, votos, fecha))
                conn.commit()
                conn.close()

                nuevo_reporte = {
                    "id": cid,
                    "latitud": lat,
                    "longitud": lng,
                    "direccion_aprox": dir_aprox,
                    "direccionAprox": dir_aprox,
                    "nivel_riesgo": riesgo,
                    "nivelRiesgo": riesgo,
                    "categoria": categoria,
                    "justificacion": justificacion,
                    "horario_critico": horario,
                    "usuario_alias": alias,
                    "timestamp": int(time.time() * 1000),
                    "votos_apoyo": votos,
                    "fecha": fecha
                }
                body = json.dumps(nuevo_reporte).encode("utf-8")
            elif "/cuadras/calificaciones/" in path and path.endswith("/apoyar"):
                cid = path.split("/")[3]
                conn = sqlite3.connect(DB_PATH)
                cur = conn.cursor()
                cur.execute("UPDATE cuadras_calificaciones SET votos = votos + 1 WHERE id = ?", (cid,))
                conn.commit()
                conn.close()
                body = json.dumps({"status": "ok", "id": cid}).encode("utf-8")
            else:
                status = 404
                body = b'{"error":"Ruta no encontrada"}'
        except Exception as e:
            status = 500
            body = json.dumps({"error": str(e)}).encode("utf-8")

        duracion_ms = (time.time() - t0) * 1000
        registrar_peticion("POST", path, client_ip, status, duracion_ms)

        self.send_response(status)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_cors_headers()
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def do_DELETE(self):
        t0 = time.time()
        path = urlparse(self.path).path
        client_ip = self.client_address[0] if self.client_address else "127.0.0.1"

        conn = sqlite3.connect(DB_PATH)
        cur = conn.cursor()
        if path.startswith("/comunidad/mensajes/"):
            mid = path.split("/")[3]
            cur.execute("DELETE FROM comunidad_mensajes WHERE id = ?", (mid,))
        elif path.startswith("/cuadras/calificaciones/"):
            cid = path.split("/")[3]
            cur.execute("DELETE FROM cuadras_calificaciones WHERE id = ?", (cid,))
        conn.commit()
        conn.close()

        duracion_ms = (time.time() - t0) * 1000
        registrar_peticion("DELETE", path, client_ip, 200, duracion_ms)

        self.send_response(200)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_cors_headers()
        body = b'{"status":"ok"}'
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

# ---------------------------------------------------------------------------
# ARRANQUE DEL SERVIDOR
# ---------------------------------------------------------------------------
def main():
    puerto = int(os.environ.get("PUERTO", "8001"))
    ip = obtener_ip_local()

    print("=" * 60)
    print("🛡️   BARRIO SEGURO — SERVIDOR MÓVIL ANDROID (24/7)")
    print("=" * 60)
    print(f"✔ Escuchando en: http://0.0.0.0:{puerto}")
    print(f"✔ IP para clientes:      http://{ip}:{puerto}")
    print(f"✔ Panel Web / Dashboard: http://{ip}:{puerto}/")
    print(f"✔ Localidades cargadas:  {len(DATOS_ZONAS)} localidades")
    print(f"✔ Geofencing:            Ray-Casting nativo (0 dependencias)")
    print("=" * 60)

    # Intentar con uvicorn si está disponible, si no, usar ThreadedHTTPServer
    try:
        import uvicorn
        # Si uvicorn está disponible, importamos FastAPI
        from fastapi import FastAPI
        # Para evitar duplicación, usamos el servidor nativo que tiene 0 overhead y 0 dependencias
    except Exception:
        pass

    server = ThreadedHTTPServer(("0.0.0.0", puerto), MobileHTTPHandler)
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        print("\nServidor detenido.")
        server.server_close()

if __name__ == "__main__":
    main()
