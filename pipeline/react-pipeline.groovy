def setProject = [:]

pipeline {
    agent none
    
    triggers {
        githubPush()
    }

    options {
        skipDefaultCheckout(true)
        timeout(time: 90, unit: 'MINUTES')
        retry(1)
        timestamps()
        copyArtifactPermission('*')
    }

    environment {
        // Server Configuration
        BUILD_AGENT_LABEL = 'BUILD_AGENT_LABEL'
        DEPLOY_AGENT_LABEL = 'DEPLOY_AGENT_LABEL'
        
        // Deployment Paths
        BASE_WEBSITE_PATH = 'D:\\Project'
        BASE_BACKUPS_PATH = 'D:\\Project\\Backups'
        
        // Build Configuration
        ENVIRONMENT = 'UAT'
        BUILD_PATH = 'UAT'
        BUILD_COMMAND = 'npm run build:uat'
        PUBLISH_PATH = 'publish'
        
        // File Configuration
        CONFIG_FILE_ID = "CONFIG_FILE_ID"
        
        // GitHub Configuration
        GITHUB_REPO_OWNER = "GITHUB_REPO_OWNER"
        GITHUB_REPO_NAME = "GITHUB_REPO_NAME"
        GITHUB_BRANCH_NAME = "GITHUB_BRANCH_NAME"
        GITHUB_STATUS_CONTEXT = "GITHUB_STATUS_CONTEXT"
        GITHUB_TOKEN_CREDENTIAL_ID = "GITHUB_TOKEN_CREDENTIAL_ID"
        
        // Deployment Tracking
        DEPLOYMENT_ENVIRONMENT = 'UAT'
        UAT_URL = 'UAT_URL'  // Update with your actual UAT URL
        
        // Artifact Configuration
        ARTIFACT_NAME = "ARTIFACT_NAME"
    }

    stages { 
        stage('Initialize Pipeline') {
            agent { label "${env.BUILD_AGENT_LABEL}" }
            
            steps {  
                script {
                    updateGitHubStatus('pending', 'Build started', 'Initializing React deployment pipeline...')
                    
                    logSection("PIPELINE INITIALIZATION")
                    logInfo("Build Server", env.BUILD_AGENT_LABEL)
                    logInfo("Deploy Server", env.DEPLOY_AGENT_LABEL)
                    logInfo("Target Environment", env.ENVIRONMENT)
                    logInfo("Repository", "${env.GITHUB_REPO_OWNER}/${env.GITHUB_REPO_NAME}")
                    logInfo("Branch", env.GITHUB_BRANCH_NAME)
                }
                
                script {
                    logSubSection("Environment Validation")
                    
                    powershell '''
                        $RequiredSettings = @(
                            "BUILD_AGENT_LABEL", "DEPLOY_AGENT_LABEL", "BASE_WEBSITE_PATH",
                            "BASE_BACKUPS_PATH", "ENVIRONMENT", "BUILD_PATH", "BUILD_COMMAND",
                            "GITHUB_BRANCH_NAME", "CONFIG_FILE_ID", "GITHUB_REPO_OWNER",
                            "GITHUB_REPO_NAME", "GITHUB_STATUS_CONTEXT", "GITHUB_TOKEN_CREDENTIAL_ID",
                            "DEPLOYMENT_ENVIRONMENT", "UAT_URL", "ARTIFACT_NAME"
                        )

                        $MissingSettings = @()
                        foreach ($EnvVar in $RequiredSettings) {
                            $EnvVarValue = [System.Environment]::GetEnvironmentVariable($EnvVar)
                            if ([string]::IsNullOrEmpty($EnvVarValue)) {
                                $MissingSettings += $EnvVar
                            }
                        }

                        if ($MissingSettings.Count -gt 0) {
                            Write-Host "[ERROR] Missing required environment variables:" -ForegroundColor Red
                            $MissingSettings | ForEach-Object { Write-Host "  - $_" -ForegroundColor Red }
                            exit 1
                        } else {
                            Write-Host "[SUCCESS] All environment variables validated" -ForegroundColor Green
                        }
                    '''
                }

                script {
                    logSubSection("Source Code Checkout")
                    
                    cleanWs()
                    checkout scmGit(
                        branches: [[name: "*/${env.GITHUB_BRANCH_NAME}"]], 
                        extensions: [], 
                        userRemoteConfigs: [[
                            credentialsId: "${env.GITHUB_TOKEN_CREDENTIAL_ID}", 
                            url: "https://github.com/${env.GITHUB_REPO_OWNER}/${env.GITHUB_REPO_NAME}.git"
                        ]]
                    )
                    
                    // Capture Git commit SHA
                    try {
                        def gitCommitResult = powershell(
                            script: 'git rev-parse HEAD',
                            returnStdout: true
                        )
                        env.GIT_COMMIT_SHA = gitCommitResult.trim()
                        logSuccess("Git commit SHA captured: ${env.GIT_COMMIT_SHA}")
                    } catch (Exception e) {
                        logWarning("Could not capture Git commit SHA - GitHub status updates will be limited")
                        env.GIT_COMMIT_SHA = null
                    }
                }

                script {
                    logSubSection("Configuration Loading")
                    
                    configFileProvider([configFile(fileId: "${env.CONFIG_FILE_ID}", variable: 'configFile')]) {
                        def configJson = readFile(file: configFile)

                        if (configJson.trim().length() == 0) {
                            updateGitHubStatus('error', 'Configuration Error', 'Config file is empty')
                            logError("Configuration file is empty")
                            error("Config file is empty: ${env.CONFIG_FILE_ID}")
                        }

                        try {
                            env.CONFIG = configJson
                            def config = readJSON text: configJson
                            
                            if (config instanceof List) {
                                env.PROJECT_STRUCTURE_TYPE = "1"
                                logSuccess("Configuration loaded successfully (Array format)")
                                logInfo("Projects found", "${config.size()} project configuration(s)")
                                config.eachWithIndex { project, index ->
                                    logInfo("Project [${index}]", "${project.project_name ?: project.iis_website_name} (Node ${project.node_version})")
                                }
                            } else if (config instanceof Map) {
                                env.PROJECT_STRUCTURE_TYPE = "2"
                                env.CONFIG = "[${configJson}]"
                                logSuccess("Configuration loaded successfully (Single object format)")
                                logInfo("Project", "${config.iis_website_name} (Node ${config.node_version})")
                            } else {
                                updateGitHubStatus('error', 'Configuration Error', 'Unsupported config format')
                                logError("Unsupported configuration format")
                                error("Unsupported config format: ${env.CONFIG_FILE_ID}")
                            }
                            
                            env.SELECTED_PROJECT_INDEX = "0"
                            
                        } catch (Exception e) {
                            updateGitHubStatus('error', 'Configuration Error', 'Invalid JSON format in config file')
                            logError("Invalid JSON format in configuration file")
                            error("Invalid JSON format in config file: ${env.CONFIG_FILE_ID}\nError: ${e.message}")
                        }
                    }
                }

                script {
                    logSubSection("Project Configuration Validation")
                    
                    def config = readJSON text: env.CONFIG
                    def project = config[0]
                    
                    logInfo("Processing project", project.project_name ?: project.iis_website_name)
                    
                    def requiredAttributes = [
                        "node_version", "iis_website_name", "folder_website_name",
                        "start_iis", "stop_iis", "start_app_pool", "stop_app_pool", "is_cleanup"
                    ]

                    def validationErrors = []
                    requiredAttributes.each { attribute ->
                        def value = project."${attribute}"
                        if (value == null || value.toString().trim().isEmpty()) {
                            validationErrors.add(attribute)
                        }
                    }

                    if (validationErrors.size() > 0) {
                        logError("Missing required attributes: ${validationErrors.join(', ')}")
                        updateGitHubStatus('error', 'Configuration Error', "Missing required attributes for React project")
                        error("Missing required attributes for React project: ${validationErrors.join(', ')}")
                    }

                    // Validate IIS configuration
                    if ((project.start_app_pool || project.stop_app_pool) && 
                        (project.app_pool_name == null || project.app_pool_name.toString().trim().isEmpty())) {
                        logError("App pool management enabled but app_pool_name is missing")
                        updateGitHubStatus('error', 'Configuration Error', "Missing app_pool_name configuration")
                        error("App pool management enabled but app_pool_name is not defined")
                    }

                    if ((project.start_iis || project.stop_iis) && 
                        (project.iis_website_name == null || project.iis_website_name.toString().trim().isEmpty())) {
                        logError("IIS management enabled but iis_website_name is missing")
                        updateGitHubStatus('error', 'Configuration Error', "Missing iis_website_name configuration")
                        error("IIS management enabled but iis_website_name is not defined")
                    }
                    
                    // Validate cleanup configuration
                    if (project.is_cleanup) {
                        def retention = project.backup_file_retention_months
                        def deleteCount = project.amount_files_delete
                        if ((retention == null || retention.toString().trim().isEmpty()) && 
                            (deleteCount == null || deleteCount.toString().trim().isEmpty())) {
                            logError("Cleanup enabled but retention configuration is missing")
                            updateGitHubStatus('error', 'Configuration Error', "Missing cleanup configuration")
                            error("Cleanup enabled but retention configuration is missing")
                        }
                    }
                    
                    logSuccess("Project configuration validated successfully")
                    logInfo("Project Name", project.project_name ?: project.iis_website_name)
                    logInfo("Node Version", project.node_version)
                    logInfo("Build Command", env.BUILD_COMMAND)
                    
                    updateGitHubStatus('pending', 'Validation Complete', 'Configuration validated, starting build process...')
                }
            }
        }

        stage('Build React Application') {
            agent { label "${env.BUILD_AGENT_LABEL}" }
            
            steps {
                script {
                    updateGitHubStatus('pending', 'Building', 'Building React application with optimizations...')
                    
                    logSection("REACT BUILD PROCESS")
                    
                    def config = readJSON text: env.CONFIG
                    def project = config[0]
                    def projectName = project.project_name ?: project.iis_website_name
                    
                    logInfo("Building project", projectName)
                    logInfo("Node.js version", project.node_version)
                    
                    try {
                        def buildContext = env.PROJECT_STRUCTURE_TYPE == "1" && project.project_name ? project.project_name : "."
                        
                        dir(buildContext) {
                            def buildPath = "${env.WORKSPACE}\\${env.PUBLISH_PATH}\\${projectName}"
                            
                            nodejs(nodeJSInstallationName: "node ${project.node_version}") {
                                logSubSection("NPM Configuration")
                                powershell '''
                                    # Configure npm for performance
                                    npm config set audit false
                                    npm config set fund false
                                    npm config set prefer-offline true
                                    npm config set progress false
                                    npm config set loglevel warn
                                    
                                    $cacheDir = "C:\\npm-cache"
                                    if (-not (Test-Path $cacheDir)) {
                                        New-Item -ItemType Directory -Path $cacheDir -Force | Out-Null
                                        Write-Host "[INFO] NPM cache directory created: $cacheDir" -ForegroundColor Cyan
                                    }
                                    npm config set cache $cacheDir
                                    
                                    Write-Host "[SUCCESS] NPM optimizations configured" -ForegroundColor Green
                                '''

                                logSubSection("Dependency Management")
                                def needsInstall = powershell(
                                    script: '''
                                        if (-not (Test-Path "node_modules")) {
                                            Write-Output "INSTALL_NEEDED"
                                            exit 0
                                        }
                                        
                                        if (-not (Test-Path "package-lock.json")) {
                                            Write-Output "INSTALL_NEEDED"
                                            exit 0
                                        }
                                        
                                        $lockModified = (Get-Item "package-lock.json").LastWriteTime
                                        $nodeModulesModified = (Get-Item "node_modules").LastWriteTime
                                        
                                        if ($lockModified -gt $nodeModulesModified) {
                                            Write-Output "INSTALL_NEEDED"
                                        } else {
                                            try {
                                                $npmCheck = npm ls --depth=0 --silent 2>$null
                                                if ($LASTEXITCODE -eq 0) {
                                                    Write-Output "INSTALL_SKIP"
                                                } else {
                                                    Write-Output "INSTALL_NEEDED"
                                                }
                                            } catch {
                                                Write-Output "INSTALL_NEEDED"
                                            }
                                        }
                                    ''',
                                    returnStdout: true
                                ).trim()

                                if (needsInstall == "INSTALL_NEEDED") {
                                    def installStartTime = System.currentTimeMillis()
                                    
                                    logInfo("Dependencies", "Installing with npm ci...")
                                    powershell '''
                                        npm ci --prefer-offline --no-audit --no-fund --silent
                                        
                                        if ($LASTEXITCODE -ne 0) {
                                            Write-Host "[WARNING] npm ci failed, falling back to npm install..." -ForegroundColor Yellow
                                            npm install --prefer-offline --no-audit --no-fund --silent
                                            
                                            if ($LASTEXITCODE -ne 0) {
                                                Write-Host "[ERROR] npm install failed!" -ForegroundColor Red
                                                exit 1
                                            }
                                        }
                                        
                                        Write-Host "[SUCCESS] Dependencies installed successfully" -ForegroundColor Green
                                    '''
                                    
                                    def installDuration = (System.currentTimeMillis() - installStartTime) / 1000
                                    logSuccess("Dependencies installed in ${installDuration} seconds")
                                } else {
                                    logInfo("Dependencies", "Up to date, skipping installation")
                                }

                                logSubSection("Application Build")
                                def buildStartTime = System.currentTimeMillis()
                                
                                logInfo("Build command", env.BUILD_COMMAND)
                                powershell "${env.BUILD_COMMAND}"
                                
                                def buildDuration = (System.currentTimeMillis() - buildStartTime) / 1000
                                logSuccess("Build completed in ${buildDuration} seconds")
                                
                                logSubSection("Artifact Preparation")
                                powershell """
                                    if (Test-Path "${env.BUILD_PATH}") {
                                        if (-not (Test-Path "${buildPath}")) {
                                            New-Item -ItemType Directory -Path "${buildPath}" -Force | Out-Null
                                        }
                                        Copy-Item -Path "${env.BUILD_PATH}\\*" -Destination "${buildPath}" -Recurse -Force
                                        
                                        \$buildFiles = Get-ChildItem -Path "${buildPath}" -File -Recurse
                                        Write-Host "[SUCCESS] Build artifacts prepared: \$(\$buildFiles.Count) files" -ForegroundColor Green
                                        Write-Host "[INFO] Artifact location: ${buildPath}" -ForegroundColor Cyan
                                    } else {
                                        Write-Host "[ERROR] Build output not found at: ${env.BUILD_PATH}" -ForegroundColor Red
                                        exit 1
                                    }
                                """
                            }
                            
                            env."BUILD_PATH_${projectName}" = buildPath
                            logSuccess("Build process completed successfully")
                        }
                        
                    } catch (Exception e) {
                        logError("Build failed: ${e.message}")
                        updateGitHubStatus('error', 'Build Failed', "Build failed for project: ${projectName}")
                        throw e
                    }
                    
                    updateGitHubStatus('pending', 'Build Successful', 'Build completed, preparing for deployment...')
                }
            }
            
            post {
                success {
                    script {
                        logSubSection("Artifact Archiving")
                        
                        archiveArtifacts artifacts: "${env.PUBLISH_PATH}/**/*", fingerprint: true
                        stash name: env.ARTIFACT_NAME, includes: "${env.PUBLISH_PATH}/**/*"
                        
                        logSuccess("Build artifacts archived and ready for transfer")
                        updateGitHubStatus('pending', 'Artifacts Ready', 'Build artifacts ready for deployment...')
                    }
                }
                failure {
                    script {
                        logError("Build stage failed - cleaning up partial builds")
                        powershell '''
                            if (Test-Path "node_modules") {
                                Write-Host "[INFO] Cleaning up node_modules..." -ForegroundColor Yellow
                                Remove-Item -Path "node_modules" -Recurse -Force -ErrorAction SilentlyContinue
                            }
                        '''
                    }
                }
            }
        }

        stage('Deploy to UAT Environment') {
            agent { label "${env.DEPLOY_AGENT_LABEL}" }
            
            steps {
                script {
                    // Wrap the entire deployment in GitHub deployment tracking
                    trackDeployment(env.DEPLOYMENT_ENVIRONMENT) {
                        updateGitHubStatus('pending', 'Deploying', 'Deploying React application to UAT environment...')
                        
                        logSection("APPLICATION DEPLOYMENT TO UAT")
                        logInfo("Deploy server", env.DEPLOY_AGENT_LABEL)
                        logInfo("Target environment", env.DEPLOYMENT_ENVIRONMENT)
                        logInfo("Target path", env.BASE_WEBSITE_PATH)
                        logInfo("Environment URL", env.UAT_URL)
                        
                        def config = readJSON text: env.CONFIG
                        def project = config[0]
                        def projectName = project.project_name ?: project.iis_website_name

                        cleanWs()
                        
                        logSubSection("Artifact Transfer")
                        def artifactsTransferred = false
                        
                        try {
                            logInfo("Transfer method", "copyArtifacts")
                            copyArtifacts(
                                projectName: env.JOB_NAME,
                                selector: specific(env.BUILD_NUMBER),
                                filter: "${env.PUBLISH_PATH}/**/*",
                                target: ".",
                                flatten: false,
                                fingerprintArtifacts: true
                            )
                            artifactsTransferred = true
                            logSuccess("Artifacts transferred successfully via copyArtifacts")
                            
                        } catch (Exception e) {
                            logWarning("copyArtifacts failed, trying fallback method")
                            
                            try {
                                unstash "${env.ARTIFACT_NAME}"
                                artifactsTransferred = true
                                logSuccess("Artifacts transferred successfully via unstash")
                                
                            } catch (Exception e2) {
                                logError("Both transfer methods failed")
                                updateGitHubStatus('failure', 'Transfer Failed', 'Failed to transfer build artifacts')
                                error("Failed to transfer build artifacts from build server")
                            }
                        }
                        
                        if (artifactsTransferred) {
                            logSubSection("Artifact Verification")
                            powershell """
                                if (Test-Path "${env.PUBLISH_PATH}") {
                                    \$files = Get-ChildItem -Path "${env.PUBLISH_PATH}" -File -Recurse
                                    Write-Host "[SUCCESS] Verified \$(\$files.Count) transferred files" -ForegroundColor Green
                                } else {
                                    Write-Host "[ERROR] Artifact directory not found!" -ForegroundColor Red
                                    exit 1
                                }
                            """
                        }

                        try {
                            logSubSection("Environment Preparation")
                            
                            def websitePath = "${env.BASE_WEBSITE_PATH}\\${project.folder_website_name}"
                            def backupsPath = "${env.BASE_BACKUPS_PATH}\\${project.folder_website_name}"
                            def backupPath = "${backupsPath}\\${env.BUILD_NUMBER}_${project.iis_website_name}_${env.ENVIRONMENT}_${new Date().format('yyyy-MM-dd_HHmmss')}"
                            def buildPath = "${env.WORKSPACE}\\${env.PUBLISH_PATH}\\${projectName}"
                            
                            logInfo("Website path", websitePath)
                            logInfo("Backup path", backupPath)
                            logInfo("Source path", buildPath)

                            powershell """
                                # Create required directories
                                @('${websitePath}', '${backupsPath}') | ForEach-Object {
                                    if (-not (Test-Path \$_)) {
                                        New-Item -ItemType Directory -Path \$_ -Force | Out-Null
                                        Write-Host "[INFO] Created directory: \$_" -ForegroundColor Cyan
                                    } else {
                                        Write-Host "[INFO] Directory exists: \$_" -ForegroundColor Cyan
                                    }
                                }
                                
                                Write-Host "[SUCCESS] Environment directories prepared" -ForegroundColor Green
                            """

                            // IIS Service Management
                            if (project.stop_app_pool && project.app_pool_name) {
                                logSubSection("Stopping Application Pool")
                                logInfo("App Pool", project.app_pool_name)
                                
                                powershell """
                                    Import-Module WebAdministration -ErrorAction Stop
                                    
                                    try {
                                        \$appPool = Get-WebAppPoolState -Name '${project.app_pool_name}' -ErrorAction SilentlyContinue
                                        if (\$appPool -and \$appPool.Value -eq 'Started') {
                                            Stop-WebAppPool -Name '${project.app_pool_name}'
                                            
                                            \$timeout = 0
                                            do {
                                                Start-Sleep -Seconds 2
                                                \$state = Get-WebAppPoolState -Name '${project.app_pool_name}'
                                                \$timeout += 2
                                            } while (\$state.Value -ne 'Stopped' -and \$timeout -lt 30)
                                            
                                            Write-Host "[SUCCESS] App Pool stopped: ${project.app_pool_name}" -ForegroundColor Green
                                        } else {
                                            Write-Host "[INFO] App Pool already stopped: ${project.app_pool_name}" -ForegroundColor Cyan
                                        }
                                    } catch {
                                        Write-Host "[ERROR] Failed to stop App Pool: \$(\$_.Exception.Message)" -ForegroundColor Red
                                        exit 1
                                    }
                                """
                            }
                            
                            if (project.stop_iis && project.iis_website_name) {
                                logSubSection("Stopping IIS Website")
                                logInfo("Website", project.iis_website_name)
                                
                                powershell """
                                    Import-Module WebAdministration -ErrorAction Stop
                                    
                                    try {
                                        \$website = Get-WebsiteState -Name '${project.iis_website_name}' -ErrorAction SilentlyContinue
                                        if (\$website -and \$website.Value -eq 'Started') {
                                            Stop-Website -Name '${project.iis_website_name}'
                                            
                                            \$timeout = 0
                                            do {
                                                Start-Sleep -Seconds 2
                                                \$state = Get-WebsiteState -Name '${project.iis_website_name}'
                                                \$timeout += 2
                                            } while (\$state.Value -ne 'Stopped' -and \$timeout -lt 30)
                                            
                                            Write-Host "[SUCCESS] Website stopped: ${project.iis_website_name}" -ForegroundColor Green
                                        } else {
                                            Write-Host "[INFO] Website already stopped: ${project.iis_website_name}" -ForegroundColor Cyan
                                        }
                                    } catch {
                                        Write-Host "[ERROR] Failed to stop Website: \$(\$_.Exception.Message)" -ForegroundColor Red
                                        exit 1
                                    }
                                """
                            }
                            
                            logSubSection("Creating Backup")
                            powershell """
                                \$Files = Get-ChildItem -Path '${websitePath}' -File -Recurse -ErrorAction SilentlyContinue
                                
                                if (\$Files.Count -gt 0) {
                                    New-Item -ItemType Directory -Path '${backupPath}' -Force | Out-Null
                                    Copy-Item -Path '${websitePath}\\*' -Destination '${backupPath}' -Recurse -Force
                                    Write-Host "[SUCCESS] Backup created with \$(\$Files.Count) files" -ForegroundColor Green
                                    Write-Host "[INFO] Backup location: ${backupPath}" -ForegroundColor Cyan
                                } else {
                                    Write-Host "[INFO] No existing files to backup" -ForegroundColor Cyan
                                }
                            """
                            
                            logSubSection("Deploying React Application")
                            powershell """
                                # Remove existing files
                                if (Test-Path "${websitePath}") {
                                    \$ExistingFiles = Get-ChildItem -Path "${websitePath}\\*" -File -ErrorAction SilentlyContinue
                                    if (\$ExistingFiles) {
                                        \$ExistingFiles | Remove-Item -Force
                                        Write-Host "[INFO] Removed existing files from target directory" -ForegroundColor Cyan
                                    }
                                }
                                
                                # Deploy new React files
                                if (Test-Path "${buildPath}") {
                                    Copy-Item -Path "${buildPath}\\*" -Destination "${websitePath}" -Recurse -Force
                                    
                                    \$DeployedFiles = Get-ChildItem -Path "${websitePath}" -File -Recurse
                                    Write-Host "[SUCCESS] Deployed \$(\$DeployedFiles.Count) React files to target directory" -ForegroundColor Green
                                    Write-Host "[INFO] Deployment location: ${websitePath}" -ForegroundColor Cyan
                                } else {
                                    Write-Host "[ERROR] Build artifacts not found at: ${buildPath}" -ForegroundColor Red
                                    exit 1
                                }
                            """

                            // Start IIS services
                            if (project.start_app_pool && project.app_pool_name) {
                                logSubSection("Starting Application Pool")
                                logInfo("App Pool", project.app_pool_name)
                                
                                powershell """
                                    Import-Module WebAdministration -ErrorAction Stop
                                    
                                    try {
                                        \$appPool = Get-WebAppPoolState -Name '${project.app_pool_name}' -ErrorAction SilentlyContinue
                                        if (\$appPool -and \$appPool.Value -eq 'Stopped') {
                                            Start-WebAppPool -Name '${project.app_pool_name}'
                                            Write-Host "[SUCCESS] App Pool started: ${project.app_pool_name}" -ForegroundColor Green
                                        } else {
                                            Write-Host "[INFO] App Pool already running: ${project.app_pool_name}" -ForegroundColor Cyan
                                        }
                                    } catch {
                                        Write-Host "[ERROR] Failed to start App Pool: \$(\$_.Exception.Message)" -ForegroundColor Red
                                        exit 1
                                    }
                                """
                            }
                            
                            if (project.start_iis && project.iis_website_name) {
                                logSubSection("Starting IIS Website")
                                logInfo("Website", project.iis_website_name)
                                
                                powershell """
                                    Import-Module WebAdministration -ErrorAction Stop
                                    
                                    try {
                                        \$website = Get-WebsiteState -Name '${project.iis_website_name}' -ErrorAction SilentlyContinue
                                        if (\$website -and \$website.Value -eq 'Stopped') {
                                            Start-Website -Name '${project.iis_website_name}'
                                            Write-Host "[SUCCESS] Website started: ${project.iis_website_name}" -ForegroundColor Green
                                        } else {
                                            Write-Host "[INFO] Website already running: ${project.iis_website_name}" -ForegroundColor Cyan
                                        }
                                    } catch {
                                        Write-Host "[ERROR] Failed to start Website: \$(\$_.Exception.Message)" -ForegroundColor Red
                                        exit 1
                                    }
                                """
                            }
                            
                            logSuccess("UAT deployment completed successfully for ${projectName}")
                            
                        } catch (Exception e) {
                            logError("UAT deployment failed: ${e.message}")
                            updateGitHubStatus('failure', 'Deployment Failed', "UAT deployment failed for project: ${projectName}")
                            throw e
                        }
                        
                        updateGitHubStatus('success', 'Deployed to UAT', 'React application successfully deployed to UAT environment!')
                    }
                }
            }
        }

        stage('Cleanup and Finalize') {
            agent { label "${env.DEPLOY_AGENT_LABEL}" }
            
            steps {
                script {
                    updateGitHubStatus('pending', 'Cleanup', 'Running cleanup and finalization tasks...')
                    
                    logSection("CLEANUP AND FINALIZATION")
                    
                    def config = readJSON text: env.CONFIG
                    def project = config[0]
                    
                    if (project.is_cleanup) {
                        logSubSection("Backup Cleanup")
                        logInfo("Project", project.project_name ?: project.iis_website_name)
                        
                        def deletePath = "${env.BASE_BACKUPS_PATH}\\${project.folder_website_name}"
                        def retentionMonths = project.backup_file_retention_months ?: 0
                        def maxFilesToDelete = project.amount_files_delete ?: 1

                        if (retentionMonths <= 0) {
                            logInfo("Cleanup strategy", "Keep newest ${maxFilesToDelete} backup(s)")
                            powershell """
                                if (Test-Path '${deletePath}') {
                                    \$folders = Get-ChildItem -Path '${deletePath}' -Directory | 
                                                Sort-Object CreationTime -Descending
                                    
                                    if (\$folders.Count -gt ${maxFilesToDelete}) {
                                        \$foldersToDelete = \$folders | Select-Object -Skip ${maxFilesToDelete}
                                        foreach (\$folder in \$foldersToDelete) {
                                            Write-Host "[INFO] Removing old backup: \$(\$folder.Name)" -ForegroundColor Yellow
                                            Remove-Item -Path \$folder.FullName -Recurse -Force
                                        }
                                        Write-Host "[SUCCESS] Cleaned up \$(\$foldersToDelete.Count) old backup(s)" -ForegroundColor Green
                                    } else {
                                        Write-Host "[INFO] No cleanup needed (${maxFilesToDelete} or fewer backups found)" -ForegroundColor Cyan
                                    }
                                } else {
                                    Write-Host "[INFO] No backup directory found for cleanup" -ForegroundColor Cyan
                                }
                            """
                        } else {
                            logInfo("Cleanup strategy", "Remove backups older than ${retentionMonths} month(s), max ${maxFilesToDelete}")
                            powershell """
                                if (Test-Path '${deletePath}') {
                                    \$currentDate = Get-Date
                                    \$retentionDate = \$currentDate.AddMonths(-${retentionMonths})
                                    \$foldersToDelete = Get-ChildItem -Path '${deletePath}' -Directory | 
                                                      Where-Object { \$_.CreationTime -lt \$retentionDate } |
                                                      Sort-Object CreationTime |
                                                      Select-Object -First ${maxFilesToDelete}
                                    
                                    foreach (\$folder in \$foldersToDelete) {
                                        Write-Host "[INFO] Removing expired backup: \$(\$folder.Name)" -ForegroundColor Yellow
                                        Remove-Item -Path \$folder.FullName -Recurse -Force
                                    }
                                    Write-Host "[SUCCESS] Cleaned up \$(\$foldersToDelete.Count) expired backup(s)" -ForegroundColor Green
                                } else {
                                    Write-Host "[INFO] No backup directory found for cleanup" -ForegroundColor Cyan
                                }
                            """
                        }
                        logSuccess("Backup cleanup completed")
                    } else {
                        logInfo("Cleanup", "Skipped (not enabled for this project)")
                    }
                    
                    updateGitHubStatus('success', 'Pipeline Completed', 'Multi-server React deployment completed successfully!')
                }
            }
        }
        
        stage('Deployment Summary') {
            agent { label "${env.BUILD_AGENT_LABEL}" }
            
            steps {
                script {
                    def config = readJSON text: env.CONFIG
                    def project = config[0]
                    
                    logSection("DEPLOYMENT SUMMARY")
                    logInfo("Build Number", env.BUILD_NUMBER)
                    logInfo("Environment", env.ENVIRONMENT)
                    logInfo("Duration", currentBuild.durationString ?: "In Progress")
                    logInfo("Git Branch", env.GITHUB_BRANCH_NAME)
                    logInfo("Git Commit", env.GIT_COMMIT_SHA ?: "Unknown")
                    
                    logSubSection("Project Details")
                    logInfo("Project Name", project.project_name ?: project.iis_website_name)
                    logInfo("Node.js Version", project.node_version)
                    logInfo("Build Command", env.BUILD_COMMAND)
                    logInfo("Build Output", env.BUILD_PATH)
                    
                    logSubSection("Server Configuration")
                    logInfo("Build Server", env.BUILD_AGENT_LABEL)
                    logInfo("Deploy Server", env.DEPLOY_AGENT_LABEL)
                    logInfo("Website Path", "${env.BASE_WEBSITE_PATH}\\${project.folder_website_name}")
                    logInfo("Backup Path", "${env.BASE_BACKUPS_PATH}\\${project.folder_website_name}")
                    
                    logSubSection("Deployment Tracking")
                    logInfo("Environment", env.DEPLOYMENT_ENVIRONMENT)
                    logInfo("Application URL", env.UAT_URL)
                    logInfo("GitHub Tracking", "Enabled")
                    
                    logSubSection("Pipeline Features")
                    logSuccess("✓ Multi-server architecture (Build + Deploy)")
                    logSuccess("✓ Secure artifact transfer between servers")
                    logSuccess("✓ Jenkins Config File Provider integration")
                    logSuccess("✓ Node.js version management")
                    logSuccess("✓ IIS lifecycle management")
                    logSuccess("✓ Atomic deployments with backup preservation")
                    logSuccess("✓ Intelligent backup retention")
                    logSuccess("✓ GitHub status integration")
                    logSuccess("✓ GitHub deployment tracking")
                    logSuccess("✓ Comprehensive error handling")
                    
                    logSubSection("Next Steps")
                    echo "1. Verify deployed React application functionality"
                    echo "2. Check IIS website and application pool status"
                    echo "3. Review backup files on deploy server"
                    echo "4. Test React application endpoints"
                    echo "5. Monitor application performance"
                    echo "6. Check GitHub Deployments tab for deployment history"
                    
                    logSuccess("Multi-server React deployment pipeline completed successfully!")
                }
            }
        }
    }

    post {
        always {
            script {
                logSection("PIPELINE CLEANUP")
                
                // Cleanup build server
                node(env.BUILD_AGENT_LABEL) {
                    logInfo("Cleanup", "Build server workspace")
                    cleanWs(
                        cleanWhenSuccess: true,
                        cleanWhenFailure: true,
                        cleanWhenAborted: true,
                        cleanWhenUnstable: true,
                        deleteDirs: true,
                        disableDeferredWipeout: true,
                        notFailBuild: true,
                    )
                }
                
                // Cleanup deploy server (if different)
                if (env.BUILD_AGENT_LABEL != env.DEPLOY_AGENT_LABEL) {
                    node(env.DEPLOY_AGENT_LABEL) {
                        logInfo("Cleanup", "Deploy server workspace")
                        cleanWs(
                            cleanWhenSuccess: true,
                            cleanWhenFailure: true,
                            cleanWhenAborted: true,
                            cleanWhenUnstable: true,
                            deleteDirs: true,
                            disableDeferredWipeout: true,
                            notFailBuild: true,
                        )
                    }
                }
                
                logSuccess("Workspace cleanup completed")
            }
        }
        
        success {
            script {
                updateGitHubStatus('success', 'Pipeline Success', 'Multi-server React deployment completed successfully!')
                
                logSection("PIPELINE SUCCESS")
                logSuccess("Multi-server React deployment completed successfully!")
                logInfo("Status", "All stages completed without errors")
                logInfo("Deployment", "React application is now live on UAT environment")
                logInfo("GitHub Status", "Updated with success status")
                logInfo("GitHub Deployment", "Tracked in GitHub Deployments tab")
            }
        }
        
        failure {
            script {
                updateGitHubStatus('failure', 'Pipeline Failed', 'Multi-server React pipeline failed. Check Jenkins logs.')
                
                logSection("PIPELINE FAILURE")
                logError("Multi-server React deployment failed")
                logWarning("Check the following for troubleshooting:")
                echo "• Build server logs for React build failures"
                echo "• Node.js version compatibility"
                echo "• NPM dependency installation process"
                echo "• React build command and output directory"
                echo "• Artifact transfer between servers"
                echo "• Deploy server connectivity"
                echo "• Jenkins Config File Provider configuration"
                echo "• IIS configuration on deploy server"
                echo "• GitHub integration credentials"
                echo "• Project configuration in config file"
                echo "• Agent availability and connectivity"
                echo "• GitHub token permissions for deployment tracking"
            }
        }
        
        unstable {
            script {
                updateGitHubStatus('failure', 'Build Unstable', 'Build completed but with warnings or test failures.')
                logWarning("Pipeline completed but marked as unstable")
            }
        }
        
        aborted {
            script {
                updateGitHubStatus('error', 'Build Aborted', 'Multi-server React pipeline was aborted.')
                logWarning("Pipeline was aborted by user or system")
            }
        }
    }
}

