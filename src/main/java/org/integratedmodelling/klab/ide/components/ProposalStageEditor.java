package org.integratedmodelling.klab.ide.components;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import javafx.beans.property.SimpleStringProperty;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import org.integratedmodelling.klab.api.services.resources.workflow.*;
import org.integratedmodelling.klab.api.services.resources.workflow.ProposalReview.*;

/** Native stage extension. Candidate bytes remain immutable; authoring edits a new submission. */
public final class ProposalStageEditor extends VBox {
  private static final ObjectMapper JSON = new ObjectMapper();
  private final ProposalReviewModel model;
  private final Runnable changed;
  private final Label problem = new Label();
  private final TextArea rationale = new TextArea();
  private final List<Node> authoringControls = new ArrayList<>();
  private final List<Evidence> evidence;
  private final List<Concept> concepts;
  private final List<Question> questions;
  private final List<QualityAnalysis> qualities;
  private final TextArea issues = new TextArea();
  private final TextArea shortfalls = new TextArea();
  private final Map<String, TextField> candidateFields = new LinkedHashMap<>();
  private final TextArea actionIds = new TextArea();
  private Artifact uploadedOntology;
  private final TabPane tabs = new TabPane();
  private final Map<String, Runnable> lexicalTargets = new LinkedHashMap<>();

  public static WorkflowEditor.StageEditor create(Workflow workflow, Flow flow, Flow.State state,
      Workflow.StateSchema schema, boolean readOnly, Runnable changed) {
    boolean optedIn = workflow.getTransitions().values().stream()
        .filter(t -> t.getSourceStates().contains(schema.getId()))
        .anyMatch(t -> t.getMetadata().containsKey("proposalReviewOperation"));
    if (!optedIn && state.getProposalReview() == null) return null;
    boolean authoring = workflow.getTransitions().values().stream()
        .filter(t -> t.getSourceStates().contains(schema.getId()))
        .anyMatch(t -> "SUBMIT".equals(String.valueOf(t.getMetadata().get("proposalReviewOperation"))));
    var view = new ProposalStageEditor(new ProposalReviewModel(state.getProposalReview(), authoring, readOnly), state, changed);
    return new WorkflowEditor.StageEditor(view, () -> view.model.editable(), state::getMetadata,
        view::readOnly, view::focusReview, null);
  }

  public ProposalStageEditor(ProposalReviewModel model, Flow.State state, Runnable changed) {
    super(10);
    setPadding(new Insets(10));
    this.model = model;
    this.changed = changed;
    var dossier = model.dossier();
    evidence = copy(dossier == null ? null : dossier.evidence());
    concepts = copy(dossier == null ? null : dossier.concepts());
    questions = copy(dossier == null ? null : dossier.questions());
    qualities = copy(dossier == null ? null : dossier.qualityAnalyses());
    var status = model.original() == null ? "Unsubmitted candidate" : String.valueOf(model.original().status());
    var heading = new Label("Proposal review • " + status);
    heading.setStyle("-fx-font-size: 16px; -fx-font-weight: bold;");
    var notice = new Label("Scientific acceptability requires an explicit human decision. Automatic checks do not confer approval. "
        + "Implication and detection are syntax-only; graph consequences are not executed.");
    notice.setWrapText(true);
    problem.setWrapText(true);
    problem.setStyle("-fx-text-fill: -color-danger-fg;");
    problem.visibleProperty().bind(problem.textProperty().isNotEmpty());
    problem.managedProperty().bind(problem.visibleProperty());
    tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
    tabs.getTabs().addAll(tab("Candidate", candidateForm(state)),
        tab("Sources", rows(evidence, Evidence.class, "id", "source", "locator")),
        tab("Concepts & bindings", rows(concepts, Concept.class, "id", "kind", "expression")),
        tab("Questions & coverage", new VBox(8,
            wrapped("Concept and observable mappings are claims to inspect, not proof of coverage. Targets of 5 per kind and 15 questions must not be padded. Reviewer questions and invalid probes require separate evidence."),
            rows(questions, Question.class, "id", "text", "intent"))),
        tab("Quality analysis", new VBox(8,
            wrapped("Keep unknown or unmeasured evidence distinct from domain categories. Inspect predicate boundaries and ordering scope in the source proposal."),
            rows(qualities, QualityAnalysis.class, "qualityId", "limitation"))),
        tab("Issues & checks", checks(dossier)), tab("Decision", decision()));
    getChildren().addAll(heading, notice, problem, tabs);
    VBox.setVgrow(tabs, Priority.ALWAYS);
    readOnly(!model.editable());
    if (!model.supported()) problem.setText("Unsupported proposal review version. This stage is inspection-only.");
    else if (!model.authoring() && model.candidate() == null) problem.setText("Missing candidate. Submission is blocked; reload or contact the workflow owner.");
  }

