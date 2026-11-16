package example.server

/** 一覧の 1 件 */
final case class Item(id: String, name: String, tags: List[String])

/** サンプルの品目。保存はせず、入力の検証だけを行う */
object Catalog {
  val items: List[Item] = List(
    Item("1", "りんご", List("果物", "赤")),
    Item("2", "</script><b>タグ</b> \"引用\" 'single'", List("記号", "<&>")),
    Item("3", "ぶどう😀", Nil)
  )

  def find(id: String): Option[Item] = items.find(_.id == id)

  /** 新しい名前を確かめる。空や長すぎる名前は理由を返す */
  def rename(name: String): Either[String, String] = {
    val trimmed = name.trim
    if (trimmed.isEmpty) Left("名前を入力してください")
    else if (trimmed.length > 40) Left("名前は 40 文字までです")
    else Right(trimmed)
  }
}
