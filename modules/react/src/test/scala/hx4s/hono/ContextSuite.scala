package hx4s.hono

import munit.FunSuite
import org.scalajs.dom.{Headers, HttpMethod, Request, RequestInit, Response}

import scala.concurrent.Future
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import scala.scalajs.js
import scala.scalajs.js.JSConverters.*
import scala.scalajs.js.annotation.JSImport
import scala.scalajs.js.|
import scala.util.Try

private type Ctx = Context[js.Object, js.Object]
private type Handler = js.Function1[Ctx, Response | js.Promise[Response]]

@js.native
@JSImport("hono", "Hono")
private class Hono extends js.Object {
  def get(path: String, handler: Handler): Hono = js.native
  def post(path: String, handler: Handler): Hono = js.native
  def onError(handler: js.Function2[js.Any, Ctx, Response]): Hono = js.native
  def use(path: String, middleware: js.Function2[Ctx, js.Function0[js.Promise[Unit]], js.Promise[Unit]]): Hono = js.native
  def fetch(request: Request, env: js.UndefOr[js.Object], executionCtx: js.UndefOr[ExecutionContext]): Response | js.Promise[Response] = js.native
}

/** An execution context that counts calls to waitUntil and passThroughOnException */
private class RecordingContext extends js.Object {
  var waited: Int = 0
  var passedThrough: Boolean = false
  def waitUntil(promise: js.Promise[?]): Unit = { waited += 1 }
  def passThroughOnException(): Unit = { passedThrough = true }
}

class ContextSuite extends FunSuite {
  private def call(app: Hono, path: String, init: RequestInit = new RequestInit {}, executionCtx: js.UndefOr[ExecutionContext] = js.undefined): Future[Response] =
    js.Promise.resolve[Response](app.fetch(new Request(s"http://localhost$path", init), js.undefined, executionCtx)).toFuture

  private def handler(body: Ctx => Response | js.Promise[Response]): Handler = body

  private def post(contentType: String, content: String): RequestInit = {
    val requestHeaders = new Headers()
    requestHeaders.set("content-type", contentType)
    new RequestInit {
      method = HttpMethod.POST
      headers = requestHeaders
      body = content
    }
  }

  test("executionCtx returns what the runtime provided, and executionCtxOption is None when it provided none") {
    val app = new Hono().get("/", handler { c =>
      c.executionCtxOption match {
        case Some(executionCtx) =>
          executionCtx.waitUntil(js.Promise.resolve[Int](1))
          c.executionCtx.passThroughOnException()
          c.text("present")
        case None =>
          val thrown = Try(c.executionCtx).isFailure
          c.text(s"absent thrown=$thrown")
      }
    })
    val recording = new RecordingContext
    for {
      absent <- call(app, "/").flatMap(_.text().toFuture)
      present <- call(app, "/", executionCtx = recording.asInstanceOf[ExecutionContext]).flatMap(_.text().toFuture)
    } yield {
      assertEquals(absent, "absent thrown=true")
      assertEquals(present, "present")
      assertEquals(recording.waited, 1)
      assert(recording.passedThrough)
    }
  }

  test("text / json / html / redirect and status / header build responses") {
    val app = new Hono()
      .get("/text", handler(c => c.text("created", 201)))
      .get("/json", handler(c => c.json(js.Dictionary[js.Any]("ok" -> true, "tags" -> js.Array("a", "b")))))
      .get("/html", handler { c =>
        c.status(404)
        c.header("x-hx4s", "yes")
        c.html("<p>none</p>")
      })
      .get("/redirect", handler(c => c.redirect("/to")))
      .get("/moved", handler(c => c.redirect("/to", 301)))
    for {
      text <- call(app, "/text")
      textBody <- text.text().toFuture
      json <- call(app, "/json")
      jsonBody <- json.text().toFuture
      html <- call(app, "/html")
      redirect <- call(app, "/redirect")
      moved <- call(app, "/moved")
    } yield {
      assertEquals((text.status, textBody), (201, "created"))
      assert(text.headers.get("content-type").startsWith("text/plain"))
      assertEquals(jsonBody, """{"ok":true,"tags":["a","b"]}""")
      assert(json.headers.get("content-type").startsWith("application/json"))
      assertEquals(html.status, 404)
      assertEquals(html.headers.get("x-hx4s"), "yes")
      assert(html.headers.get("content-type").startsWith("text/html"))
      assertEquals((redirect.status, redirect.headers.get("location")), (302, "/to"))
      assertEquals(moved.status, 301)
    }
  }

  test("body returns 204 / 304 with a null body and accepts a body and headers; text with an empty string cannot make a 204") {
    val app = new Hono()
      .get("/no-content", handler(c => c.body(null, 204)))
      .get("/not-modified", handler { c =>
        c.header("etag", "\"v1\"")
        c.body(null, 304)
      })
      .get("/bytes", handler(c => c.body("abc", 201, js.Dictionary("content-type" -> "application/octet-stream"))))
      .get("/empty-text", handler(c => c.text("", 204)))
      .get("/missing", handler(c => c.notFound()))
      .onError((error: js.Any, c: Ctx) => c.text(error.toString, 500))
    for {
      noContent <- call(app, "/no-content")
      noContentBody <- noContent.text().toFuture
      notModified <- call(app, "/not-modified")
      bytes <- call(app, "/bytes")
      bytesBody <- bytes.text().toFuture
      emptyText <- call(app, "/empty-text")
      emptyTextBody <- emptyText.text().toFuture
      missing <- call(app, "/missing")
      missingBody <- missing.text().toFuture
    } yield {
      assertEquals((noContent.status, noContentBody), (204, ""))
      assertEquals(noContent.body, null)
      assertEquals((notModified.status, notModified.headers.get("etag")), (304, "\"v1\""))
      assertEquals((bytes.status, bytesBody, bytes.headers.get("content-type")), (201, "abc", "application/octet-stream"))
      assertEquals(emptyText.status, 500)
      assert(emptyTextBody.contains("204"), emptyTextBody)
      assertEquals((missing.status, missingBody), (404, "404 Not Found"))
    }
  }

