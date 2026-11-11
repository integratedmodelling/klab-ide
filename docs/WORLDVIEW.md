# Worldview explorer

[Documentation home](../README.md) · [Observable composer](SEMANTIC_COMPOSER.md)

The Worldview explorer is a read-only view of the shared vocabulary loaded by your
Reasoner. Use it to discover concepts, inspect their meaning, and trace semantic
relationships before writing models or observation requests.

## Open the loaded worldview

Select **Worldview** in the navigation rail. Its main tab opens automatically when
a Reasoner with your certificate's worldview becomes available. Local worldview
services are preferred when available; selecting a remote digital twin does not
by itself change this preference. The tab identifies a local source with **(local)**.

The declaration tree lists ontologies and their concepts. The **Concept graph**
shows a neighborhood around the focused concept. Select a concept to inspect its
card; use graph navigation to explore related concepts.

> **Screenshot placeholder — Worldview explorer:** show the ontology tree,
> focused concept, semantic relationship labels, concept card, and locator field.

## Find and explore a concept

Enter a concept URN or observable expression in the locator beneath the graph and
press **Enter**. The Reasoner validates it and locates the concept. **Shift+Space**
opens assisted composition from the locator; **Ctrl+Shift+Space** uses its text as
a whole starting expression. Accepting a composition locates it in the explorer.

Use **Previous concept** to retrace focus changes and **Worldview subject** to
return to the initial subject. Change neighborhood depth to see more or fewer
links. **Relations** selects which kinds of relationships are displayed;
**Reload graph** refreshes the neighborhood. A loading operation can be cancelled.

## Read relationships correctly

| Relationship | Meaning in this view |
| --- | --- |
| Subclass | A type specializes another type |
| Of: native bearer | A quality or process depends on its bearer |
| Contains / within: observational containment | A declared container/contained-type relationship |
| Emerges from: configuration ingredients | A configuration's declared ingredient expression |
| Applies to: predicate / configuration target | The scope to which a predicate or configuration applies |
| Requires identity / realm | A declared constraint on a type or its bearer |

Other layers include traits, roles, influences, links, and logical operands.
Use relation tooltips for the detailed meaning. Containment alone does not assert
physical enclosure or the existence of members. A declared requirement is distinct
from a trait actually assigned to a concept.

## Refresh and author knowledge

The view follows changes in the loaded worldview. If refresh fails, the status
identifies the retained snapshot; it may be older than the service's current state.
Use refresh after restoring the connection. A bounded graph may report truncated
or unresolved results; reduce depth or focus on a smaller neighborhood.

To change ontology source, open its project in [Workspaces](PROJECT.md). To use a
concept in a request, open the composer in a [digital twin](DIGITAL_TWINS.md).
