package org.integratedmodelling.klab.ide.components.treeviews;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import org.integratedmodelling.klab.api.knowledge.observation.impl.ObservationImpl;
import org.junit.jupiter.api.Test;

class ObserverSelectionTest {
  private ObservationImpl observer(long id) {
    var observer = new ObservationImpl(); observer.setId(id); return observer;
  }

  @Test void soleObserverIsSelectedAndRepeatedClicksNeverClearIt() {
    var agent = observer(1);
    var selected = ObserverSelection.currentOrSole(null, List.of(agent));
    assertSame(agent, selected);
    assertSame(agent, ObserverSelection.choose(selected, agent));
    assertSame(agent, ObserverSelection.choose(selected, observer(1)));
    assertSame(agent, ObserverSelection.currentOrSole(selected, List.of()));
  }

  @Test void switchingKeepsTheCatalogAndTheChoiceAcrossRefreshes() {
    var first = observer(1); var second = observer(2); var catalog = List.of(first, second);
    assertNull(ObserverSelection.currentOrSole(null, catalog));
    var selected = ObserverSelection.choose(first, second);
    assertSame(second, ObserverSelection.currentOrSole(selected, catalog));
    assertEquals(2, catalog.size());
    assertSame(first, ObserverSelection.choose(selected, first));
    assertSame(second, ObserverSelection.currentOrSole(selected, List.of(first)));
  }

  @Test void duplicateLinksDoNotPreventSoleObserverSelection() {
    var agent = observer(1);
    assertSame(agent, ObserverSelection.currentOrSole(null, List.of(agent, agent)));
  }
}
