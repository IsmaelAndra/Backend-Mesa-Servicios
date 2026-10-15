# Quita la tarea del puente de telefonía (ejecutar en PowerShell como Administrador).
#   powershell -ExecutionPolicy Bypass -File .\desinstalar-windows.ps1
$ErrorActionPreference = 'Stop'
$nombre = 'Mesa - Puente de telefonia'
if (Get-ScheduledTask -TaskName $nombre -ErrorAction SilentlyContinue) {
    Stop-ScheduledTask -TaskName $nombre -ErrorAction SilentlyContinue
    Get-CimInstance Win32_Process -Filter "Name='node.exe'" |
        Where-Object { $_.CommandLine -like "*$PSScriptRoot*index.mjs*" } |
        ForEach-Object { Stop-Process -Id $_.ProcessId -Force }
    Unregister-ScheduledTask -TaskName $nombre -Confirm:$false
    Write-Host "Tarea '$nombre' eliminada." -ForegroundColor Green
} else {
    Write-Host "La tarea '$nombre' no estaba instalada."
}
