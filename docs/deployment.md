# Deployment

The only runtime on which hx4s has been verified is **Cloudflare Workers** (local workerd via `wrangler dev`). The hx4s modules don't depend on Cloudflare (see "Relationship with the runtime" below),
but other runtimes have not been verified. Production deployment (`wrangler deploy`) is not part of hx4s's verification either.

## Cloudflare Workers

The setup of `examples/minimal`.

```jsonc
// wrangler.jsonc
{
  "$schema": "node_modules/wrangler/config-schema.json",
  "name": "hx4s-minimal",
  "main": "dist/server/index.js",
  "compatibility_date": "2025-09-01",
  // react-dom/server imports node:async_hooks
  "compatibility_flags": ["nodejs_compat"],
  // Serve only the client output as static assets (the Worker code in dist/server is not exposed)
  "assets": {
    "directory": "dist/client"
  }
}
```

```ts
// vite.config.ts (the runtime adapter is chosen by the user)
import buildCloudflareWorkers from "@hono/vite-build/cloudflare-workers";
```

| Setting | Reason |
| --- | --- |
| `main` = `dist/server/index.js` | The Worker that `vite build` (server mode) creates with `@hono/vite-build/cloudflare-workers` |
| `assets.directory` = `dist/client` | Output of `vite build --mode client`. Serves `static/client.js` and `static/assets/*` with Workers Static Assets |
| `nodejs_compat` | `react-dom/server` imports `node:async_hooks`. Without it, the built Worker doesn't start on workerd (invisible in tests on Node; found by E2E) |

### Scope of static serving

Only `dist/client` is served statically; `dist/server` is not included. The E2E tests of `examples/minimal` check that:

- `/static/client.js` returns 200 (the `clientScriptSrc` of `_renderer.tsx` is `/static/client.js`)
- `/index.js` returns 404, so the Worker code is not served

When you change output names with `client.output` of `hx4s()`, update `clientScriptSrc` in `_renderer.tsx` too.

### Building and running locally

```bash
npm run build   # hx4sGenerate → client build → Worker build
npx wrangler dev   # or npm run dev (build, then wrangler dev)
```

When publishing with `wrangler deploy`, set `name`, `compatibility_date` and the account settings in `wrangler.jsonc` to your own. This step is not verified by hx4s;
follow Cloudflare's documentation for Workers settings (routes, domains, environment variables, Bindings).

### Notes

- **Island props are sent to the browser.** They go into the HTML as `data-serialized-props`, so don't put secrets in props. Secrets should be read only by server handlers from `c.env` (Bindings, Secrets)
- The client entry has a fixed name by default (`static/client.js`), so depending on cache settings an old client can linger and drift from the HTML version. The validation in `hx4s-vite/client` leaves mismatched islands unhydrated, as SSR HTML ([ssr-hydration.md](ssr-hydration.md)).
  Cache control for static assets belongs to the runtime settings; hx4s doesn't decide it

## Relationship with the runtime

| Module | Dependency on Cloudflare |
| --- | --- |
| `hx4s-core`, `hx4s-react`, `hx4s-codegen`, `sbt-hx4s`, `hx4s-vite` | None |
| `Context[Env, R]` | `Env` is a type parameter. The type of Cloudflare Bindings is defined by the user ([routing.md](routing.md)) |
| `ExecutionContext` | A minimal facade with only `waitUntil` and `passThroughOnException`. `executionCtxOption` exists for runtimes that don't provide it |
| `examples/minimal` | Depends on Cloudflare (`wrangler.jsonc`, `@hono/vite-build/cloudflare-workers`, starting E2E) |

Helpers for Cloudflare-specific features such as Durable Objects, R2 and KV (e.g. generating DO RPC stubs) are not included in hx4s. There is a plan to consider them as `hx4s-cloudflare` in the future
(P2 in [hx4s/roadmap.md](hx4s/roadmap.md), [hx4s/adr/0005-core-independent-of-cloudflare.md](hx4s/adr/0005-core-independent-of-cloudflare.md)).

## Other runtimes

Running on Node, Deno, Bun, Vercel and so on would mean replacing `buildCloudflareWorkers(...)` in `vite.config.ts` with another `@hono/vite-build` adapter, and the wrangler settings with the runtime's settings.
**hx4s has not been verified on these.** We don't claim they work. If you try, consider the following:

- `app/routes/_renderer.tsx` and `app/client.ts` are a standard HonoX setup and don't depend on the runtime
- How the runtime serves the client statically (`static/` in `dist/client` must be served at `/static/`)
- `executionCtx` of `Context` throws if the runtime doesn't provide it. Use `executionCtxOption`
