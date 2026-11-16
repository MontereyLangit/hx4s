package hx4s.react

import hx4s.props.{JsField, JsShape}
import japgolly.scalajs.react.*
import japgolly.scalajs.react.vdom.VdomNode
import japgolly.scalajs.react.vdom.html_<^.*

import scala.scalajs.js
import scala.scalajs.js.JSConverters.*

/** client が @JSExportTopLevel で公開する island のコンポーネント。P は同じ宣言の @islands[P] と同じ props の型にする */
@js.native
sealed trait IslandExport[P <: js.Object] extends js.Object

object IslandExport {

  /** props を fields で確かめてから render で描画する island を作る。不正な props では console.error を出して何も描画しない */
  def apply[P <: js.Object](label: String, fields: List[JsField])(render: P => VdomNode): IslandExport[P] = {
    val component = ScalaFnComponent.withDisplayName(label)[js.Object] { raw =>
      JsShape.read[P](label, raw, fields).fold[VdomNode](EmptyVdom)(render)
    }
    val exported = component.toJsComponent.raw.asInstanceOf[ValidatedComponent]
    exported.hx4sValidate = (raw: js.Any) => JsShape.validate[P](raw, fields).left.toOption.orUndefined
    exported.asInstanceOf[IslandExport[P]]
  }

  /** 検証せずに描画する island を作る。props の形はサーバーと client の約束だけで保たれる */
  def unchecked[P <: js.Object](label: String)(render: P => VdomNode): IslandExport[P] =
    ScalaFnComponent.withDisplayName(label)[P](props => render(props)).toJsComponent.raw.asInstanceOf[IslandExport[P]]
}

/** hydrate の前に props を確かめられるよう、export した関数に付ける検証関数。不正なら理由を返す */
private trait ValidatedComponent extends js.Object {
  var hx4sValidate: js.Function1[js.Any, js.UndefOr[String]]
}
