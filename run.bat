@echo off
setlocal

:: Set terminal window title
title MarketPulse - High-Frequency Trading Terminal ^& Matching Engine

:: Navigate to script directory
cd /d "%~dp0"

cls
echo ===============================================================================
echo   ===========================================================================
echo   ##  ##    ###    ####   ##  ##  ######  ######  #####   ##   ##  ##      #####  ######
echo   ### ##   ## ##   ## ##  ## ##   ##        ##    ##  ##  ##   ##  ##     ##      ##
echo   ######  ##   ##  ####   ####    ####      ##    #####   ##   ##  ##      ####   ####
echo   ## ###  #######  ## ##  ## ##   ##        ##    ##      ##   ##  ##         ##  ##
echo   ##  ##  ##   ##  ##  ## ##  ##  ######    ##    ##       #####   ######  #####  ######
echo   ===========================================================================
echo.
echo          HIGH-FREQUENCY TRADING TERMINAL ^& MATCHING ENGINE [PORT: 8080]
echo ===============================================================================
echo.
echo [*] Initializing MarketPulse runtime environment...

:: Verify Maven Wrapper exists
if not exist "%~dp0mvnw.cmd" goto :NO_MAVEN_WRAPPER

echo [OK] Maven Wrapper verified.
echo [*] Scheduling browser launch at http://localhost:8080 ...

:: Non-blocking background launch to open trading terminal once server is up
start /b cmd /c "ping -n 4 127.0.0.1 >nul && start http://localhost:8080"

echo [*] Compiling and starting ExchangeServer via Maven exec plugin...
echo [*] Press Ctrl+C in this terminal to shut down the server.
echo ===============================================================================
echo.

:: Execute compile and exec:java
call "%~dp0mvnw.cmd" compile exec:java
set "SERVER_EXIT=%ERRORLEVEL%"

if %SERVER_EXIT% NEQ 0 (
    echo.
    echo ===============================================================================
    echo [ERROR] MarketPulse Exchange Server stopped with exit code: %SERVER_EXIT%
    echo ===============================================================================
    pause
    exit /b %SERVER_EXIT%
)

endlocal
exit /b 0

:NO_MAVEN_WRAPPER
echo.
echo ===============================================================================
echo [ERROR] Maven wrapper executable mvnw.cmd was not found in:
echo         "%~dp0"
echo.
echo Please verify that run.bat is placed in the project root directory.
echo ===============================================================================
pause
exit /b 1

