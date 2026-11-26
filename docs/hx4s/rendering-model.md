# Rendering model

## 1. Where each rendering mode stands

| Mode | Position | State in 3ch | State in hx4s |
| --- | --- | --- | --- |
| SSR (static) | HTML that is not hydrated. Dedicated-browser compatibility and Shift_JIS responses belong here | Implemented. Existing screens through `publicHandler`, `RendererShell.legacyReact` / `createSjisResponse` | No special support needed (can be written with scalajs-react's `renderToStaticMarkup` and Hono's Response). Shift_JIS stays in 3ch |
| SSR + Islands | The default mode. Pages are SSR'd, and only the interactive parts are hydrated as islands | Implemented. The board top (`site-header`, `board-details`, `thread-list`), auth and account screens | The center of P0. Not implemented |
| SSR + Full Hydration | The whole page is one island | Not implemented | Planned (P2). See 1.2 below |
| SPA + API | A static HTML shell + client-side routing + a JSON API | Not implemented (turning the admin screen into an SPA is planned in `docs/admin-spa-auth-plan.md`, not started) | Planned (considered together with the P1 Client Router) |
| API-only | Handlers that don't return HTML | Implemented (the `/auth/*` APIs, dat / subject.txt) | Can be written with generated `@route` routes. `hx4s({ client: false })` gives a setup without a client or React (`examples/api-only`, [api.md](../api.md)) |
| Hybrid | A mix of the above per route | Implemented (static SSR, Islands and APIs live in the same Worker) | Works by default (HonoX's file routes are independent per route) |

### 1.1 Entry points and boundaries of SSR + Islands

| Stage | Entry point | Content |
| --- | --- | --- |
| Server | `app/server.ts` (`createApp`) → generated routes `app/routes/**/*.ts` → Scala handlers | The handler builds scalajs-react elements and passes them to `c.render` |
| Renderer | `app/routes/_renderer.tsx` (`@hono/react-renderer`) | Builds the HTML shell and places the client script |
| Island SSR | The generated `IslandComponents.Xxx(props)` → `hx4s.react.Island` → `React.createElement(HonoXIsland, …)` | Wraps in `<honox-island component-name=… data-serialized-props=…>` |
| Client | `app/client.ts` → `createClient({ hydrate, createElement })` | Matches the keys of `import.meta.glob('/app/islands/**/*.tsx')` against `component-name` |
| Island client | The generated `app/islands/x.tsx` (`export default XxxJs`) → the chunk of `scalajs:client.js` | `hydrateRoot` with `JSON.parse(data-serialized-props)` as props |

Hydration boundaries are decided by `HonoXIsland` (H10).

- When an island renders another island inside it, the inner one doesn't create a boundary and renders as a normal component. There is only one hydration root, the outer one.
- When props contain React elements (or arrays of elements), those keys are removed from `data-serialized-props` and SSR'd into `<template data-hono-template="key">`.
  The client rebuilds the elements from the template DOM, and if there are islands inside, imports and inserts them (`buildCreateChildrenFn` in `client/runtime.js`).
- 3ch's `tests/islands-hydration.test.js` checks three cases (simple, nested, element props) for the number of boundaries, reuse of the SSR DOM, absence of hydration errors,
  and that clicks change state. hx4s inherits this test as a compatibility test.

The client script is assumed to always be loaded. HonoX's `HasIslands` decides by searching the dependency tree for imports from route files to island TSX,
but hx4s's generated routes only import `scalajs:server.js`, so this detection is not expected to work (not verified).
Omitting the script on pages without islands is deferred to P1 or later (whether codegen can decide per route whether `IslandComponents` is used has not been investigated).

### 1.2 SSR + Full Hydration (planned)

Declaring the page's root component as one island works with the existing mechanism.
However, it has the following problems and is not recommended in P0.

- All of the page's data goes into `data-serialized-props` and is sent twice along with the HTML body.
- HTML escaping of attribute values turns `"` into `&quot;`, so the more strings the JSON has, the more it inflates.
- Per-page code splitting depends on how the client chunks are split.

To support Full Hydration officially, consider putting props in `<script type="application/json">`. In that case, the `</script>` countermeasure in 2.2 below (replacing `<` with `<`)
and its verification become mandatory. Mandatory verification: round-trip string values containing `</script><script>…</script>`, `<!--` and U+2028 / U+2029 through SSR → HTML → the client's `JSON.parse`, and check with jsdom that (1) the values come back unchanged and (2) injected scripts don't run.

### 1.3 SPA + API (planned)

Combining a route that returns a static HTML shell with `@route`s returning JSON can be written with the current mechanism.
How to avoid double maintenance of client-side routing and server routes is covered in [routing-design.md](routing-design.md) chapter 4.

### 1.4 Island file name rules (P0 public rules)

HonoX's `createClient` collects island candidates with `import.meta.glob('/app/islands/**/*.tsx')`, and hydrates only those whose basename matches `/\/[a-zA-Z0-9-]+\.tsx$/`
(`client/client.js`, 0.1.62). A file that doesn't match gets a `<honox-island>` in SSR but is never hydrated, because the client doesn't discover it.

hx4s makes the following a **P0 public rule**: the generated basename for `@islands` must match `[A-Za-z0-9-]+\.tsx`.
3ch's `AnnotationScanner` already checks this as `IslandBasename` (`875a47b`).

| Example | Result |
| --- | --- |
| `thread-view.tsx`, `Foo2.tsx`, `a/b-c.tsx` | Allowed |
| `foo_bar.tsx`, `foo.bar.tsx`, `foo bar.tsx`, `foo.island.tsx`, `$foo.tsx` | Rejected at generation time (`*.island.tsx` / `$*.tsx`, which HonoX picks up by other rules, are not used in P0 either) |

Verification conditions (added to [compatibility.md](compatibility.md) chapter 3): allowed names go all the way through SSR → discovery → hydration. Rejected names produce an error at generation time.


## 2. Props serialization

### 2.1 HonoX's implementation

`honox/dist/vite/components/honox-island.js` (0.1.62) splits props as follows.

```js
const isElementPropValue = (value) => Array.isArray(value) ? value.some(isElementPropValue)
  : typeof value === "object" && isValidElement(value);
// elements → elementProps (into <template>), everything else → restProps
[DATA_SERIALIZED_PROPS]: JSON.stringify(restProps)
```

In other words, non-element props are passed to React **with the result of `JSON.stringify` as the HTML attribute value as-is**.
The client restores them with `JSON.parse(element.attributes.getNamedItem("data-serialized-props")?.value ?? "{}")`.

### 2.2 XSS, `</script>` and Unicode

| Item | Handling | Basis |
| --- | --- | --- |
| Injection into HTML attributes | React's SSR escapes attribute values (`"` becomes `&quot;`, etc.). 3ch's tests confirm that a string containing Japanese text and `"<&>` round-trips | `tests/islands-hydration.test.js`, the expected `&quot;` in `tests/islands-bridge.test.js` |
| `</script>` | Doesn't apply to the current method (attribute values). Props go into an attribute value, not inside `<script>`, so there is no path to break out of the context with `</script>`. If props are put in `<script type="application/json">` in the future, output `<` in the JSON string **replaced by the 6-character string `<`** (after replacement it is still the original `<` as JSON, and since `<` never appears in the HTML context, `</script>` can't be formed). Replacing `<` with some other character, outputting `<` as-is, or targeting only `</script` are all considered insufficient | `honox-island.js` |
| Values invalid as JSON | Properties that are `undefined`, functions or Symbols are silently dropped. `NaN` / `Infinity` become `null`. `BigInt` throws. `Date` becomes an ISO string and stays a string when restored | The specification of `JSON.stringify` |
| Scala values | For case classes, `Option`, Scala collections and `Long` (not a JS number in Scala.js), the property names and object structure visible from JS don't match the Scala type. Passing them directly produces broken JSON or loses information | Scala.js's representation. 3ch's `docs/islands-ssr.md` also states "don't pass case classes or collections directly" |
| Unicode | Non-ASCII characters are output as UTF-8 without escaping. Lone surrogates are output in the form `\udXXX` and restored by `JSON.parse` (`JSON.stringify` from ES2019 on). U+2028 / U+2029 are not a problem inside attribute values | The specification of `JSON.stringify`. Pages are assumed to be UTF-8 (islands are not used on Shift_JIS pages) |
| Size | Props go into the HTML as attribute values and are sent separately from the SSR markup. There is no limit | — |
| Functions (event handlers) | Can't be sent. Define them inside the client component | — |

