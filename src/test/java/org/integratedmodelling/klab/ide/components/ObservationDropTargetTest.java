package org.integratedmodelling.klab.ide.components;

import static org.junit.jupiter.api.Assertions.*;
import java.util.Map;
import java.util.Set;
import org.integratedmodelling.klab.api.exceptions.KlabUnimplementedException;
import org.integratedmodelling.klab.api.geometry.Geometry;
import org.integratedmodelling.klab.api.knowledge.SemanticType;
import org.integratedmodelling.klab.api.knowledge.observation.impl.ObservationImpl;
import org.integratedmodelling.klab.api.lang.kim.impl.KimConceptImpl;
import org.integratedmodelling.klab.api.lang.kim.impl.KimModelImpl;
import org.integratedmodelling.klab.api.lang.kim.impl.KimObservableImpl;
import org.integratedmodelling.klab.api.lang.kim.impl.KimSymbolDefinitionImpl;
import org.junit.jupiter.api.Test;

class ObservationDropTargetTest {
  private KimConceptImpl concept(SemanticType type, boolean collective) {
    var concept = new KimConceptImpl();
    concept.setType(Set.of(type));
    concept.setCollective(collective);
    return concept;
  }

  @Test void dependentsUseNamedContextForModelsObservablesAndDefinitions() {
    var context = new ObservationImpl();
    context.setName("Catchment");
    context.setGeometry(Geometry.EMPTY);
    var observable = new KimObservableImpl();
    observable.setSemantics(concept(SemanticType.QUALITY, false));
    var model = new KimModelImpl();
    model.getObservables().add(observable);
    var definition = new KimSymbolDefinitionImpl();
    definition.setDefineClass("observation");
    definition.setValue(Map.of("semantics", observable));
    for (var asset : new Object[] {observable, model, definition, concept(SemanticType.PROCESS, false)}) {
      var target = ObservationDropTarget.resolve(asset, null, context, null);
      assertSame(context.getGeometry(), target.geometry());
      assertEquals("Context: Catchment", target.label());
      assertNull(ObservationDropTarget.resolve(asset, null, null, null));
    }
  }

  @Test void substantialsAndCollectivesUsePerceivedGeometryOnly() {
    var perceived = Geometry.create("S2(2,2)");
    var observer = new ObservationImpl() {
      @Override public Geometry geometry(GeometryRelationship relationship) {
        assertEquals(GeometryRelationship.PERCEIVES, relationship);
        return perceived;
      }
    };
    observer.setName("Observer");
    observer.setGeometry(Geometry.EMPTY);
    for (var asset : new Object[] {concept(SemanticType.SUBJECT, false), concept(SemanticType.QUALITY, true)}) {
      assertSame(perceived, ObservationDropTarget.resolve(asset, null, null, observer).geometry());
      assertNull(ObservationDropTarget.resolve(asset, null, null, null));
    }
  }

  @Test void unavailableObserverExtentDoesNotFallBackToOccupiedGeometry() {
    var observer = new ObservationImpl() {
      @Override public Geometry geometry(GeometryRelationship relationship) {
        throw new KlabUnimplementedException("pending");
      }
    };
    observer.setGeometry(Geometry.EMPTY);
    assertNull(ObservationDropTarget.resolve(concept(SemanticType.SUBJECT, false), null, null, observer));
    assertNull(ObservationDropTarget.resolve(new Object(), null, null, observer));
  }
}