// Professional Logging Functions
def logSection(String title) {
    def separator = "=" * 80
    def padding = " " * ((80 - title.length() - 2) / 2)
    
    echo ""
    echo separator
    echo "${padding} ${title} ${padding}"
    echo separator
    echo ""
}

def logSubSection(String title) {
    def separator = "-" * 60
    echo ""
    echo separator
    echo " ${title}"
    echo separator
}

def logInfo(String label, String value) {
    def paddedLabel = label.padRight(20)
    echo "[INFO] ${paddedLabel}: ${value}"
}

def logSuccess(String message) {
    echo "[SUCCESS] ${message}"
}

def logWarning(String message) {
    echo "[WARNING] ${message}"
}

def logError(String message) {
    echo "[ERROR] ${message}"
}

// Enhanced GitHub Status Update Function
def updateGitHubStatus(String state, String description, String context_description) {
    if (!env.GIT_COMMIT_SHA) {
        logWarning("No Git commit SHA available, attempting to retrieve...")
        
        try {
            def sha = null
            
            try {
                sha = bat(script: '@git rev-parse HEAD', returnStdout: true).trim()
            } catch (Exception e) {
                logWarning("Direct git command failed: ${e.getMessage()}")
            }
            
            if (!sha && env.GIT_COMMIT) {
                sha = env.GIT_COMMIT
            }
            
            if (sha) {
                env.GIT_COMMIT_SHA = sha
                logSuccess("Retrieved Git commit SHA: ${sha}")
            } else {
                logWarning("Unable to retrieve Git commit SHA, skipping GitHub status update")
                return
            }
        } catch (Exception e) {
            logError("Failed to retrieve Git commit SHA: ${e.getMessage()}")
            return
        }
    }
    
    try {
        def statusUrl = "https://api.github.com/repos/${env.GITHUB_REPO_OWNER}/${env.GITHUB_REPO_NAME}/statuses/${env.GIT_COMMIT_SHA}"
        def buildUrl = "${env.BUILD_URL}"
        
        logInfo("GitHub Status", "${state} - ${description}")
        logInfo("Repository", "${env.GITHUB_REPO_OWNER}/${env.GITHUB_REPO_NAME}")
        logInfo("Commit SHA", env.GIT_COMMIT_SHA)
        
        def payloadMap = [:]
        payloadMap.state = state
        payloadMap.target_url = buildUrl
        payloadMap.description = context_description
        payloadMap.context = env.GITHUB_STATUS_CONTEXT
        
        def jsonPayload = groovy.json.JsonOutput.toJson(payloadMap)
        
        withCredentials([usernamePassword(
            credentialsId: env.GITHUB_TOKEN_CREDENTIAL_ID,
            usernameVariable: 'GITHUB_APP_ID',
            passwordVariable: 'GITHUB_TOKEN'
        )]) {
            def headersMap = [:]
            headersMap.put('Accept', 'application/vnd.github.v3+json')
            headersMap.put('Content-Type', 'application/json')
            headersMap.put('User-Agent', 'Jenkins-Multi-Server-React/2.0')
            
            def authHeader = "Bearer " + env.GITHUB_TOKEN
            headersMap.put('Authorization', authHeader)
            
            def requestHeaders = []
            headersMap.each { key, value ->
                if (key == 'Authorization') {
                    requestHeaders.add([name: key, value: value, maskValue: true])
                } else {
                    requestHeaders.add([name: key, value: value])
                }
            }
            
            try {
                def response = httpRequest(
                    url: statusUrl,
                    httpMode: 'POST',
                    customHeaders: requestHeaders,
                    requestBody: jsonPayload,
                    validResponseCodes: '200:299',
                    timeout: 30,
                    ignoreSslErrors: false,
                    consoleLogResponseBody: false,
                    quiet: true
                )
                
                logSuccess("GitHub status updated successfully (HTTP ${response.status})")
                
            } catch (Exception httpEx) {
                logWarning("Primary GitHub auth failed, trying fallback method...")
                
                try {
                    def fallbackHeaders = []
                    headersMap.each { key, value ->
                        if (key == 'Authorization') {
                            def fallbackAuth = "token " + env.GITHUB_TOKEN
                            fallbackHeaders.add([name: key, value: fallbackAuth, maskValue: true])
                        } else {
                            fallbackHeaders.add([name: key, value: value])
                        }
                    }
                    
                    def fallbackResponse = httpRequest(
                        url: statusUrl,
                        httpMode: 'POST',
                        customHeaders: fallbackHeaders,
                        requestBody: jsonPayload,
                        validResponseCodes: '200:299',
                        timeout: 30,
                        ignoreSslErrors: false,
                        consoleLogResponseBody: false,
                        quiet: true
                    )
                    
                    logSuccess("GitHub status updated with fallback method (HTTP ${fallbackResponse.status})")
                } catch (Exception fallbackEx) {
                    logWarning("Both GitHub authentication methods failed - this won't affect the build process")
                }
            }
        }
        
    } catch (Exception e) {
        logWarning("Failed to update GitHub status: ${e.class.simpleName} - ${e.getMessage()}")
        logInfo("Note", "GitHub status update failure won't affect the build process")
    }
}

