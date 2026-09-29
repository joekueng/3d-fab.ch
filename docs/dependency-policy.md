# External dependency verification

Before adding or updating any external package, plugin, CLI, container or downloaded
binary, complete these checks. They apply equally to humans and AI agents.

1. **Identity and publication:** verify the exact name/namespace and version in
   npm, Maven Central, the Gradle Plugin Portal or the publisher's official release
   page. Follow the project's own installation documentation to the package; registry
   existence alone does not establish ownership or exclude typosquatting.
2. **Actual API:** consult documentation for the selected version and inspect its
   exported types, source or Javadoc. Verify every new import, method, option and CLI
   flag. Never invent a package to satisfy an imagined API. Prefer existing dependencies.
3. **Compatibility:** record engines, peer dependencies, Java/Gradle/framework
   requirements and migration notes. A registry `latest` tag is not a compatibility
   recommendation. Keep BOM-managed dependencies under the framework's management.
4. **Maintenance and advisories:** check deprecation, supported release lines,
   license, upstream security advisories and transitive dependencies. Distinguish
   build/test exposure from deployed runtime exposure; neither is automatically safe.
5. **Reproducibility:** commit npm-generated lockfile changes, review origins and
   integrity, and use exact Gradle/plugin versions. Do not hand-write hashes, add
   arbitrary repositories, run an unverified `npx` package, or bypass peer conflicts
   with `--force` / `--legacy-peer-deps` as an upgrade fix. Existing Docker exceptions
   must be removed as part of the migration after strict installation succeeds.
6. **Evidence:** include package + selected version, official documentation/release
   URLs, verification date, reason, compatibility findings and executed checks in
   the change description. If the registry/docs are unavailable, report **unverified**;
   do not claim success or introduce a guessed replacement.

## Executable checks

From the repository root, with the Node version in `frontend/.nvmrc` and network access:

```bash
node scripts/check-external-packages.mjs
```

This dependency-free script reads metadata without installing or executing packages.
It checks manifest/lockfile declarations, every direct locked npm version and its
tarball URL/integrity against npm, deprecation, literal versioned Maven POMs and
versioned Gradle plugin markers. Missing packages, mismatches and network failures
return a nonzero exit code. It prints Maven release metadata for upgrade review.
Gitea runs it on dependency-related pull requests and manual dispatch.

It does **not** parse arbitrary Gradle code, resolve BOM-managed versions or native
classifiers, check all npm transitives, validate downloaded bytes, establish publisher
trust, detect invented APIs or scan vulnerabilities. Review remains mandatory.
If dependency declaration syntax changes, update its coverage in the same change.

For frontend changes, use the repository's supported Node version and run:

```bash
cd frontend
npm view '<package>@<version>' name version repository engines peerDependencies deprecated dist --json
npm ci --ignore-scripts --strict-peer-deps
npm audit --package-lock-only --ignore-scripts
npm outdated
```

Verify new package identities before installation. The `--ignore-scripts` install
checks resolution first; after reviewing lifecycle scripts, run normal `npm ci`
and the frontend build, unit, SSR and relevant E2E checks. `npm outdated` returns
nonzero when updates exist; `npm audit` findings require triage, not `audit fix --force`.

For backend changes, after verifying plugin identities, run from `backend/`:

```bash
./gradlew dependencies --configuration runtimeClasspath
./gradlew dependencyInsight --dependency '<artifact>' --configuration runtimeClasspath
./gradlew compileJava test bootJar --warning-mode all
```

Inspect unresolved `FAILED` entries explicitly: a dependency report alone is not
proof of successful artifact resolution. Compilation/tests and a runtime smoke test
are required. The upgrade plan also calls for dependency locking, reviewed checksum
verification metadata and a JVM advisory scan; these are not yet enabled by this policy.

## Sources

- [npm view](https://docs.npmjs.com/cli/v11/commands/npm-view)
- [npm ci](https://docs.npmjs.com/cli/v11/commands/npm-ci)
- [npm audit](https://docs.npmjs.com/cli/v11/commands/npm-audit)
- [Gradle dependency verification](https://docs.gradle.org/current/userguide/dependency_verification.html)
- [Gradle dependency locking](https://docs.gradle.org/current/userguide/dependency_locking.html)
