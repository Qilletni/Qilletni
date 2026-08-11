## Qilletni Release Protocol

Qilletni consists of several repositories, so releasing in the correct order is critical.

Five repositories are onboarded as release producers: **Qilletni**, **QilletniToolchain**,
**QPMCLI**, **QilletniPackageUtility** and **QilletniDocgen**. Each declares its own
`.qilletni/release.yml` (schema/validation in `tools/release/src/release_config.ts`).
The central, reusable release-preparation logic lives in this repository
(`.github/workflows/reusable-*.yml`); every producer's own repository has a small local
"caller" workflow that invokes it.

The TypeScript release CLI referenced throughout this document (`tools/release/...`) is a
submodule pinned to a commit of [`Qilletni/ReleaseTooling`](https://github.com/Qilletni/ReleaseTooling).

### Version centralization

`Qilletni` publishes two artifacts, `dev.qilletni.impl:qilletni` (core) and
`dev.qilletni.api:qilletni-api`, from a single `qilletniVersion` declared once in the
root `gradle.properties`. They are always released together, at the same version, as one
release unit registered as `qilletni-core` in `release/components.yml`.

### Preparing a release (any onboarded producer)

1. Run that repository's `Release - Prepare` workflow (`workflow_dispatch`), choosing a
   `patch`/`minor`/`major` bump.
2. It calls `Qilletni/Qilletni/.github/workflows/reusable-release-prepare.yml@master`, which:
   - Requires `master` to be the ref being dispatched from and up to date with `origin`.
   - Computes the next version from the **latest stable `vX.Y.Z` tag** (not the current, already
     pre-bumped, `-SNAPSHOT` version file), so a repository sitting at `1.0.2-SNAPSHOT` correctly
     produces `1.0.2`, not `1.0.3`.
   - Requires a non-empty `## [Unreleased]` section in `CHANGELOG.md`, and (for a `major` bump)
     a `docs/migrations/X.Y.Z.md` migration document.
   - Runs `./gradlew clean test` and fails the workflow if it doesn't pass.
   - Updates the component's version file/key and promotes `Unreleased` to `## [X.Y.Z] - <date>`,
     leaving a fresh, empty `Unreleased` section behind.
   - Writes a `release/pending-release.json` marker recording the component, `from`/`to`
     versions and bump kind, committed on the release branch alongside the version/changelog
     changes. This marker is what lets the japicmp gate later know the bump kind without
     re-deriving it (and re-verified for provenance, see below, so it can't be spoofed by an
     unrelated PR).
   - Mints a GitHub App installation token (`QILLETNI_RELEASE_APP_ID` /
     `QILLETNI_RELEASE_APP_PRIVATE_KEY`) scoped to just this repository, and opens a signed,
     labelled (`release`) PR via `peter-evans/create-pull-request@v8` for human review.
3. **This PR never auto-merges**, same as a dependency-update PR; a maintainer reviews and
   merges it manually.

### Publishing a release (fully automatic after merge)

`release.yml` reacts to `master` pushes and to `vX.Y.Z` tag pushes, split into three jobs:

- **`tag-release`** (every push to `master`): inspects the just-pushed `qilletniVersion`.
  - If it is still a `-SNAPSHOT`, this is an ordinary commit; nothing is tagged.
  - Otherwise, it must be exactly the merge of a release-preparation PR: the
    `release/pending-release.json` marker is required and validated against the version, and
    the merge commit's originating PR is fetched from the GitHub API and checked
    (`check-merge-provenance`) so an unrelated PR can't forge a release by carrying a stray
    marker file. Once validated, it **idempotently** creates (or, if it already exists at this
    exact commit, no-ops on) the immutable `vX.Y.Z` tag - no maintainer ever has to push a tag
    by hand. A direct tag push remains supported only as a manual recovery path if this
    automation ever fails, and is likewise idempotent.
- **`publish-snapshot`** (every push to `master`, kept as its own job so a stable release
  merge is never mistaken for a snapshot): publishes to Sonatype's Maven Central snapshot
  repository whenever the version is still a `-SNAPSHOT`, unchanged from before.
- **`build-and-publish`** (triggered only by the `vX.Y.Z` tag push produced above):
  1. Validates the tag matches the (single) `qilletniVersion` and that root/API agree, and
     that the resolved dependency graph contains no SNAPSHOT/dynamic versions
     (`checkNoSnapshotDependencies`).
  2. Determines the previous published version from Maven Central's `maven-metadata.xml`.
  3. Determines the japicmp policy tier for this release, preferring the release
     marker's recorded bump kind over re-classifying the version jump, then runs the japicmp
     public-API compatibility gate for both `qilletni` and `qilletni-api`
     (`tools/release/src/japicmp_policy.ts`):
     - **patch**: rejects *any* additive or breaking public API change.
     - **minor**: rejects breaking changes; additive changes are allowed.
     - **major**: breaking changes are allowed only if `docs/migrations/X.Y.Z.md` exists.
  4. Generates a CycloneDX JSON SBOM for each artifact.
  5. Publishes to Maven Central inside the protected `production-release` GitHub Environment,
     then polls Maven Central until both artifacts are confirmed available.
  6. Deploys Javadocs, creates the GitHub Release with the extracted release notes and the
     two SBOMs attached as assets.
- **`dispatch`** (after `build-and-publish`): sends a single dependency-update dispatch -
  listing *both* the core and API artifacts - to every consumer repository registered for
  `qilletni-core` in `release/components.yml`, via a GitHub App installation token narrowly
  scoped to that single consumer repository.
- **`snapshot-followup`** (after `dispatch`): opens a follow-up PR bumping `qilletniVersion`
  to `X.Y.(Z+1)-SNAPSHOT`, so `master` immediately resumes snapshot publishing. Also opened
  via a GitHub App token and `peter-evans/create-pull-request@v8`, and never auto-merges.

### Caller contract for consumer repositories

Repositories consuming a Qilletni-produced component receive a `repository_dispatch`
event named `qilletni-dependency-release` with this exact JSON `client_payload` (see
`tools/release/README.md`):

```json
{
  "component": "qilletni-core",
  "version": "1.1.0",
  "commit": "<40-char sha>",
  "repository": "Qilletni/Qilletni",
  "artifacts": [
    {"coordinates": "dev.qilletni.impl:qilletni:1.1.0", "sha256": "<64-char hex>"},
    {"coordinates": "dev.qilletni.api:qilletni-api:1.1.0", "sha256": "<64-char hex>"}
  ]
}
```

A consumer's small local caller workflow (copy of
`tools/release/examples/dependency-update.yml`) just receives that event and forwards it,
with its own App credentials, to the central `reusable-dependency-update.yml`, which:

