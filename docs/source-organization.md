# Organizing Scala application sources

[日本語](../ja/docs/source-organization.md)

Use [the small, runnable examples](examples.md) as a starting point. As the application grows, consider `handlers/` for HTTP entry points, `views/` for page rendering, `components/` for reusable UI, and `islands/` for client hydration entry points. These are optional Scala package conventions. hx4s reads annotations on members of top-level objects in the configured source files, regardless of their directory, package, or file name.

Scala `handlers/` contains the functions that handle requests. HonoX loads route modules from `app/routes/` to register URL paths and methods. A module generated from `@route("auth/index")` lives at `app/routes/auth/index.ts` and delegates to the exported Scala handler. Similarly, Scala `islands/` can contain client implementations, while the generated `app/islands/` modules re-export their Scala.js entry points for HonoX to discover. Moving a Scala file between packages does not require changing the annotation's path.

| Scala code | Typical responsibility |
| --- | --- |
| Handler | Parse and validate requests, choose HTTP statuses, delegate work, and return HTML or JSON. |
| View | Compose page-level SSR UI from explicit inputs. Fetch data before rendering when practical. |
| Component | Render a focused, reusable part of the UI. Share components across modules when their dependencies allow it. |
| Client island | Export an interactive hydration entry point with `@islands[P]`. Keep hydration boundaries explicit. An island can contain helper components or internal subpackages that render within the same boundary. |
| Application use case, optional | Coordinate operations, effects, and transactions. |
| Domain, optional | Express business invariants, entities, value objects, and policies. |
| Repository or adapter, optional | Define a data access interface or implement persistence. |

`HomeHandler` and `HomeView` make the distinction between HTTP and page rendering visible. For components, prefer names that describe the UI role, such as `Header` or `UserProfileCard`. The `Component` suffix is optional, as are `Handler` and `View`. File and object names follow your project's conventions.

## Small applications

A page and its handler can live together in `server/src/main/scala/example/Main.scala`. For an API-only app, `server/src/main/scala/example/handlers/ApiHandler.scala` is enough for the Scala HTTP code. Add directories and abstractions as the application needs them.

```text
server/src/main/scala/example/
└── Main.scala
```

An API-only app can keep its HTTP code in a dedicated handler file.

```text
server/src/main/scala/example/
└── handlers/
    └── ApiHandler.scala
```

In the runnable minimal example, `Main.indexPage` passes `Pages.index().rawElement` to `c.renderPromise`. The view calls `IslandComponents.CounterJs` with typed `CounterProps`, so the HTML includes an SSR hydration boundary. The client export declares the same props type in `@islands`, and HonoX uses `app/islands/counter.tsx` to hydrate the counter. The test clicks the counter and checks that the existing DOM node updates. [Run the example](examples.md#minimal) · [Handler](../examples/minimal/server/src/main/scala/example/server/Main.scala) · [View](../examples/minimal/server/src/main/scala/example/server/Pages.scala) · [Client export](../examples/minimal/client/src/main/scala/example/client/Main.scala) · [Hydration test](../examples/minimal/tests/hydration.test.js)

## Medium applications

Separate request handling from page rendering as those responsibilities grow. For straightforward CRUD, a handler can call a repository directly.

```text
server/src/main/scala/example/
├── handlers/
│   ├── HomeHandler.scala
│   ├── UserHandler.scala
│   └── ApiHandler.scala
├── views/
│   ├── HomeView.scala
│   └── UserDetailView.scala
├── components/
│   ├── layouts/
│   │   ├── AppLayout.scala
│   │   └── Header.scala
│   └── UserProfileCard.scala
└── repositories/                 # Optional
    └── UserRepository.scala

client/src/main/scala/example/
└── islands/
    └── UserProfileEditor.scala

app/
├── routes/                       # HonoX route modules
└── islands/                      # HonoX island entry modules
```

## Large applications

Keep route annotations on their handler methods as the number of endpoints grows. A top-level `AuthHandler` object can export its own handlers from any configured server source file. For example, given the application's `AppContext` and `AuthView`:

```scala
import hx4s.annotation.route
import hx4s.hono.Context.*
import org.scalajs.dom.Response
import scala.scalajs.js
import scala.scalajs.js.annotation.JSExportTopLevel

object AuthHandler {
  @route("auth/index")
  @JSExportTopLevel("authPageHandler", "server")
  def page(c: AppContext): js.Promise[Response] =
    c.renderPromise(AuthView.page().rawElement)
}
```

### Layered, Clean Architecture, or DDD-oriented organization

One option is to keep the medium example's presentation packages and group domain concepts and use cases by business area. Every additional layer shown here is optional.

```text
server/src/main/scala/example/
├── handlers/
│   └── UserHandler.scala
├── views/
│   └── UserDetailView.scala
├── components/
│   └── UserProfileCard.scala
├── application/
│   └── user/
│       ├── GetUser.scala
│       └── RegisterUser.scala
├── domain/
│   └── user/
│       ├── User.scala
│       ├── UserId.scala
│       ├── UserName.scala
│       ├── UserRepository.scala
│       └── UserRegistrationPolicy.scala
└── infrastructure/
    └── persistence/
        └── D1UserRepository.scala

client/src/main/scala/example/islands/
└── UserProfileEditor.scala

shared/src/main/scala/example/props/
└── UserProfileProps.scala

app/
├── routes/
└── islands/
```

Group related concepts under `domain/user/`. For example, a `User` entity has an identity, a `UserName` value object can compare by value, and a database row or ORM entity models persistence. Keep domain rules independent of Hono, Cloudflare, and database APIs where practical.

Application services and use cases coordinate work such as registration and its transaction. Domain services and policies express business rules such as eligibility to register. Use names that describe the work, such as `RegisterUser`. A repository port can live in the domain or application layer, with its implementation in infrastructure. A simpler app can keep data access together in `repositories/`. Add `services/`, `repositories/`, `application/`, `domain/`, or `infrastructure/` when they clarify a responsibility.

### Vertical Slice Architecture

Feature-first organization is equally suitable. Keep handlers, views, and use cases for a feature or bounded context close together, and share UI where it helps.

```text
server/src/main/scala/example/
├── features/
│   ├── users/
│   │   ├── UserHandler.scala
│   │   ├── UserDetailView.scala
│   │   ├── RegisterUser.scala
│   │   └── UserRepository.scala
│   └── boards/
│       ├── BoardHandler.scala
│       └── BoardView.scala
└── components/
    └── layouts/
        └── AppLayout.scala

client/src/main/scala/example/islands/
├── users/
│   └── UserProfileEditor.scala
└── boards/
    └── BoardEditor.scala
```

A hybrid can group use cases by feature while keeping shared domain modules and UI components. In each layout, keep modules cohesive and dependencies explicit. Immutable domain values and pure transformations can keep business logic separate from effectful HTTP and database code. This functional-core/imperative-shell style works with hx4s, and the framework leaves the choice of OOP, DDD, FP, and any effect library to the application. Share props and other types in a shared module when needed, keeping server-only dependencies out of the client bundle.
