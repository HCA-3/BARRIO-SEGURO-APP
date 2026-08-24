#!/usr/bin/env python3  6IhMI562Dz4qEAm2xFuOgPOnEdwFel5P7NwYxUYjkcYkbFk4FNOP 
# -*- coding: utf-8 -*-
# 6IhMI562Dz4qEAm2xFuOgPOnEdwFel5P7NwYxUYjkcYkbFk4FNOP
"""
╔══════════════════════════════════════════════════════════════════════════════╗
║  LENS.ORG — Interfaz Gráfica del Motor de Búsqueda                         ║
║  GUI construida con tkinter (nativo de Python)                              ║
║  Privacidad: 100% local — sin telemetría, sin envío a terceros              ║
╚══════════════════════════════════════════════════════════════════════════════╝
"""

import os
import sys
import threading
import webbrowser
import json
import csv
from pathlib import Path
from dataclasses import asdict

# ─── Forzar UTF-8 en Windows ─────────────────────────────────────────────────
if sys.platform == "win32":
    os.environ.setdefault("PYTHONIOENCODING", "utf-8")
    try:
        sys.stdout.reconfigure(encoding="utf-8", errors="replace")
        sys.stderr.reconfigure(encoding="utf-8", errors="replace")
    except Exception:
        pass

import tkinter as tk
from tkinter import ttk, messagebox, filedialog, scrolledtext

# Importar el motor de búsqueda
from lens_search_engine import (
    LensAPIClient,
    LensSearchEngine,
    PDFDownloader,
    ResultExporter,
    DocumentResult,
    CONSULTAS_EJEMPLO,
    DOCUMENTS_DIR,
    OUTPUT_DIR,
    RESULTS_CSV,
    RESULTS_JSON,
)


# ═══════════════════════════════════════════════════════════════════════════════
# PALETA DE COLORES Y CONSTANTES DE DISEÑO
# ═══════════════════════════════════════════════════════════════════════════════

class Theme:
    """Paleta de colores moderna (dark mode)."""
    BG_DARK = "#1a1b2e"
    BG_CARD = "#242640"
    BG_INPUT = "#2d2f4e"
    BG_HOVER = "#353760"
    
    ACCENT = "#6c63ff"          # Morado principal
    ACCENT_HOVER = "#7f78ff"
    ACCENT_LIGHT = "#a29bfe"
    
    SUCCESS = "#00b894"
    WARNING = "#fdcb6e"
    ERROR = "#e17055"
    INFO = "#74b9ff"
    
    TEXT_PRIMARY = "#ffffff"
    TEXT_SECONDARY = "#a0a3c4"
    TEXT_MUTED = "#6c6f93"
    
    BORDER = "#3a3d5c"
    
    FONT_FAMILY = "Segoe UI"
    FONT_TITLE = ("Segoe UI", 22, "bold")
    FONT_SUBTITLE = ("Segoe UI", 13, "bold")
    FONT_BODY = ("Segoe UI", 11)
    FONT_SMALL = ("Segoe UI", 9)
    FONT_MONO = ("Consolas", 10)
    FONT_BUTTON = ("Segoe UI", 11, "bold")


# ═══════════════════════════════════════════════════════════════════════════════
# WIDGETS PERSONALIZADOS
# ═══════════════════════════════════════════════════════════════════════════════

class RoundedButton(tk.Canvas):
    """Botón con efecto hover y esquinas redondeadas simuladas."""

    def __init__(self, parent, text, command=None, width=180, height=40,
                 bg_color=None, hover_color=None, text_color=None, **kwargs):
        super().__init__(parent, width=width, height=height,
                         bg=parent.cget("bg"), highlightthickness=0, **kwargs)
        
        self.bg_color = bg_color or Theme.ACCENT
        self.hover_color = hover_color or Theme.ACCENT_HOVER
        self.text_color = text_color or Theme.TEXT_PRIMARY
        self.command = command
        self._width = width
        self._height = height
        
        self._draw(self.bg_color)
        self.text_id = self.create_text(
            width // 2, height // 2,
            text=text, fill=self.text_color,
            font=Theme.FONT_BUTTON
        )
        
        self.bind("<Enter>", self._on_enter)
        self.bind("<Leave>", self._on_leave)
        self.bind("<ButtonRelease-1>", self._on_click)

    def _draw(self, color):
        self.delete("bg")
        r = 8
        w, h = self._width, self._height
        self.create_rounded_rect(2, 2, w - 2, h - 2, r, fill=color, outline="", tags="bg")
        if hasattr(self, "text_id"):
            self.tag_raise(self.text_id)

    def create_rounded_rect(self, x1, y1, x2, y2, r, **kwargs):
        points = [
            x1 + r, y1, x2 - r, y1,
            x2, y1, x2, y1 + r,
            x2, y2 - r, x2, y2,
            x2 - r, y2, x1 + r, y2,
            x1, y2, x1, y2 - r,
            x1, y1 + r, x1, y1,
        ]
        return self.create_polygon(points, smooth=True, **kwargs)

    def _on_enter(self, event):
        self._draw(self.hover_color)
        self.config(cursor="hand2")

    def _on_leave(self, event):
        self._draw(self.bg_color)

    def _on_click(self, event):
        if self.command:
            self.command()


