package org.integratedmodelling.klab.ide.components;

import java.net.URI;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.TextArea;
import javafx.scene.control.ScrollPane;
import javafx.scene.Node;
import javafx.scene.layout.HBox;
import javafx.geometry.Insets;
import javafx.scene.layout.VBox;
import org.integratedmodelling.klab.api.services.resources.workflow.*;

/** Native editorial preparation, with server-owned review decisions opened in the review UI. */
public final class ReviewStageEditor {
  private ReviewStageEditor() {}

  static boolean supports(Workflow workflow) {
    return Boolean.TRUE.equals(workflow.getMetadata().get("reviewStages"));
  }

  public static WorkflowEditor.StageEditor create(
      Workflow workflow, Flow flow, Flow.State state, URL serviceUrl) {
    if (!supports(workflow)) return null;
    String instructions = switch (state.getSchemaId()) {
      case "bootstrap" -> "Only the creating editor can prepare the initial review cycle.";
      case "peer-review" -> "Assigned peer reviewers inspect this cycle and record their review.";
      case "community-review" -> "Community contributors inspect this cycle and contribute feedback.";
      case "coalescing" -> "Editors consolidate feedback, then terminate or open another review cycle.";
      default -> "Inspect the saved review cycle and its history.";
    };
    boolean editorial = "bootstrap".equals(state.getSchemaId()) || "coalescing".equals(state.getSchemaId());
    boolean draft = flow.getRevision() == 0 && flow.getHistory().isEmpty();
    var notice = new Label(instructions + (editorial
        ? " Name this stage, describe the review, and attach a proposal or reference material below."
        : " Open the review UI below to inspect the candidate and add your contribution."));
    notice.setWrapText(true);
    var content = new VBox(10, notice);
    var title = new TextField(state.getTitle());
    title.setId("review-stage-title");
    title.setPromptText("For example: Land and agriculture ontology review");
    title.setAccessibleText("Stage title");
    var description = new TextArea(state.getDescription());
    description.setId("review-stage-description");
    description.setPromptText("Scope, questions for reviewers, and the reference material to consider");
    description.setAccessibleText("Stage description");
    description.setPrefRowCount(3);
    title.textProperty().addListener((observable, old, value) -> state.setTitle(value));
    description.textProperty().addListener((observable, old, value) -> state.setDescription(value));
    if (editorial) content.getChildren().addAll(new Label("Stage title"), title,
        new Label("Description and review brief"), description);
    if (draft) {
      var help = new Label("Create private bootstrap saves these details and attachments together. "
          + "It does not start review. You can then prepare a proposal from the ontology, or continue with your uploaded proposal.");
      help.setWrapText(true); content.getChildren().add(help);
    } else {
      var help = new Label(editorial
          ? "Save stage details after editing. If you have no proposal, use Prepare review draft. "
              + "Then open the review UI to edit the proposal and comments and start the review cycle."
          : "The saved stages and review history are retained here. Review decisions are made in the review UI.");
      help.setWrapText(true); content.getChildren().add(help);
      var open = new Button("Open " + state.getSchemaId() + " review UI");
      open.setId("review-open-ui");
      var feedback = new Label(); feedback.setWrapText(true);
      try {
        var uri = reviewUri(serviceUrl, flow.getId(), state.getId());
        open.setOnAction(event -> {
          try { new ApplicationBrowser("Default browser", List.of()).open(uri); }
          catch (Exception error) { feedback.setText("Could not open the review UI: " + error.getMessage()); }
        });
      } catch (IllegalArgumentException error) {
        open.setDisable(true); feedback.setText("The Resources service has no usable review UI address.");
      }
      content.getChildren().addAll(open, feedback);
    }
    return new WorkflowEditor.StageEditor(content, () -> true, state::getMetadata, readOnly -> {
      title.setEditable(!readOnly); description.setEditable(!readOnly);
    });
  }

  /** Behavior-aware overview and actual persisted stage history, never the static INIT-only graph. */
  static Node structure(Workflow workflow, Flow flow) {
    var content = new VBox(12);
    content.setPadding(new Insets(16));
    var heading = new Label(WorkflowCatalog.label(workflow));
    heading.setStyle("-fx-font-size: 18px; -fx-font-weight: bold;");
    var explanation = new Label("Review stages are created by the workflow behavior. Peer and community rounds "
        + "can repeat within a cycle. Coalescing may open another cycle or terminate; termination does not publish or accept the proposal.");
    explanation.setWrapText(true);
    var route = new HBox(10);
    for (String phase : List.of("Bootstrap", "Peer / community review", "Coalescing", "Terminated")) {
      if (!route.getChildren().isEmpty()) route.getChildren().add(new Label("→"));
      var box = new Label(phase);
      box.setPadding(new Insets(12));
      box.setStyle("-fx-border-color: -color-border-muted; -fx-background-color: -color-bg-subtle; -fx-background-radius: 6;");
      route.getChildren().add(box);
    }
    content.getChildren().addAll(heading, explanation, route,
        new Label("Coalescing → another review cycle, or → termination"), new Label("Stages in this flow"));
    flow.getStates().values().stream().sorted(java.util.Comparator.comparing(Flow.State::getCreatedAt,
        java.util.Comparator.nullsLast(java.util.Comparator.naturalOrder()))).forEach(state -> {
      var row = new Label((flow.getCurrentStateIds().contains(state.getId()) ? "● " : "○ ")
          + state.getSchemaId() + " · " + state.getStatus() + " · "
          + (state.getTitle() == null || state.getTitle().isBlank() ? state.getId() : state.getTitle()));
      row.setWrapText(true); content.getChildren().add(row);
    });
    if (!flow.getHistory().isEmpty()) {
      content.getChildren().add(new Label("Recorded transitions"));
      for (var event : flow.getHistory()) {
        var source = flow.getStates().get(event.getSourceStateId());
        var target = flow.getStates().get(event.getTargetStateId());
        var row = new Label((source == null ? "INIT" : source.getSchemaId()) + " → "
            + (target == null ? event.getTargetStateId() : target.getSchemaId()) + " (" + event.getTransitionId() + ")");
        row.setWrapText(true); content.getChildren().add(row);
      }
    }
    var scroll = new ScrollPane(content); scroll.setFitToWidth(true);
    return scroll;
  }

  static URI reviewUri(URL serviceUrl, String flowId, String stateId) {
    if (serviceUrl == null) throw new IllegalArgumentException("Missing service URL");
    var base = URI.create(serviceUrl.toString());
    if (!("https".equalsIgnoreCase(base.getScheme()) || "http".equalsIgnoreCase(base.getScheme()))
        || base.getHost() == null || base.getRawUserInfo() != null
        || base.getRawQuery() != null || base.getRawFragment() != null)
      throw new IllegalArgumentException("Invalid service URL");
    return URI.create(base.toString().replaceAll("/+$", "") + "/ui/review?flow="
        + URLEncoder.encode(flowId, StandardCharsets.UTF_8) + "&stage="
        + URLEncoder.encode(stateId, StandardCharsets.UTF_8));
  }
}
