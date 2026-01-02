# Version Update Guide

This guide explains how to update the Kodama version for new releases.

## Quick Guide

### 1. Update Version in One File

**`gradle.properties`** (Root directory - single source of truth)
```properties
version=0.4.0  # Change this
```

That's it! The `kodama-compiler-plugin` automatically reads this version from the parent `gradle.properties`.

### 2. Update Documentation

Update the version in these documentation files:

- `README.md` (multiple occurrences)
- `doc/getting-started.md`
- `doc/README.md`
- `doc/code-generation.md`
- `doc/publishing.md`

Search and replace all instances of the old version with the new version:

```bash
# Find all version references in docs
grep -r "version \"0.2.0\"" doc/ README.md

# Or use sed to replace (review changes first!)
find . -name "*.md" -type f -exec sed -i '' 's/0\.2\.0/0.4.0/g' {} \;
```

### 3. Test the Build

```bash
# Clean build to verify everything compiles
./gradlew clean build

# Test publishing locally (publishes both core and compiler plugin)
./gradlew publishAllToMavenLocal

# Verify the version
ls ~/.m2/repository/com/obabichev/kodama/kodama-core/
ls ~/.m2/repository/com/obabichev/kodama/kodama-compiler-plugin/
# Both should show your new version directory
```

## Detailed Release Process

### For Regular Releases (e.g., 0.2.0 → 0.4.0)

1. **Update Version File**
   ```bash
   # Edit only this file:
   vim gradle.properties
   ```

2. **Update Documentation**
   ```bash
   # Update all markdown files with new version
   # Files to update:
   # - README.md
   # - doc/getting-started.md
   # - doc/README.md
   # - doc/code-generation.md
   # - doc/publishing.md
   ```

3. **Update ROADMAP.md** (if needed)
   - Move completed features from "In Progress" to completed sections
   - Update version references

4. **Test Build**
   ```bash
   ./gradlew clean build test
   ./gradlew publishAllToMavenLocal
   ```

5. **Commit Changes**
   ```bash
   git add .
   git commit -m "Release version 0.4.0"
   git tag v0.4.0
   ```

6. **Publish** (see `doc/publishing.md` for details)
   ```bash
   # For Maven Central
   ./gradlew publish

   # Or for local testing
   ./gradlew publishToMavenLocal
   ```

7. **Push to Repository**
   ```bash
   git push origin main
   git push origin v0.4.0
   ```

### For Snapshot Releases (e.g., 0.4.0-SNAPSHOT)

For development versions between releases:

1. **Update Version to SNAPSHOT**
   ```properties
   # In gradle.properties only
   version=0.4.0-SNAPSHOT
   ```

2. **Build and Publish**
   ```bash
   ./gradlew clean build publishAllToMavenLocal
   # Or publish to OSSRH snapshots repository
   ./gradlew publishAll
   ```

> **Note:** SNAPSHOT versions are automatically published to the snapshots repository and don't require manual release on Sonatype.

## Version Numbering (Semantic Versioning)

