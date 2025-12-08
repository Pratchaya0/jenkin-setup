# Jenkins Installation
Updated 2025-12-07 [@Pratchaya0](https://www.github.com/Pratchaya0)

# Server setting
### Download
Jenkins: 
- https://www.jenkins.io/download/#downloading-jenkins
- https://www.jenkins.io/doc/book/installing/windows/
- UAT ใช้ jenkins.war
- ถ้าใช้ .war setting jenkins.xml  
```xml
<!-- UAT -->
<service>
  <id>jenkins</id>
  <name>Jenkins</name>
  <description>This service runs Jenkins automation server.</description>
  <env name="JENKINS_HOME" value="%ProgramData%\Jenkins\.jenkins"/>
  
  <executable>C:\Program Files\Java\zulu21.34.19-ca-jre21.0.3-win_x64\bin\java.exe</executable>
  
  <!-- CONSERVATIVE SETTINGS FOR 4GB RAM -->
  <arguments>
    -Xrs 
    -Xms512m 
    -Xmx2048m
    -XX:+UseSerialGC 
    -XX:+UseCompressedOops 
    -XX:MetaspaceSize=256m
    -XX:MaxMetaspaceSize=512m
    -Djava.awt.headless=true 
    -Dfile.encoding=UTF-8 
    -Dhudson.lifecycle=hudson.lifecycle.WindowsServiceLifecycle 
    -jar "D:\Program Files\Jenkins\jenkins.war" 
    --httpPort=9090 
    --webroot="%ProgramData%\Jenkins\war"
  </arguments>
  
  <logmode>rotate</logmode>
  <onfailure action="restart"/>
  
  <extensions>
    <extension enabled="true" className="winsw.Plugins.RunawayProcessKiller.RunawayProcessKillerExtension" id="killOnStartup">
      <pidfile>%ProgramData%\Jenkins\jenkins.pid</pidfile>
      <stopTimeout>10000</stopTimeout>
      <stopParentFirst>false</stopParentFirst>
    </extension>
  </extensions>
</service>
```

Java 21 (Oracle):
- https://www.oracle.com/java/technologies/downloads/#jdk21-windows

### IIS Reverse Proxy
- Domain name: jenkins.siamsmile.co.th
- https://www.jenkins.io/doc/book/system-administration/reverse-proxy-configuration-with-jenkins/reverse-proxy-configuration-iis/
- web.config
```xml
<!-- UAT -->
<?xml version="1.0" encoding="UTF-8"?>
<configuration>
    <system.webServer>
        <rewrite>
            <rules>
                <!-- Redirect logout endpoint to auth server -->
                <rule name="RedirectLogout" enabled="true" stopProcessing="true">
                    <match url="^connect/endsession$" />
                    <conditions>
                        <add input="{HTTP_HOST}" pattern="^jenkins\.uatsiamsmile\.com$" />
                    </conditions>
                    <action type="Redirect" url="https://authlogin.uatsiamsmile.com/connect/endsession" appendQueryString="true" redirectType="Found" />
                </rule>
                
                <!-- Redirect other auth endpoints to auth server -->
                <rule name="RedirectAuthEndpoints" enabled="true" stopProcessing="true">
                    <match url="^connect/(.+)$" />
                    <conditions>
                        <add input="{HTTP_HOST}" pattern="^jenkins\.uatsiamsmile\.com$" />
                    </conditions>
                    <action type="Redirect" url="https://authlogin.uatsiamsmile.com/connect/{R:1}" appendQueryString="true" redirectType="Found" />
                </rule>
                
                <!-- Force HTTPS redirect -->
                <rule name="ForceHTTPS" enabled="true" stopProcessing="true">
                    <match url="(.*)" />
                    <conditions>
                        <add input="{HTTPS}" pattern="off" ignoreCase="true" />
                        <add input="{HTTP_HOST}" pattern="^jenkins\.uatsiamsmile\.com$" />
                    </conditions>
                    <action type="Redirect" url="https://jenkins.uatsiamsmile.com/{R:1}" appendQueryString="true" redirectType="Permanent" />
                </rule>
                
                <!-- Redirect IP and localhost with port to domain -->
                <rule name="RedirectToDomain" enabled="true" stopProcessing="true">
                    <match url="(.*)" />
                    <conditions>
                        <add input="{HTTP_HOST}" pattern="^(localhost|147\.50\.164\.136):9090$" />
                    </conditions>
                    <action type="Redirect" url="https://jenkins.uatsiamsmile.com/{R:1}" appendQueryString="true" redirectType="Permanent" />
                </rule>
                
                <!-- Handle OIDC login initiation -->
                <rule name="OIDCLogin" enabled="true" stopProcessing="true">
                    <match url="^securityRealm/commenceLogin$" />
                    <conditions>
                        <add input="{HTTP_HOST}" pattern="^jenkins\.uatsiamsmile\.com$" />
                    </conditions>
                    <action type="Rewrite" url="http://localhost:9090/securityRealm/commenceLogin" appendQueryString="true" />
                    <serverVariables>
                        <set name="HTTP_X_FORWARDED_PROTO" value="https" />
                        <set name="HTTP_X_FORWARDED_HOST" value="{HTTP_HOST}" />
                        <set name="HTTP_X_FORWARDED_FOR" value="{REMOTE_ADDR}" />
                        <set name="HTTP_X_FORWARDED_PORT" value="443" />
                    </serverVariables>
                </rule>
                
                <!-- Handle OIDC callback -->
                <rule name="OIDCCallback" enabled="true" stopProcessing="true">
                    <match url="^securityRealm/finishLogin$" />
                    <conditions>
                        <add input="{HTTP_HOST}" pattern="^jenkins\.uatsiamsmile\.com$" />
                    </conditions>
                    <action type="Rewrite" url="http://localhost:9090/securityRealm/finishLogin" appendQueryString="true" />
                    <serverVariables>
                        <set name="HTTP_X_FORWARDED_PROTO" value="https" />
                        <set name="HTTP_X_FORWARDED_HOST" value="{HTTP_HOST}" />
                        <set name="HTTP_X_FORWARDED_FOR" value="{REMOTE_ADDR}" />
                        <set name="HTTP_X_FORWARDED_PORT" value="443" />
                    </serverVariables>
                </rule>
                
                <!-- Handle logout -->
                <rule name="OIDCLogout" enabled="true" stopProcessing="true">
                    <match url="^logout$" />
                    <conditions>
                        <add input="{HTTP_HOST}" pattern="^jenkins\.uatsiamsmile\.com$" />
                    </conditions>
                    <action type="Rewrite" url="http://localhost:9090/logout" appendQueryString="true" />
                    <serverVariables>
                        <set name="HTTP_X_FORWARDED_PROTO" value="https" />
                        <set name="HTTP_X_FORWARDED_HOST" value="{HTTP_HOST}" />
                        <set name="HTTP_X_FORWARDED_FOR" value="{REMOTE_ADDR}" />
                        <set name="HTTP_X_FORWARDED_PORT" value="443" />
                    </serverVariables>
                </rule>

                <!-- Add this BEFORE the ReverseProxyToJenkins rule -->
                <rule name="GitHubWebhook" enabled="true" stopProcessing="true">
                    <match url="^github-webhook/?(.*)" />
                    <conditions>
                        <add input="{HTTP_HOST}" pattern="^jenkins\.uatsiamsmile\.com$" />
                        <add input="{REQUEST_METHOD}" pattern="POST" />
                    </conditions>
                    <action type="Rewrite" url="http://localhost:9090/github-webhook/{R:1}" appendQueryString="true" />
                    <serverVariables>
                        <set name="HTTP_X_FORWARDED_PROTO" value="https" />
                        <set name="HTTP_X_FORWARDED_HOST" value="{HTTP_HOST}" />
                        <set name="HTTP_X_FORWARDED_FOR" value="{REMOTE_ADDR}" />
                        <set name="HTTP_X_FORWARDED_PORT" value="443" />
                        <set name="HTTP_X_REAL_IP" value="{REMOTE_ADDR}" />
                    </serverVariables>
                </rule>
                                
                <!-- Main reverse proxy rule -->
                <rule name="ReverseProxyToJenkins" enabled="true" stopProcessing="true">
                    <match url="(.*)" />
                    <conditions>
                        <add input="{HTTP_HOST}" pattern="^jenkins\.uatsiamsmile\.com$" />
                    </conditions>
                    <action type="Rewrite" url="http://localhost:9090/{R:1}" appendQueryString="true" />
                    <serverVariables>
                        <set name="HTTP_X_FORWARDED_PROTO" value="https" />
                        <set name="HTTP_X_FORWARDED_HOST" value="{HTTP_HOST}" />
                        <set name="HTTP_X_FORWARDED_FOR" value="{REMOTE_ADDR}" />
                        <set name="HTTP_X_FORWARDED_PORT" value="443" />
                    </serverVariables>
                </rule>
            </rules>
        </rewrite>
        <httpProtocol>
            <customHeaders>
                <add name="Canonical" value="https://jenkins.uatsiamsmile.com" />
            </customHeaders>
        </httpProtocol>
        <security>
            <requestFiltering allowDoubleEscaping="true">
                <!-- INCREASED LIMITS FOR JWT TOKENS -->
                <requestLimits maxQueryString="32768" 
                      maxUrl="16384" 
                      maxAllowedContentLength="10485760" />
            </requestFiltering>
        </security>
    </system.webServer>
</configuration>
```

### GitHub publishes their IPs
- https://api.github.com/meta

# Github setting
- Doc: https://demopos.devsiamsmile.com/devops/new-agent
- Doc (public): https://github.com/Pratchaya0/jenkin-setup/blob/win-server/doc/github-app-authentication.md

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
- Jenkins config.xml
```xml
...
<securityRealm class="org.jenkinsci.plugins.oic.OicSecurityRealm" plugin="oic-auth@4.609.v9de140f63d01">
    <userIdStrategy class="jenkins.model.IdStrategy$CaseInsensitive"/>
    <groupIdStrategy class="jenkins.model.IdStrategy$CaseInsensitive"/>
    <clientId> ----------- INSERT ----------------- </clientId>
    <clientSecret>{ ---------- INSERT ---------------- }</clientSecret>
    <userNameField>employee_code</userNameField>
    <fullNameFieldName>employee_firstname</fullNameFieldName>
    <groupsFieldName>role</groupsFieldName>
    <disableSslVerification>false</disableSslVerification>
    <logoutFromOpenidProvider>true</logoutFromOpenidProvider>
    <postLogoutRedirectUrl>https://jenkins.uatsiamsmile.com/connect/endsession</postLogoutRedirectUrl>
    <serverConfiguration class="org.jenkinsci.plugins.oic.OicServerWellKnownConfiguration">
      <wellKnownOpenIDConfigurationUrl>https://authlogin.uatsiamsmile.com/.well-known/openid-configuration</wellKnownOpenIDConfigurationUrl>
      <scopesOverride>openid profile roles email employee_profile employee_team employee_position employee_department employee_branch</scopesOverride>
    </serverConfiguration>
    <rootURLFromRequest>true</rootURLFromRequest>
    <sendScopesInTokenRequest>false</sendScopesInTokenRequest>
    <tokenExpirationCheckDisabled>false</tokenExpirationCheckDisabled>
    <allowTokenAccessWithoutOicSession>false</allowTokenAccessWithoutOicSession>
    <properties>
      <org.jenkinsci.plugins.oic.properties.Pkce/>
      <org.jenkinsci.plugins.oic.properties.AllowedTokenExpirationClockSkew>
        <valueSeconds>0</valueSeconds>
      </org.jenkinsci.plugins.oic.properties.AllowedTokenExpirationClockSkew>
    </properties>
</securityRealm>
...
```

# Jenkins *Slave* setting
- Doc: https://demopos.devsiamsmile.com/devops/new-agent
- Doc (public): https://github.com/Pratchaya0/jenkin-setup/blob/win-server/doc/agent-config.md
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
