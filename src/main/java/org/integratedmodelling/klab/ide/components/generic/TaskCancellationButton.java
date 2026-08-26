package org.integratedmodelling.klab.ide.components.generic;

import atlantafx.base.theme.Styles;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import javafx.application.Platform;
import javafx.scene.control.Button;
import javafx.scene.control.Tooltip;
import javafx.util.Duration;
import org.kordamp.ikonli.fontawesome5.FontAwesomeSolid;

/** An ephemeral stop button whose visibility follows one exact asynchronous task. */
public class TaskCancellationButton extends Button {

  private final AtomicReference<CompletableFuture<?>> task = new AtomicReference<>();

  public TaskCancellationButton() {
    super("", new IconLabel(FontAwesomeSolid.STOP_CIRCLE, 14, "-color-danger-fg"));
    getStyleClass().addAll(Styles.FLAT, Styles.BUTTON_CIRCLE);
    var tooltip = new Tooltip("Stop the running observation");
    tooltip.setShowDelay(Duration.millis(200));
    setTooltip(tooltip);
    hideButton();
    setOnAction(event -> cancelTask());
  }

  /** Show this button for {@code future} until that exact future completes. */
  public void monitor(CompletableFuture<?> future) {
    if (future == null || future.isDone()) {
      return;
    }
    runOnFxThread(
        () -> {
          task.set(future);
          setDisable(false);
          setManaged(true);
          setVisible(true);
        });
    future.whenComplete(
        (result, failure) ->
            runOnFxThread(
                () -> {
                  if (task.compareAndSet(future, null)) {
                    hideButton();
                  }
                }));
  }

  public CompletableFuture<?> getTask() {
    return task.get();
  }

  private void cancelTask() {
    var current = task.get();
    if (current == null || current.isDone()) {
      return;
    }
    setDisable(true);
    CompletableFuture.runAsync(
        () -> {
          boolean cancelled;
          try {
            cancelled = current.cancel(true);
          } catch (Throwable failure) {
            cancelled = false;
          }
          if (!cancelled) {
            runOnFxThread(
                () -> {
                  if (task.get() == current && !current.isDone()) {
                    setDisable(false);
                  }
                });
          }
        });
  }

  private void hideButton() {
    setManaged(false);
    setVisible(false);
    setDisable(false);
  }

  private static void runOnFxThread(Runnable action) {
    if (Platform.isFxApplicationThread()) {
      action.run();
    } else {
      Platform.runLater(action);
    }
  }
}
