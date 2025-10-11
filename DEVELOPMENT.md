# Development Setup

This document provides instructions for setting up the JUnit CTRF Extension project for local development.

## Prerequisites

- Java 21 or higher
- Gradle 8.13 or higher for build management
- Git for version control

## Local Project Setup

### Clone the Repository

```bash
git clone https://github.com/alexshamrai/junit-ctrf-reporter.git
cd junit-ctrf-reporter
```

### Build the Project

Using Gradle:
```bash
./gradlew build
```

### IDE Setup

#### IntelliJ IDEA
* Go to IDEA settings:
`Settings > Editor > Code style > Java > Scheme gear button > Import Scheme`
* Import the file located in this repo using format IntelliJ IDEA code style XML:
`config/checkstyle/intellij_idea_codestyle.xml`

### Running Tests

The solution uses JUnit 5 for unit testing. Run the tests with:
```bash
./gradlew :test
```
For integration tests info refer to [INTEGRATION_TESTS.md](INTEGRATION_TESTS.md)

### Code Quality Checks

The project uses checkstyle for code quality. Run the checks with:

```bash
./gradlew checkstyleMain checkstyleTest
```

## Project Structure

- `src/main/java` - Source code
- `src/test/java` - Test code
- `src/test/resources` - Test resources and configuration files

Automated Release Process
This project uses a two-step GitHub Actions workflow to publish releases to Maven Central:

Prepare Release - Creates a release branch with updated versions
Publish to Maven Central - Builds, signs, and publishes the artifact

Step 1: Prepare Release Branch

Go to the Actions tab in your GitHub repository
Select the "Prepare Release" workflow
Click "Run workflow"
Enter the version number to release (e.g., 0.4.0)
Optionally, enter the next development version (e.g., 0.5.0-SNAPSHOT)

If left empty, it will auto-increment the minor version


Click "Run workflow"

This will:

Create a release branch named release/X.Y.Z
Update releaseVersion in build.gradle
Update version numbers in README.md
Create a Pull Request for review

Step 2: Review and Merge

Review the automatically created Pull Request
Merge the PR into main when ready

Step 3: Publish to Maven Central

Checkout the release branch locally:

bash   git checkout release/0.4.0  # Replace with your version
Or manually trigger from GitHub:

Go to the Actions tab
Select the "Publish to Maven Central" workflow
Click "Run workflow"
Select the release branch from the dropdown (e.g., release/0.4.0)
Optionally, specify the next development version
Click "Run workflow"

This will:

Extract the version from build.gradle
Build and sign the artifact with GPG
Publish to Maven Central
Create a git tag (e.g., v0.4.0)
Create a PR to update projectVersion to the next development version

Step 4: Post-Release

Verify the publication: Check the workflow summary for the artifact URL
Wait for synchronization: It may take 15-30 minutes for the artifact to appear on Maven Central
Review and merge the version bump PR: This updates projectVersion for the next development cycle
Create a GitHub Release (optional): Document the changes in a GitHub release using the created tag

Prerequisites (First-Time Setup)
Before you can publish releases, you need to configure four GitHub Secrets:
Secret NameDescriptionMAVEN_CENTRAL_USERNAMEYour Maven Central user token usernameMAVEN_CENTRAL_PASSWORDYour Maven Central user token passwordGPG_PRIVATE_KEYYour GPG private key in ASCII-armored formatGPG_PASSPHRASEThe passphrase for your GPG key
For detailed setup instructions, see the GitHub Secrets Setup Guide.
Quick Setup for Existing GPG Key
If you already have a GPG key, export it for GitHub Actions:
bash# Export your private key (replace KEY_ID with your actual key ID)
gpg --armor --export-secret-keys YOUR_KEY_ID
Copy the entire output (including -----BEGIN and -----END lines) and add it as the GPG_PRIVATE_KEY secret.
Troubleshooting
Workflow fails during signing

Verify that GPG_PRIVATE_KEY includes the complete ASCII-armored key with BEGIN/END lines
Ensure GPG_PASSPHRASE matches the passphrase used when creating the key
Check that your public key was successfully uploaded to a key server:

bash  gpg --keyserver keyserver.ubuntu.com --send-keys YOUR_KEY_ID
Workflow fails during publication

Verify your Maven Central credentials are correct
Ensure you have claimed your namespace on Maven Central (e.g., io.github.alexshamrai)
Check that the version number doesn't already exist on Maven Central
Verify your user token is still valid at https://central.sonatype.com/

Artifact doesn't appear on Maven Central

Wait 15-30 minutes for synchronization
Check Maven Central Search for your artifact
Verify the workflow completed successfully without errors
Check the Maven Central repository directly:
https://repo1.maven.org/maven2/io/github/alexshamrai/junit-ctrf-reporter/

Version conflicts
If you need to republish a version:

Maven Central doesn't allow republishing the same version
You must increment the version number (e.g., from 0.4.0 to 0.4.1)
Delete the git tag locally and remotely if already created:

bash  git tag -d v0.4.0
git push origin :refs/tags/v0.4.0
Manual Release (Alternative)
If you prefer to release manually without GitHub Actions:

Update versions in build.gradle
Update versions in README.md
Build the project: ./gradlew build
Publish: ./gradlew publishAllPublicationsToMavenCentralRepository
Create and push a git tag: git tag v0.4.0 && git push origin v0.4.0

Note: Manual releases require local configuration of Maven Central credentials and GPG keys.