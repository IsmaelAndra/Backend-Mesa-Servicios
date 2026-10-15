# =============================================================================
# Instala el puente de telefonia como tarea de Windows (sin Docker).
#
#   1. Instala Node.js 20 LTS o superior (https://nodejs.org).
#   2. Copia esta carpeta al servidor (por ejemplo C:\MesaPuenteTelefonia).
#   3. Crea el archivo .env a partir de .env.example (MESA_URL y PUENTE_TOKEN).
#   4. Abre PowerShell COMO ADMINISTRADOR en esta carpeta y ejecuta:
#        powershell -ExecutionPolicy Bypass -File .\instalar-windows.ps1
#
# Crea la tarea "Mesa - Puente de telefonia": arranca con Windows (aunque nadie
# inicie sesion), se reinicia sola si se cae y escribe en puente.log.
# Para quitarla: .\desinstalar-windows.ps1
# =============================================================================
$ErrorActionPreference = 'Stop'
$nombre = 'Mesa - Puente de telefonia'
$carpeta = $PSScriptRoot

$admin = ([Security.Principal.WindowsPrincipal][Security.Principal.WindowsIdentity]::GetCurrent()).IsInRole(
    [Security.Principal.WindowsBuiltInRole]::Administrator)
if (-not $admin) { throw 'Ejecuta este script en PowerShell como Administrador.' }

# Node.js: primero en el PATH de esta ventana; si se instalo despues de abrirla,
# en el PATH guardado del sistema y en las carpetas de instalacion habituales.
$node = (Get-Command node.exe -ErrorAction SilentlyContinue).Source
if (-not $node) {
    $rutas = ([Environment]::GetEnvironmentVariable('Path', 'Machine') + ';' +
              [Environment]::GetEnvironmentVariable('Path', 'User')).Split(';') +
             @("$env:ProgramFiles\nodejs", "${env:ProgramFiles(x86)}\nodejs", "$env:LOCALAPPDATA\Programs\nodejs")
    $node = $rutas | Where-Object { $_ } | ForEach-Object { Join-Path $_.Trim() 'node.exe' } |
        Where-Object { Test-Path $_ } | Select-Object -First 1
}
if (-not $node) { throw 'No se encontro Node.js. Instala Node.js 20 LTS o superior desde https://nodejs.org y vuelve a ejecutar.' }
Write-Host "Node.js: $node"
$version = (& $node --version).TrimStart('v')
if ([int]($version.Split('.')[0]) -lt 20) { throw "Se necesita Node.js 20 o superior (tienes $version)." }

if (-not (Test-Path (Join-Path $carpeta '.env'))) {
    throw 'Falta el archivo .env. Copialo desde .env.example y pon MESA_URL y PUENTE_TOKEN.'
}

# El .env tiene el token: solo administradores y SYSTEM pueden leerlo.
# Se usan los SID (S-1-5-32-544 = Administradores, S-1-5-18 = SYSTEM) porque el
# nombre del grupo cambia con el idioma de Windows ("Administrators" en ingles).
icacls (Join-Path $carpeta '.env') /inheritance:r /grant:r '*S-1-5-32-544:F' '*S-1-5-18:F' | Out-Null
if ($LASTEXITCODE -ne 0) { throw 'No se pudieron ajustar los permisos del archivo .env.' }

$log = Join-Path $carpeta 'puente.log'
$accion = New-ScheduledTaskAction -Execute 'cmd.exe' `
    -Argument "/c `"`"$node`" `"$carpeta\index.mjs`" >> `"$log`" 2>&1`"" -WorkingDirectory $carpeta
$inicio = New-ScheduledTaskTrigger -AtStartup
$ajustes = New-ScheduledTaskSettingsSet -RestartCount 999 -RestartInterval (New-TimeSpan -Minutes 1) `
    -ExecutionTimeLimit ([TimeSpan]::Zero) -StartWhenAvailable -AllowStartIfOnBatteries -DontStopIfGoingOnBatteries
$cuenta = New-ScheduledTaskPrincipal -UserId 'SYSTEM' -LogonType ServiceAccount -RunLevel Highest

if (Get-ScheduledTask -TaskName $nombre -ErrorAction SilentlyContinue) {
    Stop-ScheduledTask -TaskName $nombre -ErrorAction SilentlyContinue
    Unregister-ScheduledTask -TaskName $nombre -Confirm:$false
}
Register-ScheduledTask -TaskName $nombre -Action $accion -Trigger $inicio -Settings $ajustes -Principal $cuenta `
    -Description 'Recoge las llamadas de la Mesa de Servicios y las ejecuta en la central UCM.' | Out-Null
Start-ScheduledTask -TaskName $nombre

Write-Host ''
Write-Host "Instalado y en marcha: '$nombre'." -ForegroundColor Green
Write-Host "Revisa el log en: $log"
Write-Host 'En la mesa, Administracion -> Telefonia debe mostrar el puente "En linea" en unos segundos.'
