package example.api

import hx4s.annotation.route
import hx4s.hono.Context
import hx4s.props.{JsKind, JsShape}
import org.scalajs.dom.Response

import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import scala.scalajs.js
import scala.scalajs.js.JSConverters.*
import scala.scalajs.js.annotation.JSExportTopLevel
import scala.scalajs.js.|

/** POST /api/notes の本文 */
trait NoteInput extends js.Object {
  val text: String
}

/** JSON の API。@route を付けたものは hx4sGenerate が app/routes に配線する */
object Main {
  type ApiContext = Context[js.Object, js.Object]

  private val noteInput = List(JsShape.required("text", JsKind.Str))

  private def toJs(note: Note): js.Object = js.Dynamic.literal(id = note.id, text = note.text)

  private def error(c: ApiContext, message: String, status: Int): Response = c.json(js.Dynamic.literal(error = message), status)

  @route("api/notes.ts")
  @JSExportTopLevel("notesList", "server")
  def notesList(c: ApiContext): Response = c.json(Notes.all.map(toJs).toJSArray)

  /** 本文を確かめ、作ったメモを 201 で返す（保存はしない） */
  @route("api/notes.ts", "POST")
  @JSExportTopLevel("notesCreate", "server")
  def notesCreate(c: ApiContext): js.Promise[Response] =
    c.req.json().toFuture.map { raw =>
      JsShape.validate[NoteInput](raw, noteInput).flatMap(input => Notes.validate(input.text)).fold(
        reason => error(c, reason, 400),
        text => c.json(toJs(Note((Notes.all.size + 1).toString, text)), 201)
      )
    }.recover { case _: js.JavaScriptException => error(c, "本文が JSON ではありません", 400) }.toJSPromise

  @route("api/notes/[id].ts")
  @JSExportTopLevel("noteGet", "server")
  def noteGet(c: ApiContext): Response | js.Promise[Response] =
    Notes.find(c.req.param("id").getOrElse("")).fold(c.notFound())(note => c.json(toJs(note)))

  /** 本文なしの 204 を返す（保存はしないので、在るかだけを確かめる） */
  @route("api/notes/[id].ts", "DELETE")
  @JSExportTopLevel("noteDelete", "server")
  def noteDelete(c: ApiContext): Response = {
    val id = c.req.param("id").getOrElse("")
    if (Notes.find(id).isDefined) c.body(null, 204) else error(c, s"note $id not found", 404)
  }
}
