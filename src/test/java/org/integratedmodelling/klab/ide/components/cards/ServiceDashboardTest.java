package org.integratedmodelling.klab.ide.components.cards;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.ArrayDeque;
import java.util.ArrayList;
import org.integratedmodelling.klab.api.services.impl.ServiceStatusImpl;
import org.junit.jupiter.api.Test;

class ServiceDashboardTest {

  @Test
  void sampleWindowKeepsItsWidthAndRollsAfterItFills() {
    assertEquals(new ServiceDashboard.SampleWindow(0, 29), windowAt(0));
    assertEquals(new ServiceDashboard.SampleWindow(0, 29), windowAt(10));
    assertEquals(new ServiceDashboard.SampleWindow(0, 29), windowAt(29));
    assertEquals(new ServiceDashboard.SampleWindow(1, 30), windowAt(30));
    assertEquals(new ServiceDashboard.SampleWindow(71, 100), windowAt(100));
  }

  @Test
  void statusSampleSnapshotsChangingIndicatorsWithTheirContractUnits() {
    var status = new ServiceStatusImpl();
    status.setOperational(true);
    status.setHealthPercentage(80);
    status.setLoadPercentage(375);
    status.setMemoryUsedBytes(256L * 1024 * 1024);
    status.setMemoryAvailableBytes(768L * 1024 * 1024);

    var first = ServiceDashboard.StatusSample.from(status);

    status.setOperational(false);
    status.setHealthPercentage(25);
    status.setLoadPercentage(120);
    status.setMemoryUsedBytes(512L * 1024 * 1024);
    status.setMemoryAvailableBytes(512L * 1024 * 1024);
    var second = ServiceDashboard.StatusSample.from(status);

    assertEquals(37.5, first.loadPercentage());
    assertEquals(256, first.memoryUsedMb());
    assertEquals(768, first.memoryAvailableMb());
    assertEquals(80, first.healthPercentage());

    assertFalse(second.operational());
    assertEquals(12, second.loadPercentage());
    assertEquals(512, second.memoryUsedMb());
    assertEquals(512, second.memoryAvailableMb());
    assertEquals(25, second.healthPercentage());

    // The queued chart update retains the first notification's values even if
    // the service reuses and mutates the same status implementation.
    assertEquals(37.5, first.loadPercentage());
    assertEquals(256, first.memoryUsedMb());
  }

  @Test
  void statusQueuePreservesEveryNotificationInArrivalOrder() {
    var dispatched = new ArrayDeque<Runnable>();
    var samples = new ArrayList<ServiceDashboard.StatusSample>();
    var queue = new ServiceDashboard.StatusUpdateQueue(dispatched::add, samples::add);
    var status = new ServiceStatusImpl();

    status.setLoadPercentage(100);
    queue.accept(status);
    status.setLoadPercentage(100);
    queue.accept(status);
    status.setLoadPercentage(700);
    queue.accept(status);

    // Multiple producer calls use one UI task, but equal values are not collapsed:
    // they are required to render a flat interval in the chart.
    assertEquals(1, dispatched.size());
    dispatched.remove().run();

    assertEquals(3, samples.size());
    assertEquals(10, samples.get(0).loadPercentage());
    assertEquals(10, samples.get(1).loadPercentage());
    assertEquals(70, samples.get(2).loadPercentage());
  }

  private static ServiceDashboard.SampleWindow windowAt(int sample) {
    return ServiceDashboard.SampleWindow.endingAt(sample, 30);
  }
}
