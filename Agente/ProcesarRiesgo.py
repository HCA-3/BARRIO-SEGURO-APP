"""
Pipeline de clasificación de riesgo por localidad — Bogotá.

Enfoque A: reglas + geofencing (NO machine learning). "Entrenar" aquí significa
calibrar los umbrales que separan riesgo alto / medio / bajo, no ajustar pesos
de una red neuronal.

Fuentes de datos (todas en ./data, ver README_DATOS.md):
  1. Delito de Alto Impacto 2018-2025 por Localidad (SCJ Bogotá) -> conteo de
     11 categorías de delito, por localidad y por año.
  2. Proyecciones de población por Localidad 2005-2035 (DANE/SDP) -> para
     normalizar el conteo de delitos por habitantes (tasa, no conteo crudo).
  3. Estratificación socioeconómica por manzana (SDP) -> estrato promedio por
     localidad (variable de CONTEXTO, no se usa para subir/bajar el riesgo:
     ver nota ética abajo).
  4. OpenStreetMap Bogotá (vía BBBike) -> densidad de alumbrado público y de
     vías, como señal adicional de contexto urbano.
  5. (Opcional) Llamadas tramitadas NUSE/C4 — Línea 123 (SDSCJ), histórico
     mensual 2015-actualidad (dataset "datos abiertllamadastramitadas...") ->
     volumen de llamadas de emergencia por localidad. A diferencia de la
     fuente 1 (delitos verificados, corte semestral/anual), esta se
     actualiza MENSUALMENTE, así que sirve para no dejar la app tan
     desactualizada entre cortes oficiales de delito. Este dataset SÍ trae
     un diccionario público de categorías (guiatipificacionincidentes.csv),
     a diferencia de la versión anterior (IRLoc.geojson, con códigos de
     columna sin diccionario accesible): por eso ahora se reporta un
     desglose de "incidentes_seguridad" (ver CATEGORIAS_INCIDENTE_NUSE_
     SEGURIDAD) además del total. Sigue siendo CONTEXTO, NO se usa para
     calcular nivel_riesgo, por la misma razón que el estrato: es volumen de
     LLAMADAS (percepción/uso del servicio), no delito verificado. Si no se
     encuentra este dataset, el pipeline cae de vuelta al IRLoc.geojson
     agregado (sin desglose) o sigue funcionando sin ninguno de los dos.
     NOTA: la carpeta de datos también trae ~70 archivos mensuales sueltos
     de llamadas 123 en crudo (uno por mes, con nombres inconsistentes y
     formatos que cambiaron con los años). Deliberadamente NO se procesan
     uno por uno: el dataset histórico agregado de este punto 5 ya cubre el
     mismo período con una sola fuente consistente, así que parsear los ~70
     sueltos sería trabajo redundante y más frágil (mayor riesgo de contar
     dos veces el mismo mes o de romperse por un cambio de columnas).
  6. (Opcional, contexto adicional del Observatorio de Seguridad y
     Convivencia — OSB, SDSCJ/Secretaría de Salud): accidentes de tránsito
     (osb_evento_transporte.csv), accidentes domésticos (osb_saludmental-
     accidentesdomesticos.csv), violencia intrafamiliar reportada en salud
     (osb_saludmental-vintrafamiliar.csv — distinta de la fuente 1, que es
     un DELITO verificado por la Fiscalía/Policía; esta es un REGISTRO DE
     ATENCIÓN EN SALUD, con su propia subnotificación y sesgos, por lo que
     se reporta aparte y tampoco afecta nivel_riesgo), reportes comunitarios
     de inseguridad (osb_detsoc_vbc.csv) y organizaciones comunitarias
     activas (osb_detsoc_revcom.csv, señal de tejido social/resiliencia, no
     de riesgo). Todo esto es CONTEXTO opcional: si un archivo no aparece,
     el campo correspondiente queda en null y el resto del pipeline sigue.
  7. (Opcional) Personas atendidas por la Secretaría Distrital de
     Integración Social — SDIS (2024-2025) -> volumen de atención social
     por localidad. Igual que el estrato: es una señal de USO DE SERVICIOS
     SOCIALES (que depende de cobertura/oferta, no solo de necesidad), así
     que se reporta como contexto y NO se usa para nivel_riesgo, para no
     repetir el mismo sesgo que ya se evita con el estrato.

Salida: ./output/zonas_riesgo.json con:
  { "localidad_codigo": {
        "localidad": str,
        "tasa_delitos_100k": float,
        "nivel_riesgo": "alto" | "medio" | "bajo",
        "detalle_delitos": {...},
        "poblacion_2025": int,
        "estrato_promedio": float,
        "contexto_urbano": {...}
    }, ... }

NOTA ÉTICA IMPORTANTE (para la sustentación):
  El estrato socioeconómico se reporta como dato de CONTEXTO en la salida,
  pero deliberadamente NO se usa como insumo para calcular `nivel_riesgo`.
  Usar el estrato (que correlaciona con pobreza) como si fuera un factor de
  "riesgo" es un sesgo conocido en sistemas de policiamiento predictivo:
  termina estigmatizando zonas pobres independientemente de si allí ocurren
  más o menos delitos. El riesgo se calcula SOLO a partir de la tasa real de
  delitos por habitante. Esto es una decisión de diseño defendible y se
  recomienda mencionarla explícitamente en el documento del proyecto.
"""

import glob
import json
import os
import sys
import unicodedata

import geopandas as gpd
import pandas as pd

if sys.stdout.encoding != "utf-8":
    sys.stdout.reconfigure(encoding="utf-8")

BASE_DIR = os.path.dirname(os.path.abspath(__file__))
# DATA_DIR es la carpeta donde estén los datasets descargados, en CUALQUIER
# estructura de subcarpetas (no hace falta organizarlos a mano). Por defecto
# es la misma carpeta donde vive este script; si los datasets están en otro
# lado, se puede correr como: python procesar_riesgo.py "C:\ruta\a\Data"
DATA_DIR = sys.argv[1] if len(sys.argv) > 1 else BASE_DIR
OUT_DIR = os.path.join(BASE_DIR, "output")
os.makedirs(OUT_DIR, exist_ok=True)


def buscar_archivo(patron: str) -> str:
    """
    Busca un archivo por patrón (ej. "**/DAILoc.geojson") en cualquier parte
    dentro de DATA_DIR, sin importar en qué subcarpeta haya quedado ni cómo
    se llame la carpeta contenedora (los navegadores a veces agregan "(1)",
    ".geopackage", etc. a los nombres de carpeta al descomprimir).
    """
    coincidencias = glob.glob(os.path.join(DATA_DIR, "**", patron), recursive=True)
    if not coincidencias:
        raise FileNotFoundError(
            f"No encontré ningún archivo que coincida con '{patron}' dentro de "
            f"{DATA_DIR}. Revisa que lo hayas descargado y esté en algún lugar "
            f"dentro de esa carpeta (no importa en qué subcarpeta)."
        )
    if len(coincidencias) > 1:
        print(f"  (aviso: encontré {len(coincidencias)} archivos que coinciden con "
              f"'{patron}', usando el primero: {coincidencias[0]})")
    return coincidencias[0]


