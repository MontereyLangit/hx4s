# ADR 0005: Core は Cloudflare 固有 API に依存しない

- 状態: 承認（2026-11-16、Phase 0）
- 関連: [module-boundaries.md](../module-boundaries.md) 2 章、[extraction-plan.md](../extraction-plan.md) 4 章

## 背景

3ch は Cloudflare Workers / Durable Objects / R2 / KV の上で動く。連携基盤の中にも Cloudflare 固有の部分が混ざっている。

- `HonoContextFacade.env` の型が `WorkerEnv`（Bindings）。
- `vite.config.ts` が `@hono/vite-build/cloudflare-workers` を使い、`entryContentAfterHooks` で DO クラスを再 export する。
- テスト用 loader が `cloudflare:workers` をスタブに差し替える。
- `codegen` が DO の公開メソッドから TS 型（`GenerateDurableObjectTypes`）と RPC スタブ（`GenerateDurableObjectStubs`）を生成する。

Hono と HonoX は Node・Deno・Bun・Cloudflare など複数の実行基盤で動く。hx4s が Cloudflare に縛られると、利用者の範囲が狭まり、
`examples/minimal` のテストにも Cloudflare の環境が要る。

## 決定

hx4s の P0 のモジュール（core / react / codegen / sbt-hx4s / hx4s-vite）は Cloudflare 固有 API に依存しない。次は 3ch に残す。

- Durable Object の export（`entryContentAfterHooks`）と `@hono/vite-build/cloudflare-workers` の設定
- Bindings の型（`WorkerEnv` など）
- `cloudflare:workers` のテスト用スタブ
- DO の RPC スタブ / TS 型の生成（`DurableObjectSources`、`GenerateDurableObject*`）
- Tailwind と Shift_JIS（実行基盤ではないが掲示板固有）

将来の `hx4s-cloudflare` アダプタ（P2）の候補としてロードマップに書くだけにする。

## 却下案

| 案 | 理由 |
| --- | --- |
| DO の生成器を最初から hx4s に入れる | DO の RPC 境界の規則（使える型、`DurableObject` を直接継承すること）は 3ch の実装に強く結び付いており、汎用化の検証に Cloudflare の環境が要る |
| Context facade の Env を `WorkerEnv` 固定のまま移す | Node 等で使えない。型引数にしても 3ch 側は型の別名を 1 行置くだけで済む |
| 実行基盤の抽象層（アダプタ trait）を hx4s に作る | 実行基盤の差は Hono と `@hono/vite-build` の各アダプタが既に吸収している。hx4s が重ねると二重になる |

## 影響

- `examples/minimal` は Node の Hono アダプタで動かし、CI に Cloudflare の環境を要しない。
- 3ch は Phase 4 以降も DO 関連の生成器とビルド設定を自前で持つ。`hx4s-vite` の `hx4s()` と `@hono/vite-build/cloudflare-workers` を並べて使う。
- `hx4s-cloudflare` を作るときは、3ch の `GenerateDurableObject*` と `docs/durable-object-stubs.md` を出発点にする。
