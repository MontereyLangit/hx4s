package hx4s.react

import hx4s.props.{JsField, JsShape}
import japgolly.scalajs.react.*
import japgolly.scalajs.react.vdom.VdomNode
import japgolly.scalajs.react.vdom.html_<^.*

import scala.scalajs.js
import scala.scalajs.js.JSConverters.*

/**
 * JavaScript component published as a client island with `@JSExportTopLevel`.
 *
 * @tparam P Props facade corresponding to the type argument of `@islands` on the export.
 */
@js.native
sealed trait IslandExport[P <: js.Object] extends js.Object

/** Creates JavaScript React components for client island exports. */
object IslandExport {

  /**
   * Creates a component that validates before rendering and exposes a validator for hydration.
   *
   * Both validation paths use the same field descriptors. The hydration hook
   * returns a diagnostic before React hydrates an island with invalid props.
   * During rendering, validation failure is logged and produces empty output.
   *
   * @tparam P Props facade corresponding to `fields`.
   * @param label React display name and prefix for validation diagnostics.
   * @param fields Properties checked during rendering and before hydration.
   * @param render Renders props that passed validation.
   * @return React component suitable for publication via `@JSExportTopLevel`.
   */
  def apply[P <: js.Object](label: String, fields: List[JsField])(render: P => VdomNode): IslandExport[P] = {
    val component = ScalaFnComponent.withDisplayName(label)[js.Object] { raw =>
      JsShape.read[P](label, raw, fields).fold[VdomNode](EmptyVdom)(render)
    }
    val exported = component.toJsComponent.raw.asInstanceOf[ValidatedComponent]
    exported.hx4sValidate = (raw: js.Any) => JsShape.validate[P](raw, fields).left.toOption.orUndefined
    exported.asInstanceOf[IslandExport[P]]
  }

  /**
   * Creates a component that passes incoming props directly to the renderer.
   *
   * @tparam P Props facade shared by the server and client.
   * @param label React display name.
   * @param render Renders the incoming props.
   * @return Exportable component without an attached hydration validator.
   */
  def unchecked[P <: js.Object](label: String)(render: P => VdomNode): IslandExport[P] =
    ScalaFnComponent.withDisplayName(label)[P](props => render(props)).toJsComponent.raw.asInstanceOf[IslandExport[P]]
}

/** Facade for the pre-hydration hook attached to a validated export. */
private trait ValidatedComponent extends js.Object {
  /** Returns a diagnostic for invalid props or `undefined` when validation succeeds. */
  var hx4sValidate: js.Function1[js.Any, js.UndefOr[String]]
}
