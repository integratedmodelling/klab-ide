package org.integratedmodelling.klab.ide.components;

import static org.junit.jupiter.api.Assertions.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import javafx.application.Platform;
import javafx.embed.swing.SwingFXUtils;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.StackPane;
import javax.imageio.ImageIO;
import org.integratedmodelling.klab.api.services.resources.workflow.Flow;
import org.integratedmodelling.klab.api.services.resources.workflow.ProposalReview.*;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class ProposalStageEditorTest {
  @BeforeAll static void start() throws Exception {
    try { Platform.startup(() -> { Platform.setImplicitExit(false); javafx.application.Application.setUserAgentStylesheet(new atlantafx.base.theme.PrimerLight().getUserAgentStylesheet()); }); }
    catch (IllegalStateException alreadyStarted) { /* Shared JavaFX test process. */ }
  }
  private static void fx(Runnable action) throws Exception {
    var task = new FutureTask<Void>(() -> { action.run(); return null; });
    Platform.runLater(task); task.get(30, TimeUnit.SECONDS);
  }
  static BootstrapDossier hydrology() {
    // Adapted from the source-first hydrology research dossier; claims remain unvalidated.
    return new BootstrapDossier(
        List.of(new Evidence("watersheds", "https://www.usgs.gov/water-science-school/science/watersheds-and-drainage-basins",
            "Definition; Not all precipitation flows out", null)),
        List.of(new Concept("hydrology-SurfaceCatchment", ConceptKind.SUBJECT, "hydrology:SurfaceCatchment", null,
            List.of(), List.of("watersheds"), List.of(), List.of(), List.of(), List.of(), null, null, List.of())),
        List.of(new Question("hydrology-Q01", "Which land drains to the bridge outlet, and is it the same area feeding the well?",
            "Surface-drainage delineation; do not assume the groundwater contributing area is identical", List.of("watersheds"),
            List.of("hydrology-SurfaceCatchment"), List.of("hydrology:SurfaceCatchment"),
            List.of("Groundwater contributing area asserted identical"), List.of("Subsurface contributing-area distinction absent; two resolutions required"))),
        List.of(), List.of("Question mapping is blocked despite a reported parse pass"),
        List.of("One-question excerpt, not a complete domain dossier. Same-agent generation is not independent reviewer coverage."));
  }
  @Test void sharedContractRoundTripsWithoutAnIdeDto() throws Exception {
    var mapper = new ObjectMapper();
    var value = hydrology();
    assertEquals(value, mapper.readValue(mapper.writeValueAsBytes(value), BootstrapDossier.class));
  }
  @Test void nativeReviewKeepsEvidenceReadOnlyAndRationaleEditable() throws Exception {
    fx(() -> {
      var data = new StageData(1, ProposalReviewModelTest.candidate("r1", null), Status.IN_REVIEW, "author", "Review requested",
          List.of(new Check(CheckKind.PARSER, CheckStatus.PASS, List.of("Syntax only")),
              new Check(CheckKind.REASONER, CheckStatus.BLOCKED, List.of("Imports need verification"))), hydrology());
      var model = new ProposalReviewModel(data, false, false);
      var view = new ProposalStageEditor(model, Flow.State.create(), () -> {});
      var root = new StackPane(view); new Scene(root, 1180, 840); root.applyCss(); root.layout();
      var tabs = (TabPane) view.getChildren().stream().filter(TabPane.class::isInstance).findFirst().orElseThrow();
      assertEquals(7, tabs.getTabs().size());
      view.focusReview(new org.integratedmodelling.klabeditor.MonacoEditorView.ReviewMarkerClick("marker", 3, "hydrology-SurfaceCatchment", "reviewer"));
      assertEquals(2, tabs.getSelectionModel().getSelectedIndex());
      tabs.getSelectionModel().select(3); root.applyCss(); root.layout();
      assertTrue(view.lookupAll(".button").stream().filter(n -> n instanceof Button b && b.getText().startsWith("Add ")).allMatch(javafx.scene.Node::isDisabled));
      try {
        Files.createDirectories(Path.of("target/proposal-ui"));
        ImageIO.write(SwingFXUtils.fromFXImage(root.snapshot(null, null), null), "png", Path.of("target/proposal-ui/questions.png").toFile());
        tabs.getSelectionModel().select(5); root.applyCss(); root.layout();
        ImageIO.write(SwingFXUtils.fromFXImage(root.snapshot(null, null), null), "png", Path.of("target/proposal-ui/checks.png").toFile());
      } catch (Exception e) { throw new RuntimeException(e); }
      model.rationale("Please resolve the surface/subsurface distinction");
      assertEquals(Operation.REQUEST_CHANGES.name(), model.confirmation(Operation.REQUEST_CHANGES).split("\\R")[0]);
      view.readOnly(true);
      assertThrows(IllegalStateException.class, () -> model.command(Operation.REQUEST_CHANGES));
    });
  }
  @Test void missingAndFutureVersionRenderWithoutEnablingMutations() throws Exception {
    fx(() -> {
      var missing = new ProposalStageEditor(new ProposalReviewModel(null, false, false), Flow.State.create(), () -> {});
      assertNotNull(missing.model().problem(Operation.ACCEPT));
      var unknown = new ProposalStageEditor(new ProposalReviewModel(ProposalReviewModelTest.stage(42), false, false), Flow.State.create(), () -> {});
      assertFalse(unknown.model().editable());
    });
  }
}
