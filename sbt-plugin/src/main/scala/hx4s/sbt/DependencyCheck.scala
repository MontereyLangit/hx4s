package hx4s.sbt

import sbt.{ClasspathDep, ProjectRef}

import scala.annotation.tailrec

/** Checks forbidden transitive project dependencies using compile- and runtime-scoped classpath edges. */
private[sbt] object DependencyCheck {

  /** Reports paths from the client to any server and from the current server to the client. An absent client produces no diagnostics. */
  def problems(server: ProjectRef, client: Option[ProjectRef], servers: Set[ProjectRef],
               graph: Map[ProjectRef, Seq[ClasspathDep[ProjectRef]]]): List[String] = {
    client.toList.flatMap { clientRef =>
      val toServer = servers.toList.sortBy(_.project).flatMap(target => path(clientRef, target, graph).map { route =>
        s"hx4s: the client project ${clientRef.project} must not depend on the server project ${target.project} (${render(route)}). Put types shared by both, such as island props, in a project that neither depends on the other"
      })
      val toClient = path(server, clientRef, graph).map { route =>
        s"hx4s: the server project ${server.project} must not depend on the client project ${clientRef.project} (${render(route)}). The server reads client islands only through the virtual module"
      }
      toServer ++ toClient
    }
  }

  private def render(route: List[ProjectRef]): String = route.map(_.project).mkString(" -> ")

  /** Returns the shortest eligible dependency path including both endpoints, or `None` for identical or unreachable endpoints. */
  def path(from: ProjectRef, to: ProjectRef, graph: Map[ProjectRef, Seq[ClasspathDep[ProjectRef]]]): Option[List[ProjectRef]] = {
    @tailrec
    def search(queue: List[List[ProjectRef]], seen: Set[ProjectRef]): Option[List[ProjectRef]] = queue match {
      case Nil => None
      case route :: rest =>
        val next = graph.getOrElse(route.head, Nil).filter(dep => compileScoped(dep.configuration)).map(_.project).filterNot(seen)
        next.find(_ == to) match {
          case Some(found) => Some((found :: route).reverse)
          case None => search(rest ++ next.distinct.map(_ :: route), seen ++ next)
        }
    }
    if (from == to) None else search(List(List(from)), Set(from))
  }

  /** Checks whether a `dependsOn` mapping includes a `compile`, `runtime` or empty source scope. An absent mapping is eligible. */
  def compileScoped(configuration: Option[String]): Boolean =
    configuration.forall(_.split(";").exists { mapping =>
      Set("compile", "runtime", "")(mapping.split("->").headOption.getOrElse("").trim)
    })
}
