# GitHub Organization Setup Guide

## Overview
This guide will help you set up a GitHub organization with proper role-based permissions for Dev Leads and Junior Developers.

## Role Structure
- **GitHub Admin (Organization Owner)**: Full control over organization
- **Dev Leads**: Can review, approve, and merge code
- **Junior Devs**: Can create feature branches, write code, and create pull requests

---

## Step 1: Organization Setup

### 1.1 Create Organization (if not done)
1. Go to GitHub.com
2. Click your profile picture → **Your organizations**
3. Click **New organization**
4. Choose plan and complete setup

### 1.2 Set Base Permissions
1. Go to your organization page
2. Click **Settings** tab
3. Click **Member privileges** (left sidebar)
4. Set **Base permissions** to **Write**
   - This allows all members to push code but not merge to protected branches

---

## Step 2: Create Teams

### 2.1 Create Dev Leads Team
1. In organization settings → **Teams** (left sidebar)
2. Click **New team**
3. Team name: `dev-leads`
4. Description: `Senior developers with merge permissions`
5. Visibility: **Visible** (team members can see who's in the team)
6. Click **Create team**

### 2.2 Create Junior Devs Team
1. Click **New team** again
2. Team name: `junior-devs`
3. Description: `Junior developers with code contribution permissions`
4. Visibility: **Visible**
5. Click **Create team**

### 2.3 Add Members to Teams
1. Click on each team name
2. Click **Members** tab
3. Click **Add a member**
4. Search and add users to appropriate teams

---

## Step 3: Repository-Level Permissions

### 3.1 Add Teams to Repository
For each repository in your organization:

1. Go to the **specific repository**
2. Click **Settings** tab
3. Click **Manage access** (left sidebar)
4. Click **Add teams**

**For Dev Leads Team:**
- Select `dev-leads` team
- Choose **Maintain** role
- Click **Add dev-leads to [repo-name]**

**For Junior Devs Team:**
- Select `junior-devs` team  
- Choose **Write** role
- Click **Add junior-devs to [repo-name]**

---

## Step 4: Branch Protection Rules

### 4.1 Protect Main Branch
For each repository:

1. Go to repository **Settings**
2. Click **Branches** (left sidebar)
3. Click **Add rule** or **Add branch protection rule**

### 4.2 Configure Protection Settings
**Branch name pattern:** `main` (or `master`)

**Enable these settings:**
- ✅ **Require a pull request before merging**
  - ✅ **Require approvals** (set to 1 or 2)
  - ✅ **Dismiss stale PR approvals when new commits are pushed**
  - ✅ **Require review from code owners** (optional)

- ✅ **Restrict pushes that create files**
  - Add `dev-leads` team to allowed pushers

- ✅ **Do not allow bypassing the above settings**

Click **Create** to save the rule.

---

## Step 5: Optional Enhancements

### 5.1 Create CODEOWNERS File
Create `.github/CODEOWNERS` in your repository root:

```
# Global code owners (dev leads review everything)
* @your-org/dev-leads

# Specific paths (examples)
# /frontend/ @your-org/frontend-leads
# /backend/ @your-org/backend-leads
# *.md @your-org/docs-team
```

### 5.2 Enable Organization Security
1. Organization Settings → **Security**
2. Enable **Two-factor authentication requirement**
3. Set up **Dependency insights**
4. Configure **Security advisories**

### 5.3 Repository Templates
Create a template repository with:
- Pre-configured branch protection rules
- Standard `.gitignore`
- README template
- CODEOWNERS file

---

## Step 6: Workflow Testing

### 6.1 Test Junior Dev Workflow
As a junior developer:
1. Clone repository
2. Create feature branch: `git checkout -b feature/new-feature`
3. Make changes and commit
4. Push branch: `git push origin feature/new-feature`
5. Create pull request on GitHub
6. ❌ Try to merge directly (should be blocked)

### 6.2 Test Dev Lead Workflow
As a dev lead:
1. Review the pull request
2. Add comments/request changes if needed
3. Approve the pull request
4. ✅ Merge the pull request (should succeed)

---

## Step 7: Repository Settings Checklist

For each new repository, ensure:

- [ ] Teams added with correct permissions
- [ ] Branch protection rules configured
- [ ] CODEOWNERS file created (if using)
- [ ] Required status checks enabled (CI/CD)
- [ ] Auto-delete head branches enabled
- [ ] Merge button options configured

---

## Quick Reference

### Permission Summary
| Role | Organization | Repository | Can Merge to Main |
|------|-------------|------------|-------------------|
| **GitHub Admin** | Owner | Admin | Yes (but shouldn't bypass PR process) |
| **Dev Leads** | Member | Maintain | Yes (after PR review) |
| **Junior Devs** | Member | Write | No (blocked by branch protection) |

### Common Commands
```bash
# Junior dev workflow
git checkout -b feature/my-feature
git add .
git commit -m "Add new feature"
git push origin feature/my-feature
# Then create PR on GitHub

# Dev lead workflow  
git checkout main
git pull origin main
# Review PR on GitHub, then merge via web interface
```

---

## Troubleshooting

### Issue: Junior dev can merge directly
**Solution:** Check branch protection rules are enabled and properly configured

### Issue: Dev lead can't merge PR
**Solution:** Ensure dev-leads team has Maintain permissions on the repository

### Issue: Team not showing in repository access
**Solution:** Make sure team has been added to the specific repository, not just the organization

### Issue: PR requires more approvals than available
**Solution:** Adjust required reviewers count in branch protection settings

---

## Best Practices

1. **Always use pull requests** - Even admins should follow the process
2. **Enable 2FA** for all organization members
3. **Regular access review** - Remove inactive members
4. **Use meaningful branch names** - `feature/`, `bugfix/`, `hotfix/` prefixes
5. **Write clear PR descriptions** - Help reviewers understand changes
6. **Keep branches up to date** - Regularly merge main into feature branches

---

## Next Steps

After completing this setup:
1. Train your team on the new workflow
2. Set up CI/CD pipelines with required status checks
3. Configure automated code quality tools
4. Create organization-wide coding standards
5. Set up regular access audits

---

*This guide covers the essential setup for role-based GitHub organization management. Adjust settings based on your team's specific needs.*