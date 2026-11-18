# ADR 0003: Typed props are non-native JS traits + `@islands[P]`

- Status: Accepted (2026-11-16, Phase 0)
- Related: [rendering-model.md](../rendering-model.md) chapters 2 and 3, 3ch's island props typing plan (`docs/island-props型付け計画.md`)

## Context

3ch's island props are built by the server with `js.Dynamic.literal` and read back by the client with `selectDynamic`.
The generated API, `IslandComponents.XxxJs(props: js.Object)`, has no types either. Mismatched or missing key names are not caught at compile time and show up as an empty screen.
HonoX runs `JSON.stringify` on props and puts them in an HTML attribute, so they must be JSON-compatible JS objects.

## Decision

- Island props are placed as **non-native JS traits** (`trait XxxProps extends js.Object`) in a shared Scala.js module referenced by both server and client.
- The annotation becomes `final class islands[P](val filePath: String) extends StaticAnnotation`. `P` has no bound (`hx4s-core` is also compiled for the JVM, where there is no `js.Object`).
- Codegen reads the **syntax** of the type argument of `@islands[a.b.XxxProps]("x.tsx")` and generates `IslandComponents.XxxJs(props: a.b.XxxProps): VdomElement`. If the type argument is omitted, it is `js.Object` as before.
- In P0, `P` is limited to a fully qualified name (U-11).
- The client receives it with `raw.asInstanceOf[P]`, and validation of required keys (`validate(raw): Either[String, P]`) is hand-written in shared.

## Rejected alternatives

| Alternative | Reason |
| --- | --- |
| Derive a codec with case classes + Scala 3 `Mirror` | The codec code goes into the bundle, and conversions run on SSR and the client. Representation rules for `Option` and enums would also need deciding. A candidate additional API for P1 / P2 |
| Keep using `js.Dynamic` | The very problem of not detecting type mismatches remains |
| Generate conversion and validation with Scala 3 macros | Heavy to implement and maintain. As long as codegen is syntax-based ([ADR 0004](0004-scalameta-codegen.md)), anything beyond copying the type argument becomes the macro's job, which is beyond the scope of P0 |
| Add the bound `P <: js.Object` | `hx4s-core` would no longer compile on the JVM. A separate JS-only annotation is possible, but then declarations split in two |

The comparison table (simplicity of implementation, bundle size, runtime cost, Scala.js compatibility, error detection) is in [rendering-model.md](../rendering-model.md) chapter 3.

## Consequences

- The types guarantee only **the arguments on the server's calling side** (`IslandComponents.Xxx(props: P)`). There is no guarantee that the client's island component reads the same `P`,
  and the type of the client export marked with `@JSExportTopLevel` is not tied to `P`. The client relies on hand-written `validate`.
  A typed export helper tying the client export to `P` becomes a Phase 3 design item (open question U-24, [rendering-model.md](../rendering-model.md) 2.3).
- Users need a shared Scala.js project (both server and client depend on it, and the server doesn't depend on the client).
- Usable types are limited to JSON-compatible ones ([rendering-model.md](../rendering-model.md) 2.2). Codegen doesn't check this. Even values conforming to `P` break in a JSON round trip if they contain non-finite `Double`s, cyclic references or React elements inside nested objects (ibid. 2.3).
- In 3ch, Phase 1 replaces the board top island first, and the shape of `data-serialized-props` changes (nesting of `board-details`).
  **We can't say "there is no compatibility problem because they are updated in the same deployment".** The SSR HTML and the client assets are cached separately and loaded across deployments,
  so the combinations old HTML + new client and new HTML + old client can occur (3ch's client entry has the fixed name `static/client.js`).
- P0 policy: run `validate` at the client entry, and on mismatch log `console.error` and keep the SSR markup without hydrating (the screen doesn't break and stays static HTML).
- Open questions ([roadmap.md](../roadmap.md) U-26): hashed entry names, cache control, keeping old assets, guidelines for props schema changes, verifying old/new combinations. Details in [rendering-model.md](../rendering-model.md) 2.4.
