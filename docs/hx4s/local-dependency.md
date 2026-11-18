# Pinning a locally published hx4s in a user project

Until hx4s is published to Maven Central / npm ([ADR 0006](adr/0006-one-way-dependency-and-local-publish.md)), user projects use what was published with `scripts/publish-local.mjs`
from an hx4s checkout placed next to them. This document describes how a user project pins the version and commit, and decides mechanically whether a republish is needed.

## 1. Publishing and records

| Command | Result |
| --- | --- |
| `node scripts/publish-local.mjs [--version V]` | `npm ci` (from the lockfile) → sbt `publishLocal` → build and `npm pack` of hx4s-vite. Writes the completed record when both are present |
| `--skip-sbt` / `--skip-npm` | Publishes only one side and rewrites only its record. The completed record isn't written unless the other side's record has the same version, commit and working tree state |
| `--reuse-node-modules` | Skips `npm ci` if the SHA-256 of `package-lock.json` is the same as when this script last ran `npm ci`. Runs `npm ci` right after a clone or when the lockfile changed |
| `--verify [--version V] [--commit SHA]` | Verifies without publishing. Exits 0 on a match; otherwise prints the reason to standard error and exits 1 |

An example of the completed record `target/local/hx4s-publish.json` (no timestamps, so publishing the same version from the same commit yields the same content):

```json
{
  "format": 1,
  "version": "0.1.0-local.gd140b21886",
  "commit": "d140b21886ae6f454bf625cdd1dcf549b3d79010",
  "dirty": false,
  "dirtyHash": null,
  "maven": [
    {
      "organization": "io.github.montereylangit",
      "name": "hx4s-codegen_3",
      "version": "0.1.0-local.gd140b21886",
      "path": "io.github.montereylangit/hx4s-codegen_3/0.1.0-local.gd140b21886",
      "files": [
        { "path": "ivys/ivy.xml", "sha256": "…" },
        { "path": "jars/hx4s-codegen_3.jar", "sha256": "…" },
        { "path": "srcs/hx4s-codegen_3-sources.jar", "sha256": "…" },
        { "path": "docs/hx4s-codegen_3-javadoc.jar", "sha256": "…" },
        { "path": "poms/hx4s-codegen_3.pom", "sha256": "…" }
      ]
    }
  ],
  "npm": { "name": "hx4s-vite", "file": "hx4s-vite.tgz", "sha256": "e1015d78…" }
}
```

