@echo off
echo Running Spotless format check...
call mvnw.cmd spotless:check -q
if %ERRORLEVEL% NEQ 0 (
    echo.
    echo [FAIL] Code formatting violations found.
    echo        Run 'mvnw.cmd spotless:apply' to auto-fix, then re-stage.
    exit /b 1
)
echo [OK] Formatting OK