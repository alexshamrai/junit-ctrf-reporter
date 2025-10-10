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

## Releasing

This section is for maintainers who have permission to release new versions:

1. Update version in build files
2. Update documentation
3. Create a new release tag
4. Deploy to Maven Central

## Automated Release to Maven Central

This project uses GitHub Actions to automatically publish releases to Maven Central. The release process is fully
automated and can be triggered either by pushing a version tag or manually through the GitHub Actions UI.

### Triggering a Release

There are two ways to trigger an automated release:

#### Option 1: Push a Version Tag (Recommended)

```bash
# Create and push a version tag
git tag v0.4.0
git push origin v0.4.0
```

The workflow will automatically:
1. Extract the version from the tag (e.g., `v0.4.0` → `0.4.0`)
2. Build the project
3. Sign the artifacts with your GPG key
4. Publish to Maven Central

#### Option 2: Manual Dispatch

1. Go to the **Actions** tab in your GitHub repository
2. Select the **"Publish to Maven Central"** workflow
3. Click **"Run workflow"**
4. Enter the version number (e.g., `0.4.0`)
5. Click **"Run workflow"**

### Post-Release Steps

After the workflow completes successfully:

1. **Verify the publication**: Check the workflow summary for the artifact URL
2. **Wait for synchronization**: It may take 15-30 minutes for the artifact to appear on Maven Central
3. **Update version numbers**: Update `projectVersion` and `releaseVersion` in `build.gradle` for the next development cycle
4. **Create a GitHub Release** (optional): Document the changes in a GitHub release

### Troubleshooting

**Workflow fails during signing:**
- Verify that `GPG_PRIVATE_KEY` includes the complete ASCII-armored key with BEGIN/END lines
- Ensure `GPG_PASSPHRASE` matches the passphrase used when creating the key
- Check that your public key was successfully uploaded to a key server

**Workflow fails during publication:**
- Verify your Maven Central credentials are correct
- Ensure you have claimed your namespace on Maven Central (e.g., `io.github.alexshamrai`)
- Check that the version number doesn't already exist

**Artifact doesn't appear on Maven Central:**
- Wait 15-30 minutes for synchronization
- Check [Maven Central Search](https://search.maven.org/) for your artifact
- Verify the workflow completed successfully without errors