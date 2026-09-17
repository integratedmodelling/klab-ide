package org.integratedmodelling.klab.ide.components.treeviews;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Objects;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;

/** Persistent observer choice, independent of tree-row selection and catalog refresh timing. */
final class ObserverSelection {
  private ObserverSelection() {}

  static Observation choose(Observation current, Observation candidate) {
    Objects.requireNonNull(candidate, "An observer choice cannot clear the current observer");
    return current != null && current.getId() == candidate.getId() ? current : candidate;
  }

  static Observation currentOrSole(Observation current, Collection<? extends Observation> agents) {
    if (current != null) return current;
    var unique = new LinkedHashMap<Long, Observation>();
    agents.forEach(agent -> unique.put(agent.getId(), agent));
    return unique.size() == 1 ? unique.values().iterator().next() : null;
  }
}
