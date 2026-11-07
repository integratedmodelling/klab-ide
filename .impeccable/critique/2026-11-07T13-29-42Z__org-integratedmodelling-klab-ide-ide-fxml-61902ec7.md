---
target: JavaFX scientific workbench
total_score: 20
max_score: 40
na_heuristics: 
p0_count: 0
p1_count: 2
target_identity: "file:C:\\Users\\mario\\git\\klab-ide\\src\\main\\resources\\org\\integratedmodelling\\klab\\ide\\ide.fxml"
target_fingerprint: "sha256:19b4e2e6bdfdac7dc6914812dc8e10dfb37ef10210db622d6d336b869184c025"
target_path: "C:\\Users\\mario\\git\\klab-ide\\src\\main\\resources\\org\\integratedmodelling\\klab\\ide\\ide.fxml"
timestamp: 2026-11-07T13-29-42Z
slug: org-integratedmodelling-klab-ide-ide-fxml-61902ec7
---
Method: dual-agent (A: /root/design_review · B: /root/design_evidence), source-only.

# Scientific Workbench critique

Target: src/main/resources/org/integratedmodelling/klab/ide/ide.fxml, with supporting controller, theme, notebook and inspector source. Reviewed 2026-11-07. All scores are provisional source-review estimates. No rendered Modeler window, screenshot baseline, native accessibility testing, text-scale test or contrast measurement was available.

## Design specificity and overall impression

The structure fits k.LAB: knowledge workspaces, resources, behaviors, service roles, and a focal digital twin reflect scientific work. The biggest opportunity is making the current task and its prerequisites easier to recognize without memorizing icon positions. Preserve the existing JavaFX workbench and shared semantic language.

## Design health

| Heuristic | Score /4 | Main finding |
|---|---:|---|
| System status | 2 | Retained notifications help; immediate messages expire after five seconds |
| Match with real world | 3 | Domain distinctions are preserved |
| User control | 2 | Docking and inspection history help; context reset is mislabeled |
| Consistency | 2 | Theme foundation is coherent; activation patterns differ |
| Error prevention | 2 | Service guards exist; reset intent is unclear |
| Recognition over recall | 1 | Icon-only navigation requires hovering or positional memory |
| Efficiency | 2 | CLI history helps experts; rail keyboard activation is incomplete |
| Minimalist design | 2 | Central work area is protected; opening content lacks task guidance |
| Error recovery | 2 | Notification history survives; actionable urgency needs clearer treatment |
| Help and documentation | 2 | Documentation and command help exist; first-task guidance is weak |
| Total | 20/40 | Provisional: Acceptable; significant improvements indicated |

## What works

- The focal twin has a stable location in the status bar (KlabIDEController.java:911, 947, 980).
- Notifications remain available in a reverse-chronological drawer (KlabIDEController.java:828, 1202).
- The notebook provides command help and persistent history (components/NotebookViewer.java:77, 84).

## Priority issues

### [P1] Primary controls use mouse-only activation handlers

KlabIDEController.java:850–891 binds inspector, local services, profile, settings, service buttons and all view buttons with setOnMouseClicked. Standard JavaFX keyboard activation dispatches action events. This is a verified wiring issue; keyboard traversal was not exercised. Use setOnAction, provide accessible names and verify native Tab/Space activation and appropriate platform accelerators. Suggested command: $impeccable harden.

### [P1] Navigation depends heavily on remembering icons

ide.fxml:29–59 defines six textless destinations; KlabIDEController.java:811–814 initially gives four service-role buttons the same icon. Tooltips mitigate discovery for pointer users. Six destinations and a separate six-control infrastructure group increase recognition burden; the magnitude requires user/rendered validation. Offer an expandable labeled rail and textual service role/status while preserving compact mode. Suggested command: $impeccable clarify.

### [P2] Different twin actions promise the same result

