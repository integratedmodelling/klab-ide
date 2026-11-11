# Proposal review

[Documentation home](../README.md) · [Workflows](WORKFLOWS.md)

Some workflow stages provide a native proposal-review view. Use it to inspect the
exact candidate and evidence, edit an authoring draft when assigned, and record a
review decision. Other stages may open a service-hosted review page or a generic
workflow view; follow the controls offered by that stage.

## Inspect the candidate

Open a flow and select its review stage. The native view separates the candidate,
evidence references, concepts and typed bindings, questions and observable mappings,
invalid probes/gaps, quality limitations/predicates, unresolved semantics, coverage
shortfalls, server checks, and human rationale.

Use the immutable proposal and supporting attachments for details beyond the
structured view, including source scope/status and imported-parent rationale.
Text attachments can be inspected through the authorized attachment viewer.
Counts help you find issues; they are not evidence that the proposal is ready.

> **Screenshot placeholder — Review evidence:** show candidate/revision identity,
> Concepts & bindings, an unresolved item, required checks, and rationale together.

## Edit or request changes

Authoring stages offer structured draft forms and permitted candidate uploads.
Reviewer stages preserve the evidence under review. Use **Request changes** to
return work to an authoring stage rather than trying to edit reviewed evidence.
The next authoring stage returns editing to the original author.

Before a transition, review the confirmation's exact candidate, actions, and rationale.
The confirmation binds to the displayed candidate and revision. Cancelling or a
failed/conflicted request retains your draft; check the saved state before retrying
if the outcome is uncertain.

Draft edits persist through a transition. Navigating stages preserves local drafts,
but there is no separate persistent draft-save action. Reset asks before discarding,
and closing an individual workflow tab checks unsaved edits. Keep important draft
material separately before shutting down the whole application.

## Understand acceptance

Acceptance requires explicit human rationale and confirmation as well as successful
required server checks. Missing, blocked, failed, or unrun checks and unresolved
semantics disable acceptance. Required checks include import context, document
schema, parsing, adaptation, and Reasoner checks.

If your server reports production checks as blocked, the IDE cannot make the
candidate ready. Preserve the draft and resolve the check with the service/workflow
administrator. A successful review decision alone does not apply ontology changes
or publish a pull request.

## Pair the review with source

Open **Side-to-side workflow review** from a workspace source editor. Review markers
can link exact concept declarations to proposal items. Clicking a marker selects
the item in **Concepts & bindings**. Review mode controls marker visibility.

Markers apply only to declarations that can be matched unambiguously to the current
snapshot. Editing source invalidates them. If no candidate ontology artifact binds
the document, a marker is a lexical reference in the current parsed document rather
than proof that this source was reviewed. Missing markers do not imply missing
evidence: inspect the candidate and attachments directly.

> **Screenshot placeholder — Paired review:** show the source glyph, matching
> proposal concept item, immutable revision identity, and review-mode control.
