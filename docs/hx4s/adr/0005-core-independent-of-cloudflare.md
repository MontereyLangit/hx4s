# ADR 0005: Core does not depend on Cloudflare-specific APIs

- Status: Accepted (2026-11-16, Phase 0)
- Updated (2026-11-16, Phase 3): `examples/minimal` now runs on Cloudflare Workers (`@hono/vite-build/cloudflare-workers` and wrangler) instead of the Node adapter
  ([extraction-plan.md](../extraction-plan.md) 3-5). Only the examples' settings and test startup depend on Cloudflare; the decision that hx4s's modules don't depend on Cloudflare is unchanged
- Related: [module-boundaries.md](../module-boundaries.md) chapter 2, [extraction-plan.md](../extraction-plan.md) chapter 4

## Context

3ch runs on Cloudflare Workers / Durable Objects / R2 / KV. Cloudflare-specific parts are mixed into the integration layer too.

- The type of `HonoContextFacade.env` is `WorkerEnv` (Bindings).
- `vite.config.ts` uses `@hono/vite-build/cloudflare-workers` and re-exports DO classes through `entryContentAfterHooks`.
- The test loader replaces `cloudflare:workers` with a stub.
- `codegen` generates TS types (`GenerateDurableObjectTypes`) and RPC stubs (`GenerateDurableObjectStubs`) from the public methods of DOs.

Hono and HonoX run on several runtimes, including Node, Deno, Bun and Cloudflare. If hx4s were tied to Cloudflare, it would narrow the range of users,
and the tests of `examples/minimal` would also need a Cloudflare environment.

## Decision

hx4s's P0 modules (core / react / codegen / sbt-hx4s / hx4s-vite) don't depend on Cloudflare-specific APIs. The following stay in 3ch:

- Durable Object exports (`entryContentAfterHooks`) and the settings of `@hono/vite-build/cloudflare-workers`
- Bindings types (`WorkerEnv`, etc.)
- The test stub for `cloudflare:workers`
- Generation of DO RPC stubs / TS types (`DurableObjectSources`, `GenerateDurableObject*`)
- Tailwind and Shift_JIS (not the runtime, but specific to the bulletin board)

A future `hx4s-cloudflare` adapter (P2) is only listed on the roadmap as a candidate.

## Rejected alternatives

| Alternative | Reason |
| --- | --- |
| Put the DO generators into hx4s from the start | The rules for DO RPC boundaries (usable types, extending `DurableObject` directly) are tightly tied to 3ch's implementation, and verifying a generalization requires a Cloudflare environment |
| Move the Context facade with Env fixed to `WorkerEnv` | Unusable on Node, etc. With a type parameter, 3ch only needs a one-line type alias |
| Create a runtime abstraction layer (adapter trait) in hx4s | Runtime differences are already absorbed by Hono and the `@hono/vite-build` adapters. Adding another layer in hx4s would duplicate it |

## Consequences

- `examples/minimal` runs with Hono's Node adapter, and CI doesn't need a Cloudflare environment.
- 3ch keeps its own DO generators and build settings from Phase 4 on. It uses `hx4s()` of `hx4s-vite` alongside `@hono/vite-build/cloudflare-workers`.
- When creating `hx4s-cloudflare`, start from 3ch's `GenerateDurableObject*` and `docs/durable-object-stubs.md`.
