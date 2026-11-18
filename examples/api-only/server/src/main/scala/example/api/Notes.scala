package example.api

/** One note */
final case class Note(id: String, text: String)

/** Sample notes. Nothing is saved; only input is validated */
object Notes {
  val all: List[Note] = List(Note("1", "Buy milk 🥛"), Note("2", "Return the book"))

  def find(id: String): Option[Note] = all.find(_.id == id)

  /** Validates the text of a new note. Returns a reason for empty or too long text */
  def validate(text: String): Either[String, String] = {
    val trimmed = text.trim
    if (trimmed.isEmpty) Left("Enter the note text")
    else if (trimmed.length > 100) Left("Note text can be at most 100 characters")
    else Right(trimmed)
  }
}
