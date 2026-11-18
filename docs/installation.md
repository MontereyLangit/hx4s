# Installation

**hx4s is not published to Maven Central or npm.** The only way to use it today is to clone the hx4s repository and publish it locally (`publishLocal` and `npm pack`).
For publishing plans, see the open questions in [release.md](release.md) and [hx4s/roadmap.md](hx4s/roadmap.md).

## Coordinates

| Artifact | Coordinates | Where to use |
| --- | --- | --- |
| sbt plugin | `"io.github.montereylangit" % "sbt-hx4s" % <version>` (sbt 1.x) | `project/plugins.sbt` |
| `hx4s-react` | `"io.github.montereylangit" %%% "hx4s-react" % hx4sVersion` (Scala.js) | `libraryDependencies` of server, client and shared. Brings in the JS version of `hx4s-core`, scalajs-react `core` 4.0.0 and scalajs-dom 2.8.1 |
| `hx4s-core` | `"io.github.montereylangit" %%% "hx4s-core" % hx4sVersion` (JVM and Scala.js) | `@route` / `@islands` and `JsShape`. `hx4s-react` depends on it, so you usually don't list it directly |
| `hx4s-codegen` | `io.github.montereylangit:hx4s-codegen_3` | `sbt-hx4s` resolves it in a dedicated configuration and runs it in a forked JVM. Don't depend on it directly |
| npm | `hx4s-vite` | The `hx4s()` Vite plugin, `hx4s-vite/client`, the Node loader |

- The organization `io.github.montereylangit` is provisional and may change when published ([hx4s/roadmap.md](hx4s/roadmap.md) U-2). The Scala package name (`hx4s.*`) will not change.
- `hx4sVersion` is provided by sbt-hx4s and equals the plugin's version. Using it for `hx4s-react` keeps the plugin and library versions aligned.
- The npm package name (`hx4s-vite` or a scoped name) is undecided (U-1).
- The peerDependencies of `hx4s-vite` are `honox` 0.1.62, `@scala-js/vite-plugin-scalajs` 1.1.0 and `vite` ^8.3.3. Node.js 22.12 or later is required.

## Local publishing

```bash
git clone <hx4s repository> hx4s && cd hx4s
node scripts/publish-local.mjs
```

It runs `npm ci` (from the lockfile) → sbt `publishLocal` → build and `npm pack` of `hx4s-vite`, in that order.
The artifacts go to `~/.ivy2/local` and `target/local/hx4s-vite.tgz`.

```bash
node scripts/publish-local.mjs                 # version is <version in version.txt>-local.g<commit> (with .dirty-<content hash> if there are uncommitted changes)
node scripts/publish-local.mjs --version 0.1.0-local.3
node scripts/publish-local.mjs --verify --version 0.1.0-local.3 --commit <40-character SHA>   # verifies the publish record; exit code 1 on mismatch
```

| Flag | Meaning |
| --- | --- |
| `--version <v>` | Sets the version explicitly |
| `--skip-sbt` / `--skip-npm` | Publishes only one side (partial publish) |
| `--reuse-node-modules` | Skips `npm ci` if `package-lock.json` is the same as when this script last ran `npm ci` (the default is to run `npm ci` every time) |
| `--verify` | Does not publish; checks the completed record against the current HEAD, working tree and artifacts. With `--version` / `--commit`, also checks the expected values |

Change the version for every commit (use the default `<version in version.txt>-local.g<commit>`, currently `0.1.0-local.g<commit>`). If the contents change under the same version, sbt's dependency resolution may return stale artifacts.
Don't use the build's default version `<version in version.txt>-SNAPSHOT` for local publishing.

The following records are written to `target/local/` (publish time is not included).

| File | Written when | Content |
| --- | --- | --- |
| `hx4s-publish-sbt.json` / `hx4s-publish-npm.json` | Each publish succeeds | Version, commit, working tree state (`dirty` / `dirtyHash`), and either the list of Maven artifacts (SHA-256 of `ivy.xml`, JARs, sources, javadoc and pom) or the tgz file name and SHA-256 |
| `hx4s-publish.json` | Only when the two above share the same version, commit and working tree state, and all artifacts are present | The completed record combining the two (`format: 1`) |
| `hx4s-version.txt` | Together with `hx4s-publish.json` | Only the version. For `project/plugins.sbt`, which can't read JSON |

Publishing deletes `hx4s-publish.json` and `hx4s-version.txt` first, so no stale completed record remains after a failure or a partial publish.
Locations other than `~/.ivy2/local` (environments with a different `sbt.ivy.home`) are not supported.

## Using it in your project

sbt references it with `addSbtPlugin(... % "<version>")`. The version is the value in `target/local/hx4s-version.txt` (`examples/minimal` reads the environment variable `HX4S_VERSION`, or this file if it is not set).

For npm, install the tgz with `--no-save`.

```bash
npm ci
npm install --no-save /path/to/hx4s/target/local/hx4s-vite.tgz
```

The tgz's integrity hash changes with every publish. If you list it in `package.json` as `file:`, the lockfile changes every time hx4s is republished, so install it with `--no-save` when you want the lockfile to stay pinned.
Anything installed with `--no-save` is removed by the next `npm ci` / `npm install`, so reinstall it afterwards.

## Pinning the version and commit

For reproducibility, keep a lock file in your project (the full SHA of the hx4s commit and the version) and have a setup script do the following:

1. Check that the hx4s checkout is at the locked commit and that the working tree is clean
2. If `publish-local.mjs --verify --version <lock> --commit <lock>` passes, skip publishing; otherwise publish with `--version <lock>` and run `--verify` again
3. After `npm ci` in your project, install the tgz with `--no-save`
The examples follow the same approach with `scripts/setup-example.mjs` (runs `npm ci` and installs the tgz after `--verify`).

The full script and an example of reading the locked version from `project/plugins.sbt` are in [hx4s/local-dependency.md](hx4s/local-dependency.md).

## Troubleshooting

| Symptom | Cause and fix |
| --- | --- |
| `hx4s is not published locally` | `project/plugins.sbt` of `examples/minimal` can't read the version. Run `node scripts/publish-local.mjs` in the hx4s repository, or set `HX4S_VERSION` |
| `the local publish does not match this checkout` | The hx4s sources changed after publishing. Run `publish-local.mjs` again |
| `hx4s-codegen could not be resolved` | `hx4s-codegen_3` for the `hx4sCodegenVersion` version can't be resolved. Check that the local publish version and the plugin version match |
| `hx4s-vite` not found after `npm ci` | What was installed with `--no-save` was removed. Reinstall the tgz |
| Starting npm / sbt breaks on Windows | The path or arguments contain spaces or `&`. The scripts start processes without a shell, but be careful in your own npm scripts |
