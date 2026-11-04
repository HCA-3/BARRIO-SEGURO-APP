"""Fitness functions para CI: corren sin backend en vivo, sin Ollama y sin
Firebase (el backend Python no llama a Firebase directamente -- eso solo
pasa desde la app Android -- así que no hace falta mockearlo aquí).

Dos grupos de comprobaciones:
1. Reglas de negocio sobre el código fuente de Agente/ProcesarRiesgo.py
   (igual que Agente/tests/test_fitness_functions.py, pensado para correr
   también sin ese paquete instalado en el entorno de CI).
2. Sanidad estructural del backend FastAPI real (Api/backend_riesgo.py),
   usando TestClient en proceso (ASGI in-memory, sin red, sin servidor).
   El chequeo de Ollama en /health está envuelto en try/except en el
   propio backend (ollama_disponible=False si no hay Ollama), así que no
   hace falta mockearlo para que /health responda 200.
"""
import os
import re
import sys

import pytest

RAIZ_REPO = os.path.normpath(os.path.join(os.path.dirname(__file__), ".."))
RUTA_PROCESAR_RIESGO = os.path.join(RAIZ_REPO, "Agente", "ProcesarRiesgo.py")

sys.path.insert(0, os.path.join(RAIZ_REPO, "Api"))
sys.path.insert(0, RAIZ_REPO)


# ---------------------------------------------------------------------------
# 1. Reglas de negocio sobre el código fuente del pipeline
# ---------------------------------------------------------------------------

def _bloque_calculo_score_mixto() -> str:
    with open(RUTA_PROCESAR_RIESGO, encoding="utf-8") as f:
        fuente = f.read()
    m = re.search(r'df\["score_mixto"\]\s*=\s*\((.*?)\)\n', fuente, re.DOTALL)
    assert m, "No encontré la asignación de df['score_mixto'] en ProcesarRiesgo.py."
    return m.group(1)


def test_rn01_nivel_riesgo_no_usa_estrato_promedio_como_insumo():
    """RN-01: el riesgo se calcula solo con criminalidad, nunca con el
    estrato socioeconómico, para no reproducir un sesgo de clase."""
    bloque = _bloque_calculo_score_mixto()
    assert "estrato" not in bloque.lower(), (
        f"RN-01 violada: 'estrato' aparece en la fórmula de score_mixto.\n{bloque}"
    )


def test_rn01_formula_score_mixto_es_la_documentada():
    bloque = _bloque_calculo_score_mixto()
    assert "score_ponderado_100k" in bloque
    assert "score_ponderado_por_km2" in bloque
    assert "0.5" in bloque


# ---------------------------------------------------------------------------
# 2. Sanidad estructural del backend FastAPI (sin servidor real, sin Ollama)
# ---------------------------------------------------------------------------

@pytest.fixture(scope="module")
def cliente():
    from fastapi.testclient import TestClient
    from Api.backend_riesgo import app

    return TestClient(app)


def test_health_responde_sin_necesitar_ollama(cliente):
    r = cliente.get("/health")
    assert r.status_code == 200
    cuerpo = r.json()
    assert cuerpo["status"] == "ok"
    assert cuerpo["localidades_cargadas"] == 20
    # ollama_disponible puede ser False en CI (no hay Ollama corriendo) --
    # lo importante es que el endpoint no se caiga por eso.
    assert isinstance(cuerpo["ollama_disponible"], bool)


def test_zonas_devuelve_las_20_localidades_reales(cliente):
    r = cliente.get("/zonas")
    assert r.status_code == 200
    cuerpo = r.json()
    claves_localidad = [k for k in cuerpo if k != "_meta"]
    assert len(claves_localidad) == 20


def test_zonas_ranking_viene_ordenado_por_score_descendente(cliente):
    """Regresión: tool_obtener_ranking prometía orden por riesgo en su
    propia descripción pero no lo cumplía (bug real corregido)."""
    r = cliente.get("/zonas/ranking")
    assert r.status_code == 200
    ranking = r.json()
    assert len(ranking) == 20
    scores = [item["score_mixto"] for item in ranking]
    assert scores == sorted(scores, reverse=True)


def test_openapi_expone_las_rutas_reales(cliente):
    esquema = cliente.app.openapi()
    rutas = set(esquema["paths"].keys())
    for ruta_esperada in ("/health", "/zonas", "/zonas/ranking", "/riesgo", "/chat"):
        assert ruta_esperada in rutas, f"Falta la ruta {ruta_esperada} en el esquema OpenAPI real"
