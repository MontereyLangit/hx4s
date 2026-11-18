import type { Plugin, PluginOption } from "vite";
import honox from "honox/vite";
import scalaJSPlugin from "@scala-js/vite-plugin-scalajs";

type HonoxOptions = NonNullable<Parameters<typeof honox>[0]>;

/** Settings for the client Scala.js project and its build */
export interface Hx4sClientOptions {
  /** ID of the sbt project with @islands. Defaults to "client" */
  projectID?: string;
  /** @JSExportTopLevel moduleID of the client link output. Defaults to "client" */
  module?: string;
  /** Entry of the client build. Defaults to "./app/client.ts" */
  entry?: string | string[];
  /** Prefix of the virtual module that reads the client link output during SSR. Defaults to "scalajs-client" */
  uriPrefix?: string;
  /** Output names of the client build. Omitted entries default to static/client.js, static/assets/[name]-[hash].js and static/assets/[name].[ext] */
  output?: {
    entryFileNames?: string;
    chunkFileNames?: string;
    assetFileNames?: string;
  };
}

/** Settings of hx4s(). Omitted values take the HonoX and hx4s defaults (server / client, app/client.ts) */
export interface Hx4sOptions {
  /** Vite's mode. "client" returns settings for the browser; anything else, for SSR (server) */
  mode: string;
  sbt?: {
    /** Directory in which to run sbt. Defaults to process.cwd() */
    cwd?: string;
  };
  server?: {
    /** ID of the sbt project with @route. Defaults to "server" */
    projectID?: string;
  };
  /** false for setups without a client project (API only, SSR without islands). Skips linking and building the client */
  client?: Hx4sClientOptions | false;
  /** Extra settings passed as-is to honox(). With a client, islandComponents.reactApiImportSource defaults to "react" */
  honox?: HonoxOptions;
}

/** A plugin that links an sbt Scala.js project. Serializes buildStart so that sbt is not started for several projects at once */
export function scalaProject(cwd: string, projectID: string, uriPrefix?: string): Plugin {
  const plugin = scalaJSPlugin({ cwd, projectID, uriPrefix });
  const buildStart = plugin.buildStart;
  if (buildStart) {
    plugin.buildStart = {
      sequential: true,
      handler: typeof buildStart === "function" ? buildStart : buildStart.handler,
    };
  }
  return plugin;
}

/** Returns the Vite plugins that put the Scala.js server / client projects on HonoX */
export default function hx4s(options: Hx4sOptions): PluginOption[] {
  const cwd = options.sbt?.cwd ?? process.cwd();
  const serverProjectID = options.server?.projectID ?? "server";
  const client = options.client === false ? undefined : (options.client ?? {});

  if (options.mode === "client") {
    if (client === undefined) {
      throw new Error('hx4s({ client: false }) has no client build: run "vite build" without "--mode client"');
    }
    return [scalaProject(cwd, client.projectID ?? "client"), clientBuild(client)];
  }
  if (client === undefined) {
    // No islands, so no React settings are added (honox's defaults stay, so it builds without React too)
    return [honox(options.honox), scalaProject(cwd, serverProjectID)];
  }

  const clientModule = `${client.module ?? "client"}.js`;
  const clientUriPrefix = client.uriPrefix ?? "scalajs-client";
  const honoxOptions: HonoxOptions = {
    ...options.honox,
    islandComponents: { reactApiImportSource: "react", ...options.honox?.islandComponents },
  };
  const clientAlias: Plugin = {
    name: "hx4s:client-alias",
    config: () => ({ resolve: { alias: { [`scalajs:${clientModule}`]: `${clientUriPrefix}:${clientModule}` } } }),
  };
  return [
    honox(honoxOptions),
    scalaProject(cwd, serverProjectID),
    scalaProject(cwd, client.projectID ?? "client", clientUriPrefix),
    clientAlias,
  ];
}

/** A plugin that sets the entry and output names of the client build */
function clientBuild(client: Hx4sClientOptions): Plugin {
  const entry = client.entry ?? "./app/client.ts";
  const output = client.output;
  return {
    name: "hx4s:client-build",
    config: () => ({
      build: {
        rollupOptions: {
          input: Array.isArray(entry) ? entry : [entry],
          output: {
            entryFileNames: output?.entryFileNames ?? "static/client.js",
            chunkFileNames: output?.chunkFileNames ?? "static/assets/[name]-[hash].js",
            assetFileNames: output?.assetFileNames ?? "static/assets/[name].[ext]",
          },
        },
        emptyOutDir: false,
      },
    }),
  };
}
