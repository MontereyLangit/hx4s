package example.api

import munit.FunSuite

class NotesSuite extends FunSuite {
  test("本文は前後の空白を除いて確かめる") {
    assertEquals(Notes.validate("  メモ "), Right("メモ"))
    assertEquals(Notes.validate("   "), Left("本文を入力してください"))
    assertEquals(Notes.validate("あ" * 101), Left("本文は 100 文字までです"))
  }
}
