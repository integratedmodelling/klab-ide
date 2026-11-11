# IDE architecture and source map

[Developer documentation](README.md) · [Implementation gaps](GAPS.md)

The IDE is a Java 21/JavaFX desktop client. Embedded Monaco and application web
rendering are parts of the workbench. Keep service implementations outside the IDE;
the shared application-UI dependency imports renderer assets only.

## Workbench shell

Under `src/main/java/org/integratedmodelling/klab/ide`:

- `KlabIDEApplication` initializes JavaFX, themes, and the FXML application shell.
- `KlabIDEController` coordinates identity, services, navigation, focal scope,
  observation submission, notifications, and distribution controls.
- `pages/BrowsablePage` hosts section browsers and their top-level editor tabs.
- `pages/EditorPage` hosts inner editors, auxiliary views, asset trees, and a
  digital-twin panel.
- `components/InspectorView` and `components/cards` provide inspection and editing
  cards; `components/generic` provides shared JavaFX controls.
- `lsp` owns language-service sessions for embedded source editors.

The FXML, custom styles, icons, and templates live under
`src/main/resources/org/integratedmodelling/klab/ide`; installer assets live under
`src/main/resources/package`. Tests are under `src/test/java`; opt-in native QA
fixtures also exist under the main source `test` package.

## Knowledge and resources

`WorkspaceView` / `WorkspaceEditor` obtain navigable workspaces from Resources
services. Source saves reconcile parsed documents, diagnostics, and tree state.
`ProjectSettingsEditor`, `WorkspaceSettingsEditor`, and the project-material browser
handle their own access-aware drafts. Repository operations remain service-backed.

`OntologyView` now opens a persistent `WorldviewEditor`, preferring a local source.
`WorldviewSource`, `WorldviewGraphModel`, declaration projections, and concept indexes
support asynchronous loading, bounded graph neighborhoods, and source revision refresh.
This supersedes the historical empty-Worldview description.

`ResourcesView` searches services with debouncing and stale-request handling.
`ResourceEditor` has persisted drafts, validation, adapter parameters, publication,
permissions, files, workflows, and history. `BatchResourceInput` and legacy import
views handle supported bulk paths. It is no longer a static sample-value form.

## Runtime contexts

`IDEContextScope` integrates the client context with modeler/runtime events and
registered viewers. `DigitalTwinEventRouter` and activity/submission helpers support
event and completion handling. Each `DigitalTwinEditor` owns graph/tree/card views;
control panels can also appear in unrelated editors, so operations must retain
their associated scope rather than assume the global focal scope.

`DigitalTwinControlPanel` handles receiving, computation, activities, observations,
and observer selection. `ObserverCard` audits occupied/perceived geometry and saves
perceived-space drafts. Observation cards integrate runtime map exports and timeline
selection. Temporal geometry and point-query support remain service-contract dependent.

## Local execution and applications

`BehaviorEditor` separates editable source from parsed/compiled state and manages
agent consoles/debugging. `ManagedBehaviorMirrors` and `ProjectBehaviorSource` retain
origin, behavior type, synchronization baselines, and conflict handling.

The application runner uses `ApplicationUiSession`, `ApplicationRenderer`,
`ApplicationWebView`, browser options, and the Java-owned browser bridge. It launches
saved project applications on a local Runtime. Reconnection retains the instance;
Stop/editor closure requests termination. See [Application integration](APPLICATIONS.md).

## Collaboration and semantics

`WorkflowEditor` hosts stage forms and revision-checked operations through
`WorkflowUIProvider`. Native proposal review shares backend DTOs, candidate validation,
and binding rules. Paired source review uses exact lexical markers, not prose search.
See [Workflows](WORKFLOWS.md) and [Proposal review](PROPOSAL_REVIEW.md).

`SemanticComposer`, `AuthorityBrowser`, and `SemanticComposerOverlay` implement
composition, with `ObservableComposerDialog` and editor/Worldview host callbacks.
The embedded editor supplies the invoking source context. Expression acceptance is separate
from runtime adaptation/submission. See [Semantic composer integration](SEMANTIC_COMPOSER.md).

## Lifecycle and verification

Do service work off the JavaFX thread and apply UI changes on it. Close/dispose
editor-owned sessions, viewers, workers, and bridges; guard callbacks against
closed views and stale generations. Temporary scene detachment is distinct from
permanent disposal. Preserve draft fields on failure and do not automatically
replay mutations with uncertain server outcomes.

Component tests cover many lifecycle, authorization, revision, and stale-response
contracts. A synthetic JavaFX fixture is not a live service acceptance test. Dated
verification in integration documents records its scope and environment; do not
promote it to a new full-suite claim. The older detailed architecture audit is
retained in [history](history/README.md), with superseded claims explicitly marked.
