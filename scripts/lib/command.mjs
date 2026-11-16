// scripts/ から npm・sbt を起動する。シェルを介さないので、作業ディレクトリや引数に空白・& があっても壊れない。
//   npm: 実行中の Node で npm-cli.js を直接起動する（見つからなければ下のバッチの扱い）
//   sbt: POSIX では sbt を直接、Windows では sbt.bat を cmd.exe に引用済みの 1 行で渡す
// Windows のバッチ（.bat / .cmd）は CreateProcess で直接起動できず、Node も shell なしでは拒否するので cmd.exe を使う。
import { spawnSync } from "node:child_process";
import { existsSync } from "node:fs";
import path from "node:path";

const windows = process.platform === "win32";

/** cmd.exe /s /c の 1 行に入れる引数を引用する。cmd とバッチの展開で意味が変わる文字（" % ! ^ 改行）は拒否する */
export function quoteForCmd(arg) {
  if (/["%!^\r\n\0]/.test(arg)) throw new Error(`argument cannot be passed through cmd.exe safely: ${JSON.stringify(arg)}`);
  // 閉じ引用符の直前の \ は、受け取る側（MSVC の引数解析）で引用符のエスケープと読まれるので倍にする
  return `"${arg.replace(/(\\+)$/, "$1$1")}"`;
}

/** PATH から name を探して絶対パスを返す。無ければ name のまま */
function onPath(name) {
  const dirs = (process.env.PATH ?? "").split(path.delimiter).filter(Boolean);
  return dirs.map((dir) => path.join(dir, name)).find((file) => existsSync(file)) ?? name;
}

/** Windows のバッチを cmd.exe 経由で起動する。spawnSync の戻り値をそのまま返す */
export function spawnBatch(file, args, options = {}) {
  const line = [file, ...args].map(quoteForCmd).join(" ");
  return spawnSync(process.env.ComSpec ?? "cmd.exe", ["/d", "/s", "/c", `"${line}"`], { ...options, windowsVerbatimArguments: true });
}

/** 実行中の Node に付属する npm-cli.js（npm から起動されていればそれ）。無ければ undefined */
export function npmCliPath() {
  const candidates = [
    process.env.npm_execpath,
    windows
      ? path.join(path.dirname(process.execPath), "node_modules", "npm", "bin", "npm-cli.js")
      : path.join(path.dirname(process.execPath), "..", "lib", "node_modules", "npm", "bin", "npm-cli.js"),
  ];
  return candidates.find((file) => file !== undefined && path.basename(file) === "npm-cli.js" && existsSync(file));
}

/** npm か sbt をシェルなしで起動し、spawnSync の戻り値を返す */
export function spawnTool(tool, args, options = {}) {
  if (tool === "npm") {
    const cli = npmCliPath();
    if (cli !== undefined) return spawnSync(process.execPath, [cli, ...args], options);
    return windows ? spawnBatch(onPath("npm.cmd"), args, options) : spawnSync("npm", args, options);
  }
  // 引用したバッチ名を cmd に PATH から探させると、バッチ内の %~dp0 が作業ディレクトリになるので、絶対パスで渡す
  if (tool === "sbt") return windows ? spawnBatch(onPath("sbt.bat"), args, options) : spawnSync("sbt", args, options);
  throw new Error(`unknown tool ${tool}`);
}

/** npm か sbt を出力をそのまま流して起動し、失敗したら例外にする */
export function runTool(tool, args, options = {}) {
  console.log(`> ${tool} ${args.join(" ")}`);
  const result = spawnTool(tool, args, { stdio: "inherit", ...options });
  if (result.error) throw result.error;
  if (result.status !== 0) throw new Error(`${tool} ${args.join(" ")} failed with exit code ${result.status}`);
}
