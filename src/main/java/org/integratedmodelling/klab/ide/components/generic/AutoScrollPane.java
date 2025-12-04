package org.integratedmodelling.klab.ide.components.generic;

import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.geometry.Orientation;
import javafx.scene.Node;
import javafx.scene.layout.Pane;
import javafx.util.Duration;

import java.util.ArrayList;
import java.util.List;

/**
 * A JavaFX component that automatically scrolls a list of components with smooth transitions.
 * Supports both horizontal and vertical scrolling at a configurable speed.
 */
public class AutoScrollPane extends Pane {

  private final List<Node> components = new ArrayList<>();
  private final Orientation orientation;
  private double scrollSpeed; // pixels per second
  private Timeline scrollTimeline;
  private int currentIndex = 0;
  private double position = 0;
  private double targetPosition = 0;
  private static final double TRANSITION_DURATION_MS =
      500; // Duration of smooth transition in milliseconds

  /**
   * Creates a new AutoScrollPane with the specified orientation and scroll speed.
   *
   * @param orientation The scrolling orientation (HORIZONTAL or VERTICAL)
   * @param scrollSpeed The scrolling speed in pixels per second
   */
  public AutoScrollPane(Orientation orientation, double scrollSpeed) {
    this.orientation = orientation;
    this.scrollSpeed = scrollSpeed;

    // Set clip to prevent components from being visible outside the pane
    setClip(new javafx.scene.shape.Rectangle(0, 0, 1, 1)); // Will be updated in layoutChildren()

    // Initialize the scrolling timeline
    initializeScrollTimeline();
  }

  /**
   * Sets the components to be scrolled.
   *
   * @param components The list of components to scroll
   */
  public void setComponents(List<Node> components) {
    this.components.clear();
    this.components.addAll(components);

    // Add all components to the pane
    getChildren().clear();
    getChildren().addAll(components);

    // Position components based on orientation
    positionComponents();

    // Reset scrolling
    currentIndex = 0;
    position = 0;
    targetPosition = 0;

    // Start scrolling if we have components
    if (!components.isEmpty()) {
      startScrolling();
    }
  }

  /**
   * Adds a component to the scrolling list.
   *
   * @param component The component to add
   */
  public void addComponent(Node component) {
    components.add(component);
    getChildren().add(component);

    // Position the new component
    positionComponents();

    // Start scrolling if this is the first component
    if (components.size() == 1) {
      startScrolling();
    }
  }

  /** Positions all components based on the current orientation. */
  private void positionComponents() {
    double offset = 0;

    for (Node component : components) {
      if (orientation == Orientation.HORIZONTAL) {
        component.setLayoutX(offset);
        component.setLayoutY(0);
        offset += component.getBoundsInLocal().getWidth();
      } else {
        component.setLayoutX(0);
        component.setLayoutY(offset);
        offset += component.getBoundsInLocal().getHeight();
      }
    }
  }

  /** Initializes the scrolling timeline. */
  private void initializeScrollTimeline() {
    scrollTimeline =
        new Timeline(
            new KeyFrame(
                Duration.millis(16),
                event -> { // ~60 FPS
                  updateScroll();
                }));
    scrollTimeline.setCycleCount(Animation.INDEFINITE);
  }

  /** Starts the scrolling animation. */
  public void startScrolling() {
    if (!components.isEmpty() && !scrollTimeline.getStatus().equals(Animation.Status.RUNNING)) {
      scrollTimeline.play();
    }
  }

  /** Stops the scrolling animation. */
  public void stopScrolling() {
    scrollTimeline.stop();
  }

  /** Updates the scroll position and handles transitions between components. */
  private void updateScroll() {
    if (components.isEmpty()) {
      return;
    }

    // Calculate the size of the current component
    double currentSize;
    if (orientation == Orientation.HORIZONTAL) {
      currentSize = components.get(currentIndex).getBoundsInLocal().getWidth();
    } else {
      currentSize = components.get(currentIndex).getBoundsInLocal().getHeight();
    }

    // Calculate the step size based on the scroll speed
    double step = scrollSpeed / 60.0; // 60 FPS

    // Continuous scrolling (negative for upward movement)
    position -= step;

    // If we've scrolled past the current component
    if (Math.abs(position) >= currentSize) {
      // Move the current component to the end
      Node currentComponent = components.remove(currentIndex);
      components.add(currentComponent);
      position = 0;
    }

    // Apply the scroll position to all components
    double totalOffset = 0;
    for (int i = 0; i < components.size(); i++) {
      Node component = components.get(i);

      if (orientation == Orientation.HORIZONTAL) {
        component.setLayoutX(totalOffset + position);
        totalOffset += component.getBoundsInLocal().getWidth();
      } else {
        component.setLayoutY(totalOffset + position);
        totalOffset += component.getBoundsInLocal().getHeight();
      }
    }
  }

  /**
   * Sets the scroll speed.
   *
   * @param scrollSpeed The new scroll speed in pixels per second
   */
  public void setScrollSpeed(double scrollSpeed) {
    this.scrollSpeed = scrollSpeed;
  }

  /**
   * Gets the current scroll speed.
   *
   * @return The current scroll speed in pixels per second
   */
  public double getScrollSpeed() {
    return scrollSpeed;
  }

  /**
   * Gets the current orientation.
   *
   * @return The current orientation (HORIZONTAL or VERTICAL)
   */
  public Orientation getOrientation() {
    return orientation;
  }

  @Override
  protected void layoutChildren() {
    super.layoutChildren();

    // Update the clip to match the pane's size
    setClip(new javafx.scene.shape.Rectangle(0, 0, getWidth(), getHeight()));
  }
}
