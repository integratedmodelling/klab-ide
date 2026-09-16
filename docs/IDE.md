# k.LAB IDE User Guide

[Repository overview and documentation](../README.md)

## About this guide

The k.LAB IDE is a desktop workbench for authoring k.LAB knowledge, managing resources, running
behaviors, and interacting with observations held in digital twins. The current application
identifies itself as a pre-alpha release. The central workflows are usable, but several screens and
commands are still incomplete. Known limitations are listed at the end of this guide.

This guide was reviewed against the IDE source on 2026-09-15. Feature descriptions reflect
implemented client paths; availability and results still depend on the connected services.

The IDE assumes that most knowledge and runtime data are supplied by connected k.LAB services.
What appears in the workbench therefore depends on:

- the identity used to sign in;
- the services available to that identity;
- whether a local k.LAB distribution is installed and running;
- the currently selected digital twin;
- the permissions granted for each workspace, project, resource, or digital twin.

## The working model

Five concepts organize the IDE.

**Workspaces** contain projects. Projects contain k.LAB documents such as namespaces, ontologies,
observation strategies, and behaviors.

**Resources** describe datasets or other externally supplied assets, including their geometry,
metadata, provenance, adapter settings, inputs, outputs, and supported operations.

**Behaviors** may begin as local `.kactor` or legacy `.kactors` files, or may be managed documents
inside a project. Local behaviors can be edited, checked, run, and debugged as agents before they
are published. Project behaviors can be checked out to a persistent local mirror for the same
edit-run-test workflow and then updated in their originating project.

**Digital twins** are hosted observation contexts. A digital twin contains observations,
relationships, activities, schedules, and the knowledge graph produced by contextualization.

**The focal digital twin** is the one currently used by context-sensitive actions. Only one digital
twin is focal at a time, even when several digital-twin tabs are open.

## The application window

The window has four persistent regions.

### Left navigation rail

The upper group switches the main work area:

- **Dashboard** opens the notebook and command entry area.
- **Workspaces** browses and edits hosted workspaces and projects.
- **Digital Twins** creates, opens, and explores observation contexts.
- **Resources** searches resource services and opens resource descriptions.
- **Applications, Scripts and Test cases** opens local behavior files.
- **Worldview** is intended for shared semantic knowledge.

The lower service group controls or describes the working environment:

- the power button starts or stops local k.LAB services;
- the service buttons open information and management dashboards for the reasoner, resources, resolver, and runtime services connected to the account;
- the download button opens the local k.LAB software distribution management tool;
- settings, inspector, and user-profile buttons open their respective tools.

Button color communicates availability or state. In particular, the power button changes while
local services are starting, stopping, ready, unavailable, or not installed. The service buttons show the type, status and number of available services.

### Main work area

The main area displays the selected section. Most sections use the same interaction pattern:

1. Use the menu tab at the upper left to open the section browser.
2. Choose or create an item.
3. The item opens in a tab.
4. Editors may contain their own tabs and a tree index on the right.

Editor tabs can be dragged into floating windows and returned to their original tab pane. During
re-docking, a compact preview indicates the destination; releasing outside it leaves the floating
window in place. Closing a tab closes its editor and releases its editor-specific session.

### Knowledge Inspector

The inspector appears along the bottom or in a separate window. It displays a detailed card for the
selected object and maintains a navigable inspection history.

- Use the inspector button to show or hide it.
- Use the inspector toolbar to move backward or forward.
- Select a breadcrumb to return to an earlier object.
- Remove individual breadcrumbs or clear the entire history.
- Use the undock button to move the inspector into its own window.

Single-click selection updates the inspector when it is already visible. In observation and
activity trees, double-clicking can open the inspector and selecting the same item again can close
it. Related items in an inspection card can be followed without losing the previous card.

### Status bar and notifications

The bottom status bar contains:

- the current digital-twin selector;
- a shortcut to the selected digital twin;
- a control for showing the current editor's digital-twin panel;
- a reset control for the focal digital twin;
- counts for information, warnings, and errors;
- a button that opens the recent-notifications panel on the right.

The digital-twin controls are disabled until a suitable scope and editor are available.

## Getting started

### 1. Check identity and services

Open the user profile to verify whether the IDE recognizes an authenticated user. An anonymous or
failed identity may have limited access to remote knowledge and services.

