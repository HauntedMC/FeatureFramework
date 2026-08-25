# Release Process

This script releases FeatureFramework only. Consumer repositories update their own dependency properties and release
versions separately.

The reactor publishes `featureframework-theme-api` with the other framework artifacts. Publish FeatureFramework before
any separately versioned theme adapter that targets the new API.

For the 1.7.0 observability boundary, HauntedPlatform 1.3.0, DataProvider 3.3.0, and DataRegistry 1.15.0 must already be
published. Publish FeatureFramework 1.7.0 before HauntedObservability 1.0.0. After HauntedObservability is published,
align the ecosystem through HauntedPlatform 1.4.0 before ServerFeatures and ProxyFeatures adopt the observability runtime.
FeatureFramework remains vendor-neutral and does not depend on HauntedObservability.

## 1. Prepare

- Work from a clean, reviewed branch.
- Ensure Docker is available: the verification gate boots the packaged Paper and Velocity artifacts.

## 2. Verify, Commit, and Tag

Run from FeatureFramework:

```bash
./update_version.sh major
./update_version.sh minor
./update_version.sh patch
```

Choose one command. The script updates the FeatureFramework reactor `revision` and reproducible-build timestamp,
installs and verifies the full reactor with the `platform-acceptance` profile, then creates a local release commit and
annotated `vX.Y.Z` tag. It makes no remote changes.

If any version update or verification fails, the script exits before committing or tagging. Resolve the issue, restore
the clean worktrees, and rerun it.

## 3. Push and Publish

Push the FeatureFramework release:

```bash
git push origin HEAD && git push origin vX.Y.Z
```

Substitute the created version. Pushing the tag starts FeatureFramework's GitHub release workflow.
