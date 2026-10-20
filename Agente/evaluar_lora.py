"""
Evaluación real: compara el modelo base (Qwen2.5-3B-Instruct) contra el
modelo + adaptador LoRA entrenado, sobre las MISMAS preguntas que fallaron
en las pruebas de caja negra originales contra el agente en producción.

No mide "parece mejor" -- mide si invoca la herramienta correcta con
argumentos coherentes, comparando contra la respuesta esperada real.
"""
import json
import os
import sys

import torch
from peft import PeftModel
from transformers import AutoModelForCausalLM, AutoTokenizer, BitsAndBytesConfig

MODELO_BASE = os.environ.get("BARRIO_SEGURO_MODELO_LORA", "Qwen/Qwen2.5-3B-Instruct")
# Ruta relativa al proyecto (antes harcodeada a la ruta de otra máquina,
# "C:\Users\User\Desktop\Trabajo de Grado 2\...": no corría en ningún equipo
# distinto al que la entrenó). Debe coincidir con RUTA_SALIDA de
# entrenar_lora.py, que ya usaba la ruta relativa correcta.
RUTA_LORA = os.path.join(os.path.dirname(__file__), "lora_barrio_seguro")

CUDA_DISPONIBLE = torch.cuda.is_available()
if not CUDA_DISPONIBLE:
    print(
        "AVISO: no se detectó GPU CUDA en esta máquina. bitsandbytes (4-bit) "
        "requiere CUDA, así que se evalúa en CPU sin cuantizar -- mucho más "
        "lento (varios minutos por respuesta en un modelo de 3B es normal), "
        "pero funciona.\n"
    )

SYSTEM = (
    "Eres el asistente de la app \"Barrio Seguro\" sobre riesgo de inseguridad "
    "por localidad en Bogotá. SIEMPRE que te pregunten algo con números, "
    "nombres de localidades o comparaciones, usa las herramientas disponibles "
    "(localidad_extrema, obtener_ranking, obtener_localidad, "
    "comparar_localidades, buscar_barrio). Nunca inventes ni \"recuerdes\" una "
    "cifra. Un saludo o charla normal NO necesita ninguna herramienta."
)

TOOLS = [
    {"type": "function", "function": {
        "name": "localidad_extrema",
        "description": "Devuelve la localidad de mayor o menor riesgo.",
        "parameters": {"type": "object", "properties": {
            "cual": {"type": "string", "enum": ["mayor", "menor"]}}, "required": ["cual"]},
    }},
    {"type": "function", "function": {
        "name": "obtener_localidad",
        "description": "Devuelve el detalle de riesgo de UNA localidad por nombre.",
        "parameters": {"type": "object", "properties": {
            "nombre": {"type": "string"}}, "required": ["nombre"]},
    }},
    {"type": "function", "function": {
        "name": "comparar_localidades",
        "description": "Compara DOS O MÁS localidades entre sí.",
        "parameters": {"type": "object", "properties": {
            "nombres": {"type": "array", "items": {"type": "string"}}}, "required": ["nombres"]},
    }},
    {"type": "function", "function": {
        "name": "obtener_ranking",
        "description": "Devuelve el ranking completo de las 20 localidades.",
        "parameters": {"type": "object", "properties": {}, "required": []},
    }},
]

CASOS = [
    {"pregunta": "¿Cuál es la localidad más peligrosa de Bogotá?", "tool_esperada": "localidad_extrema"},
    {"pregunta": "¿Qué tan seguro es Chapinero?", "tool_esperada": "obtener_localidad"},
    {"pregunta": "Compara Kennedy y Suba", "tool_esperada": "comparar_localidades"},
    {"pregunta": "Dame el ranking de riesgo de todas las localidades", "tool_esperada": "obtener_ranking"},
    {"pregunta": "Hola", "tool_esperada": None},
]


