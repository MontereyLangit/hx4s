# Scala アプリケーションのソース構成

[English](../../docs/source-organization.md)

はじめは[実行できる小さなサンプル](examples.md)を参考にできる。規模が大きくなったら、HTTP ハンドラを `handlers/`、ページの描画を `views/`、再利用する UI を `components/`、クライアント側の hydration の入口を `islands/` に置くと役割を見分けやすい。いずれも任意の Scala パッケージ名。hx4s は設定されたソースファイル内のトップレベルオブジェクトのメンバーに付いたアノテーションを読み取り、ディレクトリ名やパッケージ名、ファイル名を問わない。

Scala の `handlers/` にはリクエストを処理する関数を置く。HonoX は `app/routes/` のルートモジュールを読み込み、URL と HTTP メソッドを登録する。たとえば `@route("auth/index")` から生成される `app/routes/auth/index.ts` は、Scala.js が公開したハンドラに処理を委ねる。同様に、Scala の `islands/` にはクライアント側の実装を置ける。生成される `app/islands/` のモジュールは、HonoX が見つけられるように Scala.js のエントリーポイントを再 export する。Scala ファイルを別のパッケージに移しても、アノテーションに指定したパスはそのまま使える。

| Scala コード | 主な役割 |
| --- | --- |
| ハンドラ | リクエストを解析・検証し、処理を委ね、HTTP ステータスを選び、HTML や JSON を返す。 |
| ビュー | 明示的な入力からページ全体の SSR 用 UI を組み立てる。データの取得は、できる限り描画の前に済ませる。 |
| コンポーネント | 再利用できる UI の一部分を描画する。依存関係が許す範囲でモジュール間で共有できる。 |
| クライアント island | `@islands[P]` で対話的な UI の hydration の入口を公開する。境界を明示し、補助コンポーネントや内部パッケージは同じ境界の中で使える。 |
| アプリケーションのユースケース（任意） | 複数の処理、副作用、トランザクションを調整する。 |
| ドメイン（任意） | 業務上の不変条件、エンティティ、値オブジェクト、ポリシーを表現する。 |
| リポジトリ・アダプタ（任意） | データアクセスのインターフェースを定義したり、永続化を実装したりする。 |

`HomeHandler` や `HomeView` という名前からは、HTTP 処理とページ描画の役割が読み取れる。コンポーネントには `Header` や `UserProfileCard` のように、UI での役割を表す名前が分かりやすい。`Component`、`Handler`、`View` はいずれも任意の接尾辞。ファイル名やオブジェクト名はプロジェクト内の慣例に合わせて決められる。

## 小規模アプリケーション

`server/src/main/scala/example/Main.scala` 一つにハンドラとページ描画をまとめられる。API 専用なら、Scala の HTTP 処理は `server/src/main/scala/example/handlers/ApiHandler.scala` だけでもよい。ディレクトリや抽象化は必要になった時点で追加する。

```text
server/src/main/scala/example/
└── Main.scala
```

API 専用の場合の例。

```text
server/src/main/scala/example/
└── handlers/
    └── ApiHandler.scala
```

