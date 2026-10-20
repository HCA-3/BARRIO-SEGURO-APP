"""
Fine-tuning LoRA del agente de Barrio Seguro, sobre el dataset Gold real
(ver Agente/gold_dataset_sharegpt.json), para corregir el bug medido en las
pruebas de caja negra: el modelo no invoca sus herramientas de forma
consistente (o invoca la equivocada) para preguntas centrales del dominio.

Modelo base: meta-llama/Llama-3.2-3B-Instruct (requiere HF_TOKEN -- aceptar
la licencia de Meta en huggingface.co/meta-llama/Llama-3.2-3B-Instruct y
generar un token en huggingface.co/settings/tokens).

Uso:
    set HF_TOKEN=hf_xxxxxxxxxxxx
    python entrenar_lora.py

Salida: adaptador LoRA en Agente/lora_barrio_seguro/ (unos pocos MB, no el
modelo completo -- eso es justamente la ventaja de LoRA).
"""
import json
import os

import torch
from datasets import Dataset
from peft import LoraConfig, get_peft_model, TaskType
from transformers import (
    AutoModelForCausalLM,
    AutoTokenizer,
    BitsAndBytesConfig,
    TrainingArguments,
    Trainer,
    DataCollatorForLanguageModeling,
)

MODELO_BASE = os.environ.get("BARRIO_SEGURO_MODELO_LORA", "Qwen/Qwen2.5-3B-Instruct")
# Por defecto Qwen2.5-3B-Instruct (Apache 2.0, acceso inmediato, sin token
# necesario) en vez de meta-llama/Llama-3.2-3B-Instruct (licencia de Meta,
# requiere aprobación manual que puede tardar) -- mismo orden de magnitud de
# parámetros y mismo soporte de tool-calling. Para repetir el entrenamiento
# con Llama una vez aprobado el acceso:
#   set BARRIO_SEGURO_MODELO_LORA=meta-llama/Llama-3.2-3B-Instruct
#   set HF_TOKEN=hf_xxxxxxxxxxxx
RUTA_DATASET = os.path.join(os.path.dirname(__file__), "gold_dataset_sharegpt.json")
RUTA_SALIDA = os.path.join(os.path.dirname(__file__), "lora_barrio_seguro")

HF_TOKEN = os.environ.get("HF_TOKEN")
if "llama" in MODELO_BASE.lower() and not HF_TOKEN:
    raise SystemExit(
        "Falta la variable de entorno HF_TOKEN para un modelo de Meta. Genera "
        "un token en https://huggingface.co/settings/tokens (tras ser "
        f"aprobado en https://huggingface.co/{MODELO_BASE}) y corre:\n"
        "  set HF_TOKEN=hf_xxxxxxxxxxxx"
    )

CUDA_DISPONIBLE = torch.cuda.is_available()
print(f"CUDA disponible: {CUDA_DISPONIBLE}")
if CUDA_DISPONIBLE:
    print(f"GPU: {torch.cuda.get_device_name(0)}")
else:
    print(
        "Sin GPU CUDA: se entrena en CPU. bitsandbytes (4-bit) no funciona sin "
        "CUDA, así que se carga el modelo en bf16 sin cuantizar -- usa más RAM "
        "(~6-7GB para 3B) y es más lento, pero con 42 ejemplos de train igual "
        "es viable en una máquina de escritorio normal."
    )

# --- 1. Cargar dataset Gold y convertirlo al formato de texto del modelo ---
with open(RUTA_DATASET, encoding="utf-8") as f:
    gold = json.load(f)


def conversacion_a_texto(tokenizer, conversacion: list[dict]) -> str:
    """Convierte una conversación ShareGPT (roles system/human/gpt/tool) al
    formato de chat template de Llama 3.2, incluyendo tool_calls cuando
    aplica. Se entrena sobre la conversación completa (incluye el turno de
    invocación de herramienta), que es justo lo que el modelo necesita
    aprender a producir correctamente."""
    mensajes = []
    for turno in conversacion:
        rol = {"system": "system", "human": "user", "gpt": "assistant", "tool": "tool"}[turno["from"]]
        msg = {"role": rol, "content": turno.get("value", "")}
        if turno.get("tool_calls"):
            msg["tool_calls"] = [
                {
                    "type": "function",
                    "function": {"name": tc["name"], "arguments": tc["arguments"]},
                }
                for tc in turno["tool_calls"]
            ]
        mensajes.append(msg)
    return tokenizer.apply_chat_template(mensajes, tokenize=False, add_generation_prompt=False)


# --- 2. Cargar tokenizer y modelo base en 4-bit (QLoRA, cabe en 12GB VRAM) ---
tokenizer = AutoTokenizer.from_pretrained(MODELO_BASE, token=HF_TOKEN)
if tokenizer.pad_token is None:
    tokenizer.pad_token = tokenizer.eos_token

