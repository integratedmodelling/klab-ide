package org.integratedmodelling.klab.ide.components;

import atlantafx.base.theme.Styles;
import java.io.File;
import java.awt.image.BufferedImage;
import javafx.concurrent.Task;
import javafx.embed.swing.SwingFXUtils;
import javafx.scene.control.Dialog;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.image.ImageView;
import javafx.stage.Modality;
import org.integratedmodelling.klab.api.knowledge.KlabAsset;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Alert;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Separator;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import org.integratedmodelling.klab.api.data.Metadata;
import org.integratedmodelling.klab.api.scope.UserScope;
import org.integratedmodelling.klab.api.services.ResourcesService;
import org.integratedmodelling.klab.api.services.resources.workflow.Flow;
import org.integratedmodelling.klab.api.services.resources.workflow.WorkflowBehavior;
import org.integratedmodelling.klab.api.services.resources.workflow.Workflow;
import org.integratedmodelling.klab.api.services.resources.workflow.WorkflowParticipant;
import org.integratedmodelling.klab.api.services.resources.workflow.WorkflowRole;
import org.integratedmodelling.klab.api.services.runtime.Notification;
import org.integratedmodelling.klab.ide.KlabIDEController;
import org.integratedmodelling.klab.ide.components.generic.UploadBox;
import org.integratedmodelling.klab.ide.components.generic.CarouselBox;
import org.kordamp.ikonli.javafx.FontIcon;
import org.kordamp.ikonli.material2.Material2AL;

/**
 * Generic client-side editor and read-only browser for a persistent {@link Flow}.
 *
 * <p>The shell owns navigation, attachments and transition actions. A {@link StageEditorProvider}
 * supplies the stage-specific content and validation contract, so extensions never need to
 * reproduce workflow authorization or persistence controls.
 */
public class WorkflowEditor extends BorderPane implements AutoCloseable {

  private final javafx.scene.control.ToggleButton sideBySide =
      new javafx.scene.control.ToggleButton("", new FontIcon(org.kordamp.ikonli.bootstrapicons.BootstrapIcons.LAYOUT_SPLIT));
  private final Map<String, String> reviewMarkerStages = new LinkedHashMap<>();

  /** Enable pairing after the host resolves the flow's document. */
  public void configureSideBySide(BooleanSupplier toggle) {
    sideBySide.setVisible(true);
    sideBySide.setManaged(true);
    sideBySide.setOnAction(event -> sideBySide.setSelected(toggle.getAsBoolean()));
  }

  public void setSideBySide(boolean enabled) { sideBySide.setSelected(enabled); }

  /** Bind glyph IDs to concrete stage IDs, for example from attachment comments. */
  public void setReviewMarkerStages(Map<String, String> stages) {
    reviewMarkerStages.clear();
    reviewMarkerStages.putAll(stages);
  }

  public void reviewMarkerClicked(org.integratedmodelling.klabeditor.MonacoEditorView.ReviewMarkerClick click) {
    var stateId = reviewMarkerStages.get(click.id());
    if (stateId == null) return;
    var state = flow.getStates().get(stateId);
    if (state == null) return;
    if (selectedState == null || !Objects.equals(selectedState.getId(), state.getId())) show(state);
    if (selectedEditor != null) selectedEditor.focusReview().accept(click);
  }

  public void reviewCommentRequested(int lineNumber) {
    if (selectedEditor != null) selectedEditor.createComment().accept(lineNumber);
  }

