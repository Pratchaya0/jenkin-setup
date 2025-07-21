# การติดตั้ง Jenkin Job

## เริ่มต้นใช้งาน

### ขั้นตอนที่ 1: ตั้งค่า Jenkins Master

1. ไปที่ `https://domain.com/`
2. คลิก **New Item**
3. สร้าง **Folder**

![Job Folder Create I](https://github.com/Pratchaya0/jenkin-setup/blob/win-server/utilities/job-folder-create-i.png)
![Job Folder Create II](https://github.com/Pratchaya0/jenkin-setup/blob/win-server/utilities/job-folder-create-ii.png)
![Job Folder Create III](https://github.com/Pratchaya0/jenkin-setup/blob/win-server/utilities/job-folder-create-iii.png)

4. จากนั้นสร้าง **Job** แบบ **Pipeline** ใน **Folder** โดยอิงโครงสร้าง **Pipeline** จากตัวอย่าง

- [`React Pipeline`](https://github.com/Pratchaya0/jenkin-setup/blob/win-server/pipeline/react-pipeline.groovy) (ต้องมีการ config เพิ่มเติม)
- [`.NET Pipeline`](https://github.com/Pratchaya0/jenkin-setup/blob/win-server/pipeline/dotnet-pipeline.groovy) (ต้องมีการ config เพิ่มเติม)

![Job Pipeline Create I](https://github.com/Pratchaya0/jenkin-setup/blob/win-server/utilities/job-pipeline-create-i.png)
![Job Pipeline Create II](https://github.com/Pratchaya0/jenkin-setup/blob/win-server/utilities/job-pipeline-create-ii.png)

5. กด **Build Now** เพื่อทดสอบ

![ฺBuild Pipeline Result](https://github.com/Pratchaya0/jenkin-setup/blob/win-server/utilities/build-pipeline.png)

