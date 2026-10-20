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
        r_ft = p_ft.add_run("Barrio Seguro | Manual de Usuario y Guía de Operación Ciudadana — v1.0")
        r_ft.font.name = 'Calibri'
        r_ft.font.size = Pt(8.5)
        r_ft.font.color.rgb = RGBColor(0x78, 0x90, 0x9C)

def add_cover(doc):
    p_pre = doc.add_paragraph()
    p_pre.alignment = WD_ALIGN_PARAGRAPH.CENTER
    p_pre.paragraph_format.space_before = Pt(30)
    p_pre.paragraph_format.space_after = Pt(6)
    r_pre = p_pre.add_run("UNIVERSIDAD CATÓLICA DE COLOMBIA\nPROYECTO DE INNOVACIÓN SOCIAL Y TECNOLÓGICA")
    r_pre.font.name = 'Calibri'
    r_pre.font.size = Pt(11)
    r_pre.font.bold = True
    r_pre.font.color.rgb = RGBColor(0x37, 0x47, 0x4F)

    p_badge = doc.add_paragraph()
    p_badge.alignment = WD_ALIGN_PARAGRAPH.CENTER
    p_badge.paragraph_format.space_before = Pt(10)
    p_badge.paragraph_format.space_after = Pt(12)
    r_badge = p_badge.add_run("✦ GUÍA OFICIAL PARA EL CIUDADANO Y ADMINISTRADOR ✦")
    r_badge.font.name = 'Calibri'
    r_badge.font.size = Pt(11)
    r_badge.font.bold = True
    r_badge.font.color.rgb = RGBColor(0x00, 0x89, 0x7B)

    p_title = doc.add_paragraph()
    p_title.alignment = WD_ALIGN_PARAGRAPH.CENTER
    p_title.paragraph_format.space_before = Pt(4)
    p_title.paragraph_format.space_after = Pt(10)
    r_title = p_title.add_run("MANUAL DE USUARIO Y GUÍA\nDE OPERACIÓN CIUDADANA")
    r_title.font.name = 'Calibri'
    r_title.font.size = Pt(24)
    r_title.font.bold = True
    r_title.font.color.rgb = RGBColor(0x00, 0x4D, 0x40)

    p_sub = doc.add_paragraph()
    p_sub.alignment = WD_ALIGN_PARAGRAPH.CENTER
    p_sub.paragraph_format.space_before = Pt(0)
    p_sub.paragraph_format.space_after = Pt(24)
    r_sub = p_sub.add_run("Aplicación Móvil de Seguridad Ciudadana, Asistente de IA Local y Centro de Control de Monitoreo Comunitario (Chapinero — Bogotá D.C.)")
    r_sub.font.name = 'Calibri'
    r_sub.font.size = Pt(12)
    r_sub.font.color.rgb = RGBColor(0x45, 0x5A, 0x64)

    table = doc.add_table(rows=6, cols=2)
    table.alignment = WD_TABLE_ALIGNMENT.CENTER
    table.autofit = False
    col_w = [Inches(2.2), Inches(4.4)]
    meta = [
        ("Aplicación:", "Barrio Seguro Móvil & Panel de Control de Escritorio"),
        ("Versión Pública:", "1.1.0 (Compilación APK v16 — app-latest.apk)"),
        ("Compatibilidad Celular:", "Android 8.0 (Oreo) o superior con GPS integrado"),
        ("Compatibilidad PC:", "Windows 10 / 11, Linux, macOS (Python 3.10+)"),
        ("Área de Cobertura:", "Localidad de Chapinero y Distrito Capital de Bogotá"),
        ("Fecha:", "Octubre de 2026 | Bogotá D.C., Colombia")
    ]
    for r_idx, (k, v) in enumerate(meta):
        for c_idx, val in enumerate([k, v]):
            cell = table.cell(r_idx, c_idx)
            cell.width = col_w[c_idx]
            set_cell_background(cell, "E0F2F1" if r_idx % 2 == 0 else "FFFFFF")
            set_cell_margins(cell, top=60, bottom=60, left=100, right=100)
            p = cell.paragraphs[0]
            p.paragraph_format.space_before = Pt(0)
            p.paragraph_format.space_after = Pt(0)
            r = p.add_run(val)
            r.font.name = 'Calibri'
            r.font.size = Pt(9.5)
            if c_idx == 0:
                r.font.bold = True
                r.font.color.rgb = RGBColor(0x00, 0x4D, 0x40)
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
    run.font.color.rgb = RGBColor(0x00, 0x4D, 0x40)
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
    run.font.color.rgb = RGBColor(0x00, 0x79, 0x6B)
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
        r_bold.font.color.rgb = RGBColor(0x00, 0x69, 0x5C)
    r_text = p.add_run(text)
    r_text.font.name = 'Calibri'
    r_text.font.size = Pt(9.5)
    r_text.font.color.rgb = RGBColor(0x33, 0x33, 0x33)
    return p

