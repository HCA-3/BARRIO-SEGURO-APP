"""
Agente conversacional sobre los datos de riesgo por localidad — Bogotá.

Chat de terminal que responde preguntas en lenguaje natural sobre
./output/zonas_riesgo.json (la salida de ProcesarRiesgo.py).

Diseño: tool-calling, no "pegar todo el JSON en el prompt". Un primer
intento inyectando el JSON completo (o incluso una tabla ya ordenada) como
contexto hizo que el modelo local (llama3.1:8B en CPU) inventara la
localidad de mayor riesgo en vez de leer el dato correcto — los modelos
chicos no son confiables "leyendo" cifras sueltas en un bloque de texto
largo. Por eso el LLM aquí NO recibe los datos crudos: solo puede llamar
funciones Python (`obtener_ranking`, `obtener_localidad`) que calculan el
resultado exacto sobre el dict ya cargado, y el LLM únicamente redacta la
respuesta a partir de ESE resultado. Esto es consistente con el enfoque
"reglas + geofencing (NO machine learning)" del proyecto: el LLM no
calcula ni clasifica nada, solo consulta y redacta.

Requiere Ollama corriendo localmente (https://ollama.com/download) y un
modelo que soporte tool-calling ya descargado, ej.:
    ollama pull llama3.1

Uso:
    python agente.py                  -> usa el modelo por defecto (llama3.1)
    python agente.py --modelo mistral -> usa otro modelo ya descargado
"""

import argparse
import json
import os
import sys
import unicodedata

import requests

if sys.stdout.encoding != "utf-8":
    sys.stdout.reconfigure(encoding="utf-8")

BASE_DIR = os.path.dirname(os.path.abspath(__file__))
ZONAS_PATH = os.path.join(BASE_DIR, "output", "zonas_riesgo.json")
OLLAMA_URL = "http://localhost:11434/api/chat"
MAX_RONDAS_TOOLS = 4