Check the four core service buttons. Workspace authoring requires a Resources service. Observation
resolution requires a Runtime and normally also the supporting reasoner, resources, and resolver
services.

### 2. Install or start local services when needed

If no local distribution is installed, open distribution management with the download button.
When a valid distribution is available, use the power button to start the local services. Wait
until the button indicates that startup has completed.

Remote services may still be usable when the local stack is stopped, subject to authentication and
permissions.

### 3. Choose the workflow

- To author models and semantic knowledge, open **Workspaces**.
- To inspect or use existing data descriptions, open **Resources**.
- To run a local application, script, or test case, open **Applications**.
- To explore observations or create a context, open **Digital Twins**.
- To issue a direct command or URN, use the **Dashboard**.

## Working with workspaces

Open the workspace browser from the menu tab. Local services are listed before remote services.

### Create a workspace

1. Select the add button in the workspace browser.
2. Enter a workspace name and description.
3. Select a Resources service that permits creation.
4. Select **Create**.

An available workspace opens as a top-level tab. Its editor locks projects when the hosting service
allows it, preventing conflicting edits.

### Navigate a workspace

The right-hand tree contains projects, folders, documents, and statements. Use the toolbar to:

- add a project;
- search the tree by URN;
- expand or collapse the complete tree;
- access workspace settings when implemented.

A single click synchronizes the tree with an already-open source editor and updates the inspector
when it is visible. A double click opens the containing document and moves the source cursor to the
selected statement.

Moving the source cursor also selects the most specific matching item in the workspace tree.
Long names are truncated independently of the asset icons; a tooltip exposes the full name.
A dot beside an icon marks an asset with an associated workflow.

### Create project content

Open a project's context menu to create:

- a namespace;
- a behavior, application, or test case;
- an ontology;
- an observation strategy.

The same menu contains project locking, deletion, and version-control operations. Deleting a
project requires confirmation and permanently removes its contents for all users.

### Edit and save a document

Documents open in a source editor with syntax support when the language service is available.
Saving sends the complete document back to the hosting Resources service. The returned parsed
document updates the workspace tree and the open editor's diagnostics. The saved source becomes
the editor's clean baseline; the toolbar reports whether subsequent edits remain unsaved.

For a behavior, application, script, component, or test case, saving replaces the corresponding
source in the managed project. If the declared behavior name changes, its canonical project path
and tree entry change with it. The workspace tree is updated from the resulting resource lifecycle
events, and the document's version-control decoration reflects whether its project file is new,
modified, removed, or otherwise changed. Saving does not commit the change; use the project's
version-control actions separately when the result is ready.

To run or test a project behavior with the local agent tools, open its context menu and select
**Edit and run locally**. This action is available when a local Runtime service is present and is
described in more detail under “Working with behaviors and agents.”

### Review changes and workflows

The source-editor toolbar offers a review mode and, when a linked workflow is available, a
side-to-side workflow view. The asset context menu lists accessible open and closed flows.
Starting a new workflow requires an installed workflow provider with a schema applicable to that
asset; the default provider does not offer startable workflows.

Workflow editors present stages, instructions, attachments, and permitted transitions. Editing
and reopening depend on the flow state and the user's role; public-read and closed flows open
read-only. See [Workflow editor integration](WORKFLOWS.md) for the provider contract.

### Resolve a workspace asset

To submit a workspace asset:

1. Drag a resolvable asset from the workspace tree.
2. The digital-twin panel opens and displays a drop target.
3. Drop the asset to submit it for observation.
4. The panel switches to activity progress and then to resulting observations.

If no digital twin is selected, the IDE attempts to create a default local context. This requires
an available local runtime. Cancelling the drag restores the panel's previous state and visibility;
when no twin exists, the temporary empty panel is removed.

The target preview explains where the request will be observed:

- For a dependent observation, such as a Quality or Process, it shows the current context
  observation's spatial geometry and name. This applies to models, observables, concepts, and
  observation definitions whose semantics identify a dependent request.
- For substantials or collectives, it uses the current observer's perceived geometry, when
  available. This is distinct from the geometry occupied by the observer itself.
- If the relevant context, observer, or geometry is unavailable, the target keeps a neutral prompt.

The name overlay has a semi-transparent background so the geometry remains visible. Creating and
choosing an observer is supported in the Observers tab.

