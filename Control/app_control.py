"""
Aplicación de Escritorio - Barrio Seguro Panel de Control y Monitoreo
=====================================================================
Desarrollada en PyQt6. Unifica todos los controles de encendido/apagado en una
sola pestaña (Backend API, Túnel Cloudflare para Internet remoto, Motor de IA
Ollama) y proporciona herramientas de monitoreo en tiempo real, estadísticas de
rendimiento y explorador de datos almacenados.
"""

import json
import os
import sys
import subprocess
import time
from typing import Any

import requests

from PyQt6.QtCore import QThread, QTimer, Qt, pyqtSignal
from PyQt6.QtGui import QColor, QFont, QIcon, QTextCursor
from PyQt6.QtWidgets import (
    QApplication,
    QComboBox,
    QFrame,
    QGroupBox,
    QHBoxLayout,
    QHeaderView,
    QLabel,
    QLineEdit,
    QMainWindow,
    QMessageBox,
    QProgressBar,
    QPushButton,
    QSplitter,
    QTabWidget,
    QTableWidget,
    QTableWidgetItem,
    QTextEdit,
    QVBoxLayout,
    QWidget,
)

BASE_DIR = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))

def obtener_api_url() -> str:
    for port in (8001, 8000):
        url = f"http://127.0.0.1:{port}"
        try:
            r = requests.get(f"{url}/health", timeout=0.5)
            if r.status_code == 200:
                return url
        except Exception:
            pass
    return "http://127.0.0.1:8001"

API_URL = obtener_api_url()
RUN_API_SCRIPT = os.path.join(BASE_DIR, "run_api.py")
VENV_PYTHON = os.path.join(BASE_DIR, "venv", "bin", "python")
if not os.path.exists(VENV_PYTHON):
    VENV_PYTHON = sys.executable

ZONAS_PATH = os.path.join(BASE_DIR, "Agente", "output", "zonas_riesgo.json")
UPZ_PATH = os.path.join(BASE_DIR, "Agente", "output", "upz_riesgo.json")


class ProcessLogWorker(QThread):
    log_signal = pyqtSignal(str)

    def __init__(self, process: subprocess.Popen):
        super().__init__()
        self.process = process
        self.running = True

    def run(self):
        if not self.process or not self.process.stdout:
            return
        while self.running and self.process.poll() is None:
            line = self.process.stdout.readline()
            if line:
                self.log_signal.emit(line)
            else:
                time.sleep(0.05)

    def stop(self):
        self.running = False


