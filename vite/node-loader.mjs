// Reproduces on Node, with module hooks, the resolution Vite performs at build time (for Scala.js tests and SSR verification).
// Other specifiers go to Node's default resolution. Runtime-specific specifiers (such as cloudflare:workers) are handled by the user's loader.
//   scalajs:<module>.js -> $HX4S_CLIENT_JS_DIR/<module>.js (the Scala.js link output of the client)
//   hono/jsx(-runtime) inside honox -> react (the same replacement as islandComponents.reactApiImportSource of honox())
import { pathToFileURL } from "node:url";
import path from "node:path";

export async function resolve(specifier, context, nextResolve) {
  const module = /^scalajs:([A-Za-z0-9_][A-Za-z0-9_.-]*\.js)$/.exec(specifier)?.[1];
  if (module) {
    const dir = process.env.HX4S_CLIENT_JS_DIR;
    if (!dir) throw new Error("HX4S_CLIENT_JS_DIR is not set (the Scala.js link output directory of the client project)");
    return { url: pathToFileURL(path.join(dir, module)).href, shortCircuit: true };
  }
  if (context.parentURL?.includes("/node_modules/honox/")) {
    if (specifier === "hono/jsx") return nextResolve("react", context);
    if (specifier === "hono/jsx/jsx-runtime") return nextResolve("react/jsx-runtime", context);
  }
  return nextResolve(specifier, context);
}
