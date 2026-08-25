<#
Encadena actualizar_datos.py + ProcesarRiesgo.py para refrescar
output/zonas_riesgo.json con lo ultimo publicado en el portal de Datos
Abiertos de Bogota, sin tener que correrlo a mano cada vez.

actualizar_datos.py --descargar solo descarga cuando el portal realmente
cambio algo (compara contra data_manifest.json), asi que correr esto
seguido no gasta ancho de banda de mas.

Pensado para Task Scheduler de Windows, por eso usa rutas absolutas y
registra todo en un log en vez de imprimir a una consola que nadie va a
ver.

Uso manual:
  powershell -File actualizar_riesgo.ps1
#>

$ErrorActionPreference = "Continue"
# Debe ser "Continue": con "Stop", cada linea que python escriba a stderr
# (ej. un traceback) se vuelve un error terminante de PowerShell y el
# script se corta ahi mismo, ANTES de llegar al chequeo de $LASTEXITCODE
# de mas abajo -- un fallo real de ProcesarRiesgo.py quedaria a medias en
# el log, sin la linea "ERROR:" que se supone que lo deja claro.

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location $scriptDir

$logFile = Join-Path $scriptDir "actualizacion.log"
$python = "C:\Python313\python.exe"
if (-not (Test-Path $python)) {
    # Si el interprete se movio o esto corre en otra maquina, usar el
    # que este en PATH en vez de fallar de una.
    $python = "python"
}

function Log-Linea($texto) {
    "$texto" | Out-File -FilePath $logFile -Append -Encoding utf8
}

Log-Linea "===== $(Get-Date -Format 'yyyy-MM-dd HH:mm:ss') ====="

# Los procesos externos (python.exe) NO lanzan una excepcion de PowerShell
# solo porque terminen con codigo de error -- hay que revisar $LASTEXITCODE
# a mano, o un fallo real de ProcesarRiesgo.py quedaria registrado como
# "OK" en el log sin que nadie se entere.

Log-Linea "--- actualizar_datos.py --descargar ---"
& $python "actualizar_datos.py" "--descargar" 2>&1 | Out-File -FilePath $logFile -Append -Encoding utf8
if ($LASTEXITCODE -ne 0) {
    # No fatal: ProcesarRiesgo.py igual puede correr sobre los datos
    # locales que ya haya, aunque el chequeo contra el portal haya fallado
    # (ej. el portal no respondio esta vez).
    Log-Linea "AVISO: actualizar_datos.py salio con codigo $LASTEXITCODE. Se sigue con los datos locales que ya haya."
}

Log-Linea "--- ProcesarRiesgo.py ---"
& $python "ProcesarRiesgo.py" 2>&1 | Out-File -FilePath $logFile -Append -Encoding utf8
if ($LASTEXITCODE -ne 0) {
    Log-Linea "ERROR: ProcesarRiesgo.py fallo con codigo $LASTEXITCODE. zonas_riesgo.json puede haber quedado desactualizado."
}
else {
    Log-Linea "OK - zonas_riesgo.json actualizado."
}
