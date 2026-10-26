"""Pruebas automatizadas reales contra el backend FastAPI en ejecución.

Antes no existía ninguna prueba automatizada en el proyecto (ver sección 7
"Pruebas" del Documento de Arquitectura de Software, que lo admitía
explícitamente). Estas pruebas cierran esa brecha.

Requieren el backend ya corriendo (python run_api.py), porque ejercitan el
comportamiento real end-to-end -- incluida la carga real de
Agente/output/localidades.geojson y zonas_riesgo.json -- en vez de mockear la
app de FastAPI. Se puede cambiar la URL con la variable de entorno
BARRIO_SEGURO_TEST_URL si el backend corre en otro host/puerto.

Ejecutar:
    pip install pytest requests geopandas shapely
    python -m pytest Api/tests/test_backend_riesgo.py -v
"""
import os

import geopandas as gpd
import pytest
import requests

BASE_URL = os.environ.get("BARRIO_SEGURO_TEST_URL", "http://127.0.0.1:8000")
BASE_DIR = os.path.dirname(os.path.abspath(__file__))
LIMITES_PATH = os.path.normpath(
    os.path.join(BASE_DIR, "..", "..", "Agente", "output", "localidades.geojson")
)


def _backend_disponible() -> bool:
    try:
        return requests.get(f"{BASE_URL}/health", timeout=3).status_code == 200
    except requests.exceptions.RequestException:
        return False


pytestmark = pytest.mark.skipif(
    not _backend_disponible(),
    reason=f"El backend no responde en {BASE_URL}. Arráncalo con 'python run_api.py' antes de correr estas pruebas.",
)


# ---------------------------------------------------------------------------
# Fixtures: puntos reales dentro de cada una de las 20 localidades
# ---------------------------------------------------------------------------

def _puntos_por_localidad() -> list[tuple[str, str, float, float]]:
    """(codigo, nombre, lat, lng) usando un punto garantizado dentro del
    polígono real de cada localidad (representative_point, no centroid --
    el centroide de un polígono cóncavo puede caer fuera de la forma)."""
    gdf = gpd.read_file(LIMITES_PATH)
    if gdf.crs is None or gdf.crs.to_epsg() != 4326:
        gdf = gdf.to_crs(4326)
    puntos = []
    for _, fila in gdf.iterrows():
        punto = fila.geometry.representative_point()
        puntos.append((str(int(fila["codigo"])), fila["localidad"], punto.y, punto.x))
    return puntos


PUNTOS_LOCALIDADES = _puntos_por_localidad()


# ---------------------------------------------------------------------------
# 1. Geofencing: punto -> localidad correcta, para las 20 localidades reales
# ---------------------------------------------------------------------------

@pytest.mark.parametrize(
    "codigo,nombre,lat,lng", PUNTOS_LOCALIDADES, ids=[p[1] for p in PUNTOS_LOCALIDADES]
)
def test_geofencing_punto_resuelve_localidad_correcta(codigo, nombre, lat, lng):
    r = requests.get(f"{BASE_URL}/riesgo", params={"lat": lat, "lng": lng}, timeout=10)
    assert r.status_code == 200, f"{nombre}: esperaba 200, obtuvo {r.status_code}: {r.text}"
    cuerpo = r.json()
    assert cuerpo["codigo"] == codigo, f"{nombre}: código esperado {codigo}, obtuvo {cuerpo['codigo']}"
    assert cuerpo["localidad"] == nombre
    assert cuerpo["nivel_riesgo"] in ("Bajo", "Medio", "Alto", "Muy alto") or cuerpo["nivel_riesgo"]
    assert isinstance(cuerpo["score_mixto"], (int, float))


def test_geofencing_punto_fuera_de_bogota_da_404():
    # Océano Pacífico, lejos de cualquier localidad de Bogotá.
    r = requests.get(f"{BASE_URL}/riesgo", params={"lat": 4.0, "lng": -80.0}, timeout=10)
    assert r.status_code == 404


# ---------------------------------------------------------------------------
# 2. Smoke tests de los endpoints de solo lectura
# ---------------------------------------------------------------------------

def test_health():
    r = requests.get(f"{BASE_URL}/health", timeout=5)
    assert r.status_code == 200
    cuerpo = r.json()
    assert cuerpo["status"] == "ok"
    assert cuerpo["localidades_cargadas"] == 20


def test_zonas_devuelve_las_20_localidades():
    r = requests.get(f"{BASE_URL}/zonas", timeout=10)
    assert r.status_code == 200
    cuerpo = r.json()
    claves_localidad = [k for k in cuerpo if k != "_meta"]
    assert len(claves_localidad) == 20


def test_zonas_ranking_viene_ordenado_por_score_descendente():
    r = requests.get(f"{BASE_URL}/zonas/ranking", timeout=10)
    assert r.status_code == 200
    ranking = r.json()
    assert isinstance(ranking, list)
    assert len(ranking) == 20
    scores = [item["score_mixto"] for item in ranking]
    assert scores == sorted(scores, reverse=True), "El ranking debería venir ordenado de mayor a menor riesgo"


@pytest.mark.parametrize("cual", ["mayor", "menor"])
def test_zonas_extremo_valido(cual):
    r = requests.get(f"{BASE_URL}/zonas/extremo", params={"cual": cual}, timeout=10)
    assert r.status_code == 200
    assert "localidad" in r.json()


def test_zonas_extremo_parametro_invalido_da_400():
    r = requests.get(f"{BASE_URL}/zonas/extremo", params={"cual": "lo_que_sea"}, timeout=10)
    assert r.status_code == 400


def test_zona_por_nombre_existente():
    r = requests.get(f"{BASE_URL}/zonas/Chapinero", timeout=10)
    assert r.status_code == 200
    assert r.json()["localidad"] == "Chapinero"


def test_zona_por_nombre_inexistente_da_404():
    r = requests.get(f"{BASE_URL}/zonas/NoExiste12345", timeout=10)
    assert r.status_code == 404


def test_barrios_buscar_sin_coincidencias():
    r = requests.get(f"{BASE_URL}/barrios/buscar", params={"nombre": "XyzNoExisteComoBarrio"}, timeout=10)
    assert r.status_code == 200
    assert "error" in r.json()


# ---------------------------------------------------------------------------
# 3. Regresión de los 3 bugs reales encontrados y corregidos en producción
#    (reproducidos en vivo contra Ollama durante la auditoría del proyecto)
# ---------------------------------------------------------------------------

def test_chat_no_revienta_con_pregunta_que_antes_causaba_keyerror_posicion():
    """Antes de la corrección, esta pregunta exacta producía un 500
    (KeyError: 'posicion') dentro de responder_deterministico_inteligente."""
    r = requests.post(
        f"{BASE_URL}/chat",
        json={"mensajes": [{"role": "user", "content": "¿Qué tan seguro es Chapinero?"}]},
        timeout=100,
    )
    assert r.status_code == 200
    assert r.json()["respuesta"].strip() != ""


def test_chat_responde_algo_no_vacio_a_un_saludo():
    """Antes de la corrección, un saludo podía colar una llamada a
    herramienta disfrazada de texto JSON crudo, o un string vacío que el
    código anterior aceptaba como respuesta 'válida'."""
    r = requests.post(
        f"{BASE_URL}/chat",
        json={"mensajes": [{"role": "user", "content": "Hola"}]},
        timeout=100,
    )
    assert r.status_code == 200
    respuesta = r.json()["respuesta"].strip()
    assert respuesta != ""
    assert not respuesta.startswith("{\"name\"")