def buscar_por_palabras(extension: str, incluir: list, excluir: list = ()) -> str:
    """
    Busca, entre todos los archivos con cierta extensión dentro de DATA_DIR,
    el que contenga TODAS las palabras de `incluir` y NINGUNA de `excluir`
    en su nombre (sin importar mayúsculas/tildes ni el orden de las
    palabras). Más robusto que un patrón fijo cuando el nombre del archivo
    puede venir con el nombre original largo del portal o ya renombrado.
    """
    candidatos = glob.glob(os.path.join(DATA_DIR, "**", f"*.{extension}"), recursive=True)
    for ruta in candidatos:
        nombre = quitar_tildes(os.path.basename(ruta))
        if all(quitar_tildes(p) in nombre for p in incluir) and not any(
            quitar_tildes(p) in nombre for p in excluir
        ):
            return ruta
    raise FileNotFoundError(
        f"No encontré ningún archivo .{extension} dentro de {DATA_DIR} cuyo "
        f"nombre contenga {incluir} (y no contenga {list(excluir)}). "
        f"Archivos .{extension} encontrados: {[os.path.basename(c) for c in candidatos]}"
    )

# Años que se consideran "recientes" para calcular la tasa de riesgo actual.
# Usamos un promedio de los últimos 3 años con datos completos en vez de un
# solo año, para no dejar que un mes atípico dispare la clasificación.
ANIOS_RECIENTES = [23, 24, 25]  # 2023, 2024, 2025

# Prefijos de columnas por categoría de delito en el dataset DAILoc.geojson,
# según el diccionario de datos de la Secretaría de Seguridad (SDSCJ).
CATEGORIAS_DELITO = {
    "CMH": "Homicidios",
    "CMLP": "Lesiones personales",
    "CMHP": "Hurto a personas",
    "CMHR": "Hurto a residencias",
    "CMHA": "Hurto de automotores",
    "CMHB": "Hurto de bicicletas",
    "CMHC": "Hurto a comercio",
    "CMHCE": "Hurto de celulares",
    "CMHM": "Hurto de motocicletas",
    "CMDS": "Delitos sexuales",
    "CMVI": "Violencia intrafamiliar",
}

# Pesos de severidad por categoría, para no tratar un homicidio igual que un
# hurto de bicicleta al sumar "delitos totales". Inspirado en el principio
# del "Crime Severity Score" del ONS (Reino Unido): los delitos violentos o
# contra la vida pesan mucho más que los delitos contra la propiedad sin
# violencia. Las cifras exactas son una decisión de diseño (no hay una única
# tabla "correcta"): están documentadas aquí para que el asesor del proyecto
# las pueda revisar y ajustar si lo considera necesario.
PESO_SEVERIDAD = {
    "Homicidios": 100,
    "Delitos sexuales": 70,
    "Violencia intrafamiliar": 40,
    "Lesiones personales": 35,
    "Hurto a residencias": 25,
    "Hurto a personas": 20,
    "Hurto de automotores": 20,
    "Hurto de motocicletas": 18,
    "Hurto a comercio": 15,
    "Hurto de bicicletas": 10,
    "Hurto de celulares": 8,
}


# Códigos del dataset histórico de llamadas NUSE/123 (ver cargar_incidentes_
# nuse) que se consideran relacionados con seguridad/convivencia, elegidos a
# mano del diccionario público guiatipificacionincidentes.csv. Es una
# selección editorial (igual que PESO_SEVERIDAD): se excluyen a propósito
# categorías médicas/ambientales/de tránsito puro (esas ya se cubren, cuando
# aplica, por accidentes_transito o accidentes_domesticos) para que este
# desglose hable de percepción de inseguridad/convivencia, no de salud.
# Es volumen de LLAMADAS, no delito verificado: se reporta como CONTEXTO,
# nunca como insumo de nivel_riesgo (ver nota_incidentes_nuse en el meta).
CATEGORIAS_INCIDENTE_NUSE_SEGURIDAD = {
    "903": "Rapto / Secuestro",
    "904": "Hurto Efectuado",
    "905": "Atraco / Hurto en Proceso",
    "906": "Violencia Sexual",
    "910": "Lesiones Personales",
    "911": "Disparos",
    "913": "Vehículo Hurtado",
    "915": "Intento/Violación de Domicilio",
    "916": "Persona o Vehículo Sospechoso",
    "922": "Narcóticos",
    "932": "Alteración del Orden Público",
    "933": "Delincuente capturado por civil",
    "934": "Riña",
    "944": "Manifestación / Motín",
    "950": "Acción Subversiva",
    "968": "Pandillas Juveniles",
    "969": "Porte Ilegal de Armas",
    "978": "Hallazgo de Explosivos",
    "611": "Maltrato",
    "611M": "Maltrato a Mujer",
}

# Alias de nombre de localidad: los datasets del Observatorio de Seguridad
# (osb_*) escriben "La Candelaria", pero el dataset de delitos (fuente de
# verdad de nombres/códigos en este pipeline) la llama solo "Candelaria".
ALIAS_LOCALIDAD = {"LA CANDELARIA": "CANDELARIA"}


def quitar_tildes(texto: str) -> str:
    nfkd = unicodedata.normalize("NFKD", texto)
    return "".join(c for c in nfkd if not unicodedata.combining(c)).upper().strip()


def normalizar_nombre_localidad(nombre: str) -> str:
    """Normaliza un nombre de localidad para cruzarlo por texto contra el
    nombre oficial (de DAILoc.geojson), cuando el dataset de origen no trae
    el código numérico de localidad."""
    normalizado = quitar_tildes(str(nombre))
    return ALIAS_LOCALIDAD.get(normalizado, normalizado)


def parse_numero_es(valor) -> float:
    """Convierte un número en formato colombiano ('.' de miles, ',' decimal)
    a float, ej. '152.380' -> 152380.0, '8,5' -> 8.5. Si pandas ya parseó la
    columna como numérica (sin comas, típico cuando ninguna fila de esa
    columna llegó a miles), `valor` ya es float/int y se devuelve tal cual:
    aplicarle el reemplazo de texto a un float como 614.0 lo arruinaría
    (614.0 -> '6140')."""
    if isinstance(valor, (int, float)) and not pd.isna(valor):
        return float(valor)
    texto = str(valor).strip().replace(".", "").replace(",", ".")
    return float(texto)


def cargar_delitos() -> pd.DataFrame:
    gdf = gpd.read_file(buscar_archivo("DAILoc.geojson"))
    gdf = gdf[gdf["CMIULOCAL"] != "99"].copy()  # excluir "Sin Localización"
    gdf["codigo"] = gdf["CMIULOCAL"].astype(int)

    filas = []
    for _, row in gdf.iterrows():
        detalle = {}
        total_reciente = 0
        for prefijo, nombre in CATEGORIAS_DELITO.items():
            # El sufijo de las columnas anuales no es consistente en el dataset:
            # la mayoría usa "CONT" (ej. CMH23CONT) pero "CMHCE" (hurto de
            # celulares) usa "CON" (ej. CMHCE23CON, sin la T). Buscamos por
            # prefijo+año en vez de asumir un sufijo fijo, para no perder esa
            # categoría silenciosamente.
            cols = [
                c
                for c in gdf.columns
                if c.startswith(prefijo)
                and any(c[len(prefijo):].startswith(str(a)) for a in ANIOS_RECIENTES)
                and c.endswith("CON") | c.endswith("CONT")
            ]
            # Evitar que un prefijo corto capture columnas de otro prefijo más
            # largo (ej. "CMH" no debe capturar "CMHP23CONT" ni "CMHCE23CON").
            cols = [c for c in cols if c[len(prefijo)] not in "ABCDEFGHIJKLMNOPQRSTUVWXYZ"]
            valor = sum(row[c] for c in cols if pd.notna(row[c]))
            detalle[nombre] = int(valor)
            total_reciente += valor

        filas.append(
            {
                "codigo": row["codigo"],
                "localidad": row["CMNOMLOCAL"],
                "delitos_recientes_total": int(total_reciente),
                "detalle_delitos": detalle,
                "geometry": row["geometry"],
            }
        )

    df = pd.DataFrame(filas)
    return gpd.GeoDataFrame(df, geometry="geometry", crs=gdf.crs)


