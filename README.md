# 🛡️ BARRIO SEGURO APP

Una aplicación integral diseñada para la gestión de riesgos sociales, predicciones y seguridad ciudadana, enfocada en la localidad de Chapinero (Bogotá).

## 🚀 Estructura del Proyecto

Este repositorio se divide en tres componentes principales:

1. **`app/` (Aplicación Android):** 
   - Interfaz móvil nativa (Kotlin).
   - Conexión con la API para mostrar zonas de riesgo y datos relevantes a los usuarios.

2. **`Api/` (Backend / Servicios):** 
   - API construida para servir los datos a la aplicación móvil.
   - Script principal: `backend_riesgo.py`

3. **`Agente/` (Procesamiento de Datos e IA):** 
   - Scripts de Python (`agente.py`, `ProcesarRiesgo.py`, `actualizar_datos.py`) encargados de analizar datos geoespaciales y generar proyecciones.
   - Manejo de información geográfica (`.geojson`, `.gpkg`) y demográfica de Bogotá.

## 🛠️ Tecnologías Utilizadas

- **Frontend Móvil:** Android (Kotlin).
- **Backend / API:** Python (FastAPI / Flask - dependiendo del framework en `backend_riesgo.py`).
- **Data Science & GIS:** Python, GeoJSON, GeoPackage.

## ⚙️ Configuración y Uso

*(Añadir aquí las instrucciones específicas para levantar la API localmente y compilar la app de Android).*
