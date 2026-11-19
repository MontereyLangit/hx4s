package hx4s.hono

import japgolly.scalajs.react.facade.React
import org.scalajs.dom.{BodyInit, FormData, Request, Response}

import scala.scalajs.js
import scala.scalajs.js.|

/**
 * Exposes Hono's request metadata, routing parameters and body readers.
 *
 * Use the body readers on this facade when the handler and middleware share the
 * request body. Reading the underlying Fetch request consumes its stream and
 * can prevent Hono from caching it.
 */
@js.native
trait HonoRequest extends js.Object {
  /** Underlying Fetch request. Reading its body directly can consume the stream before Hono caches it. */
  val raw: Request = js.native
  /** HTTP method of the incoming request. */
  val method: String = js.native
  /** Full URL of the incoming request. */
  val url: String = js.native
  /** Request path used by Hono for routing. */
  val path: String = js.native
  /** Returns the named route parameter, or `undefined` when absent. */
  def param(name: String): js.UndefOr[String] = js.native
  /** Returns the first value of the named query parameter, or `undefined` when absent. */
  def query(name: String): js.UndefOr[String] = js.native
  /** Returns all values of the named query parameter, or `undefined` when absent. */
  def queries(name: String): js.UndefOr[js.Array[String]] = js.native
  /** Returns the named request header, or `undefined` when absent. Header names are case-insensitive. */
  def header(name: String): js.UndefOr[String] = js.native
  /** Reads the request body as text through Hono's body cache. */
  def text(): js.Promise[String] = js.native
  /** Parses the body as JSON. The promise rejects if parsing fails. */
  def json(): js.Promise[js.Any] = js.native
  /** Parses the body as form data. The promise rejects when the body cannot be parsed as form data. */
  def formData(): js.Promise[FormData] = js.native
}

/** Facade for request-lifetime hooks supplied by the hosting runtime. */
@js.native
trait ExecutionContext extends js.Object {
  /** Asks the runtime to keep the request's execution alive until `promise` settles. */
  def waitUntil(promise: js.Promise[?]): Unit = js.native
  /** Asks the runtime to fall back to the origin if the handler throws. */
  def passThroughOnException(): Unit = js.native
}

/**
 * Identifies a Hono context variable by name. Share the descriptor between readers and writers.
 *
 * @tparam A Value type agreed on by all callers using the same name.
 * @param name Key in Hono's request-local variable store.
 */
final case class ContextVariable[A](name: String)

/**
 * Facade for Hono's request context.
 *
 * @tparam Env Type of the runtime bindings exposed by `env`.
 * @tparam R JavaScript props accepted by the configured renderer.
 */
@js.native
trait Context[Env, R <: js.Object] extends js.Object {
  /** Request facade for the current request. */
  val req: HonoRequest = js.native
  /** Runtime bindings attached to the current request. */
  val env: Env = js.native
  /** Current response exposed by Hono. */
  val res: Response = js.native
  /** Returns the runtime execution context. Hono throws a JavaScript exception if the runtime did not supply one. */
  val executionCtx: ExecutionContext = js.native
  /** Sets a header on the response. */
  def header(name: String, value: String): Unit = js.native
  /** Sets the status for a subsequent response built through this context. */
  def status(code: Int): Unit = js.native
  /**
   * Builds a response with the supplied Fetch body.
   *
   * @param data Response body. Pass `null` for statuses that prohibit a body, such as `204` or `304`.
   * @param status Status override. Omit it to use Hono's current response status.
   * @param headers Additional response headers.
   * @return Response created by Hono.
   */
  def body(data: BodyInit | Null, status: js.UndefOr[Int] = js.native, headers: js.UndefOr[js.Dictionary[String]] = js.native): Response = js.native
  /** Builds a plain-text response. Even an empty string is a body and is invalid for a bodyless status. */
  def text(body: String, status: js.UndefOr[Int] = js.native): Response = js.native
  /** Serializes `body` as JSON and builds a response using the optional status override. */
  def json(body: js.Any, status: js.UndefOr[Int] = js.native): Response = js.native
  /** Builds an HTML response from `body` using the optional status override. */
  def html(body: String, status: js.UndefOr[Int] = js.native): Response = js.native
  /** Builds a redirect to `location`. Hono defaults to status `302` when `status` is omitted. */
  def redirect(location: String, status: js.UndefOr[Int] = js.native): Response = js.native
  /** Invokes Hono's not-found handler, which can return a response synchronously or asynchronously. */
  def notFound(): Response | js.Promise[Response] = js.native
  /**
   * Passes the React element and optional props to Hono's configured renderer.
   *
   * @param children React element to render.
   * @param props Props forwarded to the renderer when defined.
   * @return Response or promise of a response, as returned by the renderer.
   */
  def render(children: React.Element, props: js.UndefOr[R] = js.native): Response | js.Promise[Response] = js.native
  /** Stores a JavaScript value in Hono's request-local variable store under `key`. */
  def set(key: String, value: js.Any): Unit = js.native
  /** Reads the value for `key` from Hono's request-local store, or returns `undefined`. */
  def get(key: String): js.UndefOr[js.Any] = js.native
}

/** Adds response and variable helpers to the Hono context facade. */
object Context {
  extension [Env, R <: js.Object](context: Context[Env, R]) {
    /** Invokes `render` and normalizes its result to a JavaScript promise. Synchronous exceptions from `render` propagate. */
    def renderPromise(children: React.Element, props: js.UndefOr[R] = js.undefined): js.Promise[Response] = {
      js.Promise.resolve[Response](context.render(children, props))
    }

    /** Returns the execution context, or `None` if reading it throws a `js.JavaScriptException`. */
    def executionCtxOption: Option[ExecutionContext] = {
      try {
        Some(context.executionCtx)
      } catch {
        case _: js.JavaScriptException => None
      }
    }

    /** Stores `value` under the descriptor's name. All uses of that name must agree on its Scala type. */
    def setVariable[A](variable: ContextVariable[A], value: A): Unit = {
      context.set(variable.name, value.asInstanceOf[js.Any])
    }

    /** Casts the value under the descriptor's name to `A` without runtime validation, or returns `None` for `undefined`. */
    def getVariable[A](variable: ContextVariable[A]): Option[A] = {
      context.get(variable.name).toOption.map(_.asInstanceOf[A])
    }
  }
}
