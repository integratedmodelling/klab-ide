package org.integratedmodelling.klab.ide.components;

import java.net.URI;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import org.integratedmodelling.klab.api.services.resources.workflow.*;

/** Honest native entry point to the server's stage-specific review UI. */
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
    var notice = new Label(instructions + " Use the Resources review UI for stage-specific controls. "
        + "This native view does not edit review decisions or cycle artifacts.");
    notice.setWrapText(true);
    var content = new VBox(10, notice);
    if (flow.getRevision() == 0 && flow.getHistory().isEmpty()) {
      var help = new Label("Create the workflow below first. This saves bootstrap without starting review.");
      help.setWrapText(true); content.getChildren().add(help);
    } else {
      var open = new Button("Open " + state.getSchemaId() + " review UI");
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
    return new WorkflowEditor.StageEditor(content, () -> true, state::getMetadata, ignored -> {});
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