## Working with digital twins

Open the Digital Twins browser to see contexts from connected Runtime services.

### Create a digital twin

1. Select the add button.
2. Enter a name and description.
3. Choose the hosting Runtime service.
4. Choose the persistence policy.
5. Review the displayed access rights.
6. Select **Create**.

The new context opens in a tab and becomes the focal digital twin.

### Explore a digital twin

The main digital-twin tab combines:

- a graphical knowledge-graph view;
- a tree representation of runtime assets;
- tabs opened for individual observations.

The graph supports navigation backward and forward, focus changes, depth control, and layout
controls. The tree offers context actions such as opening details or setting a suitable subject
observation as context. A quality-export entry is visible, but filesystem export is not implemented
yet.

Opening an observation creates a full observation card. The card contains:

- geometry and temporal extent;
- incoming and outgoing relationships;
- the observation's central content;
- metadata.

For a spatially distributed quality, the central content is a map exported by the runtime. Temporal
states are marked on the geometry timeline. Clicking the timeline loads the map for the selected
state. Clicking the map requests the value at that location and displays it below the image.
Unsupported geometries and non-quality observation types currently show explanatory placeholders.

### Use the digital-twin control panel

The status-bar arrow shows or hides the panel in the current editor. Its views are:

- **Activities**: the contextualization activity hierarchy and outcomes;
- **Observations**: the current knowledge-graph hierarchy;
- **Observers**: all agents, grouped by cohort, with the current observer selected;
- **Scenarios**: a view reserved for scenario selection; its catalog is not populated yet.

Each view has its own search field. The home button returns the observation tree to the graph root.
Right-clicking it offers the current context and recorded commits. Clicking the icon beside an
eligible individual subject makes it the current context; clicking it again clears that context.

During a supported asynchronous submission, the stop control requests cancellation of the active
job. Progress and final outcomes are reported through the Activities view.

The delete control in the panel removes the digital twin after confirmation. This operation also
removes its observations, storage, and schedule.

## Working with behaviors and agents

The Applications, Scripts and Test cases section manages both standalone behavior files and local
working mirrors of behaviors owned by projects. The distinction is important:

- a **local behavior** is owned by its selected file and has no project association until it is
  published;
- a **managed behavior** is owned by a project hosted by a Resources service; the file opened in
  the behavior editor is a persistent local mirror rather than the authoritative project copy.

### Create or open a behavior

Use the two distinct browser actions:

- **Create** chooses a new `.kactor` file and initializes it from a template.
- **Open** selects an existing `.kactor` or legacy `.kactors` file.

Recently used files remain in the browser and can be removed from the recent list without deleting
the file. A standalone behavior card shows its local file location. A managed behavior card instead
uses the behavior URN as its title, identifies its project, and shows the local mirror path as
secondary information.

Managed cards also show their synchronization state:

- a green check and **Up to date with project** means the mirror matches the last source
  synchronized with the project;
- an orange pencil and **Local changes not submitted** means the saved local source differs from
  that project state;
- a red error and **Mirror state unavailable** means the IDE cannot read or compare the mirror.

This comparison uses source content rather than modification times, so it remains meaningful after
an IDE restart.

### Edit and validate

The source editor remains available even when the file contains syntax errors. Saving reparses the
file:

- valid source restores the behavior tree and enables compilation;
- invalid source remains editable but disables compile-dependent actions;
- diagnostics appear as editor markers and in the editor status area.

When a local runtime is available, automatic compilation is enabled by default. Optional generated
Java source can be displayed in an auxiliary tab.

### Run and debug

The editor toolbar can:

- compile and check the behavior;
- show generated Java source;
- run a new agent;
- run a new agent in debug mode;
- stop all agents started from the editor;
- publish a standalone behavior to a local project, or update the originating project for a
  managed behavior.

Running or debugging opens a console tab for the agent. Debug sessions also appear in the debugger
area beside the behavior tree. Multiple behavior editors coordinate one current debug target.

### Publish a local behavior to a project

The cloud-upload action becomes available after the source is valid, compilation succeeds, and a
local Resources service is available.

1. Save and compile the behavior successfully.
2. Select the cloud-upload action.
3. Choose the destination project.
4. Confirm the publication.

