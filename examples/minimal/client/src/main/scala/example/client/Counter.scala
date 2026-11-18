package example.client

import example.shared.CounterProps
import japgolly.scalajs.react.*
import japgolly.scalajs.react.vdom.html_<^.*

/** A counter that increases on click */
object Counter {
  final val Component = ScalaFnComponent[CounterProps] { props =>
    for {
      count <- useState(props.start)
    } yield <.button(^.className := "counter", ^.`type` := "button", ^.onClick --> count.modState(_ + 1), s"${props.label}: ${count.value}")
  }
}
