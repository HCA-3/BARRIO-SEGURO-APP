# Script de compatibilidad para Barrio Seguro
$raiz = $PSScriptRoot
$scriptIniciar = Join-Path $raiz "iniciar.ps1"
if (Test-Path $scriptIniciar) {
    & powershell.exe -ExecutionPolicy Bypass -File $scriptIniciar
} else {
    Write-Host "No se encontro iniciar.ps1" -ForegroundColor Red
}
