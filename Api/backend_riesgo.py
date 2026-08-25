"""
Backend HTTP para la app de Android "Barrio Seguro" (Riesgos Sociales en
Chapinero / Bogotá).

Expone por REST los datos de riesgo por localidad que calcula
../../Data/ProcesarRiesgo.py, y el agente conversacional (mismo diseño de
tool-calling que Data/agente.py: el LLM nunca "lee" cifras sueltas de un
bloque de texto largo — siempre llama funciones Python que las calculan —
porque en las pruebas un modelo local de 8B alucinaba el ranking cuando se
le pedía leerlo directamente de un JSON o de una tabla en el prompt).

Requiere:
  - Ollama corriendo localmente con un modelo que soporte tool-calling ya
    descargado (ej. "ollama pull llama3.1").
  - Que ../../Data/output/zonas_riesgo.json ya exista (correr primero
    Data/ProcesarRiesgo.py).

Uso:
  uvicorn backend_riesgo:app --host 0.0.0.0 --port 8000 --reload

--host 0.0.0.0 es necesario para que algo que no sea este mismo PC (el
emulador de Android Studio, o un celular físico en la misma wifi) pueda
conectarse:
  - Emulador de Android Studio -> http://10.0.2.2:8000
  - Celular físico en la misma red wifi que el PC -> http://<IP-LAN-del-PC>:8000
    (la IP se ve con "ipconfig" en una consola de Windows, buscar
    "Dirección IPv4" de la red wifi/ethernet activa)
"""

import json
import os
import unicodedata
from typing import Any

import requests
from fastapi import FastAPI, HTTPException
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel

BASE_DIR = os.path.dirname(os.path.abspath(__file__))
ZONAS_PATH = os.environ.get(
    "ZONAS_RIESGO_PATH",
    os.path.normpath(os.path.join(BASE_DIR, "..", "..", "Data", "output", "zonas_riesgo.json")),
)
OLLAMA_URL = "http://localhost:11434/api/chat"
MODELO_DEFECTO = "llama3.1"
MAX_RONDAS_TOOLS = 4

SYSTEM_PROMPT = """Eres el asistente de la app "Barrio Seguro" sobre riesgo \
de inseguridad por localidad en Bogotá. Los datos vienen de un pipeline de \
reglas + geofencing (SIN machine learning) sobre datos oficiales de la \
Alcaldía de Bogotá (delitos SDSCJ, población DANE/SDP, estratificación \
SDP) y OpenStreetMap.

No tienes los datos memorizados: SIEMPRE que te pregunten algo con \
números, nombres de localidades o comparaciones, usa las herramientas \
disponibles (localidad_extrema, obtener_ranking, obtener_localidad). Nunca \
inventes ni "recuerdes" una cifra — si no la obtuviste de una herramienta, \
no la uses. Responde en español, breve y concreto (esto se muestra en un \
celular, no en una pantalla grande).

El campo "estrato_promedio" es solo contexto socioeconómico: el pipeline \
NO lo usa para calcular nivel_riesgo (decisión ética documentada del \
proyecto, para no estigmatizar zonas de bajos ingresos). Si preguntan por \
qué una localidad tiene cierto riesgo, explica que se basa en \
score_ponderado_100k (delitos ponderados por severidad, por 100k \
habitantes), nunca en el estrato.

"nivel_riesgo" viene de cortes de Jenks Natural Breaks sobre \
score_ponderado_100k (3 niveles: bajo, medio, alto).

OJO al leer el resultado de una herramienta, hay DOS cifras que se \
parecen pero NO son lo mismo — no las confundas:
- "delitos_recientes_total_2023_2025": delitos VERIFICADOS (SDSCJ).
- "contexto.incidentes_nuse_recientes_total": LLAMADAS de emergencia \
(NUSE/C4), no delitos verificados. Es solo contexto (igual que el \
estrato): NO se usa para calcular nivel_riesgo. Se actualiza mensual, a \
diferencia de los delitos que son de corte semestral/anual.
"""

