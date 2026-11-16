// hx4s をローカルに公開し、公開記録を書く・照合する（docs/hx4s/local-dependency.md、ADR 0006）。
//   公開: node scripts/publish-local.mjs [--version <版>-local.N] [--skip-sbt] [--skip-npm] [--reuse-node-modules]
//     sbt: publishLocal で全モジュールを ~/.ivy2/local へ。記録は target/local/hx4s-publish-sbt.json
//     npm: lockfile どおりに npm ci し、hx4s-vite をビルドして target/local/hx4s-vite.tgz に pack。記録は target/local/hx4s-publish-npm.json
//     2 つの記録が同じ版・commit・作業ツリーの状態なら、完了の記録 target/local/hx4s-publish.json と hx4s-version.txt を書く
//   照合: node scripts/publish-local.mjs --verify [--version V] [--commit <40 桁の SHA>]
//     完了の記録が今の HEAD・作業ツリー・成果物と一致すれば 0、しなければ理由を出して 1 で終わる
// 版を省くと <version.txt の版>-local.g<commit>（公開対象のソースに未コミットの変更か未追跡のファイルがあれば .dirty-<内容のハッシュ 8 桁>）。
// 記録の形と照合の中身は lib/publish-record.mjs。
import { cpSync, existsSync, mkdirSync, readFileSync, renameSync, rmSync, writeFileSync } from "node:fs";
import os from "node:os";
import path from "node:path";
import { fileURLToPath } from "node:url";
import { runTool } from "./lib/command.mjs";
import {
  artifactProblems, mavenNames, npmArtifact, publishedMavenArtifact, readJson, recordFiles, recordFormat, recordProblems, sha256, source, sourceState,
  verifyPublish,
} from "./lib/publish-record.mjs";
import { baseVersion, localVersion } from "./lib/version.mjs";

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..");
const output = path.join(root, "target", "local");
const ivyLocal = path.join(os.homedir(), ".ivy2", "local");
const files = {
  complete: path.join(output, recordFiles.complete),
  sbt: path.join(output, recordFiles.sbt),
  npm: path.join(output, recordFiles.npm),
  version: path.join(output, recordFiles.version),
  tgz: path.join(output, npmArtifact.file),
  npmCiStamp: path.join(root, "node_modules", ".hx4s-npm-ci.json"),
};

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

function organization() {
  const match = /ThisBuild\s*\/\s*organization\s*:=\s*"([^"]+)"/.exec(readFileSync(path.join(root, "build.sbt"), "utf8"));
  if (!match) throw new Error("build.sbt has no ThisBuild / organization");
  return match[1];
}

function writeJson(file, value) {
  writeFileSync(file, `${JSON.stringify(value, null, 2)}\n`);
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
  const maven = mavenNames.map((name) => publishedMavenArtifact(ivyLocal, organization(), name, version));
  writeJson(files.sbt, { ...source(version, state), maven });
}

function publishNpm(version, state, reuseNodeModules) {
  rmSync(files.npm, { force: true });
  ensureNodeModules(reuseNodeModules);
  runTool("npm", ["run", "build", "-w", npmArtifact.name], { cwd: root });
  const stage = path.join(output, "stage", npmArtifact.name);
  rmSync(stage, { recursive: true, force: true });
  mkdirSync(stage, { recursive: true });
  const pkg = JSON.parse(readFileSync(path.join(root, "vite", "package.json"), "utf8"));
  if (pkg.name !== npmArtifact.name) throw new Error(`vite/package.json is ${pkg.name}, expected ${npmArtifact.name}`);
  pkg.version = version;
  delete pkg.devDependencies;
  delete pkg.scripts;
  pkg.files = [...pkg.files, "NOTICE"];
  writeJson(path.join(stage, "package.json"), pkg);
  for (const file of ["dist", "node-loader.mjs", "node-register.mjs"]) cpSync(path.join(root, "vite", file), path.join(stage, file), { recursive: true });
  for (const file of ["LICENSE", "NOTICE"]) cpSync(path.join(root, file), path.join(stage, file));
  runTool("npm", ["pack", "--pack-destination", output], { cwd: stage });
  const packed = path.join(output, `${npmArtifact.name}-${version}.tgz`);
  if (!existsSync(packed)) throw new Error(`npm pack did not produce ${packed}`);
  renameSync(packed, files.tgz);
  writeJson(files.npm, { ...source(version, state), npm: { ...npmArtifact, sha256: sha256(readFileSync(files.tgz)) } });
}

/** sbt と npm の記録が同じ公開のもので成果物もそろっていれば、完了の記録を書く。書かなかったときは理由を返す */
function writeCompleteRecord() {
  const sbt = readJson(files.sbt);
  const npm = readJson(files.npm);
  if (sbt === undefined || npm === undefined) return ["the sbt or npm record is missing"];
  const record = { format: recordFormat, ...source(sbt.version, sbt), maven: sbt.maven, npm: npm.npm };
  const problems = [...recordProblems(record, { sbt, npm }, organization()), ...artifactProblems(record, { ivyLocal, output })];
  if (problems.length > 0) return problems;
  writeJson(files.complete, record);
  writeFileSync(files.version, `${record.version}\n`);
  return [];
}

function publish(options) {
  const state = sourceState(root);
  const version = options.version ?? localVersion(baseVersion(), state.commit, state.dirtyHash);
  mkdirSync(output, { recursive: true });
  // 途中で失敗したときに前の完了の記録が残らないよう、先に消す
  rmSync(files.complete, { force: true });
  rmSync(files.version, { force: true });
  if (options.sbt) publishSbt(version, state);
  if (options.npm) publishNpm(version, state, options.reuseNodeModules);
  const problems = writeCompleteRecord();
  console.log(`published hx4s ${version}${options.sbt && options.npm ? "" : ` (${options.sbt ? "sbt" : "npm"} only)`}`);
  if (options.sbt) console.log(`  sbt: ${path.join(ivyLocal, organization())}/*/${version}`);
  if (options.npm) console.log(`  npm: ${files.tgz}`);
  if (problems.length === 0) console.log(`  record: ${files.complete}`);
  else console.log(`  ${files.complete} was not written:\n${problems.map((problem) => `    ${problem}`).join("\n")}`);
}

try {
  const options = parseArgs(process.argv.slice(2));
  if (options.verify) {
    const { record, problems } = verifyPublish({ root, ivyLocal, output, organization: organization(), version: options.version, commit: options.commit });
    for (const problem of problems) console.error(problem);
    if (problems.length === 0) console.log(`verified hx4s ${record.version} (commit ${record.commit}${record.dirty ? `, local changes ${record.dirtyHash}` : ""})`);
    process.exitCode = problems.length === 0 ? 0 : 1;
  } else {
    publish(options);
  }
} catch (error) {
  console.error(error instanceof Error ? error.message : error);
  process.exitCode = 1;
}
