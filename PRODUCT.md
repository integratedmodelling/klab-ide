# k.LAB Modeler IDE

<!-- impeccable:product-schema 1 -->

## Platform

Desktop (JavaFX), packaged for Windows, macOS, and Linux.

The skill's platform vocabulary (`web`, `ios`, `android`, `adaptive`) does not
cover this application. Preserve the desktop architecture; embedded web content
does not make the workbench a browser application or a mobile-native interface.

## Users

Scientific modelers authoring semantic knowledge and exploring observations.
This primary audience and job were confirmed by the user during initialization.
Priorities for additional audiences and newcomer support remain open.

## Product Purpose

Provide a desktop workbench for authoring semantic models, managing scientific
resources, running k.Actors behaviors, and exploring observations in k.LAB digital
twins. Support the path from knowledge authoring to observation and inspection
while retaining the platform's semantics and provenance.

## Positioning

The IDE is the desktop client for the k.LAB 1.0 service stack. k.LAB translates
requests expressed as meaning in context into computational plans and observations.
The workbench brings that service-backed knowledge and observation workflow into
an integrated authoring environment.

## Operating Context

- Workspaces contain projects with namespaces, ontologies, observation strategies,
  and behaviors.
- Resources describe datasets and external assets, including geometry, metadata,
  provenance, and adapter-specific settings and operations.
- Behaviors can be local files or managed project documents checked out into local
  mirrors for editing, running, debugging, and submission to their projects.
- Digital twins host observations, relationships, activities, and knowledge graphs.
  One twin is focal for context-sensitive actions, even with multiple tabs open.
- Dockable editors, the Knowledge Inspector, service dashboards, notifications,
  and local distribution controls support the working environment.

## Capabilities and Constraints

- Preserve the existing Java 21 / JavaFX desktop architecture, Maven build, FXML
  views, AtlantaFX integration, and embedded Monaco editor with language services.
- Most knowledge and runtime data come from connected k.LAB services. Availability
  depends on identity, permissions, selected services, installed adapters, and the
  focal digital twin. Building the client does not provision a service stack or
  grant access to data or knowledge.
- Workspace and resource authoring use Resources services. Observation requests
  use Runtime with supporting Reasoner, Resources, and Resolver services.
- Local services can be managed through the IDE; remote service access remains
  subject to permissions and availability.
- Workflow creation and specialized stage forms depend on a configured provider.
- Preserve established domain terminology, including observable, observer,
  context observation, perceived geometry, occupied geometry, and focal digital
  twin. Their distinctions affect behavior and must not be collapsed in UI copy.
- Treat the application as under active development. The current application title
  says alpha; user documentation describes capabilities and service prerequisites
  without making deployment-readiness guarantees.
- Consult current source and docs/IDE.md before presenting any incomplete feature
  as available. Older architecture and build documents are supporting context,
  not authority over current implementation.

## Brand Commitments

Use the existing k.LAB Modeler / k.LAB Modeler IDE naming and Integrated Modelling
Partnership attribution. Existing logo, icon, and packaging assets are available
under src/main/resources/package and the IDE resource directory. No additional
voice or aesthetic commitment was established during initialization.

## Evidence on Hand

- README.md: product overview, core capabilities, setup, and service dependencies.
- docs/IDE.md and the task guides it links: current user workflows and terminology.
- docs/developer/ARCHITECTURE.md and docs/developer/GAPS.md: current source map and
  implementation boundaries. Subsystem integration notes live in the same directory;
  older audits are explicitly historical under docs/developer/history.
- pom.xml and conveyor.conf: build dependencies and packaging configuration.
- src/main/java/org/integratedmodelling/klab/ide: current client implementation.
- src/main/resources: existing FXML, styles, icons, and package assets.

No customer testimonials, adoption figures, performance benchmarks, or deployment
guarantees were established during initialization; do not invent them.

## Product Principles

1. Support scientific modelers through the connected authoring, execution, and
   observation workflow.
2. Preserve semantic meaning, provenance, and distinctions between domain concepts.
3. Make service availability, authorization, and execution state understandable
   where they affect the user's task.
4. Describe implemented capabilities accurately and distinguish incomplete or
   service-dependent behavior from working client functionality.

## Open Decisions

Beyond the repository-backed constraints, the user left additional product
requirements open. Accessibility targets, newcomer onboarding priorities,
additional audiences, and specific compatibility guarantees are not yet defined.
Do not turn these unknowns into standing requirements without confirmation.
