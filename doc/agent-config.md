# การติดตั้ง Jenkins Agent 

## เริ่มต้นใช้งาน

```bash
# 1. สร้าง Directory สำหรับ Jenkins
mkdir D:\Jenkins
cd D:\Jenkins

# 2. ดาวน์โหลด Jenkins Agent JAR
curl.exe -sO https://domain.com/jnlpJars/agent.jar

# 3. ดาวน์โหลดไฟล์การตั้งค่า (ดูคู่มือการติดตั้ง)

# 4. ตั้งค่า jenkins-agent.xml ตามค่าของคุณ

# 5. รัน Startup Script
jenkins-startup.bat
```

## คู่มือการติดตั้ง

### ขั้นตอนที่ 1: ติดตั้ง Java 21

1. ดาวน์โหลดจาก [Oracle JDK 21](https://www.oracle.com/java/technologies/downloads/#jdk21-windows)
2. ติดตั้งโดยใช้ x64 MSI Installer
3. ตรวจสอบการติดตั้ง:

```bash
java -version
# ควรได้: java version "21.0.7" 2025-04-15 LTS
```

![Java Installation](https://github.com/Pratchaya0/jenkin-setup/blob/win-server/utilities/java-installation-verification.png)

### ขั้นตอนที่ 2: ตั้งค่า Jenkins Master

1. ไปที่ `https://domain.com/manage/computer/`
2. คลิก **New Node**
3. ตั้งค่า Agent ตามต้องการ
4. บันทึกคำสั่งการเชื่อมต่อที่ได้

![Jenkins Master Configuration I](https://github.com/Pratchaya0/jenkin-setup/blob/win-server/utilities/java-master-configuration-i.png)
![Jenkins Master Configuration II](https://github.com/Pratchaya0/jenkin-setup/blob/win-server/utilities/java-master-configuration-ii.png)
![Jenkins Master Configuration Result](https://github.com/Pratchaya0/jenkin-setup/blob/win-server/utilities/java-master-configuration-result.png)

### ขั้นตอนที่ 3: เตรียม Agent Machine

สร้าง Working Directory และดาวน์โหลดไฟล์ที่จำเป็น:

```bash
mkdir D:\Jenkins
cd D:\Jenkins

curl.exe -sO https://domain.com/jnlpJars/agent.jar
```

### ขั้นตอนที่ 4: ดาวน์โหลดไฟล์การตั้งค่า

ดาวน์โหลดไฟล์เหล่านี้ไปยัง `D:\Jenkins\`:

- [`jenkins-agent.exe`](https://github.com/Pratchaya0/jenkin-setup/blob/win-server/jenkins-agent.exe) (Windows Service Wrapper)
- [`jenkins-agent.xml`](https://github.com/Pratchaya0/jenkin-setup/blob/win-server/jenkins-agent.xml) (การตั้งค่า Service)
- [`jenkins-startup.bat`](https://github.com/Pratchaya0/jenkin-setup/blob/win-server/jenkins-startup.bat) (Startup Script)

[`zip 3 ไฟล์`](https://github.com/Pratchaya0/jenkin-setup/blob/win-server/starter-kit.zip)

### ขั้นตอนที่ 5: โครงสร้าง Directory

Directory สุดท้ายควรมีหน้าตาดังนี้:

```
D:\Jenkins\
├── jenkins-agent.exe
├── jenkins-agent.xml
├── jenkins-startup.bat
├── agent.jar
└── logs\ (สร้างอัตโนมัติ)
```

## การตั้งค่า

### แม่แบบ jenkins-agent.xml

```xml
<service>
  <id>jenkins-agent</id>
  <name>Jenkins Agent</name>
  <description>Jenkins Build Agent</description>
  <executable>java</executable>
  <arguments>-jar agent.jar -url https://domain.com/ -secret YOUR-SECRET-KEY -name "agent-132" -workDir "D:\Jenkins"</arguments>
  <workingdirectory>D:\Jenkins\</workingdirectory>
  <logmode>rotate</logmode>
  <onfailure action="restart" delay="10 sec"/>
  <startmode>Automatic</startmode>
  <interactive>false</interactive>
</service>
```

## การจัดการ Service

### เริ่มต้น Service
```bash
jenkins-startup.bat
```

### ตรวจสอบสถานะ Service
```bash
sc query jenkins-agent
```

### หยุด Service
```bash
sc stop jenkins-agent
```

### รีสตาร์ท Service
```bash
sc stop jenkins-agent && sc start jenkins-agent
```

## การแก้ไขปัญหา

### ปัญหาที่พบบ่อย

#### ไม่พบ Java
```bash
# ตรวจสอบการติดตั้ง Java
java -version

# เพิ่ม Java ใน PATH หากจำเป็น
set PATH=%PATH%;C:\Program Files\Java\jdk-21\bin
```

#### ปัญหาการเชื่อมต่อ
- ตรวจสอบการเชื่อมต่อเครือข่ายไปยัง Jenkins Master
- ตรวจสอบการตั้งค่า Firewall
- ให้แน่ใจว่า URL และ Port ถูกต้อง

#### การยืนยันตัวตนล้มเหลว
- ตรวจสอบ Secret Key ใน `jenkins-agent.xml`
- สร้าง Agent Credentials ใหม่หากจำเป็น

#### ไม่มีสิทธิ์เข้าถึง
- รัน Startup Script ในฐานะ Administrator
- ตรวจสอบสิทธิ์ของ Windows Service

### ไฟล์ Log

ตรวจสอบตำแหน่งเหล่านี้เพื่อ Debug:

- `D:\Jenkins\jenkins-agent.out.log` - Standard Output
- `D:\Jenkins\jenkins-agent.err.log` - Error Output
- Windows Event Viewer → Windows Logs → Application

### คำสั่งที่เป็นประโยชน์

```bash
# ดู Service Logs
type D:\Jenkins\jenkins-agent.out.log

# ตรวจสอบการตั้งค่า Service
sc qc jenkins-agent

# ทดสอบการเชื่อมต่อ Java
java -jar agent.jar -url https://domain.com/ -secret YOUR-SECRET-KEY -name "test-connection"
```

## การบำรุงรักษา

### งานประจำ

- ตรวจสอบขนาดไฟล์ Log ทุกสัปดาห์
- ตรวจสอบว่า Service ทำงานหลัง Windows Update
- อัปเดต Java เมื่อมี Security Patches
- ติดตามพื้นที่ดิสก์ใน Jenkins Directory

### การอัปเดต

เพื่ออัปเดต Jenkins Agent:

1. หยุด Service: `sc stop jenkins-agent`
2. ดาวน์โหลด `agent.jar` ใหม่: `curl.exe -sO https://domain.com/jnlpJars/agent.jar`
3. เริ่ม Service: `sc start jenkins-agent`

## แหล่งข้อมูลเพิ่มเติม

- [เอกสารทางการของ Jenkins](https://www.jenkins.io/doc/)
- [การตั้งค่า Jenkins Agent](https://www.jenkins.io/doc/book/using/using-agents/)
- [การจัดการ Windows Service](https://docs.microsoft.com/en-us/windows-server/administration/windows-commands/sc)

---

**อัปเดตล่าสุด:** กรกฎาคม 2025  