  private static final DateTimeFormatter DATE =
      DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault());

  private record TransitionChoice(Workflow.TransitionSchema transition, String label) {}

  /** A specialized stage UI plus the callbacks needed by the generic action bar. */
  public record StageEditor(
      Node content,
      BooleanSupplier valid,
      Supplier<Map<String, Object>> metadata,
      Consumer<Boolean> readOnly,
      Consumer<org.integratedmodelling.klabeditor.MonacoEditorView.ReviewMarkerClick> focusReview,
      Consumer<Integer> createComment) {
    public StageEditor(Node content, BooleanSupplier valid, Supplier<Map<String, Object>> metadata,
        Consumer<Boolean> readOnly) {
      this(content, valid, metadata, readOnly, null, null);
    }

    public StageEditor {
      Objects.requireNonNull(content, "Stage editor content");
      valid = valid == null ? () -> true : valid;
      metadata = metadata == null ? Map::of : metadata;
      readOnly = readOnly == null ? ignored -> {} : readOnly;
      focusReview = focusReview == null ? ignored -> {} : focusReview;
      createComment = createComment == null ? ignored -> {} : createComment;
    }
  }

  /**
   * Selects a specialized editor for a state schema, or returns {@code null} for the default UI.
   */
  @FunctionalInterface
  public interface StageEditorProvider {
    StageEditor create(
        Workflow workflow,
        Flow flow,
        Flow.State state,
        Workflow.StateSchema schema,
        boolean readOnly,
        Runnable validationChanged);
  }

  private final ResourcesService service;
  private final UserScope scope;
  private final Workflow workflow;
  private final StageEditorProvider stageEditors;
  private final CarouselBox stageCarousel = new CarouselBox(Orientation.HORIZONTAL);
  private final Map<Node, Flow.State> stageCards = new LinkedHashMap<>();
  private final VBox stageArea = new VBox(12);
  private final Label status = new Label();
  private Label activeStatusChip;
  private Label startedChip;
  private Label revisionChip;
  private final Label errorMessage = new Label();
  private final Map<String, List<Flow.AttachmentUpload>> pendingAttachments = new LinkedHashMap<>();
  private final Runnable cancelJob;
  private final Consumer<Flow> initialized;
  private final Consumer<Flow> deleted;
  private Flow flow;
  private boolean provisional;
  private Flow.State selectedState;
  private StageEditor selectedEditor;
  private VBox actionBar;
  private UploadBox uploadBox;
  private VBox attachmentEntries;
  private ComboBox<TransitionChoice> transitionSelector;
  private Button submitButton;
  private Dialog<Void> workflowDiagram;
  private Task<BufferedImage> diagramTask;
  private boolean submitting;
  private final Map<String, StageEditor> proposalDrafts = new LinkedHashMap<>();
  private Consumer<Flow.State> stageLoaded;

  public void setOnStageLoaded(Consumer<Flow.State> listener) {
    stageLoaded = listener;
    if (listener != null && selectedState != null) listener.accept(selectedState);
  }

  public WorkflowEditor(
      ResourcesService service,
      UserScope scope,
      Workflow workflow,
      Flow flow,
      StageEditorProvider stageEditors) {
    this(service, scope, workflow, flow, stageEditors, null, null, null);
  }

  public WorkflowEditor(
      ResourcesService service,
      UserScope scope,
      Workflow workflow,
      Flow flow,
      StageEditorProvider stageEditors,
      Runnable cancelJob) {
    this(service, scope, workflow, flow, stageEditors, cancelJob, null, null);
  }

  public WorkflowEditor(
      ResourcesService service,
      UserScope scope,
      Workflow workflow,
      Flow flow,
      StageEditorProvider stageEditors,
      Runnable cancelJob,
      Consumer<Flow> initialized) {
    this(service, scope, workflow, flow, stageEditors, cancelJob, initialized, null);
  }

  public WorkflowEditor(
      ResourcesService service,
      UserScope scope,
      Workflow workflow,
      Flow flow,
      StageEditorProvider stageEditors,
      Runnable cancelJob,
      Consumer<Flow> initialized,
      Consumer<Flow> deleted) {
    this.service = Objects.requireNonNull(service);
    this.scope = Objects.requireNonNull(scope);
    this.workflow = Objects.requireNonNull(workflow);
    this.flow = Objects.requireNonNull(flow);
    this.stageEditors = stageEditors;
    this.cancelJob = cancelJob;
    this.initialized = initialized;
    this.deleted = deleted;
    this.provisional = flow.getRevision() == 0 && flow.getHistory().isEmpty();
    errorMessage.setWrapText(true);
    errorMessage.setStyle("-fx-text-fill: -color-danger-fg;");
    errorMessage.visibleProperty().bind(errorMessage.textProperty().isNotEmpty());
    errorMessage.managedProperty().bind(errorMessage.visibleProperty());
    setPadding(new Insets(12));
    setTop(new VBox(header(), stageBrowser()));
    var scroll = new ScrollPane(stageArea);
    scroll.setFitToWidth(true);
    setCenter(scroll);
    refresh(selectInitialState());
  }

  public Flow getFlow() {
    return flow;
  }

  public boolean isReadOnly() {
    return flow.isPublicRead() || flow.getStatus() == Flow.Status.CLOSED;
  }

  private Node header() {
    var title = new Label(workflow.getName() == null ? workflow.getId() : workflow.getName());
    title.getStyleClass().add(Styles.TITLE_3);
    var asset = new Label(flow.getAssetUrn());
    asset.setStyle("-fx-text-fill: -color-fg-muted;");
    var titleArea = new HBox(8, title, new Label("::"), asset);
    titleArea.setAlignment(Pos.CENTER_LEFT);
    activeStatusChip = chip("");
    startedChip = chip("");
    revisionChip = chip("");
    var metadata = new HBox(6, activeStatusChip, startedChip, revisionChip);
    metadata.setAlignment(Pos.CENTER_LEFT);
    var spacer = new HBox();
    HBox.setHgrow(spacer, Priority.ALWAYS);
    sideBySide.setVisible(false);
    sideBySide.setManaged(false);
    sideBySide.setTooltip(new Tooltip("Side-to-side workflow review"));
    sideBySide.setAccessibleText("Side-to-side workflow review");
    var box = new HBox(12, titleArea, spacer, metadata, sideBySide);
    box.setAlignment(Pos.CENTER_LEFT);
    box.setPadding(new Insets(0, 0, 10, 0));
    var header = new VBox();
    var diagram = new Button(null, new FontIcon(Material2AL.ACCOUNT_TREE));
    diagram.setId("workflow-diagram-button");
    diagram.getStyleClass().addAll(Styles.BUTTON_CIRCLE, Styles.FLAT);
    diagram.setAccessibleText("Show workflow diagram");
    diagram.setTooltip(new Tooltip("Show workflow diagram"));
    diagram.setOnAction(event -> showWorkflowDiagram());
    box.getChildren().add(diagram);
    if (canDeleteFlow()) {
      var delete = new Button(null, new FontIcon(Material2AL.DELETE));
      delete.getStyleClass().addAll(Styles.BUTTON_CIRCLE, Styles.FLAT, Styles.DANGER);
      delete.setAccessibleText("Delete flow");
      delete.setTooltip(new Tooltip("Delete flow"));
      delete.setOnAction(event -> deleteFlow());
      box.getChildren().add(delete);
    }
    if (isProposalWorkflow()) {
      box.getChildren().remove(metadata);
      header.setSpacing(5);
      header.getChildren().addAll(box, metadata, new Separator());
    } else header.getChildren().addAll(box, new Separator());
    return header;
  }

  private void showWorkflowDiagram() {
    if (workflowDiagram != null && workflowDiagram.isShowing()) {
      workflowDiagram.getDialogPane().getScene().getWindow().requestFocus();
      return;
    }
    var dialog = new Dialog<Void>();
    workflowDiagram = dialog;
    dialog.setTitle("Workflow: " + Objects.toString(workflow.getName(), workflow.getId()));
    if (getScene() != null && getScene().getWindow() != null) {
      dialog.initOwner(getScene().getWindow());
      dialog.initModality(Modality.NONE);
    } else {
      dialog.initModality(Modality.NONE);
    }
    dialog.setResizable(true);
    dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
    var loading = new VBox(12, new ProgressIndicator(), new Label("Loading workflow diagram..."));
    loading.setAlignment(Pos.CENTER);
    var content = new BorderPane(loading);
    content.setPrefSize(900, 620);
    dialog.getDialogPane().setContent(content);
    var task = new Task<BufferedImage>() {
      @Override
      protected BufferedImage call() {
        var image = service.info(workflow.getUrn(), KlabAsset.KnowledgeClass.WORKFLOW,
            BufferedImage.class, scope);
        if (image == null) throw new IllegalStateException("The workflow diagram is unavailable.");
        return image;
      }
    };
    diagramTask = task;
    task.setOnSucceeded(event -> {
      if (!dialog.isShowing()) return;
      var image = new ImageView(SwingFXUtils.toFXImage(task.getValue(), null));
      image.setPreserveRatio(true);
      image.setSmooth(true);
      image.setAccessibleText("Diagram of " + Objects.toString(workflow.getName(), workflow.getId()));
      var scroll = new ScrollPane(image);
      scroll.setPannable(true);
      scroll.viewportBoundsProperty().addListener((observable, previous, bounds) -> {
        image.setFitWidth(Math.max(1, bounds.getWidth() - 16));
        image.setFitHeight(Math.max(1, bounds.getHeight() - 16));
      });
      content.setCenter(scroll);
    });
    task.setOnFailed(event -> {
      if (!dialog.isShowing()) return;
      var message = new Label("Could not load the workflow diagram. " + errorMessage(task.getException()));
      message.setWrapText(true);
      message.setStyle("-fx-text-fill: -color-danger-fg;");
      BorderPane.setMargin(message, new Insets(20));
      content.setCenter(message);
    });
    dialog.setOnHidden(event -> {
      task.cancel(true);
      if (workflowDiagram == dialog) { workflowDiagram = null; diagramTask = null; }
    });
    dialog.show();
    var worker = new Thread(task, "workflow-diagram");
    worker.setDaemon(true);
    worker.start();
  }

  private Label chip(String text) {
    var chip = new Label(text);
    chip.getStyleClass().addAll(Styles.BG_NEUTRAL_SUBTLE, Styles.ROUNDED, Styles.TEXT_SMALL);
    chip.setPadding(new Insets(3, 7, 3, 7));
    return chip;
  }

  private boolean canDeleteFlow() {
    if (isProposalWorkflow()) return false;
    if (provisional) return false;
    var participant = WorkflowParticipant.from(scope);
    return participant.getRoles().contains(WorkflowRole.ADMIN)
        || (participant.getRoles().contains(WorkflowRole.EDITOR)
            && participant.isWorkflowPermitted(workflow)
            && Objects.equals(flow.getOwner(), participant.getIdentity()));
  }

  private void deleteFlow() {
    var confirmation = new Alert(Alert.AlertType.CONFIRMATION);
    confirmation.setTitle("Delete workflow");
    confirmation.setHeaderText("Delete this flow and its complete history?");
    confirmation.setContentText(
        "All stages, transitions, metadata, and attachments in this flow will be permanently deleted.");
    var delete = new ButtonType("Delete flow", ButtonBar.ButtonData.YES);
    var cancel = new ButtonType("Cancel", ButtonBar.ButtonData.CANCEL_CLOSE);
    confirmation.getButtonTypes().setAll(delete, cancel);
    if (getScene() != null && getScene().getWindow() != null)
      confirmation.initOwner(getScene().getWindow());
    if (confirmation.showAndWait().orElse(cancel) != delete) return;
    try {
      clearError();
      var removed = flow;
      service.deleteFlow(flow.getId(), scope);
      if (deleted != null) deleted.accept(removed);
      if (cancelJob != null) cancelJob.run();
    } catch (Throwable error) {
      fail(error);
    }
  }

  private Node stageBrowser() {
    stageCarousel.setPrefHeight(70);
    stageCarousel.setMaxHeight(70);
    stageCarousel.setMaxWidth(Double.MAX_VALUE);
    stageCarousel.setSelectionListener(card -> show(stageCards.get(card)));
    var box = new HBox(stageCarousel);
    box.setPadding(new Insets(10, 0, 0, 0));
    HBox.setHgrow(stageCarousel, Priority.ALWAYS);
    return box;
  }

  private Flow.State selectInitialState() {
    var participant = WorkflowParticipant.from(scope);
    return flow.getCurrentStateIds().stream()
        .map(flow.getStates()::get)
        .filter(Objects::nonNull)
        .filter(state -> state.getStatus() == Flow.StateStatus.OPEN)
        .sorted(
            Comparator.comparing(
                    (Flow.State state) -> !state.getAssignees().contains(participant.getIdentity()))
                .thenComparing(
                    Flow.State::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder())))
        .findFirst()
        .orElseGet(
            () ->
                flow.getStates().values().stream()
                    .sorted(
                        Comparator.comparing(
                            Flow.State::getCreatedAt,
                            Comparator.nullsLast(Comparator.naturalOrder())))
                    .findFirst()
                    .orElse(null));
  }

  private void refresh(Flow.State selection) {
    var active = flow.getStatus() != Flow.Status.CLOSED;
    activeStatusChip.setText(flow.getStatus().name());
    activeStatusChip.getStyleClass().remove(Styles.SUCCESS);
    if (active) activeStatusChip.getStyleClass().add(Styles.SUCCESS);
    startedChip.setText(
        "Started " + (flow.getCreatedAt() == null ? "unknown" : DATE.format(flow.getCreatedAt())));
    revisionChip.setText("Revision " + (provisional ? "draft" : flow.getRevision()));
    status.setText(
        flow.getStatus()
            + "  •  started "
            + (flow.getCreatedAt() == null ? "unknown" : DATE.format(flow.getCreatedAt()))
            + "  •  revision "
            + (provisional ? "draft — not yet started" : flow.getRevision())
            + (flow.isPublicRead() ? "  •  public read-only" : ""));
    var states = new ArrayList<>(flow.getStates().values());
    states.sort(
        Comparator.comparing(
            Flow.State::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder())));
    stageCards.clear();
    var cards = states.stream().map(this::stageCard).toList();
    stageCarousel.setItems(cards);
    var selected =
        selection == null
            ? null
            : states.stream()
                .filter(s -> Objects.equals(s.getId(), selection.getId()))
                .findFirst()
                .orElse(null);
    if (selected == null && !states.isEmpty()) selected = states.getFirst();
    var activeSelected = selected;
    if (activeSelected != null) {
      stageCards.entrySet().stream()
          .filter(entry -> Objects.equals(entry.getValue().getId(), activeSelected.getId()))
          .map(Map.Entry::getKey)
          .findFirst()
          .ifPresent(stageCarousel::selectItem);
    }
    show(selected);
  }

  private Node stageCard(Flow.State state) {
    var schema = workflow.getStates().get(state.getSchemaId());
    var name =
        state.getTitle() != null && !state.getTitle().isBlank()
            ? state.getTitle()
            : schema == null ? state.getSchemaId() : schema.getDescription();
    var title = new Label(name);
    title.setWrapText(true);
    title.setStyle("-fx-font-size: 0.9em;");
    title.setMaxWidth(Double.MAX_VALUE);
    var statusIcon =
        new FontIcon(
            state.getStatus() == Flow.StateStatus.CLOSED
                ? Material2AL.LOCK
                : Material2AL.LOCK_OPEN);
    statusIcon.setIconSize(14);
    statusIcon.setStyle(
        "-fx-icon-color: "
            + (state.getStatus() == Flow.StateStatus.CLOSED
                ? "-color-fg-muted"
                : "-color-success-fg")
            + ";");
    statusIcon.setAccessibleText(state.getStatus().name().toLowerCase());
    var owner = new Label(state.getOwner());
    owner.setStyle("-fx-font-size: 0.75em; -fx-text-fill: -color-fg-muted;");
    var footerSpacer = new HBox();
    HBox.setHgrow(footerSpacer, Priority.ALWAYS);
    var status = new HBox(owner, footerSpacer, statusIcon);
    status.setAlignment(Pos.CENTER_RIGHT);
    var card = new VBox(4, title, status);
    card.setPadding(new Insets(8));
    card.setMinHeight(54);
    card.setPrefHeight(54);
    card.setPrefWidth(214);
    card.setMaxWidth(Double.MAX_VALUE);
    card.setStyle(
        "-fx-background-color: -color-bg-subtle;"
            + "-fx-background-radius: 6;"
            + "-fx-border-color: -color-border-muted;"
            + "-fx-border-radius: 6;"
            + "-fx-border-width: 1;");
    stageCards.put(card, state);
    return card;
  }

  private void show(Flow.State state) {
    if (state == null) return;
    selectedState = state;
    var schema = workflow.getStates().get(state.getSchemaId());
    if (schema == null) {
      fail(new IllegalStateException("Missing workflow stage schema " + state.getSchemaId()));
      return;
    }
    stageArea.getChildren().clear();
    var heading = new Label(state.getTitle() == null ? schema.getDescription() : state.getTitle());
    heading.getStyleClass().add(Styles.TITLE_4);
    var instructions = new Label(schema.getInstructions());
    instructions.setWrapText(true);
    stageArea.getChildren().addAll(heading, instructions, errorMessage);

    boolean readOnly = !canEdit(state) && workflow.getTransitions().values().stream().noneMatch(
        transition -> canReviewTransition(flow, workflow, state, transition, WorkflowParticipant.from(scope)));
    selectedEditor = proposalDrafts.get(state.getId());
    if (selectedEditor == null) selectedEditor =
        stageEditors == null
            ? null
            : stageEditors.create(workflow, flow, state, schema, readOnly, this::updateActions);
    if (selectedEditor == null) selectedEditor = defaultEditor(state);
    if (provisional && selectedEditor.content() instanceof ProposalStageEditor proposal
        && proposal.model().candidate() == null && proposal.model().editable()) {
      for (var upload : pendingAttachments.getOrDefault(state.getId(), List.of()))
        proposal.bindUpload(upload.getContent(), "pending:" + ProposalCandidateReader.digest(upload.getContent()), upload.getMediaType());
    }
    if (selectedEditor.content() instanceof ProposalStageEditor) proposalDrafts.put(state.getId(), selectedEditor);
    selectedEditor.readOnly().accept(readOnly);
    stageArea.getChildren().add(selectedEditor.content());

    if (!schema.getAttachments().isEmpty()) {
      stageArea.getChildren().add(attachments(schema, !canEdit(state)));
    }
    actionBar = actions(schema, readOnly);
    stageArea.getChildren().add(actionBar);
    updateActions();
    if (stageLoaded != null) stageLoaded.accept(state);
  }

  private StageEditor defaultEditor(Flow.State state) {
    var title = new TextField(state.getTitle());
    title.setPromptText("Stage title");
    var description = new TextArea(state.getDescription());
    description.setPromptText("Describe this stage, its evidence, or its decision");
    description.setPrefRowCount(5);
    title.textProperty().addListener((ignored, old, value) -> state.setTitle(value));
    description.textProperty().addListener((ignored, old, value) -> state.setDescription(value));
    var content = new VBox(6, new Label("Title"), title, new Label("Description"), description);
    return new StageEditor(
        content,
        () -> true,
        state::getMetadata,
        readOnly -> {
          title.setEditable(!readOnly);
          description.setEditable(!readOnly);
        });
  }

  private Node attachments(Workflow.StateSchema schema, boolean readOnly) {
    attachmentEntries = new VBox(4);
    var list = new VBox(4, new Label("Attachments"), attachmentEntries);
    refreshAttachmentEntries();
    if (!readOnly) {
      var admittedRules = schema.getAttachments().stream().filter(rule ->
          !(selectedEditor.content() instanceof ProposalStageEditor proposal) || proposal.model().authoring()
              || (!"application/vnd.klab.proposal+yaml".equals(rule.getMediaType())
                  && !"application/vnd.klab.ontology".equals(rule.getMediaType()))).toList();
      if (admittedRules.isEmpty()) return list;
      var rules = new ComboBox<Workflow.AttachmentRule>();
      rules.getItems().setAll(admittedRules);
      rules.setCellFactory(ignored -> attachmentRuleCell());
      rules.setButtonCell(attachmentRuleCell());
      rules.getSelectionModel().selectFirst();
      var directory =
          Path.of(
              System.getProperty("java.io.tmpdir"),
              "klab-workflows",
              flow.getId(),
              selectedState.getId());
      uploadBox =
          new UploadBox(
              directory.toString(),
              uploadPrompt(rules.getValue()),
              file -> upload(file, rules.getValue()),
              (message, error) -> fail(error == null ? new IOException(message) : error));
      uploadBox.setPrefHeight(150);
      var attachmentDetails = new HBox(6);
      attachmentDetails.setAlignment(Pos.CENTER_RIGHT);
      var attachmentSpacer = new HBox();
      HBox.setHgrow(attachmentSpacer, Priority.ALWAYS);
      var selectorRow = new HBox(8, rules, attachmentSpacer, attachmentDetails);
      selectorRow.setAlignment(Pos.CENTER_LEFT);
      HBox.setHgrow(rules, Priority.ALWAYS);
      rules
          .valueProperty()
          .addListener(
              (ignored, previous, selected) -> {
                attachmentDetails.getChildren().setAll(attachmentRuleChips(selected));
                uploadBox.setPromptText(uploadPrompt(selected));
              });
      attachmentDetails.getChildren().setAll(attachmentRuleChips(rules.getValue()));
      list.getChildren().addAll(selectorRow, uploadBox);
    }
    return list;
  }

  private List<Node> attachmentRuleChips(Workflow.AttachmentRule rule) {
    if (rule == null) return List.of();
    var chips = new ArrayList<Node>();
    var requirement = new Label(rule.isRequired() ? "Required" : "Optional");
    requirement.getStyleClass().addAll(rule.isRequired() ? Styles.DANGER : Styles.SUCCESS);
    styleAttachmentChip(requirement);
    chips.add(requirement);
    if (rule.getMediaType() != null && !rule.getMediaType().isBlank()) {
      var mediaType = new Label(rule.getMediaType());
      styleAttachmentChip(mediaType);
      chips.add(mediaType);
    }
    var arity = new Label(attachmentArityLabel(rule));
    styleAttachmentChip(arity);
    chips.add(arity);
    return chips;
  }

  private void styleAttachmentChip(Label chip) {
    chip.getStyleClass().addAll(Styles.BG_NEUTRAL_SUBTLE, Styles.ROUNDED, Styles.TEXT_SMALL);
    chip.setPadding(new Insets(3, 7, 3, 7));
  }

  private String attachmentArityLabel(Workflow.AttachmentRule rule) {
    if (rule.getArity() < 0) return "Any number";
    if (rule.getArity() == 1) return "One file";
    return "Up to " + rule.getArity() + " files";
  }

  private void refreshAttachmentEntries() {
    if (attachmentEntries == null) return;
    attachmentEntries.getChildren().clear();
    for (var attachment : selectedState.getAttachments()) {
      var label = new Label(attachment.getFileName() + " (" + attachment.getType() + ")");
      if (selectedEditor.content() instanceof ProposalStageEditor
          && attachment.getMediaType() != null
          && (attachment.getMediaType().startsWith("text/") || attachment.getMediaType().contains("yaml")
              || attachment.getMediaType().contains("json") || attachment.getMediaType().equals("application/vnd.klab.ontology"))) {
        var inspect = new Button("Inspect source");
        inspect.setOnAction(event -> inspectProposalAttachment(attachment));
        attachmentEntries.getChildren().add(new HBox(8, label, inspect));
      } else attachmentEntries.getChildren().add(label);
    }
    for (var upload : pendingAttachments.getOrDefault(selectedState.getId(), List.of())) {
      var pending = new Label(upload.getFileName() + " (" + upload.getType() + ", pending)");
      pending.setStyle("-fx-text-fill: -color-accent-fg;");
      attachmentEntries.getChildren().add(pending);
    }
  }

  private ListCell<Workflow.AttachmentRule> attachmentRuleCell() {
    return new ListCell<>() {
      @Override
      protected void updateItem(Workflow.AttachmentRule rule, boolean empty) {
        super.updateItem(rule, empty);
        setText(empty || rule == null ? null : rule.getType());
      }
    };
  }

  private String attachmentRuleLabel(Workflow.AttachmentRule rule) {
    var label = new StringBuilder(rule.getType());
    label.append(rule.isRequired() ? " (required)" : " (optional)");
    if (rule.getMediaType() != null && !rule.getMediaType().isBlank())
      label.append("  •  ").append(rule.getMediaType());
    if (rule.getAssetType() != null) label.append("  •  ").append(rule.getAssetType());
    label.append("  •  ");
    if (rule.getArity() < 0) label.append("any number");
    else if (rule.getArity() == 1) label.append("one file");
    else label.append("up to ").append(rule.getArity()).append(" files");
    return label.toString();
  }

  private String uploadPrompt(Workflow.AttachmentRule rule) {
    return rule == null
        ? "Choose an attachment type before dropping a file"
        : "Drop a file for “" + rule.getType() + "”\nAccepted: " + attachmentRuleLabel(rule);
  }

  private void upload(File file, Workflow.AttachmentRule rule) {
    if (file == null || rule == null) return;
    if (submitting) {
      fail(new IllegalStateException("Upload was not added while a decision was being confirmed. Add it again after closing the confirmation."));
      return;
    }
    try {
      var upload = Flow.AttachmentUpload.create();
      upload.setType(rule.getType());
      upload.setFileName(file.getName());
      var detectedMediaType = Files.probeContentType(file.toPath());
      upload.setMediaType(
          rule.getMediaType() != null && !rule.getMediaType().contains("*")
                  ? rule.getMediaType()
                  : detectedMediaType != null ? detectedMediaType : "application/octet-stream");
      upload.setAssetType(
          rule.getAssetType() == null ? selectedState.getAssetType() : rule.getAssetType());
      upload.setContent(Files.readAllBytes(file.toPath()));
      clearError();
      if (provisional) {
        if (selectedEditor.content() instanceof ProposalStageEditor proposal) {
          if (pendingAttachments.getOrDefault(selectedState.getId(), List.of()).stream()
              .anyMatch(a -> a.getMediaType().equals(upload.getMediaType()))
              && (upload.getMediaType().equals("application/vnd.klab.proposal+yaml")
                  || upload.getMediaType().equals("application/vnd.klab.ontology")))
            throw new IllegalStateException("Initial submission needs one unambiguous proposal and at most one ontology. Cancel and restart to replace pending bytes.");
          proposal.bindUpload(upload.getContent(), "pending:" + ProposalCandidateReader.digest(upload.getContent()), upload.getMediaType());
        }
        pendingAttachments
            .computeIfAbsent(selectedState.getId(), ignored -> new ArrayList<>())
            .add(upload);
        refreshAttachmentEntries();
      } else {
        var stored = service.addFlowAttachment(flow.getId(), selectedState.getId(), upload, scope);
        if (stored == null) throw new IllegalStateException("No attachment was returned; reload to reconcile the upload");
        if (selectedEditor.content() instanceof ProposalStageEditor proposal)
          proposal.bindUpload(upload.getContent(), stored.getId(), stored.getMediaType());
        reload(selectedState.getId());
      }
    } catch (Throwable e) {
      fail(e);
    }
  }

  private VBox actions(Workflow.StateSchema schema, boolean readOnly) {
    var bar = new VBox(8);
    transitionSelector = null;
    submitButton = null;
    if (flow.getStatus() == Flow.Status.CLOSED && isAdmin() && !isProposalWorkflow()) {
      var reopen = new Button("Reopen flow");
      reopen.getStyleClass().add(Styles.ACCENT);
      reopen.setOnAction(event -> mutate(() -> service.reopenFlow(flow.getId(), scope), null));
      var buttons = new HBox(8, reopen);
      buttons.setAlignment(Pos.CENTER_RIGHT);
      bar.getChildren().add(buttons);
      return bar;
    }
    if (readOnly) return bar;

    var buttons = new HBox(8);
    buttons.setAlignment(Pos.CENTER_RIGHT);
    if (!provisional && flow.getCurrentStateIds().contains(selectedState.getId())) {
      for (var binding : schema.getActions()) {
        var button = new Button(binding.label() == null || binding.label().isBlank()
            ? binding.id() : binding.label());
        button.setTooltip(new Tooltip("Run " + binding.action() + " using the saved stage"));
        button.setOnAction(event -> discoverBehaviorAction(binding.id()));
        buttons.getChildren().add(button);
      }
    }

    if (provisional) {
      var cancelWorkflow = new Button("Cancel workflow");
      cancelWorkflow.getStyleClass().add(Styles.DANGER);
      cancelWorkflow.setOnAction(
          event -> {
            if (!requestClose()) return;
            pendingAttachments.clear();
            proposalDrafts.clear();
            if (cancelJob != null) cancelJob.run();
          });
      buttons.getChildren().add(cancelWorkflow);
    }

    var cancel = new Button("Reset");
    cancel.setOnAction(event -> {
      if (!discardProposalDraft("Discard the selected stage's unsaved proposal edits?")) return;
      proposalDrafts.remove(selectedState.getId());
      show(selectedState);
    });
    var delete = new Button("Delete");
    delete.setOnAction(event -> deleteStage());
    delete.setDisable(
        flow.getCurrentStateIds().contains(selectedState.getId())
            || flow.getHistory().stream()
                .anyMatch(
                    transaction ->
                        Objects.equals(selectedState.getId(), transaction.getSourceStateId())
                            || Objects.equals(
                                selectedState.getId(), transaction.getTargetStateId())));
    buttons.getChildren().add(cancel);
    if (!isProposalWorkflow()) buttons.getChildren().add(delete);
    if (!provisional && !(selectedEditor.content() instanceof ProposalStageEditor)) {
      var update = new Button("Update");
      update.setOnAction(event -> updateStage());
      update.getProperties().put("workflow-update", Boolean.TRUE);
      buttons.getChildren().add(update);
    }
    var transitions = workflow.admittedTransitions(flow, selectedState.getId(), scope).stream()
        .filter(transition -> canEdit(selectedState) || canReviewTransition(flow, workflow, selectedState, transition, WorkflowParticipant.from(scope)))
        .toList();
    if (!transitions.isEmpty()) {
      var placeholder = new TransitionChoice(null, "-- Choose the next stage --");
      transitionSelector = new ComboBox<>();
      transitionSelector.getItems().add(placeholder);
      transitions.stream()
          .map(transition -> new TransitionChoice(transition, transitionLabel(transition)))
          .forEach(transitionSelector.getItems()::add);
      transitionSelector.setCellFactory(ignored -> transitionChoiceCell());
      transitionSelector.setButtonCell(transitionChoiceCell());
      transitionSelector.getSelectionModel().select(placeholder);
      transitionSelector.setMaxWidth(Double.MAX_VALUE);
      transitionSelector.setAccessibleText("Choose the next workflow stage");
      HBox.setHgrow(transitionSelector, Priority.ALWAYS);
      submitButton = new Button("Submit");
      submitButton.getProperties().put("workflow-confirm", Boolean.TRUE);
      submitButton.getStyleClass().add(Styles.ACCENT);
      submitButton.setAccessibleText("Submit selected workflow transition");
      submitButton.setTooltip(new Tooltip("Submit the stage and move to the selected next stage"));
      submitButton.setOnAction(
          event -> {
            var choice = transitionSelector.getValue();
            if (choice != null && choice.transition() != null) confirm(choice.transition());
          });
      transitionSelector
          .valueProperty()
          .addListener((ignored, previous, selected) -> updateActions());
      var transitionRow = new HBox(transitionSelector);
      HBox.setHgrow(transitionSelector, Priority.ALWAYS);
      bar.getChildren().add(transitionRow);
      buttons.getChildren().add(submitButton);
    }
    bar.getChildren().add(buttons);
    return bar;
  }

  private void discoverBehaviorAction(String actionId) {
    if (submitting || !requestClose()) return;
    String flowId = flow.getId();
    String stateId = selectedState.getId();
    submitting = true;
    setDisable(true);
    var task = new Task<List<WorkflowBehavior.AvailableAction>>() {
      @Override protected List<WorkflowBehavior.AvailableAction> call() {
        return service.getFlowActions(flowId, stateId, scope);
      }
    };
    task.setOnFailed(event -> { submitting = false; setDisable(false); fail(task.getException()); });
    task.setOnSucceeded(event -> {
      submitting = false; setDisable(false);
      var action = task.getValue().stream().filter(candidate -> candidate.id().equals(actionId))
          .findFirst().orElse(null);
      if (action == null) { fail(new IllegalStateException("This action is no longer available")); return; }
      promptBehaviorAction(flowId, stateId, action);
    });
    var worker = new Thread(task, "workflow-action-discovery"); worker.setDaemon(true); worker.start();
  }

  private void promptBehaviorAction(String flowId, String stateId, WorkflowBehavior.AvailableAction action) {
    var dialog = new Dialog<Map<String, Object>>();
    dialog.setTitle(action.label() == null ? action.id() : action.label());
    dialog.setHeaderText("Runs against the saved stage. The editor refreshes after completion; save any changes first.");
    if (getScene() != null) dialog.initOwner(getScene().getWindow());
    var content = new VBox(8);
    var fields = new LinkedHashMap<WorkflowBehavior.Parameter, TextField>();
    for (var parameter : action.missing()) {
      var field = new TextField();
      field.setPromptText(parameter.javaType() == null ? "Text" : parameter.javaType());
      content.getChildren().addAll(new Label(parameter.name()), field);
      fields.put(parameter, field);
    }
    var validation = new Label(); validation.setWrapText(true);
    content.getChildren().add(validation);
    dialog.getDialogPane().setContent(content);
    var run = new ButtonType("Run", ButtonBar.ButtonData.OK_DONE);
    dialog.getDialogPane().getButtonTypes().addAll(run, ButtonType.CANCEL);
    var values = new LinkedHashMap<String, Object>();
    dialog.getDialogPane().lookupButton(run).addEventFilter(javafx.event.ActionEvent.ACTION, event -> {
      values.clear();
      try {
        for (var entry : fields.entrySet())
          values.put(entry.getKey().name(), behaviorInput(entry.getKey(), entry.getValue().getText()));
      } catch (IllegalArgumentException invalid) { validation.setText(invalid.getMessage()); event.consume(); }
    });
    dialog.setResultConverter(button -> button == run ? values : null);
    dialog.showAndWait().ifPresent(parameters -> {
      submitting = true; setDisable(true);
      var request = new WorkflowBehavior.ActionRequest(action.revision(), parameters);
      var task = new Task<Flow>() {
        @Override protected Flow call() { return service.executeFlowAction(flowId, stateId, action.id(), request, scope); }
      };
      task.setOnFailed(event -> { submitting = false; setDisable(false); fail(task.getException()); });
      task.setOnSucceeded(event -> {
        submitting = false; setDisable(false);
        if (task.getValue() == null) { fail(new IllegalStateException("No action result; reload before retrying")); return; }
        flow = task.getValue(); proposalDrafts.clear();
        refresh(flow.getStates().get(stateId));
      });
      var worker = new Thread(task, "workflow-action"); worker.setDaemon(true); worker.start();
    });
  }

  static Object behaviorInput(WorkflowBehavior.Parameter parameter, String value) {
    if (parameter.behaviorType() != null)
      throw new IllegalArgumentException(parameter.name() + " requires an agent; configure it on the server");
    String type = parameter.javaType() == null ? "string" : parameter.javaType().toLowerCase(java.util.Locale.ROOT);
    if (type.startsWith("java.lang.")) type = type.substring(10);
    try {
      return switch (type) {
        case "string" -> value;
        case "boolean" -> {
          if (!"true".equalsIgnoreCase(value) && !"false".equalsIgnoreCase(value))
            throw new IllegalArgumentException("Enter true or false");
          yield Boolean.valueOf(value);
        }
        case "int", "integer" -> Integer.valueOf(value);
        case "long" -> Long.valueOf(value);
        case "double" -> Double.valueOf(value);
        case "float" -> Float.valueOf(value);
        default -> throw new IllegalArgumentException("No form editor for " + parameter.javaType());
      };
    } catch (IllegalArgumentException invalid) {
      throw new IllegalArgumentException(parameter.name() + ": " + invalid.getMessage());
    }
  }

  private ListCell<TransitionChoice> transitionChoiceCell() {
    return new ListCell<>() {
      @Override
      protected void updateItem(TransitionChoice choice, boolean empty) {
        super.updateItem(choice, empty);
        setText(empty || choice == null ? null : choice.label());
        setTooltip(
            empty || choice == null || choice.transition() == null
                ? null
                : new Tooltip(transitionDetails(choice.transition())));
      }
    };
  }

  private String transitionLabel(Workflow.TransitionSchema transition) {
    var target = workflow.getStates().get(transition.getTargetState());
    var targetName =
        target == null || target.getDescription() == null || target.getDescription().isBlank()
            ? transition.getTargetState()
            : target.getDescription();
    var action = transition.getDescription();
    return action == null || action.isBlank() ? targetName : targetName + " — " + action;
  }

  private String transitionDetails(Workflow.TransitionSchema transition) {
    return "Next stage: " + transitionLabel(transition) + "\nTransition: " + transition.getId();
  }

  private void deleteStage() {
    try {
      service.deleteFlowState(flow.getId(), selectedState.getId(), scope);
      reload(null);
    } catch (Throwable error) {
      fail(error);
    }
  }

  private void updateStage() {
    try {
      clearError();
      var update = copyState(selectedState);
      update.setMetadata(selectedEditor.metadata().get());
      service.updateFlowState(flow.getId(), selectedState.getId(), update, scope);
      reload(selectedState.getId());
    } catch (Throwable error) {
      fail(error);
    }
  }

  private void confirm(Workflow.TransitionSchema transition) {
    if (submitting || selectedEditor == null || (!canEdit(selectedState)
        && !canReviewTransition(flow, workflow, selectedState, transition, WorkflowParticipant.from(scope)))) return;
    try {
      clearError();
      // showAndWait runs a nested FX event loop: guard before it can dispatch uploads or another action.
      submitting = true;
      updateActions();
      validateRequiredAttachments();
      var proposal = selectedEditor.content() instanceof ProposalStageEditor editor ? editor : null;
      var command = proposal == null ? null : proposal.command(transition);
      var initialSubmission = provisional;
      var flowId = flow.getId();
      var workflowId = workflow.getId();
      var expectedRevision = flow.getRevision();
      var publicRead = flow.isPublicRead();
      var update = copyState(selectedState);
      update.setMetadata(Metadata.create(selectedEditor.metadata().get()));
      var uploads = pendingAttachments.getOrDefault(selectedState.getId(), List.of()).stream()
          .map(WorkflowEditor::copyUpload).toList();
      var request = Flow.TransitionRequest.create();
      request.setSourceStateId(update.getId());
      request.setTransitionId(transition.getId());
      request.setProposalReview(command);
      request.setExpectedRevision(expectedRevision + (command == null ? 1 : 0));
      // The backend assigns a returned editing stage to the author. A reviewer must not override it.
      if (ProposalStageEditor.operation(transition) != org.integratedmodelling.klab.api.services.resources.workflow.ProposalReview.Operation.REQUEST_CHANGES) {
        var target = Flow.State.create();
        target.setOwner(WorkflowParticipant.from(scope).getIdentity());
        request.setTargetState(target);
      }
      if (command != null && initialSubmission) {
        var proposals = uploads.stream().filter(a -> "application/vnd.klab.proposal+yaml".equals(a.getMediaType())).toList();
        var ontologies = uploads.stream().filter(a -> "application/vnd.klab.ontology".equals(a.getMediaType())).toList();
        if (proposals.size() != 1 || ontologies.size() > 1)
          throw new IllegalStateException("Initial submission requires one proposal and at most one ontology upload");
        var bytes = proposals.getFirst().getContent();
        var ontology = ontologies.isEmpty() ? null : new org.integratedmodelling.klab.api.services.resources.workflow.ProposalReview.Artifact(
            "pending:" + ProposalCandidateReader.digest(ontologies.getFirst().getContent()), ProposalCandidateReader.digest(ontologies.getFirst().getContent()));
        var bound = ProposalCandidateReader.read(bytes, "pending:" + ProposalCandidateReader.digest(bytes), ontology);
        if (!bound.equals(command.candidate()))
          throw new IllegalStateException("Candidate fields do not match the pending immutable bytes. Restore the uploaded binding before submitting.");
      }
      if (proposal != null) {
        var confirmation = new Alert(Alert.AlertType.CONFIRMATION);
        confirmation.setTitle("Confirm exact proposal review");
        confirmation.setHeaderText("Record this action against this immutable candidate?");
        var detail = new TextArea(proposal.model().confirmation(ProposalStageEditor.operation(transition)));
        detail.setEditable(false); detail.setWrapText(true); detail.setPrefSize(680, 380);
        confirmation.getDialogPane().setContent(detail);
        if (getScene() != null) confirmation.initOwner(getScene().getWindow());
        if (confirmation.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) return;
      }
      if (initialSubmission) {
        if (command != null) {
          // The server derives the same candidate from the exact initial uploaded bytes.
          // Temporary client IDs never cross the persistence boundary.
          request.setProposalReview(new org.integratedmodelling.klab.api.services.resources.workflow.ProposalReview.Command(
              command.version(), null, command.rationale(), command.dossier()));
        }
        request.setExpectedRevision(-1);
        var initialization = Flow.InitializationRequest.create();
        initialization.setInitialState(update);
        initialization.setAttachments(uploads);
        initialization.setTransition(request);
        initialization.setPublicRead(publicRead);
        var initializedFlow = service.initializeFlow(workflowId, initialization, scope);
        if (initializedFlow == null)
          throw new IllegalStateException(
              "The Resources service returned no Flow after initialization");
        flow = initializedFlow;
        provisional = false;
        pendingAttachments.clear();
        if (initialized != null) initialized.accept(flow);
      } else {
        // Proposal commands are atomic and bound to the revision the reviewer actually saw.
        // Never advance the revision with a metadata write before an exact-candidate decision.
        if (command == null) service.updateFlowState(flowId, update.getId(), update, scope);
        var transitioned = service.transitionFlow(flowId, request, scope);
        if (transitioned == null) throw new IllegalStateException("The Resources service returned no flow; reopen to reconcile before retrying");
        flow = transitioned;
      }
      if (flow == null) throw new IllegalStateException("The Resources service returned no flow; reload to reconcile before retrying");
      proposalDrafts.clear();
      refresh(selectInitialState());
    } catch (Throwable e) {
      fail(e);
    } finally {
      submitting = false;
      updateActions();
    }
  }

  private void inspectProposalAttachment(Flow.Attachment attachment) {
    var dialog = new Dialog<Void>();
    dialog.setTitle("Immutable source: " + attachment.getFileName());
    dialog.setHeaderText("Attachment " + attachment.getId() + "\nSHA-256 " + attachment.getChecksum());
    dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
    dialog.setResizable(true);
    if (getScene() != null) dialog.initOwner(getScene().getWindow());
    var content = new BorderPane(new ProgressIndicator()); content.setPrefSize(900, 600);
    dialog.getDialogPane().setContent(content);
    var flowId = flow.getId();
    var task = new Task<String>() {
      @Override protected String call() throws Exception {
        var bytes = service.getFlowAttachment(flowId, attachment.getId(), scope);
        if (bytes == null || !Objects.equals(attachment.getChecksum(), ProposalCandidateReader.digest(bytes)))
          throw new IllegalStateException("Source bytes are missing or do not match the immutable checksum");
        return java.nio.charset.StandardCharsets.UTF_8.newDecoder().decode(java.nio.ByteBuffer.wrap(bytes)).toString();
      }
    };
    task.setOnSucceeded(event -> {
      if (!dialog.isShowing()) return;
      var text = new TextArea(task.getValue()); text.setEditable(false); text.setWrapText(false);
      content.setCenter(text);
    });
    task.setOnFailed(event -> { var error = new Label(errorMessage(task.getException())); error.setWrapText(true); content.setCenter(error); });
    dialog.setOnHidden(event -> task.cancel(true));
    var worker = new Thread(task, "proposal-source-inspection"); worker.setDaemon(true); worker.start();
    dialog.showAndWait();
  }

  private boolean discardProposalDraft(String message) {
    if (!(selectedEditor.content() instanceof ProposalStageEditor editor) || !editor.model().dirty()) return true;
    var dialog = new Alert(Alert.AlertType.CONFIRMATION, message, ButtonType.OK, ButtonType.CANCEL);
    if (getScene() != null) dialog.initOwner(getScene().getWindow());
    return dialog.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK;
  }

  /** Called by tab hosts before discarding local stage drafts. */
  public boolean requestClose() {
    if (submitting) return false;
    boolean dirty = proposalDrafts.values().stream()
        .anyMatch(stage -> stage.content() instanceof ProposalStageEditor view && view.model().dirty());
    if (!dirty) return true;
    var dialog = new Alert(Alert.AlertType.CONFIRMATION,
        "Discard unsaved proposal review edits and notes in this workflow?", ButtonType.OK, ButtonType.CANCEL);
    if (getScene() != null) dialog.initOwner(getScene().getWindow());
    return dialog.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK;
  }

  private void validateRequiredAttachments() {
    var schema = workflow.getStates().get(selectedState.getSchemaId());
    if (schema == null) return;
    for (var rule : schema.getAttachments()) {
      boolean stored =
          selectedState.getAttachments().stream()
              .anyMatch(attachment -> Objects.equals(rule.getType(), attachment.getType()));
      boolean pending =
          pendingAttachments.getOrDefault(selectedState.getId(), List.of()).stream()
              .anyMatch(attachment -> Objects.equals(rule.getType(), attachment.getType()));
      if (rule.isRequired() && !stored && !pending)
        throw new IllegalStateException(
            "Add the required '" + rule.getType() + "' attachment before continuing");
    }
  }

  private static Flow.AttachmentUpload copyUpload(Flow.AttachmentUpload upload) {
    var copy = Flow.AttachmentUpload.create();
    copy.setType(upload.getType()); copy.setFileName(upload.getFileName());
    copy.setMediaType(upload.getMediaType()); copy.setAssetType(upload.getAssetType());
    copy.setContent(upload.getContent() == null ? null : upload.getContent().clone());
    return copy;
  }

  private Flow.State copyState(Flow.State state) {
    var copy = Flow.State.create();
    copy.setId(state.getId());
    copy.setFlowId(state.getFlowId());
    copy.setSchemaId(state.getSchemaId());
    copy.setTitle(state.getTitle());
    copy.setDescription(state.getDescription());
    copy.setAssetUrn(state.getAssetUrn());
    copy.setAssetType(state.getAssetType());
    copy.setPermissionsOwnerUrn(state.getPermissionsOwnerUrn());
    copy.setStatus(state.getStatus());
    copy.setOwner(state.getOwner());
    copy.setAssignees(new java.util.LinkedHashSet<>(state.getAssignees()));
    copy.setMetadata(Metadata.create(state.getMetadata()));
    return copy;
  }

  private boolean canEdit(Flow.State state) {
    if (flow.isPublicRead()
        || flow.getStatus() == Flow.Status.CLOSED
        || state.getStatus() == Flow.StateStatus.CLOSED) return false;
    var participant = WorkflowParticipant.from(scope);
    return workflow.canAccess(workflow.getStates().get(state.getSchemaId()), participant)
        && (participant.getRoles().contains(WorkflowRole.ADMIN)
            || Objects.equals(state.getOwner(), participant.getIdentity())
            || (participant.getRoles().contains(WorkflowRole.EDITOR)
                && state.getAssignees().contains(participant.getIdentity())));
  }

  private boolean isAdmin() {
    return WorkflowParticipant.from(scope).getRoles().contains(WorkflowRole.ADMIN);
  }

  private boolean isProposalWorkflow() {
    return workflow.getTransitions().values().stream()
        .anyMatch(transition -> transition.getMetadata().containsKey("proposalReviewOperation"));
  }

  /** Mirrors the service's narrow assigned-reviewer transition allowance, not editor privileges. */
  static boolean canReviewTransition(Flow flow, Workflow workflow, Flow.State state,
      Workflow.TransitionSchema transition, WorkflowParticipant participant) {
    var schema = workflow.getStates().get(state.getSchemaId());
    var operation = ProposalStageEditor.operation(transition);
    return !flow.isPublicRead() && flow.getStatus() != Flow.Status.CLOSED
        && state.getStatus() == Flow.StateStatus.OPEN && flow.getCurrentStateIds().contains(state.getId())
        && operation != null && operation != org.integratedmodelling.klab.api.services.resources.workflow.ProposalReview.Operation.SUBMIT
        && schema != null && schema.getContributorRoles().contains(WorkflowRole.REVIEWER)
        && participant.getRoles().contains(WorkflowRole.REVIEWER)
        && state.getAssignees().contains(participant.getIdentity())
        && transition.getRoles().contains(WorkflowRole.REVIEWER)
        && workflow.canAccess(schema, participant)
        && workflow.admittedTransitions(state, participant).contains(transition);
  }

  private void updateActions() {
    if (actionBar == null || selectedEditor == null) return;
    boolean valid = selectedEditor.valid().getAsBoolean();
    for (var node : actionBar.getChildren()) {
      if (Boolean.TRUE.equals(node.getProperties().get("workflow-update"))) node.setDisable(!valid);
    }
    if (submitButton != null) {
      var choice = transitionSelector == null ? null : transitionSelector.getValue();
      var proposalProblem = selectedEditor.content() instanceof ProposalStageEditor proposal
          && choice != null && choice.transition() != null
          ? proposal.model().problem(ProposalStageEditor.operation(choice.transition())) : null;
      submitButton.setDisable(submitting || !valid || choice == null || choice.transition() == null || proposalProblem != null);
      submitButton.setTooltip(new Tooltip(proposalProblem == null ? "Submit the selected workflow transition" : proposalProblem));
      if (selectedEditor.content() instanceof ProposalStageEditor proposal) proposal.showProblem(proposalProblem);
    }
  }

  private void reload(String stateId) {
    flow = service.getFlow(flow.getId(), scope);
    refresh(stateId == null ? selectInitialState() : flow.getStates().get(stateId));
  }

  private void mutate(Supplier<Flow> mutation, String stateId) {
    try {
      flow = mutation.get();
      refresh(stateId == null ? selectInitialState() : flow.getStates().get(stateId));
    } catch (Throwable e) {
      fail(e);
    }
  }

  private void fail(Throwable error) {
    var message = errorMessage(error) + (selectedEditor != null && selectedEditor.content() instanceof ProposalStageEditor
        ? " Your proposal draft is retained. For a stale revision or conflicting edit, copy your notes, reopen the workflow and review the latest candidate before submitting again." : "");
    Platform.runLater(
        () -> {
          errorMessage.setText(message);
          if (KlabIDEController.instance() != null) KlabIDEController.instance().handleNotification(Notification.error(message));
        });
  }

  private void clearError() {
    errorMessage.setText("");
  }

  private static String errorMessage(Throwable error) {
    Throwable cause = error;
    while (cause.getCause() != null && cause.getCause() != cause) cause = cause.getCause();
    var detail = cause.getMessage();
    return detail == null || detail.isBlank()
        ? "The workflow action could not be completed. Correct the stage and try again."
        : "The workflow action could not be completed: " + detail;
  }

  @Override
  public void close() {
    if (diagramTask != null) diagramTask.cancel(true);
    Runnable closeDiagram = () -> { if (workflowDiagram != null) workflowDiagram.close(); };
    if (Platform.isFxApplicationThread()) closeDiagram.run();
    else Platform.runLater(closeDiagram);
    // UploadBox uses daemon workers; detaching the editor is sufficient until it gains lifecycle
    // API.
  }
}