The Resources service writes the source into the canonical folder for its behavior type, indexes
the resulting project document, and reports the creation to open workspace trees. Applications,
scripts, test cases, and general behaviors therefore appear in their respective project folders.
The project tree also receives the current version-control state. Publishing uses create-or-update
semantics: if the destination already contains the same behavior identity, its source is replaced.

The original standalone file remains local after publication. To establish an explicitly managed
working copy tied to the project, use **Edit and run locally** on the project behavior.

### Edit and update a project behavior locally

1. Locate the behavior in the workspace tree.
2. Open its context menu and select **Edit and run locally**.
3. The IDE retrieves the authoritative source and opens its persistent local mirror in the behavior
   editor.
4. Save, compile, run, debug, or test the mirror as needed.
5. When satisfied, select the cloud-sync action to replace the project source with the local source.

Managed mirrors are stored below `~/.klab/ide/behavior-mirrors`. Their origin metadata records the
Resources service, project, behavior identity, and last synchronized source. Reopening **Edit and
run locally** reuses the same mirror, so saved work that has not yet been submitted survives IDE
restarts.

The editor toolbar and recent-behavior card identify the originating project. After a successful
update, the mirror returns to **Up to date with project**, the project workspace receives the new
behavior structure and source, open editors are refreshed, and version-control decorations are
recomputed. Renaming the behavior in its source updates its project identity and canonical path.

If a project behavior changes elsewhere, the IDE refreshes an unchanged mirror and any open editor
automatically. A mirror containing unsubmitted local changes is not overwritten; it remains marked
**Local changes not submitted** until the user publishes it or otherwise reconciles the source.

## Working with resources

The Resources browser searches every connected Resources service. When a digital twin is focal, it
also lists resources submitted specifically to that context.

### Create, import, and edit

Use **Create a new resource** to select a hosting service and adapter, supply the resource identity,
and attach files where required by the adapter. Batch import is offered when a connected adapter
supports it. A resource can also be parameterized without a source file when its adapter allows it.

Selecting a result opens an editor with sections for overview, geometry, interface (attributes,
inputs, and outputs), adapter parameters, metadata, license, publication, permissions, files,
workflows, and history. Fields are populated from the resource; validation identifies missing or
inconsistent values and controls whether submission is available.

- **Create resource** submits a new draft to its hosting service.
- **Save new version** submits changes to an existing resource.
- **Update temporary data** uses the temporary-data path where the resource and service allow it.
- The Files section attaches or removes local ancillary data and documentation.

Editing depends on service and resource permissions. A published local copy is protected until
**Edit published local copy** is enabled. Submission uses a snapshot of the form; edits made while
a save is running remain distinguishable from the source that was submitted.

### Publish and manage access

The Publication section offers eligible destination services for a saved local resource. Select a
destination, supply an intended editor when required, and confirm publication. On success the IDE
records the authoritative service and resource identity and marks the local copy as published.
Re-publication is available when permitted. The browser can include published local copies through
**Show published local resources**.

The Permissions section submits access changes separately when the user can administer the
resource. Resource workflows use the same provider-based stage editor as workspace workflows.
The History section displays the history available in the resource information returned by the
service. Adapter behavior, remote acceptance, and review permissions remain service responsibilities.

## Using the dashboard

The dashboard is a notebook of collapsible cards. It initially contains About information and can
open cards for:

- distribution management;
- user identity;
- settings;
- each core service.

The command field accepts a command or URN. Type `help` for command assistance. Each submitted
command adds a result card to the notebook and previous cards are collapsed.

Some complex command results still use generic object rendering instead of purpose-built tables or
trees.

## Current limitations

The following limitations are important when planning work:

- The Worldview explorer has no functional browser or editor yet.
- Resource creation, publication, and batch import depend on compatible service and adapter
  capabilities; not every service offers every operation.
- Concept search in the digital-twin panel is a placeholder.
- Scenario catalogs are incomplete. Observer selection and perceived-geometry maintenance are
  supported, including perceived-space editing in the twin editor. Automatic user-behavior attachment remains pending.
- Starting new workflows and specialized stage forms requires a configured workflow provider.
- The access-rights editor shown while creating a digital twin is incomplete.
- Workspace settings, project settings, version-control branch selection, detach/untrack, and
  operation confirmation are incomplete.
- Dirty source tabs are not marked with an asterisk.
- The digital-twin map depends on runtime export support. Point-value lookup currently depends on a
  compatible text export from the runtime.
