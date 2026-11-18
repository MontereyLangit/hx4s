package example.server

/** One item in the list */
final case class Item(id: String, name: String, tags: List[String])

/** Sample items. Nothing is saved; only input is validated */
object Catalog {
  val items: List[Item] = List(
    Item("1", "apple", List("fruit", "red")),
    Item("2", "</script><b>tag</b> \"quoted\" 'single'", List("symbols", "<&>")),
    Item("3", "grape😀", Nil)
  )

  def find(id: String): Option[Item] = items.find(_.id == id)

  /** Items having all the tags. All items if tags is empty */
  def withTags(tags: List[String]): List[Item] = items.filter(item => tags.forall(item.tags.contains))

  /** Validates a new name. Returns a reason for empty or too long names */
  def rename(name: String): Either[String, String] = {
    val trimmed = name.trim
    if (trimmed.isEmpty) Left("Enter an item name")
    else if (trimmed.length > 40) Left("Item names can be at most 40 characters")
    else Right(trimmed)
  }
}
