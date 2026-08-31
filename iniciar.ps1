# Arranca todo lo que necesita "Barrio Seguro" y deja el backend corriendo.
#
#   .\iniciar.ps1
#
# Hace, en orden:
#   1. Levanta Ollama si no responde.
#   2. Levanta el backend (run_api.py) si el puerto 8000 esta libre.
#   3. Abre el tunel USB al celular (adb reverse), que se cae al desconectar
#      el cable y es la causa mas comun de "No pude conectarme al backend".
#   4. Comprueba que el celular llega al backend de verdad.
#
# No pide permisos de administrador ni toca el firewall.

$ErrorActionPreference = "Stop"
$raiz = $PSScriptRoot
$candidatosPython = @(
    (Join-Path $raiz "Agente\.venv\Scripts\python.exe"),
    (Join-Path $raiz "venv\Scripts\python.exe"),
    (Join-Path $raiz "venv_app\Scripts\python.exe")
)
$python = $candidatosPython | Where-Object { Test-Path $_ } | Select-Object -First 1
$adb = Join-Path $env:LOCALAPPDATA "Android\Sdk\platform-tools\adb.exe"

function Responde($url) {
    try { $null = Invoke-WebRequest $url -TimeoutSec 3 -UseBasicParsing; return $true }
    catch { return $false }
}

# --- 1. Ollama ---------------------------------------------------------------
if (Responde "http://localhost:11434/api/tags") {
    Write-Host "[ok] Ollama ya esta corriendo."
} else {
    Write-Host "[..] Arrancando Ollama..."
    Start-Process -FilePath "ollama" -ArgumentList "serve" -WindowStyle Hidden
    foreach ($i in 1..20) {
        Start-Sleep -Seconds 1
        if (Responde "http://localhost:11434/api/tags") { break }
    }
    if (Responde "http://localhost:11434/api/tags") { Write-Host "[ok] Ollama arriba." }
    else { Write-Host "[!!] Ollama no respondio. Abrelo a mano y reintenta."; exit 1 }
}

# --- 2. Backend --------------------------------------------------------------
if (Responde "http://127.0.0.1:8000/health") {
    Write-Host "[ok] El backend ya esta corriendo en el puerto 8000."
} else {
    if (-not $python -or -not (Test-Path $python)) {
        Write-Host "[!!] No encuentro el entorno Python virtual. Crea el entorno con: python -m venv venv"
        exit 1
    }
    Write-Host "[..] Arrancando el backend (tarda ~20s en cargar los datos geo)..."
    Start-Process -FilePath $python -ArgumentList "run_api.py", "--no-reload" -WorkingDirectory $raiz
    foreach ($i in 1..60) {
        Start-Sleep -Seconds 1
        if (Responde "http://127.0.0.1:8000/health") { break }
    }
    if (Responde "http://127.0.0.1:8000/health") { Write-Host "[ok] Backend arriba." }
    else { Write-Host "[!!] El backend no arranco. Corre '$python run_api.py' a mano para ver el error."; exit 1 }
}

# --- 3. Tunel al celular -----------------------------------------------------
if (-not (Test-Path $adb)) {
    Write-Host "[--] No encuentro adb; me salto el tunel USB. Si usas el emulador no hace falta."
} else {
    $dispositivos = & $adb devices | Select-String -Pattern "\sdevice$"
    if (-not $dispositivos) {
        Write-Host "[--] No hay ningun celular conectado por USB."
        Write-Host "     Conectalo con depuracion USB activada y vuelve a correr este script."
    } else {
        & $adb reverse tcp:8000 tcp:8000 | Out-Null
        Write-Host "[ok] Tunel USB puesto (el 127.0.0.1:8000 del celular -> este PC)."

        # Comprobacion de verdad: se consulta DESDE el celular, no desde el PC.
        $desdeElCelular = & $adb shell curl -s -m 10 http://127.0.0.1:8000/health
        if ($desdeElCelular -match "ok") {
            Write-Host "[ok] El celular llega al backend: $desdeElCelular"
        } else {
            Write-Host "[!!] El celular NO llega al backend. Respuesta: $desdeElCelular"
        }
    }
}

Write-Host ""
Write-Host "Listo. Abre la app en el celular."
Write-Host "Recuerda: si desconectas el cable, vuelve a correr este script."
