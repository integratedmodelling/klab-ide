package org.integratedmodelling.klab.ide.components.cards;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;
import org.junit.jupiter.api.Test;

class GeometryCardTimelineTest {

  @Test
  void clickSelectsStateAtOrImmediatelyBeforePosition() {
    List<Long> states = List.of(0L, 200L, 400L);

    assertEquals(0L, GeometryCard.timelineStateAt(states, 100L, 500L, 0.0));
    assertEquals(200L, GeometryCard.timelineStateAt(states, 100L, 500L, 0.30));
    assertEquals(400L, GeometryCard.timelineStateAt(states, 100L, 500L, 1.0));
  }

  @Test
  void clickWithoutAvailableStatesDoesNothing() {
    assertNull(GeometryCard.timelineStateAt(List.of(), 100L, 500L, 0.5));
  }
}
