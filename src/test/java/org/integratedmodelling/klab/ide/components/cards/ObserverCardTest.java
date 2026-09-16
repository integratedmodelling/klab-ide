package org.integratedmodelling.klab.ide.components.cards;

import static org.junit.jupiter.api.Assertions.*;
import java.util.EnumSet;
import org.integratedmodelling.klab.api.geometry.Geometry;
import org.integratedmodelling.klab.api.knowledge.SemanticType;
import org.integratedmodelling.common.knowledge.ConceptImpl;
import org.integratedmodelling.common.knowledge.ObservableImpl;
import org.integratedmodelling.klab.api.knowledge.observation.impl.ObservationImpl;
import org.junit.jupiter.api.Test;

class ObserverCardTest {
  private ObservationImpl agent() {
    var concept = new ConceptImpl(); concept.setType(EnumSet.of(SemanticType.AGENT));
    var observable = new ObservableImpl(); observable.setSemantics(concept);
    var agent = new ObservationImpl(); agent.setId(42); agent.setObservable(observable);
    agent.setGeometry(Geometry.UNIVERSAL);
    return agent;
  }
  @Test void editUsesPerceivedBaselineAndNeverOccupiedGeometry() {
    var agent = agent();
    var update = ObserverCard.request(agent, new double[] {-10, 40, 10, 50});
    assertNull(update.getExpectedGeometry());
    var perception = Geometry.create("T0(1){tstart=10,tend=20,ttype=PHYSICAL}");
    agent.setPerceivedGeometry(perception);
    update = ObserverCard.request(agent, new double[] {-10, 40, 10, 50});
    assertEquals(perception.encode(), update.getExpectedGeometry());
    assertEquals(42, update.getObserverId());
    assertEquals(-10, update.getWest()); assertEquals(40, update.getSouth());
    assertEquals(10, update.getEast()); assertEquals(50, update.getNorth());
  }
  @Test void invalidAndWrappedViewportsCannotBecomeEdits() {
    for (var bounds : new double[][] {{1,2,1,3}, {170,0,190,10}, {-10,40,10,Double.NaN}, {-10,-91,10,30}}) {
      assertThrows(IllegalArgumentException.class, () -> ObserverCard.request(agent(), bounds));
    }
  }
}