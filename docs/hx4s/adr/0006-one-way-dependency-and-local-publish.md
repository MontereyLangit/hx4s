# ADR 0006: One-way dependency 3ch → hx4s, and local dependencies right after the split

- Status: Accepted (2026-11-16, Phase 0). The resolution method in CI is undecided (U-3)
- Updated (2026-11-16, Phase 3 review K10 and K13): the default local publish version gets `.dirty-<8-character content hash>` if the published sources have uncommitted changes or untracked files
  (previously only tracked files were checked and `.dirty` was appended, so the version stayed the same even when the content changed). The examples' npm dependencies are pinned with the lockfile,
  and only the hx4s-vite tgz is installed by `scripts/setup-example.mjs` with `npm install --no-save`
- Updated (2026-11-16, Phase 4 user migration and Codex's findings): publish records are split between sbt and npm, and the completed record `target/local/hx4s-publish.json` is written only when
  both have the same version, commit and working tree state. `--verify` checks the records against HEAD and the artifacts, and users pin with a lock file (the full commit SHA and the version) and
  a setup script ([local-dependency.md](../local-dependency.md), [reviews/phase4-feedback.md](../reviews/phase4-feedback.md))
- Related: [extraction-plan.md](../extraction-plan.md) Phases 3–5, [module-boundaries.md](../module-boundaries.md) chapter 2

## Context

After hx4s is split out of 3ch, 3ch becomes a user of hx4s. We need to decide how 3ch resolves hx4s while hx4s is not yet published.
We also need to keep 3ch's concerns from flowing back into hx4s.

## Decision

- Dependencies go one way, 3ch → hx4s. hx4s doesn't reference 3ch's code, names, file paths or wording. Features only 3ch needs stay in 3ch.
- For a while after the split, local dependencies are allowed.
  - sbt: run `sbt publishLocal` in hx4s, and 3ch resolves it from `~/.ivy2/local` with ordinary `libraryDependencies` / `addSbtPlugin`. The proposal is to avoid `-SNAPSHOT` and use explicit versions like `0.1.0-local.N` (to avoid accidents where overwriting the same version leaves stale caches).
  - npm: 3ch's `package.json` references it like `"hx4s-vite": "file:../hx4s/vite"`.
  - The Phase 3 implementation (`scripts/publish-local.mjs`): the default version is `0.1.0-local.g<commit>` (with `.dirty-<content hash>` if the published sources have uncommitted changes or untracked files), and it can be given explicitly with `--version`.
    npm doesn't reference `vite/` directly; it references `target/local/hx4s-vite.tgz`, built and created with `npm pack`, via `file:` (so it isn't a link to a working directory with its own `node_modules`).
    The published version is written to `target/local/hx4s-version.txt`. The build's default version (`0.1.0-SNAPSHOT`) is not used for local publishing.
  - Added in Phase 4:
    - Before publishing, install hx4s's npm dependencies from the lockfile with `npm ci` (so it can be published even without `node_modules` right after a clone).
    - Split the sbt and npm publish records into `hx4s-publish-sbt.json` / `hx4s-publish-npm.json`. Only when both have the same version, commit and working tree state and all artifacts are present,
      write the completed record `hx4s-publish.json` (the list of Maven artifacts and the SHA-256 of the tgz; no timestamps) and `hx4s-version.txt`. No completion marker remains after a partial publish or a failure.
    - Keep `hx4s-version.txt`, because `project/plugins.sbt`, which can't read JSON, reads the version from it. Its meaning changed to "written only on completion".
    - `--verify` checks the completed record against the current HEAD, working tree, `~/.ivy2/local` and the tgz hash. Users use it to decide whether republishing can be skipped.
    - Users keep a lock file (the full commit SHA and the version); a setup script checks hx4s's HEAD and cleanliness, then runs `--verify`, and publishes with `--version` if it fails.
      Steps and an example script are in [local-dependency.md](../local-dependency.md).
    - npm and sbt are started without a shell (only Windows' sbt.bat is passed to cmd.exe as a single pre-quoted line), so that spaces and `&` in paths don't break them.
    - Added in the final review: the working tree state includes the root `package.json` / `package-lock.json`. For Maven artifacts, not just the existence of `ivy.xml`,
      but the SHA-256 of `ivy.xml` and the actual publications are recorded and checked. The completed record is strictly checked for its entries, the set of artifacts, and agreement with the partial records.
- The resolution method in CI **that involves publishing** is decided in Phase 5.
- However, a verification path that is reproducible without publishing is a prerequisite for Phase 4.
  - Script the steps to run `publishLocal` and prepare the npm package (`npm pack`, etc.) from an hx4s checkout pinned to a commit.
  - Include in the completion criteria that 3ch passes its build and tests starting from **empty dependency caches** (empty coursier / ivy caches and `node_modules`).
  - Don't consider the migration complete if it succeeds only by relying on whatever happens to remain in the local `~/.ivy2/local` and `node_modules`.

### Comparison of resolution methods in CI

| Method | Pros | Cons |
| --- | --- | --- |
| sbt source dependency (`RootProject(uri("https://…#<commit>"))`) | No publishing needed. Can be pinned to a commit | Can't be used for the sbt plugin (`sbt-hx4s`) (a separate `dependsOn` inside `project/` is needed). npm needs another method. The handling of sbt's cache (`~/.sbt/1.0/staging`) is hard to follow |
| git submodule + `publishLocal` in CI and npm `file:` | The sbt plugin, the libraries and npm are handled with the same steps. Can be pinned to a commit | CI builds hx4s every time. Easy to forget to update the submodule |
| Publish to Maven Central / npm | The users' settings are the most ordinary | Requires publishing procedures (signing, namespace verification). Published versions pile up during the alpha |
| GitHub Packages (Maven / npm) | Can limit who can access it | Reading requires authentication too, adding settings for CI and developers |

Proposal: submodule + `publishLocal` until publishing, Maven Central / npm after.

## Rejected alternatives

| Alternative | Reason |
| --- | --- |
| Call 3ch's tests from hx4s to catch regressions | The dependency would point the wrong way. 3ch-specific regressions are caught by 3ch's CI. hx4s's regressions are caught by `examples/minimal` and the compatibility tests |
| Depend only on published versions from the start | Right after the split, hx4s and 3ch are often fixed at the same time. Round trips through publishing would slow development |
| Local publish with `-SNAPSHOT` | Overwriting the same version can make the dependency resolution cache return stale artifacts. Explicit versions are easier to track |

## Consequences

- From Phase 4, 3ch assumes an hx4s checkout next to it (`../hx4s`). The steps are written in 3ch's docs.
- While dependencies are local, 3ch's production deployment (`npm run deploy`) can only be done from a local environment. Deployment from CI comes in Phase 5 or later.
- The Phase 4 migration is complete only after the prerequisites above pass (steps from a commit-pinned checkout, a build from empty dependency caches) ([extraction-plan.md](../extraction-plan.md) Phase 4).
- From Phase 4 on, whether changes to hx4s break 3ch is checked on the 3ch side. hx4s's CI can't check it.
