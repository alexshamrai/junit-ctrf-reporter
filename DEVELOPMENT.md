# Development Setup

This document provides instructions for setting up the JUnit CTRF Reporter project for local development.

## Prerequisites

- A JDK, 17 or later, to run Gradle. The build uses JDK 25 to compile and test, and JDKs 17 and 21 to test on older Java. Gradle uses them if they are installed, and downloads them otherwise (foojay toolchain resolver)
- The Gradle wrapper (`./gradlew`); no separate Gradle installation is needed
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

### Consumer Compatibility Check

`compat/run.sh` publishes the library to `build/compat-repo`, then builds small consumer projects against each JUnit version you pass: Maven and Gradle projects reporting through the listener, among them a JUnit 4 project that has no JUnit Jupiter on its classpath. Each consumer must run on exactly the JUnit version it asked for and produce the expected CTRF report:

```bash
compat/run.sh 5.10.5 5.14.4 6.1.3
```

It needs Maven and Python 3. CI runs it on every pull request (`.github/workflows/compat.yml`).

### Publishing to Local Maven Repository

To test local changes in another project, publish the SNAPSHOT version to your local Maven repository:

```bash
VERSION=$(sed -n "s/.*projectVersion = '\(.*\)'/\1/p" build.gradle)
./gradlew clean publishToMavenLocal -PpublishVersion=$VERSION
echo $VERSION
```

This publishes to `~/.m2/repository/io/github/alexshamrai/junit-ctrf-reporter/<version>/`. Without `-PpublishVersion`, the build publishes under `releaseVersion`, the last released version, so your local repository would hold a local build under a real release's number.

Then in your test project, add the printed version:

```gradle
repositories {
    mavenLocal()
    mavenCentral()
}

dependencies {
    testImplementation 'io.github.alexshamrai:junit-ctrf-reporter:<the printed version>'
}
```

**Note:** `mavenLocal()` must come before `mavenCentral()` to ensure the local SNAPSHOT is picked up first.

### Code Quality Checks

The project uses checkstyle for code quality. Run the checks with:

```bash
./gradlew checkstyleMain checkstyleTest
```

## Project Structure

- `src/main/java` - the library
- `src/test/java` - its unit and scenario tests
- `integration-tests-listener` - fake tests that produce a report through `CtrfListener`
- `integration-ctrf-validator` - checks those reports; see [INTEGRATION_TESTS.md](INTEGRATION_TESTS.md)
- `compat` - consumer projects for the compatibility check above
- `schema` - the pinned official CTRF schema, a test resource only
- `config/checkstyle` - the checkstyle rules and the IntelliJ IDEA code style

How the library is put together, package by package, is in [ARCHITECTURE.md](ARCHITECTURE.md).

## Automated Release Process
This project uses GitHub Actions workflows to manage releases to Maven Central. The process involves preparing a release branch, then running the publishing workflow on it.

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
*   Update `releaseVersion` and `projectVersion` (the next development version) in `build.gradle`.
*   Update the Gradle and Maven dependency versions in `README.md`. If either still has another version, the workflow fails before pushing anything.
*   Create a Pull Request (PR) from `release/X.Y.Z` to `master` for review.

### Step 2: Publish to Maven Central

Run it yourself: in the **Actions** tab, select **Publish to Maven Central**, click **Run workflow**, and choose the `release/X.Y.Z` branch. It doesn't start on its own: Step 1 pushes the release branch with the workflow's `GITHUB_TOKEN`, and GitHub doesn't let such a push start other workflows. The workflow only runs on `release/*` branches.

The `Publish to Maven Central` workflow will:
*   Extract the version from `build.gradle`.
*   Build and sign the artifact with GPG.
*   Publish the artifact to Maven Central.

### Step 3: Review and Merge

1.  Review the automatically created Pull Request.
2.  Once the artifact is on Maven Central, merge this PR into `master` to finalize the release.

### Step 4: Post-Release

1.  **Verify the publication**: Check the `Publish to Maven Central` workflow run for the artifact URL.
2.  **Wait for synchronization**: It may take 15-30 minutes for the artifact to appear on Maven Central.
3.  **Create a GitHub Release**: Document the changes in a GitHub release, creating the tag (e.g., `v0.5.0`) on `master`.

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