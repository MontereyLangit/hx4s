package hx4s.hono

import japgolly.scalajs.react.facade.React
import org.scalajs.dom.{Request, Response}

import scala.scalajs.js
import scala.scalajs.js.|

/** Hono の `c.req` の facade。raw は Fetch API の Request */
@js.native
trait HonoRequest extends js.Object {
  val raw: Request = js.native
  val method: String = js.native
  val url: String = js.native
  val path: String = js.native
  def param(name: String): js.UndefOr[String] = js.native
  def query(name: String): js.UndefOr[String] = js.native
  def header(name: String): js.UndefOr[String] = js.native
}

/** Hono の Context。Env は `c.env`、R は `c.render` に渡すレンダラの props */
@js.native
trait Context[Env, R <: js.Object] extends js.Object {
  val req: HonoRequest = js.native
  val env: Env = js.native
  val res: Response = js.native
  def header(name: String, value: String): Unit = js.native
  def status(code: Int): Unit = js.native
  def text(body: String, status: js.UndefOr[Int] = js.native): Response = js.native
  def json(body: js.Any, status: js.UndefOr[Int] = js.native): Response = js.native
  def redirect(location: String, status: js.UndefOr[Int] = js.native): Response = js.native
  def render(children: React.Element, props: js.UndefOr[R] = js.native): Response | js.Promise[Response] = js.native
}

object Context {
  extension [Env, R <: js.Object](context: Context[Env, R]) {
    /** c.render の戻り値を Promise にそろえる */
    def renderPromise(children: React.Element, props: js.UndefOr[R] = js.undefined): js.Promise[Response] = {
      js.Promise.resolve[Response](context.render(children, props))
    }
  }
}