def cargar_poblacion() -> pd.DataFrame:
    # El nombre del .ods varía según de dónde se descargue (a veces trae el
    # nombre largo original del portal, ej.
    # "202503_localidad_proyeccion_retroproyeccion_poblacion_2005_2035.ods",
    # otras veces ya renombrado a "poblacion_localidad_2005_2035.ods").
    # Buscamos por palabras clave en vez de exigir un nombre exacto.
    ruta = buscar_por_palabras("ods", incluir=["localidad", "poblacion"], excluir=["upz"])
    xl = pd.read_excel(
        ruta,
        engine="odf",
        sheet_name="Hoja1",
        header=4,
    )
    anio_objetivo = 2000 + max(ANIOS_RECIENTES)  # año más reciente con datos de delito
    xl = xl[xl["AÑO"] == anio_objetivo].copy()

    cols_pob = [c for c in xl.columns if c.startswith("Hombres_") or c.startswith("Mujeres_")]
    xl["poblacion_fila"] = xl[cols_pob].sum(axis=1)

    xl = xl.rename(columns={"Código Localidad": "codigo", "Nombre Localidad": "localidad_pob"})
    # El dataset trae una fila por Área (Cabecera Municipal / Centro Poblado y Rural
    # Disperso) para cada localidad-año: sumamos para tener un solo total por localidad.
    agregado = (
        xl.groupby(["codigo", "localidad_pob"], as_index=False)["poblacion_fila"]
        .sum()
        .rename(columns={"poblacion_fila": "poblacion_total"})
    )
    assert agregado["codigo"].is_unique, "Sigue habiendo localidades duplicadas en población"
    return agregado


def cargar_poblacion_upz() -> pd.DataFrame:
    """Igual que cargar_poblacion(), pero a nivel UPZ: usado solo para la
    capa secundaria de llamadas NUSE por UPZ (ver main()), NO para
    nivel_riesgo por localidad."""
    ruta = buscar_por_palabras("ods", incluir=["upz", "poblacion"])
    xl = pd.read_excel(ruta, engine="odf", sheet_name="Hoja1", header=4)
    anio_objetivo = 2000 + max(ANIOS_RECIENTES)
    xl = xl[xl["AÑO"] == anio_objetivo].copy()

    cols_pob = [c for c in xl.columns if c.startswith("Hombres_") or c.startswith("Mujeres_")]
    xl["poblacion_fila"] = xl[cols_pob].sum(axis=1)

    xl = xl.rename(columns={"Código UPZ": "codigo_upz", "Nombre UPZ": "upz_pob"})
    agregado = (
        xl.groupby(["codigo_upz", "upz_pob"], as_index=False)["poblacion_fila"]
        .sum()
        .rename(columns={"poblacion_fila": "poblacion_total"})
    )
    return agregado


def cargar_incidentes_nuse_upz() -> gpd.GeoDataFrame | None:
    """Llamadas NUSE/C4 por UPZ (más fino que por localidad), con geometría
    para poder hacer geofencing a este nivel. Igual que
    cargar_incidentes_nuse(): opcional, y NO es delito verificado."""
    try:
        ruta = buscar_archivo("IRUPZ.geojson")
    except FileNotFoundError:
        print("  (no encontré IRUPZ.geojson — se omite la capa UPZ de llamadas)")
        return None

    gdf = gpd.read_file(ruta)
    gdf = gdf[gdf["CMIUUPLA"].notna() & gdf.geometry.notna()].copy()
    # CMIUUPLA viene como "UPZ22" para zonas urbanas (el código numérico hace
    # match con "Código UPZ" del dataset de población) o como "UPR<n>" para
    # zonas rurales (Unidad de Planeamiento Rural, sin equivalente en el
    # dataset de población UPZ): estas últimas se excluyen de esta capa.
    gdf = gdf[gdf["CMIUUPLA"].str.match(r"^UPZ\d+$")].copy()
    gdf["codigo_upz"] = gdf["CMIUUPLA"].str.replace("UPZ", "", regex=False).astype(int)

    filas = []
    for _, row in gdf.iterrows():
        total_reciente = 0
        for prefijo in PREFIJOS_INCIDENTES_NUSE:
            for anio in ANIOS_RECIENTES:
                for sufijo in ("CONT", "CON"):
                    col = f"{prefijo}{anio}{sufijo}"
                    if col in row.index and pd.notna(row[col]):
                        total_reciente += row[col]
                        break
        filas.append(
            {
                "codigo_upz": row["codigo_upz"],
                "upz": row["CMNOMUPLA"],
                "incidentes_nuse_recientes_total": int(total_reciente),
                "geometry": row["geometry"],
            }
        )

    df = pd.DataFrame(filas)
    return gpd.GeoDataFrame(df, geometry="geometry", crs=gdf.crs)


def cargar_estratificacion() -> pd.DataFrame:
    """Estrato promedio por manzana, agregado luego por localidad vía join espacial."""
    gdf = gpd.read_file(buscar_archivo("manzanaestratificacion.json"))
    gdf = gdf[gdf["ESTRATO"] > 0].copy()  # 0 = no residencial / sin estrato
    gdf["centroid"] = gdf.geometry.centroid
    return gdf


def cargar_osm_contexto():
    """Capas de OSM: puntos (alumbrado/POIs) y líneas (vías) para contexto urbano."""
    gpkg = buscar_archivo("Bogota.gpkg")
    puntos = gpd.read_file(gpkg, layer="points")
    lineas = gpd.read_file(gpkg, layer="lines")
    return puntos, lineas


# Prefijos de categoría del dataset "Incidente Reportado" (NUSE/C4, SDSCJ).
# A diferencia de CATEGORIAS_DELITO, aquí no hay diccionario público que
# traduzca cada prefijo a un nombre de categoría legible, así que solo se
# suman TODAS para un total agregado (ver nota en el docstring del módulo).
PREFIJOS_INCIDENTES_NUSE = ["CMAOP", "CMD", "CMH", "CMHC", "CMM", "CMMM", "CMN", "CMPIA", "CMR"]


def cargar_guia_incidentes_nuse() -> dict | None:
    """Diccionario público COD_INCIDENTE -> nombre legible, de
    guiatipificacionincidentes.csv. Sin esto no se puede desglosar el
    histórico de llamadas por categoría (ver cargar_incidentes_nuse)."""
    try:
        ruta = buscar_por_palabras("csv", incluir=["guia", "tipificacion", "incidentes"])
    except FileNotFoundError:
        return None
    guia = pd.read_csv(ruta, encoding="latin-1", sep=";", dtype=str)
    return dict(zip(guia["COD_INCIDENTE"].str.strip(), guia["INCIDENTE"].str.strip()))