def add_callout(doc, title, content, bg_hex="E0F2F1", border_color="00897B"):
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
    r_title.font.color.rgb = RGBColor(0x00, 0x4D, 0x40)
    
    r_content = p.add_run(content)
    r_content.font.name = 'Calibri'
    r_content.font.size = Pt(9.5)
    r_content.font.color.rgb = RGBColor(0x37, 0x47, 0x4F)
    
    p_sp = doc.add_paragraph()
    p_sp.paragraph_format.space_before = Pt(0)
    p_sp.paragraph_format.space_after = Pt(2)

def add_step_box(doc, step_num, step_title, step_desc):
    table = doc.add_table(rows=1, cols=2)
    table.alignment = WD_TABLE_ALIGNMENT.CENTER
    table.autofit = False
    table.columns[0].width = Inches(0.9)
    table.columns[1].width = Inches(5.9)
    
    cell_num = table.cell(0, 0)
    set_cell_background(cell_num, "00695C")
    set_cell_margins(cell_num, top=80, bottom=80, left=60, right=60)
    p_n = cell_num.paragraphs[0]
    p_n.alignment = WD_ALIGN_PARAGRAPH.CENTER
    r_n = p_n.add_run(f"PASO\n{step_num}")
    r_n.font.name = 'Calibri'
    r_n.font.size = Pt(11)
    r_n.font.bold = True
    r_n.font.color.rgb = RGBColor(0xFF, 0xFF, 0xFF)
    
    cell_txt = table.cell(0, 1)
    set_cell_background(cell_txt, "F1F8F6")
    set_cell_margins(cell_txt, top=70, bottom=70, left=120, right=120)
    p_t = cell_txt.paragraphs[0]
    p_t.paragraph_format.space_before = Pt(0)
    p_t.paragraph_format.space_after = Pt(2)
    r_t = p_t.add_run(step_title + "\n")
    r_t.font.name = 'Calibri'
    r_t.font.size = Pt(10.5)
    r_t.font.bold = True
    r_t.font.color.rgb = RGBColor(0x00, 0x4D, 0x40)
    
    r_d = p_t.add_run(step_desc)
    r_d.font.name = 'Calibri'
    r_d.font.size = Pt(9.5)
    r_d.font.color.rgb = RGBColor(0x33, 0x33, 0x33)
    
    p_sp = doc.add_paragraph()
    p_sp.paragraph_format.space_before = Pt(0)
    p_sp.paragraph_format.space_after = Pt(2)

def format_table(table, col_widths, headers, data, header_bg="00695C", alt_bg="F1F8F6"):
    table.alignment = WD_TABLE_ALIGNMENT.CENTER
    table.autofit = False
    
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

