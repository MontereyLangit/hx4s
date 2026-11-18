# Islands

An island is the part of the SSR'd HTML that is hydrated in the browser. HonoX detects the boundaries, serializes the props and hydrates (`<honox-island>`), and hx4s places components written in Scala there, with types.
See [ssr-hydration.md](ssr-hydration.md) for the rendering flow.

An island consists of three parts.

| Part | Location | Role |
| --- | --- | --- |
| The props trait and validation rules | shared | The type `P` shared by server and client |
| An `IslandExport[P]` annotated with `@islands[P]` | client | The component shipped to the browser |
| The generated `IslandComponents.<name>(props: P)` | server (generated) | Places the component during SSR |

The minimal set is in [getting-started.md](getting-started.md) and `examples/minimal`.

## The props trait

Props are a **non-native JS trait** extending `js.Object`. The server builds them with `new CounterProps { val label = ...; val start = ... }`, and the client reads them with the same type. There is no conversion; the JS object is passed as-is.

```scala
trait OwnerProps extends js.Object {
  val name: String
  val since: Int
}

trait ItemListProps extends js.Object {
  val title: String
  val owner: OwnerProps                 // nested props
  val items: js.Array[ItemProps]
  val note: js.UndefOr[String]          // optional
}
```

### Supported types

Props are passed as JSON (HonoX runs `JSON.stringify` and puts the result in an attribute value), so only types that round-trip through JSON can be used.

| Type | Notes |
| --- | --- |
| `String` | |
| `Int` | |
| `Double` | Finite values only. `NaN` and `Infinity` become `null` |
| `Boolean` | |
| `js.UndefOr[T]` | When there is no value, the key itself is dropped. "Key missing" and "`undefined`" are not distinguished |
| `js.Array[T]` | Elements must be supported types |
| Another props trait | A nested object |

Unsupported types and what happens.

| Type | Result |
| --- | --- |
| Functions (event handlers, etc.) | Can't be sent (dropped from the JSON). Define them inside the client component |
| `Option` / Scala collections (`List`, `Map`) / case classes | The properties visible from JS don't match the Scala type, producing broken JSON or losing information. Convert to `js.UndefOr` / `js.Array` / a props trait |
| `Long` | Scala.js's `Long` is not a JS number and can't be written to JSON. Use `Double` or a string |
| `BigInt` / `Date` / Symbol | `BigInt` throws, `Date` becomes a string, Symbol is dropped |
| Cyclic structures | `JSON.stringify` throws and SSR fails |
| React elements inside a nested trait | HonoX treats only top-level props values (and arrays of them) as elements; elements inside nested objects break |

## `@islands[P]`

```scala
import hx4s.annotation.islands
import hx4s.react.IslandExport

@islands[example.shared.CounterProps]("counter.tsx")
@JSExportTopLevel("CounterJs", "client")
val counter: IslandExport[CounterProps] = IslandExport("CounterJs", CounterProps.fields)(props => Counter.Component(props))
```

| Item | Rule |
| --- | --- |
| Target | A `def` / `val` in a top-level object of the configured sources (the client project) |
| Type argument `P` | The **fully qualified name** of the props type. An imported short name can't be used (`must be a fully qualified class name`). If omitted, `js.Object` |
| `filePath` | A string literal relative to `app/islands`, ending in `.tsx` |
| `@JSExportTopLevel` | Exactly one. The moduleID is `"client"`. The export name is a JS identifier, unique within the project |
| `@islands` per member | At most one |

### File name rules

HonoX's `createClient` hydrates only files whose basename matches `[A-Za-z0-9-]+\.tsx`. A file that doesn't match gets a boundary in SSR but is never hydrated, so hx4s rejects it at generation time.

| Example | Result |
| --- | --- |
| `thread-view.tsx`, `Foo2.tsx`, `a/b-c.tsx` | Allowed |
| `foo_bar.tsx`, `foo.bar.tsx`, `foo bar.tsx`, `foo.island.tsx`, `$foo.tsx` | Rejected: `island basename of bad_name.tsx must match [A-Za-z0-9-]+\.tsx for HonoX discovery` |

Paths containing directories starting with `.` or `node_modules` are also rejected. Paths that differ only in case are rejected as duplicates.

## `IslandExport[P]`

The type of an island exported by the client. Use the same type as the `P` of `@islands[P]`.

| Construction | Behavior |
| --- | --- |
| `IslandExport(label, fields)(render)` | Checks the received props with `fields` (a list of `JsField`) and then renders with `render`. If invalid, logs `console.error("[label] invalid props: …")` and renders nothing. Also used for validation before hydration |
| `IslandExport.unchecked(label)(render)` | Renders without validation. The shape of the props is kept only by the agreement between server and client. Not covered by validation before hydration |

`render` is `P => VdomNode`. `label` is used as the React display name and as the prefix of error messages.

### What the types guarantee

