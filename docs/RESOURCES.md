# Resources

[Documentation home](../README.md) · [Digital twins](DIGITAL_TWINS.md)

A resource describes a dataset or external asset so k.LAB can use it through an
adapter. Its description includes geometry, metadata, parameters, interfaces,
license, and permissions. Available adapters and operations depend on the service.

## Find a resource

Open **Resources** and search in its browser. The IDE searches connected Resources
services and also shows resources submitted to the focal digital twin. Open a
result to inspect its description. **Show published local resources** includes
local copies that have already been published.

## Create or import

1. Select **Create a new resource**.
2. Choose the hosting service and adapter, and supply the resource identity.
3. Attach source files if the adapter requires them. Some adapters can be configured
   entirely with parameters.
4. Continue to the editor, complete required fields, and review validation messages.
5. Select **Create resource** to submit the draft.

Use **Import resources in batch** when offered to import multiple resources through
a compatible adapter. Select the service/import option and supply its requested
inputs. Local services can also offer legacy-folder import: the IDE scans for
`resource.json` definitions and reports each import's result. The local service
must support the legacy importer. Review per-item outcomes rather than assuming
the whole batch succeeded.

> **Screenshot placeholder — Resource import:** show the service/adapter chooser,
> required inputs, and batch results with a successful and a rejected item.

## Edit the description

The editor organizes the resource into these sections:

| Section | What to review or change |
| --- | --- |
| Overview | Identity and general description |
| Geometry | Spatial geometry and time extent; apply each draft using its geometry/time control |
| Interface | Attributes, data types, keys, optional fields, inputs, and outputs |
| Adapter parameters | Adapter-specific settings and available operations |
| Metadata and license | Descriptive information, provenance, and reuse terms |
| Files | Ancillary data and documentation |
| Publication and permissions | Destination services and access rights |
| Workflows and history | Available review processes and service-provided history |

Fields are populated from the resource. Validation identifies missing or inconsistent
values and controls submission. Add or remove ancillary files in **Files**.
Editing requires the appropriate service and resource permissions.

The resource tree's **Add codelist** action adds a codelist identifier to an editable
description. This associates the identifier with the resource; it does not itself
populate an authority's identity catalog.

Select **Save new version** for an existing resource. **Update temporary data**
uses the temporary-data path where supported. A save submits a snapshot of the
form; changes you make while saving remain separate from that submitted snapshot.
Check completion before leaving the editor.

> **Screenshot placeholder — Resource editor:** show the section tree, geometry,
> adapter fields, validation messages, and create/save/temporary-data actions.

## Publish a local resource

1. Save the local resource.
2. Open **Publication** and choose an eligible destination service.
3. Supply an intended editor if required and confirm publication.
4. Check the reported authoritative service and resource identity.

A successfully published local copy is marked as published and protected from
ordinary editing. Enable **Edit published local copy** if you need to revise it,
then save and re-publish where permitted. Remote acceptance and any review process
are controlled by the destination service.

## Manage access and follow review

In **Permissions**, users with administration rights can submit access changes
using **Update permissions**. This is separate from saving resource content.
Open **Workflows** to inspect or start an available process; the same
[workflow controls](WORKFLOWS.md) used for knowledge assets apply here.
**History** shows the records returned by the service.

Deleting a resource requires permission and confirmation. Check whether models or
other users rely on it before confirming removal.
