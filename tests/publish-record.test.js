import assert from 'node:assert/strict';
import { execFileSync } from 'node:child_process';
import { mkdirSync, readFileSync, rmSync, writeFileSync } from 'node:fs';
import { mkdtemp, rm } from 'node:fs/promises';
import os from 'node:os';
import path from 'node:path';
import test from 'node:test';
import {
  ivyPublications, mavenNames, npmArtifact, publishedMavenArtifact, publishedSources, recordFiles, recordFormat, sha256, source, sourceState, verifyPublish,
} from '../scripts/lib/publish-record.mjs';
import { root as repositoryRoot } from '../scripts/lib/version.mjs';

const organization = 'io.example';
const version = '0.1.0-local.gtest';

const ivyXml = (name) => `<?xml version="1.0" encoding="UTF-8"?>
<ivy-module version="2.0" xmlns:e="http://ant.apache.org/ivy/extra">
  <info organisation="${organization}" module="${name}" revision="${version}" status="release"/>
  <publications>
    <artifact name="${name}" type="jar" ext="jar" conf="compile"/>
    <artifact e:classifier="sources" name="${name}" type="src" ext="jar" conf="sources"/>
    <artifact e:classifier="javadoc" name="${name}" type="doc" ext="jar" conf="docs"/>
    <artifact name="${name}" type="pom" ext="pom" conf="pom"/>
  </publications>
</ivy-module>
`;

const writeFile = (file, content) => {
  mkdirSync(path.dirname(file), { recursive: true });
  writeFileSync(file, content);
};
const writeJson = (file, value) => writeFile(file, `${JSON.stringify(value, null, 2)}\n`);
const readJson = (file) => JSON.parse(readFileSync(file, 'utf8'));
const git = (cwd, ...args) => execFileSync('git', ['-c', 'user.name=hx4s test', '-c', 'user.email=test@example.invalid', '-c', 'commit.gpgsign=false', ...args], { cwd, encoding: 'utf8' });

/** Creates a publish in a temporary directory with a git checkout, an ivy local repository and publish records */
async function withPublish(body) {
  const dir = await mkdtemp(path.join(os.tmpdir(), 'hx4s-publish-'));
  try {
    const root = path.join(dir, 'repo');
    for (const [file, content] of [['version.txt', '0.1.0\n'], ['package.json', '{"name":"w"}\n'], ['package-lock.json', '{"lockfileVersion":3}\n'], ['build.sbt', '\n']]) {
      writeFile(path.join(root, file), content);
    }
    git(root, 'init', '-q');
    git(root, 'config', 'core.autocrlf', 'false');
    git(root, 'add', '-A');
    git(root, 'commit', '-q', '-m', 'init');
    const ivyLocal = path.join(dir, 'ivy');
    for (const name of mavenNames) {
      const moduleDir = path.join(ivyLocal, organization, name, ...(name.startsWith('sbt-') ? ['scala_2.12', 'sbt_1.0'] : []), version);
      writeFile(path.join(moduleDir, 'ivys', 'ivy.xml'), ivyXml(name));
      for (const file of [`jars/${name}.jar`, `srcs/${name}-sources.jar`, `docs/${name}-javadoc.jar`, `poms/${name}.pom`]) writeFile(path.join(moduleDir, file), `${name} ${file}`);
    }
    const output = path.join(dir, 'local');
    writeFile(path.join(output, npmArtifact.file), 'tgz');
    const state = sourceState(root);
    const maven = mavenNames.map((name) => publishedMavenArtifact(ivyLocal, organization, name, version));
    const npm = { ...npmArtifact, sha256: sha256('tgz') };
    writeJson(path.join(output, recordFiles.sbt), { ...source(version, state), maven });
    writeJson(path.join(output, recordFiles.npm), { ...source(version, state), npm });
    writeJson(path.join(output, recordFiles.complete), { format: recordFormat, ...source(version, state), maven, npm });
    writeFile(path.join(output, recordFiles.version), `${version}\n`);
    const problems = () => verifyPublish({ root, ivyLocal, output, organization }).problems;
    const editRecord = (file, change) => {
      const record = readJson(path.join(output, file));
      change(record);
      writeJson(path.join(output, file), record);
    };
    const moduleDir = (name) => path.join(ivyLocal, ...maven.find((artifact) => artifact.name === name).path.split('/'));
    return await body({ root, output, problems, editRecord, moduleDir });
  } finally {
    await rm(dir, { recursive: true, force: true });
  }
}

const assertProblem = (problems, pattern) => assert.ok(problems.some((problem) => pattern.test(problem)), `${pattern} not in ${JSON.stringify(problems)}`);

test('ivyPublications maps the publications of ivy.xml to [type]s/[artifact](-[classifier]).[ext]', () => {
  assert.deepEqual(ivyPublications(ivyXml('m_3')), ['jars/m_3.jar', 'srcs/m_3-sources.jar', 'docs/m_3-javadoc.jar', 'poms/m_3.pom']);
});

