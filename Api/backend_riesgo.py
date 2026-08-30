"""
Backend HTTP para la app de Android "Barrio Seguro" (Riesgos Sociales en
Chapinero / Bogotá).

Expone por REST los datos de riesgo por localidad que calcula
../Agente/ProcesarRiesgo.py, y el agente conversacional (mismo diseño de
tool-calling que Agente/agente.py: el LLM nunca "lee" cifras sueltas de un
bloque de texto largo — siempre llama funciones Python que las calculan —
porque en las pruebas un modelo local de 8B alucinaba el ranking cuando se
le pedía leerlo directamente de un JSON o de una tabla en el prompt).

Requiere:
  - Ollama corriendo localmente con un modelo que soporte tool-calling ya
    descargado (ej. "ollama pull llama3.1").
  - Que ../Agente/output/zonas_riesgo.json ya exista (correr primero
    Agente/ProcesarRiesgo.py).

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

import glob
import json
import os
import re
import unicodedata
from typing import Any

import geopandas as gpd
import pandas as pd
import requests
from fastapi import FastAPI, HTTPException
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel
from shapely.geometry import Point

BASE_DIR = os.path.dirname(os.path.abspath(__file__))
ZONAS_PATH = os.environ.get(
    "ZONAS_RIESGO_PATH",
    os.path.normpath(os.path.join(BASE_DIR, "..", "Agente", "output", "zonas_riesgo.json")),
)
LIMITES_PATH = os.environ.get(
    "LIMITES_LOCALIDADES_PATH",
    os.path.normpath(os.path.join(BASE_DIR, "..", "Agente", "output", "localidades.geojson")),
)
UPZ_PATH = os.environ.get(
    "UPZ_RIESGO_PATH",
    os.path.normpath(os.path.join(BASE_DIR, "..", "Agente", "output", "upz_riesgo.json")),
)
UPZ_LIMITES_PATH = os.environ.get(
    "UPZ_LIMITES_PATH",
    os.path.normpath(os.path.join(BASE_DIR, "..", "Agente", "output", "upz_limites.geojson")),
)
# El .gpkg de OSM se descomprime con nombres de carpeta que varían (BBBike
# a veces agrega "(1)", etc. — ver Agente/ProcesarRiesgo.py buscar_archivo),
# así que se busca en vez de exigir una ruta fija, salvo que se fije por env.
OSM_GPKG_PATH = os.environ.get("OSM_GPKG_PATH")
if not OSM_GPKG_PATH:
    _candidatos_gpkg = glob.glob(
        os.path.normpath(os.path.join(BASE_DIR, "..", "Agente", "**", "Bogota.gpkg")), recursive=True
    )
    OSM_GPKG_PATH = _candidatos_gpkg[0] if _candidatos_gpkg else None
OLLAMA_URL = "http://localhost:11434/api/chat"

# llama3.2:3b en vez de llama3.1 (8B): este backend corre en un portátil sin
# GPU utilizable (Intel UHD), o sea 100% CPU, donde el 8B tardaba ~3 minutos por
# respuesta. El 3B da respuestas comparables para lo que se le pide aquí (elegir
# una herramienta y redactar una frase con lo que devuelve) a una fracción del
# coste. Se puede volver al 8B sin tocar código:
#     set BARRIO_SEGURO_MODELO=llama3.1
MODELO_DEFECTO = os.environ.get("BARRIO_SEGURO_MODELO", "llama3.2:3b")

# Cuánto se queda el modelo cargado en RAM tras la última petición. Por defecto
# Ollama lo descarga a los 5 minutos, y volver a cargarlo cuesta más que la
# propia respuesta. Con esto, la primera pregunta paga la carga y las demás no.
KEEP_ALIVE = os.environ.get("BARRIO_SEGURO_KEEP_ALIVE", "30m")

# SYSTEM_PROMPT (~1.8k tokens) + TOOLS (~3.4k tokens) se mandan en cada ronda,
# así que con los 4096 por defecto de Ollama el contexto se truncaba y el modelo
# perdía parte de las herramientas. 8192 deja sitio para el prompt, el historial
# y los resultados de las tools.
NUM_CTX = int(os.environ.get("BARRIO_SEGURO_NUM_CTX", "8192"))

# Tope de tokens de la respuesta. Las respuestas útiles aquí son de dos o tres
# frases; sin tope, el modelo a veces se enrolla y cada token extra son décimas
# de segundo en CPU.
NUM_PREDICT = int(os.environ.get("BARRIO_SEGURO_NUM_PREDICT", "400"))

MAX_RONDAS_TOOLS = 4

SYSTEM_PROMPT = """Eres el asistente de la app "Barrio Seguro" sobre riesgo \
de inseguridad por localidad en Bogotá. Los datos vienen de un pipeline de \
reglas + geofencing (SIN machine learning) sobre datos oficiales de la \
Alcaldía de Bogotá (delitos SDSCJ, población DANE/SDP, estratificación \
SDP) y OpenStreetMap.

No tienes los datos de riesgo memorizados: SIEMPRE que te pregunten algo \
con números, nombres de localidades, barrios o comparaciones, usa las \
herramientas disponibles (localidad_extrema, obtener_ranking, \
obtener_localidad, comparar_localidades, buscar_barrio). Nunca inventes ni \
"recuerdes" una cifra ni una ubicación — si no la obtuviste de una \
herramienta, no la uses. Responde en español, breve y concreto (esto se \
muestra en un celular, no en una pantalla grande).

Esta regla de "usa herramientas, nunca inventes" es SOLO para datos de \
riesgo/localidades/barrios. Un saludo ("hola, cómo estás?"), una \
despedida, o una charla normal NO necesitan ninguna herramienta — \
respóndelos de forma natural y amable, como cualquier conversación. NUNCA \
digas "no tengo función para responder eso" ni "no tengo suficiente \
información" ante un saludo o comentario casual: eso es solo para cuando \
de verdad preguntan un dato que requiere una herramienta. Tampoco \
NARRES tu propia decisión de usar o no una herramienta ("no hay \
necesidad de llamar una herramienta para esto", "no hay herramienta que \
llamar") — eso es tu proceso interno, no algo para decirle al usuario. \
Solo responde directo, como si la decisión ni existiera.

SIEMPRE responde en texto plano, natural, como si estuvieras escribiendo \
un mensaje de chat. NUNCA envuelvas tu respuesta en JSON ni en un formato \
tipo {"type": "message", "text": "..."} — eso rompe la app, que espera \
texto normal, no una estructura de datos.

