package org.integratedmodelling.klab.ide.components;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Proxy;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.function.*;
import org.integratedmodelling.common.services.ReasonerCapabilitiesImpl;
import org.integratedmodelling.klab.api.lang.kim.impl.KimNamespaceImpl;
import org.integratedmodelling.klab.api.scope.Scope;
import org.integratedmodelling.klab.api.services.Reasoner;
import org.integratedmodelling.klab.api.services.reasoner.objects.*;
import org.junit.jupiter.api.Test;

class WorkspaceSemanticValidationTest {
  private static KimNamespaceImpl document(String source) {
    var document = new KimNamespaceImpl();
    document.setUrn("test");
    document.setProjectName("project");
    document.setSourceCode(source);
    return document;
  }

  private static Scope scope(AtomicReference<Reasoner> reasoner) {
    return (Scope)
        Proxy.newProxyInstance(
            Scope.class.getClassLoader(),
            new Class<?>[] {Scope.class},
            (self, method, args) -> method.getName().equals("getService") ? reasoner.get() : null);
  }

  private static Reasoner reasoner(
      AtomicLong revision,
      Function<SemanticValidationRequest, SemanticValidationResponse> validate) {
    return (Reasoner)
        Proxy.newProxyInstance(
            Reasoner.class.getClassLoader(),
            new Class<?>[] {Reasoner.class},
            (self, method, args) -> {
              if (method.getName().equals("capabilities")) {
                var caps = new ReasonerCapabilitiesImpl();
                caps.setServiceId("reasoner");
                caps.setKnowledgeRevision(revision.get());
                return caps;
              }
              if (method.getName().equals("validateDocument"))
                return validate.apply((SemanticValidationRequest) args[0]);
              return null;
            });
  }

  private static SemanticValidationResponse success(SemanticValidationRequest request) {
    var response = SemanticValidationResponse.forRequest(request);
    response.setKnowledgeRevision(request.getKnowledgeRevision());
    response.setStatus(SemanticValidationResponse.Status.COMPLETE);
    return response;
  }

  private static void until(BooleanSupplier condition, Queue<Runnable> ui) throws Exception {
    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
    do {
      Runnable task;
      while ((task = ui.poll()) != null) task.run();
      if (condition.getAsBoolean()) return;
      Thread.sleep(10);
    } while (System.nanoTime() < deadline);
    fail("Timed out waiting for validation");
  }

  @Test
  void identicalDiagnosticsIgnoreKnowledgeRevisionButChangesRemainVisible() {
    var request = SemanticValidationRequest.of(document("source"), "1");
    var first = success(request);
    var second = success(request);
    second.setKnowledgeRevision(99);
    var a = new WorkspaceSemanticValidation.Update(request, first);
    var b = new WorkspaceSemanticValidation.Update(request, second);
    assertEquals(
        WorkspaceSemanticValidation.presentation(a), WorkspaceSemanticValidation.presentation(b));
    second.setReason("Changed status");
    assertNotEquals(
        WorkspaceSemanticValidation.presentation(a), WorkspaceSemanticValidation.presentation(b));
    second.setReason(first.getReason());
    second
        .getNotifications()
        .add(org.integratedmodelling.klab.api.services.runtime.Notification.warning("New warning"));
    assertNotEquals(
        WorkspaceSemanticValidation.presentation(a), WorkspaceSemanticValidation.presentation(b));
  }

