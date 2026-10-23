package org.integratedmodelling.klab.ide;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import org.integratedmodelling.klab.api.knowledge.observation.impl.ObservationImpl;
import org.junit.jupiter.api.Test;

class DetachedQueryResultTest {
  @Test void detachedResultsAreDisplayableButNeverGraphAssets() {
    assertFalse(IDEContextScope.isDetachedQueryResult(null));
    var query = new ObservationImpl(); query.setId(0);
    var otherQuery = new ObservationImpl(); otherQuery.setId(0);
    assertTrue(IDEContextScope.isDetachedQueryResult(query));
    assertTrue(IDEContextScope.isDetachedQueryResult(otherQuery));
    assertFalse(IDEContextScope.isKnowledgeGraphAsset(query));
    query.setEmpty(true);
    assertTrue(IDEContextScope.isDetachedQueryResult(query));
    query.setEmpty(false); query.setId(42);
    assertFalse(IDEContextScope.isDetachedQueryResult(query));
    assertTrue(IDEContextScope.isKnowledgeGraphAsset(query));
  }
  @Test void everyDetachedResultCompletesWithoutGraphRefreshOrIdDeduplication() throws Exception {
    var scope = mock(IDEContextScope.class, CALLS_REAL_METHODS);
    var router = new DigitalTwinEventRouter();
    var viewer = mock(org.integratedmodelling.klab.ide.api.DigitalTwinViewer.class);
    router.add(viewer);
    var field = IDEContextScope.class.getDeclaredField("eventRouter");
    field.setAccessible(true); field.set(scope, router);
    var complete = IDEContextScope.class.getDeclaredMethod(
        "processSubmissionFinished", org.integratedmodelling.klab.api.knowledge.observation.Observation.class,
        boolean.class);
    complete.setAccessible(true);
    var first = new ObservationImpl(); first.setId(0);
    var second = new ObservationImpl(); second.setId(0);
    complete.invoke(scope, first, false);
    complete.invoke(scope, second, false);
    var empty = new ObservationImpl(); empty.setId(0); empty.setEmpty(true);
    complete.invoke(scope, empty, false);
    verify(viewer).submissionFinished(empty);
    verify(viewer).submissionFinished(first);
    verify(viewer).submissionFinished(second);
    verify(viewer, never()).knowledgeGraphModified();
    verify(viewer, never()).submissionAborted(any());
    verify(scope, never()).setFocus(any(), any());
  }
}
