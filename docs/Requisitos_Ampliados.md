# Requisitos funcionales y no funcionales — ampliación

Extiende las tablas RF-01..RF-10 / RNF-01..RNF-07 de
`Documento_Arquitectura_Software.docx` (sección 1). No se renumeran los
requisitos existentes -- esto solo agrega filas nuevas (RF-11 en adelante,
RNF-08 en adelante) y corrige el estado de cobertura de dos filas
existentes que ya no son PENDIENTE tras el trabajo de esta sesión.

Cada fila nueva está derivada de comportamiento real observado en el
código o de un bug real encontrado y corregido durante la auditoría del
proyecto -- no de requisitos hipotéticos o "deseables" sin respaldo.

---

## Correcciones a filas existentes

| ID | Cambio |
|---|---|
| RNF-05 (Seguridad) | Estado pasa de **PENDIENTE** a **Cubierto (parcial)**: `X-API-Key`/`X-Admin-Key` implementadas (ver `Complemento_DAS.md` §6 y `Complemento_DAS_2.md` §G). Parcial porque el script de despliegue a Oracle Cloud no las activa por defecto (hallazgo real, ver `Complemento_DAS_2.md` punto B) y porque no hay TLS. |
| RNF-01 (Eficiencia de desempeño) | Se agrega la métrica objetivo propuesta en `Complemento_DAS.md` §1.7 (p95 ≤ 30s en chat, p95 ≤ 1s en endpoints de solo lectura) -- antes solo decía "timeout de 100s", que es un límite máximo, no un objetivo de desempeño. |

---

## Requisitos funcionales nuevos

