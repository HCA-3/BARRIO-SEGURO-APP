"""Prueba de regresión para Defecto D-01:
Verifica que con Ollama apagado, el backend FastAPI responde a 30 preguntas
al chat (incluyendo las 20 localidades de Bogotá, comparaciones y consultas generales)
utilizando el motor determinístico inteligente, sin generar error 500 por valores
vacíos en luminarias o estrato (ej. Sumapaz) y devolviendo respuestas no vacías.
"""
import os
import sys
from unittest.mock import patch

import pytest
import requests

RAIZ_REPO = os.path.normpath(os.path.join(os.path.dirname(__file__), ".."))
sys.path.insert(0, os.path.join(RAIZ_REPO, "Api"))
sys.path.insert(0, RAIZ_REPO)

PREGUNTAS_30 = [
    # 20 localidades oficiales de Bogotá
    "¿Cómo es la seguridad en Usaquén?",
    "¿Qué tan seguro es Chapinero?",
    "Cuéntame sobre la seguridad en Santa Fe",
    "¿Cuál es el nivel de riesgo en San Cristóbal?",
    "Información sobre Usme",
    "¿Qué riesgo hay en Tunjuelito?",
    "¿Es peligroso Bosa?",
    "Háblame de la seguridad en Kennedy",
    "¿Qué tan seguro es Fontibón?",
    "Nivel de seguridad de Engativá",
    "¿Cómo está la delincuencia en Suba?",
    "Seguridad en Barrios Unidos",
    "¿Qué tan seguro es Teusaquillo?",
    "Dime el riesgo de Los Mártires",
    "Seguridad en Antonio Nariño",
    "¿Cómo es Puente Aranda en términos de riesgo?",
    "Nivel de delincuencia en La Candelaria",
    "¿Qué tan seguro es Rafael Uribe Uribe?",
    "Háblame del riesgo en Ciudad Bolívar",
    "¿Qué tan seguro es Sumapaz?",
    # 10 consultas adicionales (extremos, rankings, saludos, ayuda, metodología, sismos, comparaciones)
    "¿Cuál es la localidad más peligrosa de Bogotá?",
    "¿Cuál es la localidad más segura de Bogotá?",
    "Muéstrame el ranking de seguridad de las localidades",
    "Hola, buenas tardes",
    "¿Qué puedes hacer y cómo me puedes ayudar?",
    "¿Cómo se calcula la fórmula de riesgo?",
    "¿Cuáles son las líneas de emergencia de Bogotá?",
    "¿Qué debo hacer en caso de sismo o temblor?",
    "Comparar Chapinero con Sumapaz",
    "¿En qué localidad queda el barrio Chicó?",
]


@pytest.fixture(scope="module")
def cliente():
    from fastapi.testclient import TestClient
    from Api.backend_riesgo import app

    return TestClient(app)


def test_chat_30_preguntas_sin_ollama_sin_error_500(cliente):
    """Ejecuta las 30 preguntas con Ollama apagado y valida que ninguna da 500 ni respuesta vacía."""
    original_get = requests.get

    def mock_get(url, *args, **kwargs):
        # Simula Ollama apagado asegurando que la verificación local falle
        if "11434" in str(url):
            raise requests.exceptions.ConnectionError("Ollama apagado para prueba de regresión D-01")
        return original_get(url, *args, **kwargs)

    respuestas_correctas = 0
    with patch("requests.get", side_effect=mock_get):
        for idx, pregunta in enumerate(PREGUNTAS_30, start=1):
            res = cliente.post(
                "/chat",
                json={
                    "modelo": "llama3.2:latest",
                    "mensajes": [{"role": "user", "content": pregunta}],
                },
            )
            # 1. No debe responder error 500 (ni ningún código de error HTTP)
            assert res.status_code == 200, (
                f"Pregunta #{idx} ('{pregunta}') falló con código HTTP {res.status_code}: {res.text}"
            )

            cuerpo = res.json()
            assert "respuesta" in cuerpo, f"Pregunta #{idx} no incluyó el campo 'respuesta'"
            texto = cuerpo["respuesta"]

            # 2. La respuesta no debe venir vacía
            assert isinstance(texto, str) and texto.strip(), (
                f"Pregunta #{idx} ('{pregunta}') devolvió una respuesta vacía"
            )

            respuestas_correctas += 1

    assert respuestas_correctas == 30, f"Se esperaban 30 respuestas exitosas, se obtuvieron {respuestas_correctas}"


@pytest.mark.parametrize("pregunta", PREGUNTAS_30, ids=[f"p{i:02d}_{p[:25]}" for i, p in enumerate(PREGUNTAS_30, 1)])
def test_pregunta_individual_sin_ollama(cliente, pregunta):
    """Permite correr y visualizar cada una de las 30 preguntas como un caso de prueba parametrizado en pytest."""
    original_get = requests.get

    def mock_get(url, *args, **kwargs):
        if "11434" in str(url):
            raise requests.exceptions.ConnectionError("Ollama apagado")
        return original_get(url, *args, **kwargs)

    with patch("requests.get", side_effect=mock_get):
        res = cliente.post(
            "/chat",
            json={
                "modelo": "llama3.2:latest",
                "mensajes": [{"role": "user", "content": pregunta}],
            },
        )
        assert res.status_code == 200
        texto = res.json().get("respuesta", "")
        assert isinstance(texto, str) and texto.strip()
