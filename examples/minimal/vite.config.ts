import { defineConfig } from "vite";
import buildCloudflareWorkers from "@hono/vite-build/cloudflare-workers";
import hx4s from "hx4s-vite";

// client: dist/client/static/* (served with Workers Static Assets), server: dist/server/index.js (Worker)
export default defineConfig(({ mode }) => {
  const plugins = hx4s({ mode, sbt: { cwd: import.meta.dirname } });
  if (mode === "client") {
    return { plugins, build: { outDir: "dist/client" } };
  }
  return { plugins: [...plugins, buildCloudflareWorkers({ outputDir: "dist/server" })] };
});
