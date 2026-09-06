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

  private static final DateTimeFormatter DATE =
      DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault());

  private record TransitionChoice(Workflow.TransitionSchema transition, String label) {}

  /** A specialized stage UI plus the callbacks needed by the generic action bar. */
  public record StageEditor(
      Node content,
      BooleanSupplier valid,
      Supplier<Map<String, Object>> metadata,
      Consumer<Boolean> readOnly) {

    public StageEditor {
      Objects.requireNonNull(content, "Stage editor content");
      valid = valid == null ? () -> true : valid;
      metadata = metadata == null ? Map::of : metadata;
      readOnly = readOnly == null ? ignored -> {} : readOnly;
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
  private final CarouselBox stageCarousel = new CarouselBox(Orientation.VERTICAL);
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
    setTop(header());
    setLeft(stageBrowser());
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
    var box = new HBox(12, titleArea, spacer, metadata);
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
    header.getChildren().addAll(box, new Separator());
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
    stageCarousel.setPrefWidth(230);
    stageCarousel.setMaxWidth(Double.MAX_VALUE);
    stageCarousel.setSelectionListener(card -> show(stageCards.get(card)));
    var box = new VBox(6, new Label("Stages"), stageCarousel);
    box.setPadding(new Insets(10, 12, 0, 0));
    VBox.setVgrow(stageCarousel, Priority.ALWAYS);
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

    boolean readOnly = !canEdit(state);
    selectedEditor =
        stageEditors == null
            ? null
            : stageEditors.create(workflow, flow, state, schema, readOnly, this::updateActions);
    if (selectedEditor == null) selectedEditor = defaultEditor(state);
    selectedEditor.readOnly().accept(readOnly);
    stageArea.getChildren().add(selectedEditor.content());

    if (!schema.getAttachments().isEmpty()) {
      stageArea.getChildren().add(attachments(schema, readOnly));
    }
    actionBar = actions(schema, readOnly);
    stageArea.getChildren().add(actionBar);
    updateActions();
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
      var rules = new ComboBox<Workflow.AttachmentRule>();
      rules.getItems().setAll(schema.getAttachments());
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
      attachmentEntries
          .getChildren()
          .add(new Label(attachment.getFileName() + " (" + attachment.getType() + ")"));
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
    try {
      var upload = Flow.AttachmentUpload.create();
      upload.setType(rule.getType());
      upload.setFileName(file.getName());
      var detectedMediaType = Files.probeContentType(file.toPath());
      upload.setMediaType(
          detectedMediaType != null
              ? detectedMediaType
              : rule.getMediaType() != null && !rule.getMediaType().contains("*")
                  ? rule.getMediaType()
                  : "application/octet-stream");
      upload.setAssetType(
          rule.getAssetType() == null ? selectedState.getAssetType() : rule.getAssetType());
      upload.setContent(Files.readAllBytes(file.toPath()));
      clearError();
      if (provisional) {
        pendingAttachments
            .computeIfAbsent(selectedState.getId(), ignored -> new ArrayList<>())
            .add(upload);
        refreshAttachmentEntries();
      } else {
        service.addFlowAttachment(flow.getId(), selectedState.getId(), upload, scope);
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
    if (flow.getStatus() == Flow.Status.CLOSED && isAdmin()) {
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

    if (provisional) {
      var cancelWorkflow = new Button("Cancel workflow");
      cancelWorkflow.getStyleClass().add(Styles.DANGER);
      cancelWorkflow.setOnAction(
          event -> {
            pendingAttachments.clear();
            if (cancelJob != null) cancelJob.run();
          });
      buttons.getChildren().add(cancelWorkflow);
    }

    var cancel = new Button("Reset");
    cancel.setOnAction(event -> show(selectedState));
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
    buttons.getChildren().addAll(cancel, delete);
    if (!provisional) {
      var update = new Button("Update");
      update.setOnAction(event -> updateStage());
      update.getProperties().put("workflow-update", Boolean.TRUE);
      buttons.getChildren().add(update);
    }
    var transitions = workflow.admittedTransitions(flow, selectedState.getId(), scope);
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
    try {
      clearError();
      validateRequiredAttachments();
      var update = copyState(selectedState);
      update.setMetadata(selectedEditor.metadata().get());
      var request = Flow.TransitionRequest.create();
      request.setSourceStateId(selectedState.getId());
      request.setTransitionId(transition.getId());
      request.setExpectedRevision(flow.getRevision() + 1);
      var target = Flow.State.create();
      target.setOwner(WorkflowParticipant.from(scope).getIdentity());
      request.setTargetState(target);
      if (provisional) {
        request.setExpectedRevision(-1);
        var initialization = Flow.InitializationRequest.create();
        initialization.setInitialState(update);
        initialization.setAttachments(
            new ArrayList<>(pendingAttachments.getOrDefault(selectedState.getId(), List.of())));
        initialization.setTransition(request);
        initialization.setPublicRead(flow.isPublicRead());
        var initializedFlow = service.initializeFlow(workflow.getId(), initialization, scope);
        if (initializedFlow == null)
          throw new IllegalStateException(
              "The Resources service returned no Flow after initialization");
        flow = initializedFlow;
        provisional = false;
        pendingAttachments.clear();
        if (initialized != null) initialized.accept(flow);
      } else {
        service.updateFlowState(flow.getId(), selectedState.getId(), update, scope);
        request.setExpectedRevision(flow.getRevision() + 1);
        flow = service.transitionFlow(flow.getId(), request, scope);
      }
      refresh(selectInitialState());
    } catch (Throwable e) {
      fail(e);
    }
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
    copy.setAssignees(state.getAssignees());
    copy.setMetadata(state.getMetadata());
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

  private void updateActions() {
    if (actionBar == null || selectedEditor == null) return;
    boolean valid = selectedEditor.valid().getAsBoolean();
    for (var node : actionBar.getChildren()) {
      if (Boolean.TRUE.equals(node.getProperties().get("workflow-update"))) node.setDisable(!valid);
    }
    if (submitButton != null) {
      var choice = transitionSelector == null ? null : transitionSelector.getValue();
      submitButton.setDisable(!valid || choice == null || choice.transition() == null);
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
    var message = errorMessage(error);
    Platform.runLater(
        () -> {
          errorMessage.setText(message);
          KlabIDEController.instance().handleNotification(Notification.error(message));
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
