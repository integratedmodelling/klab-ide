# k.LAB Modeler IDE

The k.LAB Modeler IDE is a desktop workbench for scientific modelers. Author semantic
knowledge, describe and publish data resources, run applications and behaviors, and
explore the observations produced in your digital twins.

[Download k.LAB Modeler](https://products.integratedmodelling.org/klab-ide/develop/download.html)
· [Get started](docs/GETTING_STARTED.md)
· [User guide](docs/IDE.md)
· [Report an issue](https://github.com/integratedmodelling/klab-ide/issues)

The IDE is under active development. Its central authoring, resource, execution, and
inspection workflows are available. What you can access depends on your identity,
permissions, connected k.LAB services, and the adapters installed on those services.

> **Screenshot placeholder — The workbench:** show a workspace source editor, the
> project tree, an open Knowledge Inspector, and the digital-twin controls. Label the
> navigation rail and focal-twin selector with the terms used in the guide.

## Start working

1. Download and install the application for your platform.
2. Register or sign in at the [k.LAB Hub](https://integratedmodelling.org/hub) and
   download your user certificate. With local services stopped, open the IDE's user
   profile and drop the file onto **Drop a new klab.cert certificate** to authenticate.
   Use the same procedure to update the certificate or switch identity; see
   [certificate setup](docs/GETTING_STARTED.md#set-up-your-identity-with-a-user-certificate).
3. Check the service buttons in the navigation rail; use distribution management
   and the power button if you need a local service stack.
4. Open **Workspaces** to author knowledge, **Resources** to describe data,
   **Applications, Scripts and Test cases** to run behaviors, or **Digital Twins**
   to create and explore an observation context.

The [getting-started guide](docs/GETTING_STARTED.md) explains service requirements
and takes you through opening knowledge and submitting an observation.

## What you can do

| Your task | Guide |
| --- | --- |
| Navigate the workbench, arrange tabs, and inspect objects | [Using the IDE](docs/IDE.md) |
| Create projects, edit knowledge, validate source, and work with Git | [Workspaces and projects](docs/PROJECT.md) |
| Browse the shared vocabulary and relationships between concepts | [Worldview explorer](docs/WORLDVIEW.md) |
| Build semantic expressions with concepts, operators, and authority identities | [Observable composer](docs/SEMANTIC_COMPOSER.md) |
| Create, import, edit, version, and publish data descriptions | [Resources](docs/RESOURCES.md) |
| Create a twin, choose an observer, submit knowledge, and inspect results | [Digital twins and observations](docs/DIGITAL_TWINS.md) |
| Edit, compile, run, debug, and publish local or project behaviors | [Behaviors and agents](docs/BEHAVIORS.md) |
| Run a saved web application inside the IDE or in a browser | [Applications](docs/APPLICATIONS.md) |
| Start and follow asset workflows and ontology review stages | [Workflows](docs/WORKFLOWS.md) and [proposal review](docs/PROPOSAL_REVIEW.md) |
| Check services, manage components, and read diagnostic logs | [Services](docs/SERVICES.md) and [service logs](docs/REMOTE_LOGS.md) |
| Resolve access, validation, execution, and connection problems | [Troubleshooting](docs/TROUBLESHOOTING.md) |
| Look up the IDE's core terms | [Glossary](docs/GLOSSARY.md) |

## From knowledge to observations

k.LAB translates a request expressed as meaning in context into a computational
plan and an observation, retaining its semantics and provenance. You work with
three connected kinds of material:

- **Knowledge:** ontologies, model namespaces, observation strategies, and behaviors
  organized into projects and workspaces.
- **Resources:** descriptions of datasets and external assets, with metadata,
  geometry, access rights, and adapter settings.
- **Digital twins:** runtime contexts containing observations, relationships,
  activities, and knowledge graphs.

> **Diagram placeholder — From authoring to observation:** knowledge and resources
> feed a request in a digital twin; resolution produces activities and observations;
> the inspector, graph, and maps expose the results and their relationships.

You can browse existing knowledge without authoring it, and run a standalone behavior
before publishing it to a project. The [glossary](docs/GLOSSARY.md) explains how an
observable, an observer, and a context observation play different roles.

## Help and feedback

For a client problem, [report an IDE issue](https://github.com/integratedmodelling/klab-ide/issues)
with your build, operating system, relevant service versions, and steps to reproduce.
The [troubleshooting guide](docs/TROUBLESHOOTING.md) explains which status and logs
help diagnose a problem. Remove credentials and private data before sharing evidence.

k.LAB is a project of the [Integrated Modelling Partnership](https://integratedmodelling.org/).
The IDE's distribution declares the AGPL-3.0-or-later license.