hx4s's conventions:

- Types usable in island props are limited to `String`, `Double`, `Int`, `Boolean`, `js.UndefOr[T]`, `js.Array[T]`, other props traits, and React elements (treated as templates).
- Fields of `js.UndefOr` drop their key when there is no value. Write the client assuming it doesn't distinguish "key missing" from "`undefined`".
- Props validation (required keys and types) is done by `validate` at the client entry. On the server side, types guarantee that the caller's arguments fit `P` (scope of the guarantee in 2.3, [ADR 0003](adr/0003-typed-island-props.md)).
- When `validate` fails, the client logs `console.error`, doesn't hydrate, and keeps the SSR markup. This is the P0 policy (U-12).
- In P0, codegen doesn't check the contents of props traits (it's syntax-based and doesn't resolve types; [ADR 0004](adr/0004-scalameta-codegen.md)).
  The restriction on usable types is covered by documentation and, if possible in P1, by type-level constraints in Scala (an upper bound on `IslandProps`, etc.).

### 2.3 Scope of typed props guarantees and constraints of the JSON round trip

This separates what Scala's types guarantee from what JSON guarantees.

**What Scala's types guarantee (P0)**: that the caller's arguments to `IslandComponents.Xxx(props: P)` fit `P` (a non-native JS trait). That is, **only the argument types of the server's code**.
There is no guarantee that the client's island component reads the same `P`. The `P` of `@islands[P]` is only copied into the generated API as the annotation's type argument,
and is not tied to the argument type of the client component exported with `@JSExportTopLevel`. So even if the client reads `props` with a different type (`Q`), it compiles.
As a countermeasure, a **typed export helper** tying the client export to `P` (e.g. a type like `def XxxJs: IslandExport[P]` that makes implementations not matching `P` a compile error) becomes a Phase 3 design item ([extraction-plan.md](extraction-plan.md) 3-1a, open question U-24).
Until then, it is covered by `validate` in shared and by reviewing the client's reading code.