| ID | Requisito | Dónde vive en el código | Caso de uso | Origen |
|---|---|---|---|---|
| RF-11 | Cuando el nombre de barrio buscado existe en más de una localidad, el sistema debe devolver todas las opciones encontradas (no una elegida arbitrariamente) para que el usuario desambigüe. | Backend — `tool_buscar_barrio()`, campo `"opciones"` de la respuesta de `/barrios/buscar` | CU-02 | Comportamiento real verificado en `backend_riesgo.py`; probado en `Api/tests/test_backend_riesgo.py::test_barrios_buscar_sin_coincidencias` (caso complementario de "sin coincidencias"; el caso de ambigüedad real requeriría un barrio homónimo conocido para parametrizar la prueba) |
| RF-12 | El agente debe poder recordar un hecho que el usuario mencione explícitamente durante la conversación (ej. "vivo en Kennedy"), pero NO debe disparar esa herramienta ante saludos o comentarios sobre la conversación misma (ej. "hola", "es la primera vez que hablamos"). | Backend — tool `recordar_hecho`, guardrail `_PISTAS_HECHO_INVALIDO` en `preguntar()` | CU-03 | Bug real encontrado y corregido esta sesión: el modelo guardaba "hechos" falsos como "Habló por primera vez" |
| RF-13 | El endpoint `/chat` debe devolver siempre una respuesta de texto no vacía, incluso si Ollama falla, se cuelga, o devuelve una llamada a herramienta sin parsear como texto crudo. | Backend — `_intentar_extraer_tool_call_de_texto()`, `responder_deterministico_inteligente()` como respaldo garantizado | CU-03 | 2 bugs reales encontrados y corregidos esta sesión (JSON crudo filtrándose al usuario; retorno de string vacío aceptado como respuesta "válida"). Regresión cubierta por `test_chat_responde_algo_no_vacio_a_un_saludo` y `test_chat_no_revienta_con_pregunta_que_antes_causaba_keyerror_posicion` |
| RF-14 | El ranking de las 20 localidades (`/zonas/ranking`, tool `obtener_ranking`) debe venir ordenado de mayor a menor riesgo. | Backend — `tool_obtener_ranking()` | CU-01, CU-03 | Bug real encontrado y corregido esta sesión (ver `Complemento_DAS_2.md` §H): la lista venía en orden de código oficial DANE, no de riesgo, rompiendo silenciosamente la pestaña "Ranking" de la app. Cubierto por `test_zonas_ranking_viene_ordenado_por_score_descendente` |
| RF-15 | Un usuario debe poder "apoyar" (votar) una calificación de cuadra ya publicada por otro usuario, y el contador de apoyos debe ser consistente ante votos concurrentes de distintos usuarios. | App Android — `FirebaseComunidadManager.apoyarCalificacionCuadra()`; Firestore — `runTransaction` sobre el campo `votosApoyo` | CU-05 | Verificado en código: usa `firestore.runTransaction` (lectura-modificación-escritura atómica), no un simple `increment()` sin protección |
| RF-16 | Un administrador debe poder eliminar un mensaje del chat comunitario o una calificación de cuadra, y esa eliminación debe ser visible para todos los usuarios conectados casi de inmediato. | Backend — `DELETE /comunidad/mensajes/{id}`, `DELETE /cuadras/calificaciones/{id}` (ambos requieren `X-Admin-Key`); Firestore — `addSnapshotListener` propaga el borrado en tiempo real | CU-08 | Verificado en código; métrica objetivo ya propuesta en `Complemento_DAS.md` §2.2 (ESC-05, ≤5s) |
| RF-17 | El pipeline debe clasificar el nivel de riesgo de cada localidad en 3 niveles usando un método estadístico de cortes naturales (Jenks Natural Breaks) en vez de terciles o umbrales fijos arbitrarios. | `Agente/ProcesarRiesgo.py` — `clasificar_por_jenks()` | Regla de negocio | Decisión de diseño documentada en el propio código (comentario explica por qué no se usan terciles) |
| RF-18 | El nivel de riesgo nunca debe calcularse a partir del estrato socioeconómico promedio de la localidad, para evitar que el indicador reproduzca un sesgo de clase. | `Agente/ProcesarRiesgo.py` — fórmula de `score_mixto` | Regla de negocio (RN-01) | Verificado automáticamente por `Agente/tests/test_fitness_functions.py::test_rn01_nivel_riesgo_no_usa_estrato_promedio_como_insumo` |
| RF-19 | Cuando haya datos suficientes, el sistema debe poder resolver el nivel de riesgo de un punto GPS también a nivel de UPZ (más granular que localidad), además del nivel de localidad. | Backend — `tool_upz_por_punto()`, campo `"upz"` de la respuesta de `/riesgo` | CU-01, CU-04 | Verificado en código: devuelve `None` explícitamente si la UPZ no tiene clasificación (población insuficiente), en vez de inventar un valor |
| RF-20 | Todo contenido de texto libre generado por un usuario (mensajes del chat comunitario, justificación y alias de una calificación de cuadra) debe pasar por un filtro de lenguaje ofensivo antes de almacenarse, tanto en el cliente como en el servidor. | App Android — `FiltroGroserias.censurar()`; Backend — `censurar_texto()` | CU-05, CU-06 | Verificado en código en ambos lados de forma independiente (defensa en profundidad) |

---

## Requisitos no funcionales nuevos

