// Starts npm and sbt from scripts/. No shell is involved, so spaces and & in the working directory or arguments do not break it.
//   npm: starts npm-cli.js directly with the running Node (falls back to the batch handling below if not found)
//   sbt: starts sbt directly on POSIX; on Windows passes sbt.bat to cmd.exe as one pre-quoted line
// Windows batch files (.bat / .cmd) cannot be started directly with CreateProcess, and Node refuses them without a shell, so cmd.exe is used.
import { spawnSync } from "node:child_process";
import { existsSync } from "node:fs";
import path from "node:path";

const windows = process.platform === "win32";

/** Quotes an argument for a cmd.exe /s /c line. Rejects characters whose meaning changes under cmd and batch expansion (" % ! ^ newline) */
export function quoteForCmd(arg) {
  if (/["%!^\r\n\0]/.test(arg)) throw new Error(`argument cannot be passed through cmd.exe safely: ${JSON.stringify(arg)}`);
  // A \ right before the closing quote is read as escaping the quote by the receiver (MSVC argument parsing), so double it
  return `"${arg.replace(/(\\+)$/, "$1$1")}"`;
}

/** Finds name on PATH and returns the absolute path, or name itself if not found */
function onPath(name) {
  const dirs = (process.env.PATH ?? "").split(path.delimiter).filter(Boolean);
  return dirs.map((dir) => path.join(dir, name)).find((file) => existsSync(file)) ?? name;
}

/** Starts a Windows batch file through cmd.exe. Returns the result of spawnSync as-is */
export function spawnBatch(file, args, options = {}) {
  const line = [file, ...args].map(quoteForCmd).join(" ");
  return spawnSync(process.env.ComSpec ?? "cmd.exe", ["/d", "/s", "/c", `"${line}"`], { ...options, windowsVerbatimArguments: true });
}

/** The npm-cli.js bundled with the running Node (or the one that started us, if started from npm). undefined if none */
export function npmCliPath() {
  const candidates = [
    process.env.npm_execpath,
    windows
      ? path.join(path.dirname(process.execPath), "node_modules", "npm", "bin", "npm-cli.js")
      : path.join(path.dirname(process.execPath), "..", "lib", "node_modules", "npm", "bin", "npm-cli.js"),
  ];
  return candidates.find((file) => file !== undefined && path.basename(file) === "npm-cli.js" && existsSync(file));
}

/** Starts npm or sbt without a shell and returns the result of spawnSync */
export function spawnTool(tool, args, options = {}) {
  if (tool === "npm") {
    const cli = npmCliPath();
    if (cli !== undefined) return spawnSync(process.execPath, [cli, ...args], options);
    return windows ? spawnBatch(onPath("npm.cmd"), args, options) : spawnSync("npm", args, options);
  }
  // If cmd searches PATH for a quoted batch name, %~dp0 inside the batch becomes the working directory, so pass an absolute path
  if (tool === "sbt") return windows ? spawnBatch(onPath("sbt.bat"), args, options) : spawnSync("sbt", args, options);
  throw new Error(`unknown tool ${tool}`);
}

/** Starts npm or sbt streaming its output, and throws on failure */
export function runTool(tool, args, options = {}) {
  console.log(`> ${tool} ${args.join(" ")}`);
  const result = spawnTool(tool, args, { stdio: "inherit", ...options });
  if (result.error) throw result.error;
  if (result.status !== 0) throw new Error(`${tool} ${args.join(" ")} failed with exit code ${result.status}`);
}