test('mavenNames matches the modules aggregated by the root of build.sbt', () => {
  const build = readFileSync(path.join(repositoryRoot, 'build.sbt'), 'utf8');
  assert.match(build, /\.aggregate\(coreJVM, coreJS, react, codegen, sbtHx4s\)/, 'when changing aggregate, also update mavenNames in scripts/lib/publish-record.mjs');
  assert.equal(mavenNames.length, 5);
});

test('the published sources include the root package.json and package-lock.json', () => {
  assert.ok(publishedSources.includes('package.json'));
  assert.ok(publishedSources.includes('package-lock.json'));
});

test('a complete publish passes verification, and the record has ivy.xml and the publication files with SHA-256', async () => {
  await withPublish(({ output, problems }) => {
    assert.deepEqual(problems(), []);
    const plugin = readJson(path.join(output, recordFiles.complete)).maven.find((artifact) => artifact.name === 'sbt-hx4s');
    assert.equal(plugin.path, `${organization}/sbt-hx4s/scala_2.12/sbt_1.0/${version}`);
    assert.deepEqual(plugin.files.map((file) => file.path), ['ivys/ivy.xml', 'jars/sbt-hx4s.jar', 'srcs/sbt-hx4s-sources.jar', 'docs/sbt-hx4s-javadoc.jar', 'poms/sbt-hx4s.pom']);
  });
});

test('verification fails when only package-lock.json or package.json changes after publishing', async () => {
  for (const file of ['package-lock.json', 'package.json']) {
    await withPublish(({ root, problems }) => {
      writeFileSync(path.join(root, file), '{"changed":true}\n');
      assertProblem(problems(), /published sources were clean, but they have local changes/);
    });
  }
});

test('verification fails when ivy JARs, sources or poms are deleted or replaced', async () => {
  for (const [file, change] of [
    ['jars/hx4s-core_3.jar', 'delete'], ['jars/hx4s-core_3.jar', 'replace'], ['srcs/hx4s-core_3-sources.jar', 'delete'],
    ['poms/hx4s-core_3.pom', 'replace'], ['ivys/ivy.xml', 'replace'],
  ]) {
    await withPublish(({ problems, moduleDir }) => {
      const absolute = path.join(moduleDir('hx4s-core_3'), ...file.split('/'));
      if (change === 'delete') rmSync(absolute);
      else writeFileSync(absolute, 'other');
      assertProblem(problems(), change === 'delete' ? /is missing$/ : /does not match the recorded SHA-256/);
    });
  }
});

test('verification fails when the tgz is replaced', async () => {
  await withPublish(({ output, problems }) => {
    writeFileSync(path.join(output, npmArtifact.file), 'other');
    assertProblem(problems(), /does not match the recorded SHA-256/);
  });
});

test('verification fails on missing entries, empty arrays, extra entries or a mismatched artifact set in the completed record', async () => {
  const cases = [
    [(record) => delete record.maven, /has no maven/],
    [(record) => { record.maven = []; }, /lists no maven artifacts/],
    [(record) => delete record.npm, /has no npm/],
    [(record) => delete record.format, /has no format/],
    [(record) => { record.format = 2; }, /unknown record format 2/],
    [(record) => delete record.dirty, /has no dirty/],
    [(record) => { record.extra = 1; }, /unexpected field extra/],
    [(record) => { record.maven.pop(); }, /has no maven artifact sbt-hx4s/],
    [(record) => { record.maven.push({ ...record.maven[0], name: 'other_3' }); }, /unexpected maven artifacts other_3/],
    [(record) => { record.maven.push(record.maven[0]); }, /more than once/],
    [(record) => { record.maven[0].files = []; }, /lists no files/],
    [(record) => { record.maven[0].files = record.maven[0].files.filter((file) => !file.path.startsWith('jars/')); }, /does not list a jar/],
    [(record) => { record.maven[0].files[0].path = '../x'; }, /invalid file path/],
    [(record) => { record.maven[0].extra = 1; }, /unexpected field extra/],
    [(record) => { record.maven[0].organization = 'other'; }, /has organization "other"/],
    [(record) => { record.npm.name = 'other'; }, /npm artifact is "other"/],
    [(record) => { record.npm.file = 'other.tgz'; }, /npm artifact file is "other.tgz"/],
    [(record) => delete record.npm.sha256, /npm artifact has no sha256/],
  ];
  for (const [change, pattern] of cases) {
    await withPublish(({ problems, editRecord }) => {
      editRecord(recordFiles.complete, change);
      assertProblem(problems(), pattern);
    });
  }
});

test('verification fails when the artifacts of a partial record differ from the completed record', async () => {
  for (const [file, change, pattern] of [
    [recordFiles.sbt, (record) => { record.maven[0].files[1].sha256 = '0'.repeat(64); }, /the sbt record does not belong/],
    [recordFiles.sbt, (record) => delete record.maven, /the sbt record does not belong/],
    [recordFiles.npm, (record) => { record.npm.sha256 = '0'.repeat(64); }, /the npm record does not belong/],
  ]) {
    await withPublish(({ problems, editRecord }) => {
      editRecord(file, change);
      assertProblem(problems(), pattern);
    });
  }
});
