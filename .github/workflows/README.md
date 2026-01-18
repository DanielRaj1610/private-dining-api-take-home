# GitHub Actions Workflows

This directory contains CI/CD pipelines for the Private Dining Reservation API.

## 📋 Workflows

### 1. CI Pipeline (`ci.yml`)

**Triggers:**
- Push to `main`, `develop`, or any `feature/**` branch
- Pull requests to `main` or `develop`

**Jobs:**

1. **Build and Test**
   - Sets up Java 17 and Maven
   - Starts MongoDB 7.0 container
   - Runs all 189 tests
   - Generates test reports
   - Uploads test results as artifacts

2. **Code Quality Check**
   - Runs Maven verify
   - Checks for compilation warnings

3. **Docker Build Test**
   - Builds Docker image
   - Verifies image creation

**What It Validates:**
- ✅ Code compiles successfully
- ✅ All tests pass (189 tests)
- ✅ Docker image builds correctly
- ✅ No compilation warnings

**View Results:**
- GitHub Actions tab → Select workflow run
- Download test reports from Artifacts section

---

### 2. Release Pipeline (`release.yml`)

**Triggers:**
- Push of version tags (e.g., `v1.0.0`, `v1.2.3`)

**Jobs:**

1. **Build Release Artifact**
   - Builds production JAR
   - Extracts version from tag
   - Generates changelog from commits
   - Creates GitHub Release
   - Uploads JAR artifact

2. **Docker Release**
   - Builds versioned Docker image
   - Saves image as tar.gz
   - Uploads to release assets

**What It Produces:**
- 📦 Executable JAR: `private-dining-api-{version}.jar`
- 🐳 Docker image: `private-dining-api-{version}-docker.tar.gz`
- 📝 Automated release notes with changelog

---

## 🚀 Usage Guide

### Creating a Release

1. **Ensure all tests pass on main:**
   ```bash
   git checkout main
   git pull origin main
   ```

2. **Create and push a version tag:**
   ```bash
   # For version 1.0.0
   git tag -a v1.0.0 -m "Release version 1.0.0"
   git push origin v1.0.0
   ```

3. **Monitor the release:**
   - Go to Actions tab on GitHub
   - Watch the "Release" workflow
   - Once complete, find the release under "Releases"

4. **Download artifacts:**
   - Go to Releases section
   - Download JAR or Docker image

### Semantic Versioning

Follow semantic versioning (MAJOR.MINOR.PATCH):

- **MAJOR** (v2.0.0): Breaking API changes
- **MINOR** (v1.1.0): New features, backward compatible
- **PATCH** (v1.0.1): Bug fixes, backward compatible

Examples:
```bash
# Major release (breaking changes)
git tag -a v2.0.0 -m "Release 2.0.0 - New capacity algorithm"

# Minor release (new features)
git tag -a v1.1.0 -m "Release 1.1.0 - Add waitlist feature"

# Patch release (bug fixes)
git tag -a v1.0.1 -m "Release 1.0.1 - Fix timezone handling"
```

### Running Locally Before Release

Before creating a tag, test locally:

```bash
# Run all tests
./mvnw clean test

# Build the JAR
./mvnw clean package -DskipTests

# Test the JAR
java -jar target/private-dining-api-*.jar

# Build Docker image
docker build -t private-dining-api:local .
```

---

## 🔧 Configuration

### Required Secrets

No secrets required for basic functionality. Releases work out of the box with `GITHUB_TOKEN`.

### Optional: Docker Hub Integration

To push images to Docker Hub, add these repository secrets:

1. Go to Settings → Secrets → Actions
2. Add secrets:
   - `DOCKERHUB_USERNAME`: Your Docker Hub username
   - `DOCKERHUB_TOKEN`: Docker Hub access token

3. Uncomment the Docker Hub section in `release.yml` (lines 100-112)

### Maven Version Property

The release pipeline uses `-Drevision` to set the version. Ensure your `pom.xml` supports this:

```xml
<version>${revision}</version>
<properties>
    <revision>0.0.1-SNAPSHOT</revision>
</properties>
```

---

## 📊 CI/CD Status Badge

Add this to your README.md to show build status:

```markdown
![CI](https://github.com/YOUR_USERNAME/REPO_NAME/workflows/CI/badge.svg)
```

---

## 🐛 Troubleshooting

### Tests Fail in CI But Pass Locally

**Issue:** MongoDB connection issues
**Solution:** Check `SPRING_DATA_MONGODB_URI` in `ci.yml`

### Release Workflow Fails

**Issue:** Tag already exists
**Solution:** Delete and recreate tag:
```bash
git tag -d v1.0.0
git push origin :refs/tags/v1.0.0
git tag -a v1.0.0 -m "Release 1.0.0"
git push origin v1.0.0
```

### Docker Build Fails

**Issue:** Maven build context missing
**Solution:** Ensure `.dockerignore` doesn't exclude required files

---

## 📈 Metrics

Current test coverage:
- **189 passing tests**
- **Unit tests**: Service, validation, utility layers
- **Integration tests**: Full API workflows
- **Concurrency tests**: Race condition verification

---

## 🔄 Future Enhancements

Potential additions:
- [ ] Code coverage reporting (JaCoCo)
- [ ] SonarQube integration
- [ ] Dependency vulnerability scanning
- [ ] Performance benchmarking
- [ ] Automatic changelog generation (conventional commits)
- [ ] Slack/Discord notifications
- [ ] Deploy to staging environment

---

## 📝 Notes

- Workflows use Java 17 (Temurin distribution)
- MongoDB 7.0 used for testing
- Maven wrapper (`./mvnw`) ensures consistent build environment
- Test artifacts retained for 30 days (GitHub default)
