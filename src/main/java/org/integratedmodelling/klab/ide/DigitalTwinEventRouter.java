package org.integratedmodelling.klab.ide;

import java.util.List;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.function.Consumer;
import org.integratedmodelling.common.logging.Logging;
import org.integratedmodelling.klab.ide.api.DigitalTwinViewer;

/**
 * Thread-safe registry for the viewers attached to one digital twin.
 *
 * <p>Event ordering is owned by {@link IDEContextScope}; this class only provides stable snapshots
 * and isolates a failing viewer so it cannot prevent refreshes in the remaining views.
 */
final class DigitalTwinEventRouter {

  private final CopyOnWriteArraySet<DigitalTwinViewer> viewers = new CopyOnWriteArraySet<>();

  void add(DigitalTwinViewer viewer) {
    if (viewer != null) {
      viewers.add(viewer);
    }
  }

  void remove(DigitalTwinViewer viewer) {
    viewers.remove(viewer);
  }

  void dispatch(Consumer<DigitalTwinViewer> notification) {
    for (var viewer : viewers) {
      // A scope switch can remove a viewer while a CopyOnWriteArraySet snapshot is being walked.
      // Do not deliver the old scope's event after that viewer has rebound to another scope.
      if (!viewers.contains(viewer)) {
        continue;
      }
      try {
        notification.accept(viewer);
      } catch (RuntimeException e) {
        Logging.INSTANCE.warn(
            "Digital twin viewer "
                + viewer.getClass().getSimpleName()
                + " failed while processing an event: "
                + e.getMessage());
      }
    }
  }

  List<DigitalTwinViewer> drain() {
    var ret = List.copyOf(viewers);
    viewers.clear();
    return ret;
  }

  int size() {
    return viewers.size();
  }
}
