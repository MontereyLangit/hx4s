package hx4s.hono

import japgolly.scalajs.react.facade.React
import org.scalajs.dom.{BodyInit, FormData, Request, Response}

import scala.scalajs.js
import scala.scalajs.js.|

/** Facade for Hono's `c.req`. Hono caches the body in the form first read, so it can be read any number of times as text / json / formData. Reading raw directly makes it unreadable */
@js.native
trait HonoRequest extends js.Object {
  val raw: Request = js.native
  val method: String = js.native
  val url: String = js.native
  val path: String = js.native
  def param(name: String): js.UndefOr[String] = js.native
  def query(name: String): js.UndefOr[String] = js.native
  def queries(name: String): js.UndefOr[js.Array[String]] = js.native
  def header(name: String): js.UndefOr[String] = js.native
  def text(): js.Promise[String] = js.native
  def json(): js.Promise[js.Any] = js.native
  def formData(): js.Promise[FormData] = js.native
}

/** Execution context the runtime passes per request. The runtime-independent part of Hono's ExecutionContext */
@js.native
trait ExecutionContext extends js.Object {
  def waitUntil(promise: js.Promise[?]): Unit = js.native
  def passThroughOnException(): Unit = js.native
}

/** Name and type of a value passed through c.set / c.get. Share the same value between the reader and the writer */
final case class ContextVariable[A](name: String)

/** Hono's Context. Env is `c.env`, and R is the renderer props passed to `c.render` */
@js.native
trait Context[Env, R <: js.Object] extends js.Object {
  val req: HonoRequest = js.native
  val env: Env = js.native
  val res: Response = js.native
  /** Throws if the runtime did not provide one. Use executionCtxOption to also run on runtimes that do not */
  val executionCtx: ExecutionContext = js.native
  def header(name: String, value: String): Unit = js.native
  def status(code: Int): Unit = js.native
  /** Returns the body as-is. Pass null as data for statuses that cannot have a body, such as 204 / 304 */
  def body(data: BodyInit | Null, status: js.UndefOr[Int] = js.native, headers: js.UndefOr[js.Dictionary[String]] = js.native): Response = js.native
  /** Even an empty string counts as a body, so it cannot be used for 204 / 304 (use body(null, 204)) */
  def text(body: String, status: js.UndefOr[Int] = js.native): Response = js.native
  def json(body: js.Any, status: js.UndefOr[Int] = js.native): Response = js.native
  def html(body: String, status: js.UndefOr[Int] = js.native): Response = js.native
  def redirect(location: String, status: js.UndefOr[Int] = js.native): Response = js.native
  /** The response of the app's Not Found handler (Hono's default is 404 with the body 404 Not Found) */
  def notFound(): Response | js.Promise[Response] = js.native
  def render(children: React.Element, props: js.UndefOr[R] = js.native): Response | js.Promise[Response] = js.native
  def set(key: String, value: js.Any): Unit = js.native
  def get(key: String): js.UndefOr[js.Any] = js.native
}

object Context {
  extension [Env, R <: js.Object](context: Context[Env, R]) {
    /** Normalizes the return value of c.render to a Promise */
    def renderPromise(children: React.Element, props: js.UndefOr[R] = js.undefined): js.Promise[Response] = {
      js.Promise.resolve[Response](context.render(children, props))
    }

    /** Reads c.executionCtx. None when the runtime did not provide one (Hono throws) */
    def executionCtxOption: Option[ExecutionContext] = {
      try {
        Some(context.executionCtx)
      } catch {
        case _: js.JavaScriptException => None
      }
    }

    /** Calls c.set with the name of variable */
    def setVariable[A](variable: ContextVariable[A], value: A): Unit = {
      context.set(variable.name, value.asInstanceOf[js.Any])
    }

    /** Calls c.get with the name of variable. None if not set */
    def getVariable[A](variable: ContextVariable[A]): Option[A] = {
      context.get(variable.name).toOption.map(_.asInstanceOf[A])
    }
  }
}