textos_train = [conversacion_a_texto(tokenizer, ej["conversations"]) for ej in gold["train"]]
textos_val = [conversacion_a_texto(tokenizer, ej["conversations"]) for ej in gold["validation"]]

print(f"\nEjemplo de conversación formateada:\n{textos_train[0][:600]}\n")

ds_train = Dataset.from_dict({"text": textos_train})
ds_val = Dataset.from_dict({"text": textos_val})


def tokenizar(ejemplo):
    # Sin padding fijo: cada ejemplo queda en su longitud real (truncada a
    # 1024 como tope de seguridad). DataCollatorForLanguageModeling rellena
    # dinámicamente cada lote hasta el más largo DE ESE LOTE, no a 1024 fijo
    # -- y genera "labels" él mismo enmascarando el padding con -100, así que
    # no hace falta fijarlo aquí. Con conversaciones típicas de este dataset
    # (unos cientos de tokens, no 1024), esto evita pagar el costo cuadrático
    # de atención de rellenar cada ejemplo a 1024 por nada.
    return tokenizer(ejemplo["text"], truncation=True, max_length=1024)


ds_train = ds_train.map(tokenizar, remove_columns=["text"])
ds_val = ds_val.map(tokenizar, remove_columns=["text"])

if CUDA_DISPONIBLE:
    bnb_config = BitsAndBytesConfig(
        load_in_4bit=True,
        bnb_4bit_quant_type="nf4",
        bnb_4bit_compute_dtype=torch.bfloat16,
        bnb_4bit_use_double_quant=True,
    )
    modelo = AutoModelForCausalLM.from_pretrained(
        MODELO_BASE,
        quantization_config=bnb_config,
        # device_map="auto" dispara en Windows un mmap de los safetensors que
        # falla con "El archivo de paginación es demasiado pequeño" (error 1455)
        # incluso con RAM/pagefile de sobra -- es un problema conocido del path
        # de carga diferida de accelerate en Windows, no de recursos reales.
        # device_map={"": 0} fuerza todo directo a la GPU 0 sin ese mecanismo.
        device_map={"": 0},
        token=HF_TOKEN,
    )
else:
    # Sin CUDA no hay bitsandbytes: se entrena en CPU sobre el modelo sin
    # cuantizar. fp32 y no bf16: esta CPU (Intel Comet Lake, sin AVX-512) no
    # tiene aceleración nativa de bf16, así que PyTorch termina convirtiendo
    # de ida y vuelta a fp32 en cada operación -- más lento que usar fp32
    # directamente, no más rápido (se midió en esta misma máquina: más de
    # una hora sin completar el primer paso en bf16). device_map="cpu" evita
    # el mismo mmap problemático de accelerate en Windows que "auto" dispara
    # (ver nota arriba).
    modelo = AutoModelForCausalLM.from_pretrained(
        MODELO_BASE,
        torch_dtype=torch.float32,
        device_map="cpu",
        token=HF_TOKEN,
    )

# --- 3. Configuración LoRA ---
# r=16, alpha=32 (relación 1:2, estándar recomendado), dropout=0.05: valores
# conservadores apropiados para un dataset pequeño (42 ejemplos de train) --
# un r más alto sobreajustaría con tan pocos datos. target_modules cubre las
# proyecciones de atención, donde LoRA suele dar más beneficio por parámetro
# entrenado.
lora_config = LoraConfig(
    task_type=TaskType.CAUSAL_LM,
    r=16,
    lora_alpha=32,
    lora_dropout=0.05,
    target_modules=["q_proj", "k_proj", "v_proj", "o_proj"],
    bias="none",
)

modelo = get_peft_model(modelo, lora_config)
modelo.print_trainable_parameters()

# --- 4. Entrenamiento ---
args_entrenamiento = TrainingArguments(
    output_dir=RUTA_SALIDA,
    num_train_epochs=3,
    per_device_train_batch_size=2,
    gradient_accumulation_steps=4,
    learning_rate=2e-4,
    logging_steps=1,
    eval_strategy="epoch",
    save_strategy="epoch",
    save_total_limit=1,
    bf16=CUDA_DISPONIBLE,  # en CPU se entrena en fp32 (ver nota arriba)
    # bf16=True sin GPU exige declarar explícitamente use_cpu=True, si no
    # transformers asume que bf16 implica GPU y aborta con ValueError.
    use_cpu=not CUDA_DISPONIBLE,
    report_to="none",
)

collator = DataCollatorForLanguageModeling(tokenizer=tokenizer, mlm=False)

trainer = Trainer(
    model=modelo,
    args=args_entrenamiento,
    train_dataset=ds_train,
    eval_dataset=ds_val,
    data_collator=collator,
)

print("\n=== Iniciando entrenamiento LoRA ===")
resultado = trainer.train()
print(resultado)

modelo.save_pretrained(RUTA_SALIDA)
tokenizer.save_pretrained(RUTA_SALIDA)
print(f"\nAdaptador LoRA guardado en: {RUTA_SALIDA}")
