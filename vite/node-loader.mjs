// Reproduces on Node, with module hooks, the resolution Vite performs at build time (for Scala.js tests and SSR verification).
// Other specifiers go to Node's default resolution. Runtime-specific specifiers (such as cloudflare:workers) are handled by the user's loader.
//   scalajs:client.js  -> $HX4S_CLIENT_JS_DIR/client.js (the Scala.js link output of the client)
//   hono/jsx(-runtime) inside honox -> react (the same replacement as islandComponents.reactApiImportSource of honox())
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
