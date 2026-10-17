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
  @Test void newSaveDiscardsAlreadyQueuedManualResults() throws Exception {
    var ui = new ConcurrentLinkedQueue<Runnable>();
    var updates = new ArrayList<WorkspaceSemanticValidation.Update>();
    var backend = reasoner(new AtomicLong(1), WorkspaceSemanticValidationTest::success);
    try (var coordinator = new WorkspaceSemanticValidation(() -> scope(new AtomicReference<>(backend)), ui::add,
        (key, update) -> updates.add(update))) {
      coordinator.setAutomatic(false);
      coordinator.documents(List.of(document("before save")));
      updates.clear();
      coordinator.validateOnce(() -> {});
      var worker = WorkspaceSemanticValidation.class.getDeclaredField("worker"); worker.setAccessible(true);
      ((ScheduledThreadPoolExecutor) worker.get(coordinator)).submit(() -> {}).get(5, TimeUnit.SECONDS);
      coordinator.invalidatePending();
      Runnable task; while ((task = ui.poll()) != null) task.run();
      assertTrue(updates.isEmpty(), "Results from before the new save must not reach the UI");
    }
  }
  @Test void pausedValidationDoesNotPollAndManualRunUsesLatestSnapshot() throws Exception {
    var calls = new AtomicInteger();
    var sources = new CopyOnWriteArrayList<String>();
    var backend = reasoner(new AtomicLong(1), request -> {
      calls.incrementAndGet(); sources.add(request.document().getSourceCode()); return success(request);
    });
    var ui = new ConcurrentLinkedQueue<Runnable>();
    try (var coordinator = new WorkspaceSemanticValidation(() -> scope(new AtomicReference<>(backend)), ui::add, (key, update) -> {})) {
      coordinator.setAutomatic(false);
      coordinator.start();
      coordinator.documents(List.of(document("old")));
      coordinator.documents(List.of(document("latest")));
      var worker = WorkspaceSemanticValidation.class.getDeclaredField("worker"); worker.setAccessible(true);
      ((ScheduledThreadPoolExecutor) worker.get(coordinator)).submit(() -> {}).get(5, TimeUnit.SECONDS);
      assertEquals(0, calls.get());
      var completed = new AtomicBoolean();
      coordinator.validateOnce(() -> completed.set(true));
      until(completed::get, ui);
      assertEquals(List.of("latest"), sources);
      coordinator.documents(List.of(document("saved after manual validation")));
      ((ScheduledThreadPoolExecutor) worker.get(coordinator)).submit(() -> {}).get(5, TimeUnit.SECONDS);
      assertEquals(1, calls.get(), "Manual validation must not resume automatic validation");
    }
  }

  @Test void deferredSnapshotsAreNotSentToTheReasoner() throws Exception {
    var calls = new AtomicInteger();
    var backend = reasoner(new AtomicLong(1), request -> { calls.incrementAndGet(); return success(request); });
    var deferred = document("saved but deferred");
    deferred.getMetadata().put(org.integratedmodelling.klab.api.services.ResourcesService.VALIDATION_DEFERRED_METADATA, true);
    var ui = new ConcurrentLinkedQueue<Runnable>();
    try (var coordinator = new WorkspaceSemanticValidation(() -> scope(new AtomicReference<>(backend)), ui::add, (key, update) -> fail("Deferred snapshots must not emit pending validation"))) {
      coordinator.setAutomatic(false); coordinator.documents(List.of(deferred));
      var completed = new AtomicBoolean(); coordinator.validateOnce(() -> completed.set(true));
      until(completed::get, ui);
      assertEquals(0, calls.get());
    }
  }
  private static org.integratedmodelling.klab.api.services.impl.ServiceStatusImpl status(
      String service, String boot, long revision, String state) {
    var status = new org.integratedmodelling.klab.api.services.impl.ServiceStatusImpl();
    status.setServiceId(service); status.setAvailable(true); status.setConnected(true);
    status.setUptimeMs(100);
    status.getMetadata().put("semantic.validation", boot + ":" + revision + ":" + state);
    return status;
  }

  private static Reasoner signaledReasoner(
      AtomicReference<org.integratedmodelling.klab.api.services.impl.ServiceStatusImpl> status,
      AtomicInteger polls, Function<SemanticValidationRequest, SemanticValidationResponse> validate) {
    return (Reasoner) Proxy.newProxyInstance(Reasoner.class.getClassLoader(),
        new Class<?>[] {Reasoner.class}, (self, method, args) -> {
          if (method.getName().equals("status")) { polls.incrementAndGet(); return status.get(); }
          if (method.getName().equals("capabilities"))
            throw new AssertionError("Settled status must not query capabilities");
          if (method.getName().equals("validateDocument"))
            return validate.apply((SemanticValidationRequest) args[0]);
          return null;
        });
  }

  private static void refresh(WorkspaceSemanticValidation coordinator,
      org.integratedmodelling.klab.api.lang.kim.KlabDocument<?> document,
      AtomicInteger polls, Queue<Runnable> ui) throws Exception {
    int before = polls.get();
    coordinator.documents(List.of(document));
    until(() -> polls.get() > before, ui);
    // Status is read before processing; wait until the worker has finished this pass.
    var done = new CountDownLatch(1);
    var field = WorkspaceSemanticValidation.class.getDeclaredField("worker");
    field.setAccessible(true);
    ((ScheduledThreadPoolExecutor) field.get(coordinator)).schedule(done::countDown, 250, TimeUnit.MILLISECONDS);
    assertTrue(done.await(5, TimeUnit.SECONDS));
    Runnable task; while ((task = ui.poll()) != null) task.run();
  }

  @Test void mixedDocumentSnapshotsStayDetachedAcrossRepeatedEdits() {
    var namespace = document("namespace before");
    var ontology = new org.integratedmodelling.klab.api.lang.kim.impl.KimOntologyImpl();
    ontology.setUrn("ontology"); ontology.setProjectName("project"); ontology.setSourceCode("ontology before");
    var concept = new org.integratedmodelling.klab.api.lang.kim.impl.KimConceptStatementImpl();
    concept.setUrn("before"); ontology.getStatements().add(concept);
    var updates = new ArrayList<WorkspaceSemanticValidation.Update>();
    try (var coordinator = new WorkspaceSemanticValidation(() -> null, Runnable::run, (key, update) -> updates.add(update))) {
      coordinator.documents(List.of(namespace, ontology));
      var oldNamespace = updates.get(0).request().document();
      var oldOntology = updates.get(1).request().getOntology();
      assertNotSame(namespace, oldNamespace); assertNotSame(ontology, oldOntology);
      assertNotSame(concept, oldOntology.getStatements().getFirst());
      namespace.setSourceCode("namespace after"); ontology.setSourceCode("ontology after"); concept.setUrn("after");
      coordinator.documents(List.of(namespace, ontology));
      assertEquals("namespace before", oldNamespace.getSourceCode());
      assertEquals("ontology before", oldOntology.getSourceCode());
      assertEquals("before", oldOntology.getStatements().getFirst().getUrn());
      assertEquals("namespace after", updates.get(2).request().getNamespace().getSourceCode());
      assertEquals("after", updates.get(3).request().getOntology().getStatements().getFirst().getUrn());
      coordinator.documents(List.of(namespace, ontology)); assertEquals(4, updates.size());
    }
  }

  @Test void localBlockedReasonerIsObservedWithoutOperationalServiceLookup() throws Exception {
    var status = status("local", "boot", 0, "unavailable");
    status.getMetadata().put("semantic.validation.blocked", true);
    status.getMetadata().put("semantic.validation.reason", "Missing authority provider descriptor");
    var calls = new AtomicInteger(); var polls = new AtomicInteger();
    var local = (Reasoner) Proxy.newProxyInstance(Reasoner.class.getClassLoader(), new Class<?>[] {Reasoner.class},
        (self, method, args) -> switch (method.getName()) {
          case "getUrl" -> java.net.URI.create("http://localhost:8091/reasoner").toURL();
          case "status" -> { polls.incrementAndGet(); yield status; }
          case "validateDocument" -> { calls.incrementAndGet(); throw new AssertionError("Must not validate blocked knowledge"); }
          default -> null;
        });
    var scope = (Scope) Proxy.newProxyInstance(Scope.class.getClassLoader(), new Class<?>[] {Scope.class},
        (self, method, args) -> {
          if (method.getName().equals("getServices")) return List.of(local);
          if (method.getName().equals("getService")) throw new IllegalStateException("No operational reasoner");
          return null;
        });
    var ui = new ConcurrentLinkedQueue<Runnable>();
    var updates = new ArrayList<WorkspaceSemanticValidation.Update>();
    var document = document("source");
    try (var coordinator = new WorkspaceSemanticValidation(() -> scope, ui::add, (key, update) -> updates.add(update))) {
      coordinator.documents(List.of(document)); coordinator.start();
      until(() -> updates.stream().anyMatch(u -> u.response() != null), ui);
      assertEquals(SemanticValidationResponse.Status.FAILED, updates.getLast().response().getStatus());
      assertTrue(updates.getLast().response().getReason().contains("provider descriptor"));
      refresh(coordinator, document, polls, ui);
      assertEquals(0, calls.get());
      assertEquals(1, updates.stream().filter(u -> u.response() != null).count());
    }
  }
  @Test void sharedFailureDetailsDoNotRepeatReasonAndMarkerOrEachDocument() {
    var updates = new ArrayList<WorkspaceSemanticValidation.Update>();
    for (var urn : List.of("imod", "biology")) {
      var document = document("source"); document.setUrn(urn);
      var request = SemanticValidationRequest.of(document, "1");
      var response = SemanticValidationResponse.forRequest(request);
      response.setStatus(SemanticValidationResponse.Status.FAILED);
      response.setReason("Worldview ingestion blocked: missing ODO import");
      response.addDocumentDiagnostic(request, org.integratedmodelling.klab.api.services.runtime.Notification.Level.Error);
      updates.add(new WorkspaceSemanticValidation.Update(request, response));
    }
    var details = WorkspaceSemanticValidation.details(updates);
    assertEquals(1, details.split("missing ODO import", -1).length - 1);
    assertTrue(details.contains("imod, biology"));
  }
  @Test void unavailableDiagnosticsUpdateWithoutValidationOrRepeatedPresentation() throws Exception {
    var status = new AtomicReference<>(status("reasoner", "boot", 0, "unavailable"));
    status.get().getMetadata().put("semantic.validation.reason",
        "Worldview ingestion blocked: CORE_ALIAS_TARGET_UNAVAILABLE: odo:PhysicalDomain");
    var calls = new AtomicInteger(); var polls = new AtomicInteger();
    var selected = new AtomicReference<>(signaledReasoner(status, polls, request -> {
      calls.incrementAndGet(); return success(request);
    }));
    var ui = new ConcurrentLinkedQueue<Runnable>();
    var updates = new ArrayList<WorkspaceSemanticValidation.Update>();
    var document = document("source");
    try (var coordinator = new WorkspaceSemanticValidation(
        () -> scope(selected), ui::add, (key, update) -> updates.add(update))) {
      coordinator.documents(List.of(document)); coordinator.start();
      until(() -> updates.stream().anyMatch(u -> u.response() != null), ui);
      assertTrue(updates.getLast().response().getReason().contains("odo:PhysicalDomain"));
      assertNotNull(updates.getLast().response().getNotifications().getFirst().getLexicalContext());
      for (int i = 0; i < 3; i++) refresh(coordinator, document, polls, ui);
      assertEquals(1, updates.stream().filter(u -> u.response() != null).count());
      var changed = status("reasoner", "boot", 0, "unavailable");
      changed.getMetadata().put("semantic.validation.blocked", true);
      changed.getMetadata().put("semantic.validation.reason", "Worldview ingestion blocked: missing authority TAXA");
      status.set(changed);
      refresh(coordinator, document, polls, ui);
      assertTrue(updates.getLast().response().getReason().contains("TAXA"));
      assertEquals(SemanticValidationResponse.Status.FAILED, updates.getLast().response().getStatus());
      assertEquals(org.integratedmodelling.klab.api.services.runtime.Notification.Level.Error,
          updates.getLast().response().getNotifications().getFirst().getLevel());
      refresh(coordinator, document, polls, ui);
      assertEquals(2, updates.stream().filter(u -> u.response() != null).count());
      assertEquals(0, calls.get());
      status.set(status("reasoner", "boot", 1, "ready"));
      refresh(coordinator, document, polls, ui);
      assertEquals(1, calls.get());
      assertTrue(updates.getLast().response().valid());
      refresh(coordinator, document, polls, ui);
      assertEquals(1, calls.get());
    }
  }

  @Test void lightweightLoadingPollsMakeNoSemanticCallsAndReadyValidatesOnce() throws Exception {
    var status = new AtomicReference<>(status("reasoner", "boot", 0, "loading"));
    var calls = new AtomicInteger(); var polls = new AtomicInteger();
    var selected = new AtomicReference<>(signaledReasoner(status, polls, request -> {
      calls.incrementAndGet(); return success(request);
    }));
    var ui = new ConcurrentLinkedQueue<Runnable>();
    var updates = new ArrayList<WorkspaceSemanticValidation.Update>();
    var document = document("source");
    try (var coordinator = new WorkspaceSemanticValidation(
        () -> scope(selected), ui::add, (key, update) -> updates.add(update))) {
      coordinator.documents(List.of(document)); coordinator.start();
      until(() -> updates.stream().anyMatch(u -> u.response() != null), ui);
      for (int i = 0; i < 3; i++) refresh(coordinator, document, polls, ui);
      assertEquals(0, calls.get());
      assertEquals(1, updates.stream().filter(u -> u.response() != null).count());
      status.set(status("reasoner", "boot", 1, "ready"));
      refresh(coordinator, document, polls, ui);
      assertEquals(1, calls.get()); assertTrue(updates.getLast().response().valid());
      for (int i = 0; i < 3; i++) refresh(coordinator, document, polls, ui);
      assertEquals(1, calls.get());
    }
  }

  @Test void unavailableAndStaleRepliesBackOffWithoutBeingMarkedSuccessful() throws Exception {
    for (var result : List.of(SemanticValidationResponse.Status.UNAVAILABLE,
        SemanticValidationResponse.Status.STALE_KNOWLEDGE)) {
      var status = new AtomicReference<>(status("reasoner", "boot", 1, "ready"));
      var calls = new AtomicInteger(); var polls = new AtomicInteger(); var clock = new AtomicLong();
      var selected = new AtomicReference<>(signaledReasoner(status, polls, request -> {
        calls.incrementAndGet(); var response = success(request); response.setStatus(result); return response;
      }));
      var ui = new ConcurrentLinkedQueue<Runnable>();
      var updates = new ArrayList<WorkspaceSemanticValidation.Update>();
      var document = document("source");
      try (var coordinator = new WorkspaceSemanticValidation(
          () -> scope(selected), ui::add, (key, update) -> updates.add(update), clock::get)) {
        coordinator.documents(List.of(document)); coordinator.start();
        until(() -> updates.stream().anyMatch(u -> u.response() != null), ui);
        for (int i = 0; i < 3; i++) refresh(coordinator, document, polls, ui);
        assertEquals(1, calls.get()); assertFalse(updates.getLast().response().valid());
        clock.set(TimeUnit.SECONDS.toNanos(30)); refresh(coordinator, document, polls, ui);
        assertEquals(2, calls.get());
        clock.set(TimeUnit.SECONDS.toNanos(89)); refresh(coordinator, document, polls, ui);
        assertEquals(2, calls.get());
        clock.set(TimeUnit.SECONDS.toNanos(90)); refresh(coordinator, document, polls, ui);
        assertEquals(3, calls.get());
        for (int i = 0; i < 3; i++) {
          clock.set(TimeUnit.SECONDS.toNanos(new long[] {210, 450, 750}[i]));
          refresh(coordinator, document, polls, ui); assertEquals(4 + i, calls.get());
        }
        clock.set(TimeUnit.SECONDS.toNanos(1049)); refresh(coordinator, document, polls, ui);
        assertEquals(6, calls.get());
        clock.set(TimeUnit.SECONDS.toNanos(1050)); refresh(coordinator, document, polls, ui);
        assertEquals(7, calls.get());
        status.set(status("reasoner", "boot", 2, "ready"));
        refresh(coordinator, document, polls, ui); assertEquals(8, calls.get());
        refresh(coordinator, document("changed"), polls, ui); assertEquals(9, calls.get());
      }
    }
  }

  @Test void sameClientRestartAndReasonerSwitchRevalidateEqualCounters() throws Exception {
    var status = new AtomicReference<>(status("reasoner", "boot1", 1, "ready"));
    var calls = new AtomicInteger(); var polls = new AtomicInteger();
    Function<SemanticValidationRequest, SemanticValidationResponse> validate = request -> {
      calls.incrementAndGet(); return success(request);
    };
    var selected = new AtomicReference<>(signaledReasoner(status, polls, validate));
    var ui = new ConcurrentLinkedQueue<Runnable>(); var document = document("source");
    try (var coordinator = new WorkspaceSemanticValidation(() -> scope(selected), ui::add, (k, u) -> {})) {
      coordinator.documents(List.of(document)); coordinator.start();
      until(() -> calls.get() == 1, ui);
      status.set(status("reasoner", "boot2", 1, "ready"));
      refresh(coordinator, document, polls, ui); assertEquals(2, calls.get());
      selected.set(signaledReasoner(status, polls, validate));
      refresh(coordinator, document, polls, ui); assertEquals(3, calls.get());
      status.get().setConnected(false);
      refresh(coordinator, document, polls, ui); assertEquals(3, calls.get());
      status.set(status("reasoner", "boot2", 1, "ready"));
      refresh(coordinator, document, polls, ui); assertEquals(4, calls.get());
    }
  }

  @Test void restartDuringValidationDiscardsAnOldBootReplyEvenWhenTheRevisionMatches() throws Exception {
    var status = new AtomicReference<>(status("reasoner", "old", 1, "ready"));
    var calls = new AtomicInteger(); var polls = new AtomicInteger();
    var selected = new AtomicReference<>(signaledReasoner(status, polls, request -> {
      if (calls.incrementAndGet() == 1) {
        status.set(status("reasoner", "new", 1, "loading"));
        return success(request);
      }
      return success(request);
    }));
    var ui = new ConcurrentLinkedQueue<Runnable>();
    var updates = new ArrayList<WorkspaceSemanticValidation.Update>(); var document = document("source");
    try (var coordinator = new WorkspaceSemanticValidation(
        () -> scope(selected), ui::add, (k, u) -> updates.add(u))) {
      coordinator.documents(List.of(document)); coordinator.start();
      until(() -> updates.stream().anyMatch(u -> u.response() != null), ui);
      assertTrue(updates.stream().noneMatch(u -> u.response() != null && u.response().valid()));
      assertEquals(1, calls.get());
      status.set(status("reasoner", "new", 1, "ready"));
      refresh(coordinator, document, polls, ui);
      assertEquals(2, calls.get()); assertTrue(updates.getLast().response().valid());
    }
  }

  @Test void failedSignalAllowsOneSavedOntologyRepairButNeverCertifiesNamespaces() throws Exception {
    var status = new AtomicReference<>(status("reasoner", "boot", 2, "failed"));
    var calls = new AtomicInteger(); var polls = new AtomicInteger();
    var selected = new AtomicReference<>(signaledReasoner(status, polls, request -> {
      calls.incrementAndGet(); return SemanticValidationResponse.forRequest(request);
    }));
    var ui = new ConcurrentLinkedQueue<Runnable>();
    var updates = new ArrayList<WorkspaceSemanticValidation.Update>();
    var ontology = new org.integratedmodelling.klab.api.lang.kim.impl.KimOntologyImpl();
    ontology.setUrn("test"); ontology.setProjectName("project"); ontology.setSourceCode("old");
    try (var coordinator = new WorkspaceSemanticValidation(
        () -> scope(selected), ui::add, (k, u) -> updates.add(u))) {
      coordinator.documents(List.of(ontology)); coordinator.start();
      until(() -> updates.stream().anyMatch(u -> u.response() != null), ui);
      assertEquals(1, calls.get());
      for (int i = 0; i < 3; i++) refresh(coordinator, ontology, polls, ui);
      assertEquals(1, calls.get());
      ontology.setSourceCode("corrected");
      refresh(coordinator, ontology, polls, ui); assertEquals(2, calls.get());
      refresh(coordinator, document("models"), polls, ui); assertEquals(2, calls.get());
      assertTrue(updates.stream().noneMatch(u -> u.response() != null && u.response().valid()));
    }
  }

  @Test void oneUnavailableDocumentDoesNotResubmitCompletedNeighbours() throws Exception {
    var status = new AtomicReference<>(status("reasoner", "boot", 1, "ready"));
    var calls = new ConcurrentHashMap<String, AtomicInteger>(); var polls = new AtomicInteger();
    var selected = new AtomicReference<>(signaledReasoner(status, polls, request -> {
      calls.computeIfAbsent(request.document().getUrn(), k -> new AtomicInteger()).incrementAndGet();
      if (request.document().getUrn().equals("bad")) throw new IllegalStateException("Offline dependency");
      return success(request);
    }));
    var ui = new ConcurrentLinkedQueue<Runnable>();
    var updates = new ArrayList<WorkspaceSemanticValidation.Update>();
    var good = document("good"); good.setUrn("good");
    var bad = document("bad"); bad.setUrn("bad");
    try (var coordinator = new WorkspaceSemanticValidation(
        () -> scope(selected), ui::add, (k, u) -> updates.add(u))) {
      coordinator.documents(List.of(good, bad)); coordinator.start();
      until(() -> updates.stream().filter(u -> u.response() != null).count() == 2, ui);
      coordinator.documents(List.of(good, bad));
      Thread.sleep(350);
      assertEquals(1, calls.get("good").get()); assertEquals(1, calls.get("bad").get());
      assertTrue(updates.stream().anyMatch(u -> u.response() != null && u.response().valid()));
    }
  }

  @Test void queuedResultIsDiscardedWhenSelectionOrBootChangesBeforeUiDelivery() throws Exception {
    for (boolean restart : List.of(false, true)) {
      var status = new AtomicReference<>(status("reasoner", "old", 1, "ready"));
      var calls = new AtomicInteger(); var polls = new AtomicInteger();
      Function<SemanticValidationRequest, SemanticValidationResponse> validate = request -> {
        calls.incrementAndGet(); return success(request);
      };
      var selected = new AtomicReference<>(signaledReasoner(status, polls, validate));
      var ui = new ConcurrentLinkedQueue<Runnable>();
      var updates = new ArrayList<WorkspaceSemanticValidation.Update>();
      try (var coordinator = new WorkspaceSemanticValidation(
          () -> scope(selected), ui::add, (k, u) -> updates.add(u))) {
        coordinator.documents(List.of(document("source"))); coordinator.start();
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (ui.size() < 2 && System.nanoTime() < deadline) Thread.sleep(10);
        assertTrue(ui.size() >= 2);
        if (restart) status.set(status("reasoner", "new", 1, "ready"));
        else selected.set(signaledReasoner(status, polls, validate));
        Runnable task; while ((task = ui.poll()) != null) task.run();
        assertTrue(updates.stream().noneMatch(u -> u.response() != null && u.response().valid()));
      }
    }
  }

  @Test void authorityChangesInvalidateResultsButMapOrderDoesNot() throws Exception {
    var status = new AtomicReference<>(status("reasoner", "boot", 1, "ready"));
    status.get().getMetadata().put("authority.codelist.revisions", new LinkedHashMap<>(Map.of("A", 1L, "B", 2L)));
    var calls = new AtomicInteger(); var polls = new AtomicInteger();
    var selected = new AtomicReference<>(signaledReasoner(status, polls, request -> {
      calls.incrementAndGet(); return success(request);
    }));
    var ui = new ConcurrentLinkedQueue<Runnable>();
    var updates = new ArrayList<WorkspaceSemanticValidation.Update>(); var document = document("source");
    try (var coordinator = new WorkspaceSemanticValidation(
        () -> scope(selected), ui::add, (k, u) -> updates.add(u))) {
      coordinator.documents(List.of(document)); coordinator.start();
      long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
      while (ui.size() < 2 && System.nanoTime() < deadline) Thread.sleep(10);
      assertTrue(ui.size() >= 2);
      status.get().getMetadata().put("authority.codelist.revisions", Map.of("A", 2L, "B", 2L));
      Runnable task; while ((task = ui.poll()) != null) task.run();
      assertTrue(updates.stream().noneMatch(u -> u.response() != null && u.response().valid()));
      refresh(coordinator, document, polls, ui); assertEquals(2, calls.get());
      var reversed = new LinkedHashMap<String, Long>(); reversed.put("B", 2L); reversed.put("A", 2L);
      status.get().getMetadata().put("authority.codelist.revisions", reversed);
      refresh(coordinator, document, polls, ui); assertEquals(2, calls.get());
    }
  }
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
