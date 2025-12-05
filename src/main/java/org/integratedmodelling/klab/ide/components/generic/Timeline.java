package org.integratedmodelling.klab.ide.components.generic;

import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Rectangle;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.Node;
import org.integratedmodelling.common.logging.Logging;
import org.integratedmodelling.klab.ide.components.Components;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * A JavaFX component that displays a timeline with alternating vertical sections of contrasting
 * gray colors. The timeline is configured with a start and end time (in milliseconds from epoch)
 * and a temporal resolution (time unit and multiplier). The end time can be changed, and the
 * background will update accordingly.
 */
public class Timeline extends Components.BaseComponent {

  private boolean initialized;

  /**
   * Enum representing the type of event on the timeline. Each type determines the icon that will be
   * displayed.
   */
  public enum EventType {
    /**
     * Temporal transition emitted by the scheduler. We only log the transitions that have caused
     * consequential changes in the knowledge graph.
     */
    TIME,
    /** Semantic event originating in a connected digital twin */
    EVENT_EXTERNAL,
    /** Event that requires attention */
    WARNING,
    /** Event that caused a problem */
    ERROR,
    /** Semantic event originating in the main digital twin */
    EVENT_INTERNAL
  }

  /** Class representing an event on the timeline. */
  public static class Event {
    private final long timestamp;
    private final EventType type;
    private final Consumer<Event> onClick;
    private Node icon;

    /**
     * Creates a new event with the specified timestamp, type, and click handler.
     *
     * @param timestamp The timestamp of the event in milliseconds from epoch
     * @param type The type of the event
     * @param onClick The consumer to call when the event is clicked
     */
    public Event(long timestamp, EventType type, Consumer<Event> onClick) {
      this.timestamp = timestamp;
      this.type = type;
      this.onClick = onClick;
    }

    /**
     * Gets the timestamp of the event.
     *
     * @return The timestamp in milliseconds from epoch
     */
    public long getTimestamp() {
      return timestamp;
    }

    /**
     * Gets the type of the event.
     *
     * @return The event type
     */
    public EventType getType() {
      return type;
    }

    /**
     * Gets the click handler for the event.
     *
     * @return The consumer to call when the event is clicked
     */
    public Consumer<Event> getOnClick() {
      return onClick;
    }

    /**
     * Sets the icon node for this event.
     *
     * @param icon The JavaFX node representing the icon
     */
    void setIcon(Node icon) {
      this.icon = icon;
    }

    /**
     * Gets the icon node for this event.
     *
     * @return The JavaFX node representing the icon
     */
    Node getIcon() {
      return icon;
    }
  }

  private long startTimeMs;
  private long endTimeMs;
  private TimeUnit timeUnit;
  private int multiplier;

  private double dragStartX;
  private long dragStartTimeMs;
  private long dragEndTimeMs;
  private boolean isDragging;

  private Pane timelinePane;
  private Label startTimeLabel;
  private Label endTimeLabel;

  private List<Event> events = new ArrayList<>();

