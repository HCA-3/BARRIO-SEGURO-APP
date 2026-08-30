# 🛡️ BARRIO SEGURO APP

Una aplicación integral diseñada para la gestión de riesgos sociales, predicciones y seguridad ciudadana, enfocada en la localidad de Chapinero (Bogotá).

## 🚀 Estructura del Proyecto

Este repositorio se divide en tres componentes principales:

1. **`app/` (Aplicación Android):** 
   - Interfaz móvil nativa (Kotlin/Compose).
   - Conexión con la API para mostrar zonas de riesgo y agente de chat.

2. **`Api/` (Backend / Servicios):** 
   - API construida con FastAPI para servir datos y chat con LLM local.
   - Script principal: `backend_riesgo.py`

3. **`Agente/` (Procesamiento de Datos e IA):** 
   - Scripts de Python para procesamiento de datos geoespaciales y entrenamiento del pipeline de riesgo.

## ⚙️ Configuración y Uso

### 1. Requisitos Previos
- **Ollama:** Descargar e instalar desde [ollama.com](https://ollama.com/).
- **Modelo:** Ejecutar `ollama pull llama3.1` para descargar el modelo de lenguaje.
- **Python 3.10+**

### 2. Preparación del Backend
Desde una terminal en la raíz del proyecto:

```bash
# 1. Crear y activar entorno virtual
python -m venv venv
.\venv\Scripts\activate

# 2. Instalar dependencias
pip install -r Agente/requirements.txt
pip install fastapi uvicorn

# 3. Procesar datos iniciales (Genera los JSON y GeoJSON necesarios)
python Agente/ProcesarRiesgo.py

# 4. Iniciar el servidor API
uvicorn Api.backend_riesgo:app --host 0.0.0.0 --port 8000 --reload
```

#### Atajo: `run_api.py`

En esta máquina las dependencias quedaron repartidas en dos entornos
(`venv/` con fastapi+uvicorn, `Agente/.venv/` con geopandas+pandas+shapely),
así que `uvicorn Api.backend_riesgo:app` falla con `ModuleNotFoundError`
desde cualquiera de los dos. `run_api.py` une los dos `site-packages` y
arranca el servidor sin reinstalar nada:

```bash
venv\Scripts\python.exe run_api.py --no-reload
```

Si algún día unificas los entornos (paso 2 de arriba dentro de `venv`),
`run_api.py` sigue sirviendo igual.

#### Comprobar que quedó bien

```bash
curl http://127.0.0.1:8000/health
curl http://127.0.0.1:8000/zonas/ranking
curl "http://127.0.0.1:8000/riesgo?lat=4.6486&lng=-74.0570"
```

`/health` debe responder `ollama_disponible: true` y `localidades_cargadas: 20`.

> Nota: en Windows PowerShell 5.1, `Invoke-RestMethod` con `-Method Post`
> contra `127.0.0.1` se queda colgado sin llegar a enviar la petición. Para
> probar `/chat` desde consola usa `curl.exe` o Python (`requests`), no
> `Invoke-RestMethod`.

### 3. Ejecución de la App Android
1. Abre el proyecto en **Android Studio**.
2. Compila y ejecuta el módulo `:app`.

La app ya no trae la URL del backend fija en el código. Al arrancar prueba,
en orden, `10.0.2.2:8000` (emulador), `127.0.0.1:8000` (USB con `adb reverse`)
y la IP LAN del PC, y se queda con la primera que responda. La guarda, así que
solo hace falta acertar una vez. Si ninguna sirve, la pantalla de error trae un
campo **Servidor** donde puedes escribir la IP a mano (`192.168.0.107:8000`)
sin recompilar.

#### Celular físico: usa USB (`adb reverse`)

Es la forma que funciona siempre, porque **no depende de la red ni del
firewall**:

```bash
adb reverse tcp:8000 tcp:8000
```

Con eso, el `127.0.0.1:8000` del celular sale al `8000` del PC. **Hay que
repetirlo cada vez que se reconecta el cable**, y es la causa más común de que
la app diga "No pude conectarme al backend" después de haber funcionado.
Sirve también para el emulador.

Para comprobar que el túnel está puesto, sin depender de la app:

```bash
adb reverse --list                              # debe listar tcp:8000 tcp:8000
adb shell curl -s http://127.0.0.1:8000/health  # se consulta DESDE el celular
```

#### Celular físico por wifi

Solo funciona si se cumplen las dos cosas:

1. **Misma subred.** Compara la IP del PC (`ipconfig`) con la del celular
   (Ajustes → Wi‑Fi → red conectada). Si el PC está en `192.168.0.x` y el
   celular en `192.168.1.x`, son routers distintos y no se ven: conecta el
   celular a la misma red del PC.
2. **Firewall abierto en el 8000.** Windows lo bloquea por defecto. En
   PowerShell **como administrador**, una sola vez:

   ```powershell
   New-NetFirewallRule -DisplayName "Barrio Seguro API" -Direction Inbound `
     -Protocol TCP -LocalPort 8000 -Action Allow -Profile Private
   ```

   (`-Profile Private` a propósito: no conviene exponer el puerto en redes
   públicas.)

Luego escribe la IP del PC en el campo **Servidor** de la app.

Desde consola: `gradlew.bat :app:assembleDebug` (el APK queda en
`app/build/outputs/apk/debug/app-debug.apk`).

> **JDK:** `gradle.properties` fija `org.gradle.java.home` al JBR que trae
> Android Studio. Hace falta porque el `JAVA_HOME` de esta máquina apunta a
> un *JRE*, y el JRE no trae `jlink`, que AGP necesita para
> `JdkImageTransform`; sin eso la build falla en
> `:app:compileDebugJavaWithJavac`. Si compilas en otra máquina, ajusta esa
> ruta a tu JDK 17+. Si cambias el valor con un daemon vivo, corre primero
> `gradlew.bat --stop`: el daemon en marcha ignora el nuevo JDK.

### 4. Permisos de ubicación

La app pide los permisos **al entrar, y solo si no están concedidos**, en un
único diálogo (ubicación precisa/aproximada + notificaciones). Una vez que el
usuario acepta, no vuelve a salir nunca.

Si los niega, el botón de la tarjeta pasa a decir "Ajustes" y lleva a la
pantalla del sistema para concederlos a mano. En el siguiente arranque se
vuelve a preguntar (Android deja de mostrar el diálogo por su cuenta tras dos
negativas), a propósito: bloquear la petición para siempre dejaría la app
inservible a quien le dio "No permitir" sin querer.

Para volver a probar el flujo desde cero:

```bash
adb shell pm revoke com.example.riesgossocialesenchapinero android.permission.ACCESS_FINE_LOCATION
adb shell pm revoke com.example.riesgossocialesenchapinero android.permission.ACCESS_COARSE_LOCATION
```

## 🛠️ Tecnologías Utilizadas
- **Frontend:** Jetpack Compose, Material 3, Room, OkHttp.
- **Backend:** FastAPI, Pandas, GeoPandas, Ollama.
