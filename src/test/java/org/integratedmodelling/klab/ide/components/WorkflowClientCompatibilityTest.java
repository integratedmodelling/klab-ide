package org.integratedmodelling.klab.ide.components;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.integratedmodelling.common.services.client.ResourcesClient;
import org.integratedmodelling.common.services.client.ResourcesMerger;
import org.integratedmodelling.klab.api.services.ResourcesService;
import org.junit.jupiter.api.Test;

/** Validate the actual IDE dependency classpath, not only same-reactor Services source. */
class WorkflowClientCompatibilityTest {
  @Test void packagedResourcesClientsImplementThePilotApiInsteadOfUnsupportedDefaults() throws Exception {
    for (var implementation : new Class<?>[] {ResourcesClient.class, ResourcesMerger.class}) {
      for (var api : ResourcesService.class.getDeclaredMethods()) {
        if (!api.isDefault() || !(api.getName().contains("Flow") || api.getName().contains("Review")
            || api.getName().equals("reviewCycle"))) continue;
        var method = implementation.getMethod(api.getName(), api.getParameterTypes());
        assertEquals(implementation, method.getDeclaringClass(),
            () -> "Incompatible IDE Resources dependency: " + implementation.getName() + " inherits " + api
                + " instead of implementing it. Loaded common artifact: " + source(implementation)
                + "; API artifact: " + source(ResourcesService.class)
                + ". Build/install matching Services API and common artifacts into the IDE's selected Maven repository,"
                + " then rerun tests and package the IDE. Do not ship an API-only update with an older common jar.");
      }
    }
  }

  private static String source(Class<?> type) {
    var location = type.getProtectionDomain().getCodeSource();
    return location == null ? "unknown" : location.getLocation().toString();
  }
}
