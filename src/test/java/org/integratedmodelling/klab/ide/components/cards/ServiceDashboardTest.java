package org.integratedmodelling.klab.ide.components.cards;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.EnumSet;
import org.integratedmodelling.klab.api.authentication.CRUDOperation;
import org.integratedmodelling.klab.api.data.Version;
import org.integratedmodelling.klab.api.services.KlabService;
import org.integratedmodelling.klab.api.services.impl.AbstractServiceCapabilities;
import org.integratedmodelling.klab.api.services.impl.ServiceStatusImpl;
import org.integratedmodelling.klab.api.services.runtime.extension.Extensions;
import org.junit.jupiter.api.Test;

class ServiceDashboardTest {

  @Test
  void serviceSettingsRequireAdvertisedAdministerPermission() {
    var capabilities =
        new AbstractServiceCapabilities() {
          @Override
          public KlabService.Type getType() {
            return KlabService.Type.RUNTIME;
          }
        };

    assertFalse(ServiceDashboard.hasAdministerPermission(null));
    assertFalse(ServiceDashboard.hasAdministerPermission(capabilities));
    capabilities.setPermissions(EnumSet.of(CRUDOperation.READ, CRUDOperation.ADMINISTER));
    assertTrue(ServiceDashboard.hasAdministerPermission(capabilities));
  }

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

  @Test
  void componentCardStateShowsHostedAndDependencyProvenance() {
    var hosted =
        component(
            Extensions.ComponentImportType.MAVEN,
            Extensions.ComponentUpdateStatus.UPDATE_AVAILABLE,
            "resources-1",
            "org.example:test:1.0-SNAPSHOT",
            2000L);
    var dependency =
        component(
            Extensions.ComponentImportType.DEPENDENCY,
            Extensions.ComponentUpdateStatus.UP_TO_DATE,
            "resources-1",
            null,
            1000L);
    var file =
        component(
            Extensions.ComponentImportType.FILE,
            Extensions.ComponentUpdateStatus.UP_TO_DATE,
            "resources-1",
            null,
            1000L);

    var hostedState = ServiceDashboard.componentCardState(hosted, "resources-1");
    var dependencyState = ServiceDashboard.componentCardState(dependency, "runtime-1");
    var fileState = ServiceDashboard.componentCardState(file, "resources-1");

    assertEquals("Hosted Maven import", hostedState.provenanceText());
    assertEquals("org.example:test:1.0-SNAPSHOT", hostedState.sourceText());
    assertEquals("Update available", hostedState.updateStatusText());
    assertTrue(hostedState.updateEnabled());
    assertTrue(hostedState.removalEnabled());

    assertEquals(
        "Dependency imported from a Resources service", dependencyState.provenanceText());
    assertEquals("Source: resources-1", dependencyState.sourceText());
    assertFalse(dependencyState.updateEnabled());
    assertEquals("Hosted .kar import", fileState.provenanceText());
    assertEquals("Up to date", fileState.updateStatusText());
  }

  @Test
  void builtInComponentActionsRemainDisabled() {
    var state =
        ServiceDashboard.componentCardState(
            component(
                Extensions.ComponentImportType.BUILT_IN,
                Extensions.ComponentUpdateStatus.NOT_UPDATEABLE,
                "service-1",
                null,
                0L),
            "service-1");

    assertEquals("Built into this service", state.provenanceText());
    assertEquals("Not updateable", state.updateStatusText());
    assertFalse(state.updateEnabled());
    assertFalse(state.removalEnabled());
  }

  private Extensions.ComponentDescriptor component(
      Extensions.ComponentImportType importType,
      Extensions.ComponentUpdateStatus updateStatus,
      String sourceServiceId,
      String mavenCoordinates,
      long latestTimestamp) {
    return new Extensions.ComponentDescriptor(
        "test.component",
        Version.create("1.0.0"),
        "Test component",
        null,
        null,
        mavenCoordinates,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        sourceServiceId,
        1000L,
        importType,
        updateStatus,
        latestTimestamp);
  }

  private static ServiceDashboard.SampleWindow windowAt(int sample) {
    return ServiceDashboard.SampleWindow.endingAt(sample, 30);
  }
}
