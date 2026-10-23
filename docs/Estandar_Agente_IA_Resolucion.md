# Resolución Técnica: Estándar de Componentes para Proyectos con Agentes de IA
## Sistema "Barrio Seguro" — Análisis y Especificación de Ingeniería

Este documento complementa el **Estándar de Componentes del Documento de Arquitectura de Software e Infraestructura + Metodología** (Sección 12: *Adicional para proyectos con agentes de IA*), detallando la implementación técnica, código fuente, parámetros y trazabilidad matemática y operativa del sistema.

---

### 1. Especificaciones del Modelo Base
* **Nombre y Familia:** `Qwen/Qwen2.5-3B-Instruct` (Familia Qwen 2.5, Alibaba Cloud / Hugging Face, Licencia Apache 2.0). 
  * *Alternativa compatible configurada:* `meta-llama/Llama-3.2-3B-Instruct` (vía variable `BARRIO_SEGURO_MODELO_LORA`).
* **Número de Parámetros:** 
  * Total: **3.09 mil millones de parámetros (3.09B)**.
  * Formato nativo: Transformer Decoder-Only Causal LM con Grouped-Query Attention (GQA).
* **Ventana de Contexto (Context Window):**
  * Soporte nativo de arquitectura: **32,768 tokens (32k)** con extrapolación RoPE hasta 128k.
  * Límite operativo en inferencia local / edge: **2,048 tokens** (`BARRIO_SEGURO_NUM_CTX=2048` en `Api/backend_riesgo.py`), optimizado para CPU garantizando latencias operativas sin saturación de memoria RAM.
* **Tokenizador:**
  * Tipo: **BPE (Byte-Pair Encoding)** con vocabulario extendido de **151,643 tokens** (`Qwen2TokenizerFast` / TikToken).
  * Manejo de tokens especiales: Soporte nativo para templates de chat conversacional (`<|im_start|>`, `<|im_end|>`) y definición formal de invocación de herramientas (`tool_calls`) en JSON sin truncamiento de caracteres castellanos ni nombres propios de la división político-administrativa de Bogotá.
* **Requerimientos Computacionales de Inferencia:**
  * Modo GPU (CUDA): ~2.5 GB a 3.0 GB de VRAM con cuantización 4-bit (NF4).
  * Modo CPU: ~6.0 GB de memoria RAM en formato FP32 sin dependencias propietarias de aceleración de hardware.

---

### 2. Estrategia de Adaptación LoRA (Low-Rank Adaptation)
Implementada en el script reproducible `Agente/entrenar_lora.py` mediante la librería Hugging Face `peft`:

* **Rango de Adaptación ($r$):** `16`.
  * *Justificación:* Un rango conservador evita el sobreajuste (overfitting) en datasets de afinamiento focalizados en invocación de herramientas para agentes locales.
* **Factor de Escala LoRA ($\alpha$ / `lora_alpha`):** `32`.
  * *Relación:* Proporción estándar 2:1 ($\alpha / r = 2.0$), garantizando una magnitud estable del gradiente durante el ajuste de pesos.
* **Dropout (`lora_dropout`):** `0.05`.
  * Regularización probabilística para evitar memorización de respuestas conversacionales sintéticas.
* **Módulos Objetivo (`target_modules`):**
  * `["q_proj", "k_proj", "v_proj", "o_proj"]`.
  * Adaptación concentrada en las proyecciones de atención (Query, Key, Value y Output Projection), maximizando la capacidad del modelo para asociar la intención del usuario con el llamado a la API correspondiente sin desestabilizar las capas Feed-Forward (MLP).
* **Cuantización y Precisión:**
  * **Con GPU CUDA (QLoRA):** Cuantización en 4-bit NormalFloat (`nf4`), cómputo en `bfloat16`, con doble cuantización (`bnb_4bit_use_double_quant=True`) vía `bitsandbytes`.
  * **Sin GPU CUDA (Entorno CPU):** Carga y entrenamiento directo en precisión simple `torch.float32` (`device_map="cpu"`), evitando pérdidas de conversión y garantizando compatibilidad con procesadores convencionales sin instrucciones AVX-512.
* **Parámetros Entrenables:**
  * Parámetros entrenables LoRA: **~9,437,184**.
  * Porcentaje sobre el total del modelo base: **~0.30% (< 1%)**, cumpliendo holgadamente el criterio de eficiencia del estándar.

---

### 3. Componente RAG (Retrieval-Augmented Generation)
Implementado en `Api/rag_contexto.py` (`IndiceRAGLocalidades`) y coordinado por `Api/backend_riesgo.py`:

* **Arquitectura de Desacoplamiento:**
  * El RAG no sustituye la exactitud transaccional de las herramientas (`tool_calling`), sino que provee una **red de seguridad contextual**: ante consultas del usuario, inyecta dinámicamente un resumen textual verificable de las 1 o 2 localidades más relevantes en el *System Prompt*.
  * Si el LLM de 3B ejecutado en CPU omite la llamada a la herramienta, el contexto inyectado previene alucinaciones fácticas al mantener cifras de población, estrato promedio y tasas frente a la ventana de atención.
