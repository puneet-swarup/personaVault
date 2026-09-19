@echo off
echo Running full quality gate (mvn verify)...
call mvnw.cmd verify -q
if %ERRORLEVEL% NEQ 0 (
    echo.
    echo [FAIL] Quality gate failed. Fix the issues above before pushing.
    exit /b 1
)
echo [OK] Quality gate passed