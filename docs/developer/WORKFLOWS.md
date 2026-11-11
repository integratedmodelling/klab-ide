> **Developer reference:** implementation and dated verification notes relocated
> from the former mixed-audience guide. See the [developer index](README.md) and
> [current gap ledger](GAPS.md). Recorded test results are historical evidence.

# Workflow editor integration

`WorkflowEditor` is the generic JavaFX shell for Resources-service `Flow` objects. It is opened as
an auxiliary tab in the `WorkspaceEditor` containing the flow's target asset. The canonical model,
storage, authorization, REST, CRUD, URN, and provenance documentation remains in
`klab-services/docs/WORKFLOWS.md`.

## Enabling workflows

Install one `WorkflowUIProvider` on the `WorkspaceView`:

```java
workspaceView.setWorkflowUIProvider(
    new WorkflowUIProvider() {
      @Override
      public List<Workflow> availableWorkflows(NavigableAsset asset, UserScope scope) {
        return workflowCatalog.forAsset(asset, scope);
      }

      @Override
      public WorkflowEditor.StageEditor stageEditor(
          Workflow workflow,
          Flow flow,
          Flow.State state,
          Workflow.StateSchema schema,
          boolean readOnly,
          Runnable validationChanged) {
        return stageEditors.editorFor(schema.getId(), state, readOnly, validationChanged);
      }
    });
```

The provider is propagated to open workspace editors and inherited by editors opened later. The
no-op provider returns no startable workflows. Accessible existing flows are still listed because
they are obtained from the selected workspace's `ResourcesService`.

`availableWorkflows(asset, scope)` must return only applicable schemas. The start menu retains only the newest definition per workflow ID and displays its name and version;
the ID is the fallback name. Returning an empty list suppresses **Start workflow**.
The overall **Workflows** section is suppressed only when there are no startable workflows and no
accessible open or closed flows for the exact asset URN.

## Specialized stage contract

`WorkflowEditor.StageEditor` contains four values:

- `content`: the JavaFX node inserted between the common instructions and controls;
- `valid`: a predicate controlling every confirmation button;
- `metadata`: exports the validated stage contents as `Flow.State.metadata`;
- `readOnly`: informs the specialized component whenever mutation is forbidden.

The specialized editor must call `validationChanged.run()` whenever validity changes. On
confirmation, the shell saves exported metadata, then applies the chosen transition using the
updated flow revision. It owns the shared attachment UI, transition buttons, cancellation, stage
deletion, error reporting, and refresh behavior.

Returning `null` from `stageEditor` uses a generic metadata browser. Override `startFlow` only when
creation requires additional policy, such as setting `Flow.publicRead` through generic Resources
CRUD; the default calls `ResourcesService.createFlow`.

## Navigation and authorization

The tree context menu has a separated **Workflows** section with **Start workflow**, **Open flows**,
and **Closed flows** submenus. Multiple flows on the same asset are independent menu entries. New
flows focus their INIT stage. Existing flows focus the first open stage assigned to the user, then
the first visible current stage.

Public-read flows always open as read-only full-flow browsers. A private open stage is editable by
an `ADMIN`, its owner, or an assigned `EDITOR`. Closed flows are read-only; only `ADMIN` receives
the **Reopen flow** action. Server authorization repeats these rules, so hiding or disabling a
client control is never the security boundary.

## Ontology review preparation

Behavior-driven review workflows (`reviewStages`) retain the standard header structure button.
Its overview shows the behavior lifecycle and the actual persisted stages and transitions, rather
than the static schema's INIT-only graph. The **Graph** tab uses the original service-rendered
ELK/PNG visualizer: saved reviews request the actual flow, including persisted review cycles,
while an unsaved draft requests its pinned workflow definition. **Export PNG** saves that image
through the native file chooser. **Descriptions and history** retains the explanatory overview.
Graph load failures leave the descriptions accessible and offer **Refresh graph**.
The definition version is visible in the header and start
menu. Existing flows load their pinned definition, including historical versions.

