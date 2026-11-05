# 📱 Catálogo de Mockups - Barrio Seguro App

Este directorio contiene las imágenes de alta fidelidad en formato **Mockup profesional** de la aplicación móvil **Barrio Seguro**, diseñadas para su inclusión en la documentación técnica, manuales de usuario y presentaciones académicas/institucionales.

---

## 📂 Archivos Generados (Mapeo con `mockups.xlsx`)

Los archivos se encuentran disponibles en:
- `documentacion/`
- `documentacion/mockups/`
- Directamente embebidos en `documentacion/mockups/mockups.xlsx`

| # | Archivo | Pantalla / Módulo | Historia | Términos de Dominio | Estado de Error o Vacío Representado |
| :-: | :--- | :--- | :-: | :--- | :--- |
| **01** | `01_terminos_y_condiciones.jpg` | **Términos y condiciones** | HU-02b | términos, ubicación, Ley 1581 | **No deja avanzar sin aceptar** (checkbox sin marcar, banner de advertencia y botón deshabilitado). |
| **02** | `02_riesgo_mapa_ranking.jpg` | **Riesgo (mapa y ranking)** | HU-01 | localidad, nivel de riesgo, score | **Sin conexión muestra el ranking de respaldo guardado en la app** (banner superior ámbar offline, mapa de calor y ranking local Chapinero Score 78.4). |
| **03** | `03_detalle_localidad.jpg` | **Detalle de localidad** | HU-01 | delitos por categoría, contexto | Desglose analítico de delitos (Hurto personas 48%, celulares 32%, etc.), franja horaria crítica (20:00 - 02:00) y cuadrante CAI Lourdes. |
| **04** | `04_buscar_barrio.jpg` | **Buscar barrio** | HU-07 | barrio, UPZ, localidad | **"No encontrado" o lista de opciones si es ambiguo** (banner de advertencia y sugerencias directas: Chapinero Central UPZ 89, Pardo Rubio, Chicó Lago). |
| **05** | `05_calificar_cuadra.jpg` | **Calificar cuadra** | HU-03 | calificación de cuadra, horario crítico | **Texto filtrado si trae groserías** (comentario con palabra censurada con `***` y badge del filtro de convivencia comunitaria). |
| **06** | `06_agente_virtual_ia.jpg` | **Agente** | HU-02 | conversación, mensaje | **Sin backend responde el respaldo local** (banner de backend desconectado y respuesta generada por modelo local Llama 3.2). |
| **07** | `07_comunidad_red_vecinal.jpg` | **Comunidad** | HU-06 | mensaje comunitario, alias | **Sin Firestore lee la copia del backend** (alerta superior y feed con alias anónimos `Vecino_Chapi42`, confirmaciones ciudadanas). |
| **08** | `08_emergencias_sos.jpg` | **Emergencias** | HU-01 | líneas de emergencia, kit | Botón central de pánico SOS con efecto de pulso, directorio rápido Línea 123, Bomberos 119, Cruz Roja 132 y Kit de Emergencia. |
| **09** | `09_ajustes_monitoreo.jpg` | **Ajustes** | HU-02b | monitoreo, intervalo | **Botón inactivo si falta el permiso** (alerta de permiso 'Todo el tiempo' no concedido y botón de monitoreo en gris inactivo). |
| **10** | `10_app_control_escritorio.jpg` | **App de Control** | HU-04, HU-05 | telemetría, moderación | **Panel en rojo si Ollama o el backend no responden** (aplicación de escritorio PyQt6 con alerta roja en Ollama, tabla de moderación y métricas). |

---

## 🎨 Especificaciones Visuales del Diseño

- **Guía de Estilo:** Google Material Design 3 (Material You) para las pantallas móviles de Android y Dark Cybersecurity Theme para la App de Escritorio.
- **Paleta de Colores de Riesgo (Norma WCAG Contrast):**
  - 🟢 **Bajo Riesgo:** `#0CA30C` (Verde esmeralda de seguridad)
  - 🟡 **Riesgo Medio:** `#FAB219` (Ámbar de precaución)
  - 🔴 **Riesgo Alto:** `#D03B3B` / `#D32F2F` (Rojo de alerta / SOS)
  - 🔵 **Identidad Primaria:** `#2A78D6` (Azul institucional de protección ciudadana)
- **Dispositivo Mockup:** Smartphone Android insignia moderno con bordes ultrafinos y laptop moderna para el panel de control.

---

## 📝 Instrucciones de Inserción en Documentos Word

Para incorporar estas imágenes en los documentos existentes (`Manual de Usuario.docx`, `Manual Tecnico.docx` o `Documento_Arquitectura_Software.docx`):
1. Abre el documento en Microsoft Word.
2. Ve a la pestaña **Insertar** > **Imágenes** > **Este dispositivo...**
3. Selecciona la carpeta `documentacion/mockups/` y el archivo deseado.
4. Se recomienda ajustar el tamaño entre **8 cm y 12 cm de alto** para visualización vertical de un solo teléfono, o **ancho completo (14-16 cm)** para la aplicación de control.

