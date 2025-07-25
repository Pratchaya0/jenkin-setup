# การสร้าง Github App Authentication

### 1.1 Navigate to GitHub App Creation
1. Go to your GitHub organization settings
2. Navigate to **Settings** → **Developer settings** → [**GitHub Apps**](https://github.com/settings/apps)
3. Click **"New GitHub App"**

#### รูปตัวอย่าง
[Page I](https://github.com/Pratchaya0/jenkin-setup/blob/win-server/utilities/githubapp-i.png)
[Page II](https://github.com/Pratchaya0/jenkin-setup/blob/win-server/utilities/githubapp-ii.png)
[Install App I](https://github.com/Pratchaya0/jenkin-setup/blob/win-server/utilities/githubapp-iii.png)
[Install App II](https://github.com/Pratchaya0/jenkin-setup/blob/win-server/utilities/githubapp-iv.png)

### 1.2 Basic Information
```
App Name: Jenkins-CI-CD-Pipeline
Description: Jenkins CI/CD automation for deployment pipelines
Homepage URL: https://your-jenkins-instance.com
Webhook URL: https://your-jenkins-instance.com/github-webhook/
Webhook Secret: [Generate a secure random string]
```
## Step 2: Required Permissions

### 2.1 Repository Permissions
Configure these permissions for your GitHub App:

| Permission | Access Level | Purpose |
|------------|--------------|---------|
| **Contents** | Read | Access repository code and files |
| **Metadata** | Read | Basic repository information |
| **Pull requests** | Read | Access PR information |
| **Commit statuses** | Write | Update commit status (pending/success/failure) |
| **Deployments** | Write | Create and update deployment records |
| **Actions** | Read | Access GitHub Actions (if needed) |

### 2.2 Organization Permissions
| Permission | Access Level | Purpose |
|------------|--------------|---------|
| **Members** | Read | Access organization member information |

### 2.3 Account Permissions
No additional account permissions required.

## Step 3: Subscribe to Events
Select these webhook events:
- **Push** - Trigger builds on code push
- **Pull request** - Trigger builds on PR events
- **Deployment** - Track deployment events
- **Deployment status** - Track deployment status changes

### 4.1 Install the App
1. After creating the app, go to **"Install App"** tab
2. Select your organization
3. Choose **"All repositories"** or select specific repositories
4. Complete the installation

### 4.2 Generate Private Key
1. Go to your GitHub App settings
2. Scroll down to **"Private keys"** section
3. Click **"Generate a private key"**
4. Download the `.pem` file securely

### 4.3 Get App Information
Note these values from your GitHub App:
- **App ID** (found in app settings)
- **Private Key** (the .pem file you downloaded)