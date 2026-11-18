# ADR 0001: Name and publishing coordinates

- Status: Accepted (2026-11-16, Phase 0)
- Updated (2026-11-16, Phase 3): the idea in "Consequences" of automatically accepting the old banner during a transition period was not adopted. The generated marker is a single setting; to replace generated files with the old banner,
  the user sets `hx4sBanner` / `hx4sGeneratedMarker` to the old values ("Settings" in [codegen.md](../codegen.md), [build-integration.md](../build-integration.md) 3.3)
- Related: [roadmap.md](../roadmap.md) U-1, U-2, U-20

## Context

3ch's Scala.js × HonoX integration layer is written assuming the Scala package `io._3ch.*`, the old project name `ch3s`, and the file paths `bbs/` and `bbs-client/`.
Publishing it as independent OSS requires a name and coordinates unrelated to the bulletin board.

## Decision

| Item | Value |
| --- | --- |
| Official name (provisional) | `hx4s` (HonoX for Scala) |
| Scala package | `hx4s.*` (`hx4s.annotation`, `hx4s.react`, `hx4s.hono`, `hx4s.codegen`, `hx4s.sbt`) |
| Maven organization (provisional) | `io.github.montereylangit` |
| Artifact names | `hx4s-core`, `hx4s-react`, `hx4s-codegen`, `sbt-hx4s` |
| npm | `hx4s-vite` (scope undecided, U-1) |

Don't leave `io._3ch` / `ch3s` / `3ch` in anything published (sources, banners of generated files, environment variable names, error messages).
Example: the test environment variable `CH3S_CLIENT_JS_DIR` becomes `HX4S_CLIENT_JS_DIR`.

## Rejected alternatives

| Alternative | Reason |
| --- | --- |
| Publish as `io._3ch.framework.*` | The bulletin board's name remains and means nothing to users unrelated to 3ch |
| Make the package the same as the organization, `io.github.montereylangit.hx4s.*` | Long. The organization may change (U-2), but changing the package changes all user code. Keep the package short and stable: `hx4s` |
| Fix npm to the scoped `@hx4s/vite` from the start | Availability of the scope is not confirmed. Move to it once acquired (U-1) |

## Consequences

- From Phase 2, when the subprojects are created inside 3ch, use the `hx4s.*` packages. 3ch's imports change at that point.
- The banner text of generated files changes, so the detection of generated files also accepts the old banner during the transition period ([build-integration.md](../build-integration.md) 3.3).
- If the name collision check (U-20) finds a problem, revise this ADR.
