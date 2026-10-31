package org.integratedmodelling.klab.ide.components;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.*;
import org.integratedmodelling.common.data.jackson.JacksonConfiguration;
import org.integratedmodelling.common.diagnostics.DocumentValidationTrace;
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
    var text =
        new StringBuilder(
            Objects.toString(
                SemanticValidationRequest.sourceHash(update.request().document().getSourceCode()),
                ""));
    var response = update.response();
    text.append('\n').append(update.status());
    if (response != null) {
      text.append('\n').append(response.getReason());
      for (var notification : response.getNotifications()) {
        text.append('\n')
            .append(notification.getLevel())
            .append(':')
            .append(notification.getMessage());
        var location = notification.getLexicalContext();
        if (location != null)
          text.append('@')
              .append(location.getOffsetInDocument())
              .append(':')
              .append(location.getLength());
      }
    }
    return text.toString();
  }

  record Update(
      SemanticValidationRequest request, SemanticValidationResponse response, boolean validating) {
    Update(SemanticValidationRequest request, SemanticValidationResponse response) {
      this(request, response, false);
    }

    String status() {
      if (response == null) return "Semantic validation pending";
      return switch (response.getStatus()) {
        case COMPLETE -> response.valid() ? "Ready" : "Semantic errors";
        case SYNTAX_ERRORS -> "Document errors";
        case STALE_KNOWLEDGE -> "Semantic validation waiting for knowledge synchronization";
        case UNAVAILABLE -> validating ? "Validating" : "Semantic validation unavailable";
        case FAILED -> "Semantic validation failed";
      };
    }
  }

  /** Group shared ingestion failures and show each reason/marker message only once. */
  static String details(Collection<Update> updates) {
    var groups = new LinkedHashMap<String, List<String>>();
    for (var update : updates) {
      var lines = new LinkedHashSet<String>();
      lines.add(update.status());
      if (update.response() != null) {
        var reason = update.response().getReason();
        if (reason != null && !reason.isBlank()) lines.add(reason);
        for (var notification : update.response().getNotifications())
          if (notification.getMessage() != null && !notification.getMessage().isBlank())
            lines.add(notification.getMessage());
      }
      groups
          .computeIfAbsent(String.join("\n", lines), ignored -> new ArrayList<>())
          .add(update.request().document().getUrn());
    }
    return groups.entrySet().stream()
        .map(entry -> String.join(", ", entry.getValue()) + ": " + entry.getKey())
        .collect(java.util.stream.Collectors.joining("\n"));
  }

  private static final class Entry {
    final SemanticValidationRequest request;
    volatile String checkedAgainst;
    String attemptedAgainst;
    long retryAfter;
    int retries;
    String unavailableAgainst = "<never>";
    volatile SemanticValidationResponse.Status lastStatus;
    volatile boolean waitingForKnowledge;
    SemanticValidationResponse lastResponse;
    boolean pendingPresentationEligible;
    PendingCandidate presentationCandidate = PendingCandidate.NONE;
    volatile long presentedVisibility = -1;

    Entry(SemanticValidationRequest request) {
      this.request = request;
    }
  }

  // Reuse serializer/type caches across snapshots; convertValue still detaches each document.
  private final ObjectMapper snapshotMapper = JacksonConfiguration.newObjectMapper();
  private final Map<String, Entry> entries = new ConcurrentHashMap<>();
  private final Supplier<Scope> scopeSupplier;
  private final Executor ui;
  private final BiConsumer<String, Update> listener;
  private final LongSupplier clock;
  private ScheduledThreadPoolExecutor worker;
  private ScheduledFuture<?> requested;
  private volatile long lifecycle;
  private volatile boolean active;
  private volatile boolean closed;
  private volatile long visibility;
  private volatile boolean automatic = true;
  private volatile String fingerprint;
  private Reasoner observedReasoner;
  private long lastUptime = -1;
  private long connection;
  private String publishedSignal;
  private String publishedAuthority = "";
  private String publishedReason = "";
  private boolean publishedBlocked;
  private PendingCandidate publishedCandidate = PendingCandidate.NONE;

  /** Detached display context only: never changes readiness, retry stamps or protocol results. */
  record PendingCandidate(long generation, Map<String, String> sources) {
    PendingCandidate {
      sources = Map.copyOf(sources);
    }

    static final PendingCandidate NONE = new PendingCandidate(-1, Map.of());

    static PendingCandidate from(
        org.integratedmodelling.klab.api.services.KlabService.ServiceStatus status) {
      if (status == null
          || !status.isConnected()
          || !status.isAvailable()
          || status.isShutdown()
          || status.getMetadata() == null
          || !(status.getMetadata().get("semantic.candidate") instanceof Map<?, ?> candidate)
          || !"PENDING".equals(candidate.get("logicalStatus"))
          || !"VALID".equals(candidate.get("beanStatus"))
          || !(candidate.get("inputGeneration") instanceof Number generation)
          || generation.longValue() < 0
          || generation.doubleValue() != generation.longValue()
          || !(candidate.get("candidateSources") instanceof Map<?, ?> values)) return NONE;
      var sources = new HashMap<String, String>();
      for (var entry : values.entrySet()) {
        if (!(entry.getKey() instanceof String key) || !(entry.getValue() instanceof String hash))
          return NONE;
        sources.put(key, hash);
      }
      return new PendingCandidate(generation.longValue(), sources);
    }

    boolean matches(SemanticValidationRequest request) {
      var document = request.document();
      String key =
          org.integratedmodelling.klab.api.knowledge.KlabAsset.KnowledgeClass.classify(
                      document.getClass())
                  .name()
              + ":"
              + document.getUrn();
      String hash = SemanticValidationRequest.sourceHash(document.getSourceCode());
      return generation >= 0 && hash != null && hash.equals(sources.get(key));
    }
  }

  private static boolean unavailableWithoutErrors(SemanticValidationResponse response) {
    return response != null
        && response.getStatus() == SemanticValidationResponse.Status.UNAVAILABLE
        && response.getNotifications().stream()
            .noneMatch(
                n ->
                    n.getLevel()
                            == org.integratedmodelling.klab.api.services.runtime.Notification.Level
                                .Error
                        || n.getLevel()
                            == org.integratedmodelling.klab.api.services.runtime.Notification.Level
                                .SystemError);
  }

  // One atomic metadata value: boot UUID, settled cache revision, validation state. This is
  // readable knowledge, not an assertion that the entire worldview is transactionally accepted.
  private record Signal(
      String stamp,
      long revision,
      String state,
      String session,
      String reason,
      boolean blocked,
      PendingCandidate candidate) {
    boolean ready() {
      return state.equals("ready");
    }
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
    return org.integratedmodelling.klab.api.services.resources.AuthoringSource.key(document);
  }

  // Called on the UI thread. Snapshot now: navigable documents may be replaced by later saves.
  void documents(Collection<? extends KlabDocument<?>> documents) {
    if (closed) return;
    var retained = new HashSet<String>();
    for (var document : documents) {
      if (!(document instanceof KimNamespace || document instanceof KimOntology)) continue;
      if (Boolean.TRUE.equals(
          document
              .getMetadata()
              .get(
                  org.integratedmodelling.klab.api.services.ResourcesService
                      .VALIDATION_DEFERRED_METADATA))) continue;
      var key = key(document);
      retained.add(key);
      String version = DocumentValidationTrace.version(document);
      if (DocumentValidationTrace.enabled())
        DocumentValidationTrace.snapshot(
            DocumentValidationTrace.Event.IDE_INPUT,
            document,
            version,
            -1,
            lifecycle,
            DocumentValidationTrace.Decision.OBSERVE);
      var previous = entries.get(key);
      if (previous != null && Objects.equals(version, previous.request.getDocumentVersion())) {
        if (DocumentValidationTrace.enabled())
          DocumentValidationTrace.snapshot(
              DocumentValidationTrace.Event.IDE_RETAINED,
              previous.request.document(),
              previous.request.getDocumentVersion(),
              previous.request.getKnowledgeRevision(),
              lifecycle,
              DocumentValidationTrace.Decision.REUSE);
        continue;
      }
      if (previous != null) {
        if (DocumentValidationTrace.enabled())
          DocumentValidationTrace.snapshot(
              DocumentValidationTrace.Event.IDE_RETAINED,
              previous.request.document(),
              previous.request.getDocumentVersion(),
              previous.request.getKnowledgeRevision(),
              lifecycle,
              DocumentValidationTrace.Decision.REPLACE);
      }
      var snapshot =
          snapshotMapper.convertValue(
              SemanticValidationRequest.of(document, version), SemanticValidationRequest.class);
      var entry = new Entry(snapshot);
      entries.put(key, entry);
      listener.accept(key, new Update(snapshot, documentErrors(snapshot)));
    }
    entries.keySet().retainAll(retained);
    refreshSoon();
  }

  private static SemanticValidationResponse documentErrors(SemanticValidationRequest request) {
    if (request.document().getNotifications().stream()
        .noneMatch(
            n ->
                n.getLevel()
                        == org.integratedmodelling.klab.api.services.runtime.Notification.Level
                            .Error
                    || n.getLevel()
                        == org.integratedmodelling.klab.api.services.runtime.Notification.Level
                            .SystemError)) return null;
    var response = SemanticValidationResponse.forRequest(request);
    response.setStatus(SemanticValidationResponse.Status.SYNTAX_ERRORS);
    response.setReason("Correct the document errors before semantic validation");
    response.getNotifications().addAll(request.document().getNotifications());
    response.deduplicateNotifications();
    return response;
  }

  synchronized void start() {
    if (closed) return;
    if (active) {
      refreshSoon();
      return;
    }
    active = true;
    if (worker != null) {
      // Serialize resume with any RPC still completing while hidden. Even manual mode must
      // replay an undelivered result after checking the current document and semantic signal.
      worker.execute(() -> refreshNow(false, automatic));
      return;
    }
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

  /** Temporary scene detachment keeps completed validation and the observed service identity. */
  synchronized void pause() {
    if (!active) return;
    active = false;
    visibility++; // Queued UI callbacks from the detached scene must not be applied on return.
    if (requested != null) requested.cancel(false);
  }

  private synchronized void refreshSoon() {
    if (!active || !automatic) return;
    if (requested != null) requested.cancel(false);
    requested = worker.schedule(this::refresh, 200, TimeUnit.MILLISECONDS);
  }

  private void deliver(
      String key, Entry entry, SemanticValidationResponse response, long epoch, String stamp) {
    deliver(key, entry, response, epoch, stamp, false);
  }

  private void deliver(
      String key,
      Entry entry,
      SemanticValidationResponse response,
      long epoch,
      String stamp,
      boolean pendingEligible) {
    entry.lastStatus = response == null ? null : response.getStatus();
    entry.lastResponse = response;
    entry.pendingPresentationEligible = pendingEligible && unavailableWithoutErrors(response);
    var candidate = entry.pendingPresentationEligible ? publishedCandidate : null;
    entry.presentationCandidate = candidate == null ? PendingCandidate.NONE : candidate;
    var update =
        new Update(entry.request, response, candidate != null && candidate.matches(entry.request));
    var uptime = lastUptime;
    var selected = observedReasoner;
    var published = publishedSignal;
    var authority = publishedAuthority;
    var reason = publishedReason;
    var blocked = publishedBlocked;
    long presentationVisibility = visibility;
    ui.execute(
        () -> {
          if (active
              && visibility == presentationVisibility
              && lifecycle == epoch
              && entries.get(key) == entry
              && Objects.equals(fingerprint, stamp)
              && (stamp == null
                  || currentSelection(
                      selected, published, authority, reason, blocked, candidate, uptime))) {
            if (DocumentValidationTrace.enabled())
              DocumentValidationTrace.response(
                  DocumentValidationTrace.Event.IDE_DELIVERY,
                  entry.request,
                  response,
                  epoch,
                  DocumentValidationTrace.Decision.ACCEPT);
            entry.presentedVisibility = presentationVisibility;
            listener.accept(key, update);
          } else {
            if (DocumentValidationTrace.enabled())
              DocumentValidationTrace.response(
                  DocumentValidationTrace.Event.IDE_DELIVERY,
                  entry.request,
                  response,
                  epoch,
                  DocumentValidationTrace.Decision.DISCARD);
          }
        });
  }

  private boolean currentSelection(
      Reasoner selected,
      String published,
      String authority,
      String reason,
      boolean blocked,
      PendingCandidate candidate,
      long sampledUptime) {
    try {
      var scope = scopeSupplier.get();
      if (scope == null || selectedReasoner(scope) != selected) return false;
      var status = selected.status();
      if (status == null) return published == null; // Legacy service without status support.
      return status != null
          && status.isConnected()
          && status.isAvailable()
          && !status.isShutdown()
          && (sampledUptime < 0 || status.getUptimeMs() >= sampledUptime)
          && (candidate == null || candidate.equals(PendingCandidate.from(status)))
          && status.getMetadata() != null
          && Objects.equals(published, status.getMetadata().get("semantic.validation"))
          && reason.equals(validationReason(status.getMetadata(), published))
          && blocked == validationBlocked(status.getMetadata(), published)
          && authority.equals(
              authorityRevisions(status.getMetadata().get("authority.codelist.revisions")));
    } catch (RuntimeException e) {
      return false;
    }
  }

  private static boolean validationBlocked(
      org.integratedmodelling.klab.api.data.Metadata metadata, String signal) {
    return metadata != null
        && signal != null
        && !signal.endsWith(":ready")
        && Boolean.TRUE.equals(metadata.get("semantic.validation.blocked"));
  }

  private static String validationReason(
      org.integratedmodelling.klab.api.data.Metadata metadata, String signal) {
    return metadata == null || signal == null || signal.endsWith(":ready")
        ? ""
        : Objects.toString(metadata.get("semantic.validation.reason"), "");
  }

  private static String authorityRevisions(Object revisions) {
    return revisions instanceof Map<?, ?> values
        ? values.entrySet().stream()
            .map(e -> e.getKey() + "=" + e.getValue())
            .sorted()
            .collect(java.util.stream.Collectors.joining(","))
        : Objects.toString(revisions, "");
  }

  /**
   * Observe local startup failures even though normal service lookup requires operational state.
   */
  static Reasoner selectedReasoner(Scope scope) {
    if (scope == null) return null;
    var services =
        scope
                    instanceof
                    org.integratedmodelling.common.authentication.scope.AbstractClientScope client
                && client.getEngine().getServiceMonitor() != null
            ? client.getEngine().getServiceMonitor().getAllServices(Reasoner.class)
            : scope.getServices(Reasoner.class);
    if (services != null)
      for (var reasoner : services) {
        if (reasoner.getUrl() == null
            || !org.integratedmodelling.klab.api.utils.Utils.URLs.isLocalHost(reasoner.getUrl()))
          continue;
        var status = reasoner.status();
        if (status != null && status.isConnected() && status.isAvailable() && !status.isShutdown())
          return reasoner;
      }
    return scope.getService(Reasoner.class);
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
    publishedReason = validationReason(metadata, publishedSignal);
    publishedBlocked = validationBlocked(metadata, publishedSignal);
    publishedCandidate = PendingCandidate.from(status);
    var revisions = metadata == null ? null : metadata.get("authority.codelist.revisions");
    String authorityRevision = authorityRevisions(revisions);
    publishedAuthority = authorityRevision;
    if (published instanceof String value) {
      var parts = value.split(":", -1);
      if (parts.length != 3
          || parts[0].isBlank()
          || !Set.of("ready", "loading", "failed", "unavailable").contains(parts[2]))
        throw new IllegalStateException("Invalid semantic validation status");
      long revision = Long.parseLong(parts[1]);
      if (parts[2].equals("ready") && revision < 0)
        throw new IllegalStateException("Invalid semantic validation revision");
      return new Signal(
          status.getServiceId()
              + ":"
              + connection
              + ":"
              + value
              + ":"
              + authorityRevision
              + ":"
              + publishedReason
              + ":"
              + publishedBlocked,
          revision,
          parts[2],
          status.getServiceId() + ":" + connection + ":" + parts[0] + ":" + authorityRevision,
          publishedReason,
          publishedBlocked,
          publishedCandidate);
    }
    // Older servers have no settled signal. Keep compatibility, with bounded retries below.
    var capabilities = reasoner.capabilities(scope);
    return new Signal(
        capabilities.getServiceId()
            + ":"
            + connection
            + ":legacy:"
            + capabilities.getKnowledgeRevision()
            + ":"
            + authorityRevision,
        capabilities.getKnowledgeRevision(),
        "ready",
        capabilities.getServiceId() + ":" + connection + ":legacy:" + authorityRevision,
        "",
        false,
        PendingCandidate.NONE);
  }

  private void defer(Entry entry, String stamp) {
    if (!Objects.equals(entry.attemptedAgainst, stamp)) entry.retries = 0;
    entry.attemptedAgainst = stamp;
    long seconds = Math.min(300, 30L << Math.min(entry.retries++, 4));
    entry.retryAfter = clock.getAsLong() + TimeUnit.SECONDS.toNanos(seconds);
  }

  private void unavailableEntry(
      String key,
      Entry entry,
      long epoch,
      String stamp,
      String reason,
      boolean blocked,
      boolean pendingEligible) {
    if (Objects.equals(entry.unavailableAgainst, stamp)) {
      if (entry.presentedVisibility != visibility)
        deliver(key, entry, entry.lastResponse, epoch, stamp, entry.pendingPresentationEligible);
      return;
    }
    entry.unavailableAgainst = stamp;
    var response = documentErrors(entry.request);
    if (response == null) {
      response = SemanticValidationResponse.forRequest(entry.request);
      response.setReason(reason);
      if (blocked) response.setStatus(SemanticValidationResponse.Status.FAILED);
      response.addDocumentDiagnostic(
          entry.request,
          blocked
              ? org.integratedmodelling.klab.api.services.runtime.Notification.Level.Error
              : org.integratedmodelling.klab.api.services.runtime.Notification.Level.Warning);
    }
    deliver(key, entry, response, epoch, stamp, pendingEligible);
  }

  private void refresh() {
    if (automatic) refreshNow(false);
  }

  synchronized void setAutomatic(boolean enabled) {
    if (automatic == enabled) return;
    automatic = enabled;
    lifecycle++; // Discard responses queued before the mode change.
    if (requested != null) requested.cancel(false);
    if (enabled) refreshSoon();
  }

  synchronized void invalidatePending() {
    lifecycle++;
    if (DocumentValidationTrace.enabled())
      for (var entry : entries.values())
        DocumentValidationTrace.snapshot(
            DocumentValidationTrace.Event.IDE_INVALIDATED,
            entry.request.document(),
            entry.request.getDocumentVersion(),
            entry.request.getKnowledgeRevision(),
            lifecycle,
            DocumentValidationTrace.Decision.GLOBAL_SAVE);
    entries.values().forEach(entry -> entry.checkedAgainst = null);
  }

  synchronized void validateOnce(Runnable completed) {
    if (closed) return;
    start();
    long epoch = lifecycle;
    worker.execute(() -> manualAttempt(epoch, 0, completed));
  }

  private void manualAttempt(long epoch, int attempt, Runnable completed) {
    if (!active || lifecycle != epoch) {
      ui.execute(completed);
      return;
    }
    refreshNow(attempt == 0);
    boolean waiting =
        entries.values().stream()
            .anyMatch(
                entry ->
                    entry.waitingForKnowledge
                        || entry.lastStatus == SemanticValidationResponse.Status.STALE_KNOWLEDGE);
    if (waiting && attempt < 20 && active && lifecycle == epoch)
      worker.schedule(
          () -> manualAttempt(epoch, attempt + 1, completed), 1500, TimeUnit.MILLISECONDS);
    else ui.execute(completed);
  }

  private void refreshNow(boolean force) {
    refreshNow(force, true);
  }

  private void refreshNow(boolean force, boolean validate) {
    long epoch = lifecycle;
    if (!active) return;
    try {
      var scope = scopeSupplier.get();
      var reasoner = selectedReasoner(scope);
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
        entry.waitingForKnowledge =
            !signal.ready() && !signal.blocked() && !signal.state().equals("failed");
        if (Boolean.TRUE.equals(
            entry
                .request
                .document()
                .getMetadata()
                .get(
                    org.integratedmodelling.klab.api.services.ResourcesService
                        .VALIDATION_DEFERRED_METADATA))) continue;
        if (force) {
          entry.checkedAgainst = null;
          entry.retryAfter = 0;
          entry.unavailableAgainst = "<never>";
        }
        if (entries.get(item.getKey()) != entry) continue;
        if (documentErrors(entry.request) != null) {
          if (DocumentValidationTrace.enabled())
            DocumentValidationTrace.snapshot(
                DocumentValidationTrace.Event.IDE_DOCUMENT_ERRORS,
                entry.request.document(),
                entry.request.getDocumentVersion(),
                entry.request.getKnowledgeRevision(),
                epoch,
                DocumentValidationTrace.Decision.SKIP_ERRORS);
          continue;
        }
        // A candidate transition repaints an existing wait result without retrying validation.
        if (entry.pendingPresentationEligible
            && !entry.presentationCandidate.equals(signal.candidate()))
          deliver(item.getKey(), entry, entry.lastResponse, epoch, stamp, true);
        // A changed saved ontology can repair a failed update through authoritative source sync.
        boolean repair =
            !signal.blocked()
                && signal.state().equals("failed")
                && entry.request.document() instanceof KimOntology
                && entry.attemptedAgainst == null;
        if (!signal.ready() && !repair) {
          entry.checkedAgainst = null;
          unavailableEntry(
              item.getKey(),
              entry,
              epoch,
              stamp,
              signal.reason().isBlank()
                  ? "Semantic validation waiting for a completed knowledge update ("
                      + signal.state()
                      + ")"
                  : signal.reason(),
              signal.blocked(),
              true);
          continue;
        }
        if (stamp.equals(entry.checkedAgainst)
            || (stamp.equals(entry.attemptedAgainst) && clock.getAsLong() < entry.retryAfter)) {
          if (entry.presentedVisibility != visibility)
            deliver(item.getKey(), entry, entry.lastResponse, epoch, stamp,
                entry.pendingPresentationEligible);
          continue;
        }
        if (!validate) {
          entry.checkedAgainst = null;
          var stale = SemanticValidationResponse.forRequest(entry.request);
          stale.setStatus(SemanticValidationResponse.Status.STALE_KNOWLEDGE);
          stale.setReason("Saved semantic validation is out of date; use Validate now");
          deliver(item.getKey(), entry, stale, epoch, stamp);
          continue;
        }
        entry.unavailableAgainst = "<never>";
        deliver(item.getKey(), entry, null, epoch, stamp);
        entry.request.setKnowledgeRevision(signal.revision());
        if (DocumentValidationTrace.enabled())
          DocumentValidationTrace.response(
              DocumentValidationTrace.Event.IDE_RPC_SEND,
              entry.request,
              null,
              epoch,
              DocumentValidationTrace.Decision.OBSERVE);
        SemanticValidationResponse response;
        boolean pendingEligible = true;
        try {
          response = reasoner.validateDocument(entry.request, scope);
        } catch (RuntimeException e) {
          pendingEligible = false;
          response = SemanticValidationResponse.forRequest(entry.request);
          response.setReason("Semantic validation unavailable: " + e.getMessage());
        }
        if (response == null) {
          pendingEligible = false;
          response = SemanticValidationResponse.forRequest(entry.request);
          response.setReason("The reasoner returned no semantic validation result");
        }
        if (DocumentValidationTrace.enabled())
          DocumentValidationTrace.response(
              DocumentValidationTrace.Event.IDE_RPC_RETURN,
              entry.request,
              response,
              epoch,
              DocumentValidationTrace.Decision.OBSERVE);
        var latest = signal(reasoner, scope);
        // A hidden editor may retain this completed result. Resume runs on this same worker,
        // rechecks its signal, and republishes it under the new visibility epoch.
        if (closed || lifecycle != epoch) return;
        var currentScope = scopeSupplier.get();
        if (currentScope == null || selectedReasoner(currentScope) != reasoner) {
          fingerprint = null;
          refreshSoon();
          return;
        }
        if (entries.get(item.getKey()) != entry) continue;
        if (!latest.stamp().equals(stamp)) {
          // Accept synchronization only within the same boot/selection. A new boot can repeat
          // revision numbers, so a matching counter alone cannot make an old reply current.
          boolean sameSession = latest.session().equals(signal.session());
          if (!sameSession
              || !latest.ready()
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
          response.addDocumentDiagnostic(
              entry.request,
              org.integratedmodelling.klab.api.services.runtime.Notification.Level.Error);
        deliver(item.getKey(), entry, response, epoch, stamp, pendingEligible);
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
      unavailableEntry(item.getKey(), item.getValue(), epoch, null, reason, false, false);
    }
  }

  @Override
  public synchronized void close() {
    closed = true;
    active = false;
    lifecycle++;
    visibility++;
    fingerprint = null;
    entries
        .values()
        .forEach(
            entry -> {
              entry.checkedAgainst = null;
              entry.attemptedAgainst = null;
              entry.unavailableAgainst = "<never>";
            });
    observedReasoner = null;
    lastUptime = -1;
    if (worker != null) worker.shutdownNow();
  }
}
