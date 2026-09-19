package org.integratedmodelling.klab.ide.components;

import java.util.*;
import java.util.concurrent.*;
import java.util.function.*;
import org.integratedmodelling.common.data.jackson.JacksonConfiguration;
import org.integratedmodelling.klab.api.lang.kim.*;
import org.integratedmodelling.klab.api.scope.Scope;
import org.integratedmodelling.klab.api.services.Reasoner;
import org.integratedmodelling.klab.api.services.reasoner.objects.*;

/**
 * Background validation of immutable saved snapshots. UI delivery checks document and lifecycle
 * epochs.
 */
final class WorkspaceSemanticValidation implements AutoCloseable {
  // Compare visible diagnostics, not response timestamps or reasoner revision bookkeeping.
  static String presentation(Update update) {
    if (update == null) return null;
    var text = new StringBuilder(Objects.toString(SemanticValidationRequest.sourceHash(update.request().document().getSourceCode()), ""));
    var response = update.response();
    text.append('\n').append(update.status());
    if (response != null) {
      text.append('\n').append(response.getReason());
      for (var notification : response.getNotifications()) {
        text.append('\n').append(notification.getLevel()).append(':').append(notification.getMessage());
        var location = notification.getLexicalContext();
        if (location != null) text.append('@').append(location.getOffsetInDocument())
            .append(':').append(location.getLength());
      }
    }
    return text.toString();
  }
  record Update(SemanticValidationRequest request, SemanticValidationResponse response) {
    String status() {
      if (response == null) return "Semantic validation pending";
      return switch (response.getStatus()) {
        case COMPLETE -> response.valid() ? "Ready" : "Semantic errors";
        case SYNTAX_ERRORS -> "Document errors";
        case STALE_KNOWLEDGE -> "Semantic validation waiting for knowledge synchronization";
        case UNAVAILABLE -> "Semantic validation unavailable";
        case FAILED -> "Semantic validation failed";
      };
    }
  }

  private static final class Entry {
    final SemanticValidationRequest request;
    volatile String checkedAgainst;

    Entry(SemanticValidationRequest request) {
      this.request = request;
    }
  }

  private final Map<String, Entry> entries = new ConcurrentHashMap<>();
  private final Supplier<Scope> scopeSupplier;
  private final Executor ui;
  private final BiConsumer<String, Update> listener;
  private ScheduledThreadPoolExecutor worker;
  private ScheduledFuture<?> requested;
  private volatile long lifecycle;
  private volatile boolean active;
  private volatile String fingerprint;

  WorkspaceSemanticValidation(
      Supplier<Scope> scope, Executor ui, BiConsumer<String, Update> listener) {
    this.scopeSupplier = scope;
    this.ui = ui;
    this.listener = listener;
  }

  static String key(KlabDocument<?> document) {
    return document.getProjectName()
        + ":"
        + (document instanceof KimOntology ? "ontology:" : "namespace:")
        + document.getUrn();
  }

  // Called on the UI thread. Snapshot now: navigable documents may be replaced by later saves.
  void documents(Collection<? extends KlabDocument<?>> documents) {
    var retained = new HashSet<String>();
    for (var document : documents) {
      if (!(document instanceof KimNamespace || document instanceof KimOntology)) continue;
      var key = key(document);
      retained.add(key);
      String version =
          SemanticValidationRequest.sourceHash(document.getSourceCode())
              + ":"
              + document.getLastUpdateTimestamp();
      var previous = entries.get(key);
      if (previous != null && Objects.equals(version, previous.request.getDocumentVersion()))
        continue;
      var mapper = JacksonConfiguration.newObjectMapper();
      var snapshot =
          mapper.convertValue(
              SemanticValidationRequest.of(document, version), SemanticValidationRequest.class);
      var entry = new Entry(snapshot);
      entries.put(key, entry);
      listener.accept(key, new Update(snapshot, documentErrors(snapshot)));
    }
    entries.keySet().retainAll(retained);
    refreshSoon();
  }

  private static SemanticValidationResponse documentErrors(SemanticValidationRequest request) {
    if (request.document().getNotifications().stream().noneMatch(n ->
        n.getLevel() == org.integratedmodelling.klab.api.services.runtime.Notification.Level.Error
            || n.getLevel() == org.integratedmodelling.klab.api.services.runtime.Notification.Level.SystemError)) return null;
    var response = SemanticValidationResponse.forRequest(request);
    response.setStatus(SemanticValidationResponse.Status.SYNTAX_ERRORS);
    response.setReason("Correct the document errors before semantic validation");
    response.getNotifications().addAll(request.document().getNotifications());
    response.deduplicateNotifications();
    return response;
  }

