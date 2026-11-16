// examples/<name> の npm 依存を入れる。
//   1. examples/<name>/package-lock.json のとおりに npm ci する（通常の依存は lockfile で固定する）
//   2. scripts/publish-local.mjs が作った target/local/hx4s-vite.tgz を --no-save で入れる
//      （tgz の整合性ハッシュは公開のたびに変わるので、package.json と lockfile には載せない）
// 使い方: node scripts/setup-example.mjs [name]（既定は minimal）
import { spawnSync } from "node:child_process";
import { existsSync } from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..");
const name = process.argv[2] ?? "minimal";
if (!/^[A-Za-z0-9_-]+$/.test(name)) throw new Error(`invalid example name ${name}`);
const example = path.join(root, "examples", name);
const tgz = path.join(root, "target", "local", "hx4s-vite.tgz");
if (!existsSync(path.join(example, "package-lock.json"))) throw new Error(`${example} has no package-lock.json`);
if (!existsSync(tgz)) throw new Error("target/local/hx4s-vite.tgz is missing. Run `node scripts/publish-local.mjs` first.");

function npm(args) {
  console.log(`> npm ${args.join(" ")}`);
  // Windows の npm.cmd はシェル経由でしか起動できない。引数に空白や引用符を含めない
  const result = spawnSync(process.platform === "win32" ? "npm.cmd" : "npm", args, { cwd: example, stdio: "inherit", shell: process.platform === "win32" });
  if (result.status !== 0) throw new Error(`npm ${args.join(" ")} failed with exit code ${result.status}`);
}

npm(["ci"]);
npm(["install", "--no-save", "--no-audit", "--no-fund", path.relative(example, tgz).replaceAll("\\", "/")]);