def build_manual_usuario(file_path):
    doc = Document()
    setup_page(doc)
    add_cover(doc)

    # 1. BIENVENIDA Y PRESENTACION
    add_heading_1(doc, "1. Presentación y Bienvenida a Barrio Seguro")
    add_paragraph_styled(doc, "Bienvenido a Barrio Seguro, la plataforma ciudadana de prevención del delito, asistencia en emergencias y análisis inteligente de seguridad diseñada para la localidad de Chapinero y el Distrito Capital de Bogotá. Desarrollada en el marco de proyectos de innovación de la Universidad Católica de Colombia, esta aplicación combina análisis de geodatos oficiales, inteligencia artificial local y herramientas de reacción comunitaria para que te muevas por la ciudad con tranquilidad y respaldo.")

    add_heading_2(doc, "1.1. ¿Qué puedes hacer con Barrio Seguro?")
    add_bullet_styled(doc, "Tu teléfono analiza tu ubicación GPS y te indica mediante un semáforo si la calle o cuadrante donde estás presenta riesgo bajo, moderado o alto.", "Conocer tu Nivel de Riesgo en Vivo: ")
    add_bullet_styled(doc, "Explora mapas de calor de Bogotá por UPZs y cuadrantes, filtrando por tipos de delito (hurto a celular, robo a viviendas, atraco, zonas oscuras).", "Consultar el Mapa de Calor: ")
    add_bullet_styled(doc, "Pregunta en lenguaje natural sobre calles seguras, horarios recomendados y rutas seguras usando texto o tu propia voz.", "Hablar con el Asistente de IA: ")
    add_bullet_styled(doc, "Envía alertas a las autoridades y a la comunidad adjuntando descripción, fotos y ubicación exacta de manera 100% anónima.", "Registrar Denuncias y Reportes: ")
    add_bullet_styled(doc, "Emite una señal de auxilio inmediata, llama con un solo toque al cuadrante policial CAI más cercano o a la Línea 123, y consulta guías médicas de primeros auxilios sin necesidad de internet.", "Activar el Botón de Pánico y Emergencias: ")
    add_bullet_styled(doc, "Recibe avisos sobre deslizamientos en los cerros orientales y puntos de encharcamiento en vías principales.", "Monitorear Desastres Ambientales: ")

    add_callout(
        doc,
        "Compromiso con tu Privacidad y Datos Personales",
        "Barrio Seguro respeta profundamente tu privacidad. La aplicación no comercializa tus datos ni almacena tu trayectoria personal. Tu ubicación GPS es procesada para consultar el nivel de riesgo de la zona y no queda vinculada a tu nombre ni cédula. Las denuncias ciudadanas pueden enviarse de forma completamente anónima."
    )

    # 2. REQUISITOS E INSTALACION
    add_heading_1(doc, "2. Requisitos y Preparación del Teléfono Móvil")
    add_paragraph_styled(doc, "Para disfrutar de todas las características de Barrio Seguro, verifica que tu dispositivo cumpla con los siguientes requisitos mínimos:")

    tbl_req = doc.add_table(rows=1, cols=3)
    format_table(
        tbl_req,
        [Inches(1.8), Inches(2.2), Inches(2.8)],
        ["Componente", "Requisito Mínimo", "Recomendado para Mejor Experiencia"],
        [
            ["Sistema Operativo", "Android 8.0 (Oreo) o superior", "Android 11, 12, 13 o 14 con Material You"],
            ["Memoria RAM", "2 GB de memoria libre", "3 GB o más para fluidez de mapas"],
            ["Almacenamiento", "80 MB libres en el dispositivo", "150 MB (para caché de mapas y audios)"],
            ["Ubicación / Sensores", "Sensor GPS / Localización activo", "GPS de alta precisión y brújula digital"],
            ["Conectividad", "Conexión móvil 3G / 4G / Wi-Fi", "Wi-Fi o datos móviles 4G/5G para IA en tiempo real"]
        ]
    )

    add_heading_2(doc, "2.1. Cómo Instalar la Aplicación Paso a Paso")
    add_step_box(doc, "1", "Descargar el Archivo Instalador (APK v16)", "Descarga el archivo instalador más reciente 'app-latest.apk' (o 'app-v16.apk') en tu celular desde el repositorio o la carpeta compartida de Descargas.")
    add_step_box(doc, "2", "Permitir Fuentes Desconocidas", "Si Android te muestra una advertencia de seguridad, pulsa sobre 'Ajustes' o 'Configuración' y activa la casilla 'Confiar en esta fuente' o 'Permitir instalación de aplicaciones desconocidas'.")
    add_step_box(doc, "3", "Completar la Instalación", "Abre el archivo descargado, presiona 'Instalar' y espera unos segundos hasta que aparezca el mensaje 'Aplicación instalada'. Presiona 'Abrir'.")

    # 3. PRIMEROS PASOS Y PERMISOS
    add_heading_1(doc, "3. Primeros Pasos y Asistente Inicial")
    add_paragraph_styled(doc, "Al abrir Barrio Seguro por primera vez, serás recibido por un tutorial interactivo de bienvenida que te explicará de forma visual las herramientas de la app.")

    add_heading_2(doc, "3.1. Autorización de Permisos Esenciales")
    add_paragraph_styled(doc, "La aplicación te solicitará dos permisos fundamentales del sistema Android:")
    add_bullet_styled(doc, "Permite a Barrio Seguro calcular si la calle o manzana donde caminas es segura o crítica, y sugerirte el CAI policial más cercano. Te recomendamos seleccionar 'Mientras la app está en uso' y marcar 'Precisa'.", "1. Permiso de Ubicación (GPS): ")
    add_bullet_styled(doc, "Permite que la app te envíe alertas preventivas si te aproximas a una zona de alta peligrosidad o si ocurre una emergencia vecinal reportada.", "2. Permiso de Notificaciones: ")

    add_callout(
        doc,
        "¿Qué pasa si rechazas los permisos por error?",
        "Si negaste la ubicación por equivocación, la tarjeta principal mostrará un botón de 'Ajustes'. Al tocarlo, el teléfono te llevará directamente a los ajustes de la aplicación donde podrás reactivar el interruptor de Ubicación sin reinstalar la app."
    )

    add_heading_2(doc, "3.2. Conexión con el Servidor Barrio Seguro")
    add_paragraph_styled(doc, "Barrio Seguro incluye tecnología de autoconexión inteligente. Al iniciar, la aplicación busca automáticamente el servidor en tu red local o conexión USB. Si te conectas fuera de la red local, puedes ingresar a la pantalla de Ajustes y escribir la dirección web segura (URL de Cloudflare) provista por el administrador del sistema.")

    # 4. GUIA DETALLADA DE LA APP MOVIL
    add_heading_1(doc, "4. Guía Detallada de las Funciones de la Aplicación")

    add_heading_2(doc, "4.1. Pantalla de Inicio (Dashboard de Riesgo Personalizado)")
    add_paragraph_styled(doc, "La pantalla principal te ofrece una lectura instantánea de tu entorno urbano:")
    add_bullet_styled(doc, "Un indicador circular de color te resume la seguridad de tu entorno: Verde (Zona Segura), Amarillo (Precaución Moderada) y Rojo (Zona de Alto Riesgo Delictivo).", "Semáforo de Seguridad: ")
    add_bullet_styled(doc, "Indica la UPZ donde te ubicas (por ejemplo: UPZ 89 - San Isidro Patios, UPZ 99 - Chapinero Central) y el número de cuadrante policial asignado.", "Tu Localización Exacta: ")
    add_bullet_styled(doc, "Una barra dinámica te muestra el índice calculado de 0 a 100 puntos y te aconseja sobre las horas de mayor precaución en la zona (por ejemplo, después de las 21:00 horas).", "Puntaje y Horario Crítico: ")
    add_bullet_styled(doc, "En la parte superior verás si tu teléfono está sincronizado con el centro de datos o si está operando con los últimos datos guardados.", "Estado de Conexión: ")

    add_heading_2(doc, "4.2. Mapa Interactivo de Calor y Zonas de Riesgo")
    add_paragraph_styled(doc, "Accede tocando el icono de 'Mapa' en la barra inferior para ver la radiografía completa de seguridad de Bogotá y Chapinero:")
    add_bullet_styled(doc, "Navega con gestos táctiles (pellizcar para hacer zoom, deslizar con un dedo para moverte y presionar el botón de la mira para regresar a tu ubicación GPS).", "Navegación Intuitiva: ")
    add_bullet_styled(doc, "Las zonas con sombras rojas y anaranjadas concentran mayor número de incidentes delictivos históricos. Las zonas verdes y azules corresponden a sectores vigilados o de baja incidencia.", "Capas Térmicas de Calor: ")
    add_bullet_styled(doc, "Filtra el mapa según lo que necesites consultar: 'Hurto a Personas', 'Hurto de Celulares', 'Robo de Vehículos' o 'Sectores con Poca Luz'.", "Selector de Delitos: ")
    add_bullet_styled(doc, "Toca los iconos azules de escudo para ver los CAIs de policía (Lourdes, Virrey, Chapinero) con su dirección y teléfono directo.", "Puntos de Asistencia Policial: ")

    add_heading_2(doc, "4.3. Asistente Virtual Inteligente (Chat de Seguridad IA)")
    add_paragraph_styled(doc, "El Asistente Virtual es un agente de inteligencia artificial entrenado para aconsejarte sobre seguridad urbana:")
    add_bullet_styled(doc, "Escribe tus dudas como si hablaras con un experto: '¿Qué precauciones debo tomar en la Calle 63?', '¿Cómo llegar seguro a la estación de TransMilenio de la Calle 72?'.", "Consultas en Lenguaje Natural: ")
    add_bullet_styled(doc, "Si vas caminando y no puedes escribir, presiona el icono del micrófono para hablarle a la aplicación. Tu voz se convertirá en texto automáticamente.", "Uso del Micrófono (Dictado por Voz): ")
    add_bullet_styled(doc, "Presiona el botón de altavoz en cualquier respuesta para que la aplicación te lea el consejo en voz alta mediante los auriculares o altavoz.", "Lectura Auditiva (Voz en Altavoz): ")

    add_heading_2(doc, "4.4. Módulo de Denuncias y Reportes Ciudadanos")
    add_paragraph_styled(doc, "Ayuda a proteger a tus vecinos reportando cualquier anomalía que presencies en 4 sencillos pasos:")
    add_step_box(doc, "1", "Elegir el Tipo de Incidente", "Selecciona una categoría: Hurto, Intento de atraco, Acoso callejero, Venta de estupefacientes, Riña o Luminaria dañada.")
    add_step_box(doc, "2", "Confirmar el Lugar", "La app marcará automáticamente tus coordenadas GPS actuales. También puedes mover el pin en el mapa si el hecho ocurrió a unas cuadras.")
    add_step_box(doc, "3", "Escribir la Descripción", "Añade detalles útiles: características de los sospechosos, hora estimada, placas de vehículos o descripción de lo ocurrido.")
    add_step_box(doc, "4", "Adjuntar Foto y Enviar", "Si dispones de una fotografía que sirva de evidencia, adjúntala y presiona 'Enviar Denuncia'. El reporte ingresará inmediatamente a la base comunitaria.")

    add_heading_2(doc, "4.5. Botón de Pánico y Asistencia en Emergencias")
    add_paragraph_styled(doc, "Diseñado para actuar en situaciones de riesgo inminente o crisis médica:")
    add_bullet_styled(doc, "Mantén pulsado el botón rojo central SOS. Se activará una cuenta regresiva de 3 segundos con vibración antes de enviar la señal, permitiéndote cancelar en caso de toque involuntario.", "Botón de Pánico SOS: ")
    add_bullet_styled(doc, "Toca cualquiera de los accesos directos para enlazar la llamada telefónica sin marcar números: Línea 123 Nacional, CAI Chapinero (Policía de cuadrante) y Línea Púrpura 155 (Asistencia a mujeres).", "Llamadas de Auxilio con 1 Toque: ")
    add_bullet_styled(doc, "Accede a fichas ilustradas paso a paso para atender casos urgentes antes de que llegue la ambulancia: Reanimación Cardiopulmonar (RCP), control de hemorragias, quemaduras y manejo de ataques de pánico.", "Guías de Primeros Auxilios Offline: ")

    add_heading_2(doc, "4.6. Desastres Ambientales y Gestión del Riesgo")
    add_paragraph_styled(doc, "La ladera oriental de Chapinero presenta condiciones geográficas particulares. Esta sección te mantiene informado sobre:")
    add_bullet_styled(doc, "Monitoreo en UPZ Pardo Rubio, San Luis y vía a La Calera ante riesgo de deslizamiento de tierras por lluvias prolongadas.", "Alertas de Remoción en Masa: ")
    add_bullet_styled(doc, "Identificación de puntos críticos de estancamiento de aguas en la Carrera Séptima, Calle 72 y Avenida Caracas.", "Puntos Críticos de Inundación: ")
    add_bullet_styled(doc, "Enlace directo con el IDIGER, Bomberos de Bogotá y Defensa Civil.", "Números de Socorro: ")

    add_heading_2(doc, "4.7. Chat Comunitario Vecinal y Ajustes")
    add_bullet_styled(doc, "Comunícate en directo con los vecinos de tu sector para compartir alertas sobre vehículos sospechosos, pérdidas de mascotas o eventos del barrio.", "Chat Comunitario en Vivo: ")
    add_bullet_styled(doc, "Configura a qué distancia deseas que te avise la app ante una zona de riesgo (100 metros, 500 metros o 1 kilómetro) y alterna entre el Modo Claro y el Modo Oscuro.", "Ajustes de Notificaciones y Tema: ")

    # 5. GUIA DEL PANEL DE CONTROL DE ESCRITORIO
    add_heading_1(doc, "5. Guía de Operación del Panel de Control de Escritorio")
    add_paragraph_styled(doc, "Para administradores, coordinadores de seguridad vecinal o evaluadores, el sistema incluye una aplicación de escritorio completa desarrollada en PyQt6 que centraliza todo el control operativo:")

    add_step_box(doc, "1", "Lanzar el Panel de Control", "En la computadora central, abre una terminal y ejecuta 'python Control/main.py' o haz doble clic sobre el acceso directo.")
    add_step_box(doc, "2", "Encender los 3 Servicios Esenciales", "En la Pestaña 1 ('Centro de Encendido & Servicios'), pulsa en orden los botones verdes: '▶️ Encender API', '🌐 Encender Túnel' y '🧠 Encender IA'.")
    add_step_box(doc, "3", "Supervisar Usuarios Conectados", "Observa la tarjeta superior '👥 USUARIOS EN TIEMPO REAL'. El contador verde indicará cuántos celulares están usando activamente la red.")
    add_step_box(doc, "4", "Monitorear la Actividad y Rendimiento", "Navega a la Pestaña 2 para ver en tiempo real cada solicitud que hacen los celulares, o a la Pestaña 3 para verificar el consumo de procesador y memoria de la computadora.")

    # 6. PREGUNTAS FRECUENTES Y SOPORTE
    add_heading_1(doc, "6. Preguntas Frecuentes (FAQ) y Soporte Técnico")

    faq_data = [
        ("1. ¿La aplicación consume muchos datos móviles?", "No. El intercambio de datos está optimizado en paquetes JSON comprimidos de menos de 2 kilobytes por consulta. Los mapas e iconos se guardan en la memoria caché del teléfono."),
        ("2. ¿Qué hago si la app dice 'No pude conectarme al backend'?", "Verifica que el computador que aloja el servidor tenga encendida la API y el Túnel. Si estás por USB, revisa que el cable esté bien conectado y que el comando 'adb reverse tcp:8000 tcp:8000' esté activo."),
        ("3. ¿Puedo usar la aplicación fuera de Chapinero?", "Sí. La aplicación cubre todo el Distrito Capital de Bogotá. La localidad de Chapinero cuenta con la mayor granularidad y detalle de datos históricos y respuestas de IA."),
        ("4. ¿Mis denuncias pueden ser vistas por cualquier persona?", "Las denuncias son procesadas de manera anónima y sólo se reflejan como puntos estadísticos agregados en el mapa de calor para alertar a otros transeúntes."),
        ("5. ¿La inteligencia artificial requiere conexión a internet externa?", "No necesariamente. El modelo de lenguaje corre localmente mediante Ollama en el servidor de control, lo que asegura que tus preguntas no salgan a empresas externas."),
        ("6. ¿Cómo activo el modo oscuro?", "Ingresa a la pantalla de Ajustes desde la barra de navegación inferior y activa el interruptor de 'Modo Oscuro'."),
        ("7. ¿Qué pasa si presiono el Botón de Pánico sin querer?", "Tienes 3 segundos de cuenta regresiva para pulsar el botón de cancelar antes de que se dispare la alerta sonora y la llamada de auxilio."),
        ("8. ¿Dónde puedo descargar las actualizaciones de la aplicación?", "El equipo administrador publica periódicamente los nuevos archivos APK en el repositorio oficial o en el canal comunitario."),
        ("9. ¿La app reemplaza la llamada formal al 123?", "Barrio Seguro es una herramienta de alerta temprana y acompañamiento preventivo. En caso de delito flagrante o riesgo vital grave, siempre utiliza el acceso directo para llamar a la Línea 123."),
        ("10. ¿A quién acudo si necesito soporte técnico?", "Comunícate con el equipo de soporte de la Facultad de Ingeniería de Sistemas de la Universidad Católica de Colombia mediante el correo institucional del proyecto.")
    ]

    for q, a in faq_data:
        add_paragraph_styled(doc, a, bold_prefix=q + "\n")

    doc.save(file_path)
    print(f"Manual de Usuario guardado exitosamente en: {file_path}")

if __name__ == "__main__":
    out_dir = r"c:\Users\dsant\Desktop\U CATOLICA\Programas\BARRIO SEGURO APP\documentacion"
    os.makedirs(out_dir, exist_ok=True)
    out_file = os.path.join(out_dir, "Manual_de_Usuario.docx")
    build_manual_usuario(out_file)
