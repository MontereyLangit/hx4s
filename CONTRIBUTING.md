# Contributing

Changes to hx4s (bug fixes, features, documentation) are welcome. hx4s 0.1 is in development, and the API and settings may change. For large changes, please discuss the approach in an issue first.

## Development environment

| Tool | Version |
| --- | --- |
| JDK | 17 or later (CI uses 21) |
| sbt | 1.x (`project/build.properties` is 1.13.0) |
| Node.js | 22.12 or later |

```bash
git clone <hx4s repository> && cd hx4s
npm ci
```

Branch from `develop`.

## Build and test

```bash
sbt test                      # munit for core / react / codegen
sbt sbtHx4s/scripted          # scripted tests for sbt-hx4s (runs after publishLocal of codegen)
sbt 'set ThisBuild / scalacOptions += "-no-indent"' Test/compile   # checks that no indentation syntax remains
npm test                      # hx4s-vite build, island SSR and hydration, independence check
npm run typecheck             # type-check hx4s-vite and the TypeScript examples in the docs (tests/docs-typecheck)
node scripts/check-independence.mjs
```

Running the examples requires a local publish. The tests read the build output (`dist`), so run `npm run build` before `npm test`.
The E2E tests of `examples/minimal` use Playwright's Chromium (`HX4S_SKIP_E2E=1` skips only the E2E tests).

```bash
node scripts/publish-local.mjs
node scripts/setup-example.mjs minimal
cd examples/minimal
npx playwright install chromium   # first time only
npm run build
npm run typecheck
npm test
cd ../..

node scripts/setup-example.mjs api-only
cd examples/api-only
npm run build
npm run typecheck
npm test
```

CI (`.github/workflows/ci.yml`) runs them in the same order.

See [docs/testing.md](docs/testing.md) for what the tests cover and [docs/examples.md](docs/examples.md) for the examples.
When you change code, add the relevant tests and make sure the commands above pass before submitting. CI runs the same steps on Ubuntu and Windows.

## Generated files

The generated files in `examples/minimal/app/routes`, `app/islands` and `examples/api-only/app/routes` are committed. When you change `@route` / `@islands` or the codegen output, run `npm run generate` in the examples and include the diff
(CI checks that each example's `app` has no diff or untracked files after the build).

## Structure and dependencies

| Module | Location | Language |
| --- | --- | --- |
| hx4s-core | `modules/core` | Scala 3 (JVM and Scala.js) |
| hx4s-react | `modules/react` | Scala.js |
| hx4s-codegen | `modules/codegen` | Scala 3 (JVM) |
| sbt-hx4s | `sbt-plugin` | Scala 2.12 (sbt 1.x plugin) |
| hx4s-vite | `vite` | TypeScript |

- **Don't add dependencies.** For a new Maven / npm dependency, explain in the issue or PR why existing ones are not enough and what its license is (whether it is compatible with Apache-2.0). Pin versions
- `hx4s-codegen` does not depend on `hx4s-core` (it recognizes annotations by name)
- The hx4s modules do not depend on a runtime (such as Cloudflare). Runtime-specific code goes in the examples or in the user's project
- Don't write logic in TypeScript that can be written in Scala. TypeScript is limited to what can't be written in Scala, such as wiring Vite and HonoX

## Coding conventions

- Follow the style, naming and placement of the existing code
- **As a rule, don't use Scala's optional braces (indentation syntax).** Use `{}`. Control syntax such as `if ... then` is fine, but wrap multiple lines in `{}` (CI checks compilation with `-no-indent`)
- Prefer FP in design. Separate pure computation from I/O, and return rejections and failures as `Either` or enums instead of exceptions. Don't use mutable state or kinds represented as strings more than necessary
- Make scalajs-react components function components (`ScalaFnComponent`), and make `val`s holding components `final val`
- Prefer Scala.js libraries, when they exist, over facades for npm packages
- Avoid TypeScript's `any` and Scala's untyped structural types. Fix compiler and lint warnings instead of suppressing them
- **Keep Scaladoc / JSDoc short**: in one or two lines, state what it does and the constraints users need to know. Write them in English, without a trailing period. History, rejected alternatives and measurements go in `docs/hx4s/`
- Don't add unnecessary comments or excessive abstraction
- Don't put secrets (keys, tokens, passwords) in code, logs or commit messages

## Documentation

- User documentation goes directly under `docs/`, and design and history documents go in `docs/hx4s/`, both in English, with the Japanese translation at the same path under `ja/`. Use a concise technical style (avoid long lists of explanations and ornate wording; use tables, short paragraphs and code examples). When you change a document, update its Japanese translation too
- **Only include code examples that have been verified against real code.** Either write them the same way as `examples/minimal`, or actually compile and run them. Don't describe unverified features as if they work
- Say so when something is not implemented or not verified. Don't make performance claims. Only cite numbers recorded in `docs/hx4s/reviews/`, together with their conditions
- Check that relative links are not broken
- Don't put the names of specific applications in user documentation or code (`node scripts/check-independence.mjs` checks this, except for the history in `docs/hx4s/`)

## Commits

Use the [Conventional Commits](https://www.conventionalcommits.org/) format.

```
<type>(<scope>): <summary of the change>
```

| type | Use |
| --- | --- |
| `feat` | New features |
| `fix` | Bug fixes |
| `docs` | Documentation-only changes |
| `refactor` | Cleanups that don't change behavior (add `!`, as in `refactor!:`, when breaking compatibility) |
| `build` / `ci` / `chore` | Build settings, CI, everything else |

Scopes include `core`, `react`, `codegen`, `sbt`, `vite`, `scripts`, `examples` and `deps`. Example: `fix(codegen): fix output safeguards and handling of identifiers and settings in generated code`.
Keep each commit to one purpose, and explain the reason for the change in the body.

## Review flow

1. Branch from `develop` and make your changes
2. Pass "Build and test" above and review your own change (consistency with the conventions, nothing written in TypeScript that could be in Scala, sufficient tests, documentation style)
3. Open a pull request against `develop`. Describe the purpose, the changes, what you verified and what you did not verify
4. Make sure CI (Ubuntu and Windows) passes, and address review comments
5. For design decisions (public API, format of generated files, dependencies), record an ADR in `docs/hx4s/adr/` (one decision per file)

## License

Contributions are provided under the [Apache License 2.0](LICENSE) (Section 5 of the license).