| Guarantee | How |
| --- | --- |
| Compile error if the server passes something other than `P` | The argument type of the generated `IslandComponents.CounterJs(props: P)` |
| Error if the client export's type and `@islands[P]` disagree | Codegen syntactically compares the `T` of the export's declared type `IslandExport[T]` with `P`. Example: `IslandExport[CounterProps] does not match @islands[example.shared.ItemListProps]` |
| Compile error if the declared type and `render`'s props type disagree | The Scala compiler |
| Whether the received JSON has the shape of `P` | Validated at run time with `JsShape` (`fields`) |

**Not guaranteed**: `fields` and the trait are written separately, so a mismatch is not caught at compile time. Writing `required("start", JsKind.Str)` for a trait with `val start: Int` compiles.
Codegen's comparison is a syntactic match of type names and can't follow type aliases or import aliases.

### `JsShape` and `JsKind`

Build `fields` with `JsShape.required(name, kind)` and `JsShape.optional(name, kind)`.

| `JsKind` | Meaning |
| --- | --- |
| `Str` | String |
| `Num` | Finite number |
| `Int` | Integer in the `Int` range |
| `Bool` | Boolean |
| `Obj(fields)` | Nested object |
| `Arr(element)` | Array |

```scala
private val owner = JsKind.Obj(List(required("name", JsKind.Str), required("since", JsKind.Int)))
val fields: List[JsField] = List(
  required("title", JsKind.Str), required("owner", owner), required("items", JsKind.Arr(item)), optional("note", JsKind.Str)
)
```

`JsShape.validate[P](raw, fields)` collects all errors and returns `Left(<string listing them>)`; `JsShape.read` logs `console.error` on failure and returns `None`.
Error locations are shown like `props.owner.since: expected number, got string`.

## The generated `IslandComponents`

On every `sbt server/compile` (only when inputs change), `server/target/.../src_managed/hx4s/generated/IslandComponents.scala` is generated.

```scala
import hx4s.generated.IslandComponents

IslandComponents.CounterJs(new CounterProps { val label = "A"; val start = 0 })   // VdomElement
```

- The method name is the `@JSExportTopLevel` name (`CounterJs`). The argument type is the `P` of `@islands[P]`, or `js.Object` if the type argument is omitted
- The return type is `VdomElement`, wrapped in a HonoX island boundary (`<honox-island component-name="/app/islands/counter.tsx" data-serialized-props=…>`)
- The package and object name can be changed with `hx4sIslandComponentsPackage` / `hx4sIslandComponentsObject` ([build-configuration.md](build-configuration.md))
- With no islands at all, it is an empty `object IslandComponents {}`
- Don't commit it (it is under `target`)

`TypedPropsSuite` in `examples/minimal` checks the types with `compileErrors`. Calls such as `IslandComponents.CounterJs(new js.Object)` or passing another island's props don't compile.

## The generated island files

For each `@islands`, a file that re-exports the client export is generated at `app/islands/<filePath>`.

```tsx
// This file is generated by hx4s-codegen. Do not edit this file manually.
// @ts-expect-error scalajs:client.js is a virtual module resolved by @scala-js/vite-plugin-scalajs (no .d.ts).
import { CounterJs as __hx4s_island } from "scalajs:client.js";
export default __hx4s_island;
```

HonoX looks for the default export of `app/islands/**/*.tsx` as islands. The actual code lives in the single linked Scala.js client module; each island file is only a re-export.

## Multiple instances, nesting, element props

- **Placing the same island several times**: works. The top page of `examples/minimal` places two counters, each with its own props (checked by `tests/build-output.test.js` and E2E)
- **Rendering an island inside another island**: per HonoX's design, the inner one doesn't create a boundary and renders as a normal component. The hydration root is the single outer one
- **Passing React elements as props** (like `children`): HonoX removes the elements from `data-serialized-props`, SSRs them into a `<template>`, and the client restores them.
  hx4s's compatibility tests check these three cases (simple, nested, element props) with JS components and Scala's `Island` (`tests/islands-hydration.test.js`)

However, **there is no example in the examples of writing a `P` with element props using `IslandComponents` and `IslandExport`, and it has not been verified**. `JsKind` has no kind for elements, so they can't be validated with `fields`.
If you need it, first get it working in the same shape as `examples/minimal`, then try it.

## Don'ts

| Action | Reason |
| --- | --- |
| Passing functions as props | They can't be written to JSON. Create callbacks inside the client component |
| Using `Option` / `List` / `Map` / case classes / `Long` as props types | They break as shown in the table above |
| Changing the fields of `P` and deploying only the server first | Old clients receive the new props. Avoid adding required keys or changing types, and make additions `js.UndefOr`. Validation before hydration stops mismatches, but the island stays static HTML |
| Editing the generated `app/islands/*.tsx` or `app/routes/*.ts` | They are overwritten by the next generation (they carry the generated marker) |
| Importing server modules from the client | Server code ends up in the browser bundle. See "Keeping server code out of the client" in [ssr-hydration.md](ssr-hydration.md) |
| Hand-writing an island whose basename doesn't follow the rules | HonoX won't hydrate it |
