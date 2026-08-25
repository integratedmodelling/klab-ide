package org.integratedmodelling.klab.ide;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Supplier;
import org.integratedmodelling.klab.api.data.Version;
import org.integratedmodelling.klab.api.engine.distribution.Stack;

/** Runs the blocking portion of IDE startup on a dedicated virtual thread. */
final class StartupInitialization {

  private static final Executor EXECUTOR =
      task -> Thread.ofVirtual().name("klab-ide-initialization").start(task);

  private StartupInitialization() {}

  static <T> CompletableFuture<T> run(Supplier<T> initialization) {
    return CompletableFuture.supplyAsync(initialization, EXECUTOR);
  }

  static boolean isNetworkSynchronizationCandidate(boolean enabled, Stack.Tag current) {
    return enabled
        && current != null
        && current.version() != Version.HEAD
        && current.availableLocally()
        && !current.orphan();
  }

  static boolean hasAvailableUpdate(Stack.Status status) {
    return status != null && !status.downloadList().isEmpty();
  }
}
