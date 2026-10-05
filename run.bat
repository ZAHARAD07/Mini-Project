@echo off
setlocal
cd /d "%~dp0"

set "CP=.;mysql-connector-j-9.2.0.jar"

echo Compiling...
javac -cp "%CP%" StudentGradeManagement.java
if errorlevel 1 (
    echo Compilation failed.
    exit /b 1
)

echo Starting...
java -cp "%CP%" StudentGradeManagement
