# デプロイ

hx4s が動作を確かめた実行基盤は **Cloudflare Workers**（ローカルの workerd。`wrangler dev`）だけ。hx4s のモジュールは Cloudflare に依存しない（下の「実行基盤との関係」）が、
ほかの基盤は検証していない。本番へのデプロイ（`wrangler deploy`）も、hx4s の検証には含まれていない。

## Cloudflare Workers

`examples/minimal` の構成。

```jsonc
// wrangler.jsonc
{
  "$schema": "node_modules/wrangler/config-schema.json",
  "name": "hx4s-minimal",
  "main": "dist/server/index.js",
  "compatibility_date": "2025-09-01",
  // react-dom/server が node:async_hooks を import する
  "compatibility_flags": ["nodejs_compat"],
  // client の成果物だけを静的配信する（dist/server の Worker のコードは公開しない）
  "assets": {
    "directory": "dist/client"
  }
}
```

```ts
// vite.config.ts（実行基盤のアダプタは利用側が選ぶ）
import buildCloudflareWorkers from "@hono/vite-build/cloudflare-workers";
```

| 設定 | 理由 |
| --- | --- |
| `main` = `dist/server/index.js` | `vite build`（server モード）が `@hono/vite-build/cloudflare-workers` で作る Worker |
| `assets.directory` = `dist/client` | `vite build --mode client` の出力。`static/client.js` と `static/assets/*` を Workers Static Assets で配信する |
| `nodejs_compat` | `react-dom/server` が `node:async_hooks` を import する。無いと、ビルドした Worker が workerd で起動しない（Node 上のテストでは見えず、E2E で見つかった） |

### 静的配信の範囲

静的に配信するのは `dist/client` だけ。`dist/server` は含めない。`examples/minimal` の E2E は次を確かめている。

- `/static/client.js` が 200 で返る（`_renderer.tsx` の `clientScriptSrc` が `/static/client.js`）
- `/index.js` が 404 で、Worker のコードは配信されない

`hx4s()` の `client.output` で出力名を変えたときは、`_renderer.tsx` の `clientScriptSrc` も合わせる。

### ビルドとローカル実行

```bash
npm run build   # hx4sGenerate → client ビルド → Worker ビルド
npx wrangler dev   # または npm run dev（build してから wrangler dev）
```

`wrangler deploy` で公開するときは、`wrangler.jsonc` の `name`・`compatibility_date`・アカウントの設定を自分のものにする。hx4s としては検証していない手順で、
Workers の設定（ルート、ドメイン、環境変数、Bindings）は Cloudflare の文書に従う。

### 注意

- **island の props はブラウザに送られる。** `data-serialized-props` として HTML に入るので、秘匿値を props に入れない。秘匿値は `c.env`（Bindings、Secrets）から server のハンドラだけが読む
- client の入口は既定で固定名（`static/client.js`）のため、キャッシュの設定によっては古い client が残り、HTML との世代がずれる。`hx4s-vite/client` の検証は不一致の island を hydrate せず SSR の HTML のまま残す（[ssr-hydration.md](ssr-hydration.md)）。
  静的アセットのキャッシュ制御は実行基盤の設定で、hx4s は決めない

## 実行基盤との関係

| モジュール | Cloudflare への依存 |
| --- | --- |
| `hx4s-core`・`hx4s-react`・`hx4s-codegen`・`sbt-hx4s`・`hx4s-vite` | 無い |
| `Context[Env, R]` | `Env` は型引数。Cloudflare の Bindings の型は利用側が定義する（[routing.md](routing.md)） |
| `ExecutionContext` | `waitUntil` と `passThroughOnException` だけを持つ最小の facade。渡さない基盤向けに `executionCtxOption` がある |
| `examples/minimal` | Cloudflare に依存する（`wrangler.jsonc`、`@hono/vite-build/cloudflare-workers`、E2E の起動） |

Durable Objects・R2・KV などの Cloudflare 固有の機能向けの補助（DO の RPC スタブの生成など）は hx4s に含まれない。将来 `hx4s-cloudflare` として検討する構想がある
（[hx4s/roadmap.md](hx4s/roadmap.md) の P2、[hx4s/adr/0005-core-independent-of-cloudflare.md](hx4s/adr/0005-core-independent-of-cloudflare.md)）。

## ほかの実行基盤

Node・Deno・Bun・Vercel などで動かすには、`vite.config.ts` の `buildCloudflareWorkers(...)` を `@hono/vite-build` の別のアダプタに替え、wrangler の設定を基盤の設定に替えることになる。
**hx4s はこれらで検証していない。** 動くとは書かない。試す場合は次の点が検討対象になる。

- `app/routes/_renderer.tsx` と `app/client.ts` は HonoX の標準の構成で、基盤に依存しない
- client の静的配信を基盤側でどう行うか（`dist/client` の `static/` を `/static/` で配信する必要がある）
- `Context` の `executionCtx` は基盤が渡さなければ例外になる。`executionCtxOption` を使う
