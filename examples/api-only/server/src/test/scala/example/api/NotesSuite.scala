package example.api

import munit.FunSuite

class NotesSuite extends FunSuite {
  test("text is validated after trimming surrounding whitespace") {
    assertEquals(Notes.validate("  memo "), Right("memo"))
    assertEquals(Notes.validate("   "), Left("Enter the note text"))
    assertEquals(Notes.validate("a" * 101), Left("Note text can be at most 100 characters"))
  }
}
