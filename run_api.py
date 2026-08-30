"""
Lanzador del backend de "Barrio Seguro".

El proyecto quedo con las dependencias repartidas en dos entornos virtuales:

  - venv/          -> fastapi, uvicorn, pydantic
  - Agente/.venv/  -> geopandas, pandas, shapely, numpy, jenkspy, odfpy

Los dos son Python 3.14, asi que este script une los site-packages en sys.path
y arranca uvicorn, sin necesidad de reinstalar nada. Si algun dia se unifican
los entornos (pip install -r Agente/requirements.txt dentro de venv), este
script sigue funcionando igual: el site-packages extra solo se agrega si falta
alguna dependencia.

Uso:
    venv\\Scripts\\python.exe run_api.py
    venv\\Scripts\\python.exe run_api.py --port 8001 --no-reload
"""

import argparse
import importlib.util
import os
import sys

BASE_DIR = os.path.dirname(os.path.abspath(__file__))
EXTRA_SITE_PACKAGES = [
    os.path.join(BASE_DIR, "Agente", ".venv", "Lib", "site-packages"),
    os.path.join(BASE_DIR, "venv", "Lib", "site-packages"),
]
REQUERIDOS = ("geopandas", "pandas", "shapely", "fastapi", "uvicorn", "pydantic")


def _completar_sys_path() -> None:
    """Agrega los site-packages del otro venv si falta alguna dependencia."""
    if BASE_DIR not in sys.path:
        sys.path.insert(0, BASE_DIR)
    for ruta in EXTRA_SITE_PACKAGES:
        if not os.path.isdir(ruta) or ruta in sys.path:
            continue
        if all(importlib.util.find_spec(m) is not None for m in REQUERIDOS):
            return
        sys.path.append(ruta)


def _verificar_dependencias() -> None:
    faltantes = [m for m in REQUERIDOS if importlib.util.find_spec(m) is None]
    if faltantes:
        sys.exit(
            "Faltan dependencias: "
            + ", ".join(faltantes)
            + "\nInstalalas con: venv\\Scripts\\python.exe -m pip install -r Agente/requirements.txt fastapi uvicorn"
        )


def main() -> None:
    parser = argparse.ArgumentParser(description="Arranca la API de Barrio Seguro.")
    parser.add_argument("--host", default="0.0.0.0", help="por defecto 0.0.0.0 (visible para el emulador)")
    parser.add_argument("--port", type=int, default=8000)
    parser.add_argument("--no-reload", action="store_true", help="desactiva el autorecargado")
    args = parser.parse_args()

    _completar_sys_path()
    _verificar_dependencias()

    import uvicorn

    uvicorn.run(
        "Api.backend_riesgo:app",
        host=args.host,
        port=args.port,
        reload=not args.no_reload,
        reload_dirs=[os.path.join(BASE_DIR, "Api")],
    )


if __name__ == "__main__":
    main()
