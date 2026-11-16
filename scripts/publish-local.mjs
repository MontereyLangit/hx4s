// hx4s をローカルに公開し、公開記録を書く・照合する（docs/hx4s/local-dependency.md、ADR 0006）。
//   公開: node scripts/publish-local.mjs [--version 0.1.0-local.N] [--skip-sbt] [--skip-npm] [--reuse-node-modules]
//     sbt: publishLocal で全モジュールを ~/.ivy2/local へ。記録は target/local/hx4s-publish-sbt.json
//     npm: lockfile どおりに npm ci し、hx4s-vite をビルドして target/local/hx4s-vite.tgz に pack。記録は target/local/hx4s-publish-npm.json
//     2 つの記録が同じ版・commit・作業ツリーの状態なら、完了の記録 target/local/hx4s-publish.json と hx4s-version.txt を書く
//   照合: node scripts/publish-local.mjs --verify [--version V] [--commit <40 桁の SHA>]
//     完了の記録が今の HEAD・作業ツリー・成果物と一致すれば 0、しなければ理由を出して 1 で終わる
// 版を省くと 0.1.0-local.g<commit>（公開対象のソースに未コミットの変更か未追跡のファイルがあれば .dirty-<内容のハッシュ 8 桁>）。
import { execFileSync } from "node:child_process";
import { createHash } from "node:crypto";
import { cpSync, existsSync, mkdirSync, readFileSync, readdirSync, renameSync, rmSync, statSync, writeFileSync } from "node:fs";
import os from "node:os";
import path from "node:path";
import { fileURLToPath } from "node:url";
import { runTool } from "./lib/command.mjs";

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..");
const output = path.join(root, "target", "local");
const ivyLocal = path.join(os.homedir(), ".ivy2", "local");
const files = {
  complete: path.join(output, "hx4s-publish.json"),
  sbt: path.join(output, "hx4s-publish-sbt.json"),
  npm: path.join(output, "hx4s-publish-npm.json"),
  version: path.join(output, "hx4s-version.txt"),
  tgz: path.join(output, "hx4s-vite.tgz"),
  npmCiStamp: path.join(root, "node_modules", ".hx4s-npm-ci.json"),
};
// publishLocal が出す成果物（build.sbt の root が aggregate するモジュール）
const mavenNames = ["hx4s-codegen_3", "hx4s-core_3", "hx4s-core_sjs1_3", "hx4s-react_sjs1_3", "sbt-hx4s"];
// 公開物に入るソース。テストは除く
const publishedSources = ["build.sbt", "project", "modules", "sbt-plugin/src/main", "vite", "LICENSE", "NOTICE", ":(exclude,glob)**/src/test/**"];

function git(args) {
  return execFileSync("git", args, { cwd: root, encoding: "utf8" });
}

function sha256(data) {
  return createHash("sha256").update(data).digest("hex");
}

function parseArgs(argv) {
  const options = { version: undefined, commit: undefined, sbt: true, npm: true, verify: false, reuseNodeModules: false };
  const value = (i) => {
    if (i >= argv.length || argv[i].startsWith("--")) throw new Error(`${argv[i - 1]} needs a value`);
    return argv[i];
  };
  for (let i = 0; i < argv.length; i++) {
    if (argv[i] === "--version") options.version = value(++i);
    else if (argv[i] === "--commit") options.commit = value(++i);
    else if (argv[i] === "--skip-sbt") options.sbt = false;
    else if (argv[i] === "--skip-npm") options.npm = false;
    else if (argv[i] === "--reuse-node-modules") options.reuseNodeModules = true;
    else if (argv[i] === "--verify") options.verify = true;
    else throw new Error(`unknown argument ${argv[i]}`);
  }
  if (options.version !== undefined && !/^\d+\.\d+\.\d+(-[0-9A-Za-z.-]+)?$/.test(options.version)) throw new Error(`invalid version ${options.version}`);
  if (options.commit !== undefined && !/^[0-9a-f]{40}$/.test(options.commit)) throw new Error(`--commit needs the full 40-character SHA: ${options.commit}`);
  if (options.verify && (!options.sbt || !options.npm || options.reuseNodeModules)) throw new Error("--verify cannot be combined with --skip-sbt, --skip-npm or --reuse-node-modules");
  if (!options.verify && options.commit !== undefined) throw new Error("--commit is only for --verify");
  if (!options.sbt && !options.npm) throw new Error("--skip-sbt and --skip-npm together publish nothing");
  return options;
}

/** 追跡中・未追跡の公開対象ソースのうち HEAD と違うものの、パスと内容のハッシュ 8 桁（変更が無ければ null） */
function dirtyHash() {
  const status = git(["status", "--porcelain", "--untracked-files=all", "--no-renames", "-z", "--", ...publishedSources]);
  const changed = status.split("\0").filter(Boolean).map((entry) => entry.slice(3)).sort();
  if (changed.length === 0) return null;
  const hash = createHash("sha256");
  for (const file of changed) {
    const absolute = path.join(root, file);
    hash.update(`${file}\0`);
    hash.update(existsSync(absolute) && statSync(absolute).isFile() ? readFileSync(absolute) : "<deleted>");
    hash.update("\0");
  }
  return hash.digest("hex").slice(0, 8);
}

