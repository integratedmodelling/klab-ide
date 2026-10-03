package org.integratedmodelling.klab.services.resources.workflow;

import static org.junit.jupiter.api.Assertions.*;
import java.lang.reflect.Proxy;
import java.util.Set;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import javafx.application.Platform;
import javafx.scene.control.*;
import javafx.stage.Window;
import org.integratedmodelling.klab.api.services.ResourcesService;
import org.integratedmodelling.klab.api.services.resources.workflow.*;
import org.integratedmodelling.klab.ide.components.*;

/** Standalone integration regression; uses built backend test fixtures, never starts a service. */
public class IDERequestChangesLifecycle {
  public static void main(String[] args) throws Exception {
    var fixture = new ProposalReviewProtocolTest();
    var manager = fixture.manager(fixture.store());
    var author = fixture.scope("editor", "EDITOR");
    var reviewer = fixture.scope("reviewer", "REVIEWER");
    var initialization = fixture.initialization(true);
    var peer = Flow.State.create(); peer.setAssignees(Set.of("reviewer"));
    initialization.getTransition().setTargetState(peer);
    var created = manager.initializeFlow(ProposalReviewProtocolTest.WORKFLOW, initialization, author);
    var flow = manager.getFlow(created.getId(), reviewer);
    var schema = manager.getWorkflow(ProposalReviewProtocolTest.WORKFLOW);
    var captured = new AtomicReference<Flow.TransitionRequest>();
    var service = (ResourcesService) Proxy.newProxyInstance(ResourcesService.class.getClassLoader(),
        new Class<?>[]{ResourcesService.class}, (p,m,a) -> {
          if (m.getName().equals("transitionFlow")) {
            var request = (Flow.TransitionRequest)a[1]; captured.set(request);
            return manager.transition((String)a[0], request, reviewer);
          }
          return null;
        });
    Platform.startup(() -> Platform.setImplicitExit(false));
    try {
      var fx = new FutureTask<Void>(() -> {
        var shell = new WorkflowEditor(service, reviewer, schema, flow, ProposalStageEditor::create);
        try {
          var selected = WorkflowEditor.class.getDeclaredField("selectedEditor"); selected.setAccessible(true);
          var stage = (ProposalStageEditor)((WorkflowEditor.StageEditor)selected.get(shell)).content();
          stage.model().rationale("Original author must revise this exact candidate");
          Platform.runLater(() -> {
            for (var window : Window.getWindows().stream().filter(Window::isShowing).toList()) {
              if (window.getScene().getRoot() instanceof DialogPane pane && pane.getButtonTypes().contains(ButtonType.OK))
                ((Button)pane.lookupButton(ButtonType.OK)).fire();
            }
          });
          var confirm = WorkflowEditor.class.getDeclaredMethod("confirm", Workflow.TransitionSchema.class);
          confirm.setAccessible(true); confirm.invoke(shell, schema.getTransitions().get("request-changes"));
          assertNotNull(captured.get(), "Actual IDE confirmation must send its request");
          assertTrue(captured.get().getTargetState() == null || captured.get().getTargetState().getOwner() == null);
        } finally { shell.close(); }
        return null;
      });
      Platform.runLater(fx); fx.get(30, TimeUnit.SECONDS);
      var returned = manager.getFlow(created.getId(), author);
      var editing = fixture.current(returned);
      assertEquals("editor", editing.getOwner());
      assertNotNull(manager.addAttachment(created.getId(), editing.getId(), fixture.proposal("r2", "r1"), author));
      assertThrows(org.integratedmodelling.klab.api.exceptions.KlabResourceAccessException.class,
          () -> manager.addAttachment(created.getId(), editing.getId(), fixture.upload("supporting-material", "text/plain", "reviewer cannot edit"), reviewer));
      System.out.println("PASS: real IDE request-changes -> backend transition -> author owns editing -> original EDITOR revision upload allowed -> REVIEWER upload denied");
      System.out.println("Backend d6e781daa0d440a11589fa550cdf69cdf765a04e; isolated memory store and test validator; no live services.");
    } finally { Platform.exit(); }
  }
}

