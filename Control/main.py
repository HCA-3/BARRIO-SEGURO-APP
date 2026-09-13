#!/usr/bin/env python3
"""
Punto de entrada principal para lanzar la Aplicación de Escritorio Barrio Seguro Control.
"""

import sys
import os

BASE_DIR = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
sys.path.insert(0, BASE_DIR)

from Control.app_control import main

if __name__ == "__main__":
    main()
