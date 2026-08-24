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


def quitar_tildes(texto: str) -> str:
    nfkd = unicodedata.normalize("NFKD", texto)
    return "".join(c for c in nfkd if not unicodedata.combining(c)).upper().strip()


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

    print("Clasificando riesgo con Jenks Natural Breaks sobre el score ponderado por severidad...")
    df["nivel_riesgo"], (q1, q2) = clasificar_por_jenks(df["score_ponderado_100k"])
    print(f"  Umbral bajo/medio: {q1:.1f} (score ponderado/100k)")
    print(f"  Umbral medio/alto: {q2:.1f} (score ponderado/100k)")

    # --- Construir salida JSON ---
    salida = {}
    for _, row in df.iterrows():
        salida[str(int(row["codigo"]))] = {
            "localidad": row["localidad"],
            "poblacion_2025": None if pd.isna(row["poblacion_total"]) else int(row["poblacion_total"]),
            "delitos_recientes_total_2023_2025": int(row["delitos_recientes_total"]),
            "tasa_delitos_100k": round(row["tasa_delitos_100k"], 2),
            "score_ponderado_100k": round(row["score_ponderado_100k"], 2),
            "nivel_riesgo": row["nivel_riesgo"],
            "detalle_delitos": row["detalle_delitos"],
            "contexto": {
                "estrato_promedio": None if pd.isna(row.get("estrato_promedio")) else round(row["estrato_promedio"], 2),
                "luminarias_estimadas": None if pd.isna(row.get("num_luminarias")) else int(row["num_luminarias"]),
                "luminarias_por_km2": None if pd.isna(row.get("luminarias_por_km2")) else round(row["luminarias_por_km2"], 1),
                "longitud_vias_km": None if pd.isna(row.get("longitud_vias_km")) else round(row["longitud_vias_km"], 1),
                "area_km2": round(row["area_km2"], 2),
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
                "variable_usada": "score_ponderado_100k (delitos ponderados por severidad, por 100k habitantes)",
                "metodo_corte": "Jenks Natural Breaks (3 clases)",
                "umbral_bajo_medio": round(q1, 2),
                "umbral_medio_alto": round(q2, 2),
            },
            "pesos_severidad": PESO_SEVERIDAD,
            "nota_pesos": (
                "Los pesos de severidad son una decision de diseno (no una "
                "cifra oficial): reflejan que delitos contra la vida/integridad "
                "deben pesar mas que hurtos sin violencia. Se recomienda "
                "validarlos con el asesor del proyecto."
            ),
            "nota_etica": (
                "El estrato socioeconomico se reporta como contexto pero NO "
                "se usa para calcular nivel_riesgo, para evitar sesgar el "
                "modelo contra localidades de bajos ingresos."
            ),
        }
    }

    resultado = {**meta, **salida}
    out_path = os.path.join(OUT_DIR, "zonas_riesgo.json")
    with open(out_path, "w", encoding="utf-8") as f:
        json.dump(resultado, f, ensure_ascii=False, indent=2)

    print(f"\nListo -> {out_path}")
    print(
        df[["localidad", "tasa_delitos_100k", "score_ponderado_100k", "nivel_riesgo"]]
        .sort_values("score_ponderado_100k", ascending=False)
        .to_string(index=False)
    )


if __name__ == "__main__":
    main()