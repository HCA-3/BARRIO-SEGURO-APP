"""Fitness functions: comprobaciones automáticas de reglas arquitectónicas
(ver DAS sección 8.4, antes marcada PENDIENTE por falta de este archivo).

A diferencia de Api/tests/test_backend_riesgo.py (que prueba comportamiento
en vivo contra el backend corriendo), estas pruebas verifican una regla de
negocio sobre el CÓDIGO FUENTE de ProcesarRiesgo.py directamente -- no
necesitan el backend arrancado.
"""
import os
import re

RUTA_PROCESAR_RIESGO = os.path.normpath(
    os.path.join(os.path.dirname(__file__), "..", "ProcesarRiesgo.py")
)


def _bloque_calculo_score_mixto() -> str:
    """Extrae el bloque de código donde se calcula df['score_mixto'], desde
    la asignación hasta el cierre del paréntesis, para poder inspeccionar
    exactamente qué variables entran en la fórmula."""
    with open(RUTA_PROCESAR_RIESGO, encoding="utf-8") as f:
        fuente = f.read()
    m = re.search(r'df\["score_mixto"\]\s*=\s*\((.*?)\)\n', fuente, re.DOTALL)
    assert m, "No encontré la asignación de df['score_mixto'] en ProcesarRiesgo.py -- revisar si cambió de forma/nombre."
    return m.group(1)


def test_rn01_nivel_riesgo_no_usa_estrato_promedio_como_insumo():
    """RN-01 (regla de negocio documentada en el DAS): el nivel de riesgo se
    calcula solo a partir de criminalidad (score_ponderado_100k /
    score_ponderado_por_km2), nunca del estrato socioeconómico -- para que
    el indicador no reproduzca un sesgo de clase ("barrio pobre = peligroso"
    por construcción). estrato_promedio se guarda solo como dato de
    contexto para mostrarlo al usuario, no como insumo del cálculo.
    """
    bloque = _bloque_calculo_score_mixto()
    assert "estrato" not in bloque.lower(), (
        "RN-01 violada: 'estrato' aparece dentro de la fórmula de score_mixto. "
        f"Bloque encontrado:\n{bloque}"
    )


def test_rn01_formula_score_mixto_es_la_documentada():
    """Verifica que la fórmula siga siendo el promedio 50/50 de las dos
    métricas normalizadas que describe el comentario del propio archivo y
    el DAS (ADR correspondiente) -- si alguien cambia la ponderación sin
    actualizar la documentación, esta prueba debe fallar primero."""
    bloque = _bloque_calculo_score_mixto()
    assert "score_ponderado_100k" in bloque
    assert "score_ponderado_por_km2" in bloque
    assert "0.5" in bloque
