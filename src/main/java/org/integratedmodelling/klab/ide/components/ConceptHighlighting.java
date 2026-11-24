package org.integratedmodelling.klab.ide.components;

import static org.integratedmodelling.klab.api.utils.Utils.URLs.isLocalHost;

import java.util.*;
import java.util.concurrent.*;
import java.util.function.Supplier;
import javafx.application.Platform;
import org.integratedmodelling.klab.api.knowledge.KlabAsset.KnowledgeClass;
import org.integratedmodelling.klab.api.knowledge.Worldview;
import org.integratedmodelling.klab.api.scope.UserScope;
import org.integratedmodelling.klab.api.services.KlabService;
import org.integratedmodelling.klab.api.services.ResourcesService;
import org.integratedmodelling.klab.api.services.resources.ResourceSet;
import org.integratedmodelling.klab.ide.utils.ApplicationLog;
import org.integratedmodelling.klabeditor.MonacoEditorView;
import org.integratedmodelling.klabeditor.lsp.KlabLspService;

/** Shares resource snapshots across editors and publishes them without transferring the worldview to WebKit. */
public final class ConceptHighlighting {
  private final Supplier<UserScope> scope;
  private final ScheduledExecutorService worker = Executors.newSingleThreadScheduledExecutor(task -> {
    var thread = new Thread(task, "concept-highlighting");
    thread.setDaemon(true);
    return thread;
  });
  private final Map<MonacoEditorView, ResourcesService> editors = new WeakHashMap<>();
  private final Map<String, String> statusStamps = new HashMap<>();
  private Map<ResourcesService, Map<String, String>> snapshots = new HashMap<>();
  private UserScope publishedScope;
  private UserScope requestedScope;
  private ScheduledFuture<?> pending;
  private long generation;

  public ConceptHighlighting(Supplier<UserScope> scope) {
    this.scope = scope;
  }

  public synchronized void attach(MonacoEditorView editor, ResourcesService remoteSource) {
    editors.put(editor, remoteSource);
    var snapshot = publishedScope == scope.get() ? snapshots.get(remoteSource) : null;
    editor.setConceptClassifications(snapshot == null ? Map.of() : snapshot);
    if (snapshot == null) refresh();
  }

  public synchronized void detach(MonacoEditorView editor) {
    editors.remove(editor);
  }

  public synchronized void serviceStatusChanged(KlabService.ServiceStatus status) {
    var type = status.getServiceType();
    if (type != KlabService.Type.RESOURCES && type != KlabService.Type.REASONER) return;
    // Reasoner exposes its boot and settled knowledge revision in a single status value.
    var metadata = status.getMetadata();
    var knowledge = type == KlabService.Type.REASONER && metadata != null
        ? metadata.get("semantic.validation") : null;
    if (type == KlabService.Type.REASONER && status.isOperational()
        && knowledge instanceof String signal && !signal.endsWith(":ready")) return;
    var key = type + ":" + status.getServiceId();
    var stamp = status.isOperational() + ":" + knowledge;
    if (!stamp.equals(statusStamps.put(key, stamp)) || requestedScope != scope.get()) refresh();
  }

  public void workspaceChanged(ResourceSet changes) {
    if (Worldview.WORLDVIEW_WORKSPACE_IDENTIFIER.equals(changes.getWorkspace())
        || !changes.getOntologies().isEmpty()
        || changes.getResults().stream().anyMatch(change ->
            change.getKnowledgeClass() == KnowledgeClass.ONTOLOGY
                || change.getKnowledgeClass() == KnowledgeClass.WORLDVIEW)) {
      refresh();
    }
  }

  public synchronized void refresh() {
    var user = scope.get();
    requestedScope = user;
    if (user != publishedScope && !snapshots.isEmpty()) {
      snapshots = new HashMap<>();
      for (var editor : editors.keySet()) editor.setConceptClassifications(Map.of());
    }
    long request = ++generation;
    if (pending != null) pending.cancel(false);
    if (editors.isEmpty()) {
      snapshots = new HashMap<>();
      return;
    }
    pending = worker.schedule(() -> load(user, request), 250, TimeUnit.MILLISECONDS);
  }

  private void load(UserScope user, long request) {
    Set<ResourcesService> sources;
    Map<ResourcesService, Map<String, String>> previous;
    synchronized (this) {
      if (request != generation || user != scope.get() || user == null) return;
      sources = new HashSet<>(editors.values());
      previous = snapshots;
    }

    Map<ResourcesService, Map<String, String>> next = new HashMap<>();
    try {
      // Use the individual services. UserScope#getService may return a merger or throw
      // even when a previously opened workspace still has a usable Resources client.
      var available = user.getServices(ResourcesService.class);
      var loaded = new HashMap<ResourcesService, Map<String, String>>();
      Map<String, String> local = null;
      for (var service : available) {
        if (isLocal(service)) {
          local = read(service, user, loaded);
          if (local != null) break;
        }
      }
      for (var source : sources) {
        var classifications = local;
        if (classifications == null) {
          classifications = source == null || isLocal(source) ? null : read(source, user, loaded);
          if (classifications == null) {
            for (var service : available) {
              if (!isLocal(service)) {
                classifications = read(service, user, loaded);
                if (classifications != null) break;
              }
            }
          }
        }
        var old = previous.get(source);
        if (classifications == null) classifications = old;
        if (classifications != null) next.put(source, classifications.equals(old) ? old : classifications);
      }
    } catch (RuntimeException unavailable) {
      ApplicationLog.report("Unable to refresh concept classifications", unavailable);
      return;
    }
    Platform.runLater(() -> publish(user, request, next));
  }

  private synchronized void publish(UserScope user, long request,
      Map<ResourcesService, Map<String, String>> next) {
    if (generation != request || user != scope.get()) return;
    publishedScope = user;
    snapshots = next;
    editors.forEach((editor, source) -> editor.setConceptClassifications(next.getOrDefault(source, Map.of())));
  }

  private static boolean isLocal(ResourcesService service) {
    return service.getUrl() != null && isLocalHost(service.getUrl());
  }

  private static Map<String, String> read(ResourcesService service, UserScope user,
      Map<ResourcesService, Map<String, String>> loaded) {
    if (loaded.containsKey(service)) return loaded.get(service);
    Map<String, String> classifications = null;
    try {
      for (var worldview : service.list(Worldview.class, user)) {
        if (worldview != null && !worldview.isEmpty()) {
          classifications = KlabLspService.classifyWorldview(worldview);
          break;
        }
      }
    } catch (RuntimeException unavailable) {
      ApplicationLog.report("Unable to read worldview for concept highlighting from " + service.serviceId(), unavailable);
    }
    loaded.put(service, classifications);
    return classifications;
  }
}
