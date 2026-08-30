# Plan de Ajuste y Ejecución de Barrio Seguro

Este plan detalla los ajustes técnicos necesarios para asegurar que la aplicación Android y su backend funcionen correctamente en entornos modernos (Android 14+), se estabilice la configuración de compilación y se documente el proceso de ejecución.

## User Review Required

> [!IMPORTANT]
> Se ha detectado el uso de versiones de AGP (9.3.2) y Kotlin (2.2.10+) muy recientes/futuras. El plan mantiene estas versiones ya que el proyecto compila con ellas, pero se estabilizará el `compileSdk` a la versión 35 (Android 15) por ser la más estable actualmente, evitando la versión 36 que aún está en pre-lanzamiento.

## Proposed Changes

### Componente Android (`app`)

#### [MODIFY] [MonitoreoUbicacionService.kt](file:///C:/Users/dsant/Desktop/U%20CATOLICA/Programas/BARRIO%20SEGURO%20APP/app/src/main/java/com/example/riesgossocialesenchapinero/location/MonitoreoUbicacionService.kt)
- Ajustar la llamada a `startForeground` para incluir explícitamente el tipo `FOREGROUND_SERVICE_TYPE_LOCATION`, obligatorio desde Android 14 (API 34) cuando se usa este tipo de servicio.

#### [MODIFY] [build.gradle.kts](file:///C:/Users/dsant/Desktop/U%20CATOLICA/Programas/BARRIO%20SEGURO%20APP/app/build.gradle.kts)
- Cambiar `compileSdk` de `release(36)` a `35` para asegurar compatibilidad con las herramientas actuales de Android Studio.
- Ajustar `targetSdk` a `35`.

#### [MODIFY] [MainActivity.kt](file:///C:/Users/dsant/Desktop/U%20CATOLICA/Programas/BARRIO%20SEGURO%20APP/app/src/main/java/com/example/riesgossocialesenchapinero/MainActivity.kt)
- Añadir una pequeña validación visual o log para confirmar la conexión con el backend al inicio.

---

### Documentación y Configuración

#### [MODIFY] [README.md](file:///C:/Users/dsant/Desktop/U%20CATOLICA/Programas/BARRIO%20SEGURO%20APP/README.md)
- Completar la sección de "Configuración y Uso" con los comandos exactos para:
  1. Instalar dependencias de Python.
  2. Ejecutar el pipeline de datos (`ProcesarRiesgo.py`).
  3. Levantar la API (`backend_riesgo.py`).
  4. Ejecutar la app en el emulador (explicando la importancia de `10.0.2.2`).

## Verification Plan

### Automated Tests
- Ejecutar `./gradlew clean assembleDebug` para verificar que los cambios en `build.gradle.kts` no rompen la compilación.

### Manual Verification
- Verificar visualmente los cambios en el código.
- Revisar que el `README.md` sea claro y contenga toda la información necesaria.
