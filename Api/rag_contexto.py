"""
Componente RAG (Retrieval-Augmented Generation) para el agente de Barrio Seguro.

No sustituye al tool-calling (las cifras que el agente CITA siguen viniendo de
las funciones de Api/backend_riesgo.py, nunca de aquí) -- lo complementa: antes
de llamar a Ollama, se recupera el resumen en texto de las 1-2 localidades más
relacionadas con la pregunta del usuario (por similitud TF-IDF) y se agrega
como contexto adicional en el system prompt. Esto ayuda especialmente cuando
el modelo (3B, corriendo en CPU) no decide invocar una herramienta: aun así
tiene a la vista datos reales de la localidad relevante, en vez de alucinar.

Indice construido en memoria al arrancar el backend, sobre el mismo
zonas_riesgo.json que ya carga backend_riesgo.py -- no hay base vectorial
externa (no se justifica para 20 documentos).
"""
from __future__ import annotations

import unicodedata

from sklearn.feature_extraction.text import TfidfVectorizer
from sklearn.metrics.pairwise import cosine_similarity


def _quitar_tildes(texto: str) -> str:
    nfkd = unicodedata.normalize("NFKD", texto)
    return "".join(c for c in nfkd if not unicodedata.combining(c))


def _preprocesar(texto: str) -> str:
    return _quitar_tildes(texto.lower())


def _construir_documento(codigo: str, info: dict) -> str:
    """Resumen en texto plano de una localidad, a partir de sus datos reales
    (los mismos que ya sirve la API) -- sin inventar ningún dato nuevo."""
    detalle = info.get("detalle_delitos", {})
    top_delitos = sorted(detalle.items(), key=lambda kv: kv[1], reverse=True)[:3]
    top_txt = ", ".join(f"{nombre} ({cantidad})" for nombre, cantidad in top_delitos)
    ctx = info.get("contexto", {})

    return (
        f"Localidad {info['localidad']}. Nivel de riesgo: {info['nivel_riesgo']}. "
        f"Score mixto: {info['score_mixto']}. "
        f"Delitos verificados 2023-2025: {info['delitos_recientes_total_2023_2025']}. "
        f"Tasa por 100 mil habitantes: {info['tasa_delitos_100k']}. "
        f"Población 2025: {info['poblacion_2025']}. "
        f"Categorías de delito más frecuentes: {top_txt}. "
        f"Estrato promedio: {ctx.get('estrato_promedio')}. "
        f"Área: {ctx.get('area_km2')} km2. "
        f"Longitud de vías: {ctx.get('longitud_vias_km')} km. "
        f"Incidentes NUSE (llamadas de emergencia, no delito verificado): "
        f"{ctx.get('incidentes_nuse_recientes_total')}."
    )


class IndiceRAGLocalidades:
    """Índice TF-IDF en memoria sobre los resúmenes de las 20 localidades."""

    def __init__(self, datos: dict):
        self.codigos: list[str] = []
        self.nombres: list[str] = []
        self.documentos: list[str] = []

        for codigo, info in datos.items():
            if codigo == "_meta":
                continue
            self.codigos.append(codigo)
            self.nombres.append(info["localidad"])
            self.documentos.append(_construir_documento(codigo, info))

        self.vectorizador = TfidfVectorizer(
            preprocessor=_preprocesar,
            ngram_range=(1, 2),
            min_df=1,
        )
        self.matriz = self.vectorizador.fit_transform(self.documentos)

    def recuperar(self, pregunta: str, k: int = 2, umbral: float = 0.05) -> list[dict]:
        """Devuelve hasta k documentos más relevantes para la pregunta, con su
        score de similitud. Si ninguno supera el umbral, devuelve lista vacía
        (mejor no agregar contexto irrelevante que confundir al modelo)."""
        vector_pregunta = self.vectorizador.transform([pregunta])
        similitudes = cosine_similarity(vector_pregunta, self.matriz)[0]

        indices_ordenados = similitudes.argsort()[::-1][:k]
        resultados = []
        for i in indices_ordenados:
            score = float(similitudes[i])
            if score < umbral:
                continue
            resultados.append({
                "localidad": self.nombres[i],
                "documento": self.documentos[i],
                "score": round(score, 4),
            })
        return resultados

    def contexto_para_prompt(self, pregunta: str, k: int = 2) -> str:
        """Texto listo para inyectar en el system prompt, o cadena vacía si
        no hay nada suficientemente relevante."""
        recuperados = self.recuperar(pregunta, k=k)
        if not recuperados:
            return ""
        bloques = "\n".join(f"- {r['documento']}" for r in recuperados)
        return (
            "\n\nCONTEXTO RECUPERADO (RAG) sobre localidades posiblemente "
            "relevantes a esta pregunta -- úsalo solo como referencia "
            "adicional, SIGUE llamando a las herramientas para confirmar "
            "cifras exactas antes de citarlas, no tomes esto como la fuente "
            f"final de verdad:\n{bloques}"
        )
