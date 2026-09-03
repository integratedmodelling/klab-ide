package org.integratedmodelling.klab.ide.components;

import atlantafx.base.theme.Styles;
import java.io.File;
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
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Separator;
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

  /** Selects a specialized editor for a state schema, or returns {@code null} for the default UI. */
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
  private final ListView<Flow.State> stateList = new ListView<>();
  private final VBox stageArea = new VBox(12);
  private final Label status = new Label();
  private Flow flow;
  private Flow.State selectedState;
  private StageEditor selectedEditor;
  private HBox actionBar;
  private UploadBox uploadBox;

  public WorkflowEditor(
      ResourcesService service,
      UserScope scope,
      Workflow workflow,
      Flow flow,
      StageEditorProvider stageEditors) {
    this.service = Objects.requireNonNull(service);
    this.scope = Objects.requireNonNull(scope);
    this.workflow = Objects.requireNonNull(workflow);
    this.flow = Objects.requireNonNull(flow);
    this.stageEditors = stageEditors;
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
    var spacer = new HBox();
    HBox.setHgrow(spacer, Priority.ALWAYS);
    var box = new HBox(12, new VBox(2, title, asset), spacer, status);
    box.setAlignment(Pos.CENTER_LEFT);
    box.setPadding(new Insets(0, 0, 10, 0));
    return new VBox(box, new Separator());
  }

  private Node stageBrowser() {
    stateList.setPrefWidth(230);
    stateList.setCellFactory(
        ignored ->
            new ListCell<>() {
              @Override
              protected void updateItem(Flow.State state, boolean empty) {
                super.updateItem(state, empty);
                if (empty || state == null) {
                  setText(null);
                  setGraphic(null);
                } else {
                  var schema = workflow.getStates().get(state.getSchemaId());
                  var name =
                      state.getTitle() != null && !state.getTitle().isBlank()
                          ? state.getTitle()
                          : schema == null ? state.getSchemaId() : schema.getDescription();
                  setText(name + "\n" + state.getStatus().name().toLowerCase());
                }
              }
            });
    stateList.getSelectionModel().selectedItemProperty().addListener((o, old, state) -> show(state));
    var box = new VBox(6, new Label("Stages"), stateList);
    box.setPadding(new Insets(10, 12, 0, 0));
    VBox.setVgrow(stateList, Priority.ALWAYS);
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
                .thenComparing(Flow.State::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder())))
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
    status.setText(
        flow.getStatus()
            + "  •  started "
            + (flow.getCreatedAt() == null ? "unknown" : DATE.format(flow.getCreatedAt()))
            + "  •  revision "
            + flow.getRevision()
            + (flow.isPublicRead() ? "  •  public read-only" : ""));
    var states = new ArrayList<>(flow.getStates().values());
    states.sort(
        Comparator.comparing(
            Flow.State::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder())));
    stateList.getItems().setAll(states);
    var selected =
        selection == null
            ? null
            : states.stream().filter(s -> Objects.equals(s.getId(), selection.getId())).findFirst().orElse(null);
    if (selected == null && !states.isEmpty()) selected = states.getFirst();
    stateList.getSelectionModel().select(selected);
    show(selected);
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
    stageArea.getChildren().addAll(heading, instructions);

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
    var metadata = new Label(state.getMetadata().toString());
    metadata.setWrapText(true);
    return new StageEditor(metadata, () -> true, Map::of, ignored -> {});
  }

  private Node attachments(Workflow.StateSchema schema, boolean readOnly) {
    var list = new VBox(4, new Label("Attachments"));
    for (var attachment : selectedState.getAttachments()) {
      list.getChildren().add(new Label(attachment.getFileName() + " (" + attachment.getType() + ")"));
    }
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
              "Drop an admitted attachment here",
              file -> upload(file, rules.getValue()),
              (message, error) -> fail(error == null ? new IOException(message) : error));
      uploadBox.setPrefHeight(150);
      list.getChildren().addAll(rules, uploadBox);
    }
    return list;
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

  private void upload(File file, Workflow.AttachmentRule rule) {
    if (file == null || rule == null) return;
    try {
      var upload = Flow.AttachmentUpload.create();
      upload.setType(rule.getType());
      upload.setFileName(file.getName());
      upload.setMediaType(
          rule.getMediaType() == null ? Files.probeContentType(file.toPath()) : rule.getMediaType());
      upload.setAssetType(rule.getAssetType());
      upload.setContent(Files.readAllBytes(file.toPath()));
      service.addFlowAttachment(flow.getId(), selectedState.getId(), upload, scope);
      reload(selectedState.getId());
    } catch (Throwable e) {
      fail(e);
    }
  }

  private HBox actions(Workflow.StateSchema schema, boolean readOnly) {
    var bar = new HBox(8);
    bar.setAlignment(Pos.CENTER_RIGHT);
    if (flow.getStatus() == Flow.Status.CLOSED && isAdmin()) {
      var reopen = new Button("Reopen flow");
      reopen.getStyleClass().add(Styles.ACCENT);
      reopen.setOnAction(event -> mutate(() -> service.reopenFlow(flow.getId(), scope), null));
      bar.getChildren().add(reopen);
      return bar;
    }
    if (readOnly) return bar;

    var cancel = new Button("Cancel changes");
    cancel.setOnAction(event -> show(selectedState));
    var delete = new Button("Delete stage");
    delete.getStyleClass().add(Styles.DANGER);
    delete.setDisable(
        flow.getCurrentStateIds().contains(selectedState.getId())
            || flow.getHistory().stream()
                .anyMatch(
                    transaction ->
                        Objects.equals(selectedState.getId(), transaction.getSourceStateId())
                            || Objects.equals(
                                selectedState.getId(), transaction.getTargetStateId())));
    delete.setOnAction(
        event -> {
          try {
            service.deleteFlowState(flow.getId(), selectedState.getId(), scope);
            reload(null);
          } catch (Throwable e) {
            fail(e);
          }
        });
    bar.getChildren().addAll(cancel, delete);
    for (var transition :
        workflow.admittedTransitions(flow, selectedState.getId(), scope)) {
      var confirm =
          new Button(
              transition.getDescription() == null ? transition.getId() : transition.getDescription());
      confirm.getProperties().put("workflow-confirm", Boolean.TRUE);
      confirm.getStyleClass().add(Styles.ACCENT);
      confirm.setOnAction(event -> confirm(transition));
      bar.getChildren().add(confirm);
    }
    return bar;
  }

  private void confirm(Workflow.TransitionSchema transition) {
    try {
      var update = copyState(selectedState);
      update.setMetadata(selectedEditor.metadata().get());
      service.updateFlowState(flow.getId(), selectedState.getId(), update, scope);
      var request = Flow.TransitionRequest.create();
      request.setSourceStateId(selectedState.getId());
      request.setTransitionId(transition.getId());
      request.setExpectedRevision(flow.getRevision() + 1);
      var target = Flow.State.create();
      target.setOwner(WorkflowParticipant.from(scope).getIdentity());
      request.setTargetState(target);
      flow = service.transitionFlow(flow.getId(), request, scope);
      refresh(selectInitialState());
    } catch (Throwable e) {
      fail(e);
    }
  }

  private Flow.State copyState(Flow.State state) {
    var copy = Flow.State.create();
    copy.setId(state.getId());
    copy.setFlowId(state.getFlowId());
    copy.setSchemaId(state.getSchemaId());
    copy.setTitle(state.getTitle());
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
      if (Boolean.TRUE.equals(node.getProperties().get("workflow-confirm"))) node.setDisable(!valid);
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
    Platform.runLater(
        () -> KlabIDEController.instance().handleNotification(Notification.error(error)));
  }

  @Override
  public void close() {
    // UploadBox uses daemon workers; detaching the editor is sufficient until it gains lifecycle API.
  }
}
