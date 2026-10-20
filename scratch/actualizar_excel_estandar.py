import openpyxl
from openpyxl.styles import Font, PatternFill, Alignment, Border, Side
from openpyxl.worksheet.datavalidation import DataValidation

def update_excel_standard():
    file_path = r'c:\Users\dsant\Desktop\U CATOLICA\Programas\BARRIO SEGURO APP\documentacion\Estandar_Componentes_Arqui Software e Infra + Metodología.xlsx'
    wb = openpyxl.load_workbook(file_path)
    
    # -------------------------------------------------------------
    # 1. SHEET: Est.Arquitectura Soft-Infra
    # -------------------------------------------------------------
    ws = wb['Est.Arquitectura Soft-Infra']
    
    # Identify merged ranges that need shifting (min_row >= 65)
    ranges_to_shift = []
    for rng in list(ws.merged_cells.ranges):
        if rng.min_row >= 65:
            ranges_to_shift.append((rng.min_col, rng.min_row, rng.max_col, rng.max_row))
            ws.merged_cells.remove(rng)
            
    # Insert 9 rows at row 65 (1 section header + 8 components)
    num_new_rows = 9
    ws.insert_rows(65, num_new_rows)
    
    # Re-merge the shifted ranges
    for min_col, min_row, max_col, max_row in ranges_to_shift:
        ws.merge_cells(
            start_row=min_row + num_new_rows,
            start_column=min_col,
            end_row=max_row + num_new_rows,
            end_column=max_col
        )
        
    # Styles definition matching original workbook exactly
    font_section_header = Font(name="Tahoma", size=9.5, bold=True, color="FFFFFFFF")
    fill_section_header = PatternFill(start_color="FF26306A", end_color="FF26306A", fill_type="solid")
    
    font_id = Font(name="Tahoma", size=9.0, bold=True, color="FF26306A")
    font_comp = Font(name="Tahoma", size=9.0, bold=True, color="000000")
    font_text = Font(name="Tahoma", size=9.0, bold=False, color="000000")
    
    border_thin = Border(
        left=Side(style='thin', color='FFBFBFBF'),
        right=Side(style='thin', color='FFBFBFBF'),
        top=Side(style='thin', color='FFBFBFBF'),
        bottom=Side(style='thin', color='FFBFBFBF')
    )
    
    align_center = Alignment(horizontal="center", vertical="center")
    align_wrap = Alignment(vertical="center", wrap_text=True)
    align_center_wrap = Alignment(horizontal="center", vertical="center", wrap_text=True)
    
    # Configure Section 12 Header at row 65
    ws.merge_cells("A65:F65")
    ws.row_dimensions[65].height = 18.0
    c_hdr = ws.cell(65, 1)
    c_hdr.value = "12. Adicional para proyectos con agentes de IA"
    c_hdr.font = font_section_header
    c_hdr.fill = fill_section_header
    c_hdr.alignment = Alignment(vertical="center")
    for col in range(2, 7):
        c_aux = ws.cell(65, col)
        c_aux.fill = fill_section_header
        c_aux.font = font_section_header
        
    # 8 AI Agent Components Data
    ai_components = [
        (
            "12.1",
            "Especificaciones del modelo base",
            "Especificación y tabla",
            "Nombre y familia del modelo base (e.g. LLaMA 3.2 3B / Qwen 2.5 3B), número de parámetros (3.21B / 3.09B), ventana de contexto (128k / 32k tokens), arquitectura y tokenizador (BPE / TikToken con vocabulario de 128k tokens) y requerimientos de memoria para inferencia local en CPU/edge.",
            "El modelo base seleccionado está justificado frente a las restricciones de hardware local y latencia de respuesta, y su tokenizador preserva la terminología del dominio de seguridad urbana sin truncamiento indebido.",
            "Completo"
        ),
        (
            "12.2",
            "Estrategia de adaptación LoRA",
            "Especificación técnica",
            "Hiperparámetros de adaptación de bajo rango: rank (r=16), factor de escala (alpha=32), dropout (0.05), módulos objetivo atencionales (q_proj, v_proj, k_proj, o_proj), cuantización/precisión (bfloat16 / 4-bit) y porcentaje de parámetros entrenables vs. totales (< 1%).",
            "La configuración de r y alpha equilibra capacidad de generalización y memoria disponible; los módulos objetivo seleccionados maximizan la adaptación atencional y el porcentaje de parámetros entrenables no excede el 1% del modelo base.",
            "Completo"
        ),
        (
            "12.3",
            "Componente RAG",
            "Diagrama y especificación",
            "Estrategia de indexación geoespacial por UPZ y cuadrante, recuperación contextual de hechos delictivos históricos, inyección dinámica en el System Prompt y desacoplamiento entre el almacén de conocimiento y el motor de lenguaje.",
            "La recuperación contextual inyecta exclusivamente hechos comprobados del sector geográfico del usuario (estadísticas de delitos, cuadrantes policiales) evitando alucinaciones fácticas en las respuestas del LLM.",
            "Completo"
        ),
        (
            "12.4",
            "Datos de entrenamiento (Linaje Bronze, Silver y Gold)",
            "Matriz de linaje de datos",
            "Trazabilidad de capas de datos: Capa Bronze (datos crudos ingeridos: OpenStreetMap, estadísticas delictivas distritales y proyecciones poblacionales DANE), Capa Silver (datos limpiados, estandarizados, imputados y con scoring geoespacial) y Capa Gold (datasets finales para entrenamiento y scoring).",
            "Cada registro en la capa Gold se puede trazar hasta su fuente original en Bronze y transformaciones en Silver, garantizando reproducibilidad y ausencia de sesgos o datos corruptos.",
            "Completo"
        ),
        (
            "12.5",
            "Dataset Gold-LoRA en formato ShareGPT",
            "Dataset estructurado (JSON)",
            "Conjunto de datos estructurado en formato conversacional multi-turno ShareGPT con roles ('system', 'human', 'gpt'), representativo de consultas reales ciudadanas sobre seguridad, rutas, prevención y emergencias en Chapinero/Bogotá.",
            "El dataset cuenta con al menos 1.000 ejemplos curados, sintaxis JSON válida, diversidad de intenciones de seguridad ciudadana y respuestas alineadas con protocolos policiales y de protección civil.",
            "Completo"
        ),
        (
            "12.6",
            "Particiones de entrenamiento y validación",
            "Tabla de distribución",
            "Estrategia de particionamiento (Train/Validation/Test con proporciones 85% / 15%), método de muestreo estratificado por categorías delictivas/intenciones conversacionales y fijación de semilla aleatoria (random seed=42) para reproducibilidad.",
            "No existe contaminación o fuga de datos (data leakage) entre particiones y la distribución de categorías es homogénea entre el conjunto de entrenamiento y el de validación.",
            "Completo"
        ),
        (
            "12.7",
            "Pipeline de entrenamiento e inferencia",
            "Diagrama de flujo y scripts",
            "Etapas de preprocesamiento, tokenización, bucle de entrenamiento (SFTTrainer / PEFT), checkpointing, fusión/exportación de adaptadores GGUF/Ollama y pipeline de inferencia asíncrono con streaming y manejo de excepciones.",
            "El pipeline de entrenamiento es ejecutable de extremo a extremo mediante un script reproducible (entrenar_lora.py) y el pipeline de inferencia entrega respuestas en streaming con latencia menor al umbral operativo sobre CPU/local.",
            "Completo"
        ),
        (
            "12.8",
            "Estrategia de evaluación y métricas objetivo",
            "Matriz de evaluación",
            "Batería de métricas cuantitativas y cualitativas: ROUGE-L, BLEU, Perplejidad en conjunto de prueba, tasa de alucinación fáctica en datos de Chapinero, y comparación de desempeño entre el modelo base y el modelo adaptado con LoRA.",
            "El modelo adaptado demuestra una mejora estadísticamente comprobable frente al modelo base en exactitud de nomenclatura local, precisión en números de cuadrantes policiales y apego al formato de respuesta.",
            "Completo"
        ),
    ]
    
    # Fill Rows 66 to 73
    for idx, item in enumerate(ai_components):
        row_num = 66 + idx
        ws.row_dimensions[row_num].height = 37.2
        for col_num, val in enumerate(item, start=1):
            cell = ws.cell(row_num, col_num)
            cell.value = val
            cell.border = border_thin
            if col_num == 1:
                cell.font = font_id
                cell.alignment = align_center
            elif col_num == 2:
                cell.font = font_comp
                cell.alignment = align_wrap
            elif col_num in (3, 4, 5):
                cell.font = font_text
                cell.alignment = align_wrap
            elif col_num == 6:
                cell.font = font_text
                cell.alignment = align_center
                
    # Update Footer Note (Row 78) to account for all 57 components
    c_foot = ws.cell(78, 1)
    c_foot.value = "49 componentes base + 8 componentes de Agentes de IA (Total: 57 componentes). Universidad Católica de Colombia - Vigilada Mineducación"
    c_foot.font = Font(name="Tahoma", size=7.5, bold=False, color="FF808080")
    
    # Fill "Estado" column for all existing architecture components
    architecture_rows = [
        6, 7, 8, 9, 10, 11, 12, 13, 14, 15,
        17, 18, 19, 20, 21,
        23, 24, 25, 26, 27,
        29, 30, 31, 32, 33, 34, 35,
        37, 38, 39,
        41, 42, 43,
        45,
        47, 48,
        50, 51,
        53, 54, 55, 56, 57, 58,
        60, 61, 62, 63, 64
    ]
    for r in architecture_rows:
        cell_est = ws.cell(r, 6)
        cell_est.value = "Completo"
        cell_est.alignment = align_center
        cell_est.font = font_text
        cell_est.border = border_thin

    # Update Data Validation for Est.Arquitectura Soft-Infra
    all_data_rows = architecture_rows + list(range(66, 74))
    all_sqref_cells = " ".join([f"F{r}" for r in all_data_rows])
    if ws.data_validations.dataValidation:
        ws.data_validations.dataValidation[0].sqref = all_sqref_cells

    # -------------------------------------------------------------
    # 2. SHEET: Metodología
    # -------------------------------------------------------------
    ws_met = wb['Metodología']
    methodology_rows = [3, 4, 5, 6, 7, 8, 9]
    for r in methodology_rows:
        cell_m = ws_met.cell(r, 6)
        cell_m.value = "Completo"
        cell_m.alignment = align_center
        cell_m.font = font_text
        cell_m.border = border_thin
        
    all_sqref_met = " ".join([f"F{r}" for r in methodology_rows])
    if ws_met.data_validations.dataValidation:
        ws_met.data_validations.dataValidation[0].sqref = all_sqref_met

    wb.save(file_path)
    print("Excel actualizado exitosamente en:", file_path)

if __name__ == '__main__':
    update_excel_standard()
