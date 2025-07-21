@echo off
if not exist "jenkins-agent.exe" (
    echo jenkins-agent.exe not found in current directory
    pause
    exit /b 1
)

echo Checking if Jenkins Agent service already exists...
sc query "Jenkins Agent" >nul 2>&1
if %errorlevel% equ 0 (
    echo Service already exists, attempting to start...
    jenkins-agent.exe start
) else (
    echo Installing Jenkins Agent service...
    jenkins-agent.exe install
    if %errorlevel% neq 0 (
        echo Installation failed with error code %errorlevel%
        pause
        exit /b %errorlevel%
    )
    
    echo Starting Jenkins Agent service...
    jenkins-agent.exe start
    if %errorlevel% neq 0 (
        echo Start failed with error code %errorlevel%
        pause
        exit /b %errorlevel%
    )
)

echo Verifying service status...
sc query "Jenkins Agent" >nul 2>&1
if %errorlevel% equ 0 (
    echo Jenkins agent service is running successfully
) else (
    echo Warning: Service may not be running properly
)