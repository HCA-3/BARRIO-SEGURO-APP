"""Resume docs/pruebas/CP-16_registro.csv: latencia promedio, minima, maxima,
y cuantos cruces quedaron por debajo del umbral objetivo de latencia de
notificacion.

UMBRAL: no se encontro un valor formalmente definido en el codigo ni en la
documentacion del equipo para "latencia de notificacion por geolocalizacion"
(lo que SI existe es el timeout de 100s del chat con Ollama, que es un
requisito distinto -- ver Api/backend_riesgo.py). Por eso este script usa
10 segundos como supuesto, tal como se le indico. Si el equipo define un
valor real, cambiar UMBRAL_SEGUNDOS_SUPUESTO abajo.
"""
import csv
import os
import statistics
import sys

UMBRAL_SEGUNDOS_SUPUESTO = 10.0  # SUPUESTO, no confirmado por el equipo/documentacion

RUTA_CSV = os.path.join(os.path.dirname(__file__), "CP-16_registro.csv")


def main() -> None:
    if not os.path.exists(RUTA_CSV):
        print(f"No encontre {RUTA_CSV}")
        sys.exit(1)

    latencias = []
    filas_con_datos = 0
    filas_vacias = 0

    with open(RUTA_CSV, encoding="utf-8") as f:
        lector = csv.DictReader(f)
        for fila in lector:
            valor = (fila.get("latencia_s") or "").strip()
            if not valor:
                filas_vacias += 1
                continue
            try:
                latencias.append(float(valor))
                filas_con_datos += 1
            except ValueError:
                print(f"Fila {fila.get('n')}: 'latencia_s'={valor!r} no es un numero, se ignora.")

    print(f"Filas con datos: {filas_con_datos} / Filas vacias (aun no registradas): {filas_vacias}")

    if not latencias:
        print("No hay ninguna latencia registrada todavia -- llena CP-16_registro.csv primero.")
        return

    promedio = statistics.mean(latencias)
    minima = min(latencias)
    maxima = max(latencias)
    bajo_umbral = sum(1 for l in latencias if l <= UMBRAL_SEGUNDOS_SUPUESTO)

    print(f"\nUmbral usado: {UMBRAL_SEGUNDOS_SUPUESTO:.1f}s (SUPUESTO, no confirmado por el equipo)")
    print(f"Latencia promedio: {promedio:.2f}s")
    print(f"Latencia minima:   {minima:.2f}s")
    print(f"Latencia maxima:   {maxima:.2f}s")
    print(f"Cruces por debajo del umbral: {bajo_umbral}/{len(latencias)} ({bajo_umbral/len(latencias)*100:.0f}%)")


if __name__ == "__main__":
    main()
