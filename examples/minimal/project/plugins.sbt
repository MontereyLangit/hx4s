// hx4s は scripts/publish-local.mjs で ~/.ivy2/local に公開したものを使う。
// 版は環境変数 HX4S_VERSION、無ければ公開のときに書かれる版のファイルから読む
val hx4sPublishedVersion: String = sys.env.getOrElse("HX4S_VERSION", {
  val versionFile = file("../../target/local/hx4s-version.txt")
  if (!versionFile.exists) sys.error("hx4s is not published locally. Run `node scripts/publish-local.mjs` in the hx4s repository first.")
  IO.read(versionFile).trim
})

addSbtPlugin("io.github.montereylangit" % "sbt-hx4s"                 % hx4sPublishedVersion)
addSbtPlugin("org.scala-js"             % "sbt-scalajs"              % "1.22.0")
addSbtPlugin("org.portable-scala"       % "sbt-scalajs-crossproject" % "1.3.2")
