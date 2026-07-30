package org.integratedmodelling.klab.ide.components.cards;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.integratedmodelling.klab.api.geometry.Geometry;
import org.integratedmodelling.klab.api.knowledge.observation.impl.ObservationImpl;
import org.junit.jupiter.api.Test;

class ValueCardTest {

  @Test
  void onlyDistributedTwoDimensionalSpaceUsesMapRenderer() {
    assertTrue(ValueCard.supportsMap(Geometry.create("S2(64,32)")));
    assertFalse(ValueCard.supportsMap(Geometry.create("S2(1,1)")));
    assertFalse(ValueCard.supportsMap(Geometry.create("S1(64)")));
    assertFalse(ValueCard.supportsMap(Geometry.create("T1(12)")));
  }

  @Test
  void imageExportCarriesViewportAndTemporalState() {
    var parameters = ValueCard.exportParameters(1234L, 640, 480);

    assertEquals(640, parameters.get("viewportX"));
    assertEquals(480, parameters.get("viewportY"));
    assertEquals(1234L, parameters.get("timestamp"));
    assertEquals(1234L, parameters.get("time"));
  }

  @Test
  void clickCoordinatesExcludeLetterboxingAndMapNorthToTop() {
    assertTrue(ValueCard.normalizedPoint(50, 25, 100, 100, 200, 100).isPresent());
    assertTrue(ValueCard.normalizedPoint(50, 10, 100, 100, 200, 100).isEmpty());

    var geometry =
        Geometry.builder().grid(-10.0, 30.0, 35.0, 60.0, "5 km").build();
    var point =
        ValueCard.mapPoint(geometry, new ValueCard.NormalizedPoint(0.25, 0.20));

    assertEquals(0.25, point.normalizedX());
    assertEquals(0.20, point.normalizedY());
    assertEquals(0.0, point.longitude());
    assertEquals(55.0, point.latitude());
  }

  @Test
  void temporalStatesMergeEventAndHistogramTimestampsInOrder() {
    var observation = new ObservationImpl();
    observation.setEventTimestamps(List.of(300L, 0L, 200L));
    // A histogram value is irrelevant to state discovery; a null value also mirrors partially
    // restored metadata while keeping this test independent of a concrete histogram implementation.
    observation.getHistograms().put(100L, null);

    assertEquals(List.of(0L, 100L, 200L, 300L), ValueCard.temporalStates(observation));
  }

  @SuppressWarnings({"rawtypes", "unchecked"})
  @Test
  void temporalStatesNormalizeJsonNumbersAndMapKeysWithoutCasting() {
    var observation = new ObservationImpl();
    observation.setEventTimestamps((List) List.of(300, "200", 100L, "not-a-time"));
    ((java.util.Map) observation.getHistograms()).put("400", null);

    assertEquals(
        List.of(100L, 200L, 300L, 400L), ValueCard.temporalStates(observation));
  }
}