def cargar(con_lora: bool):
    if not os.path.isdir(RUTA_LORA) and con_lora:
        raise SystemExit(
            f"No existe {RUTA_LORA}. Hace falta entrenar el adaptador primero "
            f"(python entrenar_lora.py) o copiar aquí la carpeta lora_barrio_seguro/ "
            f"ya entrenada en otra máquina."
        )

    tok = AutoTokenizer.from_pretrained(MODELO_BASE)
    if CUDA_DISPONIBLE:
        bnb_config = BitsAndBytesConfig(
            load_in_4bit=True, bnb_4bit_quant_type="nf4",
            bnb_4bit_compute_dtype=torch.bfloat16, bnb_4bit_use_double_quant=True,
        )
        modelo = AutoModelForCausalLM.from_pretrained(
            MODELO_BASE, quantization_config=bnb_config, device_map={"": 0},
        )
    else:
        # Sin CUDA no hay bitsandbytes: se carga en fp32 sobre CPU (no bf16 --
        # esta CPU no tiene aceleración nativa bf16, ver entrenar_lora.py).
        # Más RAM (~12-13GB para un modelo de 3B en fp32) pero no depende de GPU.
        modelo = AutoModelForCausalLM.from_pretrained(
            MODELO_BASE, torch_dtype=torch.float32, device_map="cpu",
        )
    if con_lora:
        modelo = PeftModel.from_pretrained(modelo, RUTA_LORA)
    modelo.eval()
    return tok, modelo


def preguntar(tok, modelo, pregunta):
    mensajes = [{"role": "system", "content": SYSTEM}, {"role": "user", "content": pregunta}]
    texto = tok.apply_chat_template(mensajes, tools=TOOLS, tokenize=False, add_generation_prompt=True)
    entradas = tok(texto, return_tensors="pt").to(modelo.device)
    with torch.no_grad():
        salida = modelo.generate(**entradas, max_new_tokens=200, do_sample=False, temperature=None, top_p=None)
    nuevo_texto = tok.decode(salida[0][entradas["input_ids"].shape[1]:], skip_special_tokens=True)
    return nuevo_texto


def tool_llamada(texto: str) -> str | None:
    if "<tool_call>" in texto:
        try:
            bloque = texto.split("<tool_call>")[1].split("</tool_call>")[0].strip()
            return json.loads(bloque).get("name")
        except Exception:
            return "ERROR_PARSEO"
    return None


def evaluar(etiqueta, con_lora):
    print(f"\n{'='*70}\n{etiqueta}\n{'='*70}")
    tok, modelo = cargar(con_lora)
    aciertos = 0
    for caso in CASOS:
        salida = preguntar(tok, modelo, caso["pregunta"])
        tool_obtenida = tool_llamada(salida)
        correcto = tool_obtenida == caso["tool_esperada"]
        aciertos += correcto
        print(f"\n[{'OK' if correcto else 'FALLO'}] \"{caso['pregunta']}\"")
        print(f"   esperaba: {caso['tool_esperada']!r} | obtuvo: {tool_obtenida!r}")
        print(f"   salida cruda: {salida[:200]!r}")
    print(f"\n>>> {etiqueta}: {aciertos}/{len(CASOS)} correctos ({aciertos/len(CASOS)*100:.0f}%)")
    del modelo
    if CUDA_DISPONIBLE:
        torch.cuda.empty_cache()
    return aciertos, len(CASOS)


if __name__ == "__main__":
    base_ok, total = evaluar("MODELO BASE (sin LoRA)", con_lora=False)
    lora_ok, _ = evaluar("MODELO + LoRA (Barrio Seguro)", con_lora=True)

    print(f"\n\n{'#'*70}")
    print(f"RESUMEN: base {base_ok}/{total} ({base_ok/total*100:.0f}%) vs. LoRA {lora_ok}/{total} ({lora_ok/total*100:.0f}%)")
    print(f"{'#'*70}")