IMPORTANTE sobre BARRIOS: cuando NOMBREN un barrio nuevo (ej. "¿es seguro \
el barrio Acapulco?", "vivo en Galerías"), usa SIEMPRE buscar_barrio \
primero, pasando el NOMBRE PROPIO del barrio — te dice en qué localidad \
cae de verdad, con su riesgo real, en vez de que tengas que adivinar. \
Si en vez de nombrar un barrio te hacen una pregunta de SEGUIMIENTO sobre \
uno del que YA hablaron en la conversación (ej. "¿dónde queda?", "¿cómo \
es?", "cuéntame más"), NO llames buscar_barrio con esa pregunta como si \
fuera el nombre (nunca pases "donde", "eso", "aquí", "cuál" ni palabras \
así como argumento "nombre") — usa la localidad/UPZ que ya te devolvió la \
herramienta antes en esta misma conversación para responder. NUNCA inventes ni \
"recuerdes" en qué localidad está un barrio ni su ubicación geográfica \
(norte/sur/etc.) por tu cuenta, aunque te suene familiar o creas saberlo \
de memoria — decir algo incorrecto con seguridad es peor que admitir que \
no lo sabes. Solo si buscar_barrio devuelve error (no lo encontró, o hay \
varios barrios con ese nombre en localidades distintas) puedes decirlo \
honestamente y preguntar más detalle — nunca rellenar el hueco por tu \
cuenta. Ten en cuenta que buscar_barrio usa datos de OpenStreetMap, no un \
registro oficial: puede no tener todos los barrios, sobre todo los más \
pequeños o informales.

Si el usuario afirma o da por hecho algo que CONTRADICE lo que devolvió \
una herramienta EN ESTA CONVERSACIÓN (no un ejemplo de memoria, sino lo \
que la herramienta te acaba de responder ahora), CORRÍGELO claro y \
directo con el dato real que te dio la herramienta — no mezcles su \
versión incorrecta con el dato real en la misma frase (eso sale confuso y \
suena a que le estás dando la razón). No hay que darle la razón a lo que \
diga el usuario, hay que ser preciso con los datos aunque eso signifique \
contradecirlo. OJO: esto es sobre contradecir al USUARIO cuando se \
equivoca — nunca al revés. Si buscar_barrio te devolvió un resultado SIN \
la clave "error" (osea, SÍ lo encontró), esa localidad/nivel_riesgo son el \
dato real y correcto para ESTE barrio ahora — no digas "no tengo datos" \
ni "no lo encontré" en ese caso, y no asumas que un barrio y una \
localidad no van juntos solo porque en otra conversación pasada esa \
combinación te haya parecido rara. Cada llamada a la herramienta es \
independiente: confía en lo que te devuelve ahora, no en patrones de \
antes.

Sí puedes recordar cosas sobre el USUARIO (no cifras de riesgo) con la \
herramienta recordar_hecho, para conversaciones futuras: dónde vive, sus \
rutinas, qué le preocupa. Úsala cuando comparta algo así de forma natural, \
sin interrogarlo ni pedirle explícitamente que te cuente datos personales. \
Si en "Cosas que ya sabes de este usuario" ves algo relevante a lo que \
pregunta, úsalo con naturalidad (ej. si sabes que vive en Kennedy y \
pregunta "¿es seguro donde vivo?", no le preguntes dónde vive).

El campo "estrato_promedio" es solo contexto socioeconómico: el pipeline \
NO lo usa para calcular nivel_riesgo (decisión ética documentada del \
proyecto, para no estigmatizar zonas de bajos ingresos). Si preguntan por \
qué una localidad tiene cierto riesgo, explica que se basa en score_mixto, \
nunca en el estrato.

Sobre cómo COMUNICAR nivel_riesgo: no lo aplanes a un simple sí/no de \
"es peligroso" o "es seguro" — sobre todo con "medio", que NO es lo mismo \
que "no es peligroso" (eso subestima un riesgo real) ni lo mismo que \
"es peligroso" (eso lo exagera). Dilo tal cual: "riesgo medio/moderado". \
Para "bajo" sí puedes decir que es relativamente segura, y para "alto" \
que sí es una zona de riesgo alto — pero "medio" queda en el medio, no lo \
conviertas en una de las otras dos categorías.

"nivel_riesgo" viene de cortes de Jenks Natural Breaks sobre score_mixto \
(3 niveles: bajo, medio, alto). score_mixto promedia dos señales, cada \
una normalizada 0-1: score_ponderado_100k (delitos ponderados por \
severidad, por habitante) y score_ponderado_por_km2 (lo mismo, pero por \
área). Se combinan las dos porque normalizar solo por población residente \
infla el riesgo de localidades pequeñas con mucha población flotante no \
residente (comercio, turismo, tránsito) y diluye localidades grandes y \
pobladas donde el delito es alto en cifras absolutas — y la app alerta \
según dónde está alguien físicamente (GPS), no según dónde vive \
registrado. Si preguntan por qué una localidad concreta tiene tal nivel, \
puedes mencionar ambos componentes (por habitante y por área) para \
explicarlo mejor.

OJO al leer el resultado de una herramienta, hay DOS cifras que se \
parecen pero NO son lo mismo — no las confundas:
- "delitos_recientes_total_2023_2025": delitos VERIFICADOS (SDSCJ).
- "contexto.incidentes_nuse_recientes_total": LLAMADAS de emergencia \
(NUSE/C4), no delitos verificados. Es solo contexto (igual que el \
estrato): NO se usa para calcular nivel_riesgo. Se actualiza mensual, a \
diferencia de los delitos que son de corte semestral/anual.
IMPORTANTE: estas dos son TOTALES ABSOLUTOS (un conteo de casos), NO tasas \
por 100.000 habitantes — nunca les agregues "por 100k habitantes" ni ningún \
otro sufijo de tasa, eso los convierte en un número inventado sin sentido. \
Si quieres dar una cifra normalizada por población, usa "tasa_delitos_100k" \
(que sí es una tasa real, ya calculada) — no inventes tu propia conversión \
ni una cifra que "suene" parecida. Tampoco muestres los nombres de campo \
en crudo (ej. "delitos_recientes_total_2023_2025") en tu respuesta — son \
para ti, tradúcelos a lenguaje natural ("los delitos verificados entre \
2023 y 2025 fueron...").

