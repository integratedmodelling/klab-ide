---
name: k.LAB Modeler IDE
description: A precise scientific workbench for semantic knowledge and inspectable observations.
colors:
  workbench-blue: "#0969da"
  selection-wash: "#ddf4ff"
  paper-white: "#ffffff"
  cool-gray: "#f6f8fa"
  foreground: "#24292f"
  muted-foreground: "#57606a"
  border: "#d0d7de"
  hover-surface: "#eaeef2"
  danger: "#cf222e"
  warning: "#9a6700"
  reasoner-blue: "#0052cc"
  reasoner-muted: "#b3d1ff"
  resources-green: "#29a329"
  resources-muted: "#c2f0c2"
  resolver-orange: "#cc6600"
  resolver-muted: "#ffd9b3"
  runtime-red: "#cc0000"
  runtime-muted: "#ffb3b3"
typography:
  body:
    fontSize: "14px"
  observation-title:
    fontSize: "13px"
    fontWeight: 700
  card-title:
    fontSize: "11px"
    fontWeight: 700
  inspector-label:
    fontSize: "9px"
  geometry-code:
    fontFamily: "Consolas, JetBrains Mono, Courier New, monospace"
    fontSize: "10px"
rounded:
  control: "4px"
  chip: "5px"
  selection: "6px"
  inset: "7px"
  card: "8px"
spacing:
  icon-padding: "2px"
  toolbar-gap: "3px"
  card-padding: "8px"
components:
  button-standard:
    backgroundColor: "{colors.cool-gray}"
    textColor: "{colors.foreground}"
    rounded: "{rounded.control}"
    padding: "8px 12px"
  button-standard-hover:
    backgroundColor: "{colors.hover-surface}"
  button-icon:
    backgroundColor: "transparent"
    padding: "2px"
  button-icon-selected:
    backgroundColor: "{colors.selection-wash}"
    rounded: "{rounded.control}"
  input:
    backgroundColor: "{colors.paper-white}"
    textColor: "{colors.foreground}"
    rounded: "{rounded.control}"
    padding: "8px 12px"
  inspection-card:
    backgroundColor: "{colors.paper-white}"
    rounded: "{rounded.card}"
  geometry-chip:
    backgroundColor: "{colors.cool-gray}"
    textColor: "{colors.muted-foreground}"
    rounded: "{rounded.chip}"
    padding: "2px 5px"
---

# Design System: k.LAB Modeler IDE

## Overview

**Creative North Star: "Scientific Workbench"**

Precise, restrained, and information-dense: familiar desktop controls organize
semantic knowledge, service operations, and inspectable observations. Components
are precise and utilitarian. The work area carries the scientific content while
compact navigation and inspection tools support it.

This is a record of the incumbent JavaFX system, not a redesign. The metaphor,
descriptive language, color names, depth treatment, and component character were
confirmed with the user. Extraction is source-based: no running Modeler window or
committed screenshot baseline was available. Rendered spacing, contrast, native
focus behavior, and operating-system font metrics remain unverified.

**Key Characteristics:**

- Dockable desktop editors and a persistent service/navigation rail.
- Theme-aware neutral surfaces, fine borders, and selective tonal emphasis.
- Separate UI-state and semantic-type colors, plus declared service-role constants.
- Compact cards, breadcrumbs, maps, metadata, and inspection history.

Sources: `Theme.java`, `KlabIDEApplication.java`, `KlabIDEController.java`,
`ide.fxml`, `custom.css`, `InspectorView.java`, `IconButton.java`,
`NotebookViewer.java`, and the Maven-resolved AtlantaFX 2.1.0 theme stylesheet.
Java source paths are relative to `src/main/java/org/integratedmodelling/klab/ide`;
FXML/CSS paths are relative to `src/main/resources/org/integratedmodelling/klab/ide`.

## Colors

Workbench Blue and Paper White / Cool Gray describe the fallback Primer Light
theme. Frontmatter records that theme's resolved values; it does not replace
JavaFX looked-up colors in implementation.

### Primary

- **Workbench Blue:** accent text, selected emphasis, and focus in Primer Light.
- **Selection Wash:** a quiet selected-toggle surface, paired with accent text
  and borders where the component defines them.

### Secondary

- **Reasoner Blue, Resources Green, Resolver Orange, Runtime Red:** service-role
  colors defined in `Theme.java`, with corresponding muted variants. These are
  currently fixed literals rather than fully theme-adaptive roles. No consumers
  of these constants were found in the IDE source; they are declared palette
  assets, not verified colors of the rendered service buttons. Those buttons
  initially use muted theme icons and later encode provisioning state.
- **Danger and Warning:** inherited UI feedback roles. Runtime Red identifies
  a service role and must not be interpreted as an error solely because it is red.

### Neutral

- **Paper White:** default cards, editor-adjacent surfaces, and inputs.
- **Cool Gray:** inset areas, toolbars, and subtle surface separation.
- **Foreground / Muted Foreground:** primary information and supporting details.
- **Border / Hover Surface:** thin boundaries and interactive background changes.

**The Role Separation Rule.** Keep UI feedback, service identity, and semantic
type colors distinct in meaning. Semantic colors come from shared `Branding`
constants and `KimStyle` token metadata, not a new local palette.

**The Theme Inheritance Rule.** Use the active theme's `-color-*` lookups for
ordinary surfaces and text. `Theme.java` supports Primer Light/Dark, Nord
Light/Dark, Cupertino Light/Dark, and Dracula; it falls back to Primer Light when
the persisted theme is unavailable. Theme variants are not identical palettes.
Existing fixed-color exceptions are implementation facts, not a mandate to add
more hardcoded colors.

## Typography

