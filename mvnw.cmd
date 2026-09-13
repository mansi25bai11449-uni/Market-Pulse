@echo off
if defined JAVA_HOME if exist "%JAVA_HOME%\bin\java.exe" goto run_maven

set "JAVA_HOME="
for /d %%D in ("C:\Program Files\Java\jdk-*") do set "JAVA_HOME=%%~fD"

:run_maven
if not defined JAVA_HOME (
    echo JAVA_HOME is not set to a valid JDK installation. 1>&2
    exit /b 1
)
if not exist "%~dp0maven\apache-maven-3.9.6\boot\plexus-classworlds-*.jar" (
    echo Bundled Maven is incomplete: plexus-classworlds JAR is missing. 1>&2
    exit /b 1
)
"%~dp0maven\apache-maven-3.9.6\bin\mvn.cmd" %*