def _cargar_incidentes_nuse_geojson_legacy() -> pd.DataFrame | None:
    """Método anterior (dataset oficial 'Incidente Reportado', IRLoc.geojson):
    sin diccionario público de categorías, solo el total agregado. Se
    conserva como respaldo por si el histórico de llamadas (fuente
    preferida, ver cargar_incidentes_nuse) no está disponible."""
    try:
        ruta = buscar_archivo("IRLoc.geojson")
    except FileNotFoundError:
        return None

    gdf = gpd.read_file(ruta)
    gdf = gdf[gdf["CMIULOCAL"] != "99"].copy()  # excluir "Sin Localización"
    gdf["codigo"] = gdf["CMIULOCAL"].astype(int)

    filas = []
    for _, row in gdf.iterrows():
        total_reciente = 0
        for prefijo in PREFIJOS_INCIDENTES_NUSE:
            for anio in ANIOS_RECIENTES:
                for sufijo in ("CONT", "CON"):
                    col = f"{prefijo}{anio}{sufijo}"
                    if col in row.index and pd.notna(row[col]):
                        total_reciente += row[col]
                        break
        filas.append({"codigo": row["codigo"], "incidentes_nuse_recientes_total": int(total_reciente)})

    return pd.DataFrame(filas)


def cargar_incidentes_nuse() -> pd.DataFrame | None:
    """
    Volumen (y, si hay diccionario, desglose) de llamadas de emergencia
    NUSE/123 por localidad. Fuente preferida: el histórico mensual agregado
    2015-actualidad ("...llamadastramitadas...nuse_linea-123...csv"), que
    trae código de localidad y tipo de incidente ya contados
    (CANT_INCIDENTES) por año/mes -- no hace falta (ni conviene, ver
    docstring del módulo) parsear los ~70 archivos mensuales sueltos.
    Si no está, cae de vuelta al dataset oficial IRLoc.geojson (sin
    desglose). Opcional en ambos casos: si ninguno está, el pipeline sigue
    sin esta columna de contexto.
    """
    try:
        ruta = buscar_por_palabras("csv", incluir=["llamadastramitadas"])
    except FileNotFoundError:
        print("  (no encontré el histórico de llamadas NUSE/123 — probando con IRLoc.geojson)")
        return _cargar_incidentes_nuse_geojson_legacy()

    df = pd.read_csv(ruta, encoding="latin-1", sep=";", dtype={"COD_LOCALIDAD": str})
    df["codigo"] = pd.to_numeric(df["COD_LOCALIDAD"], errors="coerce")
    df = df.dropna(subset=["codigo"])
    df["codigo"] = df["codigo"].astype(int)
    df = df[df["codigo"].between(1, 20)]  # excluir "-"/Sin Localización

    anios_completos = [2000 + a for a in ANIOS_RECIENTES]
    reciente = df[df["ANIO"].isin(anios_completos)]

    total = (
        reciente.groupby("codigo")["CANT_INCIDENTES"].sum().rename("incidentes_nuse_recientes_total")
    )
    resultado = total.reset_index()

    guia = cargar_guia_incidentes_nuse()
    if guia is None:
        print("  (no encontré guiatipificacionincidentes.csv — se omite el desglose por categoría)")
        return resultado

    reciente = reciente.copy()
    reciente["categoria"] = (
        reciente["TIPO_INCIDENTE"].astype(str).str.strip().map(CATEGORIAS_INCIDENTE_NUSE_SEGURIDAD)
    )
    seguridad = reciente.dropna(subset=["categoria"])
    detalle = (
        seguridad.groupby(["codigo", "categoria"])["CANT_INCIDENTES"].sum().unstack(fill_value=0)
    )
    detalle_por_codigo = {
        codigo: {cat: int(val) for cat, val in fila.items()}
        for codigo, fila in detalle.iterrows()
    }
    resultado["detalle_incidentes_seguridad"] = resultado["codigo"].map(
        lambda c: detalle_por_codigo.get(c, {})
    )
    return resultado


def cargar_accidentes_transito() -> pd.DataFrame | None:
    """Accidentes de tránsito por localidad (Observatorio de Seguridad,
    osb_evento_transporte.csv). Contexto opcional, no afecta nivel_riesgo."""
    try:
        ruta = buscar_archivo("osb_evento_transporte.csv")
    except FileNotFoundError:
        print("  (no encontré osb_evento_transporte.csv — se omite este contexto opcional)")
        return None

    df = pd.read_csv(ruta, encoding="utf-8-sig", sep=";")
    df["codigo"] = pd.to_numeric(df["CODIGO_LOCALIDAD"], errors="coerce")
    df = df.dropna(subset=["codigo"])
    df["codigo"] = df["codigo"].astype(int)
    df = df[df["codigo"].between(1, 20)]  # excluir "Sin dato"/citywide (0, 21)

    anios_completos = [2000 + a for a in ANIOS_RECIENTES]
    reciente = df[df["ANO"].isin(anios_completos)]
    total = reciente.groupby("codigo")["casos"].sum().rename("accidentes_transito_recientes_total")
    return total.reset_index()


def cargar_accidentes_domesticos() -> pd.DataFrame | None:
    """Accidentes domésticos en menores por localidad (Observatorio de
    Seguridad / Salud, osb_saludmental-accidentesdomesticos.csv), ya viene
    agregado por año. Se toma el año más reciente disponible. Contexto
    opcional, no afecta nivel_riesgo."""
    try:
        ruta = buscar_archivo("osb_saludmental-accidentesdomesticos.csv")
    except FileNotFoundError:
        print("  (no encontré osb_saludmental-accidentesdomesticos.csv — se omite este contexto opcional)")
        return None

    df = pd.read_csv(ruta, encoding="utf-8-sig", sep=";")
    df["nombre_norm"] = df["Área"].apply(normalizar_nombre_localidad)
    anio_max = df["Año"].max()
    df = df[df["Año"] == anio_max].copy()
    df["accidentes_domesticos_casos"] = df["Casos"].apply(parse_numero_es).astype(int)
    df["accidentes_domesticos_tasa"] = df["Tasa"].apply(parse_numero_es)
    df["accidentes_domesticos_anio"] = int(anio_max)
    return df[["nombre_norm", "accidentes_domesticos_casos", "accidentes_domesticos_tasa", "accidentes_domesticos_anio"]]


