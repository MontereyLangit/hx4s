package hx4s.hono

import japgolly.scalajs.react.facade.React
import org.scalajs.dom.{BodyInit, FormData, Request, Response}

import scala.scalajs.js
import scala.scalajs.js.|

/** Hono の `c.req` の facade。本文は最初に読んだ形を Hono がキャッシュし、text / json / formData のどれでも何度でも読める。raw を直接読むと読めなくなる */
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

/** 実行基盤がリクエストごとに渡す実行コンテキスト。Hono の ExecutionContext のうち基盤に依らない部分 */
@js.native
trait ExecutionContext extends js.Object {
  def waitUntil(promise: js.Promise[?]): Unit = js.native
  def passThroughOnException(): Unit = js.native
}

/** c.set / c.get で受け渡す値の名前と型。読む側と書く側で同じ値を共有する */
final case class ContextVariable[A](name: String)

/** Hono の Context。Env は `c.env`、R は `c.render` に渡すレンダラの props */
@js.native
trait Context[Env, R <: js.Object] extends js.Object {
  val req: HonoRequest = js.native
  val env: Env = js.native
  val res: Response = js.native
  /** 実行基盤が渡していなければ例外を投げる。渡さない基盤でも動かすなら executionCtxOption を使う */
  val executionCtx: ExecutionContext = js.native
  def header(name: String, value: String): Unit = js.native
  def status(code: Int): Unit = js.native
  /** 本文をそのまま返す。204 / 304 など本文を持てないステータスでは data に null を渡す */
  def body(data: BodyInit | Null, status: js.UndefOr[Int] = js.native, headers: js.UndefOr[js.Dictionary[String]] = js.native): Response = js.native
  /** 本文が空文字列でも本文ありになるので、204 / 304 には使えない（body(null, 204) を使う） */
  def text(body: String, status: js.UndefOr[Int] = js.native): Response = js.native
  def json(body: js.Any, status: js.UndefOr[Int] = js.native): Response = js.native
  def html(body: String, status: js.UndefOr[Int] = js.native): Response = js.native
  def redirect(location: String, status: js.UndefOr[Int] = js.native): Response = js.native
  /** アプリの Not Found ハンドラの応答（Hono の既定は 404 と本文 404 Not Found） */
  def notFound(): Response | js.Promise[Response] = js.native
  def render(children: React.Element, props: js.UndefOr[R] = js.native): Response | js.Promise[Response] = js.native
  def set(key: String, value: js.Any): Unit = js.native
  def get(key: String): js.UndefOr[js.Any] = js.native
}

object Context {
  extension [Env, R <: js.Object](context: Context[Env, R]) {
    /** c.render の戻り値を Promise にそろえる */
    def renderPromise(children: React.Element, props: js.UndefOr[R] = js.undefined): js.Promise[Response] = {
      js.Promise.resolve[Response](context.render(children, props))
    }

    /** c.executionCtx を読む。実行基盤が渡していない（Hono が例外を投げる）ときは None */
    def executionCtxOption: Option[ExecutionContext] = {
      try {
        Some(context.executionCtx)
      } catch {
        case _: js.JavaScriptException => None
      }
    }

    /** variable の名前で c.set する */
    def setVariable[A](variable: ContextVariable[A], value: A): Unit = {
      context.set(variable.name, value.asInstanceOf[js.Any])
    }

    /** variable の名前で c.get する。未設定なら None */
    def getVariable[A](variable: ContextVariable[A]): Option[A] = {
      context.get(variable.name).toOption.map(_.asInstanceOf[A])
    }
  }
}
