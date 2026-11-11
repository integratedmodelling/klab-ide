# Glossary

[Documentation home](../README.md)

| Term | Meaning in the IDE |
| --- | --- |
| Adapter | A service extension that describes how k.LAB accesses a particular kind of resource |
| Agent | A running behavior instance or an agent observation in a twin; an eligible agent can act as observer |
| Authority identity | A canonical identity/code supplied through a configured worldview authority binding |
| Behavior | A k.Actors program, including applications, scripts, test cases, and other behavior types |
| Certificate | A user authentication file downloaded from the k.LAB Hub and installed through the IDE's user-profile drop area; see [certificate setup](GETTING_STARTED.md#set-up-your-identity-with-a-user-certificate) |
| Context observation | An existing subject selected as the context for dependent observation requests |
| Digital twin | A runtime-hosted context containing observations, activities, relationships, and a knowledge graph |
| Flow | One instance of a workflow definition, with its stages and history |
| Focal digital twin | The one twin currently used by context-sensitive workbench actions |
| Geometry | The spatial and/or temporal extent associated with an asset or request |
| Managed mirror | A persistent local working copy tied to a project behavior; local Save and project synchronization are separate |
| Namespace | A knowledge document organizing model declarations and related statements |
| Observable | A semantic expression describing what is to be observed |
| Observation | A contextualized runtime result, retaining semantics and relationships |
| Observation strategy | Knowledge describing how observations can be requested or resolved |
| Observer | The selected agent from whose perspective an observation request is made |
| Occupied geometry | The extent occupied by an observer itself |
| Ontology | A document declaring shared concepts and semantic relationships |
| Perceived geometry | The observer's observation extent, which can grow through successful observations |
| Project | A collection of knowledge documents and supporting material hosted by a Resources service, optionally tracked in Git |
| Quality | A dependent observable, such as a measurement of a subject; a request needs a suitable context |
| Resource | A description of data or an external asset, including adapter configuration and metadata |
| Substantial / collective | A subject-like type / request for its instances; twin requests can use an observer's perceived extent |
| URN | A stable identifier used to address a knowledge asset, resource, or semantic declaration |
| Workflow | A versioned definition of a collaborative process |
| Workspace | A service-hosted grouping of projects |
| Worldview | The shared semantic vocabulary loaded for your k.LAB identity and services |

See [Getting started](GETTING_STARTED.md) for the service roles and
[Digital twins](DIGITAL_TWINS.md) for the relationship between context, observer,
and geometry.