  public ProposalReviewModel model() { return model; }
  public void focusReview(org.integratedmodelling.klabeditor.MonacoEditorView.ReviewMarkerClick marker) {
    var focus = lexicalTargets.get(marker.action());
    if (focus != null) { tabs.getSelectionModel().select(2); focus.run(); }
  }
  public void showProblem(String text) { problem.setText(Objects.requireNonNullElse(text, "")); }
  public void bindUpload(byte[] bytes, String attachmentId, String mediaType) {
    if (!model.authoring() || !model.editable()) return;
    Candidate candidate;
    if ("application/vnd.klab.proposal+yaml".equals(mediaType)) {
      candidate = ProposalCandidateReader.read(bytes, attachmentId,
          uploadedOntology == null && model.candidate() != null ? model.candidate().ontology() : uploadedOntology);
    } else if ("application/vnd.klab.ontology".equals(mediaType)) {
      uploadedOntology = new Artifact(attachmentId, ProposalCandidateReader.digest(bytes));
      var previous = model.candidate();
      if (previous == null) return;
      candidate = new Candidate(previous.proposalId(), previous.revisionId(), previous.supersedesRevision(),
          previous.proposal(), uploadedOntology, previous.actionIds(), previous.contextDigest());
    } else return;
    candidateFields.get("Proposal ID").setText(candidate.proposalId());
    candidateFields.get("Revision ID").setText(candidate.revisionId());
    candidateFields.get("Supersedes revision").setText(Objects.requireNonNullElse(candidate.supersedesRevision(), ""));
    candidateFields.get("Proposal attachment ID").setText(candidate.proposal().attachmentId());
    candidateFields.get("Proposal checksum").setText(candidate.proposal().checksum());
    candidateFields.get("Import context digest").setText(candidate.contextDigest());
    candidateFields.get("Ontology attachment ID").setText(candidate.ontology() == null ? "" : candidate.ontology().attachmentId());
    candidateFields.get("Ontology checksum").setText(candidate.ontology() == null ? "" : candidate.ontology().checksum());
    actionIds.setText(String.join("\n", candidate.actionIds()));
    model.candidate(candidate);
    changed.run();
  }
  public static Operation operation(Workflow.TransitionSchema transition) {
    try { return Operation.valueOf(String.valueOf(transition.getMetadata().get("proposalReviewOperation"))); }
    catch (IllegalArgumentException e) { return null; }
  }
  public Command command(Workflow.TransitionSchema transition) { return model.command(operation(transition)); }
  public void readOnly(boolean value) {
    model.readOnly(value);
    authoringControls.forEach(control -> {
      boolean locked = !model.editable() || !model.authoring();
      if (control instanceof TextInputControl input) input.setEditable(!locked);
      else control.setDisable(locked);
    });
    rationale.setEditable(model.editable());
  }

  private Node candidateForm(Flow.State state) {
    var box = new VBox(8);
    box.getChildren().add(wrapped("Bind the immutable uploaded proposal and optional ontology. When revising, use a fresh revision ID and supersede the previous revision. Full source scope, source status, imported parents and rationale remain in the attached proposal."));
    var c = model.candidate();
    var fields = candidateFields;
    fields.put("Proposal ID", field(c == null ? null : c.proposalId()));
    fields.put("Revision ID", field(c == null ? null : c.revisionId()));
    fields.put("Supersedes revision", field(c == null ? null : c.supersedesRevision()));
    fields.put("Proposal attachment ID", field(c == null || c.proposal() == null ? null : c.proposal().attachmentId()));
    fields.put("Proposal checksum", field(c == null || c.proposal() == null ? null : c.proposal().checksum()));
    fields.put("Ontology attachment ID", field(c == null || c.ontology() == null ? null : c.ontology().attachmentId()));
    fields.put("Ontology checksum", field(c == null || c.ontology() == null ? null : c.ontology().checksum()));
    fields.put("Import context digest", field(c == null ? null : c.contextDigest()));
    fields.forEach((name, input) -> { box.getChildren().addAll(new Label(name), input); authoringControls.add(input); });
    var actions = actionIds;
    actions.setText(c == null ? "" : String.join("\n", c.actionIds()));
    actions.setPrefRowCount(3);
    authoringControls.add(actions);
    box.getChildren().addAll(new Label("Complete ordered action IDs (one per line)"), actions);
    Runnable update = () -> {
      if (!model.editable() || !model.authoring()) return;
      var ontologyId = fields.get("Ontology attachment ID").getText();
      model.candidate(new Candidate(fields.get("Proposal ID").getText(), fields.get("Revision ID").getText(),
          nullable(fields.get("Supersedes revision").getText()),
          new Artifact(fields.get("Proposal attachment ID").getText(), fields.get("Proposal checksum").getText()),
          ontologyId.isBlank() && fields.get("Ontology checksum").getText().isBlank() ? null : new Artifact(ontologyId, fields.get("Ontology checksum").getText()), lines(actions.getText()), fields.get("Import context digest").getText()));
      changed.run();
    };
    fields.values().forEach(input -> input.textProperty().addListener((o,a,b) -> update.run()));
    actions.textProperty().addListener((o,a,b) -> update.run());
    for (var attachment : state.getAttachments()) {
      var ref = new TextArea(attachment.getFileName() + "\nID: " + attachment.getId() + "\nChecksum: " + attachment.getChecksum());
      ref.setEditable(false); ref.setPrefRowCount(3);
      box.getChildren().add(ref);
    }
    return box;
  }

