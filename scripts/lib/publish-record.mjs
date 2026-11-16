// ローカル公開の記録（docs/hx4s/local-dependency.md）の組み立てと照合。publish-local.mjs が使い、tests/publish-record.test.js が一時ディレクトリで確かめる。
//   完了の記録 hx4s-publish.json: { format: 1, version, commit, dirty, dirtyHash, maven, npm }
//   maven の各要素: { organization, name, version, path, files: [{ path, sha256 }] }。files は ivy.xml と、ivy.xml の publications が指す実体
//   npm: { name, file, sha256 }
import { execFileSync } from "node:child_process";
import { createHash } from "node:crypto";
import { existsSync, readFileSync, readdirSync, statSync } from "node:fs";
import path from "node:path";
import { isDeepStrictEqual } from "node:util";

export const recordFormat = 1;
// publishLocal が出す成果物（build.sbt の root が aggregate するモジュール）。照合は記録をこの集合と比べる
export const mavenNames = ["hx4s-codegen_3", "hx4s-core_3", "hx4s-core_sjs1_3", "hx4s-react_sjs1_3", "sbt-hx4s"];
export const npmArtifact = { name: "hx4s-vite", file: "hx4s-vite.tgz" };
// 公開物に入るソースと、npm のビルドの入力（ルートの package.json と lockfile）。テストは除く
export const publishedSources = [
  "version.txt", "build.sbt", "project", "modules", "sbt-plugin/src/main", "vite", "package.json", "package-lock.json", "LICENSE", "NOTICE",
  ":(exclude,glob)**/src/test/**",
];
export const recordFiles = {
  complete: "hx4s-publish.json",
  sbt: "hx4s-publish-sbt.json",
  npm: "hx4s-publish-npm.json",
  version: "hx4s-version.txt",
};

const sourceKeys = ["version", "commit", "dirty", "dirtyHash"];
const completeKeys = ["format", ...sourceKeys, "maven", "npm"];
const mavenKeys = ["organization", "name", "version", "path", "files"];
const hex = (length) => new RegExp(`^[0-9a-f]{${length}}$`);
const versionPattern = /^\d+\.\d+\.\d+(-[0-9A-Za-z.-]+)?$/;

export function sha256(data) {
  return createHash("sha256").update(data).digest("hex");
}

export function readJson(file) {
  try {
    return JSON.parse(readFileSync(file, "utf8"));
  } catch {
    return undefined;
  }
}

