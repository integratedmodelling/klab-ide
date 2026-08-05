package org.integratedmodelling.klab.ide;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.concurrent.atomic.AtomicInteger;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.knowledge.observation.scale.time.Schedule;
import org.integratedmodelling.klab.ide.api.DigitalTwinViewer;
import org.junit.jupiter.api.Test;

class DigitalTwinEventRouterTest {

  @Test
  void registrationIsIdempotentAndRemovalStopsDelivery() {
    var router = new DigitalTwinEventRouter();
    var viewer = new RecordingViewer();

    router.add(viewer);
    router.add(viewer);
    router.dispatch(DigitalTwinViewer::knowledgeGraphModified);

    assertEquals(1, viewer.graphChanges.get());
    assertEquals(1, router.size());

    router.remove(viewer);
    router.dispatch(DigitalTwinViewer::knowledgeGraphModified);

    assertEquals(1, viewer.graphChanges.get());
    assertEquals(0, router.size());
  }

  @Test
  void oneFailingViewerDoesNotBlockOtherRefreshes() {
    var router = new DigitalTwinEventRouter();
    var recordingViewer = new RecordingViewer();
    router.add(new FailingViewer());
    router.add(recordingViewer);

    router.dispatch(DigitalTwinViewer::knowledgeGraphModified);

    assertEquals(1, recordingViewer.graphChanges.get());
  }

  @Test
  void drainingReturnsCurrentViewersAndStopsFurtherDelivery() {
    var router = new DigitalTwinEventRouter();
    var first = new RecordingViewer();
    var second = new RecordingViewer();
    router.add(first);
    router.add(second);

    assertEquals(2, router.drain().size());
    router.dispatch(DigitalTwinViewer::knowledgeGraphModified);

    assertEquals(0, first.graphChanges.get());
    assertEquals(0, second.graphChanges.get());
    assertEquals(0, router.size());
  }

  @Test
  void removalDuringDispatchPreventsStaleDeliveryFromSnapshot() {
    var router = new DigitalTwinEventRouter();
    var removedViewer = new RecordingViewer();
    var removingViewer =
        new RecordingViewer() {
          @Override
          public void knowledgeGraphModified() {
            router.remove(removedViewer);
          }
        };
    router.add(removingViewer);
    router.add(removedViewer);

    router.dispatch(DigitalTwinViewer::knowledgeGraphModified);

    assertEquals(0, removedViewer.graphChanges.get());
  }

  private static class RecordingViewer implements DigitalTwinViewer {

    private final AtomicInteger graphChanges = new AtomicInteger();

    @Override
    public void submissionStarted(Observation observation) {}

    @Override
    public void submissionAborted(Observation observation) {}

    @Override
    public void submissionFinished(Observation observation) {}

    @Override
    public void setContext(Observation observation) {}

    @Override
    public void setObserver(Observation observation) {}

    @Override
    public void knowledgeGraphModified() {
      graphChanges.incrementAndGet();
    }

    @Override
    public void scheduleModified(Schedule schedule) {}

    @Override
    public void cleanup() {}

    @Override
    public void activitiesModified() {}

    @Override
    public boolean isAffectedBy(IDEContextScope scope) {
      return false;
    }

    @Override
    public void setDigitalTwin(IDEContextScope scope, boolean inFocus) {}

    @Override
    public void close() {}

    @Override
    public void closeDigitalTwin(IDEContextScope scope) {}

    @Override
    public void unsetDigitalTwin(IDEContextScope scope) {}
  }

  private static final class FailingViewer extends RecordingViewer {

    @Override
    public void knowledgeGraphModified() {
      throw new IllegalStateException("expected test failure");
    }
  }
}