**Implemented in Phase 3**: `hx4s.react.IslandExport[P]` ([ADR 0007](adr/0007-props-validation-and-typed-export.md)). Codegen syntactically compares the export's declared type `IslandExport[T]` with `@islands[P]`,
and the compiler compares the declared type with the props type of the render function. Received JSON is checked with `JsShape`, and the same validation function is called before hydration; on mismatch the SSR markup is kept (the P0 policy of 2.4).

**What the JSON round trip doesn't guarantee**: even values fitting `P` are lost or fail when passed through `JSON.stringify` in the following cases.

| Item | Behavior | Response |
| --- | --- | --- |
| Non-finite `Double` (`NaN`, `±Infinity`) | Becomes `null`. If the client's `validate` requires a number, it mismatches | Don't pass them from the server. Detect them with `validate` |
| Cyclic references | `JSON.stringify` throws and SSR fails (can become a 500) | Don't create cyclic structures in props. Include in the verification items |
| `undefined` | The key is dropped (`null` inside arrays). Matches "no value" of `js.UndefOr` | Don't distinguish "key missing" from "`undefined`" (2.2) |
| React elements inside nested objects | HonoX's element detection (`isElementPropValue`) only works on **top-level props values and arrays of them**. Elements put in nested objects (fields of another props trait) don't become `<template>`s, are passed to `JSON.stringify` and break | Element props are limited to top-level fields of `P` or arrays of them |
| `Long` | Scala.js's `Long` is not a JS number and can't be written to JSON directly | Don't use `Long` in props (use `Double` or a string). Not among the "usable types" in 2.2 |
| Functions, Symbols, `BigInt` | Functions and Symbols are dropped; `BigInt` throws (2.2) | Don't use them |

Verification items (added to [compatibility.md](compatibility.md) chapter 3): with props containing non-finite `Double`s, cyclic references or nested elements, confirm that SSR fails or hydration mismatches,
and that the behavior in that case (`console.error` and keeping the SSR markup, 2.2) is as specified.

### 2.4 Version skew between HTML and client assets

The SSR HTML (the `data-serialized-props` output by the server) and the client assets (`static/client.js` and island chunks) are served and cached separately.
**We can't say they match because they are built in the same deployment.** The following combinations can occur:

- Old HTML (cached, fetched before the deployment) + new client assets
- New HTML + old client assets (asset cache still alive, CDN propagation delayed)
- A deployment in the middle of loading (`static/client.js` is new while lazily imported chunks are old, or vice versa)

3ch's client entry has a fixed name (`entryFileNames: "static/client.js"` in `vite.config.ts`), so the name referenced from the HTML carries no version information.
Changing the props schema (fields of `P`) in this situation means that during the skew, the JSON the client receives doesn't fit `P`.

P0 policy: run `validate` at the client entry, and on mismatch log `console.error` and keep the SSR markup without hydrating (it stays as static HTML; 2.2).
This is the minimum to keep the screen from breaking when skew happens; it doesn't prevent the skew itself.

Implementation (Phase 3): `hydrate` in `app/client.ts` calls `islandPropsProblem(element)` from `hx4s-vite/client`, and if a reason is returned, doesn't call `hydrateRoot`. The scope of the guarantee is as follows.