def cargar_violencia_intrafamiliar_salud() -> pd.DataFrame | None:
    """Casos de violencia intrafamiliar registrados por el sector SALUD
    (osb_saludmental-vintrafamiliar.csv), a nivel de registro individual.
    Distinto de 'Violencia intrafamiliar' en CATEGORIAS_DELITO (esa es un
    DELITO verificado por Fiscalía/Policía, fuente 1 del módulo; esta es un
    REGISTRO DE ATENCIÓN EN SALUD, con su propia subnotificación): se
    reporta aparte para no mezclar dos fuentes con distinta naturaleza.
    Contexto opcional, no afecta nivel_riesgo."""
    try:
        ruta = buscar_archivo("osb_saludmental-vintrafamiliar.csv")
    except FileNotFoundError:
        print("  (no encontré osb_saludmental-vintrafamiliar.csv — se omite este contexto opcional)")
        return None

    df = pd.read_csv(ruta, encoding="utf-8-sig", sep=";", usecols=["ano", "NOMBRE_LOCALIDAD"])
    anios_completos = [2000 + a for a in ANIOS_RECIENTES]
    reciente = df[df["ano"].isin(anios_completos)].copy()
    reciente["nombre_norm"] = reciente["NOMBRE_LOCALIDAD"].apply(normalizar_nombre_localidad)
    conteo = reciente.groupby("nombre_norm").size().rename("violencia_intrafamiliar_salud_recientes_total")
    return conteo.reset_index()


def cargar_reportes_comunitarios_inseguridad() -> pd.DataFrame | None:
    """Reportes comunitarios de 'situación problemática' relacionados con
    inseguridad (osb_detsoc_vbc.csv), filtrando por texto en
    SITUACION_PROBLEMATICA (ej. 'Inseguridad, entorno propicio a violencia y
    conflictos'). Es percepción/reporte comunitario, no delito verificado:
    contexto opcional, no afecta nivel_riesgo."""
    try:
        ruta = buscar_archivo("osb_detsoc_vbc.csv")
    except FileNotFoundError:
        print("  (no encontré osb_detsoc_vbc.csv — se omite este contexto opcional)")
        return None

    df = pd.read_csv(ruta, encoding="utf-8-sig", sep=";")
    anios_completos = [2000 + a for a in ANIOS_RECIENTES]
    reciente = df[df["ANIO"].isin(anios_completos)].copy()
    situacion_norm = reciente["SITUACION_PROBLEMATICA"].apply(quitar_tildes)
    es_inseguridad = situacion_norm.str.contains("INSEGURIDAD", na=False)
    df_inseg = reciente[es_inseguridad].copy()
    df_inseg["nombre_norm"] = df_inseg["LOCALIDAD"].apply(normalizar_nombre_localidad)
    conteo = df_inseg.groupby("nombre_norm").size().rename("reportes_comunitarios_inseguridad_recientes_total")
    return conteo.reset_index()


def cargar_organizaciones_comunitarias() -> pd.DataFrame | None:
    """Organizaciones comunitarias / vigías en salud activas por localidad
    (osb_detsoc_revcom.csv): señal de tejido social/resiliencia, NO de
    riesgo (más organizaciones no significa más inseguridad). Contexto
    opcional, no afecta nivel_riesgo."""
    try:
        ruta = buscar_archivo("osb_detsoc_revcom.csv")
    except FileNotFoundError:
        print("  (no encontré osb_detsoc_revcom.csv — se omite este contexto opcional)")
        return None

    df = pd.read_csv(ruta, encoding="utf-8-sig", sep=";")
    df["nombre_norm"] = df["LOCALIDAD"].apply(normalizar_nombre_localidad)
    conteo = df.groupby("nombre_norm").size().rename("organizaciones_comunitarias_registradas")
    return conteo.reset_index()


def cargar_personas_atendidas_sdis() -> pd.DataFrame | None:
    """Volumen de personas atendidas por la Secretaría Distrital de
    Integración Social (SDIS), 2024 (.xlsx) + 2025 (.csv), por localidad.
    Igual que el estrato: refleja uso/cobertura de servicios sociales, no
    solo necesidad -- contexto opcional, no afecta nivel_riesgo. El CSV 2025
    es grande (~500MB) así que se lee por chunks, solo la columna necesaria."""
    conteo_total = None

    try:
        ruta_csv = buscar_por_palabras("csv", incluir=["personas", "atendidas", "sdis"])
        conteo_csv = pd.Series(dtype="int64")
        for chunk in pd.read_csv(
            ruta_csv, encoding="utf-8-sig", sep=";", usecols=["CODLOCALIDAD_ATENCION"], chunksize=500_000
        ):
            codigos = pd.to_numeric(chunk["CODLOCALIDAD_ATENCION"], errors="coerce")
            codigos = codigos[codigos.between(1, 20)].astype(int)
            conteo_csv = conteo_csv.add(codigos.value_counts(), fill_value=0)
        conteo_total = conteo_csv
    except FileNotFoundError:
        print("  (no encontré el CSV de personas atendidas SDIS 2025 — se omite ese año)")

    try:
        ruta_xlsx = buscar_por_palabras("xlsx", incluir=["personas", "atendidas", "sdis"])
        df_xlsx = pd.read_excel(ruta_xlsx, usecols=["CODLOCALIDAD_ATENCION"])
        codigos = pd.to_numeric(df_xlsx["CODLOCALIDAD_ATENCION"], errors="coerce")
        codigos = codigos[codigos.between(1, 20)].astype(int)
        conteo_xlsx = codigos.value_counts()
        conteo_total = conteo_xlsx if conteo_total is None else conteo_total.add(conteo_xlsx, fill_value=0)
    except FileNotFoundError:
        print("  (no encontré el .xlsx de personas atendidas SDIS 2024 — se omite ese año)")

    if conteo_total is None:
        return None

    resultado = conteo_total.rename("personas_atendidas_sdis_recientes_total").reset_index()
    resultado = resultado.rename(columns={"index": "codigo", "CODLOCALIDAD_ATENCION": "codigo"})
    resultado["codigo"] = resultado["codigo"].astype(int)
    resultado["personas_atendidas_sdis_recientes_total"] = resultado[
        "personas_atendidas_sdis_recientes_total"
    ].astype(int)
    return resultado


def clasificar_por_jenks(serie: pd.Series):
    """
    Clasifica en 3 niveles usando Jenks Natural Breaks en vez de terciles.

    Terciles simples fuerzan el mismo número de localidades en cada nivel,
    sin importar si los datos realmente se agrupan así. Jenks busca los 2
    "saltos" que minimizan la varianza DENTRO de cada grupo y maximizan la
    varianza ENTRE grupos, es decir, corta donde los datos realmente cambian
    de comportamiento. Con solo 20 localidades el resultado puede diferir
    poco de los terciles, pero es el método correcto y escala bien si más
    adelante se baja a nivel de UPZ o barrio (más zonas).
    """
    import jenkspy

    valores = serie.tolist()
    breaks = jenkspy.jenks_breaks(valores, n_classes=3)
    q1, q2 = breaks[1], breaks[2]

    def nivel(x):
        if x <= q1:
            return "bajo"
        elif x <= q2:
            return "medio"
        else:
            return "alto"

    return serie.apply(nivel), (q1, q2)