1. Declares which upstream components it accepts in its own `.qilletni/release.yml`
   `dependencies` list: each entry pins the exact allowed `group:artifact` coordinate set
   for one `upstream_component` (and its expected `repository`) to a single local
   `version_file`/`version_key` (multiple coordinates - e.g. core+API - may map to one key).
   A coordinate may be marked `resolved: false` (e.g. QPM, which only puts the API half of
   the core+API unit on its own classpath) to stay mandatory/hash-verified/version-coupled
   while being excluded from the resolved-dependency-graph check in step 4 below.
2. Validates the payload's `component`/`repository`/coordinate-set/version against that
   mapping - rejecting a spoofed producer, a missing/extra coordinate, a divergent
   per-artifact version, or a `-SNAPSHOT`/dynamic version - **before touching anything**.
3. Re-downloads (streamed) and hash-verifies every artifact against Maven Central.
4. Applies the update to *only* the one configured `version_file`/`version_key`
   (idempotent: a no-op if already at that version), refreshes Gradle dependency locks
   (sibling composite builds explicitly disabled), runs the consumer's full test suite
   plus its own `checkNoSnapshotDependencies` guard, and confirms the resolved dependency
   graph actually contains the requested version(s).
5. Opens a signed PR via a repository-scoped GitHub App token. **That PR never
   auto-merges**; a maintainer decides whether, and with what bump, to release the
   consumer afterwards.

### Authentication

