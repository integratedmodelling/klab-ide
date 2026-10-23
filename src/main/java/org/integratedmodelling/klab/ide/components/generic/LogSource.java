package org.integratedmodelling.klab.ide.components.generic;

import org.integratedmodelling.klab.api.services.logging.LogPage;

/** Bounded paged source; UI code performs calls off the JavaFX thread. */
public interface LogSource extends AutoCloseable {
  LogPage poll();
  /** Explicit reset after expiry, rotation or a new authenticated service selection. */
  void reset();
  @Override default void close() {}
}