実行できる minimal サンプルでは、`Main.indexPage` が `Pages.index().rawElement` を `c.renderPromise` に渡す。ビューは `IslandComponents.CounterJs` に型付きの `CounterProps` を渡し、SSR で hydration 境界を作る。クライアント側の export は `@islands` に同じ props の型を指定し、HonoX は `app/islands/counter.tsx` を通じてカウンターを hydrate する。テストでは、クリックすると SSR 時の DOM ノードがそのまま更新されることを確かめている。 [実行方法](examples.md#minimal) · [ハンドラ](../../examples/minimal/server/src/main/scala/example/server/Main.scala) · [ビュー](../../examples/minimal/server/src/main/scala/example/server/Pages.scala) · [クライアント側の export](../../examples/minimal/client/src/main/scala/example/client/Main.scala) · [hydration テスト](../../examples/minimal/tests/hydration.test.js)

## 中規模アプリケーション

HTTP 処理とページ描画が増えてきたら、役割ごとに分けられる。単純な CRUD なら、ハンドラからリポジトリを直接呼び出せる。

```text
server/src/main/scala/example/
├── handlers/
│   ├── HomeHandler.scala
│   ├── UserHandler.scala
│   └── ApiHandler.scala
├── views/
│   ├── HomeView.scala
│   └── UserDetailView.scala
├── components/
│   ├── layouts/
│   │   ├── AppLayout.scala
│   │   └── Header.scala
│   └── UserProfileCard.scala
└── repositories/                 # 任意
    └── UserRepository.scala

client/src/main/scala/example/
└── islands/
    └── UserProfileEditor.scala

app/
├── routes/                       # HonoX のルートモジュール
└── islands/                      # HonoX の island エントリーモジュール
```

## 大規模アプリケーション

エンドポイントが増えてきたら、ハンドラのメソッドに `@route` を付けると、ルートと処理の対応が分かりやすい。トップレベルの `AuthHandler` は、設定対象のサーバーソースファイルであればどこからでもハンドラを公開できる。アプリケーションに `AppContext` と `AuthView` がある場合の例を示す。

```scala
import hx4s.annotation.route
import hx4s.hono.Context.*
import org.scalajs.dom.Response
import scala.scalajs.js
import scala.scalajs.js.annotation.JSExportTopLevel

object AuthHandler {
  @route("auth/index")
  @JSExportTopLevel("authPageHandler", "server")
  def page(c: AppContext): js.Promise[Response] =
    c.renderPromise(AuthView.page().rawElement)
}
```

### レイヤー別の構成・Clean Architecture・DDD

中規模の例をもとに、ドメインの概念やユースケースを業務領域ごとにまとめる方法もある。ここで追加するレイヤーはいずれも任意。

```text
server/src/main/scala/example/
├── handlers/
│   └── UserHandler.scala
├── views/
│   └── UserDetailView.scala
├── components/
│   └── UserProfileCard.scala
├── application/
│   └── user/
│       ├── GetUser.scala
│       └── RegisterUser.scala
├── domain/
│   └── user/
│       ├── User.scala
│       ├── UserId.scala
│       ├── UserName.scala
│       ├── UserRepository.scala
│       └── UserRegistrationPolicy.scala
└── infrastructure/
    └── persistence/
        └── D1UserRepository.scala

client/src/main/scala/example/islands/
└── UserProfileEditor.scala

shared/src/main/scala/example/props/
└── UserProfileProps.scala

app/
├── routes/
└── islands/
```

関連する概念を `domain/user/` のようなパッケージにまとめる。たとえば `User` エンティティは識別子で同一性を判断し、値オブジェクトの `UserName` は値で比較できる。DB の行や ORM エンティティは永続化の形式を表す。ドメインのルールは、できる限り Hono や Cloudflare、DB API への依存から切り離す。

アプリケーションサービスやユースケースは、ユーザー登録とそのトランザクションなど、一連の処理を調整する。ドメインサービスやポリシーは、登録資格などの業務ルールを表す。`RegisterUser` のように処理の内容が伝わる名前を使える。リポジトリのポートはドメイン層かアプリケーション層に置き、実装をインフラストラクチャ層に置ける。シンプルなアプリならデータアクセスを `repositories/` にまとめてもよい。`services/`、`repositories/`、`application/`、`domain/`、`infrastructure/` は、役割を明確にできる場合に追加する。

### Vertical Slice Architecture

機能を軸にした構成も同様に選べる。機能や境界づけられたコンテキストごとにハンドラ、ビュー、ユースケースを近くに置き、共通の UI は必要に応じて共有する。

```text
server/src/main/scala/example/
├── features/
│   ├── users/
│   │   ├── UserHandler.scala
│   │   ├── UserDetailView.scala
│   │   ├── RegisterUser.scala
│   │   └── UserRepository.scala
│   └── boards/
│       ├── BoardHandler.scala
│       └── BoardView.scala
└── components/
    └── layouts/
        └── AppLayout.scala

client/src/main/scala/example/islands/
├── users/
│   └── UserProfileEditor.scala
└── boards/
    └── BoardEditor.scala
```

ユースケースを機能ごとにまとめつつ、ドメインモジュールや UI コンポーネントを共有する混合構成も使える。どの構成でも、密接に関連する処理は同じモジュールにまとめ、依存関係を明示する。ドメインを不変の値と純粋な変換で表現すると、業務ロジックを HTTP や DB の副作用から切り分けやすい。この functional core / imperative shell の構成も利用でき、OOP・DDD・FP の採用やエフェクトライブラリの選択はアプリケーションに委ねられる。props などの型は必要に応じて共有モジュールに置き、サーバー専用の依存関係をクライアントのバンドルに持ち込まないようにする。
