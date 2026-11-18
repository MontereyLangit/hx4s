// hx4s's version definition. version.txt at the repository root holds the one next version to release (X.Y.Z), and everything else derives from it.
//   sbt's default version: <base>-SNAPSHOT (read by build.sbt; Maven's convention for development versions, not used for npm)
//   Local publish version: <base>-local.g<10-character commit>[.dirty-<8-character content hash>] (publish-local.mjs)
//   version in vite/package.json: <base> (the npm version; tests/version.test.js detects mismatches)
import { readFileSync } from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

export const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..", "..");
export const versionFile = path.join(root, "version.txt");

/** The version in version.txt. Throws unless it has the form X.Y.Z */
export function baseVersion(file = versionFile) {
  const text = readFileSync(file, "utf8").trim();
  if (!/^\d+\.\d+\.\d+$/.test(text)) throw new Error(`${file} must contain a version like 0.1.0, found ${JSON.stringify(text)}`);
  return text;
}

/** The default local publish version. Semver does not allow leading zeros in numeric identifiers, so the hash follows dirty- as one identifier */
export function localVersion(base, commit, dirtyHash) {
  return `${base}-local.g${commit.slice(0, 10)}${dirtyHash === null ? "" : `.dirty-${dirtyHash}`}`;
}
