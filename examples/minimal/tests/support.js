import { readdirSync, readFileSync, statSync } from 'node:fs';
import path from 'node:path';
import { fileURLToPath, pathToFileURL } from 'node:url';

export const root = path.resolve(fileURLToPath(new URL('..', import.meta.url)));

/** Recursively lists the files under dir */
export function listFiles(dir) {
  return readdirSync(dir).flatMap((name) => {
    const file = path.join(dir, name);
    return statSync(file).isDirectory() ? listFiles(file) : [file];
  });
}

export function read(file) {
  return readFileSync(file, 'utf8');
}

/** Loads the built Worker on Node and calls fetch (the Workers runtime is checked with wrangler by e2e.test.js) */
export async function workerFetch(pathname, init) {
  const worker = (await import(pathToFileURL(path.join(root, 'dist/server/index.js')).href)).default;
  return worker.fetch(new Request(`http://localhost${pathname}`, init), {}, { waitUntil() {}, passThroughOnException() {} });
}

/** The client's Scala.js link output (built by vite build --mode client via sbt's fullLinkJS) */
export function linkedClientDirectory() {
  const target = path.join(root, 'client', 'target');
  const scalaDir = readdirSync(target).find((name) => name.startsWith('scala-'));
  if (!scalaDir) throw new Error('client is not linked. Run npm run build first.');
  return path.join(target, scalaDir, 'client-opt');
}
