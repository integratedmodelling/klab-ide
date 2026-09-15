# k.LAB Modeler IDE

A JavaFX desktop workbench for authoring semantic models, managing scientific resources, running
k.Actors behaviors, and exploring observations in k.LAB digital twins.

The IDE is the desktop client for the [k.LAB 1.0 service stack](https://github.com/integratedmodelling/klab-services).
k.LAB turns a request for knowledge expressed as meaning in context into a computational plan and
an observation, retaining its semantics and provenance. Start with the
[k.LAB introduction](https://github.com/integratedmodelling/klab-services/blob/develop/docs/KLAB.md)
for the platform's purpose and architecture.

[Download the prototype](https://products.integratedmodelling.org/klab-ide/develop/download.html)
· [User guide](docs/IDE.md)
· [k.LAB documentation](#learn-klab)
· [Report an issue](https://github.com/integratedmodelling/klab-ide/issues)

> [!IMPORTANT]
> This is a **pre-alpha prototype** under active development. Available actions depend on identity,
> permissions, connected services, and installed adapters. See the
> [current limitations](docs/IDE.md#current-limitations) before planning a deployment.

## Contents

- [Features](#features)
- [Getting started](#getting-started)
- [IDE documentation](#ide-documentation)
- [Learn k.LAB](#learn-klab)
- [Build and run from source](#build-and-run-from-source)
- [Repository layout](#repository-layout)
- [Project and support](#project-and-support)

## Features

- **Author knowledge:** browse service-hosted workspaces and projects; edit namespaces, ontologies,
  observation strategies, and behaviors with Monaco and language-service support; save and inspect
  validation results and version-control state.
- **Manage resources:** create and import adapter-backed resources, edit and validate descriptions,
  save versions or temporary data, publish local resources, and administer access where permitted.
- **Run behaviors:** create local k.Actors files or check out managed project behaviors into persistent
  local mirrors; compile, run, debug, and submit them back to their projects.
- **Observe and inspect:** submit workspace assets to a digital twin, preview the receiving context,
  follow activities and observations, inspect knowledge graphs, and explore geometry and time-dependent
  quality maps when runtime exports are available.
- **Review work:** browse asset workflows and use source review and side-to-side views. New workflows
  and specialized forms require a configured workflow provider.
- **Arrange the workbench:** use dockable editor tabs, inspection history, service dashboards,
  notifications, and local service-distribution controls.

The [IDE user guide](docs/IDE.md) describes these workflows and their current boundaries.

## Getting started

1. [Download the prototype](https://products.integratedmodelling.org/klab-ide/develop/download.html)
   and follow the installation instructions for your platform, or [build from source](#build-and-run-from-source).
2. Open the user profile to check your identity and the service rail to check available services.
   Workspace and resource authoring use a Resources service. Observation requests use a Runtime
   with supporting Reasoner, Resources, and Resolver services.
3. If you need a local stack, use the distribution-management and power controls in the IDE.
   Remote services may be usable independently, subject to permissions.
4. Open **Workspaces**, **Resources**, **Applications**, or **Digital Twins** for your task.

For service setup and backend development, use the
[klab-services README](https://github.com/integratedmodelling/klab-services/blob/develop/README.md).
Building the desktop client alone does not install a configured service stack or grant access to
knowledge and data.

## IDE documentation

| Resource | Purpose |
| --- | --- |
| [User guide](docs/IDE.md) | Navigation, authoring, resources, behaviors, digital twins, and current limitations |
| [Architecture](docs/IDE_ARCHITECTURE.md) | Application shell, controllers, editors, and service integration; some subsystem descriptions predate newer features |
| [Workflow integration](docs/WORKFLOWS.md) | Workflow providers, stage editors, navigation, and authorization |
| [Conveyor build guide](docs/BUILD_README.md) | Packaging background and platform targets |
| [Maven configuration](pom.xml), [Conveyor configuration](conveyor.conf), [CI pipeline](Jenkinsfile) | Current build, packaging, signing, and deployment configuration |

## Learn k.LAB

The following guides live in the companion `klab-services` repository. Links target its `develop`
branch, matching the evolving 1.0 codebase.

| Topic | Resources |
| --- | --- |
| Platform and architecture | [k.LAB overview](https://github.com/integratedmodelling/klab-services/blob/develop/docs/KLAB.md), [service architecture](https://github.com/integratedmodelling/klab-services/blob/develop/docs/ARCHITECTURE.md) |
| Semantic modeling | [Modeling guide](https://github.com/integratedmodelling/klab-services/blob/develop/docs/SEMANTIC_MODELING.md), [observable expressions](https://github.com/integratedmodelling/klab-services/blob/develop/docs/OBSERVABLES.md) |
| Authoring languages | [Worldview ontologies](https://github.com/integratedmodelling/klab-services/blob/develop/docs/ONTOLOGY_LANGUAGE.md), [k.IM](https://github.com/integratedmodelling/klab-services/blob/develop/docs/KIM.md), [k.Actors](https://github.com/integratedmodelling/klab-services/blob/develop/docs/AGENTS.md) |
| Observation and execution | [Observation strategies](https://github.com/integratedmodelling/klab-services/blob/develop/docs/OBSERVATION.md), [resolution](https://github.com/integratedmodelling/klab-services/blob/develop/docs/RESOLUTION.md), [scopes](https://github.com/integratedmodelling/klab-services/blob/develop/docs/SCOPES.md) |
| Data and traceability | [Resources](https://github.com/integratedmodelling/klab-services/blob/develop/docs/RESOURCES.md), [storage](https://github.com/integratedmodelling/klab-services/blob/develop/docs/STORAGE.md), [provenance](https://github.com/integratedmodelling/klab-services/blob/develop/docs/PROVENANCE.md) |
| Extensions and collaboration | [Components and adapters](https://github.com/integratedmodelling/klab-services/blob/develop/docs/COMPONENTS.md), [workflow contracts](https://github.com/integratedmodelling/klab-services/blob/develop/docs/WORKFLOWS.md), [behavior testing](https://github.com/integratedmodelling/klab-services/blob/develop/docs/TESTING.md) |

Some platform guides include proposals and implementation ledgers; their status notes distinguish
available behavior from planned extensions.

## Build and run from source

Use **JDK 21** and Maven (or the included Maven wrapper). The build depends on k.LAB SNAPSHOT
artifacts and companion libraries, including the modeler, Monaco editor, and JavaFX graph library.
These must be available from the configured repositories or installed in your local Maven cache.

On Windows, from the repository root:

```powershell
.\mvnw.cmd clean test
.\mvnw.cmd javafx:run@run
```

On Linux or macOS:

```sh
./mvnw clean test
./mvnw javafx:run@run
```

Conveyor packaging is enabled through `-Pconveyor`. Consult the current configuration and CI
pipeline linked above for required environment variables, signing, assets, and output targets;
the older packaging guide is background rather than an exact deployment recipe.

## Repository layout

- `src/main/java/org/integratedmodelling/klab/ide`: application, service coordination, editors,
  inspection cards, and reusable JavaFX controls.
- `src/main/resources`: FXML, styles, icons, and package resources.
- `src/test/java`: automated regression and contract tests.
- `docs`: user and developer documentation.

## Project and support

Use [IDE issues](https://github.com/integratedmodelling/klab-ide/issues) for client bugs and feature
requests, and [service issues](https://github.com/integratedmodelling/klab-services/issues) for backend
problems. Include the build, operating system, relevant service versions, and steps to reproduce.

Project information: [Integrated Modelling](https://integratedmodelling.org/).
The IDE packaging declares **AGPL-3.0-or-later**; the companion service repository includes the
[AGPL license text](https://github.com/integratedmodelling/klab-services/blob/develop/LICENSE.txt).
