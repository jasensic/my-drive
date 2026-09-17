# Capture my-drive in-app update logs (Windows PowerShell)
# 1. Enable USB debugging, plug in the phone, accept the RSA prompt.
# 2. Run this from any folder. Reproduce the update when it says so.

$ErrorActionPreference = "Stop"
$adb = $null
foreach ($candidate in @(
    "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe",
    "$env:ANDROID_HOME\platform-tools\adb.exe",
    "$env:ANDROID_SDK_ROOT\platform-tools\adb.exe"
)) {
    if ($candidate -and (Test-Path $candidate)) { $adb = $candidate; break }
}
if (-not $adb) {
    $fromPath = Get-Command adb -ErrorAction SilentlyContinue
    if ($fromPath) { $adb = $fromPath.Source }
}
if (-not $adb) { throw "adb.exe not found. Install Android platform-tools or add them to PATH." }

& $adb start-server | Out-Null
$devices = & $adb devices
Write-Host $devices
if ($devices -notmatch "device$") {
    throw "No phone in 'device' state. Enable USB debugging and accept the prompt."
}

$out = Join-Path (Get-Location) "mydrive-update.log"
Write-Host "Clearing logcat. Reproduce Install update on the phone, then press Enter here."
& $adb logcat -c
Read-Host "Press Enter after the install error"
& $adb logcat -d -v threadtime -s MyDriveUpdate:V PackageInstaller:V PackageManager:V installd:V | Set-Content -Encoding utf8 $out
Write-Host "Wrote $out"
Get-Content $out | Select-Object -Last 80
