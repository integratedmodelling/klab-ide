package org.integratedmodelling.klab.ide.components.generic;

import atlantafx.base.theme.Styles;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Supplier;
import javafx.application.Platform;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.css.PseudoClass;
import javafx.scene.control.Button;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import org.kordamp.ikonli.material2.Material2AL;
import org.kordamp.ikonli.material2.Material2MZ;

/**
 * A button with an explicit lifecycle for work that may take time.
 *
 * <p>A {@code WaitButton} is initially {@link State#READY}. While work is running it is
 * {@link State#WAITING}, ignores additional clicks, and displays an indeterminate progress
 * indicator. Completion leaves a success or failure icon visible until the next invocation or
 * {@link #reset()}.
 *
 * <p>There are three supported ways to manage the work:
 *
 * <ul>
 *   <li>{@link #setOnAction(Supplier)} runs blocking work on a shared background executor.
 *   <li>{@link #setOnActionAsync(Supplier)} observes an already asynchronous operation until its
 *       returned stage completes.
 *   <li>{@link #showWaiting()}, {@link #showSucceeded()}, and {@link #showFailed()} expose the same
 *       lifecycle for work managed by another component.
 * </ul>
 *
 * <p>The component and the externally managed lifecycle methods follow the normal JavaFX rule and
 * must be used on the FX application thread. Completion of configured actions is automatically
 * marshalled back to that thread.
 */
public class WaitButton extends Button {

  public enum State {
    READY,
    WAITING,
    SUCCEEDED,
    FAILED
  }

  private static final PseudoClass WAITING_PSEUDO_CLASS = PseudoClass.getPseudoClass("waiting");
  private static final PseudoClass SUCCEEDED_PSEUDO_CLASS = PseudoClass.getPseudoClass("succeeded");
  private static final PseudoClass FAILED_PSEUDO_CLASS = PseudoClass.getPseudoClass("failed");
  private static final ExecutorService ACTION_EXECUTOR =
      Executors.newCachedThreadPool(
          runnable -> {
            var thread = new Thread(runnable, "wait-button-action");
            thread.setDaemon(true);
            return thread;
          });

  private final StackPane graphicContainer;
  private final ProgressIndicator progressIndicator;
  private final IconLabel arrowIcon;
  private final IconLabel successIcon;
  private final IconLabel errorIcon;
  private final ReadOnlyObjectWrapper<State> state =
      new ReadOnlyObjectWrapper<>(this, "state", State.READY);

  private Supplier<Boolean> action;
  private Supplier<? extends CompletionStage<Boolean>> asyncAction;

  public WaitButton(String text) {
    this(text, 16);
  }

  public WaitButton(String text, int size) {
    super(text);

    arrowIcon = new IconLabel(Material2MZ.NAVIGATE_NEXT, size, Color.GRAY);
    successIcon = new IconLabel(Material2AL.CHECK_CIRCLE, size, Color.GREEN);
    errorIcon = new IconLabel(Material2AL.ERROR, size, Color.RED);

    progressIndicator = new ProgressIndicator(ProgressIndicator.INDETERMINATE_PROGRESS);
    progressIndicator.setMinSize(size + 8, size + 8);
    progressIndicator.setPrefSize(size + 8, size + 8);
    progressIndicator.setMaxSize(size + 8, size + 8);
    progressIndicator.setMouseTransparent(true);
    Styles.addStyleClass(progressIndicator, Styles.ACCENT);

    // Keep every state graphic in one attached container. In particular, an initially invisible
    // ProgressIndicator must be attached to the scene graph so its skin and animation are ready
    // when the state changes to WAITING.
    graphicContainer = new StackPane(arrowIcon, successIcon, errorIcon, progressIndicator);
    graphicContainer.setMinSize(size + 8, size + 8);
    graphicContainer.setPrefSize(size + 8, size + 8);
    setGraphic(graphicContainer);
    setContentDisplay(ContentDisplay.RIGHT);
    setGraphicTextGap(5);
    getStyleClass().add("wait-button");

    state.addListener((observable, oldState, newState) -> render(newState));
    render(State.READY);

    super.setOnAction(
        event -> {
          if (getState() != State.WAITING) {
            executeConfiguredAction();
          }
        });
  }

