@echo off
setlocal

if not defined JAVA_HOME (
    if exist "C:\Program Files\Java\jdk-21\bin\java.exe" (
        set "JAVA_HOME=C:\Program Files\Java\jdk-21"
    )
)

where mvn >nul 2>nul
if %ERRORLEVEL% EQU 0 (
    call mvn %*
    exit /b %ERRORLEVEL%
)

set "LOCAL_MVN=%USERPROFILE%\.m2\wrapper\dists\apache-maven-3.10.0\2aaba761688b1a6ea5a46e7e5f4d5406ed7a75ee8da95e5e3079863a23461c9b\bin\mvn.cmd"
if exist "%LOCAL_MVN%" (
    call "%LOCAL_MVN%" %*
    exit /b %ERRORLEVEL%
)

for /d %%D in ("%USERPROFILE%\.m2\wrapper\dists\apache-maven-*") do (
    for /d %%S in ("%%D\*") do (
        if exist "%%S\bin\mvn.cmd" (
            call "%%S\bin\mvn.cmd" %*
            exit /b %ERRORLEVEL%
        )
    )
)

echo Error: No se encontro Maven en el PATH ni en %USERPROFILE%\.m2\wrapper\dists
exit /b 1