Cuando te pregunten qué tipo de delito es más común y uses el bloque \
"detalle_delitos" de una localidad, cita EXACTAMENTE los nombres de \
categoría y las cifras que trae ese diccionario — nunca inventes \
categorías que no están ahí (ej. "robo con fuerza", "robo a transeúnte", \
"roboteca" no son categorías reales de este dataset) ni inventes números \
que no vienen literalmente de "detalle_delitos". Si no recuerdas los \
números exactos de una respuesta anterior en la conversación, vuelve a \
llamar la herramienta en vez de inventar de memoria.

Sobre el bloque "upz" que traen buscar_barrio y localidad_por_punto: \
"tasa_llamadas_100k" es un TOTAL ACUMULADO de 2023 a 2025 (3 años, no un \
año), de TODAS las llamadas de emergencia (médicas, accidentes, riñas, \
incendios, etc. — no solo delito) por 100k habitantes de esa UPZ \
específica. Si la citas, ACLARA que es un acumulado de 3 años y de \
llamadas en general, no delito verificado — si no lo aclaras, un número \
como "49.000 por 100k" suena exagerado o como un error cuando en \
realidad es razonable para 3 años de TODAS las llamadas de emergencia. \
IMPORTANTE: a nivel de UPZ/barrio SOLO existe este número agregado — NO \
hay desglose por tipo de delito a ese nivel (el portal de Bogotá no lo \
publica así, sí a nivel de localidad completa). Si preguntan qué tipo de \
delito es más común en una UPZ/barrio específico, esto NO significa que \
"no tengas información" del barrio — SÍ la tienes (localidad, \
nivel_riesgo, llamadas UPZ), solo falta ESE dato puntual (el desglose por \
tipo). Responde dando lo que sí tienes (localidad, nivel_riesgo) y luego \
aclara que el desglose por tipo de delito solo existe a nivel de \
localidad completa, ofreciendo ese de la localidad entera si quieren. \
NUNCA respondas "no tengo información sobre el barrio X" ni "el barrio X \
no existe" solo porque falte ESE desglose puntual — eso es al revés de lo \
que hay que decir: sí sabes dónde está y qué tan riesgoso es, solo no el \
desglose por tipo. Si el usuario SÍ quiere ese desglose de la localidad \
entera, llama obtener_localidad con el nombre EXACTO de la localidad que \
buscar_barrio ya devolvió antes en esta conversación — nunca adivines ni \
cambies a otra localidad distinta a la que ya se estableció.

Cada localidad trae un bloque "contexto" con señales adicionales que \
puedes usar para responder preguntas más ricas, no solo repetir el \
nivel_riesgo:
- "estrato_promedio": nivel socioeconómico (SOLO contexto, ver nota arriba).
- "luminarias_estimadas" / "luminarias_por_km2": alumbrado público (OSM). \
Más luminarias por km² sugiere calles mejor iluminadas de noche.
- "longitud_vias_km": km de vías registradas (OSM). Útil para preguntas \
sobre qué tan transitada/conectada es la zona.
- "area_km2": tamaño de la localidad, para dar contexto de escala.
- "incidentes_nuse_recientes_total": ver nota abajo, es llamadas de \
emergencia, no delito.
Úsalas cuando la pregunta se preste (ej. "¿está bien iluminado Kennedy de \
noche?", "¿cuál es más grande, Suba o Usaquén?"), no las fuerces si no \
vienen al caso.

IMPORTANTE: estas señales de contexto son solo eso, contexto — NUNCA las \
uses para concluir "es segura"/"es peligrosa" ni para contradecir o \
suavizar nivel_riesgo. Si nivel_riesgo es "medio" o "alto", no digas \
después que "la zona es segura" solo porque tiene buen alumbrado o buenas \
vías — eso es CONTRADICTORIO y confunde (buen alumbrado no compensa un \
riesgo real de delito). Puedes mencionar el contexto como dato aparte \
("además, tiene bastante alumbrado"), pero la conclusión sobre qué tan \
segura es la zona sale SOLO de nivel_riesgo/score_mixto, nunca del \
contexto. Tampoco inventes frases genéricas sin respaldo en los datos \
como "la seguridad es considerada estable" — si no viene de una \
herramienta, no lo digas.

