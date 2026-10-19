package org.integratedmodelling.klab.ide.components;

import static org.junit.jupiter.api.Assertions.*;
import java.lang.reflect.Proxy;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.function.Function;
import org.integratedmodelling.klab.api.lang.kim.impl.KimOntologyImpl;
import org.integratedmodelling.klab.api.scope.Scope;
import org.integratedmodelling.klab.api.services.Reasoner;
import org.integratedmodelling.klab.api.services.impl.ServiceStatusImpl;
import org.integratedmodelling.klab.api.services.reasoner.objects.*;
import org.integratedmodelling.klab.api.services.runtime.Notification;
import org.junit.jupiter.api.Test;

class WorkspaceValidatingPresentationTest {
  private static KimOntologyImpl document(String source) {
    var document = new KimOntologyImpl();document.setUrn("w");document.setProjectName("fixture");
    document.setSourceCode(source);return document;
  }
  private static ServiceStatusImpl status(long accepted) {
    var status = new ServiceStatusImpl();status.setServiceId("reasoner");
    status.setConnected(true);status.setAvailable(true);status.setOperational(accepted > 0);
    status.setUptimeMs(100_000);
    status.getMetadata().put("semantic.validation", "accepted:"+accepted+(accepted > 0 ? ":ready" : ":unavailable"));
    status.getMetadata().put("semantic.validation.reason", "Whole-worldview semantic validation is in progress");
    return status;
  }
  private static Map<String,Object> pending(ServiceStatusImpl status,long generation,String source) {
    var candidate = new LinkedHashMap<String,Object>();
    candidate.put("inputGeneration",generation);candidate.put("logicalStatus","PENDING");
    candidate.put("beanStatus","VALID");candidate.put("candidateSources",new HashMap<>(Map.of(
        "ONTOLOGY:w",SemanticValidationRequest.sourceHash(source))));
    status.getMetadata().put("semantic.candidate",candidate);return candidate;
  }
  private static SemanticValidationResponse reply(SemanticValidationRequest request,
      SemanticValidationResponse.Status status) {
    var response=SemanticValidationResponse.forRequest(request);response.setStatus(status);
    response.setKnowledgeRevision(request.getKnowledgeRevision());
    response.setReason("Whole-worldview semantic validation is in progress");return response;
  }
  private static final class Harness implements AutoCloseable {
    final AtomicReference<ServiceStatusImpl> status;
    final AtomicReference<Reasoner> selected=new AtomicReference<>();
    final AtomicReference<Function<SemanticValidationRequest,SemanticValidationResponse>> respond=
        new AtomicReference<>(r->reply(r,SemanticValidationResponse.Status.UNAVAILABLE));
    final AtomicInteger calls=new AtomicInteger();final AtomicLong clock=new AtomicLong();
    final Queue<Runnable> ui=new ConcurrentLinkedQueue<>();
    final List<WorkspaceSemanticValidation.Update> updates=new ArrayList<>();
    final WorkspaceSemanticValidation coordinator;
    Harness(long accepted,String source) {
      status=new AtomicReference<>(status(accepted));selected.set(reasoner());
      var scope=(Scope)Proxy.newProxyInstance(Scope.class.getClassLoader(),new Class<?>[]{Scope.class},
          (self,method,args)->method.getName().equals("getService")?selected.get():null);
      coordinator=new WorkspaceSemanticValidation(()->scope,ui::add,(key,update)->updates.add(update),clock::get);
      coordinator.setAutomatic(false);coordinator.documents(List.of(document(source)));coordinator.start();
    }
    Reasoner reasoner() {
      return (Reasoner)Proxy.newProxyInstance(Reasoner.class.getClassLoader(),new Class<?>[]{Reasoner.class},
          (self,method,args)->switch(method.getName()) {
            case "status" -> status.get();
            case "capabilities" -> throw new AssertionError("Presentation must not query capabilities");
            case "validateDocument" -> {calls.incrementAndGet();yield respond.get().apply((SemanticValidationRequest)args[0]);}
            default -> null;
          });
    }
    void poll(boolean drain) throws Exception {
      var method=WorkspaceSemanticValidation.class.getDeclaredMethod("refreshNow",boolean.class);
      method.setAccessible(true);method.invoke(coordinator,false);if(drain)drain();
    }
    void drain() {Runnable task;while((task=ui.poll())!=null)task.run();}
    WorkspaceSemanticValidation.Update last(){return updates.getLast();}
    public void close(){coordinator.close();}
  }

