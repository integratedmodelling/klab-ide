package org.integratedmodelling.klab.ide.components;

import java.util.Map;
import java.util.Set;
import org.integratedmodelling.klab.api.exceptions.KlabUnimplementedException;
import org.integratedmodelling.klab.api.geometry.Geometry;
import org.integratedmodelling.klab.api.knowledge.Concept;
import org.integratedmodelling.klab.api.knowledge.Observable;
import org.integratedmodelling.klab.api.knowledge.SemanticType;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.lang.kim.*;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.services.Reasoner;
import org.integratedmodelling.klab.ide.Theme;
import org.integratedmodelling.klab.modeler.model.NavigableKlabStatement;

/** Resolves the receiving extent without creating or submitting an observation. */
record ObservationDropTarget(Geometry geometry, String label) {
  record Semantics(Set<SemanticType> types, boolean collective) {}

  static ObservationDropTarget resolve(
      Object asset, ContextScope scope, Observation context, Observation observer) {
    var semantics = semantics(asset, scope);
    if (semantics == null) return null;
    boolean dependent = !semantics.collective() && SemanticType.isDependent(semantics.types());
    if (dependent) {
      return context == null || context.getGeometry() == null
          ? null
          : new ObservationDropTarget(context.getGeometry(), "Context: " + name(context));
    }
    if (observer == null
        || (!semantics.collective() && !SemanticType.isSubstantial(semantics.types()))) return null;
    // PERCEIVES is the observed extent; OCCUPIES is the observer's own location.
    try {
      var geometry = observer.geometry(Observation.GeometryRelationship.PERCEIVES);
      return geometry == null
          ? null
          : new ObservationDropTarget(geometry, "Observer: " + name(observer));
    } catch (KlabUnimplementedException e) {
      return null;
    }
  }

  private static String name(Observation observation) {
    return observation.getName() == null || observation.getName().isBlank()
        ? Theme.getLabel(observation)
        : observation.getName();
  }

  static Semantics semantics(Object asset, ContextScope scope) {
    if (asset instanceof NavigableKlabStatement<?> statement) asset = statement.getDelegate();
    if (asset instanceof KimModel model) {
      return model.getObservables().isEmpty()
          ? null
          : semantics(model.getObservables().getFirst(), scope);
    }
    if (asset instanceof KimSymbolDefinition symbol) {
      return "observation".equals(symbol.getDefineClass())
              && symbol.getValue() instanceof Map<?, ?> map
          ? semantics(map.get("semantics"), scope)
          : null;
    }
    if (asset instanceof KimObservable observable)
      return semantics(observable.getSemantics(), scope);
    if (asset instanceof KimConcept concept)
      return new Semantics(concept.getType(), concept.isCollective());
    if (asset instanceof KimConceptStatement concept)
      return new Semantics(concept.getType(), false);
    if (asset instanceof Observation observation)
      return semantics(observation.getObservable(), scope);
    if (asset instanceof Observable observable) return semantics(observable.getSemantics(), scope);
    if (asset instanceof Concept concept)
      return new Semantics(concept.getType(), concept.isCollective());
    if (asset instanceof String urn && scope != null) {
      var observable = scope.getService(Reasoner.class).resolveObservable(urn);
      return observable == null ? null : semantics(observable, scope);
    }
    return null;
  }
}