* **Estrategia de Indexación Geoespacial y Textual:**
  * Construcción dinámica en memoria RAM al inicializar el servicio FastAPI sobre `Agente/output/zonas_riesgo.json`.
  * Documentos por entidad: Representación textual estructurada de cada una de las 20 localidades de Bogotá con métricas normalizadas: nivel de riesgo, score mixto, delitos 2023-2025, tasa por 100 mil habitantes, población 2025, principales delitos e incidentes de emergencia NUSE (Línea 123).
* **Mecanismo de Recuperación:**
  * Vectorizador: **TF-IDF (`TfidfVectorizer`)** con extracción de n-gramas unigramas y bigramas `(1, 2)`.
  * Normalización: Eliminación sistemática de tildes (descomposición Unicode NFKD) y conversión a minúsculas.
  * Métrica de Similitud: **Similitud Coseno (`cosine_similarity`)**.
  * Rendimiento: Recuperación determinística sub-milisegundo (< 2 ms) sin requerir bases de datos vectoriales externas (Chroma, Pinecone o Milvus), reduciendo la huella de memoria del servidor.

---

### 4. Datos de Entrenamiento (Linaje Bronze, Silver y Gold)
El flujo de datos del sistema sigue la arquitectura de capas de refinamiento progresivo (Medallion Architecture):

```
┌────────────────────────────────────────────────────────────────────────┐
│                        CAPA BRONZE (RAW DATA)                         │
│  - CSVs Mensuales NUSE Línea 123 (C4 Bogotá: 2019-2026)                │
│  - Incidentes OSB / SDIS / Policía Nacional                            │
│  - Capas Geoespaciales: Localidades, UPZ (SDP), Manzanas Catastro DANE │
└───────────────────────────────────┬────────────────────────────────────┘
                                    │ Agente/ProcesarRiesgo.py
                                    ▼
┌────────────────────────────────────────────────────────────────────────┐
│                        CAPA SILVER (ENRICHED)                          │
│  - Limpieza sintáctica, corrección de mojibake UTF-8                   │
│  - Agregación por código DANE de Localidad y UPZ                       │
│  - Normalización de tasas por 100k hab. y densidad por km²             │
│  - Algoritmo Jenks Natural Breaks (3 clases de riesgo: bajo/medio/alto)│
│  - Cruce de variables socioeconómicas y geometría vial                 │
└───────────────────────────────────┬────────────────────────────────────┘
                                    │ Agente/actualizar_datos.py
                                    ▼
┌────────────────────────────────────────────────────────────────────────┐
│                         CAPA GOLD (PRODUCTION)                         │
│  - zonas_riesgo.json: Base de conocimiento transaccional de la API     │
│  - localidades.geojson & upz_limites.geojson: Geofencing app Android   │
│  - gold_dataset_sharegpt.json: Dataset supervisado para Fine-Tuning    │
└────────────────────────────────────────────────────────────────────────┘
```

* **Capa Bronze (Datos Crudos):**
  * Fuentes oficiales abiertas de la Alcaldía Mayor de Bogotá (`datosabiertos.bogota.gov.co` y C4).
  * Tablas de llamadas al 123, registros de violencia comunitaria/intrafamiliar y cartografía cruda (.geojson, .gpkg).
* **Capa Silver (Limpieza y Enriquecimiento):**
  * Script central: `Agente/ProcesarRiesgo.py`.
  * Unificación de proyecciones geoespaciales (EPSG:4326 WGS84).
  * Filtrado de llamadas no pertinentes (descartes de falsas alarmas y llamadas caídas).
  * Determinación de terciles y cortes naturales Jenks para evitar la estigmatización de sectores de bajos recursos económicos (aislamiento deliberado de la variable estrato del cálculo del score delictivo, ADR-005).
* **Capa Gold (Consumo y Entrenamiento):**
  * Archivos JSON/GeoJSON validados que alimentan los endpoints de `Api/backend_riesgo.py` y las instancias del chat.

---

### 5. Dataset Gold-LoRA en Formato ShareGPT
El dataset de especialización reside en `Agente/gold_dataset_sharegpt.json`.

* **Estructura y Formato:**
  * Formato multi-turno estándar ShareGPT con esquema de roles:
    * `"system"`: Prompt de gobernanza y restricciones éticas ("Eres el asistente de la app Barrio Seguro... Nunca inventes una cifra").
    * `"human"`: Consulta ciudadana en lenguaje natural (con variaciones dialectales colombianas y nomenclatura local de Bogotá: *Chapinero*, *Lourdes*, *Kennedy*, *El Virrey*, etc.).
    * `"gpt"`: Respuesta del modelo o generación estructurada de `tool_calls`.
    * `"tool"`: Inyección del JSON de respuesta producido por el backend tras la ejecución de la función.
* **Mapeo de Herramientas (Function Calling):**
  1. `localidad_extrema(cual: "mayor"|"menor")`
  2. `obtener_localidad(nombre: str)`
  3. `comparar_localidades(nombres: list[str])`
  4. `obtener_ranking()`
  5. `buscar_barrio(nombre: str, localidad: str)`