  @Test void startupAndPriorAcceptedPendingUseMatchingSavedHashWithoutChangingResults() throws Exception {
    for(long accepted:List.of(0L,1L))try(var h=new Harness(accepted,"saved")) {
      pending(h.status.get(),8,"saved");
      var returned=new AtomicReference<SemanticValidationResponse>();
      h.respond.set(request->{var response=reply(request,SemanticValidationResponse.Status.UNAVAILABLE);
        response.addDocumentDiagnostic(request,Notification.Level.Warning);returned.set(response);return response;});
      h.poll(true);assertEquals("Validating",h.last().status());assertTrue(h.last().validating());
      assertEquals(SemanticValidationResponse.Status.UNAVAILABLE,h.last().response().getStatus());
      assertEquals(Notification.Level.Warning,h.last().response().getNotifications().getFirst().getLevel());
      assertEquals(accepted>0,h.status.get().isOperational());assertEquals(accepted>0?1:0,h.calls.get());
      if(accepted>0)assertSame(returned.get(),h.last().response());
      int delivered=h.updates.size();h.poll(true);assertEquals(delivered,h.updates.size());
      assertEquals(accepted>0?1:0,h.calls.get());
    }
  }

  @Test void alreadyValidDocumentStaysReadyWhileWorldviewReplacementIsPending() throws Exception {
    try(var h=new Harness(1,"unchanged")) {
      h.respond.set(r->reply(r,SemanticValidationResponse.Status.COMPLETE));h.poll(true);
      assertEquals("Ready",h.last().status());int delivered=h.updates.size();
      pending(h.status.get(),8,"unchanged");h.poll(true);
      assertEquals("Ready",h.last().status());assertFalse(h.last().validating());
      assertEquals(delivered,h.updates.size());assertEquals(1,h.calls.get());
    }
  }

  @Test void missingMismatchedAndMalformedCandidateMetadataKeepUnavailable() throws Exception {
    try(var h=new Harness(0,"saved")) {
      h.poll(true);assertEquals("Semantic validation unavailable",h.last().status());
      var metadata=pending(h.status.get(),8,"different saved text");h.poll(true);
      assertFalse(h.last().validating());assertEquals("Semantic validation unavailable",h.last().status());
      metadata.put("inputGeneration",8.5);h.poll(true);assertFalse(h.last().validating());
      metadata.put("inputGeneration",8L);metadata.put("beanStatus","INVALID");h.poll(true);
      assertFalse(h.last().validating());assertEquals(0,h.calls.get());
    }
  }

  @Test void failuresSyntaxAndErrorDiagnosticsNeverBecomeValidating() throws Exception {
    for(var result:List.of(SemanticValidationResponse.Status.FAILED,
        SemanticValidationResponse.Status.SYNTAX_ERRORS,SemanticValidationResponse.Status.UNAVAILABLE)) {
      try(var h=new Harness(1,"saved")) {
        pending(h.status.get(),8,"saved");
        h.respond.set(r->{var response=reply(r,result);response.addDocumentDiagnostic(r,Notification.Level.Error);return response;});
        h.poll(true);assertFalse(h.last().validating());assertEquals(result,h.last().response().getStatus());
        assertNotEquals("Validating",h.last().status());
        assertEquals(Notification.Level.Error,h.last().response().getNotifications().getFirst().getLevel());
      }
    }
    try(var h=new Harness(0,"saved")) {
      var broken=document("saved with syntax error");broken.getNotifications().add(Notification.error("Invalid source"));
      pending(h.status.get(),8,broken.getSourceCode());h.coordinator.documents(List.of(broken));h.poll(true);
      assertEquals("Document errors",h.last().status());assertFalse(h.last().validating());assertEquals(0,h.calls.get());
    }
  }

  @Test void transportFailureAndMissingReplyStayUnavailableDuringPendingCandidate() throws Exception {
    for(boolean missing:List.of(false,true))try(var h=new Harness(1,"saved")) {
      pending(h.status.get(),8,"saved");
      h.respond.set(r->{if(missing)return null;throw new IllegalStateException("transport failed");});
      h.poll(true);assertEquals("Semantic validation unavailable",h.last().status());
      pending(h.status.get(),10,"saved");h.poll(true);assertFalse(h.last().validating());assertEquals(1,h.calls.get());
    }
  }

