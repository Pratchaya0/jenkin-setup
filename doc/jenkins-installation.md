# Jenkins Installation
Updated 2025-12-07 [@octokatherine](https://www.github.com/Pratchaya0)

# Server setting
### Download
Jenkins: 
- https://www.jenkins.io/download/#downloading-jenkins
- https://www.jenkins.io/doc/book/installing/windows/

Java 21 (Oracle):
- https://www.oracle.com/java/technologies/downloads/#jdk21-windows

### IIS Reverse Proxy
- Domain name: jenkins.siamsmile.co.th
- web.config **Paste url form Discord** **Copy form UAT**

### GitHub publishes their IPs
- https://api.github.com/meta

# Jenkins *Master* setting
### Plugin
- .NET SDK Support
- Ant Plugin
- Build Monitor View
- Build Timeout
- Copy Artifact Plugin
- Dark Theme
- HTTP Request Version
- **OpenId Connect Authentication Plugin**
- Pipeline Utility Steps 
- Pipeline: GitHub Groovy Libraries 
- Pipeline: Stage View Plugin Version 
- Pre SCM BuildStep Plugin 
- **Role-based Authorization Strategy**
- SSH Build Agents plugin
- Timestamper Version 
- Versions Node Monitors plugin 
- Workspace Cleanup Plugin

### Oauth 
- **Check UAT**

# Jenkins *Slave* setting
- https://demopos.devsiamsmile.com/devops/new-agent
- Fix jenkins-agent.xml
```xml
<service>
  <id>jenkins-agent</id>
  <name>Jenkins Agent</name>
  <description>Jenkins Build Agent</description>
  <executable>java</executable>
   <arguments>
    -Xms128m 
    -Xmx1024m 
    -XX:+UseG1GC 
    -XX:MaxGCPauseMillis=200 
    -XX:+UseCompressedOops 
    -XX:+UseCompressedClassPointers 
    -XX:MaxMetaspaceSize=128m 
    -Djava.awt.headless=true 
    -Dfile.encoding=UTF-8 
    -Dhudson.remoting.Launcher.pingIntervalSec=300 
    -Dhudson.slaves.ChannelPinger.pingTimeoutSec=240 
    -jar agent.jar 
    -url https://domain.com/ ------------------*** INSERT YOUR DOMAIN
    -secret YOUR-SECRET-KEY -------------------*** INSERT YOUR KEY  
    -name YOUR-AGENT-NAME  --------------------*** INSERT YOUR AGENT NAME 
    -workDir "D:\Jenkins"
  </arguments>
  <workingdirectory>D:\Jenkins\</workingdirectory>
  <logmode>rotate</logmode>
  <onfailure action="restart" delay="10 sec"/>
  <startmode>Automatic</startmode>
  <interactive>false</interactive>
</service>
```

# Testing

### Pipeline
- Best practices for using webhooks: https://docs.github.com/en/webhooks/using-webhooks/best-practices-for-using-webhooks
```groovy
pipeline {
    agent any

    environment {
        SECRET = credentials('github_webhook_secret')
    }

    stages {
        stage('Validate Signature') {
            steps {
                script {

                    echo "📫 Delivery ID: ${env.DELIVERY_ID}"
                    echo "📦 Event: ${env.GITHUB_EVENT}"
                    echo "🌐 Sender IP: ${env.SENDER_IP}"

                    // === 1. Validate signature (HMAC SHA-256) ===
                    def payload = env.PAYLOAD
                    def theirSig = env.SIG_HEADER

                    if (!theirSig || !theirSig.startsWith("sha256=")) {
                        error("❌ Missing Github Signature header")
                    }

                    def mac = javax.crypto.Mac.getInstance("HmacSHA256")
                    mac.init(new javax.crypto.spec.SecretKeySpec(SECRET.getBytes("UTF-8"), "HmacSHA256"))
                    def computed = mac.doFinal(payload.getBytes("UTF-8"))
                    def mySig = "sha256=" + computed.encodeHex().toString()

                    if (mySig != theirSig) {
                        error("❌ Signature mismatch — NOT from GitHub!")
                    }

                    echo "✔ Signature validated"
                }
            }
        }

        stage('Check Event Type') {
            steps {
                script {
                    switch (env.GITHUB_EVENT) {
                        case "push":
                            echo "Push event received"
                            break
                        case "pull_request":
                            echo "PR event received"
                            break
                        default:
                            echo "Ignoring unsupported event: ${env.GITHUB_EVENT}"
                            currentBuild.result = "SUCCESS"
                            return
                    }
                }
            }
        }

        stage('Process') {
            steps {
                echo "🚀 Webhook validated and processed safely!"
                echo "Client IP: ${env.SENDER_IP}"
            }
        }
    }
}
```
