# Getting started

[Documentation home](../README.md) · [Using the IDE](IDE.md)

## Install and open the workbench

[Download k.LAB Modeler](https://products.integratedmodelling.org/klab-ide/develop/download.html),
choose the installer for your platform, and follow its installation instructions.
Launch **k.LAB Modeler**. Installing the IDE does not by itself grant access to
knowledge, datasets, or digital twins.

Open the user-profile button in the navigation rail. Check that the identity shown
is the one you intend to use. An anonymous or failed identity may have limited
access. Next, inspect the service buttons in the lower part of the rail.

| Task | What you need |
| --- | --- |
| Browse and edit a workspace or resource | A connected Resources service and access to that asset; editing also requires write rights |
| Browse a worldview or compose an expression | A Reasoner with a loaded worldview |
| Submit an observation | A Runtime with supporting Reasoner, Resources, and Resolver services and permission to use them |
| Compile, run, or debug a local behavior | A local Runtime; publication also needs a local Resources service |
| Run a project web application | A saved project application and a local Runtime at a local address |

Use the download button to open **distribution management** if you need to install
or update local services. Once a distribution is available, use the power button
to start it and wait for startup to finish. Remote services can remain usable when
local services are stopped. [Services](SERVICES.md) explains the controls.

> **Screenshot placeholder — First launch:** show the profile, distribution,
> power, and four service buttons, with examples of ready and unavailable states.

## Open knowledge

1. Select **Workspaces** in the upper navigation rail.
2. Open the section browser using its menu tab and choose an accessible workspace.
3. Expand a project in the tree on the right.
4. Double-click a document or statement to open its source. Selecting a statement
   takes you to its position in the document.
5. Show the **Knowledge Inspector** and select an item to see its details.

If you have write access, lock the project before editing where required by its
service. Save the source and review its diagnostics. Saving project source and
publishing a Git commit are separate steps. See [Workspaces and projects](PROJECT.md).

You can also open **Worldview** to explore the loaded shared vocabulary without
editing source. See [Worldview explorer](WORLDVIEW.md).

## Make your first observation

Use an accessible model or observation definition supplied by your projects;
the exact knowledge available depends on your worldview and services.

1. Open **Digital Twins**, use the add button, enter a name and description,
   choose a Runtime and persistence policy, and create the twin.
2. Confirm that it is selected in the status bar. This is your **focal digital twin**.
3. Open its control panel and check the **Observers** tab. Select an available agent.
   For a request that needs an observer extent, set perceived space if it is empty.
4. For a dependent request such as a quality, select a suitable subject observation
   as context first. It supplies the geometry for the request.
5. Drag the model or observation definition from the workspace tree onto the
   twin panel. Review the receiving context preview before dropping it.
6. Follow **Activities**, then open the resulting observation to inspect its
   geometry, relationships, metadata, and content.

An observation needs meaningful spatial or temporal context. If you do not yet
have the required observer extent or subject observation, follow
[Digital twins and observations](DIGITAL_TWINS.md) before submitting. The
[observable composer](SEMANTIC_COMPOSER.md) provides another way to construct a request.

> **Screenshot placeholder — First observation:** show a workspace asset being
> dragged onto the receiving preview, followed by the completed activity and result.

## Choose your next task

- [Describe and publish a resource](RESOURCES.md).
- [Create and run a behavior](BEHAVIORS.md).
- [Run a project web application](APPLICATIONS.md).
- [Participate in a workflow](WORKFLOWS.md).
- [Diagnose a missing service or failed request](TROUBLESHOOTING.md).
