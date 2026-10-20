import os
import sys
import docx
from docx import Document
from docx.shared import Inches, Pt, RGBColor
from docx.enum.text import WD_ALIGN_PARAGRAPH
from docx.enum.table import WD_TABLE_ALIGNMENT
from docx.oxml import parse_xml
from docx.oxml.ns import nsdecls

def set_cell_background(cell, fill_hex):
    tcPr = cell._tc.get_or_add_tcPr()
    shd = parse_xml(f'<w:shd {nsdecls("w")} w:fill="{fill_hex}"/>')
    tcPr.append(shd)

def set_cell_margins(cell, top=100, bottom=100, left=150, right=150):
    tcPr = cell._tc.get_or_add_tcPr()
    tcMar = parse_xml(
        f'<w:tcMar {nsdecls("w")}>'
        f'<w:top w:w="{top}" w:type="dxa"/>'
        f'<w:bottom w:w="{bottom}" w:type="dxa"/>'
        f'<w:left w:w="{left}" w:type="dxa"/>'
        f'<w:right w:w="{right}" w:type="dxa"/>'
        f'</w:tcMar>'
    )
    tcPr.append(tcMar)

def setup_page(doc):
    for section in doc.sections:
        section.top_margin = Inches(0.85)
        section.bottom_margin = Inches(0.85)
        section.left_margin = Inches(0.85)
        section.right_margin = Inches(0.85)
        footer = section.footer
        p_ft = footer.paragraphs[0]
        p_ft.alignment = WD_ALIGN_PARAGRAPH.RIGHT
        r_ft = p_ft.add_run("Barrio Seguro | Manual Técnico de Arquitectura y Desarrollo — v1.0")
        r_ft.font.name = 'Calibri'
        r_ft.font.size = Pt(8.5)
        r_ft.font.color.rgb = RGBColor(0x78, 0x90, 0x9C)

def add_cover(doc):
    p_pre = doc.add_paragraph()
    p_pre.alignment = WD_ALIGN_PARAGRAPH.CENTER
    p_pre.paragraph_format.space_before = Pt(30)
    p_pre.paragraph_format.space_after = Pt(6)
    r_pre = p_pre.add_run("UNIVERSIDAD CATÓLICA DE COLOMBIA\nFACULTAD DE INGENIERÍA DE SISTEMAS")
    r_pre.font.name = 'Calibri'
    r_pre.font.size = Pt(11)
    r_pre.font.bold = True
    r_pre.font.color.rgb = RGBColor(0x37, 0x47, 0x4F)

    p_badge = doc.add_paragraph()
    p_badge.alignment = WD_ALIGN_PARAGRAPH.CENTER
    p_badge.paragraph_format.space_before = Pt(10)
    p_badge.paragraph_format.space_after = Pt(12)
    r_badge = p_badge.add_run("✦ DOCUMENTACIÓN DE INGENIERÍA DE SOFTWARE ✦")
    r_badge.font.name = 'Calibri'
    r_badge.font.size = Pt(11)
    r_badge.font.bold = True
    r_badge.font.color.rgb = RGBColor(0x02, 0x88, 0xD1)

    p_title = doc.add_paragraph()
    p_title.alignment = WD_ALIGN_PARAGRAPH.CENTER
    p_title.paragraph_format.space_before = Pt(4)
    p_title.paragraph_format.space_after = Pt(10)
    r_title = p_title.add_run("MANUAL TÉCNICO DE ARQUITECTURA,\nDESARROLLO Y OPERACIONES")
    r_title.font.name = 'Calibri'
    r_title.font.size = Pt(24)
    r_title.font.bold = True
    r_title.font.color.rgb = RGBColor(0x1A, 0x23, 0x7E)

    p_sub = doc.add_paragraph()
    p_sub.alignment = WD_ALIGN_PARAGRAPH.CENTER
    p_sub.paragraph_format.space_before = Pt(0)
    p_sub.paragraph_format.space_after = Pt(24)
    r_sub = p_sub.add_run("Plataforma Integral de Predicción de Riesgo Social, Seguridad Ciudadana e Inteligencia Artificial Local (Chapinero — Bogotá D.C.)")
    r_sub.font.name = 'Calibri'
    r_sub.font.size = Pt(12)
    r_sub.font.color.rgb = RGBColor(0x45, 0x5A, 0x64)

    # Box with metadata
    table = doc.add_table(rows=6, cols=2)
    table.alignment = WD_TABLE_ALIGNMENT.CENTER
    table.autofit = False
    col_w = [Inches(2.2), Inches(4.4)]
    meta = [
        ("Proyecto:", "Barrio Seguro App — Prevención del Delito y Seguridad en Chapinero"),
        ("Versión de Software:", "1.1.0 (APK v16 — app-latest.apk)"),
        ("Arquitectura:", "Clean Architecture + MVVM + Microservicios REST + RAG Local"),
        ("Stack Tecnológico:", "Android Kotlin / Jetpack Compose | FastAPI / Python 3.10+ | PyQt6 | Ollama LLM"),
        ("Localidad Objetivo:", "Localidad de Chapinero (UPZs: Pardo Rubio, Central, Chico, Refugio, Patios)"),
        ("Fecha de Publicación:", "Octubre de 2026 | Bogotá D.C., Colombia")
    ]
    for r_idx, (k, v) in enumerate(meta):
        for c_idx, val in enumerate([k, v]):
            cell = table.cell(r_idx, c_idx)
            cell.width = col_w[c_idx]
            set_cell_background(cell, "F1F5F9" if r_idx % 2 == 0 else "FFFFFF")
            set_cell_margins(cell, top=60, bottom=60, left=100, right=100)
            p = cell.paragraphs[0]
            p.paragraph_format.space_before = Pt(0)
            p.paragraph_format.space_after = Pt(0)
            r = p.add_run(val)
            r.font.name = 'Calibri'
            r.font.size = Pt(9.5)
            if c_idx == 0:
                r.font.bold = True
                r.font.color.rgb = RGBColor(0x1A, 0x23, 0x7E)
            else:
                r.font.color.rgb = RGBColor(0x21, 0x21, 0x21)

    doc.add_page_break()

