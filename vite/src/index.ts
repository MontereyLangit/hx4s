import type { Plugin, PluginOption } from "vite";
import honox from "honox/vite";
import scalaJSPlugin from "@scala-js/vite-plugin-scalajs";

type HonoxOptions = NonNullable<Parameters<typeof honox>[0]>;

/** hx4s() の設定。省いた値は HonoX と hx4s の既定（server / client、app/client.ts）にする */
export interface Hx4sOptions {
  /** Vite の mode。"client" ならブラウザ向け、それ以外は SSR（サーバー）向けの設定を返す */
  mode: string;
  sbt?: {
    /** sbt を実行するディレクトリ。既定は process.cwd() */
    cwd?: string;
  };
  server?: {
    /** @route を持つ sbt プロジェクトの ID。既定は "server" */
    projectID?: string;
  };
  client?: {
    /** @islands を持つ sbt プロジェクトの ID。既定は "client" */
    projectID?: string;
    /** client のリンク成果物の @JSExportTopLevel moduleID。既定は "client" */
    module?: string;
    /** client ビルドの入口。既定は "./app/client.ts" */
    entry?: string | string[];
    /** SSR で client のリンク成果物を読む仮想モジュールの接頭辞。既定は "scalajs-client" */
    uriPrefix?: string;
    /** client ビルドの出力名。省いた項目は static/client.js・static/assets/[name]-[hash].js・static/assets/[name].[ext] */
    output?: {
      entryFileNames?: string;
      chunkFileNames?: string;
      assetFileNames?: string;
    };
  };
  /** honox() にそのまま渡す追加の設定。islandComponents.reactApiImportSource の既定は "react" */
  honox?: HonoxOptions;
}

/** sbt の Scala.js プロジェクトをリンクするプラグイン。複数のプロジェクトの sbt を同時に起動しないよう buildStart を直列にする */
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

/** Scala.js の server / client プロジェクトを HonoX に載せる Vite プラグイン群を返す */
export default function hx4s(options: Hx4sOptions): PluginOption[] {
  const cwd = options.sbt?.cwd ?? process.cwd();
  const serverProjectID = options.server?.projectID ?? "server";
  const clientProjectID = options.client?.projectID ?? "client";
  const clientModule = `${options.client?.module ?? "client"}.js`;
  const clientUriPrefix = options.client?.uriPrefix ?? "scalajs-client";

  if (options.mode === "client") {
    const entry = options.client?.entry ?? "./app/client.ts";
    const output = options.client?.output;
    const clientBuild: Plugin = {
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
    return [scalaProject(cwd, clientProjectID), clientBuild];
  }

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
    scalaProject(cwd, clientProjectID, clientUriPrefix),
    clientAlias,
  ];
}
