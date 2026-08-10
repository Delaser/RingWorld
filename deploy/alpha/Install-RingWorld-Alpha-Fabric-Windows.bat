@echo off
setlocal EnableExtensions

set "BASE_URL=https://andwhatnotstudio.com/ringworld/alpha"
set "MANIFEST_PATH=%TEMP%\RingWorld-Alpha-Fabric-RELEASE-MANIFEST.json"
set "ZIP_PATH=%TEMP%\RingWorld-Alpha-Fabric-Windows.zip"
set "INSTALL_DIR=%LOCALAPPDATA%\RingWorld\Alpha4-Fabric"

echo Checking for the newest RingWorld Fabric alpha build...
powershell -NoProfile -ExecutionPolicy Bypass -Command ^
  "$ErrorActionPreference='Stop';" ^
  "$ProgressPreference='SilentlyContinue';" ^
  "[Net.ServicePointManager]::SecurityProtocol=[Net.SecurityProtocolType]::Tls12;" ^
  "$headers=@{'Cache-Control'='no-cache'};" ^
  "$manifestUrl=$env:BASE_URL+'/RELEASE-MANIFEST-FABRIC.json';" ^
  "Invoke-WebRequest -UseBasicParsing -Headers $headers -Uri $manifestUrl -OutFile $env:MANIFEST_PATH;" ^
  "$manifest=Get-Content -Raw -LiteralPath $env:MANIFEST_PATH | ConvertFrom-Json;" ^
  "if([int]$manifest.format -ne 1){throw 'Unsupported RingWorld release manifest format'};" ^
  "if([string]$manifest.loader -ne 'fabric'){throw 'RingWorld manifest is not a Fabric package'};" ^
  "if([string]$manifest.license -ne 'MPL-2.0'){throw 'RingWorld manifest has an unexpected licence'};" ^
  "$revision=[string]$manifest.sourceRevision;" ^
  "if($revision -notmatch '^[0-9a-f]{40}$'){throw 'RingWorld manifest has an invalid source revision'};" ^
  "$expectedSource='https://github.com/Delaser/RingWorld/tree/'+$revision;" ^
  "if([string]$manifest.sourceUrl -ne $expectedSource){throw 'RingWorld manifest source URL mismatch'};" ^
  "$artifacts=@($manifest.artifacts | Where-Object {[string]$_.name -match '-Fabric-Client-Windows[.]zip$'});" ^
  "if($artifacts.Count -ne 1){throw 'RingWorld manifest must contain exactly one Windows Fabric client'};" ^
  "$artifactName=[string]$artifacts[0].name;" ^
  "if($artifactName -notmatch '^[A-Za-z0-9][A-Za-z0-9._+-]*[.]zip$'){throw 'Unsafe RingWorld artifact name'};" ^
  "$expected=[string]$artifacts[0].sha256;" ^
  "if($expected -notmatch '^[0-9a-fA-F]{64}$'){throw 'Invalid RingWorld package checksum'};" ^
  "$artifactUrl=$env:BASE_URL+'/'+[Uri]::EscapeDataString($artifactName);" ^
  "Write-Host ('Downloading '+[string]$manifest.publicName+'...');" ^
  "Invoke-WebRequest -UseBasicParsing -Headers $headers -Uri $artifactUrl -OutFile $env:ZIP_PATH;" ^
  "$actual=(Get-FileHash -Algorithm SHA256 -LiteralPath $env:ZIP_PATH).Hash.ToLowerInvariant();" ^
  "if($actual -ne $expected.ToLowerInvariant()){throw ('RingWorld package checksum mismatch: '+$actual)};" ^
  "New-Item -ItemType Directory -Force -Path $env:INSTALL_DIR | Out-Null;" ^
  "Expand-Archive -Force -LiteralPath $env:ZIP_PATH -DestinationPath $env:INSTALL_DIR"

if errorlevel 1 goto :error
del /Q "%ZIP_PATH%" "%MANIFEST_PATH%" 2>nul

if not exist "%INSTALL_DIR%\Launch RingWorld.bat" goto :error
echo Package verified. Starting the RingWorld Fabric launcher...
call "%INSTALL_DIR%\Launch RingWorld.bat"
exit /b %errorlevel%

:error
del /Q "%ZIP_PATH%" "%MANIFEST_PATH%" 2>nul
echo.
echo RingWorld Fabric installation failed. No unverified package was launched.
echo Download the current ZIP from https://andwhatnotstudio.com/ringworld/alpha/ if needed.
pause
exit /b 1
