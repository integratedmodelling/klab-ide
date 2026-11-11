# Maintaining the documentation

[Developer documentation](README.md)

## Audience and navigation

`README.md` is the user entry point. Its local documentation links and the links
reachable through the user guides stay user-facing. Keep setup, task procedures,
prerequisites, permissions, recovery advice, and terminology in those guides.

Build instructions, source maps, API contracts, implementation plans, test ledgers,
and backlog items belong under `docs/developer`. This directory has its own index;
it does not need a link from the user navigation. Keep root product/design guidance
for repository maintainers outside the user documentation graph.

When an incomplete feature affects a user action, state the practical boundary
beside that action and offer a usable next step. Put the implementation work in
[GAPS.md](GAPS.md). Do not reintroduce a broad “forthcoming features” list or use a
historical audit as proof that an implemented feature is missing.

## Coverage and evidence for the 2026-11-11 revision

| User guide | Implementation evidence inspected |
| --- | --- |
| IDE / getting started / services | Controller, application shell, service dashboard, distribution controls, current POM and packaging configuration |
| Projects | WorkspaceEditor, ProjectSettingsEditor, workspace settings, material and local-export helpers; existing feature regression names |
| Worldview | OntologyView, WorldviewEditor, WorldviewGraphModel and relation declarations |
| Composer | SemanticComposer, AuthorityBrowser, host dialog and Worldview entry points, including filters, aliases, and checked cache previews |
| Resources | ResourcesView, ResourceEditor, BatchResourceInput and LegacyResourceImportView |
| Digital twins | DigitalTwinView/Editor/ControlPanel, observer and observation behavior described in the prior guide and corresponding current source paths |
| Behaviors / applications | BehaviorEditor publication, managed-mirror integration notes, application runner and its documented lifecycle |
| Workflows / proposals | Workspace workflow navigation and current integration contracts, including the native/browser review distinction |
| Logs | Remote/local log integration notes and shared service dashboard entry points |

This was a documentation/source review. It did not exercise a live authenticated
service stack or rerun historical JavaFX fixtures. Preserve those distinctions in
future documentation claims.

## Visual placeholders

User guides use visible blockquotes labeled **Screenshot placeholder** or
**Diagram placeholder**, followed by a description of the required view and labels.
These render without broken image URLs. Replace them with committed images and
descriptive alt text once captured. Use sanitized data, show the relevant selected
service/twin, and avoid credentials or personal filesystem paths.

Capture an actual supported build. Synthetic fixture screenshots must be labeled
as examples. Do not use images under `target` as permanent user-guide dependencies.

## Review checklist

- Follow the README links as a new user and check that each guide offers a clear task.
- Verify action names and shortcuts in current source, including dependency-owned UI.
- Check local paths and fragments after moving documents.
- Ensure every user guide is reachable and has a path back to the documentation home.
- Keep developer documents out of the user link graph.
- Check that implemented capabilities are not described as forthcoming.
- Keep genuine limitations, especially saves, destructive actions, and uncertain
  mutations, accurate and close to the relevant steps.

## Relocated material

Former mixed-audience `APPLICATIONS`, `WORKFLOWS`, `PROPOSAL_REVIEW`, `REMOTE_LOGS`,
and `SEMANTIC_COMPOSER` documents are retained in this developer directory. Their
original user-facing paths now contain task guides. Older `BUILD`, `BUILD_README`,
`PROJECT`, `IDE_ARCHITECTURE`, `NOTES`, `Timeline_Component_Documentation`, and the
previous `IDE` guide are retained under [history](history/README.md). They are
reference material, not current instructions. Use [BUILD.md](BUILD.md) and
[ARCHITECTURE.md](ARCHITECTURE.md) for maintained entry points.
