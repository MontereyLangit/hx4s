import { defineConfig } from "vite";
import buildCloudflareWorkers from "@hono/vite-build/cloudflare-workers";
import hx4s from "hx4s-vite";

// No client (islands), so no client build either. Output is dist/server/index.js (Worker)
export default defineConfig(({ mode }) => ({
  plugins: [...hx4s({ mode, client: false, sbt: { cwd: import.meta.dirname } }), buildCloudflareWorkers({ outputDir: "dist/server" })],
}));
