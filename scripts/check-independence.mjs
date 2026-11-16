// hx4s が移動元の掲示板プロジェクトに依存していないことを確かめる。
//   1. リポジトリ内のファイルに移動元のパッケージ名・旧プロジェクト名が無い（docs/hx4s/ の経緯説明と LICENSE / NOTICE を除く）
//   2. examples/ が hx4s のソースツリーを sbt の ProjectRef や相対 import で参照していない（公開物だけで解決する）
// 誤りがあれば一覧を出して終了コード 1 で終わる。
import { execFileSync } from "node:child_process";
import { readFileSync } from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..");
// このファイル自身に一致しないよう、語を分けて組み立てる
const forbidden = [["io._", "3ch"], ["ch", "3s"], ["_", "3ch"]].map((parts) => parts.join(""));
const excluded = [/^docs\/hx4s\//, /^LICENSE$/, /^NOTICE$/];
const exampleRules = [
  { pattern: /\b(ProjectRef|RootProject)\s*\(/, reason: "examples must resolve hx4s from published artifacts, not sbt project references" },
  { pattern: /\.\.\/\.\.\/(modules|sbt-plugin|vite\/src)\b/, reason: "examples must not import hx4s sources by relative path" },
  { pattern: /(?<![A-Za-z0-9])[A-Za-z]:[\\/](?![\\/])/, reason: "examples must not refer to absolute local paths" },
];
const binary = /\.(png|jpe?g|gif|ico|jar|tgz|gz|zip|woff2?)$/i;

export function findProblems() {
  const files = execFileSync("git", ["ls-files", "--cached", "--others", "--exclude-standard"], { cwd: root, encoding: "utf8" })
    .split("\n").map((line) => line.trim()).filter(Boolean).filter((file) => !binary.test(file));
  const problems = [];
  for (const file of files) {
    let text;
    try {
      text = readFileSync(path.join(root, file), "utf8");
    } catch {
      continue; // 削除済みで作業ツリーに無いファイル
    }
    if (!excluded.some((rule) => rule.test(file))) {
      const lower = text.toLowerCase();
      for (const word of forbidden) {
        if (lower.includes(word.toLowerCase())) problems.push(`${file}: contains "${word}"`);
      }
    }
    if (file.startsWith("examples/")) {
      for (const rule of exampleRules) {
        if (rule.pattern.test(text)) problems.push(`${file}: ${rule.reason}`);
      }
    }
  }
  return problems;
}

if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  const problems = findProblems();
  if (problems.length > 0) {
    for (const problem of problems) console.error(problem);
    process.exit(1);
  }
  console.log("independence check passed");
}
