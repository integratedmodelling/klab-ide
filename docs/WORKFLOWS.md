# Workflows

[Documentation home](../README.md) · [Proposal review](PROPOSAL_REVIEW.md)

Workflows organize collaborative work on a knowledge asset or resource. A flow is
one running instance of a workflow definition, with stages, assignments, attachments,
and a history. Available definitions and actions depend on the hosting Resources
service, configured workflow support, and your role.

## Find or start a flow

Right-click an asset in the workspace tree and find **Workflows**. Its menus offer
**Start workflow**, **Open flows**, and **Closed flows** when applicable. A dot on
an asset icon marks an associated flow. A resource's **Workflows** section offers
the same kind of access.

Choose a definition under **Start workflow**. Its name and version identify what
you are starting. Existing flows keep the definition version they started with.
Several flows can exist independently on the same asset. If no startable definition
is offered, you may still be able to browse existing flows.

Loading shows progress. Reopening a flow focuses its existing tab and preserves
unsaved edits. **Cancel opening** or closing the loading tab cancels opening;
**Retry opening** is available after a load failure.

> **Screenshot placeholder — Workflow navigation:** show the asset indicator,
> start/open/closed menus, flow version, stage list, and current assigned stage.

## Work on a stage

Select a stage to read instructions and inspect its data, attachments, and available
actions. The editor opens the first editable stage assigned to you when possible.
Depending on the definition, you may see a specialized form or a metadata browser.
Complete required fields and use the offered transition to advance the flow.

Public-read flows open as read-only browsers. Closed flows and stages are inspectable
without editing. Private open stages are editable only when your role permits it.
Being able to view a flow does not grant the ability to change it.

If an operation reports a revision conflict, keep your draft and refresh the saved
flow to review changes before retrying. A missing or uncertain response may mean
the operation completed: check the flow list or saved state before submitting again.

## Prepare an ontology review

For behavior-driven ontology reviews, the initial bootstrap is private preparation.

1. Name the stage and write the review brief.
2. Use **Choose file** or the drop target to attach a proposal, comments, candidate
   ontology, or supporting material allowed by the selected attachment type.
3. Review pending files; remove any that should not be included.
4. Select **Create private bootstrap**. This saves the details and pending files
   without opening a review cycle.
5. Save later title or brief edits with **Save stage details**. Save these details
   before **Prepare review draft**.
6. If no proposal/source was supplied, complete the required preparation inputs.
   Use **Open bootstrap review UI** to edit the prepared proposal/comments and
   open review in the service's review page.
7. Use **Refresh saved workflow** to retrieve changes made in that page.

The browser may require its own sign-in. Closed stages disable editing and uploads.
The [native proposal-review guide](PROPOSAL_REVIEW.md) explains candidate inspection
and decisions for stages that expose those controls inside the IDE.

> **Screenshot placeholder — Review preparation:** show title/brief, attachment
> type and pending files, private-bootstrap creation, and the review-UI action.

## Inspect the structure and history

The header's structure button opens an overview. For behavior-driven reviews it
shows the lifecycle and actual persisted stages/transitions. **Graph** displays
the service-rendered flow diagram; a new unsaved flow uses its selected definition.
Use **Export PNG** to save the displayed image. If graph loading fails,
**Descriptions and history** stays available and **Refresh graph** retries it.

## Administrative actions

Where permitted, administrators can reopen a closed flow. **Delete flow** is an
administrator action for removing the whole saved flow and its attachments. Its
confirmation names the flow and asset and defaults to Cancel. Project files and
already exported archives are retained. Immutable review stages/cycles cannot be
edited or deleted individually through this action; specialized review controls
may hide administrative actions to preserve the review lifecycle.

After successful deletion the IDE closes views of that flow and refreshes the asset
indicator. If the result is uncertain, reconcile through saved-flow refresh or the
flow list before trying again.