Kodama follows [Semantic Versioning](https://semver.org/):

```
MAJOR.MINOR.PATCH

Example: 0.2.0
         │ │ │
         │ │ └─── PATCH: Bug fixes, backward compatible
         │ └───── MINOR: New features, backward compatible
         └─────── MAJOR: Breaking changes
```

### When to Increment Each Number

**MAJOR** (Breaking changes)
- Remove public APIs
- Change existing API behavior
- Rename core classes/functions
- Change table/query DSL syntax
- Example: 0.2.0 → 1.0.0

**MINOR** (New features, backward compatible)
- Add new query operators (AND, OR, LIKE)
- Add new aggregate functions
- Add UPDATE/DELETE support
- Add new JOIN types
- Example: 0.2.0 → 0.4.0

**PATCH** (Bug fixes, backward compatible)
- Fix query generation bugs
- Fix type inference issues
- Documentation updates
- Performance improvements
- Example: 0.2.0 → 0.2.1

## Version Management Architecture

### Single Source of Truth: Root `gradle.properties`

Kodama uses **only one** `gradle.properties` file for version management:

```
kodama/
├── gradle.properties              ← Single source of truth
│   └── version=0.2.0             (used by all modules)
└── kodama-compiler-plugin/
    └── build.gradle.kts          ← Reads ../gradle.properties
```

Even though `kodama-compiler-plugin` is an **included build** (composite build), it reads the parent's `gradle.properties` file directly:

```kotlin
// In kodama-compiler-plugin/build.gradle.kts
val parentProperties = Properties().apply {
    file("../gradle.properties").inputStream().use { load(it) }
}
group = parentProperties.getProperty("group")
version = parentProperties.getProperty("version")
```

This ensures:
1. **Single source of truth** - Only one file to update
2. **No version drift** - Impossible for versions to get out of sync
3. **Simpler maintenance** - Update one file, done!

### Optional: Automation Script

You could create a script to automate version updates across all files:

```bash
#!/bin/bash
# update-version.sh

NEW_VERSION=$1

if [ -z "$NEW_VERSION" ]; then
    echo "Usage: ./update-version.sh <new-version>"
    echo "Example: ./update-version.sh 0.4.0"
    exit 1
fi

# Update gradle.properties (single source of truth)
sed -i '' "s/^version=.*/version=$NEW_VERSION/" gradle.properties

# Update documentation
find . -name "*.md" -type f -exec sed -i '' "s/version \"[0-9.]*\"/version \"$NEW_VERSION\"/g" {} \;
find . -name "*.md" -type f -exec sed -i '' "s/:kodama-[a-z-]*:[0-9.]*\(-SNAPSHOT\)\?/:kodama-core:$NEW_VERSION/g" {} \;

echo "Updated version to $NEW_VERSION"
echo "Please review changes with: git diff"
```

This script:
1. Updates `gradle.properties` (which the compiler plugin automatically reads)
2. Updates all documentation files
3. Prompts you to review changes

## Verification Checklist

Before pushing a new version:

- [ ] `gradle.properties` updated (single source of truth)
- [ ] All documentation files updated (README.md, doc/*.md)
- [ ] ROADMAP.md updated (if applicable)
- [ ] `./gradlew clean build` succeeds
- [ ] `./gradlew test` passes
- [ ] `./gradlew publishAllToMavenLocal` works
- [ ] New version appears in both:
  - `~/.m2/repository/com/obabichev/kodama/kodama-core/`
  - `~/.m2/repository/com/obabichev/kodama/kodama-compiler-plugin/`
- [ ] Git tag created (e.g., `v0.4.0`)
- [ ] Changes committed and pushed

## Common Issues

### Issue: Version Mismatch Between Modules

**Symptom:** Different versions appear for `kodama-core` and `kodama-compiler-plugin`

**Solution:** This shouldn't happen anymore since there's only one version source, but if it does:
```bash
# Check the version
grep "^version=" gradle.properties

# Clean and rebuild
./gradlew clean build
```

### Issue: Old Version Still Appears After Update

**Symptom:** Maven Local still shows old version

**Solution:** Clean and republish:
```bash
rm -rf ~/.m2/repository/com/obabichev/kodama/
./gradlew clean publishAllToMavenLocal
```

### Issue: Documentation Still Shows Old Version

**Symptom:** README shows old version even after update

**Solution:** Search for all occurrences:
```bash
grep -r "0\.2\.0" . --include="*.md"
# Update each file manually
```

## Release Workflow Example

Here's a complete example of releasing version 0.4.0:

```bash
# 1. Create release branch
git checkout -b release/0.4.0

# 2. Update versions
echo "version=0.4.0" > gradle.properties
echo "version=0.4.0" > kodama-compiler-plugin/gradle.properties

# 3. Update documentation (manually or with sed)
# ... edit README.md, doc/*.md ...

# 4. Test
./gradlew clean build test
./gradlew publishAllToMavenLocal

# 5. Commit
git add .
git commit -m "Release 0.4.0

- Updated version to 0.4.0
- Updated all documentation
- See CHANGELOG.md for details"

# 6. Tag
git tag -a v0.4.0 -m "Release version 0.4.0"

# 7. Merge to main
git checkout main
git merge release/0.4.0

# 8. Publish
./gradlew clean build publishAll  # Publishes both core and compiler plugin to Maven Central

# 9. Push
git push origin main
git push origin v0.4.0

# 10. Start next development version
sed -i '' 's/^version=.*/version=0.4.0-SNAPSHOT/' gradle.properties
git commit -am "Start 0.4.0-SNAPSHOT development"
git push
```

## Related Documentation

- [Publishing Guide](doc/publishing.md) - Complete publishing instructions
- [ROADMAP.md](ROADMAP.md) - Feature roadmap and version planning
- [Semantic Versioning](https://semver.org/) - Versioning specification
