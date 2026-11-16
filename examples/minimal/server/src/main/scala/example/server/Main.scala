package example.server

import example.shared.ItemProps
import hx4s.annotation.route
import hx4s.hono.Context
import japgolly.scalajs.react.facade.React
import org.scalajs.dom.Response

import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import scala.scalajs.js
import scala.scalajs.js.JSConverters.*
import scala.scalajs.js.annotation.JSExportTopLevel

/** app/routes/_renderer.tsx が受け取る props */
trait RenderProps extends js.Object {
  val title: js.UndefOr[String]
}

/** HonoX から呼ばれる export。@route を付けたものは hx4sGenerate が app/routes に配線する */
object Main {
  type AppContext = Context[js.Object, RenderProps]

  private def titled(text: String): RenderProps = new RenderProps { val title = s"$text | hx4s minimal" }

  @route("index.ts")
  @JSExportTopLevel("indexPage", "server")
  def indexPage(c: AppContext): js.Promise[Response] =
    c.renderPromise(Pages.index().rawElement, titled("トップ"))

  @route("items/[id].ts")
  @JSExportTopLevel("itemPage", "server")
  def itemPage(c: AppContext): js.Promise[Response] = {
    val id = c.req.param("id").getOrElse("")
    Catalog.find(id) match {
      case Some(item) => c.renderPromise(Pages.item(item, saved = None).rawElement, titled(item.name))
      case None =>
        c.status(404)
        c.renderPromise(Pages.notFound(id).rawElement, titled("見つかりません"))
    }
  }

  @route("items/[id].ts", "POST")
  @JSExportTopLevel("itemUpdate", "server")
  def itemUpdate(c: AppContext): js.Promise[Response] = {
    val id = c.req.param("id").getOrElse("")
    c.req.formData().toFuture.flatMap { form =>
      val name = (form.get("name"): Any) match {
        case value: String => value
        case _ => ""
      }
      val result = Catalog.find(id).toRight(Pages.notFound(id)).flatMap { item =>
        Catalog.rename(name).map(renamed => Pages.item(item, saved = Some(renamed)))
          .left.map(reason => Pages.item(item, saved = None, error = Some(reason)))
      }
      result match {
        case Right(page) => c.renderPromise(page.rawElement, titled("保存しました")).toFuture
        case Left(page) =>
          c.status(400)
          c.renderPromise(page.rawElement, titled("保存できません")).toFuture
      }
    }.toJSPromise
  }

  /** 品目の JSON。?tag= を繰り返すと、そのタグをすべて持つ品目に絞る */
  @route("api/items.ts")
  @JSExportTopLevel("itemsApi", "server")
  def itemsApi(c: AppContext): Response = {
    val tags = c.req.queries("tag").fold(List.empty[String])(_.toList)
    c.json(Catalog.withTags(tags).map { item =>
      new ItemProps { val id = item.id; val name = item.name; val tags = item.tags.toJSArray }
    }.toJSArray)
  }

  /** HTML のシェル。app/routes/_renderer.tsx が呼ぶ */
  @JSExportTopLevel("renderShell", "server")
  def renderShell(children: React.Node, title: js.UndefOr[String], clientScriptSrc: String): React.Element =
    Shell(children, title.getOrElse("hx4s minimal"), clientScriptSrc).rawElement
}
