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
than the static schema's INIT-only graph. The definition version is visible in the header and start
menu. Existing flows load their pinned definition, including historical versions.

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

The opt-in `ReviewEditorJourneyFixture` test entry point opens the real JavaFX editor and start menu
against a synthetic in-memory ResourcesService. Its synthetic versions 0.1/0.2/0.3 exercise catalog
selection; they are not the current server definition. It has no production data or network access. The
`ReviewEditorJourneyTest` suite checks atomic creation with files, repeated creation, uncertain results,
revision-safe detail saves, attachment revision binding, and draft preservation across upload/conflict.