/** 追跡中・未追跡の公開対象ソースのうち HEAD と違うものの、パスと内容のハッシュ 8 桁（変更が無ければ null） */
export function dirtyHash(root, sources = publishedSources) {
  const status = execFileSync("git", ["status", "--porcelain", "--untracked-files=all", "--no-renames", "-z", "--", ...sources], { cwd: root, encoding: "utf8" });
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
export function sourceState(root) {
  return { commit: execFileSync("git", ["rev-parse", "HEAD"], { cwd: root, encoding: "utf8" }).trim(), dirtyHash: dirtyHash(root) };
}

export const source = (version, state) => ({ version, commit: state.commit, dirty: state.dirtyHash !== null, dirtyHash: state.dirtyHash });

function subdirs(dir) {
  return existsSync(dir) ? readdirSync(dir, { withFileTypes: true }).filter((entry) => entry.isDirectory()).map((entry) => entry.name).sort() : [];
}

/** ivy.xml の publications が指すファイル（ivy のローカルリポジトリの [type]s/[artifact](-[classifier]).[ext]） */
export function ivyPublications(ivyXml) {
  const publications = /<publications>([\s\S]*?)<\/publications>/.exec(ivyXml)?.[1] ?? "";
  return [...publications.matchAll(/<artifact\s([^>]*?)\/?>/g)].map((match) => {
    const attributes = Object.fromEntries([...match[1].matchAll(/([\w:]+)="([^"]*)"/g)].map((attribute) => [attribute[1], attribute[2]]));
    const classifier = attributes["e:classifier"] === undefined ? "" : `-${attributes["e:classifier"]}`;
    return `${attributes.type}s/${attributes.name}${classifier}.${attributes.ext}`;
  });
}

/** ivyLocal にある name のこの版の成果物と、ivy.xml と publications の実体の SHA-256。sbt プラグインは <name>/scala_<v>/sbt_<v>/<version> に置かれる */
export function publishedMavenArtifact(ivyLocal, organization, name, version) {
  const moduleDir = path.join(ivyLocal, organization, name);
  const nested = subdirs(moduleDir).filter((dir) => dir.startsWith("scala_"))
    .flatMap((scala) => subdirs(path.join(moduleDir, scala)).filter((dir) => dir.startsWith("sbt_")).map((sbt) => [scala, sbt, version]));
  const parts = [[version], ...nested].find((candidate) => existsSync(path.join(moduleDir, ...candidate, "ivys", "ivy.xml")));
  if (parts === undefined) throw new Error(`${organization}:${name}:${version} has no ivys/ivy.xml in ${ivyLocal}`);
  const dir = path.join(moduleDir, ...parts);
  const ivyXml = readFileSync(path.join(dir, "ivys", "ivy.xml"), "utf8");
  const publications = ivyPublications(ivyXml);
  if (publications.length === 0) throw new Error(`${path.join(dir, "ivys", "ivy.xml")} lists no publications`);
  const files = ["ivys/ivy.xml", ...publications].map((file) => {
    const absolute = path.join(dir, ...file.split("/"));
    if (!existsSync(absolute)) throw new Error(`${absolute} is listed in ivy.xml but missing`);
    return { path: file, sha256: sha256(readFileSync(absolute)) };
  });
  return { organization, name, version, path: [organization, name, ...parts].join("/"), files };
}

const isObject = (value) => typeof value === "object" && value !== null && !Array.isArray(value);

function keyProblems(label, value, keys) {
  if (!isObject(value)) return [`${label} is not an object`];
  const missing = keys.filter((key) => !Object.hasOwn(value, key));
  const extra = Object.keys(value).filter((key) => !keys.includes(key));
  return [
    ...missing.map((key) => `${label} has no ${key}`),
    ...extra.map((key) => `${label} has an unexpected field ${key}`),
  ];
}

function sourceProblems(label, record) {
  const problems = [];
  if (typeof record.version !== "string" || !versionPattern.test(record.version)) problems.push(`${label} has an invalid version ${JSON.stringify(record.version)}`);
  if (typeof record.commit !== "string" || !hex(40).test(record.commit)) problems.push(`${label} has an invalid commit ${JSON.stringify(record.commit)}`);
  if (record.dirtyHash !== null && (typeof record.dirtyHash !== "string" || !hex(8).test(record.dirtyHash))) problems.push(`${label} has an invalid dirtyHash ${JSON.stringify(record.dirtyHash)}`);
  if (record.dirty !== (record.dirtyHash !== null)) problems.push(`${label} has dirty ${JSON.stringify(record.dirty)} that does not match dirtyHash`);
  return problems;
}

function mavenEntryProblems(entry, organization, version) {
  const label = `maven artifact ${isObject(entry) && typeof entry.name === "string" ? entry.name : JSON.stringify(entry)}`;
  const shape = keyProblems(label, entry, mavenKeys);
  if (shape.length > 0) return shape;
  const problems = [];
  if (entry.organization !== organization) problems.push(`${label} has organization ${JSON.stringify(entry.organization)}, expected ${organization}`);
  if (entry.version !== version) problems.push(`${label} has version ${JSON.stringify(entry.version)}, expected ${version}`);
  const segments = typeof entry.path === "string" ? entry.path.split("/") : [];
  if (segments[0] !== organization || segments[1] !== entry.name || segments.at(-1) !== version || segments.some((segment) => segment === "" || segment === "." || segment === "..")) {
    problems.push(`${label} has an invalid path ${JSON.stringify(entry.path)}`);
  }
  if (!Array.isArray(entry.files) || entry.files.length === 0) return [...problems, `${label} lists no files`];
  for (const file of entry.files) {
    const fileShape = keyProblems(`a file of ${label}`, file, ["path", "sha256"]);
    if (fileShape.length > 0) problems.push(...fileShape);
    else if (typeof file.path !== "string" || !/^[a-z]+s\/[^/\\]+$/.test(file.path) || file.path.split("/")[1].startsWith(".")) problems.push(`${label} has an invalid file path ${JSON.stringify(file.path)}`);
    else if (typeof file.sha256 !== "string" || !hex(64).test(file.sha256)) problems.push(`${label} has an invalid SHA-256 for ${file.path}`);
  }
  const paths = entry.files.filter(isObject).map((file) => file.path);
  if (new Set(paths).size !== paths.length) problems.push(`${label} lists a file twice`);
  for (const [dir, kind] of [["ivys/ivy.xml", "ivy.xml"], ["jars/", "a jar"], ["poms/", "a pom"]]) {
    if (!paths.some((file) => typeof file === "string" && (dir.endsWith("/") ? file.startsWith(dir) : file === dir))) problems.push(`${label} does not list ${kind}`);
  }
  return problems;
}

/** 完了の記録の形と、sbt・npm の部分の記録との一致を確かめ、食い違いを返す */
export function recordProblems(record, parts, organization) {
  const shape = keyProblems("the complete record", record, completeKeys);
  if (!isObject(record)) return shape;
  const problems = [...shape];
  if (record.format !== recordFormat) problems.push(`unknown record format ${JSON.stringify(record.format)}`);
  problems.push(...sourceProblems("the complete record", record));
  if (!Array.isArray(record.maven) || record.maven.length === 0) {
    problems.push("the complete record lists no maven artifacts");
  } else {
    for (const entry of record.maven) problems.push(...mavenEntryProblems(entry, organization, record.version));
    const names = record.maven.filter(isObject).map((entry) => entry.name);
    const missing = mavenNames.filter((name) => !names.includes(name));
    const extra = names.filter((name) => !mavenNames.includes(name));
    const duplicated = names.filter((name, index) => names.indexOf(name) !== index);
    if (missing.length > 0) problems.push(`the complete record has no maven artifact ${missing.join(", ")}`);
    if (extra.length > 0) problems.push(`the complete record has unexpected maven artifacts ${extra.join(", ")}`);
    if (duplicated.length > 0) problems.push(`the complete record lists ${[...new Set(duplicated)].join(", ")} more than once`);
  }
  const npmShape = keyProblems("the npm artifact", record.npm, ["name", "file", "sha256"]);
  if (npmShape.length > 0) {
    problems.push(...npmShape);
  } else {
    if (record.npm.name !== npmArtifact.name) problems.push(`the npm artifact is ${JSON.stringify(record.npm.name)}, expected ${npmArtifact.name}`);
    if (record.npm.file !== npmArtifact.file) problems.push(`the npm artifact file is ${JSON.stringify(record.npm.file)}, expected ${npmArtifact.file}`);
    if (typeof record.npm.sha256 !== "string" || !hex(64).test(record.npm.sha256)) problems.push("the npm artifact has an invalid SHA-256");
  }
  const sourceOf = (value) => Object.fromEntries(sourceKeys.map((key) => [key, value[key]]));
  for (const [name, part, field] of [["sbt", parts.sbt, "maven"], ["npm", parts.npm, "npm"]]) {
    if (!isObject(part)) problems.push(`the ${name} record is missing`);
    else if (!isDeepStrictEqual(part, { ...sourceOf(record), [field]: record[field] })) problems.push(`the ${name} record does not belong to the completed publish`);
  }
  return problems;
}

/** 記録にある成果物が手元にあり、内容が記録と同じかを確かめ、食い違いを返す。形の正しい要素だけを見る */
export function artifactProblems(record, { ivyLocal, output }) {
  const problems = [];
  for (const entry of Array.isArray(record.maven) ? record.maven : []) {
    if (!isObject(entry) || typeof entry.path !== "string" || !Array.isArray(entry.files)) continue;
    for (const file of entry.files) {
      if (!isObject(file) || typeof file.path !== "string") continue;
      const absolute = path.join(ivyLocal, ...entry.path.split("/"), ...file.path.split("/"));
      if (!existsSync(absolute)) problems.push(`${absolute} is missing`);
      else if (sha256(readFileSync(absolute)) !== file.sha256) problems.push(`${absolute} does not match the recorded SHA-256`);
    }
  }
  if (isObject(record.npm) && record.npm.file === npmArtifact.file) {
    const tgz = path.join(output, record.npm.file);
    if (!existsSync(tgz)) problems.push(`${tgz} is missing`);
    else if (sha256(readFileSync(tgz)) !== record.npm.sha256) problems.push(`${tgz} does not match the recorded SHA-256`);
  }
  return problems;
}

/** output の完了の記録を今の checkout（root）と成果物に照らし、食い違いと照合した記録を返す */
export function verifyPublish({ root, ivyLocal, output, organization, version, commit }) {
  const complete = path.join(output, recordFiles.complete);
  const record = readJson(complete);
  if (record === undefined) return { record, problems: [`${complete} is missing: hx4s has not been published completely`] };
  const parts = { sbt: readJson(path.join(output, recordFiles.sbt)), npm: readJson(path.join(output, recordFiles.npm)) };
  const problems = recordProblems(record, parts, organization);
  if (!isObject(record)) return { record, problems };
  if (version !== undefined && record.version !== version) problems.push(`published version is ${record.version}, expected ${version}`);
  if (commit !== undefined && record.commit !== commit) problems.push(`published commit is ${record.commit}, expected ${commit}`);
  const state = sourceState(root);
  if (record.commit !== state.commit) problems.push(`published from ${record.commit}, but HEAD is ${state.commit}`);
  if (record.dirtyHash !== state.dirtyHash) {
    const then = record.dirtyHash === null ? "were clean" : `had local changes (${record.dirtyHash})`;
    const now = state.dirtyHash === null ? "are clean" : `have local changes (${state.dirtyHash})`;
    problems.push(`published sources ${then}, but they ${now} now`);
  }
  problems.push(...artifactProblems(record, { ivyLocal, output }));
  const versionFile = path.join(output, recordFiles.version);
  if (!existsSync(versionFile) || readFileSync(versionFile, "utf8").trim() !== record.version) problems.push(`${versionFile} does not contain ${record.version}`);
  return { record, problems };
}