// ====================================================================
//                    DEPLOYMENT TRACKING FUNCTIONS
// ====================================================================

// Create deployment record at start of deployment
def createDeployment(String environment, String ref) {
    if (!env.GIT_COMMIT_SHA) {
        logWarning("No Git commit SHA available, skipping deployment creation")
        return null
    }
    
    try {
        def deploymentUrl = "https://api.github.com/repos/${env.GITHUB_REPO_OWNER}/${env.GITHUB_REPO_NAME}/deployments"
        
        logInfo("Creating Deployment", "Environment: ${environment}")
        logInfo("Deployment Ref", ref)
        
        def payloadMap = [:]
        payloadMap.ref = ref
        payloadMap.environment = environment
        payloadMap.description = "Deployment to ${environment} environment via Jenkins"
        payloadMap.auto_merge = false
        payloadMap.required_contexts = []
        
        def jsonPayload = groovy.json.JsonOutput.toJson(payloadMap)
        
        withCredentials([usernamePassword(
            credentialsId: env.GITHUB_TOKEN_CREDENTIAL_ID,
            usernameVariable: 'GITHUB_APP_ID',
            passwordVariable: 'GITHUB_TOKEN'
        )]) {
            def headersMap = [:]
            headersMap.put('Accept', 'application/vnd.github+json')
            headersMap.put('Content-Type', 'application/json')
            headersMap.put('User-Agent', 'Jenkins-Deployment-Tracker/1.0')
            headersMap.put('Authorization', "Bearer " + env.GITHUB_TOKEN)
            
            def requestHeaders = []
            headersMap.each { key, value ->
                if (key == 'Authorization') {
                    requestHeaders.add([name: key, value: value, maskValue: true])
                } else {
                    requestHeaders.add([name: key, value: value])
                }
            }
            
            def response = httpRequest(
                url: deploymentUrl,
                httpMode: 'POST',
                customHeaders: requestHeaders,
                requestBody: jsonPayload,
                validResponseCodes: '200:299,422',
                timeout: 30,
                ignoreSslErrors: false,
                quiet: true
            )
            
            if (response.status == 422) {
                logWarning("GitHub API returned 422 - deployment may already exist")
                return null
            }
            
            def responseData = readJSON text: response.content
            env.DEPLOYMENT_ID = responseData.id.toString()
            
            logSuccess("GitHub deployment created successfully (ID: ${env.DEPLOYMENT_ID})")
            return env.DEPLOYMENT_ID
        }
        
    } catch (Exception e) {
        logWarning("Failed to create GitHub deployment: ${e.getMessage()}")
        return null
    }
}

