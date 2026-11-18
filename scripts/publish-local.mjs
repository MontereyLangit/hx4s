// Publishes hx4s locally, and writes and verifies publish records (docs/hx4s/local-dependency.md, ADR 0006).
//   Publish: node scripts/publish-local.mjs [--version <version>-local.N] [--skip-sbt] [--skip-npm] [--reuse-node-modules]
//     sbt: publishLocal of all modules to ~/.ivy2/local. Record in target/local/hx4s-publish-sbt.json
//     npm: npm ci from the lockfile, build hx4s-vite and pack it into target/local/hx4s-vite.tgz. Record in target/local/hx4s-publish-npm.json
//     If both records have the same version, commit and working tree state, write the completed record target/local/hx4s-publish.json and hx4s-version.txt
//   Verify: node scripts/publish-local.mjs --verify [--version V] [--commit <40-character SHA>]
//     Exits 0 if the completed record matches the current HEAD, working tree and artifacts; otherwise prints the reason and exits 1
// Without a version: <version in version.txt>-local.g<commit> (with .dirty-<8-character content hash> if the published sources have uncommitted changes or untracked files).
// The shape of the records and the verification are in lib/publish-record.mjs.
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

/** Writes the completed record if the sbt and npm records belong to the same publish and all artifacts are present. Returns the reason when it does not */
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
  // Delete first so that the previous completed record does not survive a failure midway
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