  private static final Color LIGHT_GREY = Color.rgb(240, 240, 240);
  private static final Color DARK_GREY = Color.rgb(220, 220, 220);
  private static final Color DEFAULT_EVENT_COLOR = Color.MAGENTA;
  private static final double EVENT_VERTICAL_SPACING = 5.0;
  private static final DateTimeFormatter TIME_FORMATTER =
      DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());
  private static final double TIME_EVENT_BOTTOM_MARGIN = 10.0;

  /**
   * Creates a new Timeline component with the specified configuration.
   *
   * @param startTimeMs The start time in milliseconds from epoch
   * @param endTimeMs The end time in milliseconds from epoch
   * @param timeUnit The time unit for the temporal resolution
   * @param multiplier The multiplier for the temporal resolution
   */
  public Timeline(long startTimeMs, long endTimeMs, TimeUnit timeUnit, int multiplier) {
    super(Components.Type.Object, "Timeline", false);
    this.startTimeMs = startTimeMs;
    this.endTimeMs = endTimeMs;
    this.timeUnit = timeUnit;
    this.multiplier = multiplier;
    createContent();
  }

  @Override
  protected void createContent() {
    VBox container = new VBox(10);
    //    container.setPadding(new Insets(10));

    // Create the timeline pane
    timelinePane = new Pane();
    timelinePane.setPrefHeight(65);
    timelinePane.setMinHeight(65);
    timelinePane.setMaxHeight(65);
    HBox.setHgrow(timelinePane, Priority.ALWAYS);

    // Create time labels
    startTimeLabel = new Label(formatTime(startTimeMs));
    endTimeLabel = new Label(formatTime(endTimeMs));

    //    HBox labelsBox = new HBox();
    //    labelsBox.setSpacing(10);
    //    HBox.setHgrow(labelsBox, Priority.ALWAYS);
    //
    //    VBox startLabelBox = new VBox(startTimeLabel);
    //    startLabelBox.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
    //
    //    VBox endLabelBox = new VBox(endTimeLabel);
    //    endLabelBox.setAlignment(javafx.geometry.Pos.CENTER_RIGHT);
    //
    //    HBox.setHgrow(startLabelBox, Priority.ALWAYS);
    //    HBox.setHgrow(endLabelBox, Priority.ALWAYS);
    //
    //    labelsBox.getChildren().addAll(startLabelBox, endLabelBox);

    // Add mouse wheel handler for zoom
    timelinePane.setOnScroll(
        event -> {
          double delta = event.getDeltaY();
          long currentRange = endTimeMs - startTimeMs;
          long newEndTime;

          if (delta > 0) {
            // Zoom in - decrease range by 10%
            newEndTime = endTimeMs - (currentRange / 10);
          } else {
            // Zoom out - increase range by 10%
            newEndTime = endTimeMs + (currentRange / 10);
          }

          updateEndTime(newEndTime);
        });

    // Add mouse handlers for dragging
    timelinePane.setOnMousePressed(
        event -> {
          if (event.isPrimaryButtonDown()) {
            dragStartX = event.getX();
            dragStartTimeMs = startTimeMs;
            dragEndTimeMs = endTimeMs;
            isDragging = true;
          }
        });

    // TODO check the logics
    timelinePane.setOnMouseDragged(
        event -> {
          if (isDragging) {
            double dragDelta = event.getX() - dragStartX;
            double timePerPixel =
                (double) (dragEndTimeMs - dragStartTimeMs) / timelinePane.getWidth();
            long timeDelta = (long) (dragDelta * timePerPixel);
            Logging.INSTANCE.info("draggeroni");
            startTimeMs = dragStartTimeMs - timeDelta;
            endTimeMs = dragEndTimeMs - timeDelta;
            drawTimeline();
          }
        });

    timelinePane.setOnMouseReleased(
        event -> {
          isDragging = false;
        });

    // Add components to container
    container.getChildren().addAll(timelinePane /*, labelsBox*/);

    this.getChildren().add(container);

    // Draw the initial timeline
    drawTimeline();
  }

  /**
   * Updates the end time and redraws the timeline.
   *
   * @param newEndTimeMs The new end time in milliseconds from epoch
   */
  public void updateEndTime(long newEndTimeMs) {
    if (newEndTimeMs > startTimeMs) {
      this.endTimeMs = newEndTimeMs;
      endTimeLabel.setText(formatTime(endTimeMs));
      drawTimeline();
    }
  }

  /**
   * Inserts one or more events into the timeline. If an event has a timestamp beyond the timeline's
   * end time, the timeline's span is automatically extended.
   *
   * @param events The events to insert
   */
  public void insertEvents(Event... events) {
    for (Event event : events) {
      // If the event is beyond the end time, extend the timeline
      if (event.getTimestamp() > endTimeMs) {
        updateEndTime(event.getTimestamp());
      }

      this.events.add(event);
    }

    // Redraw the timeline to show the new events
    drawTimeline();
  }

  /**
   * Creates an icon for an event based on its type.
   *
   * @param event The event to create an icon for
   * @return The icon node
   */
  private Node createEventIcon(Event event) {
    Circle circle = new Circle(3);

    // Set the color based on the event type
    switch (event.getType()) {
      case EVENT_EXTERNAL:
        circle.setFill(Color.BLUE);
        break;
      case WARNING:
        circle.setFill(Color.ORANGE);
        break;
      case ERROR:
        circle.setFill(Color.RED);
        break;
      case EVENT_INTERNAL:
        circle.setFill(Color.GREEN);
        break;
      case TIME:
      default:
        circle.setFill(DEFAULT_EVENT_COLOR);
        // Position TIME events on the timeline
        circle.setLayoutY(timelinePane.getHeight() - TIME_EVENT_BOTTOM_MARGIN);
        break;
    }
    // Add a tooltip showing the event timestamp
    Tooltip tooltip = new Tooltip(formatTime(event.getTimestamp()));
    Tooltip.install(circle, tooltip);

    // Add click handler
    circle.setOnMouseClicked(
        e -> {
          if (event.getOnClick() != null) {
            event.getOnClick().accept(event);
          }
        });

    // Make the circle slightly larger when hovered
    circle.setOnMouseEntered(e -> circle.setRadius(7));
    circle.setOnMouseExited(e -> circle.setRadius(3));

    return circle;
  }

  /**
   * Draws the timeline with alternating vertical sections of contrasting grey colors. Also draws
   * any events that have been added to the timeline.
   */
  public void drawTimeline() {
    timelinePane.getChildren().clear();

    double width = timelinePane.getWidth();
    double height = timelinePane.getHeight();

    if (width <= 0) {
      // If the pane hasn't been laid out yet, set a listener to redraw when it is
      timelinePane
          .widthProperty()
          .addListener(
              (obs, oldVal, newVal) -> {
                if (newVal.doubleValue() > 0) {
                  drawTimeline();
                }
              });
      return;
    }

    // Calculate the duration in the specified time unit
    long durationMs = endTimeMs - startTimeMs;
    long durationInUnit = timeUnit.convert(durationMs, TimeUnit.MILLISECONDS);

    // Calculate the number of intervals
    int numIntervals = (int) Math.ceil((double) durationInUnit / multiplier);

    // Calculate the width of each interval
    double intervalWidth = width / numIntervals;

    // Draw the alternating sections
    for (int i = 0; i < numIntervals; i++) {
      Rectangle section = new Rectangle(i * intervalWidth, 0, intervalWidth, height);

      // Calculate interval start and end times
      long intervalStart = startTimeMs + (long) ((double) i * multiplier * timeUnit.toMillis(1));
      long intervalEnd =
          startTimeMs + (long) ((double) (i + 1) * multiplier * timeUnit.toMillis(1));

      // Create tooltip with interval information
      String tooltipText =
          String.format("From: %s%nTo: %s", formatTime(intervalStart), formatTime(intervalEnd));
      Tooltip.install(section, new Tooltip(tooltipText));

      // Alternate between light and dark grey
      section.setFill(i % 2 == 0 ? LIGHT_GREY : DARK_GREY);

      timelinePane.getChildren().add(section);

      // Draw horizontal line for TIME events
      Rectangle timeLine = new Rectangle(0, height - TIME_EVENT_BOTTOM_MARGIN, width, 1);
      timeLine.setFill(Color.LIGHTGREY);
      timelinePane.getChildren().add(timeLine);
    }

    // Draw the events
    for (Event event : events) {
      // Calculate the x position based on the event timestamp
      double position = ((double) (event.getTimestamp() - startTimeMs) / durationMs) * width;

      // Create the event icon if it doesn't exist
      if (event.getIcon() == null) {
        event.setIcon(createEventIcon(event));
      }

      Node icon = event.getIcon();

      // Find events at the same timestamp
      int eventsAtSameTime = 0;
      int currentEventIndex = 0;
      for (int i = 0; i < events.size(); i++) {
        if (events.get(i).getTimestamp() == event.getTimestamp()) {
          if (events.get(i) == event) {
            currentEventIndex = eventsAtSameTime;
          }
          eventsAtSameTime++;
        }
      }

      // Position the icon with vertical offset if needed
      icon.setLayoutX(position);
      if (event.getType() != EventType.TIME) {
        double baseOffset = 20; // Base distance from bottom
        double verticalSpacing = 10; // Space between events
        double proposedY = height - (baseOffset + (verticalSpacing * currentEventIndex));

        // Ensure the event doesn't go beyond the top of the timeline
        // Leave 5px margin from top
        if (proposedY < 10) {
          proposedY = 10;
        }

        icon.setLayoutY(proposedY);
      }

      // Add the icon to the timeline
      timelinePane.getChildren().add(icon);
    }
  }

  /**
   * Formats a time in milliseconds from epoch as a human-readable string.
   *
   * @param timeMs The time in milliseconds from epoch
   * @return A formatted time string
   */
  private String formatTime(long timeMs) {
    return TIME_FORMATTER.format(Instant.ofEpochMilli(timeMs));
  }

  /**
   * Gets the current start time in milliseconds from epoch.
   *
   * @return The start time
   */
  public long getStartTimeMs() {
    return startTimeMs;
  }

  /**
   * Gets the current end time in milliseconds from epoch.
   *
   * @return The end time
   */
  public long getEndTimeMs() {
    return endTimeMs;
  }

  /**
   * Gets the current time unit for the temporal resolution.
   *
   * @return The time unit
   */
  public TimeUnit getTimeUnit() {
    return timeUnit;
  }

  /**
   * Gets the current multiplier for the temporal resolution.
   *
   * @return The multiplier
   */
  public int getMultiplier() {
    return multiplier;
  }

  /**
   * Gets the list of events on the timeline.
   *
   * @return The list of events
   */
  public List<Event> getEvents() {
    return new ArrayList<>(events);
  }
}