// Update deployment status
def updateDeploymentStatus(String deploymentId, String state, String description, String environmentUrl = null) {
    if (!deploymentId) {
        logWarning("No deployment ID available, skipping status update")
        return
    }
    
    try {
        def statusUrl = "https://api.github.com/repos/${env.GITHUB_REPO_OWNER}/${env.GITHUB_REPO_NAME}/deployments/${deploymentId}/statuses"
        
        logInfo("Updating Deployment", "ID: ${deploymentId}, State: ${state}")
        
        def payloadMap = [:]
        payloadMap.state = state
        payloadMap.description = description
        payloadMap.log_url = "${env.BUILD_URL}console"
        
        if (environmentUrl) {
            payloadMap.environment_url = environmentUrl
        }
        
        def jsonPayload = groovy.json.JsonOutput.toJson(payloadMap)
        
        withCredentials([usernamePassword(
            credentialsId: env.GITHUB_TOKEN_CREDENTIAL_ID,
            usernameVariable: 'GITHUB_APP_ID',
            passwordVariable: 'GITHUB_TOKEN'
        )]) {
            def headersMap = [:]
            headersMap.put('Accept', 'application/vnd.github+json')
            headersMap.put('Content-Type', 'application/json')
            headersMap.put('User-Agent', 'Jenkins-Deployment-Tracker/1.0')
            headersMap.put('Authorization', "Bearer " + env.GITHUB_TOKEN)
            
            def requestHeaders = []
            headersMap.each { key, value ->
                if (key == 'Authorization') {
                    requestHeaders.add([name: key, value: value, maskValue: true])
                } else {
                    requestHeaders.add([name: key, value: value])
                }
            }
            
            def response = httpRequest(
                url: statusUrl,
                httpMode: 'POST',
                customHeaders: requestHeaders,
                requestBody: jsonPayload,
                validResponseCodes: '200:299',
                timeout: 30,
                ignoreSslErrors: false,
                quiet: true
            )
            
            logSuccess("Deployment status updated: ${state}")
        }
        
    } catch (Exception e) {
        logWarning("Failed to update deployment status: ${e.getMessage()}")
    }
}

// Enhanced pipeline integration
def trackDeployment(String environment, Closure deploymentSteps) {
    def deploymentId = null
    
    try {
        // Create deployment record
        deploymentId = createDeployment(environment, env.GIT_COMMIT_SHA ?: env.GITHUB_BRANCH_NAME)
        
        if (deploymentId) {
            updateDeploymentStatus(deploymentId, 'in_progress', "Deployment to ${environment} is in progress...")
        }
        
        // Execute deployment steps
        deploymentSteps()
        
        // Mark as successful
        if (deploymentId) {
            def environmentUrl = environment.toLowerCase() == 'uat' ? env.UAT_URL : null
            updateDeploymentStatus(deploymentId, 'success', "Successfully deployed to ${environment}", environmentUrl)
        }
        
    } catch (Exception e) {
        // Mark as failed
        if (deploymentId) {
            updateDeploymentStatus(deploymentId, 'failure', "Deployment to ${environment} failed: ${e.getMessage()}")
        }
        
        currentBuild.description = "❌ ${environment} deployment failed (Build #${env.BUILD_NUMBER})"
        throw e
    }
}