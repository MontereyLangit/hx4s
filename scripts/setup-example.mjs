// Installs the npm dependencies of examples/<name>.
//   1. Checks with scripts/publish-local.mjs --verify that the local publish matches the current checkout
//   2. Runs npm ci from examples/<name>/package-lock.json (ordinary dependencies are pinned by the lockfile)
//   3. Installs target/local/hx4s-vite.tgz with --no-save (the tgz's integrity hash changes on every publish, so it is kept out of package.json and the lockfile)
// Usage: node scripts/setup-example.mjs [name] (minimal or api-only; default minimal)
import { spawnSync } from "node:child_process";
import { existsSync } from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";
import { runTool } from "./lib/command.mjs";

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..");
const name = process.argv[2] ?? "minimal";
if (!/^[A-Za-z0-9_-]+$/.test(name)) throw new Error(`invalid example name ${name}`);
const example = path.join(root, "examples", name);
const tgz = path.join(root, "target", "local", "hx4s-vite.tgz");
if (!existsSync(path.join(example, "package-lock.json"))) throw new Error(`${example} has no package-lock.json`);

const verified = spawnSync(process.execPath, [path.join(root, "scripts", "publish-local.mjs"), "--verify"], { cwd: root, stdio: "inherit" });
if (verified.status !== 0) throw new Error("the local publish does not match this checkout. Run `node scripts/publish-local.mjs` first.");

runTool("npm", ["ci", "--no-audit", "--no-fund"], { cwd: example });
runTool("npm", ["install", "--no-save", "--no-audit", "--no-fund", path.relative(example, tgz).replaceAll("\\", "/")], { cwd: example });
