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

### Publishing to Local Maven Repository

To test local changes in another project, publish the SNAPSHOT version to your local Maven repository:

```bash
./gradlew clean publishToMavenLocal
```

This publishes to `~/.m2/repository/io/github/alexshamrai/junit-ctrf-reporter/<version>/`

Then in your test project, add:

```gradle
repositories {
    mavenLocal()
    mavenCentral()
}

dependencies {
    testImplementation 'io.github.alexshamrai:junit-ctrf-reporter:0.4.5-SNAPSHOT'
}
```

**Note:** `mavenLocal()` must come before `mavenCentral()` to ensure the local SNAPSHOT is picked up first.

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
This project uses GitHub Actions workflows to manage releases to Maven Central. The process involves preparing a release branch, which then automatically triggers the publishing workflow.

### Step 1: Prepare Release Branch

This step creates a new release branch, updates version numbers, and generates a Pull Request (PR) for review.

1.  Go to the **Actions** tab in your GitHub repository.
2.  Select the **Prepare Release** workflow from the list.
3.  Click **Run workflow**.
4.  Enter the `version` number to release (e.g., `0.4.0`).
5.  Optionally, enter the `nextVersion` for development (e.g., `0.5.0-SNAPSHOT`). If left empty, the minor version will be auto-incremented.
6.  Click **Run workflow**.

This will:
*   Create a release branch named `release/X.Y.Z`.
*   Update `releaseVersion` in `build.gradle`.
*   Update version numbers in `README.md`.
*   Create a Pull Request (PR) from `release/X.Y.Z` to `master` for review.

### Step 2: Publish to Maven Central

This step is **automatically triggered** when a Pull Request is opened from a `release/*` branch to `master`. The workflow will publish the artifact to Maven Central.

The `Publish to Maven Central` workflow will:
*   Extract the version from `build.gradle`.
*   Build and sign the artifact with GPG.
*   Publish the artifact to Maven Central.

### Step 3: Review and Merge

1.  Review the automatically created Pull Request.
2.  **Crucially, this PR needs to be merged into `master` to finalize the release.**

### Step 4: Post-Release

1.  **Verify the publication**: Check the `Publish to Maven Central` workflow run for the artifact URL.
2.  **Wait for synchronization**: It may take 15-30 minutes for the artifact to appear on Maven Central.
3.  **Create a GitHub Release (optional)**: Document the changes in a GitHub release using the created tag (e.g., `v0.4.0`).

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