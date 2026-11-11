# Implementation gaps and follow-up work

[Developer documentation](README.md)

Reviewed against this checkout on 2026-11-11. This is a maintenance ledger, not a
release plan. Entries distinguish direct source stubs from follow-up work recorded
in subsystem notes. User guides retain only the operational boundaries needed to
complete a task safely. No new application build or live-service verification is
claimed by this documentation revision.

## Source-confirmed gaps

Paths below are relative to `src/main/java/org/integratedmodelling/klab/ide`.

| Area | Evidence and remaining work | User consequence |
| --- | --- | --- |
| Twin access-rights editing | `components/DigitalTwinView.createDigitalTwinDialog()` opens an empty Public Access Settings dialog | Creation displays initial rights but does not provide an effective access editor |
| Twin persistence UX | `components/DigitalTwinEditor.close()` retains ONE_OFF and IDLE_TIMEOUT warning/countdown TODOs | Do not promise close warnings or countdowns |
| Quality filesystem export | `components/DigitalTwinEditor.exportToFilesystem()` only logs the request; `components/cards/ExportCard` is a stub | The visible export command does not save a quality file |
| Scenario catalog | `components/treeviews/ScenarioTree` has no populated scenario source | The Scenarios panel is not a usable catalog |
| Repository detach/untrack | `components/WorkspaceEditor` has an empty detach handler and an Untrack TODO | Do not document detach as a working repository action |
| Newly created documents | `components/WorkspaceEditor` still marks automatic opening as disabled because of a UI hang | Users open the new document from the tree |
| Controller contracts | `KlabIDEController.confirm()` returns false; `log()` and `cleanWorkspace()` are empty | Shared controller contracts need completion; dedicated feature confirmations must be evaluated separately |
| Behavior compilation | `components/BehaviorEditor.compile()` area retains a serialized-executor FIXME | Compilation needs managed scheduling; publication itself is implemented |
| Debug execution controls | `components/AgentDebuggerView.createControlBar()` creates disabled Resume/Pause/Step buttons | Message tracing and manual message submission work; step/pause debugging is not available |
| Twin idle/error surface | `components/DigitalTwinControlPanel` still clears content for some idle/error paths | Improve explanatory feedback and recovery without losing activity state |

## Follow-up work retained from subsystem notes

- **Composer:** profile actual slow concept/authority insertion before choosing a
  cache strategy. Authority search currently shows the first bounded result page;
  paging, embedded authenticated media, and live binding refresh remain design
  work. Current authority browsing already includes catalog/rank/codelist filters,
  checked cached previews, and approved-name/raw-code toggles, superseding the older
  filter/cache proposals in [composer integration](SEMANTIC_COMPOSER.md).
- **Observer geometry:** the current editor supports WGS84 viewport rectangles.
  Polygon drawing, trimming/clearing, antimeridian edits, and temporal editing are
  follow-up work. Automatic user-behavior attachment was recorded as pending in
  the prior guide and needs backend verification before any availability claim.
- **Proposal review:** durable draft saving, whole-application shutdown recovery,
  nonblocking transition transport, richer shared lexical anchors, and live HTTP
  acceptance coverage remain follow-up work. Production authoritative import,
  schema, and Reasoner checks can remain blocked; never synthesize readiness from
  client-side counts. Review decisions do not implement ontology application or
  PR publication. See [proposal integration](PROPOSAL_REVIEW.md).
- **Maps and point queries:** support depends on runtime exports. A typed point-value
  contract with units, locator, no-data, and cancellation semantics remains a
  backend/API design concern. Do not present generic text export as universal lookup.
- **Settings transactions:** project settings span manifest and service catalog
  rights with rollback attempts rather than crash-atomic storage. Document and test
  partial-failure recovery as service behavior.
- **Feedback:** some controller service tooltips retain FIXME notes. Distribution
  synchronization, integrity checks, deletion, and guarded switching with progress
  are implemented; the controller's switching TODO comment is stale and must not
  be interpreted as an absent feature.

## Obsolete limitations removed from user documentation

These are implemented and must not be copied back from the historical architecture
audit: Worldview browsing; resource persistence/publication; standalone behavior
publication; managed mirrors; workspace and project settings; branch selection;
semantic source validation; observer discovery/selection and perceived-space editing;
service dashboards/components/logs; native review and local web-application rendering.

The prior architecture audit also predates much of the current regression suite.
Its test-coverage summary and proposed priorities are historical, not current evidence.

## Verification boundaries

The subsystem documents retain dated focused/full test results, synthetic fixture
checks, and explicit limits. Workflow notes record a baseline composer sizing
assertion failure in November 2026. Confirm its current status before using it as
a release gate or declaring it fixed. Target-directory logs and screenshots are
ephemeral evidence, not documentation assets or portable verification instructions.
