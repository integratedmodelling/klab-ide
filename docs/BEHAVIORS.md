# Behaviors and agents

[Documentation home](../README.md) · [Project applications](APPLICATIONS.md)

Use **Applications, Scripts and Test cases** to work with local k.Actors files and
local working copies of project behaviors. You can edit invalid source; compilation,
execution, and debugging require valid source and a local Runtime.

## Create or open a file

Use **Create** to choose a new `.kactor` file initialized from a template.
**Open** selects an existing `.kactor` or legacy `.kactors` file. Recent files stay
in the browser. Removing a recent card removes only that entry, not the file.

Save to reparse source. Valid source restores the behavior tree and enables
compilation; invalid source remains editable and shows diagnostics in the editor
and status area. With a local Runtime, automatic compilation is enabled by default.
You can also display generated Java in an auxiliary tab.

## Run or debug an agent

Use the editor toolbar to compile, run a new agent, run in debug mode, or stop
agents started from that editor. Each run opens an agent console. Debug sessions
also appear beside the behavior tree. Multiple behavior editors share one current
debug target. Review diagnostics if compilation or launch is unavailable.

The console accepts text input through **Send** or Enter and shows agent state,
start time, and idle information. **Stop and release this agent** ends an individual
run. The debugger's **Messages** view traces incoming/outgoing messages and lets
you send a custom message class and text payload to a live agent. Resume, Pause,
and Step controls are currently disabled; use message tracing to inspect execution.

Saved project web applications have their own renderer and Run/Stop lifecycle;
follow [Applications](APPLICATIONS.md) for that workflow.

> **Screenshot placeholder — Behavior execution:** show source diagnostics, the
> compile/run/debug controls, agent console, and active debugger target.

## Publish a standalone behavior

1. Save and compile successfully.
2. Select the cloud-upload action when a local Resources service is available.
3. Choose a destination project and confirm publication.

Publication creates or updates the corresponding project document in the folder
for its behavior type. Publishing an existing identity replaces its source.
The original standalone file remains local. To get an explicitly managed working
copy, open the published document from its project using **Edit and run locally**.

## Work on a project behavior locally

A managed behavior belongs to its project; the file you run locally is a persistent
mirror. This is different from editing source directly in the workspace editor,
where Save updates project source immediately.

1. Find the behavior in the workspace tree and choose **Edit and run locally**
   (or **Edit and run application locally** for an application).
2. Edit, save, compile, run, or debug the managed copy.
3. Select the cloud-sync action to update its originating project.
4. Check that the browser card returns to **Up to date with project**.

| Card state | Meaning |
| --- | --- |
| Green check: Up to date with project | Local source matches the last synchronized project source |
| Orange pencil: Local changes not submitted | Saved local edits still need synchronization |
| Red error: Mirror state unavailable | The IDE cannot read or compare the working copy |

Mirrors persist under `~/.klab/ide/behavior-mirrors`, so saved, unsubmitted edits
survive an IDE restart. Cards identify the project and show the local path as
secondary information. Renaming a managed behavior in source updates its project
identity and canonical path on successful synchronization.

> **Diagram placeholder — Two save paths:** workspace Save updates project source;
> managed-copy Save updates the local mirror, then cloud-sync updates project source.
> Git commit/publish follows separately in the project's Team menu.

## Reconcile changes from elsewhere

Reopening a managed copy checks its owning project's current source. Clean copies
refresh automatically, including clean open copies notified of project changes.
Local edits are protected. If both sides have changed, reopening offers
**Use project source** or **Keep local copy**. Using project source preserves the
local file as a `.bak` beside the mirror before loading the project version.

Removing a recent card does not reset the mirror. If automatic compilation is
enabled, refreshed source is compiled again. An already running application keeps
the source it started with: stop and run again after saving or synchronizing.
