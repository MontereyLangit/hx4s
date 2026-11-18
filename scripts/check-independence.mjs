// Checks that hx4s does not depend on the bulletin board project it was extracted from.
//   1. Files in the repository do not contain the original package name or the old project name (except the history in docs/hx4s/ and ja/docs/hx4s/, and LICENSE / NOTICE)
//   2. examples/ does not reference hx4s's source tree through sbt ProjectRefs or relative imports (resolves only published artifacts)
// On errors, prints the list and exits with code 1.
import { execFileSync } from "node:child_process";
import { readFileSync } from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..");
// Build the words from parts so that this file itself does not match
const forbidden = [["io._", "3ch"], ["ch", "3s"], ["_", "3ch"]].map((parts) => parts.join(""));
const excluded = [/^(ja\/)?docs\/hx4s\//, /^LICENSE$/, /^NOTICE$/];
const exampleRules = [
  { pattern: /\b(ProjectRef|RootProject)\s*\(/, reason: "examples must resolve hx4s from published artifacts, not sbt project references" },
  { pattern: /\.\.\/\.\.\/(modules|sbt-plugin|vite\/src)\b/, reason: "examples must not import hx4s sources by relative path" },
  { pattern: /(?<![A-Za-z0-9])[A-Za-z]:[\\/](?![\\/])/, reason: "examples must not refer to absolute local paths" },
];
const binary = /\.(png|jpe?g|gif|ico|jar|tgz|gz|zip|woff2?)$/i;

export function findProblems() {
  // Start git without a shell, and use -z to receive paths with spaces or non-ASCII characters unquoted
  const files = execFileSync("git", ["ls-files", "-z", "--cached", "--others", "--exclude-standard"], { cwd: root, encoding: "utf8" })
    .split("\0").filter(Boolean).filter((file) => !binary.test(file));
  const problems = [];
  for (const file of files) {
    let text;
    try {
      text = readFileSync(path.join(root, file), "utf8");
    } catch {
      continue; // A deleted file not in the working tree
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
