package example.api

/** メモ 1 件 */
final case class Note(id: String, text: String)

/** サンプルのメモ。保存はせず、入力の検証だけを行う */
object Notes {
  val all: List[Note] = List(Note("1", "牛乳を買う"), Note("2", "本を返す"))

  def find(id: String): Option[Note] = all.find(_.id == id)

  /** 新しいメモの本文を確かめる。空や長すぎる本文は理由を返す */
  def validate(text: String): Either[String, String] = {
    val trimmed = text.trim
    if (trimmed.isEmpty) Left("本文を入力してください")
    else if (trimmed.length > 100) Left("本文は 100 文字までです")
    else Right(trimmed)
  }
}