Cross-repository dispatch, and every PR this automation opens (release-preparation,
dependency-update, snapshot follow-up), authenticate as a GitHub App (organization secrets
`QILLETNI_RELEASE_APP_ID` / `QILLETNI_RELEASE_APP_PRIVATE_KEY`) via
`actions/create-github-app-token@v3`. Each token is requested scoped to exactly one target
repository (`repositories: <single-repo-name>`), never a broad, org-wide token.

### Platform releases (Docker)

Preparing which Toolchain/QPM composition a platform release will pin is a two-stage,
fully reviewed process; Docker publication itself remains manual and unrelated to
individual component versions:

1. **Candidate staging** (`release/platform/candidates.yml`, automatic but PR-reviewed):
   right after QilletniToolchain or QPMCLI publishes, its own workflow sends this
   repository a `repository_dispatch` event named `qilletni-platform-component-release`
   (payload schema in `tools/release/src/release_event.ts`):

   ```json
   {
     "schema_version": 1,
     "component": "toolchain",
     "repository": "Qilletni/QilletniToolchain",
     "version": "1.0.2",
     "tag": "v1.0.2",
     "commit": "<40-char sha>",
     "asset": "qilletni-1.0.2.tar.gz",
     "sha256": "<64-char hex>",
     "embeds": {"core": "1.0.2", "api": "1.0.1", "pkgutil": "1.0.1", "docgen": "1.0.1"}
   }
   ```

   `platform-candidate-dispatch.yml` schema-validates the event, re-verifies its
   `tag`/`commit`/`asset`/`sha256` claims live against the GitHub API, updates *only*
   the named component's entry in `candidates.yml` (refusing a `repository` that
   disagrees with that component's already-recorded one), and opens a signed PR via a
   GitHub App token. **This PR never auto-merges.**
2. **Platform release preparation** (`platform-prepare.yml`, manual `workflow_dispatch`
   with a required `patch`/`minor`/`major` `bump` choice): computes the next platform
   version from the **latest existing `release/platform/X.Y.Z.yml` file** (never from an
   individual component's version - a platform bump is its own, user-visible
   distribution decision), refuses to run if that manifest already exists, re-verifies
   `candidates.yml` live one more time, then copies it unchanged into the new manifest
   and opens its own signed, never-auto-merging PR.

Docker publication remains manual, on this repository, and unrelated to individual
component versions:

- **Snapshot**: fetches the latest `snapshot` commit SHAs from QilletniToolchain and
  QPMCLI, and resolves each one's release asset URL live off the same mutable `snapshot`
  release (the asset is named for the version that produced it - e.g.
  `qilletni-1.0.2-SNAPSHOT.tar.gz` - so it cannot be hardcoded), then tags the image
  `snapshot` plus the immutable `<toolchain_sha>-<qpm_sha>` commit-pair. Unlike a release
  build, a snapshot asset is not hash-pinned.
- **Release**: takes a single `platform_version` input (e.g. `1.0.0`), matching
  `release/platform/X.Y.Z.yml`. That manifest pins the exact Toolchain/QPM release asset
  name, SHA-256, tagged commit, and embedded component versions for the platform version;
  the workflow re-verifies the asset hashes and re-derives each component's commit from its
  live tag against the live GitHub API immediately before building (and cross-checks a
  published per-component manifest artifact instead, when one exists), and the `Dockerfile`
  itself re-verifies the asset hashes again at download time. The image is built and pushed
  to the immutable `X.Y.Z` tag first - labelled with the platform manifest's SHA-256, the
  triggering source commit, and the Toolchain/QPM/core versions and commits - refusing to
  overwrite an existing `X.Y.Z` tag whose labels disagree with this build's provenance (a
  matching re-run is treated as an idempotent no-op instead). Only once that immutable build
  has succeeded are the floating `X.Y`/`X`/`latest` tags re-pointed at the same image, with
  build provenance and an SBOM attached throughout.

See `release/components.yml` for the release-producer/consumer registry,
`release/platform/candidates.yml` for the mutable, PR-reviewed staging file, and
`release/platform/X.Y.Z.yml` for immutable platform manifests.
