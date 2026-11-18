# ADR 0004: Keep using scalameta syntax analysis for code generation

- Status: Accepted (2026-11-16, Phase 0)
- Related: [build-integration.md](../build-integration.md) chapter 3, [routing-design.md](../routing-design.md) 1.1 and 2.2

## Context

3ch's `codegen` parses `object Main` with scalameta 4.17.4 (the `_2.13` artifact used from Scala 3), and builds the wiring files from the string literals of annotations
and the arguments of `@JSExportTopLevel`. The generated files are needed before the Vite build (HonoX globs `app/routes` and `app/islands`),
and making them depend on Scala compilation results would complicate the ordering.

## Decision

- Generation uses scalameta syntax analysis. No semantic analysis (type resolution, tracking imports).
- Annotation arguments are limited to string literals. The `P` of `@islands[P]` copies the syntax as-is.
- For generated files, the following hold:
  - Deterministic output (ordering, `\n` line endings).
  - Files with identical content are not rewritten (compared after normalizing line endings).
  - Files without the generated banner are never overwritten or deleted.
  - Nothing is written outside the output root (checked on normalized paths, in addition to checking the segments of the input path).
  - Conflicts between paths that differ only in case are detected.
  - All checks finish before writing.

## Rejected alternatives

| Alternative | Reason |
| --- | --- |
| Scala 3 macros | Types can be resolved, but writing TS to `app/` during compilation is a side effect. Generated files are needed before the Vite build, so making Scala compilation a prerequisite of generation breaks the ordering |
| sbt source generators (analysis inside sbt itself) | sbt 1.x is Scala 2.12. The generator would have to be rewritten for 2.12, or two lines maintained. As an entry point, it is used by calling `hx4sGenerate` from `sourceGenerators` |
| A compiler plugin | Depends on Scala 3 compiler internals and is heavy to maintain across versions |
| Scanning sources with regular expressions | Can't handle multi-line annotations, comments, or false positives inside strings |

## Consequences

- Props types written via type aliases or imported aliases can't be resolved. Fully qualified names are required (U-11).
- Constants (`final val`) and expressions can't be used as arguments. P1's `hx4s-routing`, which shares route definitions as values, either has to be limited to a DSL traceable by syntax, or macros need reconsidering ([routing-design.md](../routing-design.md) 4.2).
- Codegen can't check the contents of props traits (whether the types are JSON-compatible).
- Keep using the scalameta `_2.13` artifact from Scala 3. Consider migrating when a Scala 3 native version of scalameta is released.
