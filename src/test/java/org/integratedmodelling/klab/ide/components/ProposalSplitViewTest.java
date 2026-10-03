package org.integratedmodelling.klab.ide.components;

import static org.junit.jupiter.api.Assertions.*;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import javafx.application.Platform;
import javafx.embed.swing.SwingFXUtils;
import javafx.scene.Scene;
import javafx.scene.control.SplitPane;
import javafx.scene.web.WebView;
import javafx.stage.Stage;
import javax.imageio.ImageIO;
import org.integratedmodelling.klab.api.identities.UserIdentity;
import org.integratedmodelling.klab.api.scope.*;
import org.integratedmodelling.klab.api.services.ResourcesService;
import org.integratedmodelling.klab.api.services.resources.workflow.*;
import org.integratedmodelling.klab.api.services.resources.workflow.ProposalReview.*;
import org.integratedmodelling.klab.api.services.resources.workflow.impl.WorkflowImpl;
import org.integratedmodelling.klabeditor.MonacoEditorView;
import org.junit.jupiter.api.Test;

/** Real bundled Monaco + native workflow shell, backed solely by local synthetic fixtures. */
class ProposalSplitViewTest {
  @Test void pairedEditorRendersClickableProposalGlyphAtNarrowStageWidth() throws Exception {
    try { Platform.startup(() -> Platform.setImplicitExit(false)); } catch (IllegalStateException ignored) {}
    var rendered = new CountDownLatch(1);
    var failure = new AtomicReference<Throwable>();
    var window = new AtomicReference<Stage>();
    var monacoRef = new AtomicReference<MonacoEditorView>();
    var workflowRef = new AtomicReference<WorkflowEditor>();
    var rootRef = new AtomicReference<SplitPane>();
    String source = "namespace hydrology\n\nsubject SurfaceCatchment;\n";
    Platform.runLater(() -> {
      try {
        javafx.application.Application.setUserAgentStylesheet(new atlantafx.base.theme.PrimerLight().getUserAgentStylesheet());
        var user = (UserIdentity)Proxy.newProxyInstance(UserIdentity.class.getClassLoader(), new Class<?>[]{UserIdentity.class},
            (p,m,a) -> switch(m.getName()) { case "getUsername" -> "reviewer"; case "getEmailAddress" -> "reviewer@example.test"; case "getGroups" -> Set.of(); case "isAuthenticated", "isAnonymous" -> false; default -> null; });
        var scope = (UserScope)Proxy.newProxyInstance(UserScope.class.getClassLoader(), new Class<?>[]{UserScope.class, ServiceSideScope.class},
            (p,m,a) -> switch(m.getName()) { case "getUser" -> user; case "isAuthorized" -> true; default -> null; });
        var service = (ResourcesService)Proxy.newProxyInstance(ResourcesService.class.getClassLoader(), new Class<?>[]{ResourcesService.class}, (p,m,a) -> { throw new AssertionError("No service call expected: " + m.getName()); });
        var workflow = new WorkflowImpl(); workflow.setId("proposal-review"); workflow.setName("Ontology proposal review"); workflow.setVersion("1.1");
        var schema = new WorkflowImpl.StateSchemaImpl(); schema.setId("peer-review"); schema.setDescription("Independent scientific review"); schema.setInstructions("Inspect the source and proposal together. Automatic checks do not approve this candidate."); schema.setOpen(true);
        workflow.getStates().put(schema.getId(), schema);
        var transition = new WorkflowImpl.TransitionSchemaImpl(); transition.setId("request-changes"); transition.setSourceStates(Set.of(schema.getId())); transition.setTargetState(schema.getId()); transition.setDescription("Request changes"); transition.getMetadata().put("proposalReviewOperation", "REQUEST_CHANGES"); workflow.getTransitions().put(transition.getId(), transition);
        var flow = Flow.create(); flow.setId("fixture-flow"); flow.setWorkflowId(workflow.getId()); flow.setRevision(3); flow.setOwner("reviewer"); flow.setAssetUrn("hydrology");
        var state = Flow.State.create(); state.setId("peer-stage"); state.setSchemaId(schema.getId()); state.setOwner("reviewer"); state.setStatus(Flow.StateStatus.OPEN);
        var candidate = new Candidate("hydrology-proposal", "r1", null, new Artifact("proposal", "fixture"),
            new Artifact("ontology", ProposalCandidateReader.digest(source.getBytes(StandardCharsets.UTF_8))), List.of("clarify-catchment"), "fixture-imports");
        state.setProposalReview(new StageData(1, candidate, Status.IN_REVIEW, "author", "Initial review",
            List.of(new Check(CheckKind.PARSER, CheckStatus.NOT_RUN, List.of("Synthetic UI fixture; no grammar claim")),
                new Check(CheckKind.REASONER, CheckStatus.BLOCKED, List.of("Production validator is not wired"))), ProposalStageEditorTest.hydrology()));
        flow.getStates().put(state.getId(), state); flow.getCurrentStateIds().add(state.getId());
        var shell = new WorkflowEditor(service, scope, workflow, flow, ProposalStageEditor::create); workflowRef.set(shell);
        var editor = new MonacoEditorView("inmemory:///proposal-review-fixture.txt", null); monacoRef.set(editor);
        editor.setMinimapVisible(false); editor.setReviewMode(true);
        var markers = ProposalLexicalMarkers.markers(state.getId(), state.getProposalReview(), source, source,
            Map.of("hydrology:SurfaceCatchment", source.indexOf("subject")));
        shell.setReviewMarkerStages(Map.of(markers.getFirst().id(), state.getId()));
        editor.setReviewMarkers(markers); editor.setOnReviewMarkerClicked(shell::reviewMarkerClicked);
        editor.runAfterEditorRendered(rendered::countDown);
        var split = new SplitPane(editor, shell); split.setDividerPositions(0.52); rootRef.set(split);
        var stage = new Stage(); window.set(stage); stage.setTitle("Proposal review — isolated UI test"); stage.setScene(new Scene(split, 1380, 900)); stage.show();
        editor.loadEditor(source, "plaintext", "vs");
      } catch (Throwable error) { failure.set(error); rendered.countDown(); }
    });
    try {
      assertTrue(rendered.await(30, TimeUnit.SECONDS), "Bundled Monaco did not render");
      if (failure.get() != null) throw new AssertionError(failure.get());
      // Allow the WebView's next rendering pulse after decorations are installed.
      var painted = new CountDownLatch(1);
      Platform.runLater(() -> { var pause = new javafx.animation.PauseTransition(javafx.util.Duration.millis(350)); pause.setOnFinished(e -> painted.countDown()); pause.play(); });
      assertTrue(painted.await(5, TimeUnit.SECONDS));
      WorkflowProposalTransitionTest.fx(() -> {
        var editor = monacoRef.get();
        var web = (WebView)editor.lookupAll(".web-view").stream().filter(WebView.class::isInstance).findFirst().orElseThrow();
        assertTrue(((Number)web.getEngine().executeScript("document.querySelectorAll('.klab-review-marker').length")).intValue() > 0, "Actual Monaco glyph must be visible");
        var shell = workflowRef.get();
        shell.reviewMarkerClicked(new MonacoEditorView.ReviewMarkerClick("peer-stage:proposal:hydrology-SurfaceCatchment", 3, "hydrology-SurfaceCatchment", "author"));
        var root = rootRef.get(); root.applyCss(); root.layout();
        try { Files.createDirectories(Path.of("target/proposal-ui")); ImageIO.write(SwingFXUtils.fromFXImage(root.snapshot(null, null), null), "png", Path.of("target/proposal-ui/side-by-side.png").toFile()); }
        catch (Exception e) { throw new RuntimeException(e); }
      });
    } finally {
      Platform.runLater(() -> { if (workflowRef.get() != null) workflowRef.get().close(); if (window.get() != null) window.get().close(); });
    }
  }
}
