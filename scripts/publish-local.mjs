// hx4s をローカルに公開する（docs/hx4s/adr/0006）。
//   1. sbt publishLocal で全モジュール（hx4s-core / hx4s-react / hx4s-codegen / sbt-hx4s）を ~/.ivy2/local へ
//   2. hx4s-vite をビルドして npm pack し、target/local/hx4s-vite.tgz に置く
//   3. 公開した版を target/local/hx4s-version.txt に書く（examples/minimal と利用側が読む）
// 使い方: node scripts/publish-local.mjs [--version 0.1.0-local.N] [--skip-sbt] [--skip-npm]
// 版を省くと 0.1.0-local.g<commit> にする（公開対象のソースに未コミットの変更や未追跡のファイルがあれば、
// 末尾にその内容のハッシュを .dirty-<8 桁> として付ける）。
import { spawnSync } from "node:child_process";
import { createHash } from "node:crypto";
import { cpSync, existsSync, mkdirSync, readFileSync, renameSync, rmSync, statSync, writeFileSync } from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..");
const output = path.join(root, "target", "local");
const windows = process.platform === "win32";

function run(command, args, options = {}) {
  console.log(`> ${command} ${args.join(" ")}`);
  // Windows の sbt.bat / npm.cmd はシェル経由でしか起動できない。引数に空白や引用符を含めない
  const result = spawnSync(command, args, { cwd: root, stdio: "inherit", shell: windows, ...options });
  if (result.status !== 0) throw new Error(`${command} ${args.join(" ")} failed with exit code ${result.status}`);
}

function capture(command, args) {
  const result = spawnSync(command, args, { cwd: root, encoding: "utf8" });
  if (result.status !== 0) throw new Error(`${command} ${args.join(" ")} failed: ${result.stderr}`);
  return result.stdout.trim();
}

function parseArgs(argv) {
  const options = { version: undefined, sbt: true, npm: true };
  for (let i = 0; i < argv.length; i++) {
    if (argv[i] === "--version") options.version = argv[++i];
    else if (argv[i] === "--skip-sbt") options.sbt = false;
    else if (argv[i] === "--skip-npm") options.npm = false;
    else throw new Error(`unknown argument ${argv[i]}`);
  }
  return options;
}

// 公開物に入るソース。テストは除く
const publishedSources = ["build.sbt", "project", "modules", "sbt-plugin/src/main", "vite", "LICENSE", "NOTICE", ":(exclude,glob)**/src/test/**"];

/** 追跡中・未追跡の公開対象ソースのうち HEAD と違うものの、パスと内容のハッシュ（変更が無ければ undefined） */
function dirtyHash() {
  const status = spawnSync("git", ["status", "--porcelain", "--untracked-files=all", "--no-renames", "-z", "--", ...publishedSources],
    { cwd: root, encoding: "utf8" });
  if (status.status !== 0) throw new Error(`git status failed: ${status.stderr}`);
  const changed = status.stdout.split("\0").filter(Boolean).map((entry) => entry.slice(3)).sort();
  if (changed.length === 0) return undefined;
  const hash = createHash("sha256");
  for (const file of changed) {
    const absolute = path.join(root, file);
    hash.update(`${file}\0`);
    hash.update(existsSync(absolute) && statSync(absolute).isFile() ? readFileSync(absolute) : "<deleted>");
    hash.update("\0");
  }
  return hash.digest("hex").slice(0, 8);
}

function defaultVersion() {
  const commit = capture("git", ["rev-parse", "--short=10", "HEAD"]);
  const dirty = dirtyHash();
  // 数字だけの識別子は semver で先頭の 0 を許さないので、ハッシュは dirty- に続けて 1 つの識別子にする
  return `0.1.0-local.g${commit}${dirty === undefined ? "" : `.dirty-${dirty}`}`;
}

const options = parseArgs(process.argv.slice(2));
const version = options.version ?? defaultVersion();
if (!/^\d+\.\d+\.\d+(-[0-9A-Za-z.-]+)?$/.test(version)) throw new Error(`invalid version ${version}`);
mkdirSync(output, { recursive: true });

if (options.sbt) {
  run(windows ? "sbt.bat" : "sbt", ["--batch", `-Dhx4s.version=${version}`, "publishLocal"]);
}

if (options.npm) {
  run(windows ? "npm.cmd" : "npm", ["run", "build", "-w", "hx4s-vite"]);
  const stage = path.join(output, "stage", "hx4s-vite");
  rmSync(stage, { recursive: true, force: true });
  mkdirSync(stage, { recursive: true });
  const pkg = JSON.parse(readFileSync(path.join(root, "vite", "package.json"), "utf8"));
  pkg.version = version;
  delete pkg.devDependencies;
  delete pkg.scripts;
  pkg.files = [...pkg.files, "NOTICE"];
  writeFileSync(path.join(stage, "package.json"), `${JSON.stringify(pkg, null, 2)}\n`);
  for (const file of ["dist", "node-loader.mjs", "node-register.mjs"]) cpSync(path.join(root, "vite", file), path.join(stage, file), { recursive: true });
  for (const file of ["LICENSE", "NOTICE"]) cpSync(path.join(root, file), path.join(stage, file));
  run(windows ? "npm.cmd" : "npm", ["pack", "--pack-destination", path.relative(stage, output)], { cwd: stage });
  const packed = path.join(output, `hx4s-vite-${version}.tgz`);
  if (!existsSync(packed)) throw new Error(`npm pack did not produce ${packed}`);
  renameSync(packed, path.join(output, "hx4s-vite.tgz"));
}

writeFileSync(path.join(output, "hx4s-version.txt"), `${version}\n`);
console.log(`published hx4s ${version}`);
console.log(`  sbt: ~/.ivy2/local/io.github.montereylangit/*/${version}`);
console.log(`  npm: ${path.join(output, "hx4s-vite.tgz")}`);
