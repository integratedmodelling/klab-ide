package org.integratedmodelling.klab.ide.utils;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.Supplier;
import javafx.application.Platform;

/** Loads data off the UI thread; only the latest request may publish a result or error. */
public final class AsyncLoad<T> {
  private final Executor worker;
  private final Executor presentation;
  private final AtomicLong generation = new AtomicLong();

  public AsyncLoad() {
    this(command -> Thread.startVirtualThread(command), Platform::runLater);
  }

  public AsyncLoad(Executor worker, Executor presentation) {
    this.worker = worker;
    this.presentation = presentation;
  }

  public void invalidate() {
    generation.incrementAndGet();
  }

  public void load(Supplier<T> request, Consumer<T> success, Consumer<Throwable> failure) {
    long ticket = generation.incrementAndGet();
    CompletableFuture.supplyAsync(request, worker)
        .whenComplete(
            (result, error) ->
                presentation.execute(
                    () -> {
                      if (ticket != generation.get()) return;
                      if (error == null) success.accept(result);
                      else failure.accept(error.getCause() == null ? error : error.getCause());
                    }));
  }
}
