# Digital twins and observations

[Documentation home](../README.md) · [Observable composer](SEMANTIC_COMPOSER.md)

A digital twin is a hosted runtime context containing observations, relationships,
activities, and a knowledge graph. You can have several twins open. The **focal
digital twin**, shown in the status bar, receives context-sensitive actions.

## Create or open a twin

Open **Digital Twins** and its browser to see contexts available from connected
Runtime services. To create one, use the add button, enter its name and description,
choose a Runtime and persistence policy, review the displayed access rights, and
select **Create**. The new twin opens and becomes focal. Selecting another twin's
tab makes that twin focal.

The creation form shows initial access rights; its access-settings chooser does
not currently change them. If different sharing rights are required, check with
the Runtime administrator before creating the twin.

Choose persistence to suit your work. Do not assume that closing an editor deletes
a persistent twin. Conversely, temporary contexts can have a limited lifetime
determined by the runtime policy.

> **Screenshot placeholder — Twin creation and selection:** show the Runtime and
> persistence choices, the created tab, and matching focal-twin status-bar entry.

## Context, observer, and geometry

These three choices determine where a request is evaluated:

| Choice | Role |
| --- | --- |
| Context observation | An existing suitable subject that provides context and geometry for a dependent request such as a quality |
| Current observer | An agent from whose perspective a request is made |
| Perceived geometry | The extent available to that observer for requests without a context observation or explicit extent |

An observer's **occupied geometry** describes where the agent itself is. Its
**perceived geometry** describes its observation extent. Selecting a context does
not clear your observer; changing observer does not clear your context.

> **Diagram placeholder — Choosing request geometry:** show a quality using the
> context observation's geometry, and a substantial/collective using the observer's
> perceived extent. Draw occupied geometry separately from perceived geometry.

### Select an observer

Open the control panel's **Observers** tab. Agents are grouped by cohort. The
runtime preselects a default observer when one is configured by your worldview
or groups. An outline person icon identifies an available agent; a filled,
accented icon identifies the current observer. Click an outline icon to switch.
A sole agent is selected automatically if none is current.

Default and model-created agents can appear here without appearing in the regular
observation tree. Agents enter that tree after explicit submission. Returning to
Observers expands and scrolls to the current agent.

### Audit and edit perceived space

In the twin editor's knowledge tree, or its Observers panel, right-click an agent
and choose **Audit / edit observer geometry**. The Observer tab shows separately
labeled perceived and occupied maps. Saved shapes are blue; occupied geometry is
read-only. Expand **Geometry details, including time** to audit both extents.

1. Pan and zoom the perceived map to the desired area. **Fit saved extent** returns
   to the saved shape.
2. Select **Use map view** to preview an amber rectangle.
3. Select **Save perceived space**, or **Discard draft and reload** to abandon it.

Saving replaces perceived space with the rectangle. Successful observations can
then expand it by union. Time is preserved and read-only. The editor accepts
nonempty WGS84 rectangles within ordinary longitude/latitude bounds. If perception
changes while your draft is open, reload and review the newer extent before retrying.

> **Screenshot placeholder — Observer geometry:** show blue saved space, an amber
> replacement draft, the separate occupied map, and save/discard controls.

## Submit knowledge

Drag a model, observable, concept, or observation definition from a workspace tree
to its digital-twin panel. The panel opens a receiving preview. For dependent
requests, it shows the current context observation's name and geometry. For
substantials or collectives, it shows the observer's perceived geometry. A neutral
prompt means the relevant geometry or context is unavailable.

Check the focal twin and preview, then drop the asset. Cancelling the drag restores
the previous panel state. With no selected twin, the IDE attempts to create a
default local context, which needs a local Runtime. You can also submit through
the panel's [observable composer](SEMANTIC_COMPOSER.md).

The panel switches to progress in **Activities** and then exposes results in
**Observations**. For supported asynchronous jobs, use the stop control to request
cancellation. Inspect the final activity outcome before retrying a failed request.

## Explore the graph and observations

The twin editor combines a knowledge graph, an asset tree, and detailed observation
tabs. The graph supports focus changes, history navigation, depth, and layout
controls. The tree's context menu opens detailed views and can set an eligible
subject **as context**.

An observation card contains geometry and temporal extent, incoming and outgoing
relationships, content, and metadata. For a spatially distributed quality, the
runtime supplies a map. Click a marked timeline state to load that state's map.
Click the map to request a point value when supported by the runtime. Other
observation types or geometries may show an explanatory content placeholder.

> **Screenshot placeholder — Observation results:** show a graph focused on a
> result, its relationship card, temporal states, quality map, and point-value readout.

## Use the panel while authoring

Show or hide the current editor's twin panel using the status-bar control.
**Activities**, **Observations**, and **Observers** each have a search field.
The home button returns the observation tree to the graph root; its context menu
offers context reset and recorded commits. The icon beside an eligible subject
sets it as context; clicking again clears that context. The Scenarios tab may
be empty in the current installation.

## Delete a twin

The panel's delete control requires confirmation. Deletion removes the digital
twin together with its observations, storage, and schedule. Check the named twin
and preserve any results you need before confirming. The visible quality
**Export to filesystem...** command currently does not save a file; do not use it
as evidence that you have backed up results.
