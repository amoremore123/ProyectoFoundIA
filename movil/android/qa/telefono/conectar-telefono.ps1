# Conecta un teléfono por USB con las APIs de QA de la laptop mediante adb reverse.
# El teléfono usa 127.0.0.1 y el tráfico pasa por el cable. No se abre ningún puerto en la red Wi-Fi.
# Uso: powershell -ExecutionPolicy Bypass -File movil\android\qa\telefono\conectar-telefono.ps1 [-Apk ruta.apk]
param([string]$Apk = "")

$adb = Join-Path $env:LOCALAPPDATA "Android\Sdk\platform-tools\adb.exe"
$telefonos = & $adb devices | Select-String -Pattern "^(\S+)\s+device$" | Where-Object { $_.Matches[0].Groups[1].Value -notlike "emulator-*" }
if (-not $telefonos) {
    Write-Host "No hay un teléfono conectado. Activa la depuración USB, conecta el cable y acepta el aviso en el teléfono."
    exit 1
}
if (@($telefonos).Count -gt 1) {
    Write-Host "Hay más de un teléfono conectado. Deja solo uno."
    exit 1
}
$serie = $telefonos[0].Matches[0].Groups[1].Value
Write-Host "Teléfono: $serie"

if ($Apk) {
    & $adb -s $serie install -r $Apk
    if ($LASTEXITCODE -ne 0) { exit 1 }
}

# Spring 18281, Django 18200 y web de usuario 18273, iguales a los puertos de QA de la laptop
foreach ($puerto in 18281, 18200, 18273) {
    & $adb -s $serie reverse "tcp:$puerto" "tcp:$puerto" | Out-Null
}
& $adb -s $serie reverse --list

foreach ($url in "http://127.0.0.1:18281/api/categorias", "http://127.0.0.1:18273/") {
    try {
        $codigo = (Invoke-WebRequest -UseBasicParsing -TimeoutSec 5 $url).StatusCode
        Write-Host "Laptop responde $codigo en $url"
    } catch {
        Write-Host "La laptop no responde en $url. Levanta Docker QA antes de probar."
    }
}
Write-Host "Listo. Si desconectas el cable, vuelve a ejecutar este script."
