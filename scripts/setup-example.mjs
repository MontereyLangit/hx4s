// examples/<name> の npm 依存を入れる。
//   1. scripts/publish-local.mjs --verify で、ローカル公開が今の checkout と一致していることを確かめる
//   2. examples/<name>/package-lock.json のとおりに npm ci する（通常の依存は lockfile で固定する）
//   3. target/local/hx4s-vite.tgz を --no-save で入れる（tgz の整合性ハッシュは公開のたびに変わるので、package.json と lockfile には載せない）
// 使い方: node scripts/setup-example.mjs [name]（既定は minimal）
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
