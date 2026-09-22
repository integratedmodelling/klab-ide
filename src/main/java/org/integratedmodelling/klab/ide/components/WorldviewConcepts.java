package org.integratedmodelling.klab.ide.components;

import java.util.*;
import org.integratedmodelling.klab.api.knowledge.Worldview;
import org.integratedmodelling.klab.api.lang.kim.KimConceptStatement;

/** Declaration URNs are local names; semantic URNs also require the containing ontology. */
final class WorldviewConcepts {
  private WorldviewConcepts() {}

  static String initialFocus(Worldview worldview) {
    var concepts = index(worldview);
    return concepts.entrySet().stream()
        .filter(entry -> "odo:Subject".equals(entry.getKey().getUpperConceptDefined()))
        .map(Map.Entry::getValue).sorted().findFirst()
        .orElseGet(() -> concepts.values().stream().sorted().findFirst().orElse(null));
  }

  static Map<KimConceptStatement, String> index(Worldview worldview) {
    var result = new IdentityHashMap<KimConceptStatement, String>();
    for (var ontology : worldview.getOntologies())
      for (var statement : ontology.getStatements()) index(statement, ontology.getUrn(), result);
    return result;
  }

  private static void index(KimConceptStatement statement, String ontology,
      Map<KimConceptStatement, String> result) {
    String urn = statement.getUrn();
    if (urn != null && !urn.isBlank()) {
      String namespace = statement.getNamespace();
      if (namespace == null || namespace.isBlank()) namespace = ontology;
      result.put(statement, urn.contains(":") ? urn : namespace + ":" + urn);
    }
    for (var child : statement.getChildren()) index(child, ontology, result);
  }
}
