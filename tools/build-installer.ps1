<#
    Compila NeonLauncher y genera el instalador .exe.

    Uso:
        $env:JAVA_HOME = "C:\Program Files\Eclipse Adoptium\jdk-21.0.12.101-hotspot"
        .\tools\build-installer.ps1 -Version 2.1.0

    La primera vez baja WiX 3.11 (~40 MB) a tools\wix311, que es lo que jpackage
    necesita para armar instaladores en Windows. No instala nada en el sistema.
#>
param(
    [string]$Version = "2.1.0",
    [string]$JdkHome = $env:JAVA_HOME
)

$ErrorActionPreference = 'Stop'
$proj = Split-Path -Parent $PSScriptRoot
Push-Location $proj

try {
    if (-not $JdkHome) { throw "Falta JAVA_HOME apuntando a un JDK 21." }
    $jpackage = Join-Path $JdkHome 'bin\jpackage.exe'
    if (-not (Test-Path $jpackage)) { throw "No encontrÃ© jpackage en $JdkHome" }

    # ---- 1. WiX portable -------------------------------------------------
    $wix = Join-Path $proj 'tools\wix311'
    if (-not (Test-Path (Join-Path $wix 'candle.exe'))) {
        Write-Host "Bajando WiX 3.11â€¦" -ForegroundColor Cyan
        $zip = Join-Path $env:TEMP 'wix311-binaries.zip'
        if (-not (Test-Path $zip)) {
            Invoke-WebRequest -UseBasicParsing `
                -Uri 'https://github.com/wixtoolset/wix3/releases/download/wix3112rtm/wix311-binaries.zip' `
                -OutFile $zip
        }
        Expand-Archive -Path $zip -DestinationPath $wix -Force
    }
    $env:WIX = $wix
    $env:PATH = "$wix;$env:PATH"

    # ---- 2. icono --------------------------------------------------------
    $icon = Join-Path $proj 'build\icon.ico'
    if (-not (Test-Path $icon)) {
        $python = (Get-Command python -ErrorAction SilentlyContinue).Source
        if ($python) {
            & $python (Join-Path $proj 'tools\make-icon.py') $icon (Join-Path $proj 'build\icon-256.png')
        } else {
            Write-Host "Sin icono (no hay python). Sigo igual." -ForegroundColor Yellow
        }
    }

    # ---- 3. build + app-image -------------------------------------------
    Write-Host "Compilandoâ€¦" -ForegroundColor Cyan
    # Ojo: en PowerShell 5.1 el exit code de gradlew.bat queda mal cuando se redirige la
    # salida, asÃ­ que se valida por el log y no por $LASTEXITCODE.
    $buildLog = Join-Path $proj 'build\installer-build.log'
    $ErrorActionPreference = 'Continue'
    & .\gradlew.bat --no-daemon --console=plain build :launcher-ui:installDist *> $buildLog
    $ErrorActionPreference = 'Stop'
    if (-not (Select-String -Path $buildLog -Pattern 'BUILD SUCCESSFUL' -Quiet)) {
        throw "FallÃ³ el build de Gradle (ver $buildLog)"
    }

    $lib = Join-Path $proj 'launcher-ui\build\install\launcher-ui\lib'
    $jar = (Get-ChildItem $lib -Filter 'launcher-ui-*.jar' | Select-Object -First 1).Name
    if (-not $jar) { throw "No encontrÃ© el jar de launcher-ui" }

    $dist = Join-Path $proj 'dist'
    $image = Join-Path $dist 'NeonLauncher'
    if (Test-Path $image) { Remove-Item $image -Recurse -Force }
    New-Item -ItemType Directory -Force $dist | Out-Null

    Write-Host "Armando la appâ€¦" -ForegroundColor Cyan
    $imageArgs = @(
        '--type', 'app-image',
        '--dest', $dist,
        '--name', 'NeonLauncher',
        '--app-version', $Version,
        '--input', $lib,
        '--main-jar', $jar,
        '--main-class', 'com.neonlauncher.ui.MainKt',
        '--java-options', '-Dfile.encoding=UTF-8',
        '--vendor', 'NeonShy Studios',
        '--description', 'Launcher de Minecraft'
    )
    if (Test-Path $icon) { $imageArgs += @('--icon', $icon) }

    $ErrorActionPreference = 'Continue'
    & $jpackage @imageArgs
    $imageExit = $LASTEXITCODE
    $ErrorActionPreference = 'Stop'
    if ($imageExit -ne 0) { throw "FallÃ³ jpackage --type app-image (cÃ³digo $imageExit)" }

    # ---- 4. instalador ---------------------------------------------------
    $out = Join-Path $dist 'installer'
    New-Item -ItemType Directory -Force $out | Out-Null
    Get-ChildItem $out -Filter '*.exe' -ErrorAction SilentlyContinue | Remove-Item -Force

    Write-Host "Empaquetando el instaladorâ€¦" -ForegroundColor Cyan
    $installerArgs = @(
        '--type', 'exe',
        '--dest', $out,
        '--name', 'NeonLauncher',
        '--app-image', $image,
        '--app-version', $Version,
        '--win-shortcut',
        '--win-menu',
        '--win-menu-group', 'NeonShy Studios',
        '--win-dir-chooser',
        '--win-per-user-install',
        '--vendor', 'NeonShy Studios',
        '--description', 'Launcher de Minecraft'
    )
    if (Test-Path $icon) { $installerArgs += @('--icon', $icon) }

    $ErrorActionPreference = 'Continue'
    & $jpackage @installerArgs
    $installerExit = $LASTEXITCODE
    $ErrorActionPreference = 'Stop'
    if ($installerExit -ne 0) { throw "FallÃ³ jpackage --type exe (cÃ³digo $installerExit)" }

    $built = Get-ChildItem $out -Filter '*.exe' | Select-Object -First 1
    Write-Host ""
    Write-Host "Listo: $($built.FullName)" -ForegroundColor Green
    Write-Host "Subilo a GitHub Releases con el tag v$Version y el nombre $($built.Name)."
}
finally {
    Pop-Location
}

