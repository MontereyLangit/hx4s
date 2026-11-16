// hx4s の版の定義。リポジトリ直下の version.txt に次に出す版（X.Y.Z）を 1 つだけ書き、ほかはそこから作る。
//   sbt の既定の版: <base>-SNAPSHOT（build.sbt が読む。Maven の開発版の慣習で、npm には使わない）
//   ローカル公開の版: <base>-local.g<commit 10 桁>[.dirty-<内容のハッシュ 8 桁>]（publish-local.mjs）
//   vite/package.json の version: <base>（npm の版。tests/version.test.js が食い違いを検出する）
import { readFileSync } from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

export const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..", "..");
export const versionFile = path.join(root, "version.txt");

/** version.txt の版。X.Y.Z の形でなければ例外を投げる */
export function baseVersion(file = versionFile) {
  const text = readFileSync(file, "utf8").trim();
  if (!/^\d+\.\d+\.\d+$/.test(text)) throw new Error(`${file} must contain a version like 0.1.0, found ${JSON.stringify(text)}`);
  return text;
}

/** ローカル公開の既定の版。数字だけの識別子は semver で先頭の 0 を許さないので、ハッシュは dirty- に続けて 1 つの識別子にする */
export function localVersion(base, commit, dirtyHash) {
  return `${base}-local.g${commit.slice(0, 10)}${dirtyHash === null ? "" : `.dirty-${dirtyHash}`}`;
}