TOOLS = [
    {
        "type": "function",
        "function": {
            "name": "localidad_extrema",
            "description": (
                "Devuelve UNA sola localidad: la de mayor riesgo (cual='mayor') "
                "o la de menor riesgo (cual='menor'). Es la herramienta correcta "
                "para 'cuál es la localidad más/menos riesgosa, peligrosa o "
                "segura'. No intentes resolver esto tú mismo con obtener_ranking "
                "y elegir el primero o último de la lista: pide directamente el "
                "extremo, ya viene calculado."
            ),
            "parameters": {
                "type": "object",
                "properties": {
                    "cual": {
                        "type": "string",
                        "enum": ["mayor", "menor"],
                        "description": "'mayor' para la más riesgosa/peligrosa, 'menor' para la más segura",
                    }
                },
                "required": ["cual"],
            },
        },
    },
    {
        "type": "function",
        "function": {
            "name": "obtener_ranking",
            "description": (
                "Devuelve TODAS las localidades ordenadas de mayor a menor "
                "riesgo. Úsala solo para listar varias localidades o "
                "comparar más de dos entre sí, NO para encontrar un único "
                "extremo (para eso usa localidad_extrema)."
            ),
            "parameters": {"type": "object", "properties": {}, "required": []},
        },
    },
    {
        "type": "function",
        "function": {
            "name": "obtener_localidad",
            "description": (
                "Devuelve el detalle completo (delitos por categoría, "
                "población, contexto urbano) de UNA localidad específica "
                "por nombre."
            ),
            "parameters": {
                "type": "object",
                "properties": {
                    "nombre": {
                        "type": "string",
                        "description": "Nombre de la localidad, ej. 'Chapinero', 'Los Mártires'",
                    }
                },
                "required": ["nombre"],
            },
        },
    },
]


def normalizar(texto: str) -> str:
    nfkd = unicodedata.normalize("NFKD", texto)
    return "".join(c for c in nfkd if not unicodedata.combining(c)).lower().strip()


def reparar_mojibake(texto: str) -> str:
    """Ver Data/agente.py: artefacto conocido de Ollama con tool-calling
    activado, donde una tilde sale como sus bytes UTF-8 mal reinterpretados
    como latin-1 ("más" -> "mÃ¡s")."""
    try:
        return texto.encode("latin-1").decode("utf-8")
    except (UnicodeEncodeError, UnicodeDecodeError):
        return texto


def cargar_datos() -> dict:
    if not os.path.exists(ZONAS_PATH):
        raise RuntimeError(
            f"No encontré {ZONAS_PATH}. Corre primero Data/ProcesarRiesgo.py, "
            f"o define la variable de entorno ZONAS_RIESGO_PATH."
        )
    with open(ZONAS_PATH, "r", encoding="utf-8") as f:
        return json.load(f)


DATOS = cargar_datos()


def tool_obtener_ranking(datos: dict) -> list:
    filas = [(cod, info) for cod, info in datos.items() if cod != "_meta"]
    filas.sort(key=lambda kv: kv[1]["score_ponderado_100k"], reverse=True)
    return [
        {
            "posicion": i,
            "localidad": info["localidad"],
            "nivel_riesgo": info["nivel_riesgo"],
            "score_ponderado_100k": info["score_ponderado_100k"],
            "tasa_delitos_100k": info["tasa_delitos_100k"],
        }
        for i, (_, info) in enumerate(filas, start=1)
    ]


def tool_localidad_extrema(datos: dict, cual: str) -> dict:
    ranking = tool_obtener_ranking(datos)
    return ranking[0] if cual == "mayor" else ranking[-1]


