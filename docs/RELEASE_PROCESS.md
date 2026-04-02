# Task Engine Release Process

## Versioning Strategy

Task Engine follows [Semantic Versioning 2.0.0](https://semver.org/):

- **MAJOR** version when you make incompatible API changes
- **MINOR** version when you add functionality in a backwards compatible manner
- **PATCH** version when you make backwards compatible bug fixes

## Release Checklist

### Pre-Release Preparation

1. **Update CHANGELOG.md**
   - Add new section for the release version
   - Document all changes, features, fixes, and breaking changes
   - Follow the existing format consistently

2. **Verify Documentation**
   - Ensure all new features are documented
   - Update API documentation if endpoints changed
   - Verify architecture diagrams are current
   - Check that examples work with the new version

3. **Run Comprehensive Tests**
   ```bash
   # Run all tests including stress tests
   mvn test -Dgroups="!slow,!stress"
   
   # Run stress tests separately
   mvn test -Dgroups="stress"
   
   # Run JMH benchmarks to verify performance
   mvn clean install -Pjmh
   java -jar target/benchmarks.jar
   ```

4. **Update Dependencies**
   - Check for security vulnerabilities in dependencies
   - Update Spring Boot version if needed
   - Ensure compatibility with Java 17+

5. **Verify Backward Compatibility**
   - Test with previous version's configuration
   - Ensure no breaking changes in public APIs
   - Validate migration path for existing users

### Release Execution

1. **Prepare Release Branch**
   ```bash
   git checkout main
   git pull origin main
   git checkout -b release/vX.Y.Z
   ```

2. **Update Version Numbers**
   - Update `pom.xml` version
   - Update version references in documentation
   - Update example project versions

3. **Final Verification**
   ```bash
   # Clean build
   mvn clean install
   
   # Verify examples work
   cd examples/task-engine-complete-example
   mvn clean install
   
   # Run integration tests
   mvn verify -Pintegration-test
   ```

4. **Create Release Commit**
   ```bash
   git add .
   git commit -m "Release vX.Y.Z"
   git tag -a vX.Y.Z -m "Release vX.Y.Z"
   ```

5. **Publish to Maven Central**
   ```bash
   mvn deploy -P release
   ```

6. **Merge and Cleanup**
   ```bash
   git checkout main
   git merge release/vX.Y.Z
   git push origin main
   git push origin vX.Y.Z
   ```

### Post-Release Tasks

1. **Update GitHub Release**
   - Create GitHub release from the tag
   - Copy changelog entries to release notes
   - Attach any relevant artifacts

2. **Update Documentation Site**
   - Deploy updated documentation
   - Update version selector if applicable
   - Announce release on relevant channels

3. **Prepare Next Development Version**
   - Update `pom.xml` to next SNAPSHOT version
   - Add new section to CHANGELOG.md for next version
   - Commit and push changes

## Emergency Hotfix Process

For critical bugs requiring immediate patch:

1. Create hotfix branch from the affected release tag
2. Apply minimal fix to resolve the issue
3. Update version to next patch number (e.g., 1.2.3 → 1.2.4)
4. Follow standard release process with expedited testing
5. Communicate hotfix availability to users

## Automation Scripts

### Version Bump Script
```bash
#!/bin/bash
# scripts/bump-version.sh
VERSION=$1
mvn versions:set -DnewVersion=$VERSION
mvn versions:commit
```

### Release Validation Script
```bash
#!/bin/bash
# scripts/validate-release.sh
echo "Running release validation..."
mvn clean install
mvn test -Dgroups="!slow"
./scripts/test-examples.sh
echo "Release validation completed successfully!"
```

## Quality Gates

A release must pass all of the following quality gates:

- ✅ All unit tests pass
- ✅ All integration tests pass  
- ✅ No critical or high severity security vulnerabilities
- ✅ Performance benchmarks meet targets (250K+ QPS for TaskEngine)
- ✅ Documentation is complete and accurate
- ✅ Examples are functional and up-to-date
- ✅ CHANGELOG is comprehensive and well-formatted
- ✅ Backward compatibility is maintained (unless MAJOR version)

## Communication

- Announce releases in appropriate channels (GitHub, mailing list, etc.)
- Provide clear upgrade instructions for breaking changes
- Include performance improvements and key features in announcements
- Maintain release notes accessibility for historical reference