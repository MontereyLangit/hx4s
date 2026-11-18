# hx4s design documents

English | [日本語](../../ja/docs/hx4s/README.md)

The user guides (usage, settings, API) are in [../README.md](../README.md). The documents below cover design and history.

hx4s is the plan to extract the Scala.js × HonoX integration layer of "3ch", a 2ch-compatible bulletin board, into an independent OSS meta-framework.
These were written in Phase 0 (design) and updated to match the implementation in Phase 3 (splitting into the hx4s repository, 2026-11-16). See [roadmap.md](roadmap.md) for the implementation status. "Current implementation" in these documents refers to the 3ch repository
(`ch3s`, as of 2026-11-16; reference commit `875a47b` for the investigation. 3ch's Phases 1 / 2 are in progress and the working tree may change).

| Document | Content |
| --- | --- |
| [architecture.md](architecture.md) | Findings from investigating 3ch's current implementation, the overall picture of hx4s, division of responsibilities with HonoX, places that depend on HonoX's internal APIs |
| [module-boundaries.md](module-boundaries.md) | File-level classification of framework and bulletin-board-specific code, dependency direction, public and internal APIs |
| [extraction-plan.md](extraction-plan.md) | Steps, verification conditions and completion criteria for Phases 0–5, the mapping of moved files |
| [rendering-model.md](rendering-model.md) | Where each rendering mode stands, hydration boundaries, constraints on props serialization, comparison of typed props approaches |
| [routing-design.md](routing-design.md) | Current state and extension policy of `@route`, scope of verification, P1 proposals for typed routes and a Client Router |
| [build-integration.md](build-integration.md) | Current state of sbt + Vite, responsibilities of hx4s-vite and sbt-hx4s, comparison of codegen and CLI approaches |
| [compatibility.md](compatibility.md) | Dependency versions, policy for tracking the HonoX alpha, licenses |
| [roadmap.md](roadmap.md) | P0 / P1 / P2 features and their status, open questions |
| [codegen.md](codegen.md) | hx4s-codegen declaration rules, output rules, error format, settings |
| [local-dependency.md](local-dependency.md) | Local publish records and verification, steps for pinning the version and commit in a user project, and an example setup script |
| [reviews/phase3-review.md](reviews/phase3-review.md) | Phase 3 verification results and self-review |
| [reviews/phase4-feedback.md](reviews/phase4-feedback.md) | The first user migration (Phase 4) and responses to Codex's findings |

ADRs (one decision per file):

| ADR | Decision |
| --- | --- |
| [0001](adr/0001-name-and-coordinates.md) | Name and publishing coordinates (`hx4s`, `hx4s.*`, `io.github.montereylangit`, `hx4s-vite`) |
| [0002](adr/0002-initial-modules.md) | Initial module structure (core / react / codegen / sbt-hx4s / hx4s-vite / examples) |
| [0003](adr/0003-typed-island-props.md) | Typed props are non-native JS traits + `@islands[P]` |
| [0004](adr/0004-scalameta-codegen.md) | Keep using scalameta syntax analysis for code generation |
| [0005](adr/0005-core-independent-of-cloudflare.md) | Core does not depend on Cloudflare-specific APIs |
| [0006](adr/0006-one-way-dependency-and-local-publish.md) | One-way dependency 3ch → hx4s, and local dependencies right after the split |
| [0007](adr/0007-props-validation-and-typed-export.md) | Props validation in the JS side of hx4s-core, typed export as `IslandExport[P]` in hx4s-react |

The underlying approach (no from-scratch reimplementation; improve and verify in 3ch before extracting) is in
[extraction-plan.md](extraction-plan.md), and the policy of not requiring an Effect System or DI is in section 3.4 of [architecture.md](architecture.md).
