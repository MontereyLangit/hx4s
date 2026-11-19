package example.server

/** A catalog entry with its lookup ID, display name and tags. */
final case class Item(id: String, name: String, tags: List[String])

/** Provides a fixed catalog and validates submitted names. */
object Catalog {
  /** Sample entries in display order. */
  val items: List[Item] = List(
    Item("1", "apple", List("fruit", "red")),
    Item("2", "</script><b>tag</b> \"quoted\" 'single'", List("symbols", "<&>")),
    Item("3", "grape😀", Nil)
  )

  /** Returns the item with the given ID, or `None`. */
  def find(id: String): Option[Item] = items.find(_.id == id)

  /** Returns entries containing every requested tag. An empty tag list matches all entries. */
  def withTags(tags: List[String]): List[Item] = items.filter(item => tags.forall(item.tags.contains))

  /** Returns the trimmed name or a diagnostic for empty names or names over 40 UTF-16 code units. Leaves the catalog unchanged. */
  def rename(name: String): Either[String, String] = {
    val trimmed = name.trim
    if (trimmed.isEmpty) Left("Enter an item name")
    else if (trimmed.length > 40) Left("Item names can be at most 40 characters")
    else Right(trimmed)
  }
}
