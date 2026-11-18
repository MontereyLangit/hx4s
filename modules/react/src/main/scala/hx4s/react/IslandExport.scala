package hx4s.react

import hx4s.props.{JsField, JsShape}
import japgolly.scalajs.react.*
import japgolly.scalajs.react.vdom.VdomNode
import japgolly.scalajs.react.vdom.html_<^.*

import scala.scalajs.js
import scala.scalajs.js.JSConverters.*

/** An island component the client publishes with @JSExportTopLevel. P must be the same props type as @islands[P] on the same declaration */
@js.native
sealed trait IslandExport[P <: js.Object] extends js.Object

object IslandExport {

  /** Creates an island that checks props with fields and then renders with render. With invalid props it logs console.error and renders nothing */
  def apply[P <: js.Object](label: String, fields: List[JsField])(render: P => VdomNode): IslandExport[P] = {
    val component = ScalaFnComponent.withDisplayName(label)[js.Object] { raw =>
      JsShape.read[P](label, raw, fields).fold[VdomNode](EmptyVdom)(render)
    }
    val exported = component.toJsComponent.raw.asInstanceOf[ValidatedComponent]
    exported.hx4sValidate = (raw: js.Any) => JsShape.validate[P](raw, fields).left.toOption.orUndefined
    exported.asInstanceOf[IslandExport[P]]
  }

  /** Creates an island that renders without validation. The shape of props is kept only by the agreement between server and client */
  def unchecked[P <: js.Object](label: String)(render: P => VdomNode): IslandExport[P] =
    ScalaFnComponent.withDisplayName(label)[P](props => render(props)).toJsComponent.raw.asInstanceOf[IslandExport[P]]
}

/** A validation function attached to the exported function so props can be checked before hydration. Returns a reason if invalid */
private trait ValidatedComponent extends js.Object {
  var hx4sValidate: js.Function1[js.Any, js.UndefOr[String]]
}
