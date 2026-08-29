"""
Actualizador de datos — Portal de Datos Abiertos de Bogotá (CKAN).

Revisa si las 3 fuentes de la Alcaldía que usa ProcesarRiesgo.py (delitos,
población, estratificación) tienen una versión más nueva publicada en
https://datosabiertos.bogota.gov.co, y opcionalmente la descarga,
reemplazando el archivo local en el mismo lugar donde ya está (para que
ProcesarRiesgo.py lo siga encontrando sin cambios).

No cubre la capa de OpenStreetMap (Bogota.gpkg): esa no la publica la
Alcaldía sino BBBike/OSM, así que no tiene un dataset CKAN que revisar aquí;
se sigue descargando a mano desde https://extract.bbbike.org si se quiere
una versión más reciente.

Uso:
  python actualizar_datos.py                        -> solo revisa, no descarga
  python actualizar_datos.py --descargar             -> descarga lo que cambió
  python actualizar_datos.py --descargar --forzar    -> descarga todo, aunque
                                                         el manifest diga que
                                                         no hay cambios
  python actualizar_datos.py --data-dir "C:\\ruta"    -> carpeta de datos
                                                         (por defecto, la
                                                         carpeta de este script)
"""

import argparse
import datetime
import glob
import io
import json
import os
import sys
import zipfile

import requests

if sys.stdout.encoding != "utf-8":
    sys.stdout.reconfigure(encoding="utf-8")

BASE_DIR = os.path.dirname(os.path.abspath(__file__))
CKAN_API = "https://datosabiertos.bogota.gov.co/api/3/action/package_show"
MANIFEST_NOMBRE = "data_manifest.json"

# Config de cada dataset: el "ckan_id" es el slug del dataset en el portal
# (se ve en la URL, ej. datosabiertos.bogota.gov.co/dataset/<ckan_id>).
# "archivo_local_patron" es el mismo patrón que ProcesarRiesgo.py usa para
# encontrar el archivo ya descargado, para saber dónde reemplazarlo.
DATASETS = {
    "delitos": {
        "label": "Delito de Alto Impacto por Localidad",
        "ckan_id": "delito-de-alto-impacto-bogota-d-c",
        "formato": "GeoJSON",
        # El dataset trae un GeoJSON de "Enero-Junio" (parcial) y uno de
        # "Enero-Diciembre" (año completo); queremos el de año completo.
        "preferir_en_nombre": ["diciembre"],
        "evitar_en_nombre": ["junio"],
        "archivo_local_patron": "DAILoc.geojson",
    },
    "poblacion": {
        "label": "Proyecciones de población por Localidad 2005-2035",
        "ckan_id": "proyecciones-y-retroproyecciones-de-poblacion-2005-2035",
        "formato": "ODS",
        "requerir_en_nombre": ["localidad"],
        "evitar_en_nombre": ["upz", "metadato"],
        "archivo_local_patron": "*localidad_proyeccion_retroproyeccion*.ods",
    },
    "poblacion_upz": {
        # Mismo dataset del portal que "poblacion", pero es un recurso
        # (archivo) distinto adentro del mismo paquete: población por UPZ en
        # vez de por localidad. La usa ProcesarRiesgo.py para la capa
        # secundaria de llamadas NUSE por UPZ, no para nivel_riesgo.
        "label": "Proyecciones de población por UPZ 2005-2035",
        "ckan_id": "proyecciones-y-retroproyecciones-de-poblacion-2005-2035",
        "formato": "ODS",
        "requerir_en_nombre": ["upz"],
        "evitar_en_nombre": ["metadato"],
        "archivo_local_patron": "*upz_proyeccion_retroproyeccion*.ods",
    },
    "estratificacion": {
        "label": "Estratificación socioeconómica por manzana",
        "ckan_id": "estratificacion-para-bogota",
        "formato": "GeoJSON",
        "archivo_local_patron": "manzanaestratificacion.json",
    },
    "incidentes_nuse": {
        "label": "Incidente Reportado (NUSE/C4)",
        "ckan_id": "incidente-reportado-bogota-d-c",
        "formato": "GeoJSON",
        # Igual que "delitos": el dataset trae un corte "Enero-Junio" (parcial)
        # y uno "enero-diciembre" (año completo); preferimos el completo. Este
        # dataset se actualiza MENSUALMENTE según su ficha técnica, así que el
        # corte "Enero-Junio" también se va actualizando durante el año — pero
        # igual preferimos el corte anual para que coincida con el período de
        # ANIOS_RECIENTES que usa ProcesarRiesgo.py.
        "preferir_en_nombre": ["diciembre"],
        "evitar_en_nombre": ["junio"],
        # El recurso es un .zip con 3 archivos (localidad/UPZ/sector
        # catastral): ProcesarRiesgo.py usa el de localidad (contexto) y el
        # de UPZ (capa secundaria de llamadas). El de sector catastral no se
        # usa todavía. Los 3 quedan en la misma carpeta al descomprimir, así
        # que con revisar el de localidad alcanza para saber si hay versión
        # nueva de los 3 juntos.
        "archivo_local_patron": "IRLoc.geojson",
    },
}


def parse_args():
    p = argparse.ArgumentParser(
        description="Revisa/descarga actualizaciones de los datasets de la Alcaldía de Bogotá."
    )
    p.add_argument("--data-dir", default=BASE_DIR, help="Carpeta donde están (o deben quedar) los datasets")
    p.add_argument("--descargar", action="store_true", help="Descargar y reemplazar los datasets que cambiaron")
    p.add_argument("--forzar", action="store_true", help="Descargar aunque el manifest diga que no hay cambios")
    return p.parse_args()