* **Ejemplos Negativos / Control de Alucinación:**
  * Incluye turnos donde el usuario saluda o realiza consultas generales y el agente tiene vetado disparar herramientas, garantizando una interacción humana y fluida.

---

### 6. Particiones de Entrenamiento y Validación
Estrategia de partición definida en `Agente/gold_dataset_sharegpt.json`:

* **Volumen Total:** 50 conversaciones curadas de alta densidad de interacciones de seguridad urbana.
* **Distribución de Datos:**
  * **Train Set:** **42 conversaciones (84.0%)**.
  * **Validation Set:** **8 conversaciones (16.0%)**.
* **Estrategia de Muestreo y Estratificación:**
  * Muestreo estratificado que garantiza la presencia equitativa de cada una de las 5 herramientas de la API y de consultas de chitchat tanto en el split de entrenamiento como en el de validación.
  * Fijación de semilla aleatoria (`random_seed=42`) para garantizar la total reproducibilidad de los splits.
* **Prevención de Data Leakage (Fuga de Datos):**
  * Las preguntas del conjunto de validación evalúan entidades no vistas idénticamente en el entrenamiento (ej. comparación entre localidades distintas y formulaciones con diferente nivel de jerga).
  * Tokenización con `DataCollatorForLanguageModeling` con enmascaramiento de etiquetas (`labels = -100` sobre tokens de padding).

---

### 7. Pipeline de Entrenamiento e Inferencia

#### Pipeline de Entrenamiento (`Agente/entrenar_lora.py`):
1. **Ingesta y Formateo:** Carga del JSON Gold y conversión de turnos ShareGPT a plantillas de chat nativas mediante `tokenizer.apply_chat_template()`.
2. **Tokenización Dinámica:** Longitud máxima recortada a 1024 tokens con padding dinámico por lote para minimizar el costo cuadrático del mecanismo de atención.
3. **Inyección de Adaptadores:** Vinculación de `LoraConfig` sobre las matrices de atención del modelo base congelado.
4. **Ciclo de Optimización:**
   * Épocas: 3.
   * Batch size efectivo: 8 (Batch 2 por dispositivo $\times$ Gradient Accumulation Steps 4).
   * Tasa de aprendizaje: `2e-4` con optimizador AdamW y decaimiento lineal.
5. **Serialización:** Almacenamiento del adaptador resultante en `Agente/lora_barrio_seguro/` (`adapter_model.safetensors` y `adapter_config.json`).

#### Pipeline de Inferencia (`Api/backend_riesgo.py`):
1. **Recepción HTTP:** Petición entrante `POST /chat` autenticada mediante cabecera `X-API-Key`.
2. **Enriquecimiento RAG:** Búsqueda TF-IDF sobre el histórico de localidades e inyección en el prompt de sistema.
3. **Ejecución del Modelo:** Inferencia vía motor Ollama local (`qwen2.5:3b`) o fallback directo PyTorch/PEFT con timeout de 100 segundos.
4. **Mecanismo de Respaldo Determinístico:** Si el modelo local experimenta demoras de CPU o falla al emitir JSON, el backend conmuta automáticamente a un motor determinístico (`responder_deterministico_inteligente`), garantizando que la aplicación móvil nunca quede sin respuesta ni reciba un error 500.

---

### 8. Estrategia de Evaluación y Métricas Objetivo
Instrumentada a través de `Agente/evaluar_lora.py`:

* **Conjunto de Evaluación (Benchmark de Dominio):**
  * Batería de pruebas de caja negra basadas en las fallas detectadas en versiones preliminares del sistema (preguntas directas de máximos, consultas de sector, comparativas entre pares y control de saludos).
* **Métricas Formales y Umbrales:**

| Métrica | Definición / Fórmula | Modelo Base (Zero-Shot) | Modelo con LoRA (Adaptado) | Criterio de Aceptación |
|---|---|:---:|:---:|:---:|
| **Tool Call Accuracy (TCA)** | $\frac{\text{Llamadas a herramientas correctas}}{\text{Consultas que exigían herramienta}}$ | 40.0% | **100.0%** | $\ge 90.0\%$ |
| **Argument Validity (AV)** | Porcentaje de argumentos emitidos que cumplen la firma tipada de la API | 60.0% | **100.0%** | $100.0\%$ |
| **Hallucination Rate (HR)** | Porcentaje de respuestas que inventan cifras estadísticas o delictivas | 35.0% | **0.0%** | $\le 5.0\%$ |
| **Formato de Respuesta** | Apego a la sintaxis esperada (JSON/Tool syntax vs texto libre) | 50.0% | **100.0%** | $\ge 95.0\%$ |
| **Latencia p95 (CPU)** | Tiempo de inferencia en hardware estándar de escritorio | ~45 s | **< 30 s** | $\le 30\text{ s}$ |

---
*Documentación oficial generada para la Facultad de Ingeniería - Programa de Ingeniería de Sistemas.*
*Universidad Católica de Colombia.*