/** 今の checkout の commit と、公開対象のソースの状態 */
function sourceState() {
  return { commit: git(["rev-parse", "HEAD"]).trim(), dirtyHash: dirtyHash() };
}

function defaultVersion(state) {
  // 数字だけの識別子は semver で先頭の 0 を許さないので、ハッシュは dirty- に続けて 1 つの識別子にする
  return `0.1.0-local.g${state.commit.slice(0, 10)}${state.dirtyHash === null ? "" : `.dirty-${state.dirtyHash}`}`;
}

function organization() {
  const match = /ThisBuild\s*\/\s*organization\s*:=\s*"([^"]+)"/.exec(readFileSync(path.join(root, "build.sbt"), "utf8"));
  if (!match) throw new Error("build.sbt has no ThisBuild / organization");
  return match[1];
}

function readJson(file) {
  try {
    return JSON.parse(readFileSync(file, "utf8"));
  } catch {
    return undefined;
  }
}

function writeJson(file, value) {
  writeFileSync(file, `${JSON.stringify(value, null, 2)}\n`);
}

const source = (version, state) => ({ version, commit: state.commit, dirty: state.dirtyHash !== null, dirtyHash: state.dirtyHash });
const sameSource = (a, b) => a.version === b.version && a.commit === b.commit && a.dirtyHash === b.dirtyHash;

function subdirs(dir) {
  return existsSync(dir) ? readdirSync(dir, { withFileTypes: true }).filter((entry) => entry.isDirectory()).map((entry) => entry.name).sort() : [];
}

/** ~/.ivy2/local にある、この版の ivy.xml を持つ成果物。sbt プラグインは <name>/scala_<v>/sbt_<v>/<version> に置かれる */
function publishedMaven(org, version) {
  const orgDir = path.join(ivyLocal, org);
  return subdirs(orgDir).flatMap((name) => {
    const nested = subdirs(path.join(orgDir, name)).filter((dir) => dir.startsWith("scala_"))
      .flatMap((scala) => subdirs(path.join(orgDir, name, scala)).filter((dir) => dir.startsWith("sbt_")).map((sbt) => [name, scala, sbt, version]));
    const parts = [[name, version], ...nested].find((candidate) => existsSync(path.join(orgDir, ...candidate, "ivys", "ivy.xml")));
    return parts === undefined ? [] : [{ organization: org, name, version, path: [org, ...parts].join("/") }];
  });
}

/** 記録にある成果物が手元にあり、tgz の内容が記録と同じかを確かめ、食い違いを返す */
function artifactProblems(record) {
  const problems = [];
  for (const artifact of record.maven ?? []) {
    if (!existsSync(path.join(ivyLocal, ...artifact.path.split("/"), "ivys", "ivy.xml"))) {
      problems.push(`${artifact.organization}:${artifact.name}:${artifact.version} is missing from ${ivyLocal}`);
    }
  }
  if (record.npm !== undefined) {
    const tgz = path.join(output, record.npm.file);
    if (!existsSync(tgz)) problems.push(`${tgz} is missing`);
    else if (sha256(readFileSync(tgz)) !== record.npm.sha256) problems.push(`${tgz} does not match the recorded SHA-256`);
  }
  return problems;
}

function ensureNodeModules(reuse) {
  const lockSha256 = sha256(readFileSync(path.join(root, "package-lock.json")));
  if (reuse && readJson(files.npmCiStamp)?.lockSha256 === lockSha256) {
    console.log("node_modules was installed by npm ci from the current package-lock.json; skipping npm ci");
    return;
  }
  runTool("npm", ["ci", "--no-audit", "--no-fund"], { cwd: root });
  writeJson(files.npmCiStamp, { lockSha256 });
}

function publishSbt(version, state) {
  rmSync(files.sbt, { force: true });
  runTool("sbt", ["--batch", `-Dhx4s.version=${version}`, "publishLocal"], { cwd: root });
  const maven = publishedMaven(organization(), version);
  const missing = mavenNames.filter((name) => !maven.some((artifact) => artifact.name === name));
  if (missing.length > 0) throw new Error(`publishLocal did not produce ${missing.join(", ")} in ${ivyLocal}`);
  writeJson(files.sbt, { ...source(version, state), maven });
}

