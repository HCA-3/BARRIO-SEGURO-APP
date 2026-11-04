"""Resume docs/pruebas/CP-16_registro.csv: latencia promedio, minima, maxima,
y cuantos cruces quedaron por debajo del umbral objetivo de latencia de
notificacion.

UMBRAL: RNF-12 del Anexo B v3 ("La alerta debe llegar mientras la persona
sigue en la zona") exige "alerta en <= 1 intervalo de muestreo (2 min por
defecto)". El intervalo de 2 min coincide con INTERVALO_MS en
app/.../location/MonitoreoUbicacionService.kt. Por eso el umbral es 120
segundos (no es un supuesto -- ver RNF-12, Anexo B v3, Tabla B.2).
"""
import csv
import os
import statistics
import sys

UMBRAL_SEGUNDOS = 120.0  # RNF-12 (Anexo B v3): alerta en <= 1 intervalo de muestreo (2 min por defecto)

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
    bajo_umbral = sum(1 for l in latencias if l <= UMBRAL_SEGUNDOS)

    print(f"\nUmbral usado: {UMBRAL_SEGUNDOS:.1f}s (RNF-12, Anexo B v3: alerta en <= 1 intervalo de muestreo)")
    print(f"Latencia promedio: {promedio:.2f}s")
    print(f"Latencia minima:   {minima:.2f}s")
    print(f"Latencia maxima:   {maxima:.2f}s")
    print(f"Cruces por debajo del umbral: {bajo_umbral}/{len(latencias)} ({bajo_umbral/len(latencias)*100:.0f}%)")


if __name__ == "__main__":
    main()
