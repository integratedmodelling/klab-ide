package org.integratedmodelling.klab.ide;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Supplier;

/** Runs the blocking portion of IDE startup on a dedicated virtual thread. */
final class StartupInitialization {

  private static final Executor EXECUTOR =
      task -> Thread.ofVirtual().name("klab-ide-initialization").start(task);

  private StartupInitialization() {}

  static <T> CompletableFuture<T> run(Supplier<T> initialization) {
    return CompletableFuture.supplyAsync(initialization, EXECUTOR);
  }
}
