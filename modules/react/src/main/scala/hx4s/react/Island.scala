package hx4s.react

import japgolly.scalajs.react.facade.{HasMutableDisplayName, React}
import japgolly.scalajs.react.vdom.VdomElement
import scala.scalajs.js
import scala.scalajs.js.annotation.JSImport

private final class IslandProps[P <: js.Object](
  val Component: React.ComponentType[P],
  val props: P,
  val componentName: String
) extends js.Object

@js.native
@JSImport("honox/vite/components", "HonoXIsland")
private object HonoXIsland extends js.Function1[IslandProps[?], React.Node] with HasMutableDisplayName {
  def apply(props: IslandProps[?]): React.Node = js.native
}

/** Creates React elements for HonoX island hydration boundaries. */
object Island {
  /**
   * Wraps the component and its props in HonoX's `HonoXIsland` element.
   *
   * @tparam P JavaScript props type shared by the component and its hydration data.
   * @param component React component rendered inside the boundary.
   * @param props Props passed to the component and included in the hydration data.
   * @param componentName Generated island identifier, such as `/app/islands/counter.tsx`.
   * @return React element representing the island boundary.
   */
  def apply[P <: js.Object](component: React.ComponentType[P], props: P, componentName: String): VdomElement = {
    VdomElement(React.createElement(HonoXIsland, new IslandProps[P](component, props, componentName)))
  }
}
