package org.integratedmodelling.klab.ide.utils;

import java.awt.Toolkit;
import java.util.function.Consumer;
import javafx.animation.PauseTransition;
import javafx.event.EventHandler;
import javafx.scene.input.MouseEvent;
import javafx.util.Duration;

/**
 * Use as
 *
 * <pre>{@code
 *     Button button = new Button("Click Me");
 *     DoubleClickHandler handler = new DoubleClickHandler(
 *        () -> System.out.println("Single Click"),
 *        () -> System.out.println("Double Click")
 *      );
 * button.addEventHandler(MouseEvent.MOUSE_CLICKED, handler.getHandler());
 * }</pre>
 */
public class DoubleClickHandler<T> {
  private final PauseTransition delay;
  private final Consumer<T> onSingleClick;
  private final Consumer<T> onDoubleClick;
  private boolean firstClick = false;
  private T target;

  public DoubleClickHandler(T target, Consumer<T> onSingleClick, Consumer<T> onDoubleClick) {
    this.target = target;
    this.delay =
        new PauseTransition(
            Duration.millis(osDoubleClickInterval()));
    this.delay.setOnFinished(
        e -> {
          if (firstClick) { // Ensure it's still valid
            e.consume();
            onSingleClick.accept(target);
            firstClick = false;
          }
        });
    this.onSingleClick = onSingleClick;
    this.onDoubleClick = onDoubleClick;
  }

  private static int osDoubleClickInterval() {
    Object interval = Toolkit.getDefaultToolkit().getDesktopProperty("awt.multiClickInterval");
    return (interval instanceof Integer ms && ms > 0) ? ms : 500;
  }

  public EventHandler<MouseEvent> getHandler() {
    return event -> {
      delay.stop(); // Reset the timer on every click
      if (event.getClickCount() == 2) {
        event.consume();
        // A double-click event is fired; cancel single-click and execute double-click
        firstClick = false;
        onDoubleClick.accept(target);
      } else {
        // A single click: set the timer
        if (!firstClick) {
          event.consume();
          firstClick = true;
          delay.playFromStart();
        }
      }
    };
  }
}
