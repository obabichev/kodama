# Publishing Guide for Kodama

This guide explains how to publish Kodama to Maven repositories.

## Overview

Kodama consists of two publishable modules:
- **kodama-core** - The core library with query DSL and entity layer
- **kodama-compiler-plugin** - The Gradle plugin for code generation

**Important:** The compiler plugin is an **included build** (composite build), which means it's built separately from the main project. Use `publishAllToMavenLocal` or `publishAll` to publish both modules together.

## Publishing to Maven Local

For local testing, you can publish to your local Maven repository:

```bash
# Publish all modules (recommended)
./gradlew publishAllToMavenLocal

# Or publish individual modules
./gradlew :kodama-core:publishToMavenLocal

# Note: The compiler plugin is an included build, so it requires a special task
# It's automatically included in publishAllToMavenLocal
```

Published artifacts will be available at:
- `~/.m2/repository/com/obabichev/kodama/kodama-core/0.2.0/`
- `~/.m2/repository/com/obabichev/kodama/kodama-compiler-plugin/0.2.0/`

### Testing Locally Published Version

To test the locally published version in another project:

```kotlin
// In your test project's settings.gradle.kts
pluginManagement {
    repositories {
        mavenLocal()  // Add this first
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositories {
        mavenLocal()  // Add this first
        mavenCentral()
    }
}

// In your build.gradle.kts
plugins {
    id("com.obabichev.kodama") version "0.2.0"
}

dependencies {
    implementation("com.obabichev.kodama:kodama-core:0.2.0")
}
```

## Publishing to Maven Central

### Prerequisites

1. **Sonatype Account**: Create an account at https://s01.oss.sonatype.org/
2. **Group ID Verification**: Verify ownership of `com.obabichev.kodama` group ID
3. **GPG Key**: Generate a GPG key for signing artifacts
4. **Credentials**: Configure credentials in `~/.gradle/gradle.properties`

### Step 1: Set Up Credentials

Add to `~/.gradle/gradle.properties`:

```properties
# Sonatype OSSRH credentials
ossrhUsername=your-sonatype-username
ossrhPassword=your-sonatype-password

# GPG signing credentials
signing.keyId=your-gpg-key-id
signing.password=your-gpg-passphrase
signing.secretKeyRingFile=/path/to/.gnupg/secring.gpg
```

Alternatively, use environment variables:
```bash
export OSSRH_USERNAME=your-username
export OSSRH_PASSWORD=your-password
```

### Step 2: Enable Publishing Configuration

In both `kodama-core/build.gradle.kts` and `kodama-compiler-plugin/build.gradle.kts`, uncomment the OSSRH repository configuration:

```kotlin
publishing {
    repositories {
        maven {
            name = "OSSRH"
            val releasesRepoUrl = uri("https://s01.oss.sonatype.org/service/local/staging/deploy/maven2/")
            val snapshotsRepoUrl = uri("https://s01.oss.sonatype.org/content/repositories/snapshots/")
            url = if (version.toString().endsWith("SNAPSHOT")) snapshotsRepoUrl else releasesRepoUrl
            credentials {
                username = project.findProperty("ossrhUsername") as String? ?: System.getenv("OSSRH_USERNAME")
                password = project.findProperty("ossrhPassword") as String? ?: System.getenv("OSSRH_PASSWORD")
            }
        }
    }
}
```

### Step 3: Enable Signing

Uncomment the signing configuration in both build files:

```kotlin
// For kodama-core
signing {
    sign(publishing.publications["maven"])
}

// For kodama-compiler-plugin
signing {
    publishing.publications.withType<MavenPublication>().configureEach {
        sign(this)
    }
}
```

You'll also need to apply the signing plugin:

```kotlin
plugins {
    // ... existing plugins
    signing
}
```

### Step 4: Publish

```bash
# Clean build
./gradlew clean build

# Publish to OSSRH staging
./gradlew publishToOSSRH

# Or publish individual modules
./gradlew :kodama-core:publish
./gradlew :kodama-compiler-plugin:publish
```

### Step 5: Release on Sonatype

1. Log in to https://s01.oss.sonatype.org/
2. Navigate to "Staging Repositories"
3. Find your staging repository (com.obabichev.kodama-xxxx)
4. Click "Close" to validate the artifacts
5. Once validation passes, click "Release"
6. Artifacts will sync to Maven Central within ~10 minutes