Sobre el TONO: esto es una conversación de chat, no un informe. Habla \
como una persona que conoce bien los datos y quiere ayudar, no como un \
reporte generado. Evita encadenar cifras una tras otra sin conexión — \
elige el dato más relevante para lo que preguntaron y menciona el resto \
solo si aporta. Está bien usar un tono cercano ("ojo con...", "eso sí, \
ten en cuenta que...") sin dejar de ser preciso con los números. Si la \
pregunta es ambigua (ej. no dice qué localidad), pregunta primero en vez \
de asumir. Cuando termines de responder algo, si tiene sentido, cierra \
con una pregunta corta de seguimiento (ej. "¿quieres que compare con otra \
localidad?", "¿te cuento qué tipo de delito pesa más ahí?") para invitar \
a seguir la conversación — pero no lo hagas si ya se despidieron o si \
sería forzado.
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
                "por nombre. Si la pregunta es de SEGUIMIENTO sobre una "
                "localidad que ya se estableció antes en esta misma "
                "conversación (ej. porque buscar_barrio ya dijo en qué "
                "localidad cae un barrio), usa EXACTAMENTE ese mismo "
                "nombre de localidad — nunca otro, ni uno de ejemplo."
            ),
            "parameters": {
                "type": "object",
                "properties": {
                    "nombre": {
                        "type": "string",
                        "description": "Nombre de la localidad, ej. 'Suba', 'Kennedy'",
                    }
                },
                "required": ["nombre"],
            },
        },
    },
    {
        "type": "function",
        "function": {
            "name": "comparar_localidades",
            "description": (
                "Devuelve el detalle completo de DOS O MÁS LOCALIDADES (las 20 "
                "zonas grandes, ej. Kennedy, Suba, Engativá) juntas, para "
                "comparar entre sí (ej. '¿cuál es más segura, Kennedy o "
                "Suba?', '¿dónde hay más alumbrado, Bosa o Usme?'). Más "
                "directo que llamar obtener_localidad varias veces.\n"
                "NO la uses si alguno de los nombres es un BARRIO en vez de "
                "una localidad (ej. si dice 'Barrio' en el nombre, o no está "
                "en la lista de 20 localidades, o la frase es 'el barrio X en "
                "la localidad Y' — eso NO son dos localidades a comparar, es "
                "UN barrio con SU localidad ya dicha: ahí usa buscar_barrio "
                "con nombre=X y localidad=Y, no esta herramienta)."
            ),
            "parameters": {
                "type": "object",
                "properties": {
                    "nombres": {
                        "type": "array",
                        "items": {"type": "string"},
                        "description": "Nombres de las localidades a comparar, ej. ['Kennedy', 'Suba']",
                    }
                },
                "required": ["nombres"],
            },
        },
    },
    {
        "type": "function",
        "function": {
            "name": "recordar_hecho",
            "description": (
                "Guarda un hecho DURADERO sobre este usuario para recordarlo en "
                "futuras conversaciones (ej. dónde vive, dónde trabaja/estudia, "
                "rutinas u horarios, preferencias de seguridad). Úsala solo cuando "
                "el usuario comparta algo sobre sí mismo que valga la pena recordar "
                "más allá de esta conversación — no la uses para cada mensaje, ni "
                "para preguntas sobre localidades. Nunca guardes contraseñas, datos "
                "financieros ni información sensible innecesaria."
            ),
            "parameters": {
                "type": "object",
                "properties": {
                    "hecho": {
                        "type": "string",
                        "description": (
                            "El hecho a recordar, en una frase corta y en tercera "
                            "persona, ej. 'Vive en Chapinero cerca a la Zona T'"
                        ),
                    }
                },
                "required": ["hecho"],
            },
        },
    },
    {
        "type": "function",
        "function": {
            "name": "buscar_barrio",
            "description": (
                "Busca un BARRIO por nombre (ej. 'Acapulco', 'Galerías', 'Restrepo') "
                "y devuelve en qué localidad cae y su nivel de riesgo real — así "
                "puedes responder bien cuando alguien menciona un barrio en vez de "
                "una localidad. Úsala SIEMPRE que mencionen un barrio, antes de "
                "decir que no tienes esa información. Si no lo encuentra, es "
                "porque de verdad no está en la base de datos (barrios de OpenStreetMap "
                "en Bogotá) — en ese caso sí puedes decir que no lo tienes, pero NUNCA "
                "inventes en qué localidad queda por tu cuenta.\n"
                "Si la respuesta trae varias 'opciones' (nombre ambiguo, existe en más "
                "de una localidad) y le preguntas al usuario cuál es, cuando te "
                "conteste vuelve a llamar a ESTA MISMA herramienta con el mismo "
                "'nombre' PERO ahora incluyendo también 'localidad' con lo que te "
                "dijo — así te devuelve directamente la correcta en vez de la lista "
                "ambigua otra vez. No repitas la pregunta de aclaración si el "
                "usuario ya te dijo la localidad: úsala.\n"
                "Si la pregunta YA trae el barrio Y su localidad juntos en la misma "
                "frase (ej. 'el barrio Acapulco en la localidad de Engativá', 'barrio "
                "X, localidad Y'), NO es una comparación de dos localidades — es UN "
                "barrio con su localidad ya dicha. Llama a ESTA herramienta de una vez "
                "con nombre=X y localidad=Y, no comparar_localidades ni obtener_localidad."
            ),
            "parameters": {
                "type": "object",
                "properties": {
                    "nombre": {
                        "type": "string",
                        "description": "Nombre del barrio, ej. 'Acapulco', 'Galerías', 'Suba Rincón'",
                    },
                    "localidad": {
                        "type": "string",
                        "description": (
                            "Opcional. Nombre de la localidad, para cuando el nombre del barrio "
                            "es ambiguo (existe en varias) y el usuario ya aclaró cuál. Ej: "
                            "'Engativá'."
                        ),
                    },
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


def reparar_mojibake_argumentos(argumentos: dict) -> dict:
    """El mismo artefacto de reparar_mojibake pasa también en los
    ARGUMENTOS de una tool call (ej. localidad="Engativ¡" en vez de
    "Engativá"), no solo en el texto de la respuesta final — si no se
    repara aquí, comparaciones como normalizar(localidad) fallan en
    silencio y una herramienta con nombre/localidad correctos "no
    encuentra" nada."""
    reparado = {}
    for clave, valor in argumentos.items():
        if isinstance(valor, str):
            reparado[clave] = reparar_mojibake(valor)
        elif isinstance(valor, list):
            reparado[clave] = [reparar_mojibake(v) if isinstance(v, str) else v for v in valor]
        else:
            reparado[clave] = valor
    return reparado


def desenvolver_json_accidental(texto: str) -> str:
    """El modelo local a veces (no siempre) envuelve su respuesta en algo
    como {"type": "message", "text": "..."} en vez de texto plano — un tic
    del modelo, no algo que pedimos. Si pasa, la app mostraría el JSON
    crudo en la burbuja de chat. Se intenta desenvolver; si el texto no es
    JSON o no tiene esa forma, se devuelve tal cual (no es un error)."""
    texto = texto.strip()
    if not texto.startswith("{"):
        return texto
    try:
        datos = json.loads(texto)
    except json.JSONDecodeError:
        return texto
    if not isinstance(datos, dict):
        return texto
    for clave in ("text", "content", "message", "respuesta"):
        valor = datos.get(clave)
        if isinstance(valor, str) and valor.strip():
            return valor
    return texto


# Palabras que una respuesta real (sobre riesgo/localidades/barrios, o un
# saludo/despedida normal) nunca necesita usar — si aparecen, es casi
# seguro que el modelo se puso a narrar su propia decisión de usar o no
# una herramienta ("no hay función que llamar...") en vez de responder de
# verdad. Salvaguarda de backend: el prompt ya se lo pide, pero este
# modelo local no siempre obedece.
_PISTAS_NARRACION_META = ("herramienta", "función", "funcion", "no hay nada que hacer", "puedo irme")


def reemplazar_narracion_meta(texto: str) -> str:
    minusculas = texto.lower()
    if any(pista in minusculas for pista in _PISTAS_NARRACION_META):
        return "¡De nada! Si tienes otra pregunta sobre el riesgo de alguna zona, aquí estoy."
    return texto


# Frases que el modelo repite como si fueran un dato más, pero no salen de
# ninguna herramienta (no hay ningún campo de "estabilidad" en los datos):
# el prompt ya pide no inventarlas, pero reaparecen igual. En vez de tirar
# toda la respuesta (el resto del mensaje suele ser correcto), se recorta
# solo la oración con la frase inventada.
_PATRON_FRASES_SIN_RESPALDO = re.compile(
    r"[^.\n]*\bseguridad\b[^.\n]*\bconsiderad[ao]\s+estable\b[^.\n]*[.\n]?",
    re.IGNORECASE,
)


def quitar_frases_sin_respaldo(texto: str) -> str:
    limpio = _PATRON_FRASES_SIN_RESPALDO.sub("", texto)
    return re.sub(r"\n{3,}", "\n\n", limpio).strip()


# "delitos_recientes_total_2023_2025" e "incidentes_nuse_recientes_total" son
# TOTALES absolutos (ver SYSTEM_PROMPT) -- el modelo local a veces los
# convierte en una tasa inventada tipo "2.115,36 por 100k habitantes" que no
# sale de ningún cálculo real (ni siquiera es el total dividido por
# población: es un número de la nada). El prompt ya lo prohíbe pero
# reaparece igual, así que se recorta el fragmento inventado directamente en
# vez de confiar en que el modelo obedezca. OJO: se apunta solo al fragmento
# "campo = número por 100k/100.000 habitantes" (no a la oración completa como
# _PATRON_FRASES_SIN_RESPALDO) porque estos dos campos suelen aparecer
# juntos en la MISMA oración (unidos por "y"), y el número decimal
# ("2,115.36") o el "contexto.algo" con punto de acceso rompen cualquier
# intento de detectar dónde empieza/termina la oración con un solo patrón.
_PATRON_TASA_INVENTADA = re.compile(
    r"(?:contexto\.)?\b(?:delitos_recientes_total_2023_2025|incidentes_nuse_recientes_total)\b"
    r"\s*[:=]?\s*[\d.,]+\s*por\s*100[.,]?\s*(?:000|k)\s*habitantes",
    re.IGNORECASE,
)


def quitar_tasas_inventadas(texto: str) -> str:
    limpio = _PATRON_TASA_INVENTADA.sub("", texto)
    # Limpieza de lo que queda alrededor del fragmento borrado (una "y" o
    # ":" colgando sin nada después, dobles espacios).
    limpio = re.sub(r"\s*\by\b\s*(?=[.,¿?]|$)", "", limpio, flags=re.IGNORECASE)
    limpio = re.sub(r"\s+([.,;:])", r"\1", limpio)
    limpio = re.sub(r":\s*\.", ".", limpio)
    limpio = re.sub(r"\s{2,}", " ", limpio)
    return re.sub(r"\n{3,}", "\n\n", limpio).strip()


def cargar_datos() -> dict:
    if not os.path.exists(ZONAS_PATH):
        raise RuntimeError(
            f"No encontré {ZONAS_PATH}. Corre primero Data/ProcesarRiesgo.py, "
            f"o define la variable de entorno ZONAS_RIESGO_PATH."
        )
    with open(ZONAS_PATH, "r", encoding="utf-8") as f:
        return json.load(f)


def cargar_limites() -> gpd.GeoDataFrame:
    if not os.path.exists(LIMITES_PATH):
        raise RuntimeError(
            f"No encontré {LIMITES_PATH}. Corre primero Agente/ProcesarRiesgo.py, "
            f"o define la variable de entorno LIMITES_LOCALIDADES_PATH."
        )
    gdf = gpd.read_file(LIMITES_PATH)
    if gdf.crs is None or gdf.crs.to_epsg() != 4326:
        gdf = gdf.to_crs(4326)
    return gdf


def cargar_datos_upz() -> dict | None:
    """Capa secundaria (llamadas NUSE por UPZ, ver ProcesarRiesgo.py):
    opcional, si no se generó el backend sigue funcionando sin ella."""
    if not os.path.exists(UPZ_PATH):
        return None
    with open(UPZ_PATH, "r", encoding="utf-8") as f:
        return json.load(f)


def cargar_limites_upz() -> gpd.GeoDataFrame | None:
    if not os.path.exists(UPZ_LIMITES_PATH):
        return None
    gdf = gpd.read_file(UPZ_LIMITES_PATH)
    if gdf.crs is None or gdf.crs.to_epsg() != 4326:
        gdf = gdf.to_crs(4326)
    return gdf


def cargar_barrios() -> gpd.GeoDataFrame | None:
    """Nombres de barrio de OpenStreetMap (Bogotá NO tiene un dataset
    oficial de límites de barrio en el portal de la Alcaldía, así que se
    usa OSM vía BBBike, la misma fuente que ya usa ProcesarRiesgo.py para
    alumbrado/vías). Opcional: si no está el .gpkg, buscar_barrio queda sin
    datos en vez de romper todo.

    Un solo tag no alcanza para cubrir bien los barrios reales: place=
    neighbourhood/suburb/quarter es lo más limpio, pero varios barrios
    reales en Bogotá solo aparecen en OSM como el nombre de un paradero de
    bus ("Urbanización Acapulco (Cl 69b - Kr 71b)", highway=bus_stop) —
    muchísimos con el prefijo abreviado "Br. " (de "Barrio"; ej. "Br. Palo
    Blanco" — hay 777 puntos así, casi tantos como los que sí llevan
    place=neighbourhood) — o como un polígono de uso de suelo residencial
    (landuse=residential, "Conjunto Residencial Acapulco"). Se combinan
    las tres fuentes, excluyendo negocios que casualmente se llaman igual
    (ej. "Calzado Acapulco", una zapatería) por el tag "shop".
    """
    if not OSM_GPKG_PATH or not os.path.exists(OSM_GPKG_PATH):
        return None

    pts = gpd.read_file(OSM_GPKG_PATH, layer="points")
    es_lugar = pts["place"].isin(["neighbourhood", "suburb", "quarter"])
    otros_tags = pts["other_tags"].fillna("") if "other_tags" in pts.columns else ""
    es_negocio = otros_tags.str.contains('"shop"=>', regex=False) | otros_tags.str.contains(
        '"amenity"=>', regex=False
    )
    es_residencial_por_nombre = pts["name"].notna() & pts["name"].str.contains(
        r"^(?:urbanizaci[oó]n|conjunto residencial|barrio|br\.)\s", case=False, regex=True, na=False
    )
    candidatos_pts = pts[(es_lugar | es_residencial_por_nombre) & ~es_negocio][["name", "geometry"]].dropna(
        subset=["name"]
    )

    polys = gpd.read_file(OSM_GPKG_PATH, layer="multipolygons")
    if "landuse" in polys.columns:
        candidatos_poly = polys[(polys["landuse"] == "residential") & polys["name"].notna()][
            ["name", "geometry"]
        ].copy()
        candidatos_poly["geometry"] = candidatos_poly.geometry.centroid
    else:
        candidatos_poly = polys.iloc[0:0][["name", "geometry"]]

    combinado = gpd.GeoDataFrame(
        pd.concat([candidatos_pts, candidatos_poly], ignore_index=True), geometry="geometry", crs=pts.crs
    )
    if combinado.crs is None or combinado.crs.to_epsg() != 4326:
        combinado = combinado.to_crs(4326)
    combinado["nombre_norm"] = combinado["name"].apply(normalizar)
    return combinado


DATOS = cargar_datos()
LIMITES = cargar_limites()
DATOS_UPZ = cargar_datos_upz()
LIMITES_UPZ = cargar_limites_upz()
BARRIOS = cargar_barrios()


def tool_upz_por_punto(lat: float, lng: float) -> dict | None:
    """None si no hay capa UPZ disponible, o si el punto no cae en ninguna
    UPZ clasificada (ej. población insuficiente, ver ProcesarRiesgo.py)."""
    if DATOS_UPZ is None or LIMITES_UPZ is None:
        return None
    punto = Point(lng, lat)
    coincidencias = LIMITES_UPZ[LIMITES_UPZ.contains(punto)]
    if coincidencias.empty:
        return None
    codigo_upz = str(int(coincidencias.iloc[0]["codigo_upz"]))
    info = DATOS_UPZ.get(codigo_upz)
    if info is None or info.get("nivel_llamadas") is None:
        return None
    return {
        "codigo_upz": codigo_upz,
        "upz": info["upz"],
        "nivel_llamadas": info["nivel_llamadas"],
        "tasa_llamadas_100k": info["tasa_llamadas_100k"],
    }


def tool_localidad_por_punto(datos: dict, lat: float, lng: float) -> dict:
    punto = Point(lng, lat)  # shapely usa (x, y) = (lng, lat), no al revés
    coincidencias = LIMITES[LIMITES.contains(punto)]
    if coincidencias.empty:
        return {"error": "Ese punto no cae dentro de ninguna localidad de Bogotá."}
    codigo = str(int(coincidencias.iloc[0]["codigo"]))
    info = datos.get(codigo)
    if info is None:
        return {"error": f"No tengo datos de riesgo para la localidad con código {codigo}."}
    return {
        "codigo": codigo,
        "localidad": info["localidad"],
        "nivel_riesgo": info["nivel_riesgo"],
        "score_mixto": info["score_mixto"],
        "upz": tool_upz_por_punto(lat, lng),
    }


# Palabras que a veces el modelo manda como si fueran el "nombre" de un
# barrio o localidad cuando en realidad la pregunta era de seguimiento sobre
# uno ya mencionado antes en la conversación (ej. "¿dónde queda?" ->
# nombre="donde", "¿qué delitos hay en esa zona?" -> nombre="esa zona").
# Salvaguarda de backend: no depender solo de que el prompt lo evite.
_PALABRAS_REFERENCIA_CONVERSACIONAL = {
    "donde", "aqui", "alli", "aca", "alla",
    "eso", "esto", "ese", "esa", "esos", "esas", "este", "esta", "estos", "estas",
    "cual", "cuales", "como", "que", "quien", "ahi",
    "si", "sl", "no", "ok", "vale", "bien", "listo", "gracias",
    "zona", "sitio", "lugar", "area", "sector", "parte",
}

# Los nombres reales de barrio/localidad en Bogotá no son de 1-2 letras. Sin
# este piso, una cadena corta (ej. "sl", typo de "sí") hace match parcial con
# cualquier barrio que la contenga por casualidad como substring (ej.
# "Isla Menorca", "SLR 6") y devuelve una lista de opciones sin sentido.
_LARGO_MINIMO_BARRIO = 3


def _es_referencia_conversacional(nombre_norm: str) -> bool:
    """True si nombre_norm (ya normalizado) es, o está compuesto ÚNICAMENTE
    de, palabras deícticas/de conversación (ej. "esa zona", "aquí", "donde")
    en vez de un nombre propio real — señal de que el modelo tomó una
    referencia a algo ya dicho antes en la conversación como si fuera el
    argumento, en vez de reusar el dato real ya obtenido antes."""
    palabras = nombre_norm.split()
    return not palabras or all(p in _PALABRAS_REFERENCIA_CONVERSACIONAL for p in palabras)


def tool_buscar_barrio(datos: dict, nombre: str, localidad: str | None = None) -> dict:
    if BARRIOS is None:
        return {"error": "No tengo datos de barrios cargados en este servidor (falta Bogota.gpkg de OSM)."}

    nombre_check = normalizar(nombre)
    if _es_referencia_conversacional(nombre_check) or len(nombre_check) < _LARGO_MINIMO_BARRIO:
        return {
            "error": (
                f"'{nombre}' no es el nombre de un barrio (muy corto o es una "
                "palabra de conversación, no un nombre propio). Si es sobre un "
                "barrio del que ya se habló en esta conversación, o una "
                "respuesta tipo sí/no, no llames a esta herramienta — responde "
                "con la localidad/UPZ que ya se obtuvo antes."
            )
        }

    # OJO: las tres condiciones se evalúan JUNTAS, no en cascada (probar
    # igualdad exacta primero y solo caer a "contains" si no hay match
    # exacto se quedaba con un solo resultado — ej. "Acapulco" en Ciudad
    # Bolívar — e ignoraba otros reales que coinciden parcial, como
    # "Urbanización Acapulco" en Engativá, un barrio DISTINTO que existe de
    # verdad y por eso nunca disparaba la pregunta de "¿cuál de los dos?").
    nombre_norm = normalizar(nombre)
    patron = re.escape(nombre_norm)
    coincidencias = BARRIOS[
        (BARRIOS["nombre_norm"] == nombre_norm)
        | BARRIOS["nombre_norm"].str.contains(patron, na=False, regex=True)
        | BARRIOS["nombre_norm"].apply(lambda n: bool(n) and n in nombre_norm)
    ]
    if coincidencias.empty:
        return {"error": f"No encontré el barrio '{nombre}' en la base de datos (OpenStreetMap)."}

    resultados = []
    for _, fila in coincidencias.iterrows():
        info = tool_localidad_por_punto(datos, fila.geometry.y, fila.geometry.x)
        if "error" not in info:
            # "Br. " es abreviatura de "Barrio" en el nombre de muchos
            # paraderos de bus de OSM: se quita para que la respuesta se
            # lea natural ("Palo Blanco" en vez de "Br. Palo Blanco").
            nombre_limpio = re.sub(r"^br\.\s*", "", fila["name"], flags=re.IGNORECASE)
            resultados.append({"barrio": nombre_limpio, **info})

    if not resultados:
        return {"error": f"Encontré el barrio '{nombre}' pero no pude ubicarlo dentro de ninguna localidad."}

    # Si ya se sabe la localidad (el usuario aclaró tras una pregunta
    # ambigua anterior), filtrar por ahí resuelve la ambigüedad de una vez
    # en vez de devolver la misma lista otra vez.
    if localidad:
        localidad_norm = normalizar(localidad)
        filtrados = [r for r in resultados if localidad_norm in normalizar(r["localidad"])]
        if filtrados:
            resultados = filtrados
        # Si no hay coincidencia con esa localidad, se sigue con todos los
        # resultados (mejor mostrar las opciones reales que fallar en seco
        # por una localidad mal escrita).

    # Varios puntos con el mismo nombre en localidades DISTINTAS: nombre
    # ambiguo de verdad (pasa con ~100 nombres de barrio en Bogotá) — hay
    # que preguntar cuál. Si caen en la misma localidad, no hay ambigüedad
    # real (ej. el mismo barrio con dos puntos OSM cercanos): se deduplica
    # a una opción por localidad, prefiriendo el nombre más limpio (sin
    # paréntesis/referencias de calle de un paradero de bus, ej. "Palo
    # Blanco" en vez de "Palo Blanco (Av. Boyacá - Cl69b) (B)") — importa
    # más aquí que en el chat, donde el LLM lo redactaba disimulando el
    # nombre feo, pero un buscador directo lo muestra tal cual.
    def _legibilidad(r: dict) -> tuple:
        nombre = r["barrio"]
        return (nombre.count("("), len(nombre))

    opciones_por_localidad: dict[str, dict] = {}
    for r in resultados:
        actual = opciones_por_localidad.get(r["localidad"])
        if actual is None or _legibilidad(r) < _legibilidad(actual):
            opciones_por_localidad[r["localidad"]] = r
    if len(opciones_por_localidad) > 1:
        return {
            "error": f"Hay varios barrios llamados '{nombre}' en localidades distintas — pregunta cuál.",
            "opciones": list(opciones_por_localidad.values()),
        }
    return next(iter(opciones_por_localidad.values()))


def tool_obtener_ranking(datos: dict) -> list:
    filas = [(cod, info) for cod, info in datos.items() if cod != "_meta"]
    filas.sort(key=lambda kv: kv[1]["score_mixto"], reverse=True)
    return [
        {
            "posicion": i,
            "localidad": info["localidad"],
            "nivel_riesgo": info["nivel_riesgo"],
            "score_mixto": info["score_mixto"],
            "score_ponderado_100k": info["score_ponderado_100k"],
            "score_ponderado_por_km2": info["score_ponderado_por_km2"],
            "tasa_delitos_100k": info["tasa_delitos_100k"],
        }
        for i, (_, info) in enumerate(filas, start=1)
    ]


def tool_localidad_extrema(datos: dict, cual: str) -> dict:
    ranking = tool_obtener_ranking(datos)
    return ranking[0] if cual == "mayor" else ranking[-1]


def localidad_establecida_reciente(historial: list) -> str | None:
    """Busca hacia atrás en el historial (empezando por lo más reciente) el
    último resultado de herramienta con una "localidad" real (de
    buscar_barrio, localidad_por_punto u obtener_localidad exitosos) — la
    zona "de la que se está hablando" en esta conversación hasta ahora.

    Salvaguarda de backend (ver _es_referencia_conversacional arriba): un
    modelo local de 8B, en una pregunta de seguimiento tipo "¿qué delitos
    son más comunes en esa zona?", a veces no logra recuperar el nombre de
    localidad correcto de turnos atrás y llama obtener_localidad con OTRA
    localidad real cualquiera (ej. pregunta sobre Engativá pero llama con
    "Kennedy") — como esa localidad sí existe, la herramienta no tiene forma
    de detectar el error por sí sola. Esto permite avisarle al modelo del
    posible desvío en vez de dejarlo presentar datos de una localidad como
    si fueran de otra."""
    for mensaje in reversed(historial):
        if mensaje.get("role") != "tool":
            continue
        try:
            resultado = json.loads(mensaje.get("content", ""))
        except (json.JSONDecodeError, TypeError):
            continue
        if isinstance(resultado, dict) and "error" not in resultado and isinstance(resultado.get("localidad"), str):
            return resultado["localidad"]
    return None


def tool_obtener_localidad(datos: dict, nombre: str) -> dict:
    nombre_norm = normalizar(nombre)
    if _es_referencia_conversacional(nombre_norm):
        return {
            "error": (
                f"'{nombre}' no es el nombre de una localidad real (es una "
                "palabra de conversación, no un nombre propio). Si es una "
                "pregunta de seguimiento sobre una localidad ya mencionada "
                "antes en esta conversación, no llames esta herramienta con "
                "esa palabra como argumento — usa el nombre EXACTO de la "
                "localidad que ya se estableció antes (ej. la que devolvió "
                "buscar_barrio)."
            )
        }
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


def tool_comparar_localidades(datos: dict, nombres: list) -> dict:
    return {nombre: tool_obtener_localidad(datos, nombre) for nombre in nombres}


def ejecutar_tool(nombre: str, argumentos: dict, datos: dict):
    if nombre == "localidad_extrema":
        return tool_localidad_extrema(datos, argumentos.get("cual", "mayor"))
    if nombre == "obtener_ranking":
        return tool_obtener_ranking(datos)
    if nombre == "obtener_localidad":
        return tool_obtener_localidad(datos, argumentos.get("nombre", ""))
    if nombre == "comparar_localidades":
        return tool_comparar_localidades(datos, argumentos.get("nombres", []))
    if nombre == "buscar_barrio":
        return tool_buscar_barrio(datos, argumentos.get("nombre", ""), argumentos.get("localidad"))
    if nombre == "recordar_hecho":
        return {"guardado": True}
    return {"error": f"Herramienta desconocida: {nombre}"}


def preguntar(modelo: str, historial: list, datos: dict) -> tuple[str, list[str]]:
    # hechos_nuevos: la app (no este backend) es quien guarda la memoria del
    # usuario, localmente en el celular — este backend es stateless. Cuando
    # el modelo llama recordar_hecho(), en vez de "recordarlo" él mismo aquí,
    # el hecho se junta en esta lista y viaja en la respuesta HTTP para que
    # la app lo persista.
    hechos_nuevos = []

    for _ in range(MAX_RONDAS_TOOLS):
        try:
            resp = requests.post(
                OLLAMA_URL,
                json={
                    "model": modelo,
                    "messages": historial,
                    "tools": TOOLS,
                    "stream": False,
                    "keep_alive": KEEP_ALIVE,
                    "options": {
                        "num_ctx": NUM_CTX,
                        "num_predict": NUM_PREDICT,
                        # Aquí no se quiere creatividad: se trata de elegir bien
                        # la herramienta y repetir cifras sin adornarlas. Bajar
                        # la temperatura también reduce las respuestas largas.
                        "temperature": 0.2,
                    },
                },
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
            texto = desenvolver_json_accidental(reparar_mojibake(mensaje.get("content", "")))
            texto = quitar_frases_sin_respaldo(texto)
            texto = quitar_tasas_inventadas(texto)
            return reemplazar_narracion_meta(texto), hechos_nuevos

        for llamada in tool_calls:
            fn = llamada["function"]
            argumentos = reparar_mojibake_argumentos(fn.get("arguments") or {})
            if fn["name"] == "recordar_hecho":
                hecho = str(argumentos.get("hecho", "")).strip()
                if hecho:
                    hechos_nuevos.append(hecho)
            resultado = ejecutar_tool(fn["name"], argumentos, datos)
            if fn["name"] == "obtener_localidad" and isinstance(resultado, dict):
                establecida = localidad_establecida_reciente(historial)
                # Si la llamada tuvo éxito, la localidad pedida es la que
                # devolvió; si fue error (no existe, ambigua, o la rechazó
                # _es_referencia_conversacional), es la que el modelo puso
                # como argumento -- en ambos casos puede ser un invento.
                localidad_pedida = resultado.get("localidad") if "error" not in resultado else argumentos.get(
                    "nombre", ""
                )
                if establecida and normalizar(establecida) != normalizar(localidad_pedida or ""):
                    ultimo_usuario = next(
                        (m.get("content", "") for m in reversed(historial) if m.get("role") == "user"), ""
                    )
                    # Si el usuario NO nombró esta localidad explícitamente en su
                    # último mensaje, es casi seguro que el modelo perdió el hilo
                    # de la conversación (una pregunta de seguimiento como "¿qué
                    # delitos hay en esa zona?" no menciona ninguna localidad, así
                    # que cualquier nombre que el modelo haya puesto -- exista o
                    # no como localidad real -- es un invento) -- no basta con
                    # avisarle en el resultado (un modelo local de 8B lo ignora y
                    # mezcla los datos de la localidad equivocada con el nombre
                    # correcto, que es peor que no corregir nada), así que se
                    # sobreescribe directo con la localidad ya establecida en vez
                    # de relayar un error o datos de otra localidad.
                    if normalizar(localidad_pedida or "") not in normalizar(ultimo_usuario):
                        resultado = tool_obtener_localidad(datos, establecida)
            historial.append({"role": "tool", "content": json.dumps(resultado, ensure_ascii=False)})

    return "No pude terminar de consultar los datos (demasiadas llamadas a herramientas).", hechos_nuevos


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
    # Hechos que la app ya tiene guardados localmente de conversaciones
    # anteriores con este usuario (ver recordar_hecho). El backend no
    # guarda nada entre requests: la app se los reenvía cada vez.
    hechos_recordados: list[str] = []


class ChatResponse(BaseModel):
    respuesta: str
    mensajes: list[dict[str, Any]]
    hechos_nuevos: list[str] = []


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


@app.get("/riesgo")
def riesgo_por_punto(lat: float, lng: float):
    """Geofencing: dado un punto GPS, resuelve en qué localidad cae y su
    nivel de riesgo. Así es como la app debe consultar la ubicación del
    usuario para las alertas por geolocalización."""
    resultado = tool_localidad_por_punto(DATOS, lat, lng)
    if "error" in resultado:
        raise HTTPException(status_code=404, detail=resultado)
    return resultado


@app.get("/barrios/buscar")
def barrios_buscar(nombre: str, localidad: str | None = None):
    """Búsqueda directa de barrio -> localidad/riesgo, SIN pasar por el
    agente conversacional (determinístico, mismo tool_buscar_barrio que
    usa el chat, ver ahí la lógica de desambiguación). Pensado para un
    buscador en la pantalla de Riesgo de la app: más confiable que
    preguntarle al modelo local, que a veces se equivoca de herramienta o
    responde de forma inconsistente para algo que debería ser un lookup
    directo.

    Respuesta siempre 200, con una de estas formas (el cliente decide qué
    hacer según qué campos trae):
      - Encontrado: {"barrio", "codigo", "localidad", "nivel_riesgo",
        "score_mixto", "upz": {...} | null}
      - Ambiguo (varios barrios con ese nombre en localidades distintas):
        {"error": "...", "opciones": [...]} -- cada opción tiene la misma
        forma que "Encontrado", úsense para dejar elegir cuál.
      - No encontrado: {"error": "..."} (sin "opciones").
    """
    return tool_buscar_barrio(DATOS, nombre, localidad)


@app.get("/zonas/{nombre}")
def zona_por_nombre(nombre: str):
    resultado = tool_obtener_localidad(DATOS, nombre)
    if "error" in resultado:
        raise HTTPException(status_code=404, detail=resultado)
    return resultado


@app.post("/chat", response_model=ChatResponse)
def chat(req: ChatRequest):
    system_prompt = SYSTEM_PROMPT
    if req.hechos_recordados:
        lista = "\n".join(f"- {h}" for h in req.hechos_recordados)
        system_prompt += (
            "\n\nCosas que ya sabes de este usuario, de conversaciones anteriores:\n"
            f"{lista}\nÚsalas si son relevantes para lo que pregunta ahora, pero no las "
            "repitas sin razón ni las menciones si no vienen al caso."
        )
    historial = [{"role": "system", "content": system_prompt}] + req.mensajes
    respuesta, hechos_nuevos = preguntar(req.modelo, historial, DATOS)
    return ChatResponse(respuesta=respuesta, mensajes=historial[1:], hechos_nuevos=hechos_nuevos)
