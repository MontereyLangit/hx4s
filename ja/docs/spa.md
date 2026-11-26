# SPA

**hx4s に SPA 用の機能はまだ無い。** クライアント側のルーティング（Client Router）・型付きルート・型付き API クライアントは未実装で、ロードマップにある（[hx4s/roadmap.md](hx4s/roadmap.md)）。
この文書は、今ある部品だけで成り立つ構成と、その限界を書く。

## 今できる構成: ページ全体を 1 つの island にする

ページの内容を 1 つの island にして、描画のあとに JSON の API を呼ぶ。API は `@route` で書く（[api.md](api.md)）。

```
GET /spa          → シェルの HTML と、ページ全体を包んだ island（SSR）
GET /api/items    → JSON を返す @route
island（ブラウザ）→ マウント後に /api/items を fetch して一覧を描画する
```

次のコードは `examples/minimal` を土台に試作して、ビルド・SSR・hydration・API 呼び出しを確かめたもの（`examples/minimal` には含まれていない）。

shared の props:

```scala
trait AppProps extends js.Object {
  val title: String
}

object AppProps {
  val fields: List[JsField] = List(required("title", JsKind.Str))
}
```

client の island。マウント時に 1 回だけ API を呼ぶ。

```scala
object App {
  final val Component = ScalaFnComponent.withHooks[AppProps]
    .useState(List.empty[String])
    .useEffectOnMountBy { (_, names) =>
      Callback.future(
        dom.fetch("/api/items").toFuture.flatMap(_.json().toFuture).map { json =>
          names.setState(json.asInstanceOf[js.Array[ItemProps]].toList.map(_.name))
        }
      )
    }
    .render((props, names) =>
      <.main(<.h1(props.title), <.ul(names.value.toVdomArray(name => <.li(^.key := name, name))))
    )
}

@islands[example.shared.AppProps]("app")
@JSExportTopLevel("AppJs", "client")
val app: IslandExport[AppProps] = IslandExport("AppJs", AppProps.fields)(props => App.Component(props))
```

（`import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue` と `org.scalajs.dom` が要る。）

server のページ:

```scala
@route("spa")
@JSExportTopLevel("spaPage", "server")
def spaPage(c: ApiContext): js.Promise[Response] =
  c.renderPromise(IslandComponents.AppJs(new AppProps { val title = "SPA" }).rawElement, new RenderProps { val title = "spa" })
```

確かめたこと（`wrangler dev` と Chromium）:

- JavaScript を無効にすると、SSR の部分（`<h1>SPA</h1>`）だけが表示される
- 有効にすると、island が hydrate され、`/api/items` の結果（3 件）が一覧に出る
- コンソールにエラー・警告は出ない

## 限界

| 項目 | 状態 |
| --- | --- |
| クライアント側のルーティング | **無い**。ページ遷移は通常のリンク（フルロード）で、ページごとに `@route` を書き、それぞれが island を返す。History API を使ったルーターは hx4s も提供せず、検証もしていない |
| scalajs-react の Router | hx4s とは統合していない。island の中で使えるかは検証していない |
| 型付きルート | 未実装。URL は文字列で、server の `@route` と client の `fetch` の URL を手で合わせる |
| API の型 | 未実装。レスポンスは `js.Any` で、`asInstanceOf` するか `JsShape.validate` で自分で確かめる。型付き API クライアントも OpenAPI も無い |
| ページ全体の hydrate の費用 | ページの全データが `data-serialized-props` に入り、HTML 本体と二重に送られる。属性値のエスケープで JSON が膨らむ（[hx4s/rendering-model.md](hx4s/rendering-model.md) 1.2） |
| 全パスを 1 つのシェルに向ける構成（SPA のフォールバック） | 試していない |
| 要素 props を持つ island | 検証していない（[islands.md](islands.md)） |

つまり今は、「複数ページのサイトで、ページの一部またはページ全体を island にし、その中から API を呼ぶ」構成までが使える。
アプリケーション全体を 1 つの client ルーターで動かす構成は、Client Router が入るまで作れない。