class MainWindow(QMainWindow):
    def __init__(self):
        super().__init__()
        self.backend_process: subprocess.Popen | None = None
        self.log_worker: ProcessLogWorker | None = None
        self.current_tunnel_url = ""

        self.setWindowTitle("🛡️ Barrio Seguro — Centro de Control de Servicios & Monitoreo")
        self.resize(1200, 850)
        self.setMinimumSize(950, 680)

        self.apply_dark_theme()
        self.init_ui()

        # Timers para actualización en tiempo real
        self.timer_metrics = QTimer(self)
        self.timer_metrics.timeout.connect(self.actualizar_telemetria)
        self.timer_metrics.start(2000)

        self.timer_activity = QTimer(self)
        self.timer_activity.timeout.connect(self.actualizar_actividad)
        self.timer_activity.start(2000)

        # Cargar datos iniciales
        self.actualizar_telemetria()
        self.cargar_datos_almacenados()

    def apply_dark_theme(self):
        dark_style = """
        QMainWindow {
            background-color: #0f172a;
            color: #f8fafc;
        }
        QWidget {
            background-color: #0f172a;
            color: #f8fafc;
            font-family: 'Segoe UI', 'Ubuntu', sans-serif;
            font-size: 13px;
        }
        QTabWidget::pane {
            border: 1px solid #334155;
            background: #1e293b;
            border-radius: 8px;
        }
        QTabBar::tab {
            background: #0f172a;
            color: #94a3b8;
            padding: 10px 18px;
            margin-right: 4px;
            border-top-left-radius: 6px;
            border-top-right-radius: 6px;
            font-weight: bold;
        }
        QTabBar::tab:selected {
            background: #1e293b;
            color: #00e5ff;
            border-bottom: 3px solid #00e5ff;
        }
        QTabBar::tab:hover {
            color: #f8fafc;
        }
        QGroupBox {
            font-weight: bold;
            border: 1px solid #334155;
            border-radius: 8px;
            margin-top: 12px;
            padding-top: 16px;
            background-color: #1e293b;
        }
        QGroupBox::title {
            subcontrol-origin: margin;
            subcontrol-position: top left;
            padding: 2px 8px;
            color: #00e5ff;
        }
        QPushButton {
            background-color: #334155;
            color: #f8fafc;
            border: 1px solid #475569;
            padding: 8px 16px;
            border-radius: 6px;
            font-weight: bold;
        }
        QPushButton:hover {
            background-color: #475569;
            border-color: #00e5ff;
        }
        QPushButton:pressed {
            background-color: #0f172a;
        }
        QPushButton#btn_start {
            background-color: #059669;
            border-color: #10b981;
        }
        QPushButton#btn_start:hover {
            background-color: #10b981;
        }
        QPushButton#btn_stop {
            background-color: #dc2626;
            border-color: #ef4444;
        }
        QPushButton#btn_stop:hover {
            background-color: #ef4444;
        }
        QPushButton#btn_restart {
            background-color: #d97706;
            border-color: #f59e0b;
        }
        QPushButton#btn_restart:hover {
            background-color: #f59e0b;
        }
        QPushButton#btn_test {
            background-color: #2563eb;
            border-color: #3b82f6;
        }
        QPushButton#btn_test:hover {
            background-color: #3b82f6;
        }
        QTableWidget {
            background-color: #0f172a;
            alternate-background-color: #1e293b;
            gridline-color: #334155;
            border: 1px solid #334155;
            border-radius: 6px;
        }
        QTableWidget::item {
            padding: 6px;
        }
        QHeaderView::section {
            background-color: #1e293b;
            color: #00e5ff;
            padding: 6px;
            font-weight: bold;
            border: 1px solid #334155;
        }
        QTextEdit, QLineEdit {
            background-color: #020617;
            color: #00e5ff;
            border: 1px solid #334155;
            border-radius: 6px;
            font-family: 'Consolas', 'Courier New', monospace;
            padding: 6px;
        }
        QProgressBar {
            border: 1px solid #334155;
            border-radius: 6px;
            text-align: center;
            background-color: #020617;
            color: #f8fafc;
            font-weight: bold;
        }
        QProgressBar::chunk {
            background-color: #00e5ff;
            border-radius: 5px;
        }
        """
        self.setStyleSheet(dark_style)

    def init_ui(self):
        main_widget = QWidget()
        self.setCentralWidget(main_widget)
        main_layout = QVBoxLayout(main_widget)

        # Header Superior
        header_frame = QFrame()
        header_frame.setStyleSheet("background-color: #1e293b; border-radius: 8px; border: 1px solid #334155;")
        header_layout = QHBoxLayout(header_frame)

        title_label = QLabel("🛡️ BARRIO SEGURO — CENTRO UNIFICADO DE CONTROL")
        title_label.setFont(QFont("Segoe UI", 15, QFont.Weight.Bold))
        title_label.setStyleSheet("color: #00e5ff; border: none;")

        self.lbl_server_status_badge = QLabel("ESTADO: CONSULTANDO...")
        self.lbl_server_status_badge.setFont(QFont("Segoe UI", 11, QFont.Weight.Bold))
        self.lbl_server_status_badge.setStyleSheet(
            "background-color: #334155; color: #f8fafc; padding: 6px 14px; border-radius: 12px; border: none;"
        )

        self.lbl_active_users_badge = QLabel("👥 0 USUARIOS EN TIEMPO REAL")
        self.lbl_active_users_badge.setFont(QFont("Segoe UI", 11, QFont.Weight.Bold))
        self.lbl_active_users_badge.setStyleSheet(
            "background-color: #334155; color: #00e5ff; padding: 6px 14px; border-radius: 12px; border: 1px solid #00e5ff;"
        )

        header_layout.addWidget(title_label)
        header_layout.addStretch()
        header_layout.addWidget(self.lbl_active_users_badge)
        header_layout.addWidget(self.lbl_server_status_badge)

        main_layout.addWidget(header_frame)

        # Pestanas
        self.tabs = QTabWidget()
        main_layout.addWidget(self.tabs)

        self.tab_services = QWidget()
        self.tab_monitoring = QWidget()
        self.tab_performance = QWidget()
        self.tab_data_explorer = QWidget()

        self.tabs.addTab(self.tab_services, "⚡ Centro de Encendido & Servicios")
        self.tabs.addTab(self.tab_monitoring, "📡 Actividad en Tiempo Real")
        self.tabs.addTab(self.tab_performance, "📈 Rendimiento & Capacidad")
        self.tabs.addTab(self.tab_data_explorer, "🗄️ Explorador de Datos")

        self.setup_tab_services()
        self.setup_tab_monitoring()
        self.setup_tab_performance()
        self.setup_tab_data_explorer()

    # -----------------------------------------------------------------------
    # TAB 1: PESTANA UNICA DE CONTROL Y ENCENDIDO DE TODOS LOS SERVICIOS
    # -----------------------------------------------------------------------
    def setup_tab_services(self):
        layout = QVBoxLayout(self.tab_services)

        # Fila superior de tarjetas con interruptores de encendido/apagado
        services_grid = QHBoxLayout()

        # 1. SERVIDOR BACKEND API
        card_api = QGroupBox("🚀 Servidor API Backend")
        api_box = QVBoxLayout(card_api)

        self.lbl_api_status = QLabel("Estado: Detenido")
        self.lbl_api_status.setFont(QFont("Segoe UI", 11, QFont.Weight.Bold))
        self.lbl_api_details = QLabel("Puerto: 8001 | PID: -")

        btn_api_layout = QHBoxLayout()
        self.btn_api_start = QPushButton("▶️ Encender API")
        self.btn_api_start.setObjectName("btn_start")
        self.btn_api_start.clicked.connect(self.start_backend)

        self.btn_api_stop = QPushButton("⏹️ Apagar API")
        self.btn_api_stop.setObjectName("btn_stop")
        self.btn_api_stop.clicked.connect(self.stop_backend)

        self.btn_api_restart = QPushButton("🔄 Reiniciar")
        self.btn_api_restart.setObjectName("btn_restart")
        self.btn_api_restart.clicked.connect(self.restart_backend)

        btn_api_layout.addWidget(self.btn_api_start)
        btn_api_layout.addWidget(self.btn_api_stop)
        btn_api_layout.addWidget(self.btn_api_restart)

        api_box.addWidget(self.lbl_api_status)
        api_box.addWidget(self.lbl_api_details)
        api_box.addLayout(btn_api_layout)

        # 2. TUNEL PUBLICO CLOUDFLARE (INTERNET REMOTO)
        card_tunnel = QGroupBox("🌐 Túnel Público Cloudflare (Internet / Remoto)")
        tunnel_box = QVBoxLayout(card_tunnel)

        self.lbl_tunnel_status = QLabel("Estado: Inactivo")
        self.lbl_tunnel_status.setFont(QFont("Segoe UI", 11, QFont.Weight.Bold))
        self.lbl_tunnel_url = QLabel("URL Pública: Consultando...")
        self.lbl_tunnel_url.setStyleSheet("color: #00e5ff; font-family: monospace;")

        btn_tunnel_layout = QHBoxLayout()
        self.btn_tunnel_start = QPushButton("🌐 Encender Túnel")
        self.btn_tunnel_start.setObjectName("btn_start")
        self.btn_tunnel_start.clicked.connect(self.start_tunnel)

        self.btn_tunnel_stop = QPushButton("🛑 Apagar Túnel")
        self.btn_tunnel_stop.setObjectName("btn_stop")
        self.btn_tunnel_stop.clicked.connect(self.stop_tunnel)

        self.btn_tunnel_copy = QPushButton("📋 Copiar URL")
        self.btn_tunnel_copy.clicked.connect(self.copiar_tunnel_url)

        self.btn_tunnel_test = QPushButton("🧪 Probar Conexión")
        self.btn_tunnel_test.setObjectName("btn_test")
        self.btn_tunnel_test.clicked.connect(self.probar_conexion_internet)

        btn_tunnel_layout.addWidget(self.btn_tunnel_start)
        btn_tunnel_layout.addWidget(self.btn_tunnel_stop)
        btn_tunnel_layout.addWidget(self.btn_tunnel_copy)
        btn_tunnel_layout.addWidget(self.btn_tunnel_test)

        tunnel_box.addWidget(self.lbl_tunnel_status)
        tunnel_box.addWidget(self.lbl_tunnel_url)
        tunnel_box.addLayout(btn_tunnel_layout)

        # 3. MOTOR DE IA OLLAMA LOCAL
        card_ollama = QGroupBox("🧠 Motor de IA Ollama (LLM)")
        ollama_box = QVBoxLayout(card_ollama)

        self.lbl_ollama_status = QLabel("Estado: Verificando...")
        self.lbl_ollama_status.setFont(QFont("Segoe UI", 11, QFont.Weight.Bold))
        self.lbl_ollama_model = QLabel("Modelo: llama3.2:3b (CPU)")

        btn_ollama_layout = QHBoxLayout()
        self.btn_ollama_start = QPushButton("🧠 Encender IA")
        self.btn_ollama_start.setObjectName("btn_start")
        self.btn_ollama_start.clicked.connect(self.start_ollama)

        self.btn_ollama_stop = QPushButton("🛑 Apagar IA")
        self.btn_ollama_stop.setObjectName("btn_stop")
        self.btn_ollama_stop.clicked.connect(self.stop_ollama)

        btn_ollama_layout.addWidget(self.btn_ollama_start)
        btn_ollama_layout.addWidget(self.btn_ollama_stop)

        ollama_box.addWidget(self.lbl_ollama_status)
        ollama_box.addWidget(self.lbl_ollama_model)
        ollama_box.addLayout(btn_ollama_layout)

        services_grid.addWidget(card_api, 1)
        services_grid.addWidget(card_tunnel, 1)
        services_grid.addWidget(card_ollama, 1)

        layout.addLayout(services_grid)

        # 4. MONITOR DE USUARIOS EN TIEMPO REAL
        card_users = QGroupBox("👥 USUARIOS USANDO EL SERVIDOR EN TIEMPO REAL")
        card_users.setStyleSheet("QGroupBox { border: 2px solid #00e5ff; font-weight: bold; background-color: #0f172a; border-radius: 8px; }")
        users_box = QHBoxLayout(card_users)

        self.lbl_realtime_users_large = QLabel("0 PERSONAS CONECTADAS")
        self.lbl_realtime_users_large.setFont(QFont("Segoe UI", 15, QFont.Weight.Bold))
        self.lbl_realtime_users_large.setStyleSheet("color: #00e676;")

        self.lbl_realtime_users_details = QLabel("Peticiones Activas: 0 | Dispositivos Activos: Ninguno")
        self.lbl_realtime_users_details.setFont(QFont("Segoe UI", 11))
        self.lbl_realtime_users_details.setStyleSheet("color: #94a3b8;")

        users_box.addWidget(self.lbl_realtime_users_large)
        users_box.addStretch()
        users_box.addWidget(self.lbl_realtime_users_details)

        layout.addWidget(card_users)

        # Consola de Logs y Salida de Procesos Unificada
        log_group = QGroupBox("Consola de Salida y Logs en Tiempo Real (stdout / stderr)")
        log_box = QVBoxLayout(log_group)

        self.txt_logs = QTextEdit()
        self.txt_logs.setReadOnly(True)
        log_box.addWidget(self.txt_logs)

        log_ctrl = QHBoxLayout()
        self.btn_clear_logs = QPushButton("🧹 Limpiar Consola")
        self.btn_clear_logs.clicked.connect(self.clear_logs)

        self.btn_refresh_now = QPushButton("⚡ Actualizar Estado de Servicios")
        self.btn_refresh_now.clicked.connect(self.actualizar_telemetria)

        log_ctrl.addWidget(self.btn_clear_logs)
        log_ctrl.addStretch()
        log_ctrl.addWidget(self.btn_refresh_now)

        log_box.addLayout(log_ctrl)
        layout.addWidget(log_group)

    # -----------------------------------------------------------------------
    # TAB 2: MONITOREO EN TIEMPO REAL
    # -----------------------------------------------------------------------
    def setup_tab_monitoring(self):
        layout = QVBoxLayout(self.tab_monitoring)

        filter_box = QHBoxLayout()
        filter_box.addWidget(QLabel("Filtrar Endpoint / IP:"))
        self.txt_mon_filter = QLineEdit()
        self.txt_mon_filter.setPlaceholderText("Ej. /chat, /riesgo, 127.0.0.1...")
        self.txt_mon_filter.textChanged.connect(self.actualizar_actividad)
        filter_box.addWidget(self.txt_mon_filter)

        self.lbl_mon_stats = QLabel("Total Peticiones: 0 | Errores: 0")
        self.lbl_mon_stats.setStyleSheet("color: #00e5ff; font-weight: bold;")
        filter_box.addStretch()
        filter_box.addWidget(self.lbl_mon_stats)

        layout.addLayout(filter_box)

        self.table_actividad = QTableWidget(0, 6)
        self.table_actividad.setHorizontalHeaderLabels([
            "Hora", "Método", "Endpoint / Path", "IP Cliente", "Estado HTTP", "Latencia (ms)"
        ])
        self.table_actividad.horizontalHeader().setSectionResizeMode(QHeaderView.ResizeMode.Stretch)
        layout.addWidget(self.table_actividad)

    # -----------------------------------------------------------------------
    # TAB 3: RENDIMIENTO & CAPACIDAD
    # -----------------------------------------------------------------------
    def setup_tab_performance(self):
        layout = QVBoxLayout(self.tab_performance)

        # Sección de Capacidad Estimada
        cap_group = QGroupBox("Calculadora de Capacidad Máxima de Usuarios Simultáneos")
        cap_layout = QVBoxLayout(cap_group)

        self.lbl_perf_cap_num = QLabel("ESTIMACIÓN: +0 Usuarios Adicionales")
        self.lbl_perf_cap_num.setFont(QFont("Segoe UI", 20, QFont.Weight.Bold))
        self.lbl_perf_cap_num.setStyleSheet("color: #00e676;")

        self.lbl_perf_cap_detail = QLabel("Cargando detalles de capacidad...")
        self.lbl_perf_cap_detail.setFont(QFont("Segoe UI", 12))

        cap_layout.addWidget(self.lbl_perf_cap_num)
        cap_layout.addWidget(self.lbl_perf_cap_detail)

        layout.addWidget(cap_group)

        # Recursos del Sistema & Detalles
        sys_group = QGroupBox("Métricas de Recursos del Hardware y Servidor")
        sys_layout = QVBoxLayout(sys_group)

        self.lbl_perf_cpu_detail = QLabel("Uso de CPU: 0%")
        self.pb_perf_cpu = QProgressBar()

        self.lbl_perf_ram_detail = QLabel("Memoria RAM Libre: 0 MB / 0 MB")
        self.pb_perf_ram = QProgressBar()

        self.lbl_perf_ollama_detail = QLabel("Ollama LLM: Pendiente")
        self.lbl_perf_latency_detail = QLabel("Latencia Promedio: 0 ms")

        sys_layout.addWidget(self.lbl_perf_cpu_detail)
        sys_layout.addWidget(self.pb_perf_cpu)
        sys_layout.addWidget(self.lbl_perf_ram_detail)
        sys_layout.addWidget(self.pb_perf_ram)
        sys_layout.addWidget(self.lbl_perf_ollama_detail)
        sys_layout.addWidget(self.lbl_perf_latency_detail)

        layout.addWidget(sys_group)
        layout.addStretch()

    # -----------------------------------------------------------------------
    # TAB 4: EXPLORADOR DE DATOS ALMACENADOS
    # -----------------------------------------------------------------------
    def setup_tab_data_explorer(self):
        layout = QVBoxLayout(self.tab_data_explorer)

        self.data_tabs = QTabWidget()
        layout.addWidget(self.data_tabs)

        # Subtab 1: Zonas de Riesgo
        self.subtab_zonas = QWidget()
        sz_layout = QVBoxLayout(self.subtab_zonas)
        sz_filter = QHBoxLayout()
        sz_filter.addWidget(QLabel("Buscar Localidad:"))
        self.txt_search_zona = QLineEdit()
        self.txt_search_zona.textChanged.connect(self.filtrar_zonas)
        sz_filter.addWidget(self.txt_search_zona)
        sz_layout.addLayout(sz_filter)

        self.table_zonas = QTableWidget(0, 6)
        self.table_zonas.setHorizontalHeaderLabels([
            "Código", "Localidad", "Nivel Riesgo", "Score Mixto", "Población", "Estrato Promedio"
        ])
        self.table_zonas.horizontalHeader().setSectionResizeMode(QHeaderView.ResizeMode.Stretch)
        sz_layout.addWidget(self.table_zonas)

        # Subtab 2: Mensajes de la Comunidad
        self.subtab_comunidad = QWidget()
        sc_layout = QVBoxLayout(self.subtab_comunidad)
        sc_ctrl = QHBoxLayout()
        self.btn_refresh_com = QPushButton("🔄 Actualizar Mensajes")
        self.btn_refresh_com.clicked.connect(self.cargar_mensajes_comunidad)
        self.btn_delete_com = QPushButton("🗑️ Eliminar Mensaje Seleccionado")
        self.btn_delete_com.setObjectName("btn_stop")
        self.btn_delete_com.clicked.connect(self.eliminar_mensaje_comunidad)
        sc_ctrl.addWidget(self.btn_refresh_com)
        sc_ctrl.addWidget(self.btn_delete_com)
        sc_ctrl.addStretch()
        sc_layout.addLayout(sc_ctrl)

        self.table_comunidad = QTableWidget(0, 6)
        self.table_comunidad.setHorizontalHeaderLabels([
            "ID", "Alias / Usuario", "Localidad", "Mensaje", "Alerta", "Fecha / Hora"
        ])
        self.table_comunidad.horizontalHeader().setSectionResizeMode(QHeaderView.ResizeMode.Stretch)
        sc_layout.addWidget(self.table_comunidad)

        # Subtab 3: Sismos Recientes
        self.subtab_sismos = QWidget()
        ss_layout = QVBoxLayout(self.subtab_sismos)
        self.btn_refresh_sismos = QPushButton("🔄 Actualizar Sismos")
        self.btn_refresh_sismos.clicked.connect(self.cargar_sismos)
        ss_layout.addWidget(self.btn_refresh_sismos)

        self.table_sismos = QTableWidget(0, 5)
        self.table_sismos.setHorizontalHeaderLabels([
            "Magnitud", "Lugar / Ubicación", "Distancia a Bogotá", "Profundidad", "Hora"
        ])
        self.table_sismos.horizontalHeader().setSectionResizeMode(QHeaderView.ResizeMode.Stretch)
        ss_layout.addWidget(self.table_sismos)

        self.data_tabs.addTab(self.subtab_zonas, "🏙️ Zonas de Riesgo")
        self.data_tabs.addTab(self.subtab_comunidad, "💬 Chat Comunidad & Alertas")
        self.data_tabs.addTab(self.subtab_sismos, "🌋 Sismos Recientes")

    # -----------------------------------------------------------------------
    # GESTION UNIFICADA DE SERVICIOS (START / STOP / RESTART & TUNNEL & OLLAMA)
    # -----------------------------------------------------------------------
    def start_backend(self):
        self.txt_logs.append(">>> Encendiendo Servidor Backend API...")
        res = subprocess.run(["systemctl", "--user", "start", "barrio-seguro-api.service"], capture_output=True, text=True)
        if res.returncode == 0:
            self.txt_logs.append(">>> Servidor Backend iniciado exitosamente vía systemd.")
        else:
            try:
                self.txt_logs.append(">>> Iniciando proceso directo del Backend API...")
                self.backend_process = subprocess.Popen(
                    [VENV_PYTHON, RUN_API_SCRIPT, "--no-reload"],
                    stdout=subprocess.PIPE,
                    stderr=subprocess.STDOUT,
                    text=True,
                    bufsize=1,
                    cwd=BASE_DIR
                )
                self.log_worker = ProcessLogWorker(self.backend_process)
                self.log_worker.log_signal.connect(self.append_log)
                self.log_worker.start()
                self.txt_logs.append(">>> Proceso directo del backend iniciado con PID " + str(self.backend_process.pid))
            except Exception as e:
                QMessageBox.critical(self, "Error al Iniciar", f"No se pudo iniciar el servidor:\n{e}")

        QTimer.singleShot(1500, self.actualizar_telemetria)

    def stop_backend(self):
        self.txt_logs.append(">>> Apagando Servidor Backend API...")
        subprocess.run(["systemctl", "--user", "stop", "barrio-seguro-api.service"], capture_output=True, text=True)

        if self.backend_process and self.backend_process.poll() is None:
            self.backend_process.terminate()
            try:
                self.backend_process.wait(timeout=3)
            except subprocess.TimeoutExpired:
                self.backend_process.kill()
            self.backend_process = None
            if self.log_worker:
                self.log_worker.stop()
                self.log_worker = None

        self.txt_logs.append(">>> Servidor Backend detenido.")
        QTimer.singleShot(1000, self.actualizar_telemetria)

    def restart_backend(self):
        self.stop_backend()
        QTimer.singleShot(1500, self.start_backend)

    def start_tunnel(self):
        self.txt_logs.append(">>> Encendiendo Túnel Público Cloudflare (Internet Remoto)...")
        res = subprocess.run(["systemctl", "--user", "start", "barrio-seguro-tunnel.service"], capture_output=True, text=True)
        if res.returncode == 0:
            self.txt_logs.append(">>> Túnel Público Cloudflare iniciado exitosamente.")
        else:
            QMessageBox.warning(self, "Error de Túnel", "No se pudo iniciar el servicio del túnel Cloudflare.")
        QTimer.singleShot(2000, self.actualizar_telemetria)

    def stop_tunnel(self):
        self.txt_logs.append(">>> Apagando Túnel Público Cloudflare...")
        subprocess.run(["systemctl", "--user", "stop", "barrio-seguro-tunnel.service"], capture_output=True, text=True)
        self.txt_logs.append(">>> Túnel Público Cloudflare apagado.")
        QTimer.singleShot(1000, self.actualizar_telemetria)

    def start_ollama(self):
        self.txt_logs.append(">>> Encendiendo Motor de IA Ollama (ollama.service)...")
        subprocess.run(["systemctl", "--user", "start", "ollama.service"], capture_output=True, text=True)
        self.txt_logs.append(">>> Servicio Ollama iniciado.")
        QTimer.singleShot(1500, self.actualizar_telemetria)

    def stop_ollama(self):
        self.txt_logs.append(">>> Apagando Motor de IA Ollama...")
        subprocess.run(["systemctl", "--user", "stop", "ollama.service"], capture_output=True, text=True)
        self.txt_logs.append(">>> Servicio Ollama detenido.")
        QTimer.singleShot(1000, self.actualizar_telemetria)

    def copiar_tunnel_url(self):
        url = getattr(self, "current_tunnel_url", "")
        if url and url.startswith("https://"):
            QApplication.clipboard().setText(url)
            QMessageBox.information(self, "URL Copiada", f"URL del Túnel copiada al portapapeles:\n{url}")
        else:
            QMessageBox.warning(self, "Sin Túnel Activo", "No hay ningún túnel Cloudflare activo en este momento.")

    def probar_conexion_internet(self):
        url = getattr(self, "current_tunnel_url", "")
        if not url or not url.startswith("https://"):
            QMessageBox.warning(self, "Túnel Inactivo", "Debes encender el Túnel Cloudflare antes de probar la conexión de Internet.")
            return

        self.txt_logs.append(f">>> Probando conectividad remota vía Internet hacia: {url}/health ...")
        try:
            r = requests.get(f"{url}/health", timeout=6.0)
            if r.status_code == 200:
                data = r.json()
                QMessageBox.information(
                    self,
                    "🌐 Prueba de Internet Exitosa",
                    f"¡El túnel remoto responde correctamente desde Internet!\n\n"
                    f"URL Pública: {url}\n"
                    f"Estado Servidor: {data.get('status')}\n"
                    f"Localidades Cargadas: {data.get('localidades_cargadas')}\n"
                    f"Ollama IA: {'Conectado' if data.get('ollama_disponible') else 'Desconectado'}"
                )
                self.txt_logs.append(">>> Prueba de conexión pública exitosa: 200 OK.")
            else:
                QMessageBox.warning(self, "Error de Respuesta", f"El servidor respondió con código HTTP {r.status_code}")
        except Exception as e:
            QMessageBox.critical(self, "Fallo de Conexión Remota", f"No se pudo conectar a través de Internet:\n{e}")

    def append_log(self, text: str):
        self.txt_logs.moveCursor(QTextCursor.MoveOperation.End)
        self.txt_logs.insertPlainText(text)

    def clear_logs(self):
        self.txt_logs.clear()

    # -----------------------------------------------------------------------
    # ACTUALIZACION DE TELEMETRIA Y DETECCION DE SERVICIOS
    # -----------------------------------------------------------------------
    def actualizar_info_tunnel(self):
        try:
            active_check = subprocess.run(["systemctl", "--user", "is-active", "barrio-seguro-tunnel.service"], capture_output=True, text=True)
            is_active = active_check.returncode == 0 and active_check.stdout.strip() == "active"

            tunnel_url = ""
            res = subprocess.run(
                ["journalctl", "--user", "-u", "barrio-seguro-tunnel.service", "-n", "150", "--no-pager"],
                capture_output=True, text=True
            )
            if res.returncode == 0:
                for line in reversed(res.stdout.splitlines()):
                    if "trycloudflare.com" in line:
                        for part in line.split():
                            if "https://" in part and "trycloudflare.com" in part:
                                tunnel_url = part.strip("|").strip()
                                break
                    if tunnel_url:
                        break

            if is_active and tunnel_url:
                self.current_tunnel_url = tunnel_url
                self.lbl_tunnel_status.setText("Estado: 🟢 CONECTADO (En Línea por Internet)")
                self.lbl_tunnel_status.setStyleSheet("color: #00e676; font-weight: bold;")
                self.lbl_tunnel_url.setText(f"URL Pública: {tunnel_url}")
            elif is_active:
                self.lbl_tunnel_status.setText("Estado: 🟡 INICIANDO TÚNEL...")
                self.lbl_tunnel_status.setStyleSheet("color: #ffab00; font-weight: bold;")
                self.lbl_tunnel_url.setText("URL Pública: Generando enlace remoto...")
            else:
                self.current_tunnel_url = ""
                self.lbl_tunnel_status.setText("Estado: 🔴 INACTIVO")
                self.lbl_tunnel_status.setStyleSheet("color: #ffab00; font-weight: bold;")
                self.lbl_tunnel_url.setText("URL Pública: No disponible (Túnel Apagado)")
        except Exception:
            self.current_tunnel_url = ""
            self.lbl_tunnel_status.setText("Estado: 🔴 INACTIVO")
            self.lbl_tunnel_url.setText("URL Pública: No disponible")

    def actualizar_telemetria(self):
        global API_URL
        API_URL = obtener_api_url()
        self.actualizar_info_tunnel()

        try:
            resp = requests.get(f"{API_URL}/telemetria/resumen", timeout=1.5)
            if resp.status_code == 200:
                data = resp.json()

                # Telemetría de Usuarios en Tiempo Real
                usr_realtime = data.get("usuarios_activos_tiempo_real", 0)
                pet_activas = data.get("peticiones_activas", 0)
                ips_lista = data.get("ips_activas_lista", [])
                ips_str = ", ".join(ips_lista) if ips_lista else "Ninguno"
                tot_req = data.get("total_peticiones", 0)
                tot_err = data.get("total_errores", 0)

                # Badge Superior
                self.lbl_active_users_badge.setText(f"👥 {usr_realtime} USUARIO{'S' if usr_realtime != 1 else ''} EN VIVO")
                if usr_realtime > 0:
                    self.lbl_active_users_badge.setStyleSheet(
                        "background-color: #0284c7; color: #ffffff; padding: 6px 14px; border-radius: 12px; font-weight: bold;"
                    )
                else:
                    self.lbl_active_users_badge.setStyleSheet(
                        "background-color: #334155; color: #00e5ff; padding: 6px 14px; border-radius: 12px; border: 1px solid #00e5ff;"
                    )

                # Banner Tab 1
                self.lbl_realtime_users_large.setText(
                    f"{usr_realtime} PERSONA{'S' if usr_realtime != 1 else ''} USANDO EL SERVIDOR AHORA MISMO"
                )
                self.lbl_realtime_users_details.setText(
                    f"Peticiones HTTP Procesando en Vivo: {pet_activas} | IPs de Dispositivos Conectados: {ips_str}"
                )

                # Tab Monitoreo Stats
                self.lbl_mon_stats.setText(
                    f"👥 Usuarios en Tiempo Real: {usr_realtime} | Peticiones Activas: {pet_activas} | Total Peticiones: {tot_req} | Errores: {tot_err}"
                )

                # Badges y Estados
                self.lbl_server_status_badge.setText("● PLATAFORMA ONLINE")
                self.lbl_server_status_badge.setStyleSheet(
                    "background-color: #059669; color: #ffffff; padding: 6px 14px; border-radius: 12px;"
                )

                uptime_s = data.get("uptime_seg", 0)
                pid_str = str(self.backend_process.pid) if self.backend_process else "Systemd Service"

                self.lbl_api_status.setText("Estado: 🟢 ENCENDIDO")
                self.lbl_api_status.setStyleSheet("color: #00e676; font-weight: bold;")
                self.lbl_api_details.setText(f"Puerto: 8001 | Uptime: {uptime_s}s | PID: {pid_str}")

                cpu = data.get("cpu_pct", 0)
                ram_pct = data.get("ram_pct", 0)
                ram_free = data.get("ram_libre_mb", 0)
                ram_total = data.get("ram_total_mb", 0)

                ollama_on = data.get("ollama_disponible", False)
                if ollama_on:
                    self.lbl_ollama_status.setText("Estado: 🟢 DISPONIBLE")
                    self.lbl_ollama_status.setStyleSheet("color: #00e676; font-weight: bold;")
                else:
                    self.lbl_ollama_status.setText("Estado: 🔴 APAGADO")
                    self.lbl_ollama_status.setStyleSheet("color: #ff1744; font-weight: bold;")

                # Tab Performance
                cap = data.get("capacidad", {})
                usr_extra = cap.get("usuarios_adicionales_estimados", 0)
                estado_perf = cap.get("estado_rendimiento", "Excelente")

                self.lbl_perf_cap_num.setText(
                    f"CAPACIDAD: {usr_realtime} Usuarios Activos | +{usr_extra} Adicionales Soportados"
                )
                self.lbl_perf_cap_detail.setText(
                    f"Rendimiento actual: {estado_perf} | Límite por RAM Libre: {cap.get('limite_ram_usuarios', 0)} usuarios | "
                    f"Límite por CPU Headroom: {cap.get('limite_cpu_usuarios', 0)} usuarios"
                )
                self.lbl_perf_cpu_detail.setText(f"Uso de CPU: {cpu}%")
                self.pb_perf_cpu.setValue(int(cpu))

                self.lbl_perf_ram_detail.setText(f"Memoria RAM: Libre {ram_free} MB / Total {ram_total} MB ({ram_pct}%)")
                self.pb_perf_ram.setValue(int(ram_pct))

                self.lbl_perf_ollama_detail.setText(f"Ollama local (LLM): {'Disponible y activo' if ollama_on else 'No detectado'}")
                self.lbl_perf_latency_detail.setText(f"Latencia promedio de respuestas: {data.get('latencia_media_ms', 0)} ms")

                return
        except Exception:
            pass

        # Servidor fuera de línea
        self.lbl_active_users_badge.setText("👥 0 USUARIOS EN TIEMPO REAL")
        self.lbl_active_users_badge.setStyleSheet(
            "background-color: #334155; color: #94a3b8; padding: 6px 14px; border-radius: 12px;"
        )
        self.lbl_realtime_users_large.setText("0 PERSONAS CONECTADAS (Servidor Apagado)")
        self.lbl_realtime_users_details.setText("Enciende el Servidor API para iniciar el monitoreo de usuarios en tiempo real.")
        self.lbl_server_status_badge.setText("● PLATAFORMA OFFLINE")
        self.lbl_server_status_badge.setStyleSheet(
            "background-color: #dc2626; color: #ffffff; padding: 6px 14px; border-radius: 12px;"
        )
        self.lbl_api_status.setText("Estado: 🔴 APAGADO")
        self.lbl_api_status.setStyleSheet("color: #ff1744; font-weight: bold;")
        self.lbl_api_details.setText("Puerto: 8001 | Uptime: 0s | PID: -")

    # -----------------------------------------------------------------------
    # MONITOREO DE ACTIVIDAD EN TIEMPO REAL
    # -----------------------------------------------------------------------
    def actualizar_actividad(self):
        try:
            resp = requests.get(f"{API_URL}/telemetria/actividad?limit=50", timeout=1.5)
            if resp.status_code == 200:
                actividad = resp.json()
                filtro = self.txt_mon_filter.text().lower().strip()

                if filtro:
                    actividad = [
                        a for a in actividad
                        if filtro in a.get("path", "").lower() or filtro in a.get("ip", "").lower()
                    ]

                self.table_actividad.setRowCount(len(actividad))
                for i, req in enumerate(reversed(actividad)):
                    self.table_actividad.setItem(i, 0, QTableWidgetItem(req.get("hora", "")))
                    self.table_actividad.setItem(i, 1, QTableWidgetItem(req.get("metodo", "")))
                    self.table_actividad.setItem(i, 2, QTableWidgetItem(req.get("path", "")))
                    self.table_actividad.setItem(i, 3, QTableWidgetItem(req.get("ip", "")))

                    status_item = QTableWidgetItem(str(req.get("status", 200)))
                    if req.get("status", 200) >= 400:
                        status_item.setForeground(QColor("#ff1744"))
                    else:
                        status_item.setForeground(QColor("#00e676"))
                    self.table_actividad.setItem(i, 4, status_item)

                    self.table_actividad.setItem(i, 5, QTableWidgetItem(f"{req.get('latencia_ms', 0)} ms"))

                self.lbl_mon_stats.setText(f"Peticiones Registradas: {len(actividad)}")
        except Exception:
            pass

    # -----------------------------------------------------------------------
    # EXPLORADOR DE DATOS ALMACENADOS
    # -----------------------------------------------------------------------
    def cargar_datos_almacenados(self):
        self.cargar_zonas_riesgo()
        self.cargar_mensajes_comunidad()
        self.cargar_sismos()

    def cargar_zonas_riesgo(self):
        if not os.path.exists(ZONAS_PATH):
            return
        try:
            with open(ZONAS_PATH, "r", encoding="utf-8") as f:
                data = json.load(f)

            self.all_zonas = []
            for cod, info in data.items():
                if cod == "fecha_actualizacion":
                    continue
                self.all_zonas.append(info)

            self.filtrar_zonas()
        except Exception as e:
            print("Error cargando zonas:", e)

    def filtrar_zonas(self):
        filtro = self.txt_search_zona.text().lower().strip()
        zonas_filtradas = [
            z for z in getattr(self, 'all_zonas', [])
            if not filtro or filtro in z.get("localidad", "").lower() or filtro in str(z.get("codigo", ""))
        ]

        self.table_zonas.setRowCount(len(zonas_filtradas))
        for i, z in enumerate(zonas_filtradas):
            self.table_zonas.setItem(i, 0, QTableWidgetItem(str(z.get("codigo", ""))))
            self.table_zonas.setItem(i, 1, QTableWidgetItem(z.get("localidad", "")))

            nivel = z.get("nivel_riesgo", "")
            item_nivel = QTableWidgetItem(nivel.upper())
            if nivel.lower() == "alto":
                item_nivel.setForeground(QColor("#ff1744"))
            elif nivel.lower() == "medio":
                item_nivel.setForeground(QColor("#ffab00"))
            else:
                item_nivel.setForeground(QColor("#00e676"))
            self.table_zonas.setItem(i, 2, item_nivel)

            self.table_zonas.setItem(i, 3, QTableWidgetItem(str(z.get("score_mixto", 0.0))))
            self.table_zonas.setItem(i, 4, QTableWidgetItem(str(z.get("poblacion", 0))))
            ctx = z.get("contexto", {})
            self.table_zonas.setItem(i, 5, QTableWidgetItem(str(ctx.get("estrato_promedio", "-"))))

    def cargar_mensajes_comunidad(self):
        try:
            resp = requests.get(f"{API_URL}/comunidad/mensajes", timeout=2)
            if resp.status_code == 200:
                mensajes = resp.json()
                self.table_comunidad.setRowCount(len(mensajes))
                for i, msg in enumerate(reversed(mensajes)):
                    self.table_comunidad.setItem(i, 0, QTableWidgetItem(str(msg.get("id", ""))))
                    self.table_comunidad.setItem(i, 1, QTableWidgetItem(msg.get("alias_anonimo", "")))
                    self.table_comunidad.setItem(i, 2, QTableWidgetItem(msg.get("localidad", "")))
                    self.table_comunidad.setItem(i, 3, QTableWidgetItem(msg.get("texto", "")))

                    alerta_item = QTableWidgetItem("⚠️ ALERTA" if msg.get("es_alerta") else "Normal")
                    if msg.get("es_alerta"):
                        alerta_item.setForeground(QColor("#ff1744"))
                    self.table_comunidad.setItem(i, 4, alerta_item)

                    ts = msg.get("timestamp", 0) / 1000.0
                    hora_str = time.strftime("%Y-%m-%d %H:%M:%S", time.localtime(ts)) if ts else "-"
                    self.table_comunidad.setItem(i, 5, QTableWidgetItem(hora_str))
        except Exception as e:
            print("Error cargando mensajes comunitarios:", e)

    def eliminar_mensaje_comunidad(self):
        selected = self.table_comunidad.currentRow()
        if selected < 0:
            QMessageBox.warning(self, "Selección requerida", "Selecciona un mensaje de la tabla para eliminar.")
            return

        msg_id = self.table_comunidad.item(selected, 0).text()
        try:
            resp = requests.delete(f"{API_URL}/comunidad/mensajes/{msg_id}", timeout=2)
            if resp.status_code == 200:
                QMessageBox.information(self, "Eliminado", f"Mensaje {msg_id} eliminado exitosamente.")
                self.cargar_mensajes_comunidad()
        except Exception as e:
            QMessageBox.critical(self, "Error", f"No se pudo eliminar el mensaje:\n{e}")

    def cargar_sismos(self):
        try:
            resp = requests.get(f"{API_URL}/sismos/recientes", timeout=3)
            if resp.status_code == 200:
                sismos = resp.json()
                self.table_sismos.setRowCount(len(sismos))
                for i, s in enumerate(sismos):
                    self.table_sismos.setItem(i, 0, QTableWidgetItem(f"M {s.get('magnitud', 0.0)}"))
                    self.table_sismos.setItem(i, 1, QTableWidgetItem(s.get("lugar", "")))
                    self.table_sismos.setItem(i, 2, QTableWidgetItem(f"{s.get('distancia_bogota_km', 0)} km"))
                    self.table_sismos.setItem(i, 3, QTableWidgetItem(f"{s.get('profundidad_km', 0)} km"))

                    ts = s.get("tiempo", 0) / 1000.0
                    hora_str = time.strftime("%Y-%m-%d %H:%M:%S", time.localtime(ts)) if ts else "-"
                    self.table_sismos.setItem(i, 4, QTableWidgetItem(hora_str))
        except Exception as e:
            print("Error cargando sismos:", e)


def main():
    app = QApplication(sys.argv)
    app.setApplicationName("Barrio Seguro Control")
    window = MainWindow()
    window.show()
    sys.exit(app.exec())


if __name__ == "__main__":
    main()