  private <T> Node rows(List<T> records, Class<T> type, String... columns) {
    var table = new TableView<T>();
    table.getItems().setAll(records);
    table.setPrefHeight(230);
    table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
    table.setPlaceholder(new Label("No " + type.getSimpleName() + " data supplied. This is not a successful check."));
    for (var key : columns) {
      var column = new TableColumn<T, String>(label(key));
      column.setCellValueFactory(cell -> new SimpleStringProperty(JSON.valueToTree(cell.getValue()).path(key).asText()));
      column.setPrefWidth(key.equals("id") ? 140 : 260);
      table.getColumns().add(column);
    }
    var detail = new TextArea(); detail.setEditable(false); detail.setPrefRowCount(10);
    table.getSelectionModel().selectedItemProperty().addListener((o,a,b) -> {
      if (b == null) { detail.clear(); return; }
      var text = new StringBuilder();
      JSON.valueToTree(b).fields().forEachRemaining(entry -> text.append(label(entry.getKey())).append(": ")
          .append(entry.getValue().isTextual() ? entry.getValue().asText() : entry.getValue()).append("\n"));
      detail.setText(text.toString());
    });
    table.getSelectionModel().selectFirst();
    if (type == Concept.class) for (var record : records) {
      var concept = (Concept) record;
      lexicalTargets.put(concept.id(), () -> { table.getSelectionModel().select(record); table.scrollTo(record); table.requestFocus(); });
    }
    var add = new Button("Add " + type.getSimpleName());
    var edit = new Button("Edit selected");
    var remove = new Button("Remove selected");
    authoringControls.addAll(List.of(add, edit, remove));
    add.setOnAction(e -> editRecord(null, type, value -> { records.add(value); table.getItems().setAll(records); updateDossier(); }));
    edit.setOnAction(e -> {
      var selected = table.getSelectionModel().getSelectedItem();
      if (selected != null) editRecord(selected, type, value -> { records.set(records.indexOf(selected), value); table.getItems().setAll(records); table.getSelectionModel().select(value); updateDossier(); });
    });
    remove.setOnAction(e -> { var selected = table.getSelectionModel().getSelectedItem(); if (selected != null) { records.remove(selected); table.getItems().setAll(records); updateDossier(); } });
    return new VBox(8, table, new HBox(8, add, edit, remove), new Label("Selected record — claims and references"), detail);
  }

  private <T> void editRecord(T value, Class<T> type, java.util.function.Consumer<T> save) {
    if (!model.editable() || !model.authoring()) return;
    var dialog = new Dialog<T>(); dialog.setTitle("Edit " + type.getSimpleName());
    if (getScene() != null) dialog.initOwner(getScene().getWindow());
    dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
    var form = new VBox(8); var inputs = new LinkedHashMap<String, TextArea>();
    var tree = value == null ? JSON.createObjectNode() : JSON.valueToTree(value);
    for (var component : type.getRecordComponents()) {
      var node = tree.path(component.getName());
      var input = new TextArea(); input.setPrefRowCount(2);
      if (node.isArray()) { var entries = new ArrayList<String>(); node.forEach(n -> entries.add(n.asText())); input.setText(String.join("\n", entries)); }
      else input.setText(node.isMissingNode() || node.isNull() ? component.getType() == boolean.class ? "false" : "" : node.asText());
      inputs.put(component.getName(), input);
      form.getChildren().addAll(wrapped(label(component.getName())
          + (component.getType() == List.class ? " (one per line)"
              : component.getType().isEnum() ? " (" + Arrays.toString(component.getType().getEnumConstants()) + ")"
              : component.getType() == boolean.class ? " (true / false)" : "")), input);
    }
    var error = wrapped(""); form.getChildren().add(error);
    var scroll = new ScrollPane(form); scroll.setFitToWidth(true); scroll.setPrefViewportHeight(500); scroll.setPrefViewportWidth(620);
    dialog.getDialogPane().setContent(scroll);
    final Object[] result = {null};
    dialog.getDialogPane().lookupButton(ButtonType.OK).addEventFilter(javafx.event.ActionEvent.ACTION, event -> {
      try {
        var object = JSON.createObjectNode();
        for (var component : type.getRecordComponents()) {
          var text = inputs.get(component.getName()).getText();
          if (component.getType() == List.class) { var array = object.putArray(component.getName()); lines(text).forEach(array::add); }
          else if (component.getType() == boolean.class) {
            if (!text.equals("true") && !text.equals("false")) throw new IllegalArgumentException(component.getName() + " must be true or false");
            object.put(component.getName(), Boolean.parseBoolean(text));
          }
          else if (text.isBlank()) object.putNull(component.getName());
          else object.put(component.getName(), text);
        }
        result[0] = JSON.treeToValue(object, type);
      } catch (Exception ex) { error.setText("Cannot save: " + ex.getMessage()); event.consume(); }
    });
    dialog.setResultConverter(button -> button == ButtonType.OK ? type.cast(result[0]) : null);
    dialog.showAndWait().ifPresent(save);
  }