class StyledEntry(tk.Frame):
    """Campo de entrada estilizado con placeholder."""

    def __init__(self, parent, placeholder="", show="", width=40, **kwargs):
        super().__init__(parent, bg=Theme.BG_CARD)
        
        self.placeholder = placeholder
        self.show_char = show
        self._has_content = False
        
        self.container = tk.Frame(self, bg=Theme.BG_INPUT, padx=12, pady=8)
        self.container.pack(fill=tk.X)
        
        self.entry = tk.Entry(
            self.container,
            font=Theme.FONT_BODY,
            bg=Theme.BG_INPUT,
            fg=Theme.TEXT_MUTED,
            insertbackground=Theme.ACCENT_LIGHT,
            relief=tk.FLAT,
            width=width,
            border=0,
        )
        self.entry.pack(fill=tk.X)
        
        # Placeholder
        self.entry.insert(0, placeholder)
        self.entry.bind("<FocusIn>", self._on_focus_in)
        self.entry.bind("<FocusOut>", self._on_focus_out)

    def _on_focus_in(self, event):
        if self.entry.get() == self.placeholder:
            self.entry.delete(0, tk.END)
            self.entry.config(fg=Theme.TEXT_PRIMARY)
            if self.show_char:
                self.entry.config(show=self.show_char)
        self.container.config(highlightbackground=Theme.ACCENT, highlightthickness=1)

    def _on_focus_out(self, event):
        if not self.entry.get():
            self.entry.config(fg=Theme.TEXT_MUTED, show="")
            self.entry.insert(0, self.placeholder)
        self.container.config(highlightthickness=0)

    def get(self):
        val = self.entry.get()
        return "" if val == self.placeholder else val

    def set(self, value):
        self.entry.delete(0, tk.END)
        self.entry.insert(0, value)
        self.entry.config(fg=Theme.TEXT_PRIMARY)
        if self.show_char and value:
            self.entry.config(show=self.show_char)


# ═══════════════════════════════════════════════════════════════════════════════
# APLICACIÓN PRINCIPAL
# ═══════════════════════════════════════════════════════════════════════════════

