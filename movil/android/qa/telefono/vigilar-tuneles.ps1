# Mantiene los túneles adb reverse de FoundIA en todos los dispositivos conectados.
# Cada pocos segundos revisa los dispositivos y vuelve a crear los túneles de los que los hayan perdido
# (al reconectar el cable, reinstalar la app desde Android Studio o reiniciar el emulador).
# Solo corre una copia a la vez. Para detenerlo: Stop-Process -Name powershell con este script, o cerrar sesión.
# Uso: movil\android\qa\telefono\iniciar-vigilante.cmd  (lo deja oculto en segundo plano)
$creado = $false
$candado = New-Object System.Threading.Mutex($true, "Global\FoundIA-vigilar-tuneles", [ref]$creado)
if (-not $creado) { exit 0 }

$adb = Join-Path $env:LOCALAPPDATA "Android\Sdk\platform-tools\adb.exe"
$puertos = 18281, 18200, 18273

while ($true) {
    $dispositivos = @(& $adb devices 2>$null | Select-String -Pattern "^(\S+)\s+device$" | ForEach-Object { $_.Matches[0].Groups[1].Value })
    foreach ($serie in $dispositivos) {
        $activos = (& $adb -s $serie reverse --list 2>$null) -join "`n"
        foreach ($puerto in $puertos) {
            if ($activos -notmatch "tcp:$puerto\s+tcp:$puerto") {
                & $adb -s $serie reverse "tcp:$puerto" "tcp:$puerto" 2>$null | Out-Null
            }
        }
    }
    Start-Sleep -Seconds 3
}