  private Node checks(BootstrapDossier dossier) {
    issues.setText(String.join("\n", dossier == null ? List.of() : safe(dossier.unresolvedSemantics())));
    issues.setPrefRowCount(5); authoringControls.add(issues);
    issues.textProperty().addListener((o,a,b) -> { if (model.editable() && model.authoring()) updateDossier(); });
    shortfalls.setText(String.join("\n", dossier == null ? List.of() : safe(dossier.coverageShortfalls())));
    shortfalls.setPrefRowCount(3); authoringControls.add(shortfalls);
    shortfalls.textProperty().addListener((o,a,b) -> { if (model.editable() && model.authoring()) updateDossier(); });
    var box = new VBox(8, new Label("Unresolved semantics (one per line)"), issues,
        new Label("Coverage shortfalls and rationale (one per line)"), shortfalls,
        wrapped("Checks below are server results for the submitted candidate, not for unsaved edits. NOT_RUN and BLOCKED are not passes."));
    if (model.checks().isEmpty()) box.getChildren().add(new Label("No validator results supplied."));
    for (var check : model.checks()) box.getChildren().add(wrapped(check.kind() + " — " + check.status() + "\n" + String.join("\n", check.messages())));
    return box;
  }

  private Node decision() {
    rationale.setPromptText("Scientific rationale, requested changes, or resubmission notes tied to this exact candidate");
    rationale.setPrefRowCount(7);
    rationale.textProperty().addListener((o,a,b) -> { if (model.editable()) { model.rationale(b); changed.run(); } });
    var previous = model.original();
    return new VBox(8, wrapped("Use the workflow transition selector below to submit, request changes, advance, accept or reject. Permissions come from the workflow service. Review decisions require rationale and confirmation of the exact candidate and actions."),
        new Label("Your rationale"), rationale,
        wrapped(previous == null ? "No previous decision." : "Previous actor: " + previous.actor() + "\nPrevious rationale: " + Objects.toString(previous.rationale(), "")),
        wrapped("Edits are local until transition submission. Reset discards the selected stage draft. A failed submission preserves the draft; reload only after copying notes needed for reconciliation."));
  }
  private void updateDossier() { model.dossier(new BootstrapDossier(List.copyOf(evidence), List.copyOf(concepts), List.copyOf(questions), List.copyOf(qualities), lines(issues.getText()), lines(shortfalls.getText()))); changed.run(); }
  private static Tab tab(String title, Node content) { var scroll = new ScrollPane(content); scroll.setFitToWidth(true); scroll.setPrefViewportHeight(450); if (content instanceof Region r) r.setPadding(new Insets(10)); return new Tab(title, scroll); }
  private static TextField field(String value) { return new TextField(Objects.requireNonNullElse(value, "")); }
  private static Label wrapped(String text) { var label = new Label(text); label.setWrapText(true); return label; }
  private static String label(String value) { return value.replaceAll("([a-z])([A-Z])", "$1 $2"); }
  private static String nullable(String value) { return value.isBlank() ? null : value; }
  private static List<String> lines(String value) { return Arrays.stream(value.split("\\R")).map(String::trim).filter(s -> !s.isEmpty()).toList(); }
  private static <T> List<T> safe(List<T> list) { return list == null ? List.of() : list; }
  private static <T> List<T> copy(List<T> list) { return new ArrayList<>(safe(list)); }
}
