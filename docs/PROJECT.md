# Workspaces and projects

[Documentation home](../README.md) · [Using the IDE](IDE.md)

A workspace groups projects hosted by a Resources service. Projects contain model
namespaces, ontologies, observation strategies, applications, scripts, test cases,
and supporting material. Access rights determine what you can inspect or change.

## Open or create a workspace

Open **Workspaces** and its browser. Local services appear before remote services.
Choose a workspace to open it in a tab. To create one, use the add button, enter a
name and description, choose a Resources service that allows creation, and select
**Create**.

The right-hand tree organizes projects, folders, documents, and statements. The
toolbar adds projects, searches by URN, expands or collapses the tree, and opens
workspace settings. A single click follows an already-open source editor and the
visible inspector. A double click opens the containing document at the statement.
Moving the source cursor selects the corresponding statement in the tree.

> **Screenshot placeholder — Workspace authoring:** show a project with its
> document types, a source editor, diagnostics, and the selected tree statement.

## Create and edit knowledge

Use a project's context menu to create a namespace, behavior/application/test case,
ontology, or observation strategy. Open the new document from the tree. Project
locks prevent conflicting edits where supported; a lock also needs the service's
write permission. Green project text indicates an editing lock.

Choose the document type for the work you want to do:

| Document | Purpose |
| --- | --- |
| Ontology | Declare shared concepts and their relationships |
| Model namespace | Describe how observables are modeled using knowledge and resources |
| Observation strategy | Describe how observation requests can be approached |
| Behavior/application/script/test case | Express executable activity in k.Actors |

Edit in the source editor and use **Save** to send the complete document to the
hosting service. The returned document refreshes the tree and diagnostics. The
toolbar reports whether edits remain unsaved. Behavior source saved here updates
the project directly; a separate local working mirror needs its own synchronization
action, explained in [Behaviors](BEHAVIORS.md).

The editor controls toggle line numbers, the minimap, review mode, and side-to-side
workflow review. **Ctrl+Shift+Space** opens the [observable composer](SEMANTIC_COMPOSER.md).
Accepting its expression inserts one undoable edit at the invoking cursor.

## Understand validation

Parser and language-service diagnostics appear in the editor. Saved namespaces and
ontologies that change are also enrolled for semantic validation with the selected
Reasoner. Editing and saving remain available while semantic validation runs.

Tree badges aggregate errors for documents and projects; hover for details. Editor
status distinguishes pending validation, unavailable services, syntax-blocked source,
unsynchronized knowledge, and completed validation. A successful document has no
validation badge. Editing clears its semantic markers until saved source is validated.

Opening unchanged source retains its saved diagnostics. Use **Validate now** to request
a fresh check of saved documents. A saved ontology's authoritative source and imports
must be available to the Reasoner. Validation does not publish ontology drafts.

For source declarations that propose authority aliases, the editor also reports
proposal status at the relevant declaration. Saving can submit those proposals
to the configured authority host. Status updates distinguish an unsubmitted alias
from its server-reported review status; using a proposed name does not imply it
has been approved for the authority's codelist.

## Work with Git

Open the project's **Team** menu for the repository operations offered by its
service. Use **Add to version control...** for an untracked project. Save source
drafts before committing or publishing; saving to Resources and committing/pushing
to the origin repository are separate operations.

Branch switching presents **Commit and switch branch**: select an existing branch
or enter a new name. **Merge branch** selects an existing branch to merge into
the current branch. Review repository messages and resolve reported conflicts
before continuing.

For a local project, **Team → Use Git credential set...** associates a Resources-service
credential set by ID. The next Git operation remembers that ID for the repository;
later operations recheck access. Existing Git credential helpers also remain usable.
Ask your service administrator if you need a credential set.

### Copy a remote project locally

In a remote workspace, choose **Export to local services** from the project menu.
You need an operational local Resources service, and a project with that identity
must not already exist locally. Import does not replace an existing local project.

A tracked project is cloned from its Git origin and branch, so changes present only
in the remote service's working tree are excluded. An untracked project is copied
as a snapshot into the local `local` workspace. Additional material is included;
Git configuration and credentials are excluded from snapshots. Publish the local
copy through its Team menu using the origin repository's permissions.

## Change project settings

Right-click a project and choose **Project settings**. Lock it and check that you
have edit access before changing settings. **Save settings** saves the complete draft;
a rejected save leaves your draft visible.

- **Worldview declaration:** administrators can set or clear the worldview defined
  by the project; other users can inspect it.
- **Default observer:** appears for worldview-defining projects. Enter observer
  semantics or leave it blank to contribute no default observer.
- **Snapshot:** synchronize with Git origin before the service loads the project.
  The service fetches and merges conflict-free changes; problems appear in service status.
- **Permissions:** access is stored by the Resources service. An empty selection
  means owner-only access, while existing service grants and ownership are retained.
- **Metadata:** edit scalar values inline, or use **Add / replace**, **Remove key**,
  and **Edit JSON**. Choose **Text** for literal text and **JSON** for numbers,
  booleans, arrays, objects, or null.

Project metadata is saved in `META-INF/manifest.json`. Older project settings are
migrated on save where needed. Settings changes remain pending Git changes until
you commit and publish them through Team.

### Change workspace settings

Use the workspace settings button above the tree. Owners/creators and service
administrators can change workspace metadata and permissions; other users with
workspace access can inspect them. A project lock is not required. Workspace
settings do not change project membership or project settings. Older workspaces
without a recorded owner need an administrator to change their settings.

> **Screenshot placeholder — Settings:** show project permissions and metadata,
> the conditional observer field, and a read-only workspace settings example.

## Manage additional material

Choose **Additional material…** from the project context menu to manage stylesheets,
localization properties, images, and other supporting files. Create/write access,
including material-contributor access, is needed even to browse these folders.
Deleting also requires delete permission.

1. Use **New folder…** at the material root.
2. Select a folder to create a text file or **Import files…** from your computer.
3. Select a file to inspect its text or a supported image preview.
4. Use **Edit file** for CSS, properties, Markdown, JSON, and other structured text.
   **Preview file** renders a Markdown draft without losing your edits.
5. Save, then use **Commit and publish project…** or the project's Team menu.

Imports preserve binary files and refuse overwrites. Ordinary notes use a lightweight
editor; structured text uses the source editor. Text encodings and byte-order marks
are preserved on save. Use **Export…** to save a local copy. Files are limited to
32 MiB. **Delete…** removes a file or an empty folder. Empty folders stay local
until files are added. Close the browser using the toolbar control; unsaved changes
require confirmation before discard.

Names must start with an ASCII letter or digit. Project-managed folders (`src`,
`apps`, `behaviors`, `scripts`, `testcases`, `strategies`, `components`, `resources`,
`docs`, `META-INF`) and Git control paths cannot be used as material folders.

> **Screenshot placeholder — Additional material:** show the folder tree, image
> preview, Markdown preview/edit controls, and commit/publish action.

## Review or remove content

A dot on an asset icon marks an associated workflow. Use its context menu to
[open or start a workflow](WORKFLOWS.md). Side-to-side review keeps the source and
[proposal review](PROPOSAL_REVIEW.md) together.

Document deletion requires confirmation. Project deletion permanently removes the
project's contents for all users; read the confirmation and check your backups
before proceeding.