  @Test void candidateTransitionsRepaintOnlyAndKeepExistingRetryBackoff() throws Exception {
    try(var h=new Harness(1,"saved")) {
      h.poll(true);assertFalse(h.last().validating());assertEquals(1,h.calls.get());
      pending(h.status.get(),8,"saved");h.poll(true);assertEquals("Validating",h.last().status());
      h.status.get().getMetadata().remove("semantic.candidate");h.poll(true);
      assertEquals("Semantic validation unavailable",h.last().status());
      pending(h.status.get(),10,"saved");h.poll(true);assertEquals("Validating",h.last().status());
      assertEquals(1,h.calls.get(),"Presentation changes must not bypass response backoff");
      h.clock.set(TimeUnit.SECONDS.toNanos(30));h.poll(true);assertEquals(2,h.calls.get());
      h.clock.set(TimeUnit.SECONDS.toNanos(89));h.poll(true);assertEquals(2,h.calls.get());
      h.clock.set(TimeUnit.SECONDS.toNanos(90));h.poll(true);assertEquals(3,h.calls.get());
    }
  }

  @Test void completionDiscardsQueuedValidatingAndPublishesCurrentValidation() throws Exception {
    try(var h=new Harness(0,"saved")) {
      pending(h.status.get(),8,"saved");h.poll(false);h.updates.clear();
      h.status.set(status(1));h.respond.set(r->reply(r,SemanticValidationResponse.Status.COMPLETE));
      h.drain();assertTrue(h.updates.isEmpty(),"Completed candidate invalidates queued pending presentation");
      h.poll(true);assertEquals("Ready",h.last().status());assertEquals(1,h.calls.get());
    }
  }

  @Test void replacementAndRepeatedSavesDiscardOlderPendingPresentations() throws Exception {
    try(var h=new Harness(0,"save a")) {
      pending(h.status.get(),8,"save a");h.poll(false);h.updates.clear();
      pending(h.status.get(),10,"save b");h.drain();assertTrue(h.updates.isEmpty());
      h.poll(true);assertFalse(h.last().validating());
      h.coordinator.documents(List.of(document("save b")));h.poll(false);
      h.coordinator.documents(List.of(document("save c")));pending(h.status.get(),12,"save c");
      h.updates.clear();h.drain();assertTrue(h.updates.isEmpty(),"Old saved snapshot must not deliver its pending badge");
      h.poll(true);assertEquals("Validating",h.last().status());
      assertEquals("save c",h.last().request().document().getSourceCode());assertEquals(0,h.calls.get());
    }
  }

  @Test void disconnectRestartAndReasonerSelectionDiscardQueuedPending() throws Exception {
    for(String transition:List.of("disconnect","restart","selection"))try(var h=new Harness(0,"saved")) {
      pending(h.status.get(),1,"saved");h.poll(false);h.updates.clear();
      if(transition.equals("disconnect"))h.status.get().setConnected(false);
      if(transition.equals("restart")){var restarted=status(0);restarted.setUptimeMs(10);pending(restarted,1,"saved");h.status.set(restarted);}
      if(transition.equals("selection"))h.selected.set(h.reasoner());
      h.drain();assertTrue(h.updates.isEmpty(),transition);
      h.poll(true);
      assertEquals(!transition.equals("disconnect"),h.last().validating());assertEquals(0,h.calls.get());
    }
  }

  @SuppressWarnings("unchecked") @Test void pendingContextDetachesMutableStatusMetadata() {
    var status=status(0);var metadata=pending(status,8,"saved");
    var captured=WorkspaceSemanticValidation.PendingCandidate.from(status);
    var request=SemanticValidationRequest.of(document("saved"),"version");assertTrue(captured.matches(request));
    ((Map<String,String>)metadata.get("candidateSources")).put("ONTOLOGY:w","changed");metadata.put("inputGeneration",10L);
    assertTrue(captured.matches(request));assertEquals(8,captured.generation());
    assertThrows(UnsupportedOperationException.class,()->captured.sources().put("ONTOLOGY:w","changed"));
    assertNotEquals(captured,WorkspaceSemanticValidation.PendingCandidate.from(status));
  }
}