SYSTEM_PROMPT = """Eres un asistente sobre riesgo de inseguridad por \
localidad en Bogotá. Los datos vienen de un pipeline de reglas + \
geofencing (SIN machine learning) sobre datos oficiales de la Alcaldía de \
Bogotá (delitos SDSCJ, población DANE/SDP, estratificación SDP) y \
OpenStreetMap.

No tienes los datos memorizados: SIEMPRE que te pregunten algo con \
números, nombres de localidades o comparaciones, usa las herramientas \
disponibles (obtener_ranking, obtener_localidad) para consultarlos. Nunca \
inventes ni "recuerdes" una cifra — si no la obtuviste de una herramienta, \
no la uses. Responde en español, breve y concreto.

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


def cargar_datos() -> dict:
    if not os.path.exists(ZONAS_PATH):
        print(f"No encontré {ZONAS_PATH}.\nCorre primero: python ProcesarRiesgo.py")
        sys.exit(1)
    with open(ZONAS_PATH, "r", encoding="utf-8") as f:
        return json.load(f)


def verificar_ollama(modelo: str):
    try:
        resp = requests.get("http://localhost:11434/api/tags", timeout=5)
        resp.raise_for_status()
    except requests.exceptions.ConnectionError:
        print(
            "No pude conectarme a Ollama en http://localhost:11434.\n"
            "Instálalo desde https://ollama.com/download y déjalo corriendo, "
            "luego descarga un modelo con: ollama pull " + modelo
        )
        sys.exit(1)

    modelos_disponibles = [m["name"] for m in resp.json().get("models", [])]
    if not any(m.split(":")[0] == modelo.split(":")[0] for m in modelos_disponibles):
        print(
            f"El modelo '{modelo}' no está descargado en Ollama.\n"
            f"Descárgalo con: ollama pull {modelo}\n"
            f"Modelos disponibles ahora mismo: {modelos_disponibles or '(ninguno)'}"
        )
        sys.exit(1)


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


# claves = bytes UTF-8 de la tilde reinterpretados como latin-1 (mojibake);
# valores = el caracter correcto.
MOJIBAKE_MAP = {
    "Ã¡": "á",  # a-acento
    "Ã©": "é",  # e-acento
    "Ã­": "í",  # i-acento
    "Ã³": "ó",  # o-acento
    "Ãº": "ú",  # u-acento
    "Ã±": "ñ",  # ene
    "Ã": "Á",  # A-acento
    "Ã": "É",  # E-acento
    "Ã": "Í",  # I-acento
    "Ã": "Ó",  # O-acento
    "Ã": "Ú",  # U-acento
    "Ã": "Ñ",  # ENE
    "Â¿": "¿",  # interrogacion invertido
    "Â¡": "¡",  # exclamacion invertido
    "Ã¼": "ü",  # u-dieresis
    "Ã¤": "ä",  # a-dieresis
}


def reparar_mojibake(texto: str) -> str:
    """
    A veces el modelo (llama3.1 vía Ollama, con tool-calling activado)
    genera una tilde como si fueran sus bytes UTF-8 reinterpretados como
    latin-1: "más" sale como "mÃ¡s". Es un artefacto del propio modelo, no
    de cómo Python lee la respuesta HTTP (ya se fuerza utf-8 ahí).

    La corrupción es a veces parcial: un mismo mensaje puede traer "mÃ¡s"
    (roto) junto a "Mártires" (ya bien codificado). Por eso NO se puede
    reparar re-codificando la cadena completa a latin-1 y decodificando de
    vuelta como utf-8 — el fragmento ya correcto contiene bytes que no
    son continuaciones UTF-8 válidas y el round-trip completo lanza
    UnicodeDecodeError, dejando el mensaje entero sin reparar. En su
    lugar se reemplazan directamente las secuencias mojibake conocidas.
    """
    for roto, correcto in MOJIBAKE_MAP.items():
        texto = texto.replace(roto, correcto)
    return texto


def preguntar(modelo: str, historial: list, datos: dict) -> str:
    for _ in range(MAX_RONDAS_TOOLS):
        resp = requests.post(
            OLLAMA_URL,
            json={"model": modelo, "messages": historial, "tools": TOOLS, "stream": False},
            timeout=600,
        )
        resp.raise_for_status()
        mensaje = json.loads(resp.content.decode("utf-8"))["message"]
        historial.append(mensaje)

        tool_calls = mensaje.get("tool_calls")
        if not tool_calls:
            return reparar_mojibake(mensaje.get("content", ""))

        for llamada in tool_calls:
            fn = llamada["function"]
            argumentos = fn.get("arguments") or {}
            resultado = ejecutar_tool(fn["name"], argumentos, datos)
            historial.append(
                {
                    "role": "tool",
                    "content": json.dumps(resultado, ensure_ascii=False),
                }
            )

    return "No pude terminar de consultar los datos (demasiadas llamadas a herramientas)."


def main():
    parser = argparse.ArgumentParser(description="Chat sobre los datos de riesgo por localidad")
    parser.add_argument("--modelo", default="llama3.1", help="Nombre del modelo de Ollama a usar")
    args = parser.parse_args()

    datos = cargar_datos()
    verificar_ollama(args.modelo)

    historial = [{"role": "system", "content": SYSTEM_PROMPT}]

    print(f"Agente listo (modelo: {args.modelo}). Pregunta sobre riesgo por localidad en Bogotá.")
    print("Escribe 'salir' para terminar.\n")

    while True:
        try:
            pregunta = input("Tú: ").strip()
        except (EOFError, KeyboardInterrupt):
            print()
            break
        if not pregunta:
            continue
        if pregunta.lower() in ("salir", "exit", "quit"):
            break

        historial.append({"role": "user", "content": pregunta})
        try:
            respuesta = preguntar(args.modelo, historial, datos)
        except requests.exceptions.RequestException as e:
            print(f"Error hablando con Ollama: {e}")
            historial.pop()
            continue
        print(f"\nAgente: {respuesta}\n")


if __name__ == "__main__":
    main()