def tool_obtener_localidad(datos: dict, nombre: str) -> dict:
    nombre_norm = normalizar(nombre)
    registros = [info for cod, info in datos.items() if cod != "_meta"]

    for info in registros:
        if normalizar(info["localidad"]) == nombre_norm:
            return info

    parciales = [info for info in registros if nombre_norm in normalizar(info["localidad"])]
    if len(parciales) == 1:
        return parciales[0]
    if parciales:
        return {"error": f"Nombre ambiguo. Coincidencias: {[p['localidad'] for p in parciales]}"}
    return {
        "error": f"No encontré la localidad '{nombre}'.",
        "localidades_disponibles": [info["localidad"] for info in registros],
    }


def ejecutar_tool(nombre: str, argumentos: dict, datos: dict):
    if nombre == "localidad_extrema":
        return tool_localidad_extrema(datos, argumentos.get("cual", "mayor"))
    if nombre == "obtener_ranking":
        return tool_obtener_ranking(datos)
    if nombre == "obtener_localidad":
        return tool_obtener_localidad(datos, argumentos.get("nombre", ""))
    return {"error": f"Herramienta desconocida: {nombre}"}


def preguntar(modelo: str, historial: list, datos: dict) -> str:
    for _ in range(MAX_RONDAS_TOOLS):
        try:
            resp = requests.post(
                OLLAMA_URL,
                json={"model": modelo, "messages": historial, "tools": TOOLS, "stream": False},
                timeout=600,
            )
        except requests.exceptions.ConnectionError:
            raise HTTPException(
                status_code=503,
                detail="No pude conectarme a Ollama en localhost:11434. ¿Está corriendo?",
            )
        if resp.status_code != 200:
            raise HTTPException(status_code=502, detail=f"Ollama devolvió {resp.status_code}: {resp.text}")

        mensaje = json.loads(resp.content.decode("utf-8"))["message"]
        historial.append(mensaje)

        tool_calls = mensaje.get("tool_calls")
        if not tool_calls:
            return reparar_mojibake(mensaje.get("content", ""))

        for llamada in tool_calls:
            fn = llamada["function"]
            argumentos = fn.get("arguments") or {}
            resultado = ejecutar_tool(fn["name"], argumentos, datos)
            historial.append({"role": "tool", "content": json.dumps(resultado, ensure_ascii=False)})

    return "No pude terminar de consultar los datos (demasiadas llamadas a herramientas)."


app = FastAPI(title="Barrio Seguro API", version="1.0")
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_methods=["*"],
    allow_headers=["*"],
)


class Mensaje(BaseModel):
    role: str
    content: str


class ChatRequest(BaseModel):
    mensajes: list[dict[str, Any]]
    modelo: str = MODELO_DEFECTO


class ChatResponse(BaseModel):
    respuesta: str
    mensajes: list[dict[str, Any]]


@app.get("/health")
def health():
    ollama_ok = False
    try:
        ollama_ok = requests.get("http://localhost:11434/api/tags", timeout=3).status_code == 200
    except requests.exceptions.RequestException:
        pass
    return {"status": "ok", "ollama_disponible": ollama_ok, "localidades_cargadas": len(DATOS) - 1}


@app.get("/zonas")
def zonas():
    return DATOS


@app.get("/zonas/ranking")
def ranking():
    return tool_obtener_ranking(DATOS)


@app.get("/zonas/extremo")
def extremo(cual: str = "mayor"):
    if cual not in ("mayor", "menor"):
        raise HTTPException(status_code=400, detail="El parámetro 'cual' debe ser 'mayor' o 'menor'")
    return tool_localidad_extrema(DATOS, cual)


@app.get("/zonas/{nombre}")
def zona_por_nombre(nombre: str):
    resultado = tool_obtener_localidad(DATOS, nombre)
    if "error" in resultado:
        raise HTTPException(status_code=404, detail=resultado)
    return resultado


@app.post("/chat", response_model=ChatResponse)
def chat(req: ChatRequest):
    historial = [{"role": "system", "content": SYSTEM_PROMPT}] + req.mensajes
    respuesta = preguntar(req.modelo, historial, DATOS)
    return ChatResponse(respuesta=respuesta, mensajes=historial[1:])