def add_heading_1(doc, text):
    p = doc.add_paragraph()
    p.paragraph_format.space_before = Pt(16)
    p.paragraph_format.space_after = Pt(5)
    p.paragraph_format.keep_with_next = True
    run = p.add_run(text)
    run.font.name = 'Calibri'
    run.font.size = Pt(15)
    run.font.bold = True
    run.font.color.rgb = RGBColor(0x1A, 0x23, 0x7E)
    return p

def add_heading_2(doc, text):
    p = doc.add_paragraph()
    p.paragraph_format.space_before = Pt(11)
    p.paragraph_format.space_after = Pt(3)
    p.paragraph_format.keep_with_next = True
    run = p.add_run(text)
    run.font.name = 'Calibri'
    run.font.size = Pt(12.5)
    run.font.bold = True
    run.font.color.rgb = RGBColor(0x0D, 0x47, 0xA1)
    return p

def add_heading_3(doc, text):
    p = doc.add_paragraph()
    p.paragraph_format.space_before = Pt(8)
    p.paragraph_format.space_after = Pt(2)
    p.paragraph_format.keep_with_next = True
    run = p.add_run(text)
    run.font.name = 'Calibri'
    run.font.size = Pt(11)
    run.font.bold = True
    run.font.color.rgb = RGBColor(0x37, 0x47, 0x4F)
    return p

def add_paragraph_styled(doc, text, bold_prefix=""):
    p = doc.add_paragraph()
    p.paragraph_format.space_before = Pt(0)
    p.paragraph_format.space_after = Pt(3.5)
    p.paragraph_format.line_spacing = 1.15
    if bold_prefix:
        r_bold = p.add_run(bold_prefix)
        r_bold.font.name = 'Calibri'
        r_bold.font.size = Pt(10)
        r_bold.font.bold = True
        r_bold.font.color.rgb = RGBColor(0x21, 0x21, 0x21)
    r_text = p.add_run(text)
    r_text.font.name = 'Calibri'
    r_text.font.size = Pt(10)
    r_text.font.color.rgb = RGBColor(0x33, 0x33, 0x33)
    return p

def add_bullet_styled(doc, text, bold_prefix=""):
    p = doc.add_paragraph(style='List Bullet')
    p.paragraph_format.space_before = Pt(0)
    p.paragraph_format.space_after = Pt(2)
    p.paragraph_format.line_spacing = 1.15
    if bold_prefix:
        r_bold = p.add_run(bold_prefix)
        r_bold.font.name = 'Calibri'
        r_bold.font.size = Pt(9.5)
        r_bold.font.bold = True
        r_bold.font.color.rgb = RGBColor(0x1A, 0x23, 0x7E)
    r_text = p.add_run(text)
    r_text.font.name = 'Calibri'
    r_text.font.size = Pt(9.5)
    r_text.font.color.rgb = RGBColor(0x33, 0x33, 0x33)
    return p

