package org.integratedmodelling.klab.ide.components;

import java.util.*;
import org.integratedmodelling.klab.api.knowledge.Concept;
import org.integratedmodelling.klab.api.knowledge.SemanticType;
import org.integratedmodelling.klab.api.knowledge.Worldview;
import org.integratedmodelling.klab.api.lang.kim.KimConcept;
import org.integratedmodelling.klab.ide.components.WorldviewGraphModel.Link;
import org.integratedmodelling.klab.ide.components.WorldviewGraphModel.Relation;

/** Authored contracts absent from Reasoner's graph projections; never inferred from source text. */
final class WorldviewGraphDeclarations {
  private final Map<String, Map<Relation, Set<Link>>> incident = new HashMap<>();

  WorldviewGraphDeclarations(Worldview worldview) {
    if (worldview == null) return;
    WorldviewConcepts.index(worldview).forEach((statement, urn) -> {
      for (var containment : statement.getObservationalContainments()) {
        add(Relation.CONTAINS, urn(containment.container()), urn(containment.contained()),
            "contains (declared)");
      }
      for (var ingredient : statement.getEmergenceTriggers())
        add(Relation.EMERGENCE, urn, urn(ingredient), "emerges from (declared)");
      for (var identity : statement.getRequiredIdentities())
        add(Relation.REQUIRED_IDENTITIES, urn, urn(identity), "requires identity (declared)");
      for (var realm : statement.getRequiredRealms())
        add(Relation.REQUIRED_REALMS, urn, urn(realm), "requires realm (declared)");
    });
  }

  Set<Link> links(String urn, Relation relation) {
    return incident.getOrDefault(urn, Map.of()).getOrDefault(relation, Set.of());
  }

  private void add(Relation relation, String source, String target, String label) {
    if (source == null || source.isBlank() || target == null || target.isBlank()) return;
    var link = new Link(source, target, label);
    for (var endpoint : List.of(source, target))
      incident.computeIfAbsent(endpoint, key -> new EnumMap<>(Relation.class))
          .computeIfAbsent(relation, key -> new LinkedHashSet<>()).add(link);
  }

  private static String urn(KimConcept concept) {
    // Keep Boolean operands grouped: splitting them would turn alternatives into requirements.
    return concept == null ? null : concept.getUrn();
  }

  static boolean supportedContainment(Concept container, Concept contained) {
    int family = family(container);
    return family != 0 && family == family(contained);
  }

  private static int family(Concept concept) {
    if (concept == null || concept.is(SemanticType.NOTHING)) return 0;
    if (concept.is(SemanticType.EVENT) || concept.is(SemanticType.PROCESS)) return 2;
    return concept.is(SemanticType.SUBJECT) || concept.is(SemanticType.AGENT) ? 1 : 0;
  }
}