| ID | Característica (ISO/IEC 25010) | Requisito | Implementación actual | Métrica objetivo |
|---|---|---|---|---|
| RNF-08 | Fiabilidad / tolerancia a fallos | El backend debe seguir funcionando y mostrando información correcta aunque Ollama devuelva texto corrupto (mojibake UTF-8 mal decodificado, común en Windows) o un JSON crudo de tool-call sin envolver. | `reparar_mojibake()`, `desenvolver_json_accidental()`, `_intentar_extraer_tool_call_de_texto()` | **[Propuesta]** 0 respuestas con mojibake visible o JSON crudo visible al usuario final, verificado por las pruebas de regresión de `Api/tests/test_backend_riesgo.py` |
| RNF-09 | Seguridad (riesgo residual) | La clave de API embebida en el APK debe tratarse como extraíble por un atacante con conocimiento técnico; el sistema no debe depender de que esa clave permanezca secreta para su seguridad general. | Aceptado explícitamente como riesgo residual en `Complemento_DAS.md` §6 | **[Propuesta]** Ningún endpoint debe permitir una acción irreversible o de alto impacto (ej. borrado masivo) solo con la clave compartida -- hoy `DELETE` requiere además `X-Admin-Key`, que no está en el APK de usuario final, cumpliendo esto por diseño |
| RNF-10 | Capacidad de monitoreo (observabilidad) | El sistema debe permitir a un administrador ver indicadores básicos de salud y carga del servidor en tiempo real, sin acceso directo a la máquina. | `GET /telemetria/resumen` (CPU, RAM, latencia media, usuarios activos, uptime) y `GET /telemetria/actividad` (historial reciente) | Cubierto. **[Propuesta]** complementar con alertas automáticas (ej. notificación si `ram_pct` > 90% por más de 5 min) -- no implementado hoy |
| RNF-11 | Verificabilidad / testabilidad | El sistema debe tener pruebas automatizadas que se puedan ejecutar y verificar su resultado sin intervención manual. | `Api/tests/test_backend_riesgo.py` (32 pruebas) + `Agente/tests/test_fitness_functions.py` (2 pruebas) = 34 pruebas, ejecutables con `python -m pytest` | Cubierto para el backend. **[Propuesta]** extender a la app Android (0% de cobertura hoy -- ni JUnit ni Compose UI Testing configurados en el repo) |
| RNF-12 | Privacidad / cumplimiento legal | El tratamiento de datos personales (ubicación GPS, IP de origen) debe ser conforme a la Ley 1581 de 2012 en lo que respecta a información y finalidad. | Análisis de clasificación en `Complemento_DAS_2.md` §F.2: ninguno de estos datos cae en las categorías de "dato sensible" del art. 5, pero no hay aviso de privacidad ni consentimiento explícito implementado | **PENDIENTE [Equipo]** — decidir si se agrega un aviso de privacidad mínimo antes de la entrega |
| RNF-13 | Consistencia de datos | Cuando una misma operación de escritura exista en dos almacenes distintos (Firestore y la copia local del backend), el sistema debe documentar y, en la medida de lo posible, acotar el riesgo de divergencia entre ambos. | Hoy: ninguna reconciliación automática entre `calificaciones_cuadras` (Firestore) y `Api/calificaciones_cuadras.json` (ver `Complemento_DAS_2.md` §A.2) | **[Propuesta]** Riesgo aceptado y documentado como deuda técnica mientras el volumen de escritura sea bajo (alcance académico); revisar antes de un despliegue con tráfico real |
| RNF-14 | Mantenibilidad / consistencia de contrato | La descripción de una herramienta (`tool`) expuesta al modelo de lenguaje debe coincidir con lo que la implementación real hace, para que el propio desarrollo no induzca al modelo (o a otro desarrollador) a confiar en un comportamiento que no existe. | Caso real que la violó y ya se corrigió: `obtener_ranking` prometía orden por riesgo y no lo cumplía (ver RF-14) | **[Propuesta]** Revisión manual de las descripciones de `TOOLS` contra su implementación cada vez que una tool cambie; candidato a automatizarse como fitness function futura (comparar substrings clave de la docstring contra el comportamiento, análogo a `test_fitness_functions.py`) |

---

## Nota metodológica

Ninguna fila de esta tabla fue generada para "llenar" un número mínimo de
requisitos -- cada una corresponde a una línea de código real, un archivo
real, o un bug real que se reprodujo y corrigió durante esta sesión de
trabajo (ver historial de cambios en `Api/backend_riesgo.py` y los
archivos de prueba nuevos). Donde la cobertura de un requisito es parcial
o depende de una decisión que solo el equipo puede tomar, se marca
explícitamente como tal en vez de reportarse como "Cubierto".