  test("a value set with setVariable is read with the same type by a later handler with getVariable") {
    val user = ContextVariable[String]("user")
    val missing = ContextVariable[Int]("missing")
    val app = new Hono()
      .use("*", (c: Ctx, next: js.Function0[js.Promise[Unit]]) => {
        c.setVariable(user, "Zoë")
        next()
      })
      .get("/", handler(c => c.text(s"${c.getVariable(user)} ${c.getVariable(missing)}")))
    call(app, "/").flatMap(_.text().toFuture).map(body => assertEquals(body, "Some(Zoë) None"))
  }

  test("HonoRequest reads method, path, url, queries, and the body as text / json / formData") {
    val app = new Hono()
      .get("/info", handler { c =>
        val tags = c.req.queries("tag").fold("none")(_.mkString(","))
        c.text(s"${c.req.method} ${c.req.path} ${c.req.url} $tags ${c.req.queries("none").isDefined}")
      })
      .post("/text", handler(c => c.req.text().toFuture.map(body => c.text(body.reverse)).toJSPromise))
      .post("/json", handler(c => c.req.json().toFuture.map(body => c.json(js.Array(body))).toJSPromise))
      .post("/form", handler(c => c.req.formData().toFuture.map { form =>
        (form.get("name"): Any) match {
          case name: String => c.text(name)
          case _ => c.text("no name", 400)
        }
      }.toJSPromise))
    for {
      info <- call(app, "/info?tag=a&tag=b").flatMap(_.text().toFuture)
      text <- call(app, "/text", post("text/plain", "abc")).flatMap(_.text().toFuture)
      json <- call(app, "/json", post("application/json", """{"n":1}""")).flatMap(_.text().toFuture)
      form <- call(app, "/form", post("application/x-www-form-urlencoded", "name=Zo%C3%AB")).flatMap(_.text().toFuture)
    } yield {
      assertEquals(info, "GET /info http://localhost/info?tag=a&tag=b a,b false")
      assertEquals(text, "cba")
      assertEquals(json, """[{"n":1}]""")
      assertEquals(form, "Zoë")
    }
  }

  test("Hono caches the body in the form first read so it can be reread in another form; after reading raw directly HonoRequest cannot read it") {
    def outcome(c: Ctx, read: Future[String]): js.Promise[Response] =
      read.map(body => c.text(s"read $body")).recover { case error => c.text(s"failed ${error.getClass.getSimpleName}") }.toJSPromise
    val app = new Hono()
      .post("/text-json", handler(c => (for {
        text <- c.req.text().toFuture
        json <- c.req.json().toFuture
      } yield c.text(s"$text ${js.JSON.stringify(json)}")).toJSPromise))
      .post("/json-text", handler(c => (for {
        json <- c.req.json().toFuture
        text <- c.req.text().toFuture
        again <- c.req.json().toFuture
      } yield c.text(s"${js.JSON.stringify(json)} $text ${js.JSON.stringify(again)}")).toJSPromise))
      .post("/text-form", handler(c => (for {
        text <- c.req.text().toFuture
        form <- c.req.formData().toFuture
      } yield c.text(s"$text ${form.get("name")}")).toJSPromise))
      .post("/form-text", handler(c => (for {
        _ <- c.req.formData().toFuture
        text <- c.req.text().toFuture
      } yield c.text(text)).toJSPromise))
      .post("/raw-then-req", handler(c => outcome(c, c.req.raw.text().toFuture.flatMap(_ => c.req.text().toFuture))))
      .post("/req-then-raw", handler(c => outcome(c, c.req.text().toFuture.flatMap(_ => c.req.raw.text().toFuture))))
    val json = post("application/json", """{"n":1}""")
    val form = post("application/x-www-form-urlencoded", "name=Zo%C3%AB")
    for {
      textJson <- call(app, "/text-json", json).flatMap(_.text().toFuture)
      jsonText <- call(app, "/json-text", json).flatMap(_.text().toFuture)
      textForm <- call(app, "/text-form", form).flatMap(_.text().toFuture)
      formText <- call(app, "/form-text", form).flatMap(_.text().toFuture)
      rawThenReq <- call(app, "/raw-then-req", json).flatMap(_.text().toFuture)
      reqThenRaw <- call(app, "/req-then-raw", json).flatMap(_.text().toFuture)
    } yield {
      assertEquals(textJson, """{"n":1} {"n":1}""")
      assertEquals(jsonText, """{"n":1} {"n":1} {"n":1}""")
      assertEquals(textForm, "name=Zo%C3%AB Zoë")
      assertNotEquals(formText, "name=Zo%C3%AB")
      assert(formText.contains("Content-Disposition: form-data; name=\"name\""), formText)
      assertEquals(rawThenReq, "failed JavaScriptException")
      assertEquals(reqThenRaw, "failed JavaScriptException")
    }
  }
}