(`maven` actually has 5 entries: `hx4s-codegen_3`, `hx4s-core_3`, `hx4s-core_sjs1_3`, `hx4s-react_sjs1_3` and `sbt-hx4s`. `path` is relative to `~/.ivy2/local`; for sbt-hx4s it is `…/sbt-hx4s/scala_2.12/sbt_1.0/<version>`.
`files` are `ivys/ivy.xml` and the actual files its `publications` point to (`[type]s/[artifact](-[classifier]).[ext]`). The record's shape and verification are in `scripts/lib/publish-record.mjs`, and the negative cases in `tests/publish-record.test.js`)

`--verify` checks the following.

1. `hx4s-publish.json` has exactly the entries of `format: 1` (missing entries, extra entries or an empty `maven` fail). The set of `maven` names equals the list of 5 (`mavenNames`),
   and the `npm` name and file name are `hx4s-vite` and `hx4s-vite.tgz`
2. The sbt and npm partial records have the same version, commit and working tree state as the completed record, and the same artifact information (`maven` / `npm`)
3. When `--version` / `--commit` are given, the record's version and commit equal them (`--commit` accepts only a 40-character SHA)
4. The record's commit equals the current HEAD, and uncommitted changes to the published sources (`version.txt`, `build.sbt`, `project`, `modules`, `sbt-plugin/src/main`, `vite`, the root `package.json` and `package-lock.json`, `LICENSE`, `NOTICE`; excluding tests) are the same as in the record
5. Each file in the record exists in `~/.ivy2/local` with the same SHA-256 as the record. The SHA-256 of `hx4s-vite.tgz` is also the same
6. `hx4s-version.txt` contains the record's version

Locations other than `~/.ivy2/local` (environments with a different `sbt.ivy.home`) are not supported.

## 2. Notes for user projects

- **A tgz installed with `--no-save` is removed by `npm ci`.** `npm ci` recreates `node_modules` and keeps no packages that aren't in the lockfile.
  Redo `npm install --no-save <tgz>` after every `npm ci`. The setup script below does both in this order.
- Listing the tgz in `package.json` as `file:` puts the tgz's integrity hash into the lockfile, and the lockfile changes every time hx4s is republished. Pin it with the lock file below instead, and keep it out of npm's lockfile.
- Change the version for every commit (use the default `0.1.0-local.g<10-character commit>`). If the contents change under the same version, sbt's dependency resolution may return stale artifacts.
- On Windows, starting npm and sbt through a shell breaks on spaces and `&` in paths and arguments. When starting from Node, start npm-cli.js directly with `process.execPath` (example below).

## 3. Recommended steps

Put a lock file with the hx4s commit (full SHA) and version in the user repository.

```properties
# hx4s.lock (at the root of the user repository)
commit=d140b21886ae6f454bf625cdd1dcf549b3d79010
version=0.1.0-local.gd140b21886
```

The setup script runs in this order.

1. Check that the HEAD of the hx4s checkout equals the commit in the lock and that the working tree is clean (if not, stop and print the checkout steps)
2. If `publish-local.mjs --verify --version <lock> --commit <lock>` passes, skip publishing. If not, publish with `publish-local.mjs --version <lock>` and run `--verify` again
3. After the user project's `npm ci`, install the hx4s-vite tgz with `--no-save`

```js
// scripts/setup-hx4s.mjs (user project; call from npm run or directly with node)
// The location of hx4s is the environment variable HX4S_DIR (default ../hx4s)
import { execFileSync, spawnSync } from "node:child_process";
import { existsSync, readFileSync } from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..");
const hx4s = path.resolve(root, process.env.HX4S_DIR ?? "../hx4s");
const lock = Object.fromEntries(readFileSync(path.join(root, "hx4s.lock"), "utf8").split(/\r?\n/)
  .map((line) => /^(\w+)=(.+)$/.exec(line.trim())).filter(Boolean).map((match) => [match[1], match[2]]));
if (!/^[0-9a-f]{40}$/.test(lock.commit ?? "") || !lock.version) throw new Error("hx4s.lock needs commit (40-character SHA) and version");

const git = (...args) => execFileSync("git", ["-C", hx4s, ...args], { encoding: "utf8" }).trim();
const publishLocal = (...args) =>
  spawnSync(process.execPath, [path.join(hx4s, "scripts", "publish-local.mjs"), ...args], { cwd: hx4s, stdio: "inherit" }).status === 0;
// Start npm-cli.js directly with Node (without a shell)
const npmCli = [process.env.npm_execpath, path.join(path.dirname(process.execPath), "node_modules", "npm", "bin", "npm-cli.js"),
  path.join(path.dirname(process.execPath), "..", "lib", "node_modules", "npm", "bin", "npm-cli.js")]
  .find((file) => file && path.basename(file) === "npm-cli.js" && existsSync(file));
if (!npmCli) throw new Error("npm-cli.js was not found next to the running Node");
const npm = (...args) => {
  if (spawnSync(process.execPath, [npmCli, ...args], { cwd: root, stdio: "inherit" }).status !== 0) throw new Error(`npm ${args.join(" ")} failed`);
};

if (git("rev-parse", "HEAD") !== lock.commit) throw new Error(`${hx4s} is not at ${lock.commit}. Run: git -C "${hx4s}" checkout ${lock.commit}`);
if (git("status", "--porcelain", "--untracked-files=all") !== "") throw new Error(`${hx4s} has local changes`);

const expected = ["--version", lock.version, "--commit", lock.commit];
if (!publishLocal("--verify", ...expected)) {
  if (!publishLocal("--version", lock.version, "--reuse-node-modules")) throw new Error("hx4s publish-local failed");
  if (!publishLocal("--verify", ...expected)) throw new Error("hx4s was published but does not match hx4s.lock");
}

npm("ci", "--no-audit", "--no-fund");
npm("install", "--no-save", "--no-audit", "--no-fund", path.relative(root, path.join(hx4s, "target", "local", "hx4s-vite.tgz")).replaceAll("\\", "/"));
```

The sbt side reads the version from the same lock file.

```scala
// project/plugins.sbt (user project)
val hx4sLockedVersion: String = {
  val properties = new java.util.Properties
  IO.load(properties, file("hx4s.lock"))
  Option(properties.getProperty("version")).getOrElse(sys.error("hx4s.lock has no version"))
}

addSbtPlugin("io.github.montereylangit" % "sbt-hx4s" % hx4sLockedVersion)
```

The working directory of `project/plugins.sbt` is the build root, so `file("hx4s.lock")` points to the repository root. Library versions match through sbt-hx4s's `hx4sVersion`.

To update hx4s, move hx4s to a new commit, rewrite `commit` and `version` in the lock file, and run the setup script.
`--verify` fails (the commit differs), so it is republished.