| Category | Content |
| --- | --- |
| Checked | The props of the island being hydrated (one HonoX boundary), and the props of islands contained in its element props (children HonoX restores from `<template>`, following arrays and the element props of restored elements too). Targets are exports built with `IslandExport.apply` (those with the validation function `hx4sValidate`) |
| On mismatch | The whole boundary is not hydrated. Even if only an inner island is invalid, it stays as SSR markup including the outer one (no partial hydration). The reason is returned with its location, like `props.children[1]: props.start: missing` |
| Not checked | `IslandExport.unchecked` and islands written in JS. Elements placed outside element props (inside ordinary values). The version of chunks loaded after hydration. Content that arrives later through HonoX's Suspense (`template[id^="H:"]`) |

The initial Phase 3 implementation, with the validation written directly in the template, looked only at the outermost `element.type`. If an island inside a template was invalid, hydration of the outer one started,
and the inner `IslandExport` rendered nothing, losing the SSR content (Phase 3 review K6).

Open questions ([roadmap.md](roadmap.md) U-26):

- Hashed entry names (`client.[hash].js`), and how the HTML references the hashed name (HonoX has a way using a manifest, but 3ch's client build doesn't use `honox()`).
- Cache control (`Cache-Control` that makes `static/client.js` revalidate, etc.). A runtime-side setting.
- How long to keep old assets (whether to keep serving the old generation's chunks for a while after a deployment).
- Guidelines for props schema changes: receive added fields as `js.UndefOr`, remove in two steps, avoid adding required fields, etc.
- Verifying old/new combinations: with both old HTML + new client and new HTML + old client, `validate` detects the mismatch and the SSR markup remains.
  Old HTML + new client is checked by `tests/islands-hydration.test.js` (outermost and inside templates) and the examples' `tests/hydration.test.js`.
- The client's output names can now be changed with `client.output` of `hx4s()` (the default stays a fixed name). Whether to make hashed names the default, and how to pass them to the HTML, remain undecided.

## 3. Comparison of typed props approaches

3ch currently builds props with `js.Object` / `js.Dynamic.literal` and reads them on the client with `selectDynamic`.
Mismatched key names aren't caught at compile time, and missing values turn into empty strings or 0 (chapter 1 of `docs/island-props型付け計画.md`).

| Approach | Simplicity of implementation | Bundle size | Runtime cost | Scala.js compatibility | Error detection |
| --- | --- | --- | --- | --- | --- |
| A. Put non-native JS traits in shared and make the type argument of `@islands[P]` the argument type of the generated API (**adopted for P0**) | High. Codegen only copies the syntax of the type argument as a string | No added code (a trait is a declaration of the shape of a JS object) | No conversion. The server builds JS objects directly, and the client uses `asInstanceOf` | Standard features. `js.UndefOr` / `js.Array` follow Scala.js's rules | Missing or extra fields and type mismatches on the server's calling side are compile errors (no guarantee that the client component reads the same `P`; 2.3). Values received by the client are unchecked, so a hand-written `validate` is needed |
| B. Derive a codec with case classes + Scala 3 `Mirror` | Medium. Codec derivation and error reporting need to be written. Rules for `Option`, enums and nesting need deciding too | Grows by the codec (not measured) | Conversion runs once each on SSR and the client | Mirror is a compile-time feature and works on Scala.js. Using external libraries (upickle / circe, etc.) adds their dependencies and size | Typed on both sides. Missing values can be detected with `Either` when decoding |
| C. Keep `js.Dynamic` | Simplest (status quo) | Nothing added | No conversion | No problems | Not detected. Key typos and missing values show up as empty displays at run time |
| D. Generate conversion from case classes to JS objects and validation with Scala 3 macros | Low. Requires maintaining macros and keeping error messages good | Grows by the generated code (not measured; possibly smaller than B) | Conversion runs once each | Macros are a standard Scala 3 feature, but handling around `js.Object` needs care with Scala.js-specific constraints | Same as B. Unusable types can be rejected at compile time |

A is chosen because it adds nothing at run time, needs no codec dependency, and matches 3ch's existing plan (`docs/island-props型付け計画.md`) as-is.
Its weakness, "received values aren't validated on the client", is covered by `validate(raw): Either[String, P]` in shared. This is hand-written.

B (and D) are candidates for an additional API in P1 / P2 for users who want to treat props as Scala values. Even if introduced, they wouldn't replace A;
the leading idea is to coexist, going through a codec when the `P` of `@islands[P]` is a case class (open question U-22).