Body text inherits the JavaFX/AtlantaFX font family; no single cross-platform
body family is established in the project stylesheet. The fallback theme's root
size is the frontmatter body size. Icon fonts are glyph sources, not text fonts.

The hierarchy is compact: observation headings have a stronger weight, supporting
semantics use smaller muted text, and metadata, chips, and breadcrumbs use very
small labels. Geometry code and metadata keys declare a monospace family list.
JavaFX's actual family resolution requires runtime verification.

The frontmatter's inspector label and geometry-code roles record existing sizes,
not an accessibility recommendation. Other observed roles include article card
titles (16px bold), summaries (12px), and observation semantics (11px).

**The Density Is Evidence Rule.** Preserve the distinction between observed
density and a safe reusable default. Do not propagate the inspector's smallest
labels into new primary controls without checking native text scaling and
readability.

## Layout

The shell is a JavaFX `BorderPane`, with a narrow left rail, a central work area,
an optional bottom inspector, a status bar, and a notification drawer. FXML
declares a rail preferred width of 69px and a status bar preferred height of
40px. The initial application scene is 1480 by 1060; the FXML root has separate
preferred dimensions. These are initial/preferred sizes, not responsive
breakpoints or a guaranteed minimum window size.

The rail separates six work destinations from a bordered group of service and
distribution controls, with settings, inspector, and profile utilities below.
Editors use tabs that can detach into floating windows. The docked inspector
fixes its height at 300px; undocked inspection is resizable. Notifications occupy
a 280px-wide scrolling drawer.

Spacing is locally defined through JavaFX Insets and CSS rather than a complete
global scale. Repeated values in frontmatter capture small toolbar gaps and card
padding; they are not an invented universal spacing system. No mobile layout or
browser breakpoint contract is established for the native shell.

## Elevation & Depth

**Tonal layers with lightly lifted cards** describe the incumbent treatment.
Most workbench surfaces use tone and thin borders. Geometry, metadata, histogram,
and article cards add a subtle Gaussian shadow. Inspector containers remain
largely flat. Floating windows provide actual native-window depth.

The recurring JavaFX card effect is
`dropshadow(gaussian, rgba(27, 31, 36, 0.08), 10, 0.16, 0, 2)`.
The separate sunken-icon effect is a localized exception, not the card standard.
The sidecar retains native effect syntax; its HTML preview translation is only
an approximation and must not replace JavaFX source.

## Shapes

Controls use modest rounding; cards use slightly softer corners. Chips and inset
map/plot surfaces sit between those sizes. Carousel selection uses a fine rounded
outline around a wrapper rather than painting over a skinned child. The rail
uses circular controls inherited from AtlantaFX. Certificate drop zones have
their own rounded dashed border.

This is not one radius applied everywhere. Component roles in frontmatter
record the existing distinctions. Standard AtlantaFX controls also use nested
background insets and inner rounding that a single portable radius cannot fully
represent.

## Components

### Buttons

Precise and utilitarian. Standard controls inherit AtlantaFX's bordered surface,
hover change, focus treatment, armed state, and disabled opacity. IconButton
removes normal button chrome and uses subtle hover/armed backgrounds; toggle
selection receives an accent wash. Icon glyph size varies by context.

The custom icon-button stylesheet overrides backgrounds and borders. Its native
focus visibility must be checked before adopting it as a complete accessible
pattern. Existing mouse-only rail wiring is a critique finding, not recommended
activation behavior.

### Inputs / Fields

Ordinary inputs inherit AtlantaFX neutral borders, theme foreground, accent
focus, read-only surface treatment, and selected-text styling. The notebook's
command field adds command definitions, persisted history, and a prompt for a
command or URN with help assistance. Individual editors add their own validation
and asynchronous state behavior; no universal inline-error contract was found.

### Navigation

The shell uses icon-only destinations with fast tooltips and an outlined current
view. Service icons also communicate provisioning and availability. Preserve the
separation between navigation and operational controls, while recognizing that
icon-only recognition and keyboard activation need improvement. Do not invent
mobile treatment for this desktop rail.

### Chips and Inspection History

Inspector breadcrumbs use muted labels, a default-surface background, fine
borders, and an accent-wash current item. Labels truncate with supporting
tooltips. Back/forward controls and detachable inspection support exploration.
The existing 18px toolbar controls and 12px breadcrumb close controls require
hit-area verification rather than becoming a general size standard.

### Cards / Containers

Geometry, metadata, histogram, and article cards share theme surfaces, thin
borders, softly rounded corners, and the recurring light shadow. Observation
cards emphasize their title strip and semantic summary instead of assigning the
same shadow to every container. Nested plots/maps use subtle inset surfaces.

### Semantic Expressions and Observations

Expressions use shared semantic colors and bold/italic token metadata. Rich
inspection combines headings, semantic descriptions, identifiers, maps, quality
data, and explicit unsupported-content states. Maintain semantic distinctions
across text, icons, and editors; do not substitute generic status colors for
domain types.

## Do's and Don'ts

### Do:

- **Do** inherit active-theme surface and text roles in JavaFX controls.
- **Do** preserve distinct service-role and semantic-type color meanings.
- **Do** reuse the existing card, inspection-history, and dockable-editor patterns
  where their behavior fits the task.
- **Do** validate native focus, text scaling, and hit areas before treating compact
  inspection controls as reusable defaults.

### Don't:

- **Don't** flatten all supported themes into the light-theme frontmatter values.
- **Don't** replace shared semantic colors with a new local color scheme.
- **Don't** treat observed mouse-only wiring, tiny labels, or mismatched tooltips
  as endorsed design rules.
- **Don't** treat HTML sidecar previews as production JavaFX components or proof
  of native accessibility.
