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

/** Validated JSON object expected by `POST /api/notes`. */
trait NoteInput extends js.Object {
  val text: String
}

/** Exports JSON handlers registered by the generated HonoX route files. */
object Main {
  /** Hono context used by the sample JSON API. */
  type ApiContext = Context[js.Object, js.Object]

  private val noteInput = List(JsShape.required("text", JsKind.Str))

  private def toJs(note: Note): js.Object = js.Dynamic.literal(id = note.id, text = note.text)

  private def error(c: ApiContext, message: String, status: Int): Response = c.json(js.Dynamic.literal(error = message), status)

  /** Returns the sample notes as a JSON array. */
  @route("api/notes")
  @JSExportTopLevel("notesList", "server")
  def notesList(c: ApiContext): Response = c.json(Notes.all.map(toJs).toJSArray)

  /** Returns `201` with a sample note for valid input or `400` for invalid input. Leaves the sample collection unchanged. */
  @route("api/notes", "POST")
  @JSExportTopLevel("notesCreate", "server")
  def notesCreate(c: ApiContext): js.Promise[Response] =
    c.req.json().toFuture.map { raw =>
      JsShape.validate[NoteInput](raw, noteInput).flatMap(input => Notes.validate(input.text)).fold(
        reason => error(c, reason, 400),
        text => c.json(toJs(Note((Notes.all.size + 1).toString, text)), 201)
      )
    }.recover { case _: js.JavaScriptException => error(c, "The body is not JSON", 400) }.toJSPromise

  /** Returns a note as JSON or invokes Hono's not-found handler. */
  @route("api/notes/[id]")
  @JSExportTopLevel("noteGet", "server")
  def noteGet(c: ApiContext): Response | js.Promise[Response] =
    Notes.find(c.req.param("id").getOrElse("")).fold(c.notFound())(note => c.json(toJs(note)))

  /** Returns an empty `204` if the note exists or a JSON `404` otherwise. Leaves the sample collection unchanged. */
  @route("api/notes/[id]", "DELETE")
  @JSExportTopLevel("noteDelete", "server")
  def noteDelete(c: ApiContext): Response = {
    val id = c.req.param("id").getOrElse("")
    if (Notes.find(id).isDefined) c.body(null, 204) else error(c, s"note $id not found", 404)
  }
}