  synchronized void start() {
    if (active) {
      refreshSoon();
      return;
    }
    active = true;
    lifecycle++;
    worker =
        new ScheduledThreadPoolExecutor(
            1,
            r -> {
              var thread = new Thread(r, "workspace-semantic-validation");
              thread.setDaemon(true);
              return thread;
            });
    worker.setRemoveOnCancelPolicy(true);
    worker.scheduleWithFixedDelay(this::refresh, 0, 30, TimeUnit.SECONDS);
  }

  private synchronized void refreshSoon() {
    if (!active) return;
    if (requested != null) requested.cancel(false);
    requested = worker.schedule(this::refresh, 200, TimeUnit.MILLISECONDS);
  }

  private void deliver(
      String key, Entry entry, SemanticValidationResponse response, long epoch, String stamp) {
    ui.execute(
        () -> {
          if (active
              && lifecycle == epoch
              && entries.get(key) == entry
              && Objects.equals(fingerprint, stamp))
            listener.accept(key, new Update(entry.request, response));
        });
  }

  private void refresh() {
    long epoch = lifecycle;
    if (!active) return;
    try {
      var scope = scopeSupplier.get();
      var reasoner = scope == null ? null : scope.getService(Reasoner.class);
      if (reasoner == null) {
        unavailable(epoch, "No reasoner is available in the current scope");
        return;
      }
      var capabilities = reasoner.capabilities(scope);
      String stamp =
          capabilities.getServiceId()
              + ":"
              + System.identityHashCode(reasoner)
              + ":"
              + capabilities.getKnowledgeRevision();
      if (!active || lifecycle != epoch) return;
      fingerprint = stamp;
      for (var item : List.copyOf(entries.entrySet())) {
        if (!active || epoch != lifecycle) return;
        var entry = item.getValue();
        if (documentErrors(entry.request) != null) continue;
        if (stamp.equals(entry.checkedAgainst)) continue;
        deliver(item.getKey(), entry, null, epoch, stamp);
        entry.request.setKnowledgeRevision(capabilities.getKnowledgeRevision());
        var response = reasoner.validateDocument(entry.request, scope);
        if (response == null) {
          response = SemanticValidationResponse.forRequest(entry.request);
          response.setReason("The reasoner returned no semantic validation result");
        }
        var latest = reasoner.capabilities(scope);
        if (!active || lifecycle != epoch) return;
        var currentScope = scopeSupplier.get();
        if (currentScope == null
            || currentScope.getService(Reasoner.class) != reasoner
            || !Objects.equals(latest.getServiceId(), capabilities.getServiceId())) {
          fingerprint = null;
          refreshSoon();
          return;
        }
        if (latest.getKnowledgeRevision() != capabilities.getKnowledgeRevision()) {
          if (!response.matches(entry.request, latest.getKnowledgeRevision())) {
            fingerprint = null;
            refreshSoon();
            return;
          }
          // Validation can synchronize saved knowledge itself. Accept its current response,
          // then revisit documents checked against the preceding revision.
          capabilities = latest;
          stamp = latest.getServiceId() + ":" + System.identityHashCode(reasoner)
              + ":" + latest.getKnowledgeRevision();
          fingerprint = stamp;
          refreshSoon();
        }
        if (response.getStatus() == SemanticValidationResponse.Status.COMPLETE
            && !response.matches(entry.request, latest.getKnowledgeRevision())) {
          response = SemanticValidationResponse.forRequest(entry.request);
          response.setStatus(SemanticValidationResponse.Status.STALE_KNOWLEDGE);
          response.setReason(
              "The validation response was superseded by a document or knowledge change");
        }
        if (response.getStatus() == SemanticValidationResponse.Status.COMPLETE
            || response.getStatus() == SemanticValidationResponse.Status.SYNTAX_ERRORS
            || response.getStatus() == SemanticValidationResponse.Status.FAILED)
          entry.checkedAgainst = stamp;
        if (response.getStatus() == SemanticValidationResponse.Status.FAILED
            && response.getNotifications().stream().noneMatch(n -> n.getLexicalContext() != null))
          response.addDocumentDiagnostic(entry.request,
              org.integratedmodelling.klab.api.services.runtime.Notification.Level.Error);
        deliver(item.getKey(), entry, response, epoch, stamp);
      }
    } catch (RuntimeException e) {
      unavailable(epoch, "Semantic validation unavailable: " + e.getMessage());
    }
  }

  private void unavailable(long epoch, String reason) {
    if (!active || lifecycle != epoch) return;
    fingerprint = null;
    for (var item : entries.entrySet()) {
      item.getValue().checkedAgainst = null;
      var response = documentErrors(item.getValue().request);
      if (response == null) {
        response = SemanticValidationResponse.forRequest(item.getValue().request);
        response.setReason(reason);
      }
      deliver(item.getKey(), item.getValue(), response, epoch, null);
    }
  }

  @Override
  public synchronized void close() {
    active = false;
    lifecycle++;
    fingerprint = null;
    entries.values().forEach(entry -> entry.checkedAgainst = null);
    if (worker != null) worker.shutdownNow();
  }
}
