package org.integratedmodelling.klab.ide.components;

import static org.junit.jupiter.api.Assertions.*;
import java.lang.reflect.Proxy;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import javafx.application.Platform;
import org.integratedmodelling.klab.api.identities.UserIdentity;
import org.integratedmodelling.klab.api.scope.*;
import org.integratedmodelling.klab.api.services.ResourcesService;
import org.integratedmodelling.klab.api.services.resources.workflow.*;
import org.integratedmodelling.klab.api.services.resources.workflow.impl.WorkflowImpl;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class WorkflowCreationTest {
  @BeforeAll static void start() { WorkflowProposalTransitionTest.start(); }
  @Test void createsBootstrapWithoutTransitionAndGuardsRepeatedClick() throws Exception {
    verifyCreation(false);
  }
  @Test void uncertainCreationRetainsDraftAndDoesNotRetry() throws Exception {
    verifyCreation(true);
  }
  private void verifyCreation(boolean missingResult) throws Exception {
    var calls = new AtomicInteger();
    var completed = new CountDownLatch(1);
    var editor = new AtomicReference<WorkflowEditor>();
    var sent = new AtomicReference<Flow.State>();
    WorkflowProposalTransitionTest.fx(() -> {
      var user = (UserIdentity) Proxy.newProxyInstance(UserIdentity.class.getClassLoader(), new Class<?>[]{UserIdentity.class},
          (p,m,a) -> switch (m.getName()) { case "getUsername" -> "creator"; case "getEmailAddress" -> "creator@example.test"; case "isAuthenticated", "isAnonymous" -> false; case "getGroups" -> Set.of(); default -> null; });
      var scope = (UserScope) Proxy.newProxyInstance(UserScope.class.getClassLoader(), new Class<?>[]{UserScope.class, ServiceSideScope.class},
          (p,m,a) -> switch (m.getName()) { case "getUser" -> user; case "isAuthorized" -> true; default -> null; });
      var workflow = new WorkflowImpl(); workflow.setId("pilot"); workflow.setVersion("1");
      var schema = new WorkflowImpl.StateSchemaImpl(); schema.setId("bootstrap"); schema.setOpen(true);
      workflow.getStates().put("bootstrap", schema);
      var initial = Flow.State.create(); initial.setSchemaId("bootstrap"); initial.setOwner("creator");
      var draft = WorkflowUIProvider.NONE.draftFlow(workflow, initial, scope);
      var persisted = Flow.create(); persisted.setId("saved"); persisted.setRevision(1);
      persisted.getStates().put(initial.getId(), initial); persisted.getCurrentStateIds().add(initial.getId());
      var service = (ResourcesService) Proxy.newProxyInstance(ResourcesService.class.getClassLoader(), new Class<?>[]{ResourcesService.class}, (p,m,a) -> {
        if (m.getName().equals("createFlow")) { calls.incrementAndGet(); sent.set((Flow.State)a[1]); if (missingResult) { Platform.runLater(() -> Platform.runLater(completed::countDown)); return null; } return persisted; }
        if (m.getName().equals("transitionFlow") || m.getName().equals("initializeFlow")) fail("Creation must not transition");
        return null;
      });
      editor.set(new WorkflowEditor(service, scope, workflow, draft, null, null, f -> completed.countDown()));
      create(editor.get()); create(editor.get());
    });
    assertTrue(completed.await(10, TimeUnit.SECONDS));
    WorkflowProposalTransitionTest.fx(() -> {
      create(editor.get());
      assertEquals(1, calls.get()); assertEquals(missingResult, editor.get().getFlow().getId().startsWith("draft-"));
      assertNull(sent.get().getId()); assertNull(sent.get().getFlowId());
      assertEquals("bootstrap", sent.get().getSchemaId()); editor.get().close();
    });
  }
  private static void create(WorkflowEditor editor) {
    try { var method = WorkflowEditor.class.getDeclaredMethod("createInitialFlow"); method.setAccessible(true); method.invoke(editor); }
    catch (Exception e) { throw new RuntimeException(e); }
  }
}
