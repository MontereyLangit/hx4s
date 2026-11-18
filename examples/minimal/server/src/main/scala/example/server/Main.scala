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

/** Props received by app/routes/_renderer.tsx */
trait RenderProps extends js.Object {
  val title: js.UndefOr[String]
}

/** Exports called from HonoX. hx4sGenerate wires members with @route into app/routes */
object Main {
  type AppContext = Context[js.Object, RenderProps]

  private def titled(text: String): RenderProps = new RenderProps { val title = s"$text | hx4s minimal" }

  @route("index.ts")
  @JSExportTopLevel("indexPage", "server")
  def indexPage(c: AppContext): js.Promise[Response] =
    c.renderPromise(Pages.index().rawElement, titled("Home"))

  @route("items/[id].ts")
  @JSExportTopLevel("itemPage", "server")
  def itemPage(c: AppContext): js.Promise[Response] = {
    val id = c.req.param("id").getOrElse("")
    Catalog.find(id) match {
      case Some(item) => c.renderPromise(Pages.item(item, saved = None).rawElement, titled(item.name))
      case None =>
        c.status(404)
        c.renderPromise(Pages.notFound(id).rawElement, titled("No such item"))
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
        case Right(page) => c.renderPromise(page.rawElement, titled("Saved")).toFuture
        case Left(page) =>
          c.status(400)
          c.renderPromise(page.rawElement, titled("Could not save")).toFuture
      }
    }.toJSPromise
  }

  /** JSON of the items. Repeating ?tag= narrows to items that have all those tags */
  @route("api/items.ts")
  @JSExportTopLevel("itemsApi", "server")
  def itemsApi(c: AppContext): Response = {
    val tags = c.req.queries("tag").fold(List.empty[String])(_.toList)
    c.json(Catalog.withTags(tags).map { item =>
      new ItemProps { val id = item.id; val name = item.name; val tags = item.tags.toJSArray }
    }.toJSArray)
  }

  /** The HTML shell. Called by app/routes/_renderer.tsx */
  @JSExportTopLevel("renderShell", "server")
  def renderShell(children: React.Node, title: js.UndefOr[String], clientScriptSrc: String): React.Element =
    Shell(children, title.getOrElse("hx4s minimal"), clientScriptSrc).rawElement
}
