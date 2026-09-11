package org.integratedmodelling.klab.ide;

import java.util.LinkedHashMap;
import java.util.Map;
import org.integratedmodelling.klab.api.provenance.Activity;
import org.jgrapht.Graph;
import org.jgrapht.graph.DefaultDirectedGraph;
import org.jgrapht.graph.DefaultEdge;

/** Message catalogue keyed by the stable, pre-commit activity identity. */
public final class ActivityCatalog {
  private record Entry(Activity activity, boolean finished) {}
  private final Map<Long, Entry> entries = new LinkedHashMap<>();

  public synchronized void accept(Activity activity, boolean finished) {
    if (activity == null) return;
    var previous = entries.get(activity.getTransientId());
    // A delayed start must never replace the completed payload or its diagnostics.
    if (previous == null || finished || !previous.finished()) {
      entries.put(activity.getTransientId(), new Entry(activity, finished));
    }
  }

  /** Rebuild links from complete payloads, including children received before their parents. */
  public synchronized Graph<Activity, DefaultEdge> snapshot() {
    Graph<Activity, DefaultEdge> graph = new DefaultDirectedGraph<>(DefaultEdge.class);
    Map<String, Activity> urns = new LinkedHashMap<>();
    for (var entry : entries.values()) {
      var activity = entry.activity();
      graph.addVertex(activity);
      if (activity.getUrn() != null) urns.put(activity.getUrn(), activity);
    }
    for (var entry : entries.values()) {
      var child = entry.activity();
      var parentEntry = entries.get(child.getParentTransientId());
      var parent = parentEntry == null
          ? urns.get(child.getTriggeringActivityUrn()) : parentEntry.activity();
      if (parent != null && parent != child) graph.addEdge(parent, child);
    }
    return graph;
  }
}