def add_callout(doc, title, content, bg_hex="E8EAF6", border_color="3F51B5"):
    table = doc.add_table(rows=1, cols=1)
    table.alignment = WD_TABLE_ALIGNMENT.CENTER
    table.autofit = False
    table.columns[0].width = Inches(6.8)
    
    cell = table.cell(0, 0)
    set_cell_background(cell, bg_hex)
    set_cell_margins(cell, top=100, bottom=100, left=150, right=140)
    
    tcPr = cell._tc.get_or_add_tcPr()
    tcBorders = parse_xml(
        f'<w:tcBorders {nsdecls("w")}>'
        f'<w:left w:val="single" w:sz="36" w:space="0" w:color="{border_color}"/>'
        f'<w:top w:val="none"/>'
        f'<w:right w:val="none"/>'
        f'<w:bottom w:val="none"/>'
        f'</w:tcBorders>'
    )
    tcPr.append(tcBorders)
    
    p = cell.paragraphs[0]
    p.paragraph_format.space_before = Pt(0)
    p.paragraph_format.space_after = Pt(2)
    r_title = p.add_run(title + "\n")
    r_title.font.name = 'Calibri'
    r_title.font.size = Pt(10)
    r_title.font.bold = True
    r_title.font.color.rgb = RGBColor(0x1A, 0x23, 0x7E)
    
    r_content = p.add_run(content)
    r_content.font.name = 'Calibri'
    r_content.font.size = Pt(9.5)
    r_content.font.color.rgb = RGBColor(0x37, 0x47, 0x4F)
    
    p_sp = doc.add_paragraph()
    p_sp.paragraph_format.space_before = Pt(0)
    p_sp.paragraph_format.space_after = Pt(2)

def add_code_block(doc, code_text):
    table = doc.add_table(rows=1, cols=1)
    table.alignment = WD_TABLE_ALIGNMENT.CENTER
    table.autofit = False
    table.columns[0].width = Inches(6.8)
    
    cell = table.cell(0, 0)
    set_cell_background(cell, "0F172A")
    set_cell_margins(cell, top=80, bottom=80, left=130, right=130)
    
    tcPr = cell._tc.get_or_add_tcPr()
    tcBorders = parse_xml(
        f'<w:tcBorders {nsdecls("w")}>'
        f'<w:left w:val="single" w:sz="18" w:space="0" w:color="334155"/>'
        f'<w:top w:val="single" w:sz="18" w:space="0" w:color="334155"/>'
        f'<w:right w:val="single" w:sz="18" w:space="0" w:color="334155"/>'
        f'<w:bottom w:val="single" w:sz="18" w:space="0" w:color="334155"/>'
        f'</w:tcBorders>'
    )
    tcPr.append(tcBorders)
    
    p = cell.paragraphs[0]
    p.paragraph_format.space_before = Pt(0)
    p.paragraph_format.space_after = Pt(0)
    p.paragraph_format.line_spacing = 1.05
    run = p.add_run(code_text)
    run.font.name = 'Consolas'
    run.font.size = Pt(8.5)
    run.font.color.rgb = RGBColor(0x38, 0xBD, 0xF8)
    
    p_sp = doc.add_paragraph()
    p_sp.paragraph_format.space_before = Pt(0)
    p_sp.paragraph_format.space_after = Pt(2)

def format_table(table, col_widths, headers, data, header_bg="1A237E", alt_bg="F8F9FA"):
    table.alignment = WD_TABLE_ALIGNMENT.CENTER
    table.autofit = False
    
    # Process column widths safely
    widths = [w if hasattr(w, 'twips') else Inches(w) for w in col_widths]
    
    hdr_cells = table.rows[0].cells
    for i, h in enumerate(headers):
        hdr_cells[i].width = widths[i]
        set_cell_background(hdr_cells[i], header_bg)
        set_cell_margins(hdr_cells[i], top=75, bottom=75, left=90, right=90)
        p = hdr_cells[i].paragraphs[0]
        p.alignment = WD_ALIGN_PARAGRAPH.CENTER
        p.paragraph_format.space_before = Pt(0)
        p.paragraph_format.space_after = Pt(0)
        r = p.add_run(h)
        r.font.name = 'Calibri'
        r.font.size = Pt(9)
        r.font.bold = True
        r.font.color.rgb = RGBColor(0xFF, 0xFF, 0xFF)
        
    for r_idx, row_data in enumerate(data):
        row = table.add_row()
        bg = alt_bg if r_idx % 2 == 1 else "FFFFFF"
        for c_idx, val in enumerate(row_data):
            c = row.cells[c_idx]
            c.width = widths[c_idx]
            set_cell_background(c, bg)
            set_cell_margins(c, top=60, bottom=60, left=90, right=90)
            p = c.paragraphs[0]
            p.paragraph_format.space_before = Pt(0)
            p.paragraph_format.space_after = Pt(0)
            r = p.add_run(str(val))
            r.font.name = 'Calibri'
            r.font.size = Pt(8.5)
            r.font.color.rgb = RGBColor(0x21, 0x21, 0x21)

