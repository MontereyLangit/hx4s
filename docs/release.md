# Release

**The procedure for publishing to Maven Central and npm has not been set up.** This document covers the versioning, changelog and verification done before a release, and what needs to be decided before publishing.
It does not cover the publishing operations themselves (signing, uploading, `npm publish`).

## Current state

| Item | State |
| --- | --- |
| Released versions | None. `0.1.0` is not published |
| Version definition | `version.txt` at the repository root (the next version to release; currently `0.1.0`) |
| Default build version | `<version.txt>-SNAPSHOT` (when the `hx4s.version` system property of `build.sbt` is omitted). The version in `vite/package.json` is `0.1.0`, the same as `version.txt` |
| Local publish version | `<version.txt>-local.g<commit>` (`scripts/publish-local.mjs`; [installation.md](installation.md)) |
| How users get it | Local publish only |

## Versioning policy

- Follow [Semantic Versioning](https://semver.org/). During `0.x`, a minor version may change the API, settings and the format of generated files
- sbt-hx4s, `hx4s-core`, `hx4s-react`, `hx4s-codegen` and the npm package `hx4s-vite` share **the same version**. `hx4sVersion` of sbt-hx4s keeps the plugin and library versions aligned
- Each release states the verified versions of HonoX, Hono, Vite, React, etc. (supported versions in the [README](../README.md)). The peerDependencies of `hx4s-vite` list only verified versions
- Published versions are never rewritten. Mistakes are fixed in the next version
- The version is defined in one place, `version.txt` (just `X.Y.Z`). Everything else derives from it

  | Where | Version |
  | --- | --- |
  | `build.sbt` (sbt default) | `<version.txt>-SNAPSHOT`. Can be overridden with `-Dhx4s.version=<version>` |
  | Default of `scripts/publish-local.mjs` | `<version.txt>-local.g<commit>` (`scripts/lib/version.mjs`) |
  | `version` in `vite/package.json` | Same as `version.txt`. npm doesn't use `-SNAPSHOT` (it passes as semver syntax, but npm has no notion of a "mutable development version"; it would just sort before `0.1.0`) |

- `vite/package.json` holds a version because of the npm workspace, so keep it in sync by hand. `tests/version.test.js` (`npm test`) detects mismatches
- `publish-local.mjs` overrides the version of the published artifacts (the default above if `--version` is omitted)

## Release steps

Steps up to fixing the version, until the publishing method is decided.

1. **Prepare the changelog**: turn `[Unreleased]` in [CHANGELOG.md](../CHANGELOG.md) into a section for the new version and add the date. Leave an empty `[Unreleased]` and update the comparison links
2. **Decide the version**: decide it per SemVer from the changes (breaking API changes, features, fixes) and write it to `version.txt`.
   Set `version` in `vite/package.json` to the same value, and update `package-lock.json` with `npm install --package-lock-only`
3. **Verify**: pass all of the following, in CI (Ubuntu and Windows) as well.

   ```bash
   npm ci
   npm run typecheck
   sbt test
   sbt sbtHx4s/scripted
   sbt 'set ThisBuild / scalacOptions += "-no-indent"' Test/compile
   npm test
   node scripts/publish-local.mjs --version <version> && node scripts/publish-local.mjs --verify --version <version> --commit <40-character SHA>
   node scripts/setup-example.mjs minimal && (cd examples/minimal && npm run build && npm run typecheck && npm test)
   node scripts/setup-example.mjs api-only && (cd examples/api-only && npm run build && npm run typecheck && npm test)
   ```

4. **Check the documentation**: versions, code examples and links in the README and `docs/` match reality. `node scripts/check-independence.mjs` passes
5. **Tag**: add an annotated tag `v<version>` to the verified commit
6. Publish (see "Not yet set up" below)

## Not yet set up and open questions

Publishing to Maven Central and npm requires deciding the following. Nothing is published until they are decided.

| Item | State | Reference |
| --- | --- | --- |
| Maven organization | Provisionally `io.github.montereylangit`. Change it if a custom domain is acquired. Changing it changes users' build definitions (the Scala package name `hx4s.*` stays) | [roadmap.md](hx4s/roadmap.md) U-2, [ADR 0001](hx4s/adr/0001-name-and-coordinates.md) |
| npm scope | `hx4s-vite` or `@hx4s/vite`. Check whether the scope is available | U-1 |
| Name collisions | Check whether `hx4s` is already used on Maven Central, npm or GitHub | U-20 |
| Artifact signing | Maven Central requires signing. Key management is undecided | — |
| Publishing from CI | Dependency resolution in CI that publishes, and how to automate publishing | U-3, [ADR 0006](hx4s/adr/0006-one-way-dependency-and-local-publish.md) |
| POM / package.json metadata | Developers, SCM, description and other fields (Maven Central requirements). The license is already set to Apache-2.0 | — |
| Confirming rights to the moved code | Consent from the rights holders of the original code | U-21 |
| Whether to publish at all | The decision to publish itself is open | [extraction-plan.md](hx4s/extraction-plan.md) Phase 5 |

Once these are decided, add the publishing steps (signing, uploading, automation from tags) to this document.