KlabIDEController.java:919, 927, 934 gives panel toggle, context reset and open-twin controls the same panel tooltip. Actual reset clears focal scope and can close the current twin editor (:992). No data deletion or unsaved-work loss was established. Name actions accurately (Clear focal digital twin; Open focal digital twin) and explain consequences. Suggested command: $impeccable clarify.

### [P2] The opening notebook introduces the platform before helping users resume work

components/NotebookViewer.java:113 opens About; components/cards/AboutViewComponent.java:72, 107, 151 composes welcome, introductory copy, resources and news. The card lacks direct resume/open-work task guidance. Add permission-aware workspace, recent-work and twin actions with prerequisites beside them; keep product information secondary. This is a source-grounded recommendation, not a measured onboarding failure. Suggested command: $impeccable onboard.

### [P2] Inspector controls are unusually small

custom.css:126–136 fixes toolbar buttons at 18px; :167 and :180 set 9px breadcrumb labels; :189–199 fixes close controls at 12px. Increase hit areas independently from glyph size and support readable native scaling. These values do not alone establish a WCAG failure or contrast defect. Suggested command: $impeccable typeset.

## Cognitive load

Grouping and progressive disclosure exist through the rail groups, notebook and hidden inspector. Six destinations plus six infrastructure controls create likely failures in chunking, minimal choices and recognition/working-memory support: a provisional moderate-load signal. This does not mean six destinations must be removed. Single-focus and visual hierarchy require rendered inspection. Preserve necessary domain distinctions.

## Emotional journey

Arrival establishes scientific identity but may leave users unsure where to start. Service-dependent actions can add uncertainty about prerequisites. Focal-twin context and retained notification history help restore continuity. Five-second banners may be missed during interruptions; the drawer means the feedback is not lost entirely. Reset tooltip mismatch weakens confidence at a context transition.

## Persona risks

- Alex: command history helps; rail mouse handlers omit ordinary keyboard button activation. No claim that all application shortcuts are absent.
- Sam: mouse handlers are the clearest barrier; tiny inspector controls, accessible naming, focus and native text scaling require verification. No screen-reader or contrast failure was measured.
- Riley: reset/open/panel tooltips promise identical actions but deliver different results. Notification timestamps merit checking against original event time: the drawer renders ZonedDateTime.now() (KlabIDEController.java:1214).

## Detector and independent evidence

Assessment B ran impeccable.cmd detect --json src/main/resources/org/integratedmodelling/klab/ide/ide.fxml. Exit 0, stdout [], zero emitted findings. The detector has no documented JavaFX/FXML analysis coverage. Empty output is not evidence of a clean native UI; browser overlay/CDP do not apply to this shell.

Both assessments independently identified mouse-only rail wiring. B also found Theme.FOREGROUND_COLOR is initialized once (Theme.java:153), while setCurrentTheme changes only stylesheet/state (:121–124). Consumers in KlabIDEController.java:343 and DocumentEditor.java:35 can retain the startup foreground after theme switching; visual impact is unverified.

B corrected potential false positives: no consumers were found for declared service-role color constants, so they do not establish visible service-color defects. IconButton source uses setOnAction. Custom CSS removes icon chrome but inherited AtlantaFX focus effects may remain; do not claim all focus is invisible.

## Minor observations

- AboutViewComponent.java:197: “Latests technical posts” should be “Latest technical posts.”
- NotebookViewer.java:36 retains “Here you can...” placeholder copy that could surface in an empty state.
- Use persistent treatment for actionable errors; the retained notification drawer already exists.
- Small-window layout and live theme switching need rendered verification.

## Questions to consider

- Should the opening notebook prioritize resuming work, checking service readiness, or learning the platform?
- Can service prerequisites be explained beside intended tasks while preserving the four service roles?
- Which inspector details need constant visibility and which can appear on demand?

No UI implementation was changed. DESIGN.md and .impeccable/design.json record the incumbent system and its limitations.
