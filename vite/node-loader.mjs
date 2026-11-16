// Node の module hooks で、Vite がビルド時に行う解決を Node 上で再現する（Scala.js のテストや SSR の検証用）。
// それ以外の指定子は Node 既定の解決へ委ねる。実行基盤固有の指定子（cloudflare:workers など）は利用側の loader で扱う。
//   scalajs:client.js  -> $HX4S_CLIENT_JS_DIR/client.js（client の Scala.js リンク成果物）
//   honox 内の hono/jsx(-runtime) -> react（honox() の islandComponents.reactApiImportSource と同じ置換）
import { pathToFileURL } from "node:url";
import path from "node:path";

export async function resolve(specifier, context, nextResolve) {
  if (specifier === "scalajs:client.js") {
    const dir = process.env.HX4S_CLIENT_JS_DIR;
    if (!dir) throw new Error("HX4S_CLIENT_JS_DIR is not set (the output directory of the client project's fastLinkJS)");
    return { url: pathToFileURL(path.join(dir, "client.js")).href, shortCircuit: true };
  }
  if (context.parentURL?.includes("/node_modules/honox/")) {
    if (specifier === "hono/jsx") return nextResolve("react", context);
    if (specifier === "hono/jsx/jsx-runtime") return nextResolve("react/jsx-runtime", context);
  }
  return nextResolve(specifier, context);
}
