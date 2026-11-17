# Conecta teléfonos y emuladores con las APIs de QA de la laptop mediante adb reverse.
# El dispositivo usa 127.0.0.1 y el tráfico pasa por el cable o por el emulador. No se abre ningún puerto en la red Wi-Fi.
# Sirve el mismo APK para el teléfono y el emulador, compilado con las URLs 127.0.0.1 (ver guia-prueba-telefono.md).
# Uso: powershell -ExecutionPolicy Bypass -File movil\android\qa\telefono\conectar-telefono.ps1 [-Apk ruta.apk]
param([string]$Apk = "")

$adb = Join-Path $env:LOCALAPPDATA "Android\Sdk\platform-tools\adb.exe"
$dispositivos = @(& $adb devices | Select-String -Pattern "^(\S+)\s+device$" | ForEach-Object { $_.Matches[0].Groups[1].Value })
if ($dispositivos.Count -eq 0) {
    Write-Host "No hay ningún dispositivo. En el teléfono activa la depuración USB, conecta el cable y acepta el aviso; o abre el emulador."
    exit 1
}

foreach ($serie in $dispositivos) {
    Write-Host "Dispositivo: $serie"
    if ($Apk) {
        & $adb -s $serie install -r $Apk
        if ($LASTEXITCODE -ne 0) { exit 1 }
    }
    # Spring 18281, Django 18200 y web de usuario 18273, iguales a los puertos de QA de la laptop
    foreach ($puerto in 18281, 18200, 18273) {
        & $adb -s $serie reverse "tcp:$puerto" "tcp:$puerto" | Out-Null
    }
    & $adb -s $serie reverse --list
}

foreach ($url in "http://127.0.0.1:18281/api/categorias", "http://127.0.0.1:18273/") {
    try {
        $codigo = (Invoke-WebRequest -UseBasicParsing -TimeoutSec 5 $url).StatusCode
        Write-Host "Laptop responde $codigo en $url"
    } catch {
        Write-Host "La laptop no responde en $url. Levanta Docker QA antes de probar."
    }
}
Write-Host "Listo. Si desconectas el cable o reinicias el emulador, vuelve a ejecutar este script."
