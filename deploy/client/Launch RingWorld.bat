@echo off
setlocal EnableExtensions EnableDelayedExpansion
cd /d "%~dp0"

set "ROOT=%CD%"
set "DATA=%ROOT%\.prism-data"
set "SOURCE=%ROOT%\instance"
set "INSTANCE=%DATA%\instances\RingWorld-Test"
set "MODS=%INSTANCE%\.minecraft\mods"
set "LAUNCHER_DIR=%ROOT%\.launcher\windows"
set "VERSION=11.0.3"

if not exist "%DATA%\logs" mkdir "%DATA%\logs"

if not exist "%INSTANCE%\mmc-pack.json" (
    if exist "%INSTANCE%" rmdir /S /Q "%INSTANCE%"
    mkdir "%DATA%\instances" 2>nul
    xcopy "%SOURCE%" "%INSTANCE%\" /E /I /H /Y >nul
)

rem Refresh only bundle-managed files. Accounts, saves, options, screenshots,
rem resource packs, and user-edited instance settings remain untouched.
if not exist "%MODS%" mkdir "%MODS%"
if not exist "%SOURCE%\.minecraft\mods\ringworld-*.jar" goto :error
if not exist "%SOURCE%\.minecraft\mods\fabric-api-*.jar" goto :error

for %%F in ("%SOURCE%\.minecraft\mods\ringworld-*.jar") do (
    set "RINGWORLD_JAR=%%~nxF"
    copy /Y "%%~fF" "%MODS%\%%~nxF" >nul || goto :error
)
for %%F in ("%MODS%\ringworld-*.jar") do (
    if /I not "%%~nxF"=="!RINGWORLD_JAR!" del /Q "%%~fF"
)

for %%F in ("%SOURCE%\.minecraft\mods\fabric-api-*.jar") do (
    set "FABRIC_API_JAR=%%~nxF"
    copy /Y "%%~fF" "%MODS%\%%~nxF" >nul || goto :error
)
for %%F in ("%MODS%\fabric-api-*.jar") do (
    if /I not "%%~nxF"=="!FABRIC_API_JAR!" del /Q "%%~fF"
)

copy /Y "%SOURCE%\mmc-pack.json" "%INSTANCE%\mmc-pack.json" >nul || goto :error
if not exist "%INSTANCE%\.minecraft\config\ringworld.properties" (
    mkdir "%INSTANCE%\.minecraft\config" 2>nul
    copy /Y "%SOURCE%\.minecraft\config\ringworld.properties" ^
        "%INSTANCE%\.minecraft\config\ringworld.properties" >nul || goto :error
)
rem Minecraft 26.1.2 requires Java 25. Preserve every other instance setting,
rem but let Prism replace a stale Java 21 path from an older RingWorld bundle.
powershell -NoProfile -Command "$p=Join-Path $env:INSTANCE 'instance.cfg'; $c=[IO.File]::ReadAllText($p); foreach($e in @(@('AutomaticJava','true'),@('OverrideJavaLocation','false'))){ $pattern='(?m)^'+[regex]::Escape($e[0])+'=.*$'; if([regex]::IsMatch($c,$pattern)){ $c=[regex]::Replace($c,$pattern,$e[0]+'='+$e[1]) } else { $c += [Environment]::NewLine+$e[0]+'='+$e[1] } }; [IO.File]::WriteAllText($p,$c)" || goto :error
echo RingWorld client files are current.

set "PRISM="
if exist "%ProgramFiles%\PrismLauncher\prismlauncher.exe" set "PRISM=%ProgramFiles%\PrismLauncher\prismlauncher.exe"
if exist "%LocalAppData%\Programs\PrismLauncher\prismlauncher.exe" set "PRISM=%LocalAppData%\Programs\PrismLauncher\prismlauncher.exe"
if exist "%LAUNCHER_DIR%" for /R "%LAUNCHER_DIR%" %%F in (prismlauncher.exe) do if exist "%%F" if not defined PRISM set "PRISM=%%F"

if not defined PRISM (
    if /I "%PROCESSOR_ARCHITECTURE%"=="ARM64" (
        set "ASSET=PrismLauncher-Windows-MSVC-arm64-Portable-%VERSION%.zip"
    ) else (
        set "ASSET=PrismLauncher-Windows-MinGW-w64-Portable-%VERSION%.zip"
    )
    set "URL=https://github.com/PrismLauncher/PrismLauncher/releases/download/%VERSION%/!ASSET!"
    if not exist "%LAUNCHER_DIR%" mkdir "%LAUNCHER_DIR%"
    echo Downloading official Prism Launcher %VERSION%...
    powershell -NoProfile -ExecutionPolicy Bypass -Command "$ProgressPreference='SilentlyContinue'; Invoke-WebRequest -UseBasicParsing '!URL!' -OutFile '%LAUNCHER_DIR%\PrismLauncher.zip'; Expand-Archive -Force '%LAUNCHER_DIR%\PrismLauncher.zip' '%LAUNCHER_DIR%'; Remove-Item '%LAUNCHER_DIR%\PrismLauncher.zip'"
    if errorlevel 1 goto :error
    for /R "%LAUNCHER_DIR%" %%F in (prismlauncher.exe) do if not defined PRISM set "PRISM=%%F"
)

if not defined PRISM goto :error
start "" "!PRISM!" -d "%DATA%" -l RingWorld-Test
exit /b 0

:error
echo.
echo RingWorld launcher setup or update failed. Close Minecraft and try again.
echo See README-FIRST.txt for the manual import option.
pause
exit /b 1