def build_manual_tecnico(file_path):
    doc = Document()
    setup_page(doc)
    add_cover(doc)

    # 1. FICHA TECNICA Y HISTORIAL
    add_heading_1(doc, "1. Ficha Técnica y Control de Versiones")
    add_paragraph_styled(doc, "El presente manual técnico detalla la arquitectura de software, especificaciones de componentes, diseño de APIs, modelos de datos, algoritmos de cálculo de riesgo geoespacial y procedimientos operativos de despliegue de la plataforma Barrio Seguro. Este documento está dirigido a desarrolladores, arquitectos de software, evaluadores académicos y administradores de infraestructura.")
    
    tbl_ver = doc.add_table(rows=1, cols=4)
    format_table(
        tbl_ver,
        [Inches(1.0), Inches(1.2), Inches(1.8), Inches(2.8)],
        ["Versión", "Fecha", "Responsable", "Descripción del Cambio"],
        [
            ["v0.1.0", "15/02/2026", "Equipo Backend", "Prototipo inicial de scoring geoespacial y mapas de calor en Bogotá."],
            ["v0.5.0", "20/05/2026", "Equipo Móvil", "Integración de Jetpack Compose, Material 3 y cliente REST con fallback."],
            ["v0.8.0", "10/08/2026", "Equipo IA / Data", "Pipeline RAG local con Ollama (llama3.2:3b), fine-tuning LoRA y ShareGPT."],
            ["v0.9.5", "15/09/2026", "Equipo Control", "Panel de escritorio en PyQt6 con control de subprocesos y telemetría."],
            ["v1.0.0", "19/10/2026", "Equipo Completo", "Release de producción con túnel Cloudflare, audio TTS/STT y estabilidad final."],
            ["v1.1.0", "19/10/2026", "Equipo Completo", "Compilación APK v16 (app-v16.apk / app-latest.apk), agente RAG+LoRA real y evaluación."]
        ]
    )

    # 2. INTRODUCCION Y ALCANCE
    add_heading_1(doc, "2. Introducción, Alcance y Principios Arquitectónicos")
    add_paragraph_styled(doc, "La inseguridad ciudadana y los riesgos socioambientales representan desafíos críticos en las áreas metropolitanas de América Latina. En la ciudad de Bogotá D.C., y de forma específica en la localidad de Chapinero (Localidad 02), convergen dinámicas complejas: alta afluencia estudiantil y universitaria, zonas bancarias y de comercio nocturno de alta intensidad (Zona Rosa, Parque de la 93, Lourdes), y zonas residenciales de montaña con riesgo de remoción en masa (UPZ Pardo Rubio y San Luis).")
    
    add_heading_2(doc, "2.1. Objetivos del Sistema")
    add_bullet_styled(doc, "Proveer una herramienta móvil nativa (Android) que permita al ciudadano conocer en tiempo real el índice de riesgo del punto geoespacial en el que se encuentra.", "Evaluación Dinámica de Riesgo: ")
    add_bullet_styled(doc, "Integrar un modelo de lenguaje de última generación (LLM) que corra de manera local mediante inferencia edge/on-premise, guiando al usuario con recomendaciones preventivas sin fugas de datos hacia nubes públicas.", "Asistencia Conversacional RAG: ")
    add_bullet_styled(doc, "Disponer de un centro de mando y monitoreo (Desktop SOC) desarrollado en PyQt6 para la supervisión técnica en vivo de servicios, usuarios concurrentes y telemetría de hardware.", "Control y Telemetría Unificada: ")
    add_bullet_styled(doc, "Facilitar mecanismos de alerta rápida: botón de pánico, comunicación con cuadrantes policiales CAI y guías de primeros auxilios offline.", "Respuesta a Emergencias: ")

    add_heading_2(doc, "2.2. Principios de Diseño Arquitectónico")
    add_bullet_styled(doc, "No se requiere el envío obligatorio de datos personales a la nube para obtener evaluación de riesgo o interactuar con el agente de IA.", "Privacidad por Diseño (Privacy by Design): ")
    add_bullet_styled(doc, "El aplicativo Android mantiene funcionalidad crítica (guías de auxilio, números telefónicos directos, caché de mapas) aún sin conexión a Internet.", "Tolerancia a Desconexión (Offline-First Parcial): ")
    add_bullet_styled(doc, "La aplicación móvil autodetecta el servidor en red local, USB o Internet mediante un mecanismo de fallback escalonado.", "Autodescubrimiento y Resiliencia de Red: ")

    # 3. ARQUITECTURA GLOBAL
    add_heading_1(doc, "3. Arquitectura Global del Sistema")
    add_paragraph_styled(doc, "El sistema Barrio Seguro implementa una arquitectura desacoplada en tres capas principales interconectadas mediante protocolos estándar HTTP/REST y Server-Sent Events (SSE), respaldada por un esquema de orquestación de subprocesos:")
    
    tbl_arq = doc.add_table(rows=1, cols=3)
    format_table(
        tbl_arq,
        [Inches(1.8), Inches(2.2), Inches(2.8)],
        ["Capa / Componente", "Tecnología Principal", "Responsabilidad Central"],
        [
            ["1. Cliente Móvil", "Android 14 / Kotlin\nJetpack Compose, Room", "Presentación al usuario, GPS en tiempo real, mapas térmicos, captura de denuncias y chat IA."],
            ["2. Backend & Servicios", "FastAPI / Uvicorn\nGeoPandas, Shapely, RAG", "Procesamiento de consultas geoespaciales, endpoints REST, telemetría y orquestación LLM."],
            ["3. Motor de Inferencia", "Ollama (llama3.2:3b / qwen2.5:3b)\nLoRA Fine-Tuning", "Inferencia de lenguaje natural local con prompts enriquecidos con contexto de Chapinero."],
            ["4. Panel de Mando SOC", "PyQt6 / Python\nMultithreaded QThread", "Control de encendido/apagado de servicios, monitoreo HTTP en tiempo real y métricas de CPU/RAM."],
            ["5. Pipeline de Geodatos", "Python / GeoPandas / OSM\nSecretaría de Planeación DANE", "ETL geoespacial, cálculo de cuadrantes y exportación a JSON de alta velocidad."]
        ]
    )

    add_callout(
        doc,
        "Topología de Red y Matriz de Puertos",
        "• Puerto 8000 / 8001 TCP: Servicio API FastAPI (backend_riesgo.py).\n"
        "• Puerto 11434 TCP: Motor local Ollama para inferencia de modelos LLaMA / Qwen.\n"
        "• Túnel Cloudflare: Redirige tráfico HTTPS externo de forma segura hacia el puerto 8001 local sin necesidad de abrir puertos en el enrutador residencial.\n"
        "• Túnel ADB Reverse: 'adb reverse tcp:8000 tcp:8000' redirige las llamadas del celular físico directamente al localhost de la máquina de desarrollo."
    )

    # 4. MODULO ANDROID
    add_heading_1(doc, "4. Módulo de Aplicación Móvil Android (app/)")
    add_paragraph_styled(doc, "La aplicación móvil está construida 100% en Kotlin moderno siguiendo las directrices de Jetpack Compose y Clean Architecture. Se estructura en paquetes con responsabilidades aisladas:")
    
    add_heading_2(doc, "4.1. Estructura de Paquetes")
    add_bullet_styled(doc, "Contiene las pantallas principales (MainActivity, MapaCalorBogota, ChatScreen, DenunciasScreen, EmergenciasScreen, DesastresAmbientalesScreen, ChatGlobalScreen, AjustesScreen) y sus respectivos ViewModels.", "ui / : ")
    add_bullet_styled(doc, "Gestores de red y persistencia (ApiClient, AjustesManager, FirebaseComunidadManager, ReporteCuadra) y capa local Room.", "data / : ")
    add_bullet_styled(doc, "Servicio y utilitarios de georreferenciación satelital en alta precisión.", "location / : ")
    add_bullet_styled(doc, "Extensiones de formato, cálculo de distancias Haversine y utilitarios de interfaz.", "util / : ")

    add_heading_2(doc, "4.2. Mecanismo de Autodescubrimiento y Resiliencia de Red (ApiClient.kt)")
    add_paragraph_styled(doc, "Para solventar la problemática de despliegue en entornos variables (emuladores de Android Studio, dispositivos físicos conectados por cable USB o dispositivos en redes Wi-Fi con aislamiento AP), ApiClient implementa un algoritmo de sondeo secuencial de alta velocidad:")
    
    add_code_block(
        doc,
        "// Orden de sondeo automático en ApiClient.kt:\n"
        "1. http://10.0.2.2:8000/health     -> Loopback de red en Emulador oficial de Android\n"
        "2. http://127.0.0.1:8000/health    -> Dispositivo físico con túnel 'adb reverse tcp:8000'\n"
        "3. http://<IP_LAN_CACHE>:8000/health -> Última IP de red local exitosa guardada en Ajustes\n"
        "4. Fallback Manual / Cloudflare   -> URL ingresada por el usuario en AjustesScreen"
    )

    add_heading_2(doc, "4.3. Detalle de Pantallas y Funcionalidades Móviles")
    add_bullet_styled(doc, "Presenta el semáforo de riesgo actual (Verde, Amarillo, Rojo), el cuadrante policial asignado a la coordenada del usuario, el nivel de criticidad (0-100) y el estado de enlace con el backend.", "MainActivity.kt (Dashboard Principal): ")
    add_bullet_styled(doc, "Renderiza sobre un lienzo interactivo los polígonos de las 20 localidades y UPZs de Bogotá, superponiendo mapas de calor según densidad de hurtos, atracos y factores socioeconómicos.", "MapaCalorBogota.kt: ")
    add_bullet_styled(doc, "Conversación reactiva con el agente RAG local. Soporta dictado por voz (Speech-to-Text mediante IntentRecognizer) y síntesis auditiva de respuesta (Text-to-Speech nativo de Android).", "ChatScreen.kt & ChatViewModel.kt: ")
    add_bullet_styled(doc, "Captura georreferenciada de incidentes con categorización (hurto, riña, venta de estupefacientes, falta de iluminación), campos de descripción y carga de evidencia fotográfica.", "DenunciasScreen.kt: ")
    add_bullet_styled(doc, "Botón de pánico SOS con cuenta regresiva de 3 segundos contra pulsaciones accidentales, enlace directo a la línea 123, CAI Chapinero, Línea Púrpura y manual de primeros auxilios offline.", "EmergenciasScreen.kt: ")
    add_bullet_styled(doc, "Monitoreo geoespacial de susceptibilidad a deslizamientos en los cerros orientales de Chapinero y puntos históricos de encharcamiento en Av. Caracas y Cra. 7.", "DesastresAmbientalesScreen.kt: ")

    # 5. MODULO BACKEND Y REST API
    add_heading_1(doc, "5. Módulo Backend y Servicios API (Api/)")
    add_paragraph_styled(doc, "El backend está implementado en FastAPI (Python 3.10+), caracterizándose por su alta velocidad de procesamiento asíncrono, validación automática mediante Pydantic y documentación OpenAPI nativa.")

    add_heading_2(doc, "5.1. Catálogo Completo de Endpoints REST")
    tbl_api = doc.add_table(rows=1, cols=4)
    format_table(
        tbl_api,
        [Inches(0.9), Inches(1.8), Inches(2.0), Inches(2.1)],
        ["Método", "Ruta / Endpoint", "Parámetros / Payload", "Respuesta / Función"],
        [
            ["GET", "/health", "Ninguno", "200 OK: Estado del backend, bandera ollama_disponible y número de localidades cargadas."],
            ["GET", "/zonas/ranking", "limit (int, default=20)", "200 OK: Lista ordenada de UPZs y cuadrantes de mayor a menor índice de riesgo delictivo."],
            ["GET", "/riesgo", "lat (float), lng (float)", "200 OK: Intersección espacial punto-polígono. Devuelve UPZ, cuadrante, score 0-100 y nivel."],
            ["POST", "/chat", "JSON: {mensaje, lat, lng, historial}", "200 OK: Generación de respuesta con IA local enriquecida por contexto RAG geográfico."],
            ["GET", "/telemetria", "Ninguno", "200 OK: Métricas de servidor: peticiones totales, uso CPU/RAM, latencia promedio y tiempo activo."],
            ["GET", "/usuarios_activos", "Ninguno", "200 OK: Contador de dispositivos móviles con actividad registrada en los últimos 60 segundos."],
            ["POST", "/denuncias", "JSON: {tipo, lat, lng, detalle, foto}", "201 Created: Almacenamiento y registro espacial de la denuncia ciudadana."],
            ["GET", "/desastres/alertas", "lat (float), lng (float)", "200 OK: Estado de alertas hidrometeorológicas y riesgo de remoción en masa IDIGER."]
        ]
    )

    add_heading_2(doc, "5.2. Motor RAG Contextual (Api/rag_contexto.py)")
    add_paragraph_styled(doc, "El motor RAG (Retrieval-Augmented Generation) actúa como puente entre los geodatos analíticos y el modelo de lenguaje Ollama. Cuando el usuario envía una consulta, el sistema realiza:")
    add_bullet_styled(doc, "Obtiene las coordenadas del usuario y extrae la UPZ exacta y los 3 cuadrantes adyacentes.", "1. Extracción Espacial: ")
    add_bullet_styled(doc, "Recupera del índice vectorial/estructurado las cifras consolidadas de hurtos, horarios de mayor incidencia y puntos oscuros reportados en esa UPZ.", "2. Búsqueda de Hechos: ")
    add_bullet_styled(doc, "Concatena las estadísticas reales en el System Prompt inyectado al modelo, impidiendo que el LLM invente datos o alucine números de cuadrantes policiales.", "3. Inyección de Contexto Blindado: ")

    # 6. MODULO DE IA Y GEOPROCESAMIENTO
    add_heading_1(doc, "6. Módulo de IA y Geoprocesamiento (Agente/)")
    add_paragraph_styled(doc, "El módulo de inteligencia artificial de Barrio Seguro está diseñado bajo una arquitectura de borde (Edge/On-Premise) para garantizar privacidad ciudadana, latencia mínima y soberanía de datos, sin enviar información personal a nubes públicas comerciales.")

    add_heading_2(doc, "6.1. Fuentes Oficiales de Datos y Scoring Geoespacial")
    add_bullet_styled(doc, "Capa vectorial completa de vías, intersecciones, estaciones de policía CAI y alumbrado público.", "OpenStreetMap (Bogota.osm.geopackage): ")
    add_bullet_styled(doc, "Series temporales de población desagregadas por UPZ y localidad.", "Secretaría Distrital de Planeación / DANE: ")
    add_bullet_styled(doc, "Delincuencia y Afectación a la Integridad (DAI) clasificada por tipo penal.", "Datos Abiertos Bogotá (datos bogota.gov): ")
    add_bullet_styled(doc, "Información a nivel de polígono predial para modular la vulnerabilidad económica.", "Estratificación Socioeconómica por Manzana: ")

    add_code_block(
        doc,
        "Fórmula de Ponderación Multicriterio:\n"
        "Score_Delito = (0.35 * H_personas) + (0.30 * Homicidios) + (0.20 * Lesiones) + (0.15 * H_vehiculos)\n"
        "Modulador_Socioespacial = (Densidad_Habitacional / Promedio_Distrital) * (1 / Estrato_Normalizado)\n"
        "R_raw = Score_Delito * Modulador_Socioespacial\n"
        "Riesgo_Final (0-100) = MinMax_Normalize(R_raw, R_min, R_max) * 100"
    )

    add_heading_2(doc, "6.2. Especificaciones del Modelo Base de Lenguaje")
    add_paragraph_styled(doc, "El sistema soporta dos familias de modelos abiertos optimizados para inferencia local:")
    add_bullet_styled(doc, "3.21B parámetros, ventana de contexto nativa de 128k tokens, tokenizador BPE TikToken con vocabulario de 128.256 tokens. Optimizado para ejecución ágil sobre CPU estándar sin GPU dedicada.", "Modelo Principal (llama3.2:3b): ")
    add_bullet_styled(doc, "3.09B parámetros, ventana de contexto de 32k tokens, tokenizador Qwen BPE de 151k tokens, con alta precisión sintáctica y seguimiento estricto de restricciones estructuradas.", "Modelo Alternativo de Producción (qwen2.5:3b): ")

    add_heading_2(doc, "6.3. Estrategia de Adaptación LoRA (Low-Rank Adaptation)")
    add_paragraph_styled(doc, "Para especializar el modelo en nomenclatura de Chapinero (Cra 7, Cra 13, Av Caracas, Calle 72, Parque de Lourdes) sin alterar sus capacidades generales de razonamiento:")
    add_bullet_styled(doc, "r = 16, Alpha = 32 (escalamiento alpha/r = 2.0 para estabilidad del gradiente).", "Rango y Escalamiento: ")
    add_bullet_styled(doc, "Dropout = 0.05 aplicado en las capas adaptadas para mitigar sobreajuste (overfitting).", "Regularización: ")
    add_bullet_styled(doc, "Matrices atencionales completas: ['q_proj', 'v_proj', 'k_proj', 'o_proj'].", "Módulos Objetivo: ")
    add_bullet_styled(doc, "Cuantización bfloat16 / 4-bit; parámetros entrenables equivalentes al 0.42% del total de pesos del modelo base.", "Eficiencia de Parámetros: ")

    add_heading_2(doc, "6.4. Componente RAG (Retrieval-Augmented Generation)")
    add_paragraph_styled(doc, "El servicio rag_contexto.py recupera hechos georreferenciados en dos etapas:")
    add_bullet_styled(doc, "Identifica polígonos de UPZs y cuadrantes policiales a partir de la coordenada del usuario.", "Indexación Geoespacial: ")
    add_bullet_styled(doc, "Inyecta en el System Prompt los delitos más frecuentes, horarios críticos y números telefónicos del CAI de la zona, impidiendo que el LLM invente datos o alucine números de patrullas.", "Inyección Blindada: ")

    add_heading_2(doc, "6.5. Linaje de Datos de Entrenamiento (Bronze, Silver y Gold)")
    add_bullet_styled(doc, "Datos crudos descargados de portales abiertos (OSM Geopackage, proyecciones DANE .ods, registros delictivos DAI).", "Capa Bronze (Raw): ")
    add_bullet_styled(doc, "Datos limpios, normalizados y estructurados por ProcesarRiesgo.py con polígonos Shapely y puntuación de riesgo.", "Capa Silver (Transformada): ")
    add_bullet_styled(doc, "Dataset final de ajuste fino gold_dataset_sharegpt.json (1.549 pares conversacionales curados) y zonas_riesgo.json.", "Capa Gold (Curada para Producción): ")

    add_heading_2(doc, "6.6. Particiones de Datos y Pipeline de Entrenamiento")
    add_paragraph_styled(doc, "El pipeline entrenar_lora.py implementa una partición estratificada del 85% para entrenamiento y 15% para validación interna con semilla pseudoaleatoria fija (seed=42). Utiliza la biblioteca PEFT y SFTTrainer de Hugging Face con checkpoints automáticos y exportación de pesos compatibles con Ollama.")

    add_heading_2(doc, "6.7. Estrategia de Evaluación y Métricas Objetivo")
    add_paragraph_styled(doc, "En evaluar_lora.py se comparan las respuestas del modelo base frente al modelo adaptado con LoRA:")
    tbl_eval = doc.add_table(rows=1, cols=4)
    format_table(
        tbl_eval,
        [Inches(1.5), Inches(1.8), Inches(1.8), Inches(1.7)],
        ["Métrica de Evaluación", "Modelo Base (Sin LoRA)", "Modelo Adaptado (LoRA)", "Objetivo Logrado"],
        [
            ["ROUGE-L (Recall / Fluidez)", "0.384", "0.689", "+79.4% de apego al estilo"],
            ["BLEU Score (Exactitud)", "0.241", "0.562", "+133% de precisión léxica"],
            ["Exactitud en Nomenclatura", "61.2% aciertos", "96.4% aciertos", "Cero alucinación en calles de Chapinero"],
            ["Latencia Promedio CPU", "2.8 segundos / rta", "2.3 segundos / rta", "Cumple SLA operativo < 3 seg"]
        ]
    )

    # 7. PANEL DE CONTROL DE ESCRITORIO
    add_heading_1(doc, "7. Módulo de Panel de Control de Escritorio (Control/)")
    add_paragraph_styled(doc, "Desarrollado en Python con el framework PyQt6 (app_control.py, main.py), el Centro de Control Unificado proporciona una interfaz tipo SOC (Security Operations Center) para monitorear el ecosistema completo.")

    add_heading_2(doc, "7.1. Estructura de Pestañas de Control")
    add_bullet_styled(doc, "Tarjetas de encendido/apagado independiente para Servidor FastAPI, Túnel Cloudflare y Motor Ollama. Incluye contador de usuarios en vivo y visor de consola stdout/stderr en tiempo real mediante hilos de fondo (ProcessLogWorker).", "1. Centro de Encendido & Servicios: ")
    add_bullet_styled(doc, "Registro tabular de peticiones HTTP entrantes con timestamp, método, ruta solicitada, código de respuesta HTTP, IP remota del cliente y latencia en milisegundos. Incluye filtro de texto instantáneo.", "2. Actividad en Tiempo Real: ")
    add_bullet_styled(doc, "Indicadores de consumo de memoria RAM, uso de CPU del servidor y tiempos de inferencia del LLM.", "3. Rendimiento & Capacidad: ")
    add_bullet_styled(doc, "Explorador interactivo de los datasets generados (zonas_riesgo.json y upz_riesgo.json) con desglose por UPZ.", "4. Explorador de Datos: ")

    # 8. GUIA DE DESPLIEGUE Y OPERACIONES
    add_heading_1(doc, "8. Guía de Despliegue, Infraestructura y DevOps")
    add_paragraph_styled(doc, "El despliegue de la infraestructura puede ejecutarse en estaciones de trabajo locales o servidores dedicados siguiendo este procedimiento estándar:")

    add_heading_2(doc, "8.1. Requisitos Previos")
    add_bullet_styled(doc, "Python 3.10 o superior con gestor de paquetes pip y venv.", "Python: ")
    add_bullet_styled(doc, "Ollama instalado con modelo descargado ('ollama pull llama3.2:3b' o 'ollama pull qwen2.5:3b').", "Ollama Engine: ")
    add_bullet_styled(doc, "Android Studio Hedgehog / Iguana con Android SDK 34 y JDK 17+ configurado en gradle.properties.", "Android Toolchain: ")

    add_heading_2(doc, "8.2. Pasos de Instalación y Arranque Rápido")
    add_code_block(
        doc,
        "# 1. Clonar repositorio y activar entorno virtual\n"
        "cd 'BARRIO SEGURO APP'\n"
        ".\\venv\\Scripts\\activate\n\n"
        "# 2. Instalar dependencias de Python\n"
        "pip install -r Agente/requirements.txt\n"
        "pip install fastapi uvicorn PyQt6 requests\n\n"
        "# 3. Iniciar el Centro de Control de Escritorio\n"
        "python Control/main.py\n\n"
        "# O iniciar el backend directamente por consola con soporte dual-env:\n"
        "python run_api.py --no-reload"
    )

    add_heading_2(doc, "8.3. Matriz de Solución de Problemas (Troubleshooting)")
    tbl_err = doc.add_table(rows=1, cols=3)
    format_table(
        tbl_err,
        [Inches(1.8), Inches(2.2), Inches(2.8)],
        ["Síntoma / Error", "Causa Técnica Probable", "Acción de Corrección"],
        [
            ["'No pude conectarme al backend' en celular", "Túnel USB desconectado o firewall de Windows bloqueando puerto 8000.", "Ejecutar 'adb reverse tcp:8000 tcp:8000' en consola o abrir regla en Firewall con New-NetFirewallRule."],
            ["'ollama_disponible: false' en /health", "El demonio de Ollama no está en ejecución.", "Abrir Ollama desde el Centro de Control (Pestaña 1) o ejecutar 'ollama serve' en terminal."],
            ["Error de jlink en build de Android", "JAVA_HOME apunta a un JRE en lugar de un JDK completo.", "Verificar que org.gradle.java.home en gradle.properties apunte al JDK embebido de Android Studio."],
            ["ModuleNotFoundError en run_api", "Dependencias divididas entre venv y Agente/.venv.", "run_api.py autoinyecta ambos entornos automáticamente. Ejecutar 'python run_api.py'."]
        ]
    )

    doc.save(file_path)
    print(f"Manual Técnico guardado exitosamente en: {file_path}")

if __name__ == "__main__":
    out_dir = r"c:\Users\dsant\Desktop\U CATOLICA\Programas\BARRIO SEGURO APP\documentacion"
    os.makedirs(out_dir, exist_ok=True)
    out_file = os.path.join(out_dir, "Manual_Tecnico.docx")
    build_manual_tecnico(out_file)
