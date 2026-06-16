# Setup Guide – EB Demo App (Java + S3)

This guide walks through every step needed to go from a fresh GitHub repo to a fully automated Elastic Beanstalk deployment with S3 integration.

---

## Prerequisites

- AWS account with admin access (or sufficient IAM permissions)
- AWS CLI v2 installed and configured locally (`aws configure`)
- Maven 3.9+ and JDK 17 installed locally (for the first manual deployment)
- A GitHub account

---

## 1. Create the S3 Buckets

You need **two separate S3 buckets**:

| Bucket | Purpose |
|--------|---------|
| `eb-demo-deploy-<your-initials>` | Stores CI/CD deployment ZIP bundles |
| `eb-demo-data-<your-initials>` | The external data bucket your app reads from |

```bash
# Replace us-east-1 with your preferred region
aws s3 mb s3://eb-demo-deploy-YOUR_INITIALS --region us-east-1
aws s3 mb s3://eb-demo-data-YOUR_INITIALS   --region us-east-1

# Upload a sample file so the /s3 endpoint returns something interesting
echo "Hello from S3!" > sample.txt
aws s3 cp sample.txt s3://eb-demo-data-YOUR_INITIALS/
```

---

## 2. Create IAM Roles

### 2a. EC2 Instance Profile (used by Beanstalk instances)

1. Go to **IAM → Roles → Create role**
2. Trusted entity: **AWS service → EC2**
3. Attach these managed policies:
   - `AWSElasticBeanstalkWebTier`
   - `AWSElasticBeanstalkWorkerTier`
   - `AWSElasticBeanstalkMulticontainerDocker`
4. Add an **inline policy** to allow S3 reads on your data bucket:

```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Action": ["s3:ListBucket", "s3:GetObject"],
      "Resource": [
        "arn:aws:s3:::eb-demo-data-YOUR_INITIALS",
        "arn:aws:s3:::eb-demo-data-YOUR_INITIALS/*"
      ]
    }
  ]
}
```

5. Name the role: `eb-demo-ec2-role`

### 2b. GitHub Actions Deployment Role (OIDC)

This role lets GitHub Actions deploy without storing long-lived AWS keys.

1. Go to **IAM → Identity providers → Add provider**
   - Provider type: **OpenID Connect**
   - Provider URL: `https://token.actions.githubusercontent.com`
   - Audience: `sts.amazonaws.com`

2. **IAM → Roles → Create role**
   - Trusted entity: **Web identity**
   - Identity provider: `token.actions.githubusercontent.com`
   - Audience: `sts.amazonaws.com`
   - Add condition: `token.actions.githubusercontent.com:sub` = `repo:YOUR_GITHUB_USERNAME/YOUR_REPO_NAME:ref:refs/heads/main`

3. Attach an inline policy:

```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Action": [
        "s3:PutObject",
        "s3:GetObject",
        "s3:ListBucket"
      ],
      "Resource": [
        "arn:aws:s3:::eb-demo-deploy-YOUR_INITIALS",
        "arn:aws:s3:::eb-demo-deploy-YOUR_INITIALS/*"
      ]
    },
    {
      "Effect": "Allow",
      "Action": [
        "elasticbeanstalk:CreateApplicationVersion",
        "elasticbeanstalk:UpdateEnvironment",
        "elasticbeanstalk:DescribeEnvironments",
        "elasticbeanstalk:DescribeApplicationVersions"
      ],
      "Resource": "*"
    },
    {
      "Effect": "Allow",
      "Action": ["cloudformation:*"],
      "Resource": "arn:aws:cloudformation:*:*:stack/awseb-*"
    }
  ]
}
```

4. Name the role: `github-actions-eb-deploy`
5. Copy the role ARN – you'll need it in Step 5.

---

## 3. Create the Elastic Beanstalk Application and Environment

### Via AWS Console

1. Open **Elastic Beanstalk → Create application**
2. Application name: `eb-demo-app`
3. Click **Create environment**
4. Environment tier: **Web server environment**
5. Platform: **Java** → **Corretto 17** (or the latest Java 17 option)
6. Application code: **Upload your code**
   - First, build locally:
     ```bash
     mvn package -DskipTests
     zip eb-demo-app-v0.zip target/eb-demo-app.jar Procfile
     zip -r eb-demo-app-v0.zip .ebextensions/
     ```
   - Upload `eb-demo-app-v0.zip`
7. Service role: create new (or use `aws-elasticbeanstalk-service-role`)
8. EC2 instance profile: `eb-demo-ec2-role` (created in Step 2a)
9. Under **Configure more options → Software**, add environment properties:
   - `S3_BUCKET_NAME` = `eb-demo-data-YOUR_INITIALS`
   - `AWS_REGION` = `us-east-1`
10. Click **Create environment** and wait ~5 minutes.

### Verify

Visit the environment URL (e.g., `http://eb-demo-app.eba-xxxx.us-east-1.elasticbeanstalk.com`):
- `GET /` → JSON with version and status
- `GET /health` → `{"status":"healthy"}`
- `GET /s3` → lists objects from your data bucket

---

## 4. Push Code to GitHub

```bash
git init
git add .
git commit -m "Initial commit: Spring Boot EB app with S3 integration"
git branch -M main
git remote add origin https://github.com/YOUR_USERNAME/YOUR_REPO.git
git push -u origin main
```

---

## 5. Configure GitHub Repository Secrets and Variables

Go to your repo → **Settings → Secrets and variables → Actions**.

### Secrets (encrypted)

| Name | Value |
|------|-------|
| `AWS_DEPLOY_ROLE_ARN` | ARN of `github-actions-eb-deploy` role |

### Variables (plain text)

| Name | Value |
|------|-------|
| `AWS_REGION` | `us-east-1` |
| `EB_APP_NAME` | `eb-demo-app` |
| `EB_ENV_NAME` | `eb-demo-app-env` (check the exact name in EB console) |
| `S3_DEPLOY_BUCKET` | `eb-demo-deploy-YOUR_INITIALS` |

---

## 6. Trigger a CI/CD Deployment

Make a visible code change to prove automated deployment:

```bash
# Edit AppController.java – change APP_VERSION from "1.0.0" to "1.1.0"
# or change the message string, then push:
git add .
git commit -m "feat: bump version to 1.1.0 for CI/CD demo"
git push
```

Watch the **Actions** tab – the workflow will build, package, upload, and deploy automatically. After ~5 minutes the EB URL will return the new version.

---

## 7. Live Review Checklist

| Step | What to show |
|------|-------------|
| 1 | Open `GET /` in browser → confirm version and "status: UP" |
| 2 | Open `GET /health` → confirm `{"status":"healthy"}` |
| 3 | Open `GET /s3` → confirm bucket name and object list |
| 4 | Push a code change, show GitHub Actions run completing |
| 5 | Refresh `GET /` → confirm new version string appears |
| 6 | Show EB console → Application versions list with multiple versions |

---

## Project Structure

```
eb-demo-app/
├── .ebextensions/
│   ├── health.config        # Health check path + enhanced reporting
│   └── jvm.config           # Environment variable placeholders
├── .github/
│   └── workflows/
│       └── deploy.yml       # CI/CD pipeline
├── src/
│   └── main/
│       ├── java/com/example/app/
│       │   ├── Application.java
│       │   ├── controller/AppController.java   # REST endpoints
│       │   └── service/S3Service.java          # S3 integration
│       └── resources/
│           └── application.properties
├── Procfile                 # Tells Beanstalk how to start the app
└── pom.xml                  # Maven build + dependencies
```