function publishNpm(version, state, reuseNodeModules) {
  rmSync(files.npm, { force: true });
  ensureNodeModules(reuseNodeModules);
  runTool("npm", ["run", "build", "-w", "hx4s-vite"], { cwd: root });
  const stage = path.join(output, "stage", "hx4s-vite");
  rmSync(stage, { recursive: true, force: true });
  mkdirSync(stage, { recursive: true });
  const pkg = JSON.parse(readFileSync(path.join(root, "vite", "package.json"), "utf8"));
  pkg.version = version;
  delete pkg.devDependencies;
  delete pkg.scripts;
  pkg.files = [...pkg.files, "NOTICE"];
  writeJson(path.join(stage, "package.json"), pkg);
  for (const file of ["dist", "node-loader.mjs", "node-register.mjs"]) cpSync(path.join(root, "vite", file), path.join(stage, file), { recursive: true });
  for (const file of ["LICENSE", "NOTICE"]) cpSync(path.join(root, file), path.join(stage, file));
  runTool("npm", ["pack", "--pack-destination", output], { cwd: stage });
  const packed = path.join(output, `hx4s-vite-${version}.tgz`);
  if (!existsSync(packed)) throw new Error(`npm pack did not produce ${packed}`);
  renameSync(packed, files.tgz);
  writeJson(files.npm, { ...source(version, state), npm: { name: pkg.name, file: path.basename(files.tgz), sha256: sha256(readFileSync(files.tgz)) } });
}

/** sbt と npm の記録が同じ公開のもので成果物もそろっていれば、完了の記録を書いて返す。そうでなければ undefined */
function writeCompleteRecord() {
  const sbt = readJson(files.sbt);
  const npm = readJson(files.npm);
  if (sbt === undefined || npm === undefined || !sameSource(sbt, npm)) return undefined;
  const record = { format: 1, ...source(sbt.version, sbt), maven: sbt.maven, npm: npm.npm };
  if (artifactProblems(record).length > 0) return undefined;
  writeJson(files.complete, record);
  writeFileSync(files.version, `${record.version}\n`);
  return record;
}

function publish(options) {
  const state = sourceState();
  const version = options.version ?? defaultVersion(state);
  mkdirSync(output, { recursive: true });
  // 途中で失敗したときに前の完了の記録が残らないよう、先に消す
  rmSync(files.complete, { force: true });
  rmSync(files.version, { force: true });
  if (options.sbt) publishSbt(version, state);
  if (options.npm) publishNpm(version, state, options.reuseNodeModules);
  const record = writeCompleteRecord();
  console.log(`published hx4s ${version}${options.sbt && options.npm ? "" : ` (${options.sbt ? "sbt" : "npm"} only)`}`);
  if (options.sbt) console.log(`  sbt: ${path.join(ivyLocal, organization())}/*/${version}`);
  if (options.npm) console.log(`  npm: ${files.tgz}`);
  if (record === undefined) console.log(`  ${files.complete} was not written: the sbt and npm records are not from the same version, commit and sources`);
  else console.log(`  record: ${files.complete}`);
}

/** 完了の記録を今の checkout と成果物に照らし、食い違いを返す */
function verify(options) {
  const record = readJson(files.complete);
  if (record === undefined) return [`${files.complete} is missing: hx4s has not been published completely`];
  const problems = [];
  if (record.format !== 1) problems.push(`unknown record format ${record.format}`);
  for (const [name, file] of [["sbt", files.sbt], ["npm", files.npm]]) {
    const part = readJson(file);
    if (part === undefined || !sameSource(part, record)) problems.push(`the ${name} record does not belong to the completed publish`);
  }
  if (options.version !== undefined && record.version !== options.version) problems.push(`published version is ${record.version}, expected ${options.version}`);
  if (options.commit !== undefined && record.commit !== options.commit) problems.push(`published commit is ${record.commit}, expected ${options.commit}`);
  const state = sourceState();
  if (record.commit !== state.commit) problems.push(`published from ${record.commit}, but HEAD is ${state.commit}`);
  if (record.dirtyHash !== state.dirtyHash) {
    const then = record.dirtyHash === null ? "were clean" : `had local changes (${record.dirtyHash})`;
    const now = state.dirtyHash === null ? "are clean" : `have local changes (${state.dirtyHash})`;
    problems.push(`published sources ${then}, but they ${now} now`);
  }
  problems.push(...artifactProblems(record));
  if (!existsSync(files.version) || readFileSync(files.version, "utf8").trim() !== record.version) problems.push(`${files.version} does not contain ${record.version}`);
  if (problems.length === 0) console.log(`verified hx4s ${record.version} (commit ${record.commit}${record.dirty ? `, local changes ${record.dirtyHash}` : ""})`);
  return problems;
}

try {
  const options = parseArgs(process.argv.slice(2));
  if (options.verify) {
    const problems = verify(options);
    for (const problem of problems) console.error(problem);
    process.exitCode = problems.length === 0 ? 0 : 1;
  } else {
    publish(options);
  }
} catch (error) {
  console.error(error instanceof Error ? error.message : error);
  process.exitCode = 1;
}
