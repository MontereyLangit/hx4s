package example.api

/** A sample note with its lookup ID and display text. */
final case class Note(id: String, text: String)

/** Provides a fixed set of notes and validates text for the create handler. */
object Notes {
  /** Sample notes in response order. */
  val all: List[Note] = List(Note("1", "Buy milk 🥛"), Note("2", "Return the book"))

  /** Returns the note with the given ID, or `None`. */
  def find(id: String): Option[Note] = all.find(_.id == id)

  /** Trims the input and returns the result or a diagnostic if it is empty or longer than 100 UTF-16 code units. */
  def validate(text: String): Either[String, String] = {
    val trimmed = text.trim
    if (trimmed.isEmpty) Left("Enter the note text")
    else if (trimmed.length > 100) Left("Note text can be at most 100 characters")
    else Right(trimmed)
  }
}
