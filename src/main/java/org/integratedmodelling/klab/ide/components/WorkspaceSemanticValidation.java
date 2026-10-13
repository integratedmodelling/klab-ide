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
    String attemptedAgainst;
    long retryAfter;
    int retries;
    String unavailableAgainst = "<never>";

    Entry(SemanticValidationRequest request) {
      this.request = request;
    }
  }

  private final Map<String, Entry> entries = new ConcurrentHashMap<>();
  private final Supplier<Scope> scopeSupplier;
  private final Executor ui;
  private final BiConsumer<String, Update> listener;
  private final LongSupplier clock;
  private ScheduledThreadPoolExecutor worker;
  private ScheduledFuture<?> requested;
  private volatile long lifecycle;
  private volatile boolean active;
  private volatile String fingerprint;
  private Reasoner observedReasoner;
  private long lastUptime = -1;
  private long connection;
  private String publishedSignal;
  private String publishedAuthority = "";

  // One atomic metadata value: boot UUID, settled cache revision, validation state. This is
  // readable knowledge, not an assertion that the entire worldview is transactionally accepted.
  private record Signal(String stamp, long revision, String state, String session) {
    boolean ready() { return state.equals("ready"); }
  }

  WorkspaceSemanticValidation(
      Supplier<Scope> scope, Executor ui, BiConsumer<String, Update> listener) {
    this(scope, ui, listener, System::nanoTime);
  }

  WorkspaceSemanticValidation(
      Supplier<Scope> scope, Executor ui, BiConsumer<String, Update> listener, LongSupplier clock) {
    this.scopeSupplier = scope;
    this.ui = ui;
    this.listener = listener;
    this.clock = clock;
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
    var selected = observedReasoner;
    var published = publishedSignal;
    var authority = publishedAuthority;
    ui.execute(
        () -> {
          if (active
              && lifecycle == epoch
              && entries.get(key) == entry
              && Objects.equals(fingerprint, stamp)
              && (stamp == null || currentSelection(selected, published, authority)))
            listener.accept(key, new Update(entry.request, response));
        });
  }

  private boolean currentSelection(Reasoner selected, String published, String authority) {
    try {
      var scope = scopeSupplier.get();
      if (scope == null || scope.getService(Reasoner.class) != selected) return false;
      var status = selected.status();
      if (status == null) return published == null; // Legacy service without status support.
      return status != null && status.isConnected() && status.isAvailable() && !status.isShutdown()
          && status.getMetadata() != null
          && Objects.equals(published, status.getMetadata().get("semantic.validation"))
          && authority.equals(authorityRevisions(status.getMetadata().get("authority.codelist.revisions")));
    } catch (RuntimeException e) {
      return false;
    }
  }

  private static String authorityRevisions(Object revisions) {
    return revisions instanceof Map<?, ?> values
        ? values.entrySet().stream().map(e -> e.getKey() + "=" + e.getValue()).sorted()
            .collect(java.util.stream.Collectors.joining(",")) : Objects.toString(revisions, "");
  }

  private Signal signal(Reasoner reasoner, Scope scope) {
    var status = reasoner.status(); // Remote clients already cache the lightweight status poll.
    if (status != null && (!status.isConnected() || !status.isAvailable() || status.isShutdown()))
      throw new IllegalStateException("The selected reasoner is offline");
    if (reasoner != observedReasoner
        || (status != null && lastUptime >= 0 && status.getUptimeMs() < lastUptime)) connection++;
    observedReasoner = reasoner;
    lastUptime = status == null ? -1 : status.getUptimeMs();
    var metadata = status == null ? null : status.getMetadata();
    var published = metadata == null ? null : metadata.get("semantic.validation");
    if (published != null && !(published instanceof String))
      throw new IllegalStateException("Invalid semantic validation status");
    publishedSignal = published instanceof String value ? value : null;
    var revisions = metadata == null ? null : metadata.get("authority.codelist.revisions");
    String authorityRevision = authorityRevisions(revisions);
    publishedAuthority = authorityRevision;
    if (published instanceof String value) {
      var parts = value.split(":", -1);
      if (parts.length != 3 || parts[0].isBlank()
          || !Set.of("ready", "loading", "failed", "unavailable").contains(parts[2]))
        throw new IllegalStateException("Invalid semantic validation status");
      long revision = Long.parseLong(parts[1]);
      if (parts[2].equals("ready") && revision < 0)
        throw new IllegalStateException("Invalid semantic validation revision");
      return new Signal(status.getServiceId() + ":" + connection + ":" + value
          + ":" + authorityRevision, revision, parts[2],
          status.getServiceId() + ":" + connection + ":" + parts[0] + ":" + authorityRevision);
    }
    // Older servers have no settled signal. Keep compatibility, with bounded retries below.
    var capabilities = reasoner.capabilities(scope);
    return new Signal(capabilities.getServiceId() + ":" + connection + ":legacy:"
        + capabilities.getKnowledgeRevision() + ":" + authorityRevision,
        capabilities.getKnowledgeRevision(), "ready",
        capabilities.getServiceId() + ":" + connection + ":legacy:" + authorityRevision);
  }

  private void defer(Entry entry, String stamp) {
    if (!Objects.equals(entry.attemptedAgainst, stamp)) entry.retries = 0;
    entry.attemptedAgainst = stamp;
    long seconds = Math.min(300, 30L << Math.min(entry.retries++, 4));
    entry.retryAfter = clock.getAsLong() + TimeUnit.SECONDS.toNanos(seconds);
  }

  private void unavailableEntry(String key, Entry entry, long epoch, String stamp, String reason) {
    if (Objects.equals(entry.unavailableAgainst, stamp)) return;
    entry.unavailableAgainst = stamp;
    var response = documentErrors(entry.request);
    if (response == null) {
      response = SemanticValidationResponse.forRequest(entry.request);
      response.setReason(reason);
    }
    deliver(key, entry, response, epoch, stamp);
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
      var signal = signal(reasoner, scope);
      String stamp = signal.stamp();
      if (!active || lifecycle != epoch) return;
      fingerprint = stamp;
      for (var item : List.copyOf(entries.entrySet())) {
        if (!active || epoch != lifecycle) return;
        var entry = item.getValue();
        if (entries.get(item.getKey()) != entry) continue;
        if (documentErrors(entry.request) != null) continue;
        // A changed saved ontology can repair a failed update through authoritative source sync.
        boolean repair = signal.state().equals("failed")
            && entry.request.document() instanceof KimOntology
            && entry.attemptedAgainst == null;
        if (!signal.ready() && !repair) {
          entry.checkedAgainst = null;
          unavailableEntry(item.getKey(), entry, epoch, stamp,
              "Semantic validation waiting for a completed knowledge update (" + signal.state() + ")");
          continue;
        }
        if (stamp.equals(entry.checkedAgainst)) continue;
        if (stamp.equals(entry.attemptedAgainst) && clock.getAsLong() < entry.retryAfter) continue;
        entry.unavailableAgainst = "<never>";
        deliver(item.getKey(), entry, null, epoch, stamp);
        entry.request.setKnowledgeRevision(signal.revision());
        SemanticValidationResponse response;
        try {
          response = reasoner.validateDocument(entry.request, scope);
        } catch (RuntimeException e) {
          response = SemanticValidationResponse.forRequest(entry.request);
          response.setReason("Semantic validation unavailable: " + e.getMessage());
        }
        if (response == null) {
          response = SemanticValidationResponse.forRequest(entry.request);
          response.setReason("The reasoner returned no semantic validation result");
        }
        var latest = signal(reasoner, scope);
        if (!active || lifecycle != epoch) return;
        var currentScope = scopeSupplier.get();
        if (currentScope == null
            || currentScope.getService(Reasoner.class) != reasoner) {
          fingerprint = null;
          refreshSoon();
          return;
        }
        if (entries.get(item.getKey()) != entry) continue;
        if (!latest.stamp().equals(stamp)) {
          // Accept synchronization only within the same boot/selection. A new boot can repeat
          // revision numbers, so a matching counter alone cannot make an old reply current.
          boolean sameSession = latest.session().equals(signal.session());
          if (!sameSession || !latest.ready()
              || !response.matches(entry.request, latest.revision())) {
            fingerprint = null;
            refreshSoon();
            return;
          }
          // Validation can synchronize saved knowledge itself. Accept its current response,
          // then revisit documents checked against the preceding revision.
          signal = latest;
          stamp = latest.stamp();
          fingerprint = stamp;
          refreshSoon();
        }
        if (response.getStatus() == SemanticValidationResponse.Status.COMPLETE
            && (!latest.ready() || !response.matches(entry.request, latest.revision()))) {
          response = SemanticValidationResponse.forRequest(entry.request);
          response.setStatus(SemanticValidationResponse.Status.STALE_KNOWLEDGE);
          response.setReason(
              "The validation response was superseded by a document or knowledge change");
        }
        if (response.getStatus() == SemanticValidationResponse.Status.COMPLETE
            || response.getStatus() == SemanticValidationResponse.Status.SYNTAX_ERRORS
            || response.getStatus() == SemanticValidationResponse.Status.FAILED)
          entry.checkedAgainst = stamp;
        else defer(entry, stamp);
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
    if (fingerprint != null) connection++;
    fingerprint = null;
    for (var item : entries.entrySet()) {
      item.getValue().checkedAgainst = null;
      unavailableEntry(item.getKey(), item.getValue(), epoch, null, reason);
    }
  }

  @Override
  public synchronized void close() {
    active = false;
    lifecycle++;
    fingerprint = null;
    entries.values().forEach(entry -> {
      entry.checkedAgainst = null;
      entry.attemptedAgainst = null;
      entry.unavailableAgainst = "<never>";
    });
    observedReasoner = null;
    lastUptime = -1;
    if (worker != null) worker.shutdownNow();
  }
}
