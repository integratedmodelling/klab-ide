package org.integratedmodelling.klab.ide.components.cards;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Map;
import org.integratedmodelling.klab.api.data.Histogram;
import org.integratedmodelling.klab.api.data.impl.HistogramImpl;
import org.integratedmodelling.klab.api.knowledge.observation.impl.ObservationImpl;
import org.junit.jupiter.api.Test;

class ObservationHistogramTest {
  private Histogram histogram(double value) {
    var result = new HistogramImpl();
    result.setEmpty(false);
    var bin = new HistogramImpl.BinImpl();
    bin.setMin(value); bin.setMax(value); bin.setCount(10);
    result.setBins(List.of(bin));
    return result;
  }

  @Test void physicalStartSelectedByValueCardUsesInitializationHistogram() {
    var observation = new ObservationImpl();
    var initial = histogram(123);
    observation.setEventTimestamps(List.of(0L, 1000L));
    observation.setHistograms(Map.of(0L, initial));
    var selected = ObservationCard.histogramAt(observation, ValueCard.initialTimestamp(observation));
    assertSame(initial, selected);
    assertFalse(selected.isEmpty());
    assertEquals(10, selected.getBins().getFirst().getCount());
  }

  @Test void laterStatesUseMostRecentPrecedingSnapshotWithoutBorrowingFutureValues() {
    var observation = new ObservationImpl();
    var initial = histogram(1); var second = histogram(2); var third = histogram(3);
    observation.setHistograms(Map.of(0L, initial, 2000L, second, 3000L, third));
    assertSame(initial, ObservationCard.histogramAt(observation, 0L));
    assertSame(initial, ObservationCard.histogramAt(observation, 1000L));
    assertSame(second, ObservationCard.histogramAt(observation, 2000L));
    assertSame(second, ObservationCard.histogramAt(observation, 2500L));
    assertSame(third, ObservationCard.histogramAt(observation, null));
    observation.setHistograms(Map.of(2000L, second));
    assertNull(ObservationCard.histogramAt(observation, 1000L));
    assertNull(ObservationCard.histogramAt(observation, 0L));
  }

  @Test void initializationSentinelAlsoPrecedesNegativeEpochTimes() {
    var observation = new ObservationImpl();
    var initial = histogram(1); var later = histogram(2);
    observation.setHistograms(Map.of(0L, initial, -1000L, later));
    assertSame(initial, ObservationCard.histogramAt(observation, -2000L));
    assertSame(later, ObservationCard.histogramAt(observation, -500L));
    assertSame(later, ObservationCard.histogramAt(observation, null));
    assertSame(initial, ObservationCard.histogramAt(observation, 0L));
  }
}