- Unsupported observation types and geometries display placeholders instead of specialized content.
- The quality “Export to filesystem” action does not yet write a file.
- Confirmation, service tooltips, idle states, and failure messages are inconsistent in several
  screens.

Because this is a pre-alpha application, preserve source files and important project content in
version control and verify destructive operations before confirming them.

### Choosing an observer

The runtime preselects the default observer configured by the worldview or your groups when you
connect to a twin. The **Observers** tab lists the twin's agents in their cohorts, including agents
created by models. Click an agent's icon to choose it; the active agent uses the observer icon.
Click that icon again to clear the choice. Selecting a context does not clear your observer, and
selecting an observer does not clear your context. Returning to the observer tab expands and
scrolls to the current agent.

Agents appear in the regular observation tree only after an explicit submission. Automatically
created default agents and other model-created agents remain available in the observer tab.

Successful observations grow the observer's **perceived geometry** by union. This is distinct from
its occupied geometry. Without a context observation or an explicit extent, subsequent observations
use the perceived extent; drop previews use the same geometry. A new default observer may have no
perceived extent yet; supply geometry in the first observation or set it in the Observer editor.
Geometry updates refresh the selected
observer without switching the active panel tab.

The service contract, persistence guarantees and verification boundaries are documented in
[Default observers](https://github.com/integratedmodelling/klab-services/blob/develop/docs/OBSERVERS.md).

### Auditing and editing observer geometry

In the DigitalTwinEditor knowledge tree, right-click an agent and choose **Audit / edit observer
geometry**. The same action is available in the Observers section of the control panel owned by
that editor. Each agent has one Observer tab, independent of the currently selected observer.

The tab has separately labeled maps for **perceived geometry** and **occupied geometry**. Saved
shapes are blue; missing spatial geometry is reported explicitly. Occupied geometry is read only.
Expand **Geometry details, including time** to audit both geometries. Temporal geometry is read
only and is preserved by spatial edits.

1. Pan or zoom the perceived map to the desired area. **Fit saved extent** returns to its saved shape.
2. Click **Use map view** to preview an amber rectangle. This is an unsaved replacement, not an addition.
3. Click **Save perceived space**, or **Discard draft and reload** to abandon it.

A save replaces perceived space with the rectangle. Subsequent observations can expand it again:
the automatic policy remains union. Open clean tabs refresh after graph changes without resetting
the viewport; unsaved drafts remain visible. If perception changed since the draft was based on it,
the runtime rejects the save. Reload and review the newer extent before retrying.

This first editor supports nonempty WGS84 rectangles within ordinary longitude/latitude bounds.
Polygon drawing, trimming, clearing, antimeridian-spanning edits, and temporal editing remain future
work. The existing Leaflet component is sufficient for this viewport-based workflow.

### Composing an observable

The concept icon in the Digital Twin control panel opens the assisted observable composer. Search
for a concept or operator, choose a row and press Enter or **Add selected**. The Reasoner proposes
subsequent components. **Undo**, parentheses and **Add value** support incremental construction;
the current observable card appears when the expression is complete and validated. The upper pane
uses the IDE's k.IM semantic colors and styles for both complete and incomplete expressions.

You can also type `(` or `)` to open or close a group, and press Backspace on an empty search field
to undo the last component. Consecutive opening parentheses and held-key repeats are guarded;
separate closing presses still close nested groups. When entering a literal value, parentheses remain
ordinary text. `each` is available explicitly, including in inherents such as `Height of each Tree`
(using the corresponding qualified concept names from your worldview).

**Continue** adapts the observable to an unresolved observation and submits it in the selected twin.
Qualities require a context observation and use its geometry. Substantials become collectives and
use the selected observer's perceived extent. Missing context, observer or geometry is reported in
the composer. After dispatch, the existing twin controls show progress and support cancellation.

`SemanticComposer` accepts a host-supplied asynchronous action, so other views can reuse it without
submitting an observation. Multi-operand unary operators, units/currencies and `where` conditions
remain unfinished. The [semantic-search rule catalog and extension guide](https://github.com/integratedmodelling/klab-services/blob/develop/klab.services.reasoner/SEMANTIC_SEARCH.md)
distinguishes implemented checks from missing semantic validation.
