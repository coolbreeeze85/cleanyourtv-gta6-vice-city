param(
    [string]$TvHost = $env:TV_HOST
)
$ErrorActionPreference = 'Stop'
if (-not $TvHost) { throw 'Pass -TvHost <YOUR_TV_IP> or set $env:TV_HOST' }
$root = $PSScriptRoot
$list = Join-Path $root "debloat-firestick-hd.txt"
$restoreScript = Join-Path $root "cleanyourtv\tools\restore.ps1"

Write-Host "Restoring Fire TV Stick ($TvHost) from $list ..."
& powershell -ExecutionPolicy Bypass -File $restoreScript -TvHost $TvHost $list com.amazon.tv.launcher

# Also restore window/transition/animator scales to 1.0
$env:TV_HOST = $TvHost
$py = Join-Path $root "cleanyourtv\tools\venv\Scripts\python.exe"
$tvPy = Join-Path $root "cleanyourtv\tools\tv.py"
@(
    "settings put secure enabled_accessibility_services ''",
    "settings put secure accessibility_enabled 0",
    "pm uninstall com.gathin.tvhome 2>/dev/null; echo launcher-removed",
    "settings put global window_animation_scale 1.0",
    "settings put global transition_animation_scale 1.0",
    "settings put global animator_duration_scale 1.0"
) | & $py -I $tvPy --batch
Write-Host "Restore complete."