Workspace workflow menus show **Loading workflows...** while the catalog and saved flows are
retrieved off the UI thread. Opening a saved flow immediately shows a loading tab. Repeated opens
focus the same tab without fetching again or replacing unsaved edits. **Cancel opening** or closing
the tab prevents a late response from reopening it. A failed load offers **Retry opening**.

In bootstrap and coalescing, editors can name the stage, write a review brief, and attach a proposal,
comments, candidate ontology, or supporting material allowed by the stage schema. **Choose file**
and the drop target use the selected attachment type. Before creation, files remain pending and can
be removed. **Create private bootstrap** persists the initial details and pending files atomically
without opening a review cycle. A definite validation rejection keeps the draft and allows an explicit corrected retry. An uncertain
creation keeps the draft and prevents another request until the editor checks the workflow list.

After creation, **Save stage details** and attachment uploads bind to the displayed flow revision.
A conflict leaves the local fields intact. Adding a reference does not discard unsaved title or brief
edits. **Prepare review draft** exposes the behavior's required input fields when no proposal/source
has already been supplied. Save stage details before preparing. Use **Open bootstrap review UI** to
edit the prepared proposal and comments and open review; **Refresh saved workflow** loads subsequent
changes. Closed stages remain inspectable, with editing and attachment controls disabled.

Creation, saving stage details, saved-flow refresh, action discovery, and draft preparation show
an explicit progress message while the service works. Repeated clicks cannot submit duplicates.
The editor remains visible but is locked until the accepted operation finishes. Errors retain local
fields; closing a containing editor prevents a late result from updating the disposed view.

If stage saving reports that revision-checked details are unsupported, the error identifies the
actual loaded Resources implementation, its artifact, and the class declaring the method. An API
jar paired with an older common/client jar can otherwise inherit the interface's unsupported
default without making any HTTP request. Build/install matching Services API and common artifacts
into the Maven repository selected for the IDE build, then run IDE tests and package against that
same repository. `WorkflowClientCompatibilityTest` checks the real IDE dependency classpath for
these unsupported inherited workflow/review defaults before packaging. Do not assume that
rebuilding only the IDE replaces an already-resolved common/client artifact.

The header **Delete flow** trash button is available to administrators after a flow is saved,
including failed bootstrap attempts and progressed or closed flows. Administrator rights come
from workflow roles, a server-side scope, or the selected Resources service's authenticated
`ADMINISTER` capability; ownership and a localhost address alone are insufficient. The confirmation
names the exact flow and asset, warns that the entire aggregate and attachments are irreversibly
removed, and defaults to **Cancel**. Project files and already exported archives are not deleted.
This whole-flow operation does not permit editing or deleting
individual immutable review stages or cycles. The service repeats authorization at deletion time.

Successful deletion closes all open views of the deleted flow and their diagrams, clears pairing callbacks, and refreshes
the workspace flow indicator. A rejected or unconfirmed request retains the editor and local drafts.
An uncertain outcome blocks another delete until a saved-flow refresh or reopened flow list reconciles
the result. No automatic retry is made.

The opt-in `ReviewEditorJourneyFixture` test entry point opens the real JavaFX editor and start menu
against a synthetic in-memory ResourcesService. Its synthetic versions 0.1/0.2/0.3 exercise catalog
selection; they are not the current server definition. It has no production data or network access. The
`ReviewEditorJourneyTest` suite checks atomic creation with files, repeated creation, uncertain results,
revision-safe detail saves, attachment revision binding, and draft preservation across upload/conflict.
`WorkflowNativeControlsTest` checks saved-flow and pinned-draft PNG rendering/retry/export, administration capabilities, delete
cancel/success/false/uncertain results, repeated clicks, and actual workspace tab/registry/indicator
cleanup. The opt-in `WorkflowNativeControlsFixture` opens the production controls against resettable
in-memory synthetic data for native chooser and deletion QA; it never contacts a real service. It
can take a PNG path produced by the service's `ReviewFlowChartIntegrationTest` to verify the original
renderer output in the native viewer.