## Publishing Workflow

> **Note:** For detailed instructions on updating versions, see the [Version Update Guide](../VERSION_UPDATE.md).

### For Releases

1. **Update Version**: Update version in `gradle.properties` (single source of truth) to release version (e.g., `0.3.0`)
2. **Update Documentation**: Update version references in README.md and doc/ files
3. **Commit Changes**: `git commit -am "Release 0.3.0"`
4. **Create Tag**: `git tag v0.3.0`
5. **Push**: `git push && git push --tags`
6. **Publish**: `./gradlew clean build publishAll`
7. **Release on Sonatype**: Follow Step 5 in the "Publishing to Maven Central" section above
8. **Update to Next Version**: Change version to next SNAPSHOT (e.g., `0.4.0-SNAPSHOT`)

### For Snapshots

For development versions, use SNAPSHOT suffix:

```kotlin
version = "0.3.0-SNAPSHOT"
```

Snapshots are automatically published to the snapshots repository and don't require manual release on Sonatype.

```bash
./gradlew clean build publish
```

## Verification

After publishing to Maven Central, verify the artifacts:

1. **Search Maven Central**: https://search.maven.org/search?q=g:com.obabichev.kodama
2. **Check Gradle Plugin Portal**: If submitted, check https://plugins.gradle.org/
3. **Test in New Project**: Create a fresh project and add the published version

## Troubleshooting

### Common Issues

**"401 Unauthorized"**
- Check your Sonatype credentials
- Ensure credentials are in `~/.gradle/gradle.properties` or environment variables

**"403 Forbidden"**
- Verify you have access to the `com.obabichev.kodama` group ID
- Contact Sonatype support to request access

**"gpg: signing failed"**
- Check GPG key is installed: `gpg --list-keys`
- Verify passphrase is correct
- Ensure secret key ring file path is correct

**"POM validation failed"**
- Ensure all required POM fields are filled (name, description, url, licenses, developers, scm)
- Check the POM files: `build/publications/maven/pom-default.xml`

**"Artifacts not syncing to Maven Central"**
- Wait at least 10 minutes after release
- Check https://repo1.maven.org/maven2/com/obabichev/kodama/
- Contact Sonatype if not syncing after 2 hours

### Getting Help

- Sonatype OSSRH Guide: https://central.sonatype.org/publish/publish-guide/
- GPG Guide: https://central.sonatype.org/publish/requirements/gpg/
- Maven Publish Plugin: https://docs.gradle.org/current/userguide/publishing_maven.html

## Module Structure

### kodama-core

- **Group**: `com.obabichev.kodama`
- **Artifact**: `kodama-core`
- **Description**: Core library with query DSL and entity layer
- **Dependencies**: kotlin-stdlib, kotlin-reflect, kotlinx-coroutines, slf4j, postgresql

### kodama-compiler-plugin

- **Group**: `com.obabichev.kodama`
- **Plugin ID**: `com.obabichev.kodama`
- **Description**: Gradle plugin for code generation
- **Type**: Gradle plugin (creates both plugin marker and main artifacts)

## CI/CD

For automated publishing with GitHub Actions or other CI:

```yaml
# .github/workflows/publish.yml (example)
name: Publish to Maven Central

on:
  push:
    tags:
      - 'v*'

jobs:
  publish:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3
      - uses: actions/setup-java@v3
        with:
          java-version: '17'
          distribution: 'temurin'

      - name: Publish
        env:
          OSSRH_USERNAME: ${{ secrets.OSSRH_USERNAME }}
          OSSRH_PASSWORD: ${{ secrets.OSSRH_PASSWORD }}
          GPG_PRIVATE_KEY: ${{ secrets.GPG_PRIVATE_KEY }}
          GPG_PASSPHRASE: ${{ secrets.GPG_PASSPHRASE }}
        run: |
          # Import GPG key
          echo "$GPG_PRIVATE_KEY" | gpg --batch --import

          # Publish
          ./gradlew publishToOSSRH --no-daemon
```

Store secrets in GitHub repository settings.

## Versioning

Kodama follows [Semantic Versioning](https://semver.org/):

- **MAJOR**: Breaking changes
- **MINOR**: New features, backward compatible
- **PATCH**: Bug fixes, backward compatible

Current version: **0.2.0** (Alpha)

## License

Ensure all published artifacts include the Apache License 2.0 (configured in POM).
