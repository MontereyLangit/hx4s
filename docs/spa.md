# SPA

**hx4s has no SPA features yet.** Client-side routing (Client Router), typed routes and a typed API client are not implemented and are on the roadmap ([hx4s/roadmap.md](hx4s/roadmap.md)).
This document describes a setup that works with the parts available today, and its limits.

## What works today: making the whole page one island

Make the page content one island, and call a JSON API after rendering. Write the API with `@route` ([api.md](api.md)).

```
GET /spa          → shell HTML and an island wrapping the whole page (SSR)
GET /api/items    → an @route returning JSON
island (browser)  → fetches /api/items after mounting and renders the list
```

The following code was prototyped on top of `examples/minimal`, and its build, SSR, hydration and API calls were verified (it is not included in `examples/minimal`).

Shared props:

```scala
trait AppProps extends js.Object {
  val title: String
}

object AppProps {
  val fields: List[JsField] = List(required("title", JsKind.Str))
}
```

The client island. Calls the API once on mount.

```scala
object App {
  final val Component = ScalaFnComponent.withHooks[AppProps]
    .useState(List.empty[String])
    .useEffectOnMountBy { (_, names) =>
      Callback.future(
        dom.fetch("/api/items").toFuture.flatMap(_.json().toFuture).map { json =>
          names.setState(json.asInstanceOf[js.Array[ItemProps]].toList.map(_.name))
        }
      )
    }
    .render((props, names) =>
      <.main(<.h1(props.title), <.ul(names.value.toVdomArray(name => <.li(^.key := name, name))))
    )
}

@islands[example.shared.AppProps]("app.tsx")
@JSExportTopLevel("AppJs", "client")
val app: IslandExport[AppProps] = IslandExport("AppJs", AppProps.fields)(props => App.Component(props))
```

(Requires `import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue` and `org.scalajs.dom`.)

The server page:

```scala
@route("spa.ts")
@JSExportTopLevel("spaPage", "server")
def spaPage(c: ApiContext): js.Promise[Response] =
  c.renderPromise(IslandComponents.AppJs(new AppProps { val title = "SPA" }).rawElement, new RenderProps { val title = "spa" })
```

What was verified (`wrangler dev` and Chromium):

- With JavaScript disabled, only the SSR part (`<h1>SPA</h1>`) is shown
- With JavaScript enabled, the island is hydrated and the result of `/api/items` (3 items) appears in the list
- No errors or warnings appear in the console

## Limits

| Item | Status |
| --- | --- |
| Client-side routing | **None**. Navigation uses ordinary links (full reloads); write an `@route` per page, each returning an island. hx4s provides no router based on the History API and has verified none |
| scalajs-react's Router | Not integrated with hx4s. Whether it works inside an island is not verified |
| Typed routes | Not implemented. URLs are strings; match the server's `@route` and the client's `fetch` URLs by hand |
| API types | Not implemented. Responses are `js.Any`; use `asInstanceOf` or check them yourself with `JsShape.validate`. There is no typed API client or OpenAPI |
| Cost of hydrating the whole page | All of the page's data goes into `data-serialized-props` and is sent twice along with the HTML body. Attribute escaping inflates the JSON ([hx4s/rendering-model.md](hx4s/rendering-model.md) 1.2) |
| Routing every path to one shell (SPA fallback) | Not tried |
| Islands with element props | Not verified ([islands.md](islands.md)) |

In short, what works today is "a multi-page site where part of a page or the whole page is an island that calls APIs".
An application driven entirely by a single client router can't be built until a Client Router lands.