class LensSearchGUI:
    """Interfaz gráfica principal para el motor de búsqueda de Lens.org."""

    def __init__(self):
        self.root = tk.Tk()
        self.root.title("Lens.org — Motor de Búsqueda")
        self.root.configure(bg=Theme.BG_DARK)
        self.root.minsize(960, 700)
        
        # Centrar ventana
        screen_w = self.root.winfo_screenwidth()
        screen_h = self.root.winfo_screenheight()
        win_w, win_h = 1080, 780
        x = (screen_w - win_w) // 2
        y = (screen_h - win_h) // 2
        self.root.geometry(f"{win_w}x{win_h}+{x}+{y}")
        
        # Estado
        self.engine = None
        self.resultados: list = []
        self.buscando = False
        
        # Construir UI
        self._build_ui()
        
    def run(self):
        self.root.mainloop()

    # ─── Construcción de la UI ───────────────────────────────────────────────

    def _build_ui(self):
        """Construye toda la interfaz."""
        # Contenedor principal con scroll
        main_frame = tk.Frame(self.root, bg=Theme.BG_DARK)
        main_frame.pack(fill=tk.BOTH, expand=True, padx=20, pady=10)
        
        # ── Header ──
        self._build_header(main_frame)
        
        # ── Sección Token ──
        self._build_token_section(main_frame)
        
        # ── Sección Búsqueda ──
        self._build_search_section(main_frame)
        
        # ── Barra de progreso ──
        self._build_progress_section(main_frame)
        
        # ── Resultados ──
        self._build_results_section(main_frame)
        
        # ── Log / Consola ──
        self._build_log_section(main_frame)

    def _build_header(self, parent):
        """Header con título y descripción."""
        header = tk.Frame(parent, bg=Theme.BG_DARK)
        header.pack(fill=tk.X, pady=(5, 15))
        
        # Título
        title_frame = tk.Frame(header, bg=Theme.BG_DARK)
        title_frame.pack(fill=tk.X)
        
        tk.Label(
            title_frame, text="🔬", font=("Segoe UI Emoji", 28),
            bg=Theme.BG_DARK, fg=Theme.ACCENT
        ).pack(side=tk.LEFT, padx=(0, 10))
        
        title_text = tk.Frame(title_frame, bg=Theme.BG_DARK)
        title_text.pack(side=tk.LEFT)
        
        tk.Label(
            title_text, text="Lens.org Search Engine",
            font=Theme.FONT_TITLE, bg=Theme.BG_DARK, fg=Theme.TEXT_PRIMARY
        ).pack(anchor=tk.W)
        
        tk.Label(
            title_text,
            text="Búsqueda de artículos académicos y patentes  •  100% local  •  Sin telemetría",
            font=Theme.FONT_SMALL, bg=Theme.BG_DARK, fg=Theme.TEXT_MUTED
        ).pack(anchor=tk.W)

    def _build_token_section(self, parent):
        """Sección para ingresar el API Token."""
        card = tk.Frame(parent, bg=Theme.BG_CARD, padx=20, pady=15)
        card.pack(fill=tk.X, pady=(0, 10))
        
        # Header de la card
        header = tk.Frame(card, bg=Theme.BG_CARD)
        header.pack(fill=tk.X, pady=(0, 8))
        
        tk.Label(
            header, text="🔑  API Token",
            font=Theme.FONT_SUBTITLE, bg=Theme.BG_CARD, fg=Theme.TEXT_PRIMARY
        ).pack(side=tk.LEFT)
        
        self.status_label = tk.Label(
            header, text="● Sin conectar",
            font=Theme.FONT_SMALL, bg=Theme.BG_CARD, fg=Theme.TEXT_MUTED
        )
        self.status_label.pack(side=tk.RIGHT)
        
        # Input + botón
        input_row = tk.Frame(card, bg=Theme.BG_CARD)
        input_row.pack(fill=tk.X)
        
        self.token_entry = StyledEntry(
            input_row,
            placeholder="Pega tu API Token de Lens.org aquí...",
            show="•",
            width=60,
        )
        self.token_entry.pack(side=tk.LEFT, fill=tk.X, expand=True, padx=(0, 10))
        
        RoundedButton(
            input_row, text="Conectar", command=self._conectar,
            width=130, height=38
        ).pack(side=tk.RIGHT)
        
        # Link de ayuda
        help_label = tk.Label(
            card,
            text="¿No tienes token? → lens.org/lens/user/subscriptions",
            font=Theme.FONT_SMALL, bg=Theme.BG_CARD, fg=Theme.ACCENT_LIGHT,
            cursor="hand2"
        )
        help_label.pack(anchor=tk.W, pady=(6, 0))
        help_label.bind("<Button-1>", lambda e: webbrowser.open(
            "https://www.lens.org/lens/user/subscriptions#scholar"
        ))

    def _build_search_section(self, parent):
        """Sección de búsqueda."""
        card = tk.Frame(parent, bg=Theme.BG_CARD, padx=20, pady=15)
        card.pack(fill=tk.X, pady=(0, 10))
        
        tk.Label(
            card, text="🔍  Búsqueda",
            font=Theme.FONT_SUBTITLE, bg=Theme.BG_CARD, fg=Theme.TEXT_PRIMARY
        ).pack(anchor=tk.W, pady=(0, 10))
        
        # Fila 1: Query
        self.query_entry = StyledEntry(
            card,
            placeholder='Escribe tu consulta, ej: "Digital Twins" AND "predictive maintenance"',
            width=80,
        )
        self.query_entry.pack(fill=tk.X, pady=(0, 10))
        
        # Fila 2: Opciones
        options_row = tk.Frame(card, bg=Theme.BG_CARD)
        options_row.pack(fill=tk.X, pady=(0, 10))
        
        # Tipo de búsqueda
        type_frame = tk.Frame(options_row, bg=Theme.BG_CARD)
        type_frame.pack(side=tk.LEFT, padx=(0, 20))
        
        tk.Label(
            type_frame, text="Tipo:",
            font=Theme.FONT_BODY, bg=Theme.BG_CARD, fg=Theme.TEXT_SECONDARY
        ).pack(side=tk.LEFT, padx=(0, 8))
        
        self.search_type = tk.StringVar(value="scholarly")
        
        style = ttk.Style()
        style.theme_use("clam")
        style.configure("Dark.TRadiobutton",
                         background=Theme.BG_CARD,
                         foreground=Theme.TEXT_PRIMARY,
                         font=Theme.FONT_BODY)
        
        ttk.Radiobutton(
            type_frame, text="Artículos",
            variable=self.search_type, value="scholarly",
            style="Dark.TRadiobutton"
        ).pack(side=tk.LEFT, padx=(0, 10))
        
        ttk.Radiobutton(
            type_frame, text="Patentes",
            variable=self.search_type, value="patent",
            style="Dark.TRadiobutton"
        ).pack(side=tk.LEFT)
        
        # Máximo resultados
        max_frame = tk.Frame(options_row, bg=Theme.BG_CARD)
        max_frame.pack(side=tk.LEFT, padx=(0, 20))
        
        tk.Label(
            max_frame, text="Máx. resultados:",
            font=Theme.FONT_BODY, bg=Theme.BG_CARD, fg=Theme.TEXT_SECONDARY
        ).pack(side=tk.LEFT, padx=(0, 8))
        
        self.max_results = tk.StringVar(value="50")
        max_entry = tk.Entry(
            max_frame, textvariable=self.max_results,
            font=Theme.FONT_BODY, bg=Theme.BG_INPUT, fg=Theme.TEXT_PRIMARY,
            insertbackground=Theme.ACCENT_LIGHT, relief=tk.FLAT,
            width=6, justify=tk.CENTER
        )
        max_entry.pack(side=tk.LEFT, ipady=4, ipadx=4)
        
        # Checkbox descargar PDFs
        self.descargar_pdfs = tk.BooleanVar(value=True)
        style.configure("Dark.TCheckbutton",
                         background=Theme.BG_CARD,
                         foreground=Theme.TEXT_PRIMARY,
                         font=Theme.FONT_BODY)
        ttk.Checkbutton(
            options_row, text="Descargar PDFs",
            variable=self.descargar_pdfs,
            style="Dark.TCheckbutton"
        ).pack(side=tk.LEFT)
        
        # Fila 3: Botones
        btn_row = tk.Frame(card, bg=Theme.BG_CARD)
        btn_row.pack(fill=tk.X, pady=(5, 0))
        
        RoundedButton(
            btn_row, text="🔍  Buscar", command=self._iniciar_busqueda,
            width=160, height=42,
            bg_color=Theme.ACCENT, hover_color=Theme.ACCENT_HOVER,
        ).pack(side=tk.LEFT, padx=(0, 10))
        
        RoundedButton(
            btn_row, text="📊  Exportar CSV", command=self._exportar_csv,
            width=160, height=42,
            bg_color="#2d6a4f", hover_color="#40916c",
        ).pack(side=tk.LEFT, padx=(0, 10))
        
        RoundedButton(
            btn_row, text="📋  Exportar JSON", command=self._exportar_json,
            width=170, height=42,
            bg_color="#2d6a4f", hover_color="#40916c",
        ).pack(side=tk.LEFT, padx=(0, 10))
        
        RoundedButton(
            btn_row, text="📗  Excel", command=self._exportar_excel,
            width=120, height=42,
            bg_color="#1D6F42", hover_color="#268a54",
        ).pack(side=tk.LEFT, padx=(0, 10))
        
        RoundedButton(
            btn_row, text="📁  Abrir carpeta", command=self._abrir_carpeta,
            width=160, height=42,
            bg_color=Theme.BG_HOVER, hover_color="#454870",
        ).pack(side=tk.LEFT)
        
        # Fila 4: Consultas predefinidas
        predef_frame = tk.Frame(card, bg=Theme.BG_CARD)
        predef_frame.pack(fill=tk.X, pady=(12, 0))
        
        tk.Label(
            predef_frame, text="Consultas rápidas:",
            font=Theme.FONT_SMALL, bg=Theme.BG_CARD, fg=Theme.TEXT_MUTED
        ).pack(side=tk.LEFT, padx=(0, 8))
        
        quick_queries = [
            ('"Digital Twins" AND "predictive maintenance"', "Digital Twins"),
            ('applicant.name:"Siemens" AND "IoT"', "Siemens IoT"),
            ('"Schneider Electric" AND "energy"', "Schneider"),
            ('applicant.name:"Samsung" AND "battery"', "Samsung"),
            ('"Jabil Circuit" AND "manufacturing"', "Jabil"),
        ]
        
        for query, label in quick_queries:
            btn = tk.Label(
                predef_frame, text=label,
                font=Theme.FONT_SMALL, bg=Theme.BG_INPUT, fg=Theme.ACCENT_LIGHT,
                padx=10, pady=3, cursor="hand2"
            )
            btn.pack(side=tk.LEFT, padx=2)
            btn.bind("<Button-1>", lambda e, q=query: self._set_query(q))
            btn.bind("<Enter>", lambda e, b=btn: b.config(bg=Theme.BG_HOVER))
            btn.bind("<Leave>", lambda e, b=btn: b.config(bg=Theme.BG_INPUT))

    def _build_progress_section(self, parent):
        """Barra de progreso."""
        self.progress_frame = tk.Frame(parent, bg=Theme.BG_DARK)
        self.progress_frame.pack(fill=tk.X, pady=(0, 5))
        
        self.progress_label = tk.Label(
            self.progress_frame, text="",
            font=Theme.FONT_SMALL, bg=Theme.BG_DARK, fg=Theme.TEXT_SECONDARY
        )
        self.progress_label.pack(anchor=tk.W)
        
        style = ttk.Style()
        style.configure("Custom.Horizontal.TProgressbar",
                         background=Theme.ACCENT,
                         troughcolor=Theme.BG_INPUT,
                         thickness=6)
        
        self.progress_bar = ttk.Progressbar(
            self.progress_frame,
            style="Custom.Horizontal.TProgressbar",
            mode="indeterminate",
            length=300,
        )

    def _build_results_section(self, parent):
        """Tabla de resultados."""
        card = tk.Frame(parent, bg=Theme.BG_CARD, padx=15, pady=10)
        card.pack(fill=tk.BOTH, expand=True, pady=(0, 10))
        
        # Header
        header = tk.Frame(card, bg=Theme.BG_CARD)
        header.pack(fill=tk.X, pady=(0, 8))
        
        tk.Label(
            header, text="📄  Resultados",
            font=Theme.FONT_SUBTITLE, bg=Theme.BG_CARD, fg=Theme.TEXT_PRIMARY
        ).pack(side=tk.LEFT)
        
        self.result_count_label = tk.Label(
            header, text="0 documentos",
            font=Theme.FONT_SMALL, bg=Theme.BG_CARD, fg=Theme.TEXT_MUTED
        )
        self.result_count_label.pack(side=tk.RIGHT)
        
        # Treeview (tabla)
        style = ttk.Style()
        style.configure("Dark.Treeview",
                         background=Theme.BG_INPUT,
                         foreground=Theme.TEXT_PRIMARY,
                         fieldbackground=Theme.BG_INPUT,
                         font=Theme.FONT_SMALL,
                         rowheight=28)
        style.configure("Dark.Treeview.Heading",
                         background=Theme.BG_HOVER,
                         foreground=Theme.TEXT_PRIMARY,
                         font=("Segoe UI", 10, "bold"))
        style.map("Dark.Treeview",
                   background=[("selected", Theme.ACCENT)],
                   foreground=[("selected", Theme.TEXT_PRIMARY)])
        
        columns = ("tipo", "titulo", "autores", "anio", "doi", "pdf")
        
        tree_frame = tk.Frame(card, bg=Theme.BG_CARD)
        tree_frame.pack(fill=tk.BOTH, expand=True)
        
        self.tree = ttk.Treeview(
            tree_frame, columns=columns, show="headings",
            style="Dark.Treeview", height=8,
        )
        
        # Configurar columnas
        self.tree.heading("tipo", text="Tipo")
        self.tree.heading("titulo", text="Título")
        self.tree.heading("autores", text="Autores")
        self.tree.heading("anio", text="Año")
        self.tree.heading("doi", text="DOI")
        self.tree.heading("pdf", text="PDF")
        
        self.tree.column("tipo", width=70, minwidth=50)
        self.tree.column("titulo", width=380, minwidth=200)
        self.tree.column("autores", width=200, minwidth=100)
        self.tree.column("anio", width=50, minwidth=40)
        self.tree.column("doi", width=150, minwidth=80)
        self.tree.column("pdf", width=50, minwidth=40)
        
        # Scrollbars
        scroll_y = ttk.Scrollbar(tree_frame, orient=tk.VERTICAL, command=self.tree.yview)
        scroll_x = ttk.Scrollbar(tree_frame, orient=tk.HORIZONTAL, command=self.tree.xview)
        self.tree.configure(yscrollcommand=scroll_y.set, xscrollcommand=scroll_x.set)
        
        self.tree.pack(side=tk.LEFT, fill=tk.BOTH, expand=True)
        scroll_y.pack(side=tk.RIGHT, fill=tk.Y)
        scroll_x.pack(side=tk.BOTTOM, fill=tk.X)
        
        # Doble click para ver detalle
        self.tree.bind("<Double-1>", self._ver_detalle)

    def _build_log_section(self, parent):
        """Panel de log / consola."""
        card = tk.Frame(parent, bg=Theme.BG_CARD, padx=15, pady=10)
        card.pack(fill=tk.X, pady=(0, 5))
        
        header = tk.Frame(card, bg=Theme.BG_CARD)
        header.pack(fill=tk.X, pady=(0, 5))
        
        tk.Label(
            header, text="📟  Consola",
            font=Theme.FONT_SUBTITLE, bg=Theme.BG_CARD, fg=Theme.TEXT_PRIMARY
        ).pack(side=tk.LEFT)
        
        clear_btn = tk.Label(
            header, text="Limpiar",
            font=Theme.FONT_SMALL, bg=Theme.BG_CARD, fg=Theme.ACCENT_LIGHT,
            cursor="hand2"
        )
        clear_btn.pack(side=tk.RIGHT)
        clear_btn.bind("<Button-1>", lambda e: self._limpiar_log())
        
        self.log_text = tk.Text(
            card, font=Theme.FONT_MONO,
            bg=Theme.BG_INPUT, fg=Theme.TEXT_SECONDARY,
            insertbackground=Theme.ACCENT,
            relief=tk.FLAT, height=5,
            wrap=tk.WORD, state=tk.DISABLED,
        )
        self.log_text.pack(fill=tk.X)
        
        # Tags para colores en el log
        self.log_text.tag_configure("info", foreground=Theme.INFO)
        self.log_text.tag_configure("success", foreground=Theme.SUCCESS)
        self.log_text.tag_configure("warning", foreground=Theme.WARNING)
        self.log_text.tag_configure("error", foreground=Theme.ERROR)
        self.log_text.tag_configure("accent", foreground=Theme.ACCENT_LIGHT)

    # ─── Acciones ────────────────────────────────────────────────────────────

    def _log(self, mensaje: str, tag: str = "info"):
        """Escribe un mensaje en la consola de log."""
        self.log_text.config(state=tk.NORMAL)
        self.log_text.insert(tk.END, f"{mensaje}\n", tag)
        self.log_text.see(tk.END)
        self.log_text.config(state=tk.DISABLED)

    def _limpiar_log(self):
        self.log_text.config(state=tk.NORMAL)
        self.log_text.delete(1.0, tk.END)
        self.log_text.config(state=tk.DISABLED)

    def _set_query(self, query: str):
        """Establece una consulta rápida en el campo de búsqueda."""
        self.query_entry.set(query)

    def _conectar(self):
        """Conecta con la API usando el token proporcionado."""
        token = self.token_entry.get()
        if not token:
            messagebox.showwarning("Token requerido",
                "Debes ingresar tu API Token de Lens.org.\n\n"
                "Obtenlo en:\nhttps://www.lens.org/lens/user/subscriptions")
            return
        
        try:
            self.engine = LensSearchEngine(token, descargar_pdfs=self.descargar_pdfs.get())
            self.status_label.config(text="● Conectado", fg=Theme.SUCCESS)
            self._log("Conexion establecida con Lens.org API", "success")
        except ValueError as e:
            self.status_label.config(text="● Error", fg=Theme.ERROR)
            self._log(f"Error: {e}", "error")
            messagebox.showerror("Error de conexión", str(e))

    def _iniciar_busqueda(self):
        """Inicia la búsqueda en un hilo separado."""
        if self.buscando:
            self._log("Ya hay una busqueda en progreso...", "warning")
            return
        
        query = self.query_entry.get()
        if not query:
            messagebox.showinfo("Consulta vacía", "Escribe una consulta de búsqueda.")
            return
        
        # Auto-conectar si no hay engine
        if not self.engine:
            self._conectar()
            if not self.engine:
                return
        
        # Actualizar engine con preferencia de PDFs
        self.engine.downloader = (
            PDFDownloader(DOCUMENTS_DIR) if self.descargar_pdfs.get() else None
        )
        
        self.buscando = True
        tipo = self.search_type.get()
        try:
            max_r = int(self.max_results.get())
        except ValueError:
            max_r = 50
        
        # Mostrar progreso
        self.progress_label.config(text=f"Buscando: {query[:60]}...")
        self.progress_bar.pack(fill=tk.X, pady=(4, 0))
        self.progress_bar.start(15)
        
        self._log(f"Iniciando busqueda: {query}", "accent")
        self._log(f"  Tipo: {tipo} | Max: {max_r} | PDFs: {self.descargar_pdfs.get()}", "info")
        
        # Ejecutar en hilo separado
        thread = threading.Thread(
            target=self._ejecutar_busqueda_thread,
            args=(query, tipo, max_r),
            daemon=True,
        )
        thread.start()

    def _ejecutar_busqueda_thread(self, query, tipo, max_resultados):
        """Ejecuta la búsqueda en background (hilo separado)."""
        try:
            docs = self.engine.ejecutar_busqueda(query, tipo, max_resultados)
            self.resultados = self.engine.resultados.copy()
            
            # Actualizar UI desde el hilo principal
            self.root.after(0, self._busqueda_completada, docs)
        except Exception as e:
            self.root.after(0, self._busqueda_error, str(e))

    def _busqueda_completada(self, docs):
        """Callback cuando la búsqueda termina exitosamente."""
        self.buscando = False
        self.progress_bar.stop()
        self.progress_bar.pack_forget()
        
        n = len(docs)
        pdfs = sum(1 for d in docs if d.pdf_descargado)
        
        self.progress_label.config(
            text=f"Completado: {n} documentos encontrados, {pdfs} PDFs descargados"
        )
        self._log(f"Busqueda completada: {n} documentos", "success")
        if pdfs > 0:
            self._log(f"  PDFs descargados: {pdfs}", "success")
        
        self.result_count_label.config(
            text=f"{len(self.resultados)} documentos en total"
        )
        
        # Poblar tabla
        self._poblar_tabla(docs)

    def _busqueda_error(self, error_msg):
        """Callback cuando la búsqueda falla."""
        self.buscando = False
        self.progress_bar.stop()
        self.progress_bar.pack_forget()
        self.progress_label.config(text="Error en la busqueda")
        self._log(f"Error: {error_msg}", "error")
        messagebox.showerror("Error", f"Error durante la búsqueda:\n{error_msg}")

    def _poblar_tabla(self, docs):
        """Llena la tabla con los resultados."""
        # No limpiar anteriores, agregar nuevos
        for doc in docs:
            tipo_label = "Articulo" if doc.tipo == "scholarly" else "Patente"
            pdf_label = "Si" if doc.pdf_descargado else ("URL" if doc.pdf_url else "-")
            
            self.tree.insert("", tk.END, values=(
                tipo_label,
                doc.titulo[:100] if doc.titulo else "Sin titulo",
                doc.autores[:50] if doc.autores else "-",
                doc.anio_publicacion or "-",
                doc.doi[:30] if doc.doi else "-",
                pdf_label,
            ))

    def _ver_detalle(self, event):
        """Muestra el detalle de un documento al hacer doble click."""
        selection = self.tree.selection()
        if not selection:
            return
        
        item = self.tree.item(selection[0])
        idx = self.tree.index(selection[0])
        
        if idx >= len(self.resultados):
            return
        
        doc = self.resultados[idx]
        
        # Ventana de detalle
        detail_win = tk.Toplevel(self.root)
        detail_win.title(f"Detalle — {doc.titulo[:50]}")
        detail_win.configure(bg=Theme.BG_DARK)
        detail_win.geometry("700x500")
        detail_win.transient(self.root)
        
        # Contenido
        container = tk.Frame(detail_win, bg=Theme.BG_CARD, padx=20, pady=20)
        container.pack(fill=tk.BOTH, expand=True, padx=15, pady=15)
        
        # Título
        tk.Label(
            container, text=doc.titulo,
            font=("Segoe UI", 14, "bold"), bg=Theme.BG_CARD,
            fg=Theme.TEXT_PRIMARY, wraplength=640, justify=tk.LEFT
        ).pack(anchor=tk.W, pady=(0, 10))
        
        # Metadatos
        meta_items = [
            ("Tipo", "Articulo academico" if doc.tipo == "scholarly" else "Patente"),
            ("Lens ID", doc.lens_id),
            ("Autores", doc.autores),
            ("Ano", doc.anio_publicacion),
            ("DOI", doc.doi),
            ("Solicitante", doc.solicitante),
            ("No. Patente", doc.numero_patente),
            ("Jurisdiccion", doc.jurisdiccion),
            ("URL Fuente", doc.fuente_url),
            ("PDF URL", doc.pdf_url),
            ("PDF Descargado", "Si" if doc.pdf_descargado else "No"),
        ]
        
        for label, value in meta_items:
            if value:
                row = tk.Frame(container, bg=Theme.BG_CARD)
                row.pack(fill=tk.X, pady=1)
                tk.Label(
                    row, text=f"{label}:", font=("Segoe UI", 10, "bold"),
                    bg=Theme.BG_CARD, fg=Theme.ACCENT_LIGHT, width=15, anchor=tk.W
                ).pack(side=tk.LEFT)
                
                val_label = tk.Label(
                    row, text=value[:80], font=Theme.FONT_SMALL,
                    bg=Theme.BG_CARD, fg=Theme.TEXT_SECONDARY,
                    wraplength=500, justify=tk.LEFT
                )
                val_label.pack(side=tk.LEFT, fill=tk.X)
                
                # Hacer URLs clickeables
                if value.startswith("http"):
                    val_label.config(fg=Theme.INFO, cursor="hand2")
                    val_label.bind("<Button-1>", lambda e, u=value: webbrowser.open(u))
        
        # Abstract
        if doc.abstract:
            tk.Label(
                container, text="Abstract:",
                font=("Segoe UI", 10, "bold"), bg=Theme.BG_CARD,
                fg=Theme.ACCENT_LIGHT
            ).pack(anchor=tk.W, pady=(15, 5))
            
            abstract_text = tk.Text(
                container, font=Theme.FONT_SMALL,
                bg=Theme.BG_INPUT, fg=Theme.TEXT_SECONDARY,
                relief=tk.FLAT, height=8, wrap=tk.WORD, padx=10, pady=8,
            )
            abstract_text.pack(fill=tk.BOTH, expand=True)
            abstract_text.insert(tk.END, doc.abstract)
            abstract_text.config(state=tk.DISABLED)

    def _exportar_csv(self):
        """Exporta resultados a CSV."""
        if not self.resultados:
            messagebox.showinfo("Sin datos", "No hay resultados para exportar.")
            return
        
        filepath = filedialog.asksaveasfilename(
            defaultextension=".csv",
            filetypes=[("CSV", "*.csv")],
            initialfile=RESULTS_CSV,
        )
        if filepath:
            ResultExporter.exportar_csv(self.resultados, Path(filepath).name)
            self._log(f"CSV exportado: {filepath}", "success")
            messagebox.showinfo("Exportado", f"CSV guardado en:\n{filepath}")

    def _exportar_json(self):
        """Exporta resultados a JSON."""
        if not self.resultados:
            messagebox.showinfo("Sin datos", "No hay resultados para exportar.")
            return
        
        filepath = filedialog.asksaveasfilename(
            defaultextension=".json",
            filetypes=[("JSON", "*.json")],
            initialfile=RESULTS_JSON,
        )
        if filepath:
            ResultExporter.exportar_json(self.resultados, Path(filepath).name)
            self._log(f"JSON exportado: {filepath}", "success")
            messagebox.showinfo("Exportado", f"JSON guardado en:\n{filepath}")

    def _exportar_excel(self):
        """Exporta resultados a Excel traduciendo resúmenes a español."""
        if not self.resultados:
            messagebox.showinfo("Sin datos", "No hay resultados para exportar.")
            return
        
        filepath = filedialog.asksaveasfilename(
            defaultextension=".xlsx",
            filetypes=[("Excel", "*.xlsx")],
            initialfile="resultados_lens.xlsx",
        )
        if filepath:
            self._log(f"Generando Excel y traduciendo resúmenes, por favor espera...", "warning")
            def worker():
                try:
                    ResultExporter.exportar_excel(self.resultados, Path(filepath).name)
                    self.root.after(0, lambda: self._log(f"Excel exportado: {filepath}", "success"))
                    self.root.after(0, lambda: messagebox.showinfo("Exportado", f"Excel guardado en:\n{filepath}"))
                except Exception as e:
                    self.root.after(0, lambda: self._log(f"Error al exportar Excel: {e}", "error"))
                    self.root.after(0, lambda: messagebox.showerror("Error", f"Fallo al exportar Excel:\n{e}"))
            
            threading.Thread(target=worker, daemon=True).start()

    def _abrir_carpeta(self):
        """Abre la carpeta de documentos descargados."""
        carpeta = DOCUMENTS_DIR.resolve()
        carpeta.mkdir(parents=True, exist_ok=True)
        if sys.platform == "win32":
            os.startfile(str(carpeta))
        else:
            webbrowser.open(str(carpeta))
        self._log(f"Carpeta abierta: {carpeta}", "info")


# ═══════════════════════════════════════════════════════════════════════════════
# PUNTO DE ENTRADA
# ═══════════════════════════════════════════════════════════════════════════════

if __name__ == "__main__":
    app = LensSearchGUI()
    app.run()
