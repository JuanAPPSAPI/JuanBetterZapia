@echo off
where gradle >nul 2>nul
if errorlevel 1 (
  echo Gradle is required but was not found in PATH.
  exit /b 1
)

gradle %*
exit /b %ERRORLEVEL%
