# Using the IDE

[Documentation home](../README.md) · [Getting started](GETTING_STARTED.md)

The workbench brings knowledge authoring, data descriptions, applications, and
digital twins into one desktop window. This guide describes the common controls;
the task guides linked below explain each working area.

## Navigate the window

The upper part of the left navigation rail selects your main working area.

| Area | Use it to |
| --- | --- |
| Dashboard | Use notebook cards and enter commands or URNs |
| Workspaces | Browse projects and edit knowledge documents |
| Digital Twins | Create and explore runtime observation contexts |
| Resources | Search, import, describe, and publish resources |
| Applications, Scripts and Test cases | Open standalone behavior files and managed local copies |
| Worldview | Explore shared concepts and their relationships |

The lower rail contains the local-service power control, service dashboards,
distribution management, settings, inspector, and user profile. Service buttons
indicate their type, availability, and the number of services. Hover over icon-only
controls for their tooltips.

Open **User information** with the user-profile button to inspect your identity
and groups. Its certificate drop area configures authentication for first use,
updates a certificate, or switches to another identity. Stop local services before
replacing a certificate and follow [certificate setup](GETTING_STARTED.md#set-up-your-identity-with-a-user-certificate).

> **Screenshot placeholder — Window anatomy:** annotate the navigation rail,
> section browser, editor tabs, right-hand asset tree, Knowledge Inspector,
> digital-twin panel, and bottom status bar.

## Open and arrange editors

Use the menu tab at the upper left of a section to open its browser. Choose an
item to open its editor as a tab. Editors can have their own inner tabs and an
asset tree on the right. Single-clicking a tree item updates selection and an
already visible inspector; double-clicking opens its document or detailed view.

Drag editor tabs into floating windows when you need more space. To return a tab,
drag it over its original tab pane and release over the docking preview. Releasing
outside the preview leaves the window floating. Save your drafts before closing
editors or leaving the application.

## Inspect knowledge and results

The **Knowledge Inspector** shows a detailed card for a selected object. Use its
rail button to show or hide it. Follow related items in a card to inspect them,
then use the backward/forward controls or a breadcrumb to return to earlier items.
You can remove individual breadcrumbs or clear the history.

Use the undock control to place the inspector in a separate window. In observation
and activity trees, double-clicking can open the inspector; selecting the same
item again can close it.

## Select a digital twin

The bottom status bar identifies the **focal digital twin**, which receives
context-sensitive actions. Several twin tabs can be open, but only one is focal.
Selecting a twin tab makes it focal. Check this selector before submitting work.

The status bar also provides a shortcut to the twin, a control to show its panel
in the current editor, and a focal-twin reset control. These controls become
available when a suitable scope and editor exist. The panel follows activities,
observations, and observers while you continue working in another editor.
See [Digital twins and observations](DIGITAL_TWINS.md).

## Read notifications

The bottom bar counts information, warning, and error notifications. Open the
recent-notifications panel at the right to inspect messages. Source-editor
diagnostics and activity outcomes also appear in their own working areas; a
service-level problem may need the [service dashboard and logs](SERVICES.md).

## Use the dashboard

The Dashboard is a notebook of collapsible cards. It starts with About information
and can display identity, settings, service, and distribution-management cards.
Opening a new command result collapses earlier result cards.

Enter a command or URN in the command field. Type `help` for the commands available
in your installation. Some results appear as plain text or generic object details.

## Task guides

- [Workspaces and projects](PROJECT.md): authoring, validation, settings, Git, and additional material.
- [Worldview](WORLDVIEW.md) and [observable composer](SEMANTIC_COMPOSER.md): exploring and expressing meaning.
- [Resources](RESOURCES.md): data descriptions, import, versions, and publication.
- [Digital twins](DIGITAL_TWINS.md): contexts, observers, maps, and results.
- [Behaviors](BEHAVIORS.md) and [applications](APPLICATIONS.md): local execution and project synchronization.
- [Workflows](WORKFLOWS.md) and [proposal review](PROPOSAL_REVIEW.md): collaboration and decisions.
- [Services](SERVICES.md), [logs](REMOTE_LOGS.md), and [troubleshooting](TROUBLESHOOTING.md).
- [Glossary](GLOSSARY.md).
