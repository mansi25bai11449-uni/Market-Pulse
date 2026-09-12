@echo off
setlocal enabledelayedexpansion
title MarketPulse - Environment Readiness Checker
cls

echo ===============================================================================
echo                MarketPulse - Fresh Environment Pre-flight Check
echo ===============================================================================
echo.

set "ALL_OK=1"

:: 1. Check JAVA in PATH
echo [1/4] Checking Java installation...
set "FOUND_JAVA="
where java >nul 2>&1
if %ERRORLEVEL% EQU 0 (
    set "FOUND_JAVA=PATH"
    echo   [OK] Java executable found in system PATH.
) else (
    if defined JAVA_HOME (
        if exist "%JAVA_HOME%\bin\java.exe" (
            set "FOUND_JAVA=%JAVA_HOME%\bin\java.exe"
            echo   [OK] Java found via JAVA_HOME: %JAVA_HOME%
        )
    )
    if not defined FOUND_JAVA (
        if exist "C:\Users\hp\.jdks\openjdk-24.0.2+12-54\bin\java.exe" (
            set "FOUND_JAVA=C:\Users\hp\.jdks\openjdk-24.0.2+12-54\bin\java.exe"
            echo   [OK] Java found at default IDE JDK location.
        )
    )
)

if not defined FOUND_JAVA (
    echo   [X] Java was NOT found.
    set "ALL_OK=0"
) else (
    "%FOUND_JAVA%" -version 2>&1
)

echo.
:: 2. Check JAVA_HOME
echo [2/4] Checking JAVA_HOME environment variable...
if defined JAVA_HOME (
    if exist "%JAVA_HOME%\bin\java.exe" (
        echo   [OK] JAVA_HOME is configured: %JAVA_HOME%
    ) else (
        echo   [!] JAVA_HOME is set to "%JAVA_HOME%", but bin\java.exe was not found.
    )
) else (
    echo   [i] JAVA_HOME is not set. The launcher will attempt to use 'java' from PATH.
)

echo.
:: 3. Check bundled Maven Wrapper
echo [3/4] Checking bundled Maven wrapper (mvnw.cmd)...
if exist "%~dp0mvnw.cmd" (
    echo   [OK] mvnw.cmd is present.
    call "%~dp0mvnw.cmd" -v >nul 2>&1
    if !ERRORLEVEL! EQU 0 (
        echo   [OK] Bundled Maven is functioning properly.
    ) else (
        echo   [!] Maven wrapper encountered an issue. Ensure JDK 21/24 is installed.
        set "ALL_OK=0"
    )
) else (
    echo   [X] mvnw.cmd not found in project root.
    set "ALL_OK=0"
)

echo.
:: 4. Check Port 8080 Availability
echo [4/4] Checking if Port 8080 is available...
netstat -ano | findstr ":8080 " >nul 2>&1
if %ERRORLEVEL% EQU 0 (
    echo   [!] WARNING: Port 8080 is currently in use by another process.
    echo       Make sure no other web server is running on 8080.
) else (
    echo   [OK] Port 8080 is free.
)

echo.
echo ===============================================================================
if "!ALL_OK!"=="1" (
    echo [SUCCESS] Your environment is ready!
    echo           Run "run.bat" to start MarketPulse Exchange and Web Terminal.
) else (
    echo [ACTION REQUIRED] Please install JDK 24 or JDK 21 and add it to your PATH.
    echo                   Download: https://adoptium.net/ or https://jdk.java.net/24/
)
echo ===============================================================================
echo.
pause
endlocal
