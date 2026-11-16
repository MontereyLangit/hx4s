import { readdirSync, readFileSync, statSync } from 'node:fs';
import path from 'node:path';
import { fileURLToPath, pathToFileURL } from 'node:url';

export const root = path.resolve(fileURLToPath(new URL('..', import.meta.url)));

/** dir 配下のファイルを再帰で列挙する */
export function listFiles(dir) {
  return readdirSync(dir).flatMap((name) => {
    const file = path.join(dir, name);
    return statSync(file).isDirectory() ? listFiles(file) : [file];
  });
}

export function read(file) {
  return readFileSync(file, 'utf8');
}

/** ビルドした Worker を Node で読み込み、fetch を呼ぶ（Workers の実行時は e2e.test.js が wrangler で確かめる） */
export async function workerFetch(pathname, init) {
  const worker = (await import(pathToFileURL(path.join(root, 'dist/server/index.js')).href)).default;
  return worker.fetch(new Request(`http://localhost${pathname}`, init), {}, { waitUntil() {}, passThroughOnException() {} });
}

/** client の Scala.js のリンク成果物（vite build --mode client が sbt の fullLinkJS で作る） */
export function linkedClientModule() {
  const target = path.join(root, 'client', 'target');
  const scalaDir = readdirSync(target).find((name) => name.startsWith('scala-'));
  if (!scalaDir) throw new Error('client is not linked. Run npm run build first.');
  return path.join(target, scalaDir, 'client-opt', 'client.js');
}