  /**
   * Configures blocking work. The supplier is invoked on a background thread and its Boolean result
   * determines the terminal state; {@code null}, an exception, and {@code false} all mean failure.
   */
  public void setOnAction(Supplier<Boolean> action) {
    this.action = Objects.requireNonNull(action, "action");
    this.asyncAction = null;
  }

  /**
   * Configures non-blocking work. The supplier itself is invoked on the FX thread and must return
   * promptly; the button remains waiting until the returned stage completes.
   */
  public void setOnActionAsync(Supplier<? extends CompletionStage<Boolean>> action) {
    this.asyncAction = Objects.requireNonNull(action, "action");
    this.action = null;
  }

  public State getState() {
    return state.get();
  }

  public ReadOnlyObjectProperty<State> stateProperty() {
    return state.getReadOnlyProperty();
  }

  public boolean isWaiting() {
    return getState() == State.WAITING;
  }

  /** Starts an externally managed wait phase. Must be called on the FX application thread. */
  public void showWaiting() {
    setState(State.WAITING);
  }

  /** Completes an externally managed wait phase successfully. */
  public void showSucceeded() {
    setState(State.SUCCEEDED);
  }

  /** Completes an externally managed wait phase unsuccessfully. */
  public void showFailed() {
    setState(State.FAILED);
  }

  /** Restores the initial, interactive state. */
  public void reset() {
    setState(State.READY);
  }

  /** Compatibility helper equivalent to {@link #setText(String)}. */
  public void updateText(String text) {
    setText(text);
  }

  /**
   * Retained for source compatibility. Actions now use a shared daemon executor, so instances own
   * no executor that requires shutdown.
   */
  @Deprecated(forRemoval = false)
  public void shutdown() {}

  private void executeConfiguredAction() {
    if (action == null && asyncAction == null) {
      return;
    }

    showWaiting();
    CompletionStage<Boolean> completion;
    try {
      completion =
          asyncAction == null
              ? CompletableFuture.supplyAsync(action, ACTION_EXECUTOR)
              : asyncAction.get();
      if (completion == null) {
        completion = CompletableFuture.completedFuture(false);
      }
    } catch (RuntimeException exception) {
      completion = CompletableFuture.failedFuture(exception);
    }

    completion.whenComplete(
        (succeeded, error) ->
            runOnFxThread(
                () ->
                    setState(
                        error == null && Boolean.TRUE.equals(succeeded)
                            ? State.SUCCEEDED
                            : State.FAILED)));
  }

  private void setState(State newState) {
    if (!Platform.isFxApplicationThread()) {
      throw new IllegalStateException("WaitButton state must be changed on the FX application thread");
    }
    state.set(Objects.requireNonNull(newState, "newState"));
  }

  private void render(State currentState) {
    var ready = currentState == State.READY;
    var waiting = currentState == State.WAITING;
    arrowIcon.setVisible(ready);
    successIcon.setVisible(currentState == State.SUCCEEDED);
    errorIcon.setVisible(currentState == State.FAILED);
    progressIndicator.setVisible(waiting);
    setMouseTransparent(waiting);

    pseudoClassStateChanged(WAITING_PSEUDO_CLASS, waiting);
    pseudoClassStateChanged(SUCCEEDED_PSEUDO_CLASS, currentState == State.SUCCEEDED);
    pseudoClassStateChanged(FAILED_PSEUDO_CLASS, currentState == State.FAILED);
  }

  private static void runOnFxThread(Runnable runnable) {
    if (Platform.isFxApplicationThread()) {
      runnable.run();
    } else {
      Platform.runLater(runnable);
    }
  }
}