  @Test
  void aLaterSaveSupersedesAnInFlightResponseAndRequestsRunOffTheCallerThread() throws Exception {
    var entered = new CountDownLatch(1);
    var release = new CountDownLatch(1);
    var caller = Thread.currentThread();
    var background = new AtomicReference<Thread>();
    var selected = new AtomicReference<Reasoner>();
    var ui = new ConcurrentLinkedQueue<Runnable>();
    var updates = new ArrayList<WorkspaceSemanticValidation.Update>();
    selected.set(
        reasoner(
            new AtomicLong(1),
            request -> {
              background.set(Thread.currentThread());
              if (request.document().getSourceCode().equals("old")) {
                entered.countDown();
                try {
                  release.await(5, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                  Thread.currentThread().interrupt();
                }
              }
              return success(request);
            }));
    try (var coordinator =
        new WorkspaceSemanticValidation(
            () -> scope(selected), ui::add, (key, update) -> updates.add(update))) {
      coordinator.documents(List.of(document("old")));
      coordinator.start();
      assertTrue(entered.await(5, TimeUnit.SECONDS));
      coordinator.documents(List.of(document("new")));
      release.countDown();
      until(() -> updates.stream().anyMatch(u -> u.response() != null && u.response().valid()), ui);
      assertNotSame(caller, background.get());
      assertTrue(
          updates.stream()
              .filter(u -> u.response() != null)
              .allMatch(u -> u.request().document().getSourceCode().equals("new")));
    } finally {
      release.countDown();
    }
  }

  @Test
  void absenceRecoveryAndKnowledgeChangesRefreshPreviouslyCheckedDocuments() throws Exception {
    var selected = new AtomicReference<Reasoner>();
    var revision = new AtomicLong(1);
    var ui = new ConcurrentLinkedQueue<Runnable>();
    var updates = new ArrayList<WorkspaceSemanticValidation.Update>();
    var calls = new AtomicInteger();
    var document = document("source");
    try (var coordinator =
        new WorkspaceSemanticValidation(
            () -> scope(selected), ui::add, (key, update) -> updates.add(update))) {
      coordinator.documents(List.of(document));
      coordinator.start();
      until(
          () ->
              updates.stream()
                  .anyMatch(
                      u ->
                          u.response() != null
                              && u.response().getStatus()
                                  == SemanticValidationResponse.Status.UNAVAILABLE),
          ui);
      selected.set(
          reasoner(
              revision,
              request -> {
                calls.incrementAndGet();
                return success(request);
              }));
      coordinator.documents(List.of(document));
      until(() -> updates.stream().anyMatch(u -> u.response() != null && u.response().valid()), ui);
      revision.set(2);
      coordinator.documents(List.of(document));
      until(
          () ->
              updates.stream()
                  .anyMatch(u -> u.response() != null && u.response().getKnowledgeRevision() == 2),
          ui);
      assertEquals(2, calls.get());
    }
  }

  @Test
  void responseForAnotherKnowledgeRevisionIsReportedAsPendingRatherThanValid() throws Exception {
    var selected =
        new AtomicReference<Reasoner>(
            reasoner(
                new AtomicLong(1),
                request -> {
                  var response = success(request);
                  response.setKnowledgeRevision(2);
                  return response;
                }));
    var ui = new ConcurrentLinkedQueue<Runnable>();
    var updates = new ArrayList<WorkspaceSemanticValidation.Update>();
    try (var coordinator =
        new WorkspaceSemanticValidation(
            () -> scope(selected), ui::add, (key, update) -> updates.add(update))) {
      coordinator.documents(List.of(document("source")));
      coordinator.start();
      until(() -> updates.stream().anyMatch(u -> u.response() != null), ui);
      var completed = updates.stream().filter(u -> u.response() != null).findFirst().orElseThrow();
      assertEquals(
          SemanticValidationResponse.Status.STALE_KNOWLEDGE, completed.response().getStatus());
      assertFalse(completed.response().valid());
      assertTrue(completed.status().contains("synchronization"));
    }
  }

  @Test
  void failuresHaveMarkersAndAreRetriedOnlyAfterAChange() throws Exception {
    var calls = new AtomicInteger();
    var revision = new AtomicLong(1);
    var selected =
        new AtomicReference<Reasoner>(
            reasoner(
                revision,
                request -> {
                  calls.incrementAndGet();
                  var response = success(request);
                  response.setStatus(SemanticValidationResponse.Status.FAILED);
                  response.setReason("Backend failed");
                  return response;
                }));
    var ui = new ConcurrentLinkedQueue<Runnable>();
    var updates = new ArrayList<WorkspaceSemanticValidation.Update>();
    var document = document("source");
    try (var coordinator =
        new WorkspaceSemanticValidation(
            () -> scope(selected), ui::add, (key, update) -> updates.add(update))) {
      coordinator.documents(List.of(document));
      coordinator.start();
      until(() -> updates.stream().anyMatch(u -> u.response() != null), ui);
      var failure = updates.stream().filter(u -> u.response() != null).findFirst().orElseThrow();
      assertEquals(
          "test",
          failure.response().getNotifications().getFirst().getLexicalContext().getDocumentUrn());
      coordinator.documents(List.of(document));
      Thread.sleep(400);
      assertEquals(1, calls.get());
      revision.incrementAndGet();
      coordinator.documents(List.of(document));
      until(() -> calls.get() == 2, ui);
    }
  }

  @Test
  void synchronizationRevisionChangeCompletesWithoutRedundantValidation() throws Exception {
    var revision = new AtomicLong(1);
    var calls = new AtomicInteger();
    var selected =
        new AtomicReference<Reasoner>(
            reasoner(
                revision,
                request -> {
                  if (calls.incrementAndGet() == 1) revision.incrementAndGet();
                  var response = success(request);
                  response.setKnowledgeRevision(revision.get());
                  return response;
                }));
    var ui = new ConcurrentLinkedQueue<Runnable>();
    var updates = new ArrayList<WorkspaceSemanticValidation.Update>();
    try (var coordinator =
        new WorkspaceSemanticValidation(
            () -> scope(selected), ui::add, (key, update) -> updates.add(update))) {
      coordinator.documents(List.of(document("source")));
      coordinator.start();
      until(() -> updates.stream().anyMatch(u -> u.response() != null && u.response().valid()), ui);
      assertEquals(1, calls.get());
      assertEquals("Ready", updates.getLast().status());
    }
  }

  @Test
  void documentErrorsFinishEvenWithoutAReasoner() throws Exception {
    var selected = new AtomicReference<Reasoner>();
    var ui = new ConcurrentLinkedQueue<Runnable>();
    var updates = new ArrayList<WorkspaceSemanticValidation.Update>();
    var document = document("source");
    for (int i = 0; i < 6; i++)
      document
          .getNotifications()
          .add(
              org.integratedmodelling.klab.api.services.runtime.Notification.error(
                  "Undefined concept"));
    try (var coordinator =
        new WorkspaceSemanticValidation(
            () -> scope(selected), ui::add, (key, update) -> updates.add(update))) {
      coordinator.documents(List.of(document));
      assertEquals("Document errors", updates.getLast().status());
      coordinator.start();
      until(() -> updates.size() > 1, ui);
      assertTrue(
          updates.stream()
              .allMatch(
                  u ->
                      u.response() != null
                          && u.response().getStatus()
                              == SemanticValidationResponse.Status.SYNTAX_ERRORS));
      assertEquals(1, updates.getLast().response().getNotifications().size());
      assertEquals(
          "Undefined concept",
          updates.getLast().response().getNotifications().getFirst().getMessage());
    }
  }

  @Test
  void removingTheDocumentOrClosingTheWorkspaceDiscardsQueuedCallbacks() throws Exception {
    var selected =
        new AtomicReference<Reasoner>(
            reasoner(new AtomicLong(1), WorkspaceSemanticValidationTest::success));
    var ui = new ConcurrentLinkedQueue<Runnable>();
    var updates = new ArrayList<WorkspaceSemanticValidation.Update>();
    var coordinator =
        new WorkspaceSemanticValidation(
            () -> scope(selected), ui::add, (key, update) -> updates.add(update));
    try {
      coordinator.documents(List.of(document("source")));
      coordinator.start();
      long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
      while (ui.size() < 2 && System.nanoTime() < deadline) Thread.sleep(10);
      assertTrue(ui.size() >= 2);
      coordinator.documents(List.of());
      coordinator.close();
      Runnable task;
      while ((task = ui.poll()) != null) task.run();
      assertTrue(updates.stream().allMatch(u -> u.response() == null));
    } finally {
      coordinator.close();
    }
  }
}