def main():
    print("Cargando delitos...")
    delitos = cargar_delitos()

    print("Cargando población...")
    poblacion = cargar_poblacion()

    df = delitos.merge(poblacion, on="codigo", how="left")

    df["tasa_delitos_100k"] = (
        df["delitos_recientes_total"] / df["poblacion_total"] * 100_000
    )

    # Score ponderado por severidad: cada categoría de delito aporta según su
    # peso en PESO_SEVERIDAD antes de normalizar por población. Este es el
    # score que realmente se usa para clasificar el riesgo (ver más abajo);
    # `tasa_delitos_100k` (conteo plano) se conserva solo como referencia
    # comparativa en la salida.
    df["score_ponderado"] = df["detalle_delitos"].apply(
        lambda detalle: sum(detalle[cat] * PESO_SEVERIDAD[cat] for cat in detalle)
    )
    df["score_ponderado_100k"] = df["score_ponderado"] / df["poblacion_total"] * 100_000

    print("Cargando estratificación (puede tardar, ~44k manzanas)...")
    estratos = cargar_estratificacion()
    estratos_centroides = estratos.set_geometry("centroid").to_crs(df.crs)
    join_estrato = gpd.sjoin(
        estratos_centroides,
        df[["codigo", "localidad", "geometry"]],
        predicate="within",
        how="inner",
    )
    estrato_por_localidad = join_estrato.groupby("codigo")["ESTRATO"].mean().rename(
        "estrato_promedio"
    )
    df = df.merge(estrato_por_localidad, on="codigo", how="left")

    print("Cargando contexto OSM (puede tardar)...")
    puntos, lineas = cargar_osm_contexto()

    print("Cargando incidentes NUSE/C4 (opcional)...")
    incidentes_nuse = cargar_incidentes_nuse()
    if incidentes_nuse is not None:
        df = df.merge(incidentes_nuse, on="codigo", how="left")
    else:
        df["incidentes_nuse_recientes_total"] = None
    if "detalle_incidentes_seguridad" not in df.columns:
        df["detalle_incidentes_seguridad"] = None

    # Mapa nombre-normalizado -> código, para cruzar los datasets del
    # Observatorio de Seguridad (osb_*) que solo traen el nombre de
    # localidad en texto (sin código numérico).
    mapa_nombre_codigo = {quitar_tildes(loc): cod for cod, loc in zip(df["codigo"], df["localidad"])}

    def _unir_por_nombre(df_base: pd.DataFrame, df_nuevo: pd.DataFrame | None, columnas: list) -> pd.DataFrame:
        if df_nuevo is None:
            for col in columnas:
                df_base[col] = None
            return df_base
        df_nuevo = df_nuevo.copy()
        df_nuevo["codigo"] = df_nuevo["nombre_norm"].map(mapa_nombre_codigo)
        no_encontrados = df_nuevo[df_nuevo["codigo"].isna()]["nombre_norm"].unique()
        if len(no_encontrados) > 0:
            print(f"    (aviso: {len(no_encontrados)} nombre(s) de localidad sin match: {list(no_encontrados)})")
        df_nuevo = df_nuevo.dropna(subset=["codigo"]).drop(columns=["nombre_norm"])
        df_nuevo["codigo"] = df_nuevo["codigo"].astype(int)
        return df_base.merge(df_nuevo, on="codigo", how="left")

    print("Cargando contexto adicional del Observatorio de Seguridad (osb_*, opcional)...")
    accidentes_transito = cargar_accidentes_transito()
    if accidentes_transito is not None:
        df = df.merge(accidentes_transito, on="codigo", how="left")
    else:
        df["accidentes_transito_recientes_total"] = None

    accidentes_domesticos = cargar_accidentes_domesticos()
    df = _unir_por_nombre(
        df, accidentes_domesticos,
        ["accidentes_domesticos_casos", "accidentes_domesticos_tasa", "accidentes_domesticos_anio"],
    )

    violencia_intrafamiliar_salud = cargar_violencia_intrafamiliar_salud()
    df = _unir_por_nombre(df, violencia_intrafamiliar_salud, ["violencia_intrafamiliar_salud_recientes_total"])

    reportes_comunitarios = cargar_reportes_comunitarios_inseguridad()
    df = _unir_por_nombre(df, reportes_comunitarios, ["reportes_comunitarios_inseguridad_recientes_total"])

    organizaciones_comunitarias = cargar_organizaciones_comunitarias()
    df = _unir_por_nombre(df, organizaciones_comunitarias, ["organizaciones_comunitarias_registradas"])

    print("Cargando personas atendidas SDIS 2024-2025 (opcional, puede tardar por el tamaño del CSV)...")
    personas_atendidas_sdis = cargar_personas_atendidas_sdis()
    if personas_atendidas_sdis is not None:
        df = df.merge(personas_atendidas_sdis, on="codigo", how="left")
    else:
        df["personas_atendidas_sdis_recientes_total"] = None

    # Alumbrado público: en OSM suele venir como highway=street_lamp
    alumbrado = puntos[puntos.get("highway") == "street_lamp"] if "highway" in puntos.columns else puntos.iloc[0:0]
    if not alumbrado.empty:
        join_luz = gpd.sjoin(
            alumbrado.to_crs(df.crs),
            df[["codigo", "geometry"]],
            predicate="within",
            how="inner",
        )
        luces_por_localidad = join_luz.groupby("codigo").size().rename("num_luminarias")
        df = df.merge(luces_por_localidad, on="codigo", how="left")
    else:
        df["num_luminarias"] = None

    # Densidad vial: km de vías por localidad. Hacemos el sjoin en el CRS
    # geográfico (rápido, evita reproyectar ~1M de vértices de todo el país
    # recortado) y solo reproyectamos a un CRS métrico (EPSG 3116, metros)
    # para medir longitud correctamente.
    if not lineas.empty:
        lineas_ok = lineas.to_crs(df.crs)
        join_vias = gpd.sjoin(
            lineas_ok, df[["codigo", "geometry"]], predicate="intersects", how="inner"
        )
        join_vias_m = join_vias.set_geometry("geometry").to_crs(3116)
        km_por_localidad = (
            join_vias_m.groupby("codigo").apply(lambda g: g.geometry.length.sum() / 1000)
            .rename("longitud_vias_km")
        )
        df = df.merge(km_por_localidad, on="codigo", how="left")
    else:
        df["longitud_vias_km"] = None

    # Área en km2 para densidades (proyectamos a un CRS métrico para Bogotá: EPSG 3116)
    df_m = df.set_geometry("geometry").to_crs(3116)
    df["area_km2"] = df_m.geometry.area / 1_000_000
    df["luminarias_por_km2"] = df["num_luminarias"] / df["area_km2"]

    # score_ponderado_100k normaliza por población RESIDENTE, lo que infla
    # artificialmente localidades pequeñas con mucha población flotante no
    # residente (comercio, turismo, tránsito: ej. Los Mártires con San
    # Victorino/Paloquemao, La Candelaria con el centro histórico) y diluye
    # localidades grandes y muy pobladas donde el delito violento en cifras
    # absolutas es alto (ej. Ciudad Bolívar, Kennedy, Bosa), porque la app
    # alerta según dónde está físicamente alguien en el mapa (GPS), no según
    # dónde está registrado como residente. Por eso se combina con densidad
    # por área (score_ponderado_por_km2), que mide concentración de delito
    # en el espacio físico sin depender del censo de residentes.
    df["score_ponderado_por_km2"] = df["score_ponderado"] / df["area_km2"]

    # Score mixto: promedio de las dos métricas normalizadas a escala 0-1
    # (min-max sobre las 20 localidades), para que ninguna domine solo por
    # tener números más grandes en su unidad. Ponderación 50/50 documentada
    # como decisión de diseño (ver nota_score_mixto en el meta de salida):
    # se recomienda validarla con el asesor del proyecto.
    def normalizar_minmax(serie: pd.Series) -> pd.Series:
        minimo, maximo = serie.min(), serie.max()
        return (serie - minimo) / (maximo - minimo)

    df["score_mixto"] = (
        normalizar_minmax(df["score_ponderado_100k"]) * 0.5
        + normalizar_minmax(df["score_ponderado_por_km2"]) * 0.5
    )

    print("Clasificando riesgo con Jenks Natural Breaks sobre el score mixto (población + área)...")
    df["nivel_riesgo"], (q1, q2) = clasificar_por_jenks(df["score_mixto"])
    print(f"  Umbral bajo/medio: {q1:.3f} (score mixto, escala 0-1)")
    print(f"  Umbral medio/alto: {q2:.3f} (score mixto, escala 0-1)")

    # --- Construir salida JSON ---
    salida = {}
    for _, row in df.iterrows():
        salida[str(int(row["codigo"]))] = {
            "localidad": row["localidad"],
            "poblacion_2025": None if pd.isna(row["poblacion_total"]) else int(row["poblacion_total"]),
            "delitos_recientes_total_2023_2025": int(row["delitos_recientes_total"]),
            "tasa_delitos_100k": round(row["tasa_delitos_100k"], 2),
            "score_ponderado_100k": round(row["score_ponderado_100k"], 2),
            "score_ponderado_por_km2": round(row["score_ponderado_por_km2"], 2),
            "score_mixto": round(row["score_mixto"], 4),
            "nivel_riesgo": row["nivel_riesgo"],
            "detalle_delitos": row["detalle_delitos"],
            "contexto": {
                "estrato_promedio": None if pd.isna(row.get("estrato_promedio")) else round(row["estrato_promedio"], 2),
                "luminarias_estimadas": None if pd.isna(row.get("num_luminarias")) else int(row["num_luminarias"]),
                "luminarias_por_km2": None if pd.isna(row.get("luminarias_por_km2")) else round(row["luminarias_por_km2"], 1),
                "longitud_vias_km": None if pd.isna(row.get("longitud_vias_km")) else round(row["longitud_vias_km"], 1),
                "area_km2": round(row["area_km2"], 2),
                "incidentes_nuse_recientes_total": (
                    None if pd.isna(row.get("incidentes_nuse_recientes_total"))
                    else int(row["incidentes_nuse_recientes_total"])
                ),
                "detalle_incidentes_seguridad": (
                    row["detalle_incidentes_seguridad"]
                    if isinstance(row.get("detalle_incidentes_seguridad"), dict)
                    else None
                ),
                "accidentes_transito_recientes_total": (
                    None if pd.isna(row.get("accidentes_transito_recientes_total"))
                    else int(row["accidentes_transito_recientes_total"])
                ),
                "accidentes_domesticos": (
                    None if pd.isna(row.get("accidentes_domesticos_casos"))
                    else {
                        "anio": int(row["accidentes_domesticos_anio"]),
                        "casos": int(row["accidentes_domesticos_casos"]),
                        "tasa": round(row["accidentes_domesticos_tasa"], 2),
                    }
                ),
                "violencia_intrafamiliar_salud_recientes_total": (
                    None if pd.isna(row.get("violencia_intrafamiliar_salud_recientes_total"))
                    else int(row["violencia_intrafamiliar_salud_recientes_total"])
                ),
                "reportes_comunitarios_inseguridad_recientes_total": (
                    None if pd.isna(row.get("reportes_comunitarios_inseguridad_recientes_total"))
                    else int(row["reportes_comunitarios_inseguridad_recientes_total"])
                ),
                "organizaciones_comunitarias_registradas": (
                    None if pd.isna(row.get("organizaciones_comunitarias_registradas"))
                    else int(row["organizaciones_comunitarias_registradas"])
                ),
                "personas_atendidas_sdis_recientes_total": (
                    None if pd.isna(row.get("personas_atendidas_sdis_recientes_total"))
                    else int(row["personas_atendidas_sdis_recientes_total"])
                ),
            },
        }

    meta = {
        "_meta": {
            "metodologia": "reglas + geofencing (sin machine learning)",
            "periodo_delitos": f"promedio {min(ANIOS_RECIENTES)+2000}-{max(ANIOS_RECIENTES)+2000}",
            "granularidad": (
                "localidad (20 zonas). No se usó UPZ: el portal de datos "
                "abiertos de Bogotá no publica actualmente 'Delito de Alto "
                "Impacto' desagregado por UPZ, solo por localidad."
            ),
            "clasificacion": {
                "variable_usada": "score_mixto (promedio normalizado de score_ponderado_100k y score_ponderado_por_km2)",
                "metodo_corte": "Jenks Natural Breaks (3 clases)",
                "umbral_bajo_medio": round(q1, 4),
                "umbral_medio_alto": round(q2, 4),
            },
            "pesos_severidad": PESO_SEVERIDAD,
            "nota_pesos": (
                "Los pesos de severidad son una decision de diseno (no una "
                "cifra oficial): reflejan que delitos contra la vida/integridad "
                "deben pesar mas que hurtos sin violencia. Se recomienda "
                "validarlos con el asesor del proyecto."
            ),
            "nota_score_mixto": (
                "nivel_riesgo se calcula sobre score_mixto, NO solo sobre "
                "score_ponderado_100k. Motivo: normalizar unicamente por "
                "poblacion residente infla el riesgo de localidades pequenas "
                "con mucha poblacion flotante no residente -- comercio, "
                "turismo, transito -- (ej. Los Martires por San "
                "Victorino/Paloquemao, La Candelaria por el centro historico) "
                "y diluye localidades grandes y muy pobladas donde el delito "
                "violento en cifras absolutas es alto (ej. Ciudad Bolivar, "
                "Kennedy, Bosa), a pesar de que la app alerta segun donde esta "
                "fisicamente alguien (GPS), no segun donde vive registrado. "
                "score_mixto promedia score_ponderado_100k (por habitante) y "
                "score_ponderado_por_km2 (por area), cada uno normalizado 0-1 "
                "por min-max sobre las 20 localidades, con ponderacion 50/50. "
                "Es una decision de diseno documentada, no una formula "
                "oficial: se recomienda validarla con el asesor del proyecto. "
                "score_ponderado_100k y score_ponderado_por_km2 se conservan "
                "en la salida por transparencia/comparacion."
            ),
            "nota_etica": (
                "El estrato socioeconomico se reporta como contexto pero NO "
                "se usa para calcular nivel_riesgo, para evitar sesgar el "
                "modelo contra localidades de bajos ingresos."
            ),
            "nota_incidentes_nuse": (
                "incidentes_nuse_recientes_total (contexto.incidentes_nuse_recientes_total) "
                "es el volumen de llamadas de emergencia NUSE/123, tomado del historico "
                "mensual agregado (2015-actualidad) publicado por la SDSCJ. A diferencia "
                "de una version anterior de este pipeline, este dataset SI trae un "
                "diccionario publico de categorias (guiatipificacionincidentes.csv), asi "
                "que ademas del total se reporta detalle_incidentes_seguridad: un desglose "
                "por tipo de llamada relacionado con seguridad/convivencia (rina, hurto, "
                "porte ilegal de armas, etc. -- ver CATEGORIAS_INCIDENTE_NUSE_SEGURIDAD en "
                "el codigo). Se actualiza MENSUALMENTE (a diferencia del dataset de "
                "delitos, que es semestral/anual), pero sigue siendo volumen de LLAMADAS, "
                "no delito verificado -- por eso ni el total ni el desglose se usan para "
                "calcular nivel_riesgo."
            ),
            "nota_contexto_osb_y_sdis": (
                "accidentes_transito_recientes_total, accidentes_domesticos, "
                "violencia_intrafamiliar_salud_recientes_total, "
                "reportes_comunitarios_inseguridad_recientes_total, "
                "organizaciones_comunitarias_registradas y "
                "personas_atendidas_sdis_recientes_total vienen del Observatorio de "
                "Seguridad y Convivencia (SDSCJ) y de la Secretaria Distrital de "
                "Integracion Social (SDIS). Ninguno se usa para calcular nivel_riesgo: "
                "son reportes/registros administrativos (comunitarios, de salud o de "
                "atencion social), no delito verificado, y varios dependen de cobertura "
                "de servicios tanto como de necesidad real (mismo argumento que ya aplica "
                "al estrato). violencia_intrafamiliar_salud_recientes_total en particular "
                "NO debe sumarse a 'Violencia intrafamiliar' de detalle_delitos: son dos "
                "fuentes distintas (registro de salud vs. delito verificado por Fiscalia/"
                "Policia), con su propia subnotificacion cada una."
            ),
        }
    }

    resultado = {**meta, **salida}
    out_path = os.path.join(OUT_DIR, "zonas_riesgo.json")
    with open(out_path, "w", encoding="utf-8") as f:
        json.dump(resultado, f, ensure_ascii=False, indent=2)

    # Límites geográficos por localidad, para resolver "dado un punto GPS,
    # en qué localidad cae" (geofencing en el backend). Se reproyecta a
    # WGS84 (EPSG:4326) porque es el sistema de coordenadas que reporta el
    # GPS del celular (lat/lng), distinto del CRS oficial colombiano
    # (MAGNA-SIRGAS / EPSG:4686) en el que viene el dataset de origen.
    limites_path = os.path.join(OUT_DIR, "localidades.geojson")
    limites = df[["codigo", "localidad", "geometry"]].set_geometry("geometry").to_crs(4326)
    if os.path.exists(limites_path):
        os.remove(limites_path)  # to_file no sobreescribe GeoJSON existente
    limites.to_file(limites_path, driver="GeoJSON")
    print(f"Listo -> {limites_path}")

    # --- Capa secundaria: densidad de llamadas NUSE por UPZ ---
    # Más fina que la localidad (117 UPZ vs 20 localidades), pero basada en
    # llamadas de emergencia, NO en delito verificado: por diseño no toca
    # nivel_riesgo ni score_mixto, vive en su propio archivo con su propio
    # campo "nivel_llamadas" para que nunca se confunda con el score oficial.
    print("Calculando densidad de llamadas NUSE por UPZ (capa secundaria, no es delito verificado)...")
    # Umbral mínimo de población para clasificar: UPZ como El Mochuelo (7
    # habitantes censados, junto al relleno sanitario Doña Juana) o parques/
    # aeropuertos (Parque Simón Bolívar, El Dorado) tienen poca o ninguna
    # población residente pero actividad real, así que una sola llamada
    # dispara una tasa por habitante absurda (ej. 2'200,000 por 100k). Por
    # debajo del umbral no se clasifica (nivel_llamadas queda null) en vez
    # de mostrar un número engañoso.
    UMBRAL_POBLACION_MINIMA_UPZ = 1000

    incidentes_upz = cargar_incidentes_nuse_upz()
    if incidentes_upz is not None:
        poblacion_upz = cargar_poblacion_upz()
        upz_df = incidentes_upz.merge(poblacion_upz, on="codigo_upz", how="inner")
        upz_df = upz_df[upz_df["poblacion_total"] > 0].copy()
        upz_df["tasa_llamadas_100k"] = (
            upz_df["incidentes_nuse_recientes_total"] / upz_df["poblacion_total"] * 100_000
        )

        clasificable = upz_df["poblacion_total"] >= UMBRAL_POBLACION_MINIMA_UPZ
        upz_df["nivel_llamadas"] = None
        upz_df.loc[clasificable, "nivel_llamadas"], (uq1, uq2) = clasificar_por_jenks(
            upz_df.loc[clasificable, "tasa_llamadas_100k"]
        )

        salida_upz = {}
        for _, row in upz_df.iterrows():
            salida_upz[str(int(row["codigo_upz"]))] = {
                "upz": row["upz"],
                "poblacion_2025": int(row["poblacion_total"]),
                "incidentes_nuse_recientes_total": int(row["incidentes_nuse_recientes_total"]),
                "tasa_llamadas_100k": (
                    round(row["tasa_llamadas_100k"], 2) if row["poblacion_total"] >= UMBRAL_POBLACION_MINIMA_UPZ
                    else None
                ),
                "nivel_llamadas": row["nivel_llamadas"],
            }

        meta_upz = {
            "_meta": {
                "fuente": "Incidente Reportado (NUSE/C4): llamadas de emergencia, NO delito verificado",
                "advertencia": (
                    "Capa secundaria y mas ruidosa que nivel_riesgo por localidad "
                    "(volumen de llamadas, no delito confirmado). No mezclar como "
                    "si tuviera la misma certeza: usar solo como contexto adicional "
                    "de mayor resolucion espacial."
                ),
                "periodo": f"promedio {min(ANIOS_RECIENTES)+2000}-{max(ANIOS_RECIENTES)+2000}",
                "clasificacion": {
                    "variable_usada": "tasa_llamadas_100k",
                    "metodo_corte": "Jenks Natural Breaks (3 clases)",
                    "umbral_bajo_medio": round(uq1, 2),
                    "umbral_medio_alto": round(uq2, 2),
                    "poblacion_minima_para_clasificar": UMBRAL_POBLACION_MINIMA_UPZ,
                    "nota_poblacion_minima": (
                        "UPZ con menos poblacion residente que este umbral (parques, "
                        "aeropuertos, zonas rurales) no se clasifican: con tan pocos "
                        "habitantes censados, una sola llamada dispara una tasa por "
                        "habitante estadisticamente sin sentido. nivel_llamadas y "
                        "tasa_llamadas_100k quedan null en esos casos."
                    ),
                },
            }
        }
        resultado_upz = {**meta_upz, **salida_upz}
        upz_out_path = os.path.join(OUT_DIR, "upz_riesgo.json")
        with open(upz_out_path, "w", encoding="utf-8") as f:
            json.dump(resultado_upz, f, ensure_ascii=False, indent=2)
        print(f"Listo -> {upz_out_path}")

        upz_limites_path = os.path.join(OUT_DIR, "upz_limites.geojson")
        limites_upz = upz_df[["codigo_upz", "upz", "geometry"]].set_geometry("geometry").to_crs(4326)
        if os.path.exists(upz_limites_path):
            os.remove(upz_limites_path)
        limites_upz.to_file(upz_limites_path, driver="GeoJSON")
        print(f"Listo -> {upz_limites_path}")

    print(f"\nListo -> {out_path}")
    print(
        df[["localidad", "score_ponderado_100k", "score_ponderado_por_km2", "score_mixto", "nivel_riesgo"]]
        .sort_values("score_mixto", ascending=False)
        .to_string(index=False)
    )


if __name__ == "__main__":
    main()