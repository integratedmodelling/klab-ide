package org.integratedmodelling.klab.ide;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.integratedmodelling.klab.api.engine.Engine;
import org.integratedmodelling.klab.api.scope.UserScope;
import org.integratedmodelling.klab.api.services.KlabService;
import org.integratedmodelling.klab.api.services.ResourcesService;
import org.junit.jupiter.api.Test;

class ServiceDashboardSelectionTest {
  @Test
  void includesAvailableRemoteServiceWithoutOperationalProvision() throws Exception {
    var controller = controller(List.of(), Engine.Status.ServiceProvision.INOPERATIVE);
    var remote = service("remote", false, true);
    controller.notifyServiceStatus(remote, remote.status());

    assertEquals(List.of(remote), services(controller));
  }

  @Test
  void excludesRemoteServiceAfterItBecomesUnavailable() throws Exception {
    var controller = controller(List.of(), Engine.Status.ServiceProvision.INOPERATIVE);
    var remote = service("remote", false, true);
    controller.notifyServiceStatus(remote, remote.status());
    assertEquals(List.of(remote), services(controller));

    when(remote.status().isAvailable()).thenReturn(false);
    controller.notifyServiceStatus(remote, remote.status());
    assertTrue(services(controller).isEmpty());
  }

  @Test
  void retainsLocalFirstAndOperationalServicesWithoutDuplicates() throws Exception {
    var operational = service("operational", false, true);
    when(operational.status().isOperational()).thenReturn(true);
    var controller = controller(List.of(operational),
        Engine.Status.ServiceProvision.LOCAL_INOP_REMOTE_SINGLE);
    var local = service("local", true, true);
    var remote = service("remote", false, true);
    var duplicate = service("operational", false, true);
    controller.notifyServiceStatus(local, local.status());
    controller.notifyServiceStatus(remote, remote.status());
    controller.notifyServiceStatus(duplicate, duplicate.status());

    assertEquals(List.of(local, operational, remote), services(controller));
  }

  @Test
  void ignoresStatusesFromOtherServiceTypes() throws Exception {
    var controller = controller(List.of(), Engine.Status.ServiceProvision.INOPERATIVE);
    var remote = service("remote", false, true);
    when(remote.status().getServiceType()).thenReturn(KlabService.Type.REASONER);
    controller.notifyServiceStatus(remote, remote.status());

    assertTrue(services(controller).isEmpty());
  }

  private static List<ResourcesService> services(KlabIDEController controller) {
    return controller.getServiceDashboardServices(ResourcesService.class, KlabService.Type.RESOURCES);
  }

  private static KlabIDEController controller(List<ResourcesService> operational,
      Engine.Status.ServiceProvision provision) throws Exception {
    var controller = spy(new KlabIDEController());
    var user = mock(UserScope.class);
    doReturn(user).when(controller).user();
    when(user.getServices(ResourcesService.class)).thenReturn(operational);
    var status = mock(Engine.Status.class);
    when(status.getServicesProvision()).thenReturn(Map.of(KlabService.Type.RESOURCES, provision));
    var field = KlabIDEController.class.getDeclaredField("engineStatus");
    field.setAccessible(true);
    field.set(controller, new AtomicReference<>(status));
    return controller;
  }

  private static ResourcesService service(String id, boolean local, boolean available)
      throws Exception {
    var service = mock(ResourcesService.class);
    var status = mock(KlabService.ServiceStatus.class);
    when(service.serviceId()).thenReturn(id);
    when(service.getUrl()).thenReturn(URI.create("https://" + id + ".example.org").toURL());
    when(service.isLocal()).thenReturn(local);
    when(service.status()).thenReturn(status);
    when(status.getServiceId()).thenReturn(id);
    when(status.getServiceType()).thenReturn(KlabService.Type.RESOURCES);
    when(status.isAvailable()).thenReturn(available);
    return service;
  }
}
