# ADR 0007: Props validation in the JS side of hx4s-core, typed export as `IslandExport[P]` in hx4s-react

- Status: Accepted (2026-11-16, Phase 3)
- Updated (2026-11-16, Phase 3 review K6): validation before hydration moved from code written directly in the template to `islandPropsProblem(element)` in `hx4s-vite/client`.
  It checks not only the outermost island but also islands inside element props (children restored from templates). The second paragraph of "Validation before hydration" below is kept as the original decision
- Related: [ADR 0003](0003-typed-island-props.md), [rendering-model.md](../rendering-model.md) 2.3 and 2.4, [roadmap.md](../roadmap.md) U-12, U-24, U-26, Phase 0 review C5

## Context

ADR 0003's typed props protected only the server's call `IslandComponents.Xxx(props: P)` with types; there was no guarantee that the client island reads the same `P` (C5, U-24).
The policy was to rely on hand-written validation (`validate(raw): Either[String, P]`) for values received by the client, and in the original project a validator called `JsShape` lived on the Scala.js side of shared.
Phase 3 decides which module it goes in, and how to tie the client export to `P`.

Constraints:

- Don't pull HonoX's SSR imports (`HonoXIsland` from `honox/vite/components`) or server code into the client bundle.
- `hx4s-core` doesn't depend on React ([module-boundaries.md](../module-boundaries.md) chapter 2).
- Don't add dependencies. Don't introduce macros or codec derivation (rejected in ADR 0003).

## Decision

### Where JsShape lives

`hx4s.props.{JsShape, JsKind, JsField}` goes in **the JS side of `hx4s-core`** (`modules/core/js`). `hx4s-core` becomes a crossProject (Full), and the JVM side has only the annotations.

- JsShape depends only on `scala.scalajs.js`, not on React or HonoX.
- `fields` can be written in the same place as the shared props traits (a shared project depending only on `hx4s-core`). shared doesn't need to depend on `hx4s-react`.

### Typed export helper

Put the following in `hx4s-react`.

```scala
@js.native sealed trait IslandExport[P <: js.Object] extends js.Object

object IslandExport {
  def apply[P <: js.Object](label: String, fields: List[JsField])(render: P => VdomNode): IslandExport[P]
  def unchecked[P <: js.Object](label: String)(render: P => VdomNode): IslandExport[P]
}
```

Client declarations take this form.

```scala
@islands[example.shared.CounterProps]("counter")
@JSExportTopLevel("CounterJs", "client")
val counter: IslandExport[CounterProps] = IslandExport("CounterJs", CounterProps.fields)(props => Counter.Component(props))
```

The chain of guarantees:

| Link | Who checks it |
| --- | --- |
| The `P` of `@islands[P]` and the `T` of the export's declared type `IslandExport[T]` | hx4s-codegen compares them syntactically. If `T` is fully qualified, it must match after adding `_root_`; if it is a simple name, it must match the last name of `P`. A declared type of `IslandExport[T]` with no type argument on `@islands` is also an error. If the declared type is not `IslandExport`, nothing is compared (exports of `js.Any` can still be written as before) |
| The declared type `IslandExport[T]` and the props type of the render function | The Scala compiler. `IslandExport` is invariant and `sealed`, so exports or render functions for a different `P` can't be assigned |
| The received JSON and `P` | `IslandExport.apply` checks it with `JsShape`. If invalid, it logs `console.error` and renders nothing |

`IslandExport` is a `sealed` native JS trait whose actual value is scalajs-react's `toJsComponent.raw` (a JS function component). It can be published as-is as a `@JSExportTopLevel` value,
and to the `export default` of the generated island TSX and HonoX's `createClient` it looks like an ordinary React component.

### Validation before hydration (version skew)

`IslandExport.apply` attaches a validation function `hx4sValidate(props): string | undefined` to the exported function.
`hydrate` in `app/client.ts` calls `element.type.hx4sValidate?.(element.props)`, and if a reason is returned, it logs `console.error` and doesn't call `hydrateRoot`.
This implements the P0 policy of ADR 0003 / rendering-model 2.4 (on mismatch, don't hydrate and keep the SSR markup).

These few lines are put in the `app/client.ts` template (`examples/minimal/app/client.ts`) rather than in a helper (following the U-7 policy).
The name of the validation function, `hx4sValidate`, is a contract between the template and `IslandExport`, and is part of the public API.

## Rejected alternatives

| Alternative | Reason |
| --- | --- |
| Put JsShape in `hx4s-react` | shared would have to depend on `hx4s-react` (scalajs-react) to write the props `fields` |
| Make JsShape a separate module (`hx4s-props`) | Adds artifacts and dependency lines. The JS side of core satisfies the dependency constraints |
| An alias `type IslandExport[P] = React.ComponentType[P]` | Any component that skips validation (`toJsComponent.raw`) could be assigned. Due to the contravariance of function types, `ComponentType` also accepts components taking a supertype of `P` as props |
| Compare the export's declared type only in codegen, without Scala types | Mismatches between the declared type and the implementation can't be detected |
| Don't use `hx4sValidate`; just render nothing when validation fails | Hydration wipes out the SSR markup and the screen becomes empty (the original project's behavior). Doesn't meet the P0 policy |

## Consequences

- Since the comparison is syntactic, a `T` written with a type alias or an import alias can't be followed (ADR 0004). For a simple-name `T`, only the last name is compared, so a different type with the same name slips through.
- The correspondence between `fields` and `P` is hand-written, and a mismatch still compiles (JsShape validation catches it at run time). Derivation with macros remains a P2 candidate.
- The client project depends on `hx4s-react`. `IslandExport` doesn't reference `Island` (the `@JSImport` of `HonoXIsland`), so Scala.js linking keeps `honox/vite/components` out of the client.
  `tests/build-output.test.js` in examples/minimal checks that the client bundle has no `HonoXIsland`.
