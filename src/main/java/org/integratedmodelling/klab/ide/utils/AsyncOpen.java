package org.integratedmodelling.klab.ide.utils;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;
import javafx.application.Platform;

/** Coalesces opening work by target; cancellation prevents queued work and stale UI delivery. */
public final class AsyncOpen<K, T> {
  private static final class Request { volatile boolean cancelled; }
  private final ConcurrentHashMap<K, Request> pending = new ConcurrentHashMap<>();
  private final Executor worker;
  private final Executor ui;

  public AsyncOpen() { this(command -> Thread.startVirtualThread(command), Platform::runLater); }
  public AsyncOpen(Executor worker, Executor ui) { this.worker = worker; this.ui = ui; }

  public Set<K> pendingKeys() { return Set.copyOf(pending.keySet()); }

  public boolean open(K key, Supplier<T> load, BooleanSupplier current,
      Consumer<T> success, Consumer<Throwable> failure, Runnable settled) {
    var request = new Request();
    if (pending.putIfAbsent(key, request) != null) return false;
    Runnable background = () -> {
      if (request.cancelled) return;
      T result = null;
      Throwable error = null;
      try { result = load.get(); } catch (Throwable caught) { error = caught; }
      var value = result;
      var problem = error;
      ui.execute(() -> {
        if (!pending.remove(key, request)) return;
        try {
          if (request.cancelled || !current.getAsBoolean()) return;
          if (problem == null) success.accept(value); else failure.accept(problem);
        } finally { settled.run(); }
      });
    };
    try { worker.execute(background); }
    catch (RuntimeException error) {
      ui.execute(() -> {
        if (!pending.remove(key, request)) return;
        try { if (!request.cancelled && current.getAsBoolean()) failure.accept(error); }
        finally { settled.run(); }
      });
    }
    return true;
  }

  public void cancel(K key) {
    var request = pending.remove(key);
    if (request != null) request.cancelled = true;
  }

  public void cancelAll() { pending.keySet().forEach(this::cancel); }
}