`WorkflowProgressTest` blocks service responses with latches while exercising production workspace
tabs and JavaFX controls. It verifies immediate visible progress, a responsive UI thread, duplicate
click guards, opening/action cancellation, retained fields on errors, and suppressed late results.

### Repair verification (2026-11-07)

Java 21 verification used an isolated Maven repository and test home, after the matching Services
API/common/core/resources artifacts were rebuilt and installed.

- Focused selection `WorkflowNativeControlsTest,ReviewEditorJourneyTest,Workflow*Test,ReviewStageEditorTest,Proposal*Test`:
  **45 tests passed**, including all 9 native-control regressions. Log: `target/workflow-controls-final-focused.log`.
- Full IDE suite: **385 tests, 384 passed and 1 error**, with no skipped tests. The only error was
  `SemanticComposerTest.overlayCoversOwnerSceneAndGrowsForCardWithScrollingInSmallWindows`, expecting
  `451.2` but seeing `454.4`. The same sizing error was independently reproduced on the unchanged
  baseline; it is not fixed by this workflow change. Log: `target/workflow-controls-final-aggregate.log`.
  The previously reproduced Worldview graph initialization race did not recur in this run.
- Interactive Windows verification used only the resettable in-memory fixture: the genuine service
  PNG appeared in the original viewer; descriptions remained available; cancelling the native save
  chooser kept the viewer open; saving exported an identical PNG. The generated 46,730-byte image
  at `target/native-workflow-export.png` matches the service fixture PNG byte for byte.
- Native delete cancellation retained the flow. A simulated service failure retained the editor and
  sidebar, disabled a repeated delete, and allowed reconciliation through saved-flow refresh.
  Confirmed deletion then removed only the synthetic flow, its selected tab, and sidebar entry.

Native UI verification did not contact a live Resources service or delete any user flow. Real HTTP
authorization, false/error transport results, persistence cleanup, and PNG generation are separately
covered by the matching Services tests. The IDE regressions also verify cleanup of duplicate open
views of the same saved flow.

### Progress and packaged-client verification (2026-11-07)

- Focused workflow/review/proposal suite: **53 tests passed**, including seven delayed-control
  regressions and the actual client-dependency compatibility contract.
  Log: `target/workflow-progress-focused.log`.
- Full IDE suite: **393 tests, 392 passed, one error, no skipped tests**. The sole error remains
  the known baseline `SemanticComposerTest` sizing assertion (451.2 expected, 454.4 actual).
  Log: `target/workflow-progress-full.log`.
- Normal non-Conveyor `package` completed, including packaged application-UI verification. Tests
  were skipped in this packaging invocation only after recording the separate full-suite result.
  No publish, install, deployment, or live Modeler action was performed.
  Log: `target/workflow-progress-package.log`.
- The resulting `target/klab-ide-all-1.0.0-SNAPSHOT.jar` contains API, ResourcesClient, and
  ResourcesMerger classes byte-for-byte identical to the verified isolated dependencies. `javap`
  confirms concrete implementations of revision-checked details, action discovery/execution, and
  review-cycle/artifact methods in both clients. Inspection: `target/workflow-progress-packaged-classes.txt`.

`WorkflowProgressFixture` is an opt-in native QA launcher using the real workspace/editor hosts
and synthetic service responses delayed by 3.5 seconds. It has no network calls or user flow data.
The fixture launched successfully, but the additional visual inspection could not start because
the shared Windows computer-use helper remained occupied by another verification. No native input
was sent and no visual pass is claimed for these progress changes. The automated JavaFX regressions
did exercise real Scenes, menus, controls, delayed service calls, and close/cancel interactions.