def obtener_recursos_ckan(ckan_id: str) -> list:
    resp = requests.get(CKAN_API, params={"id": ckan_id}, timeout=30)
    resp.raise_for_status()
    data = json.loads(resp.content.decode("utf-8"))
    if not data.get("success"):
        raise RuntimeError(f"El portal de datos abiertos no encontró el dataset '{ckan_id}'")
    return data["result"]["resources"]


def elegir_recurso(cfg: dict, recursos: list) -> dict:
    def contiene(nombre_norm, palabras):
        return all(p in nombre_norm for p in palabras)

    candidatos = [r for r in recursos if r.get("format", "").upper() == cfg["formato"].upper()]

    for filtro in ("requerir_en_nombre", "evitar_en_nombre", "preferir_en_nombre"):
        pass  # aplicados abajo explícitamente para mantener el orden claro

    if cfg.get("requerir_en_nombre"):
        candidatos = [
            r for r in candidatos
            if all(p in r.get("name", "").lower() for p in cfg["requerir_en_nombre"])
        ]
    if cfg.get("evitar_en_nombre"):
        candidatos = [
            r for r in candidatos
            if not any(p in r.get("name", "").lower() for p in cfg["evitar_en_nombre"])
        ]
    if not candidatos:
        raise RuntimeError(
            f"No encontré ningún recurso formato={cfg['formato']} para '{cfg['label']}' "
            f"que cumpla los filtros de nombre configurados."
        )

    if cfg.get("preferir_en_nombre"):
        preferidos = [
            r for r in candidatos
            if any(p in r.get("name", "").lower() for p in cfg["preferir_en_nombre"])
        ]
        if preferidos:
            candidatos = preferidos

    # Entre lo que quede, el más reciente según el portal.
    candidatos.sort(key=lambda r: r.get("last_modified") or r.get("created") or "", reverse=True)
    return candidatos[0]


def buscar_archivo_local(patron: str, data_dir: str) -> str | None:
    coincidencias = glob.glob(os.path.join(data_dir, "**", patron), recursive=True)
    return coincidencias[0] if coincidencias else None


def cargar_manifest(data_dir: str) -> dict:
    ruta = os.path.join(data_dir, MANIFEST_NOMBRE)
    if os.path.exists(ruta):
        with open(ruta, "r", encoding="utf-8") as f:
            return json.load(f)
    return {}


def guardar_manifest(data_dir: str, manifest: dict):
    ruta = os.path.join(data_dir, MANIFEST_NOMBRE)
    with open(ruta, "w", encoding="utf-8") as f:
        json.dump(manifest, f, ensure_ascii=False, indent=2)


def descargar_y_reemplazar(recurso: dict, cfg: dict, archivo_local: str | None, data_dir: str) -> str:
    """
    Descarga el recurso y lo deja en el mismo lugar donde ya estaba el
    archivo local (sobrescribiéndolo), o en una carpeta nueva
    'data_dir/<clave_dataset>/' si es la primera vez que se descarga.
    Si el recurso es un .zip, lo extrae ahí mismo.
    """
    url = recurso["url"]
    resp = requests.get(url, timeout=120)
    resp.raise_for_status()

    if archivo_local:
        destino_dir = os.path.dirname(archivo_local)
    else:
        destino_dir = os.path.join(data_dir, cfg["label"].lower().replace(" ", "_"))
        os.makedirs(destino_dir, exist_ok=True)

    if url.lower().endswith(".zip"):
        with zipfile.ZipFile(io.BytesIO(resp.content)) as z:
            z.extractall(destino_dir)
        return destino_dir
    else:
        nombre = os.path.basename(url)
        destino = os.path.join(destino_dir, nombre)
        with open(destino, "wb") as f:
            f.write(resp.content)
        return destino


def main():
    args = parse_args()
    data_dir = args.data_dir
    manifest = cargar_manifest(data_dir)

    print(f"Consultando datosabiertos.bogota.gov.co para {len(DATASETS)} datasets...\n")

    for clave, cfg in DATASETS.items():
        print(f"[{cfg['label']}]")
        try:
            recursos = obtener_recursos_ckan(cfg["ckan_id"])
            recurso = elegir_recurso(cfg, recursos)
        except Exception as e:
            print(f"  ERROR consultando el portal: {e}\n")
            continue

        fecha_remota = recurso.get("last_modified") or recurso.get("created")
        registro_local = manifest.get(clave)
        archivo_local = buscar_archivo_local(cfg["archivo_local_patron"], data_dir)

        if registro_local is None:
            estado = "sin registro previo (probablemente descargado a mano)"
            hay_cambio = True
        elif registro_local.get("last_modified") != fecha_remota:
            estado = f"HAY VERSIÓN NUEVA (local: {registro_local.get('last_modified')})"
            hay_cambio = True
        else:
            estado = "al día"
            hay_cambio = False

        print(f"  Última actualización en el portal: {fecha_remota}")
        print(f"  Archivo local: {archivo_local or '(no encontrado en data-dir)'}")
        print(f"  Estado: {estado}")

        if args.descargar and (hay_cambio or args.forzar):
            print("  Descargando...")
            destino = descargar_y_reemplazar(recurso, cfg, archivo_local, data_dir)
            manifest[clave] = {
                "resource_id": recurso.get("id"),
                "url": recurso["url"],
                "last_modified": fecha_remota,
                "actualizado_en": datetime.datetime.now().isoformat(timespec="seconds"),
            }
            print(f"  Descargado en: {destino}")
        elif hay_cambio and not args.descargar:
            print("  (usa --descargar para traer la versión nueva)")

        print()

    guardar_manifest(data_dir, manifest)
    print(f"Manifest guardado en {os.path.join(data_dir, MANIFEST_NOMBRE)}")


if __name__ == "__main__":
    main()
