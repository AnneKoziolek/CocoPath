@echo off
REM ============================================================================
REM Galette-Knarr Symbolic Execution Runner
REM ============================================================================
REM This script runs automatic path exploration for Vitruvius model transformations.
REM It uses the PathExplorer API to automatically generate test inputs by:
REM   1. Executing transformations with concrete values
REM   2. Collecting path constraints
REM   3. Negating constraints to find unexplored paths
REM   4. Solving for new inputs automatically
REM
REM Usage:
REM   run-symbolic-execution.bat              # Interactive mode (prompts for choice)
REM   run-symbolic-execution.bat internal     # Fast mode (2-5ms/path, simplified)
REM   run-symbolic-execution.bat external     # Full mode (26-45ms/path, complete Vitruvius)
REM ============================================================================

setlocal enabledelayedexpansion

set "USE_EXTERNAL=false"
set "EXTERNAL_PATH=C:\Users\10239\Amathea-acset"
set "INTERACTIVE_MODE=true"

REM Parse arguments
if /i "%~1"=="internal" (
    set "USE_EXTERNAL=false"
    set "INTERACTIVE_MODE=false"
)
if /i "%~1"=="external" (
    set "USE_EXTERNAL=true"
    set "INTERACTIVE_MODE=false"
)

echo ================================================================================
echo CocoPath
echo ================================================================================
echo.

REM Interactive mode selection if no argument provided
if "%INTERACTIVE_MODE%"=="true" (
    echo Please select execution mode:
    echo.
    echo   1^) INTERNAL MODE ^(Fast, simplified stub^)
    echo      - Output: Basic XMI stubs
    echo      - No external repository needed
    echo.
    echo   2^) EXTERNAL MODE ^(Full Vitruvius transformations^)
    echo      - Output: Complete Vitruvius reactions ^& transformations
    echo      - Requires external Amalthea-acset repository
    echo.
    set /p choice="Enter your choice (1 or 2): "
    echo.

    if "!choice!"=="1" (
        set "USE_EXTERNAL=false"
        echo Selected: INTERNAL MODE
    ) else if "!choice!"=="2" (
        set "USE_EXTERNAL=true"
        echo Selected: EXTERNAL MODE
    ) else (
        echo Invalid choice. Defaulting to INTERNAL MODE.
        set "USE_EXTERNAL=false"
    )
    echo.
)

echo ================================================================================
echo.

if "%USE_EXTERNAL%"=="true" (
    echo Mode: EXTERNAL ^(switching to external Amalthea-acset^)
    echo.

    REM Verify external path exists
    if not exist "%EXTERNAL_PATH%" (
        echo ERROR: External Amalthea-acset not found at: %EXTERNAL_PATH%
        echo Please check the path
        exit /b 1
    )

    echo [1/4] Building external Amalthea-acset at %EXTERNAL_PATH%...
    pushd "%EXTERNAL_PATH%"
    call mvn clean install -DskipTests -Dcheckstyle.skip=true
    if errorlevel 1 (
        echo ERROR: Failed to build external Amalthea-acset
        popd
        exit /b 1
    )
    popd
    echo       Done.
    echo.

    echo [2/4] Temporarily switching to external dependency...
    REM Backup pom.xml before modifying
    copy /y pom.xml pom.xml.bak >nul

    REM Comment out internal dependency and uncomment external
    powershell -Command "(gc pom.xml) -replace '(<dependency>\s*<groupId>edu\.neu\.ccs\.prl\.galette</groupId>\s*<artifactId>amalthea-acset-vsum</artifactId>.*?</dependency>)', '<!-- $1 -->' | Out-File -encoding ASCII pom.xml"
    powershell -Command "(gc pom.xml) -replace '<!--\s*(<dependency>\s*<groupId>tools\.vitruv</groupId>\s*<artifactId>tools\.vitruv\.methodologisttemplate\.vsum</artifactId>.*?</dependency>)\s*-->', '$1' | Out-File -encoding ASCII pom.xml"

    echo       Switched to external dependency.
    echo.

    set "STEP_OFFSET=2"
) else (
    echo Mode: INTERNAL ^(using amalthea-acset-integration module^)
    echo       Note: Requires external Amalthea-acset built once for Vitruvius dependencies
    echo.

    REM Check if Vitruvius dependencies are available
    if not exist "%USERPROFILE%\.m2\repository\tools\vitruv\tools.vitruv.methodologisttemplate.vsum" (
        echo WARNING: Vitruvius VSUM dependency not found in Maven repository
        echo          Building external Amalthea-acset to install it...
        echo.

        if exist "%EXTERNAL_PATH%" (
            pushd "%EXTERNAL_PATH%"
            call mvn clean install -DskipTests -Dcheckstyle.skip=true
            if errorlevel 1 (
                echo ERROR: Failed to build external Amalthea-acset
                popd
                exit /b 1
            )
            popd
            echo       Done. Vitruvius dependencies installed.
            echo.
        ) else (
            echo ERROR: External Amalthea-acset not found at: %EXTERNAL_PATH%
            echo        Please build it first or specify path
            exit /b 1
        )
    )

    echo [1/3] Building internal amalthea-acset-integration...
    pushd "..\amalthea-acset-integration"
    call mvn clean install -DskipTests -Dcheckstyle.skip=true
    if errorlevel 1 (
        echo ERROR: Failed to build internal amalthea-acset-integration
        popd
        exit /b 1
    )
    popd
    echo       Done.
    echo.

    set "STEP_OFFSET=0"
)

set /a STEP1=2+%STEP_OFFSET%
set /a STEP2=3+%STEP_OFFSET%
set /a TOTAL_STEPS=3+%STEP_OFFSET%

echo [%STEP1%/%TOTAL_STEPS%] Cleaning previous outputs...
if exist galette-output-* rmdir /s /q galette-output-*
if exist execution_paths.json del /q execution_paths.json
echo       Done.
echo.

echo [%STEP2%/%TOTAL_STEPS%] Running symbolic execution...
echo       With automatic constraint collection enabled

REM Note: Javaagent is not compatible with mvn exec:java
REM We use manual constraint collection via PathUtils.addIntDomainConstraint() and addSwitchConstraint()

call mvn exec:java -Dcheckstyle.skip=true
set "MVN_SUCCESS=%ERRORLEVEL%"

if not "%MVN_SUCCESS%"=="0" (
    echo.
    echo WARNING: Maven execution had errors
)

REM Restore internal dependency if we switched to external
if "%USE_EXTERNAL%"=="true" (
    echo.
    echo Restoring internal dependency configuration...
    if exist pom.xml.bak (
        copy /y pom.xml.bak pom.xml >nul
        del pom.xml.bak
    )
    echo       Done.
)

if not exist execution_paths_automatic.json (
    if not "%MVN_SUCCESS%"=="0" (
        echo.
        echo ERROR: Symbolic execution failed!
        exit /b 1
    )
)

echo.
echo ================================================================================
echo Completed.
echo ================================================================================
echo.
echo Generated files:
echo   - execution_paths_automatic.json      (Path exploration results)
echo   - galette-output-automatic-*/ (Model outputs per path)
echo.
