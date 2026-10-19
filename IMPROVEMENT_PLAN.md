# JUnit CTRF Reporter - Improvement Plan

Phases 1 to 4 are done and released: Phases 1 and 2 as 0.5.0, Phase 3 as 0.5.1, and Phase 4 as 0.5.2 on 2026-10-16. Their review, designs and decisions are in this branch's history, last complete at `99c1a6a`, and in PRs #124 to #156. This file keeps only what is left.

## Phase 5: One entry point (0.6.0)

Remove `CtrfExtension`, deprecated in 0.5.2 (#153), and keep `CtrfListener` as the only entry point. Tracked in #152.

**Status:** prepared in #157 (branch `feat/remove-extension`, four commits, all checks green), held until 0.6.0 is released. The listed removals, the docs and the POM's name and description are done there; the auto-registration decision is still open.

**When:** for 0.6.0, after 0.5.2's deprecation warning has been out for a while. Merge it shortly before that release: once the removal is on `master`, a 0.5.x fix can no longer be released from `master`.

- [ ] Remove the `jupiter` package (`CtrfExtension`, `TestRunExtension`, `ClassState`, `RunEndResource`) and `ExtensionContextAdapter`, and the manager code that only the extension needs
- [ ] Remove the extension's tests:
  - [ ] its unit tests and `Ext*` scenario fixtures;
  - [ ] its runs in the scenario tests, including the listener-and-extension agreement tests;
  - [ ] the `integration-tests-extension` module and its workflow;
  - [ ] compat's extension consumer.
- [ ] Update README, `ARCHITECTURE.md`, `CLAUDE.md` and `INTEGRATION_TESTS.md`; the release notes repeat the two migration steps
- [ ] Decide whether the jar registers the listener itself. Its own `META-INF/services` file would make the dependency the whole setup. Only one of today's two reasons against it remains, that every project with the jar would get a report, and a `ctrf.enabled=false` switch could answer it

**Done when:** the jar has no `jupiter` package; the listener's tests, scenarios, integration module and compat consumers pass; and the README describes one setup.

## Not scheduled

- **Attachments (#80):** fill a test's `attachments` from the files it publishes with JUnit's `TestReporter.publishFile`, which the listener receives through `fileEntryPublished`. Needs a design, because JUnit 5.10 and 5.11, which the reporter supports, have neither.
