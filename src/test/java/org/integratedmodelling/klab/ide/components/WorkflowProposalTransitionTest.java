package org.integratedmodelling.klab.ide.components;

import static org.junit.jupiter.api.Assertions.*;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Set;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import javafx.application.Platform;
import javafx.scene.control.*;
import javafx.stage.Window;
import org.integratedmodelling.klab.api.identities.UserIdentity;
import org.integratedmodelling.klab.api.scope.*;
import org.integratedmodelling.klab.api.services.ResourcesService;
import org.integratedmodelling.klab.api.services.resources.workflow.*;
import org.integratedmodelling.klab.api.services.resources.workflow.impl.WorkflowImpl;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class WorkflowProposalTransitionTest {
  @Test void assignedReviewerGetsOnlyPermittedReviewTransitions() {
    var workflow = new WorkflowImpl(); workflow.setId("review"); workflow.setVersion("1");
    var schema = new WorkflowImpl.StateSchemaImpl(); schema.setId("peer"); schema.setContributorRoles(Set.of(WorkflowRole.REVIEWER));
    workflow.getStates().put("peer", schema);
    var transition = new WorkflowImpl.TransitionSchemaImpl(); transition.setId("changes"); transition.setSourceStates(Set.of("peer"));
    transition.setRoles(Set.of(WorkflowRole.REVIEWER)); transition.getMetadata().put("proposalReviewOperation", "REQUEST_CHANGES");
    workflow.getTransitions().put("changes", transition);
    var flow = Flow.create(); var state = Flow.State.create(); state.setId("s"); state.setSchemaId("peer"); state.setStatus(Flow.StateStatus.OPEN);
    flow.getCurrentStateIds().add("s"); state.getAssignees().add("reviewer");
    var participant = new WorkflowParticipant(); participant.setIdentity("reviewer"); participant.setRoles(Set.of(WorkflowRole.REVIEWER)); participant.setPermittedWorkflows(Set.of("review"));
    assertTrue(WorkflowEditor.canReviewTransition(flow, workflow, state, transition, participant));
    assertFalse(participant.getRoles().contains(WorkflowRole.EDITOR));
    transition.getMetadata().put("proposalReviewOperation", "SUBMIT");
    assertFalse(WorkflowEditor.canReviewTransition(flow, workflow, state, transition, participant));
    transition.getMetadata().put("proposalReviewOperation", "REQUEST_CHANGES");
    state.getAssignees().clear();
    assertFalse(WorkflowEditor.canReviewTransition(flow, workflow, state, transition, participant));
    state.getAssignees().add("reviewer"); participant.setPermittedWorkflows(Set.of());
    assertFalse(WorkflowEditor.canReviewTransition(flow, workflow, state, transition, participant));
  }
  @BeforeAll static void start() { try { Platform.startup(() -> Platform.setImplicitExit(false)); } catch (IllegalStateException ignored) {} }
  static void fx(Runnable action) throws Exception {
    var task = new FutureTask<Void>(() -> { action.run(); return null; });
    Platform.runLater(task); task.get(30, TimeUnit.SECONDS);
  }
  static Object call(Object target, String name, Class<?> type, Object arg) {
    try { var method = WorkflowEditor.class.getDeclaredMethod(name, type); method.setAccessible(true); return method.invoke(target, arg); }
    catch (Exception e) { throw new RuntimeException(e); }
  }
  static ProposalStageEditor view(WorkflowEditor editor) {
    try { var field = WorkflowEditor.class.getDeclaredField("selectedEditor"); field.setAccessible(true); return (ProposalStageEditor)((WorkflowEditor.StageEditor)field.get(editor)).content(); }
    catch (Exception e) { throw new RuntimeException(e); }
  }
  static void answer(ButtonType answer) {
    Platform.runLater(() -> Window.getWindows().stream().filter(Window::isShowing).toList().forEach(window -> {
      if (window.getScene().getRoot() instanceof DialogPane pane && pane.getButtonTypes().contains(answer))
        ((Button)pane.lookupButton(answer)).fire();
    }));
  }
  @Test void cancellationConflictNavigationAndRepeatedClickKeepExactRevision() throws Exception {
    fx(() -> {
      var user = (UserIdentity) Proxy.newProxyInstance(UserIdentity.class.getClassLoader(), new Class<?>[]{UserIdentity.class},
          (p,m,a) -> switch (m.getName()) { case "getUsername" -> "reviewer"; case "getEmailAddress" -> "reviewer@example.test"; case "isAuthenticated", "isAnonymous" -> false; case "getGroups" -> Set.of(); default -> null; });
      var scope = (UserScope) Proxy.newProxyInstance(UserScope.class.getClassLoader(), new Class<?>[]{UserScope.class, ServiceSideScope.class},
          (p,m,a) -> switch (m.getName()) { case "getUser" -> user; case "isAuthorized" -> true; default -> null; });
      var workflow = new WorkflowImpl(); workflow.setId("review"); workflow.setVersion("1");
      var schema = new WorkflowImpl.StateSchemaImpl(); schema.setId("peer"); schema.setOpen(true);
      workflow.getStates().put("peer", schema);
      var transition = new WorkflowImpl.TransitionSchemaImpl(); transition.setId("changes"); transition.setSourceStates(Set.of("peer")); transition.setTargetState("peer");
      transition.getMetadata().put("proposalReviewOperation", "REQUEST_CHANGES"); workflow.getTransitions().put("changes", transition);
      var flow = Flow.create(); flow.setId("flow"); flow.setWorkflowId("review"); flow.setRevision(7); flow.setOwner("reviewer");
      var state = Flow.State.create(); state.setId("state"); state.setSchemaId("peer"); state.setStatus(Flow.StateStatus.OPEN); state.setOwner("reviewer"); state.setProposalReview(ProposalReviewModelTest.stage(1));
      flow.getStates().put("state", state); flow.getCurrentStateIds().add("state");
      var calls = new AtomicInteger(); var writes = new AtomicInteger(); var captured = new AtomicReference<Flow.TransitionRequest>();
      var editorRef = new AtomicReference<WorkflowEditor>();
      var service = (ResourcesService) Proxy.newProxyInstance(ResourcesService.class.getClassLoader(), new Class<?>[]{ResourcesService.class}, (p,m,a) -> {
        if (m.getName().equals("updateFlowState")) writes.incrementAndGet();
        if (m.getName().equals("transitionFlow")) {
          calls.incrementAndGet(); captured.set((Flow.TransitionRequest)a[1]);
          call(editorRef.get(), "confirm", Workflow.TransitionSchema.class, transition); // Reentrant repeated click is ignored.
          throw new IllegalStateException("Expected revision 7; current revision 8");
        }
        return null;
      });
      var editor = new WorkflowEditor(service, scope, workflow, flow, ProposalStageEditor::create); editorRef.set(editor);
      var model = view(editor).model(); model.rationale("Resolve groundwater scope");
      answer(ButtonType.CANCEL); call(editor, "confirm", Workflow.TransitionSchema.class, transition);
      assertEquals(0, calls.get()); assertTrue(model.dirty());
      call(editor, "show", Flow.State.class, state); assertSame(model, view(editor).model());
      answer(ButtonType.OK); call(editor, "confirm", Workflow.TransitionSchema.class, transition);
      assertEquals(1, calls.get()); assertEquals(0, writes.get());
      assertEquals(7, captured.get().getExpectedRevision());
      assertEquals(model.candidate(), captured.get().getProposalReview().candidate());
      assertEquals(7, editor.getFlow().getRevision()); assertTrue(model.dirty());
      answer(ButtonType.CANCEL); assertFalse(editor.requestClose());
      flow.setPublicRead(true);
      call(editor, "confirm", Workflow.TransitionSchema.class, transition); assertEquals(1, calls.get());
      editor.close();
    });
  }
}
