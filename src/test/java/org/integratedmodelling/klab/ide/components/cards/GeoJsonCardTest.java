package org.integratedmodelling.klab.ide.components.cards;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import org.integratedmodelling.common.knowledge.CohortImpl;
import org.integratedmodelling.common.knowledge.ConceptImpl;
import org.integratedmodelling.common.knowledge.ObservableImpl;
import org.integratedmodelling.klab.api.geometry.Geometry;
import org.integratedmodelling.klab.api.knowledge.SemanticType;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.knowledge.observation.impl.ObservationImpl;
import org.junit.jupiter.api.Test;

class GeoJsonCardTest {

  @Test
  void acceptsAnyTwoDimensionalSpatialExtentIncludingPoints() {
    assertTrue(GeoJsonCard.supportsSpatialGeometry(Geometry.create("S2(1,1)")));
    assertTrue(GeoJsonCard.supportsSpatialGeometry(Geometry.create("S2(64,32)")));
    assertFalse(GeoJsonCard.supportsSpatialGeometry(Geometry.create("S1(64)")));
    assertFalse(GeoJsonCard.supportsSpatialGeometry(Geometry.create("T1(12)")));
  }

  @Test
  void onlySubstantialObservationsUseTheGeoJsonRenderer() {
    assertTrue(GeoJsonCard.supportsObservation(observation(SemanticType.SUBJECT)));
    assertTrue(GeoJsonCard.supportsObservation(observation(SemanticType.EVENT)));
    assertFalse(GeoJsonCard.supportsObservation(observation(SemanticType.QUALITY)));
  }

  @Test
  void spatialCohortsUseTheGeoJsonRenderer() {
    var cohort = new CohortImpl();
    cohort.setGeometry(Geometry.create("S2(1,1)"));
    assertTrue(GeoJsonCard.supportsCohort(cohort));

    cohort.setGeometry(Geometry.create("T1(12)"));
    assertFalse(GeoJsonCard.supportsCohort(cohort));
  }

  private static Observation observation(SemanticType type) {
    var semantics = new ConceptImpl();
    semantics.setType(Set.of(type));
    var observable = new ObservableImpl();
    observable.setSemantics(semantics);
    var observation = new ObservationImpl();
    observation.setObservable(observable);
    observation.setGeometry(Geometry.create("S2(1,1)"));
    return observation;
  }
}
