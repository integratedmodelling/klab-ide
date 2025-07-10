package org.integratedmodelling.klab.ide.components;

import atlantafx.base.controls.Card;
import javafx.geometry.Insets;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Spinner;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import java.time.Instant;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * A demo component that showcases the Timeline component with various configurations.
 */
public class TimelineDemo extends Components.BaseComponent {

    private Timeline timeline;
    private ComboBox<TimeUnit> timeUnitComboBox;
    private Spinner<Integer> multiplierSpinner;
    private Button updateButton;

    // Event creation controls
    private TextField eventTimestampField;
    private ComboBox<Timeline.EventType> eventTypeComboBox;
    private Button addEventButton;
    private Button redrawButton;
    private Button addFutureEventButton;

    public TimelineDemo() {
        super(Components.Type.Object, "Timeline Demo", true);
    }

    @Override
    protected void createContent() {
        var card = new Card();
        VBox content = new VBox(20);
        content.setPadding(new Insets(20));

        // Create a description label
        Label descriptionLabel = new Label(
                "This demo showcases the Timeline component, which displays a timeline with alternating vertical sections " +
                "of contrasting grey colors. The timeline is configured with a start and end time (in milliseconds from epoch) " +
                "and a temporal resolution (time unit and multiplier). The end time can be changed using the slider below " +
                "the timeline, and the background will update accordingly. You can also add events to the timeline, " +
                "which will be displayed as colored dots. Clicking on an event will show a message.");
        descriptionLabel.setWrapText(true);

        // Create default start and end times (current time and 1 hour later)
        long currentTimeMs = System.currentTimeMillis();
        long oneHourLaterMs = currentTimeMs + 3600000; // 1 hour in milliseconds

        // Create the timeline component
        timeline = new Timeline(currentTimeMs, oneHourLaterMs, TimeUnit.MINUTES, 1);

        // Add a sample event in the middle of the timeline
        long middleTime = currentTimeMs + (oneHourLaterMs - currentTimeMs) / 2;

        Consumer<Timeline.Event> clickHandler = event -> {
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle("Sample Event Clicked");
            alert.setHeaderText("Sample Event");
            alert.setContentText("This is a sample event added automatically to demonstrate the timeline functionality.");
            alert.showAndWait();
        };

        Timeline.Event sampleEvent = new Timeline.Event(middleTime, Timeline.EventType.EVENT_EXTERNAL, clickHandler);
        timeline.insertEvents(sampleEvent);

        // Create controls for configuring the timeline
        Label timeUnitLabel = new Label("Time Unit:");
        timeUnitComboBox = new ComboBox<>();
        timeUnitComboBox.getItems().addAll(
                TimeUnit.SECONDS,
                TimeUnit.MINUTES,
                TimeUnit.HOURS,
                TimeUnit.DAYS
        );
        timeUnitComboBox.setValue(TimeUnit.MINUTES);

        Label multiplierLabel = new Label("Multiplier:");
        multiplierSpinner = new Spinner<>(1, 60, 5);
        multiplierSpinner.setEditable(true);

        updateButton = new Button("Update Configuration");
        updateButton.setOnAction(e -> updateTimelineConfiguration());

        // Create layout for timeline configuration controls
        HBox timelineConfigBox = new HBox(10,
                timeUnitLabel, timeUnitComboBox,
                multiplierLabel, multiplierSpinner,
                updateButton);
        timelineConfigBox.setPadding(new Insets(10, 0, 0, 0));

        // Create controls for adding events
        Label eventSectionLabel = new Label("Add Events to Timeline");
        eventSectionLabel.setStyle("-fx-font-weight: bold;");

        Label eventTimestampLabel = new Label("Event Timestamp (ms from epoch):");
        eventTimestampField = new TextField(String.valueOf(currentTimeMs + 1800000)); // Default to 30 minutes from now
        eventTimestampField.setPrefWidth(150);

        redrawButton = new Button("Redraw");
        redrawButton.setOnAction(e -> timeline.drawTimeline());

        Label eventTypeLabel = new Label("Event Type:");
        eventTypeComboBox = new ComboBox<>();
        eventTypeComboBox.getItems().addAll(Timeline.EventType.values());
        eventTypeComboBox.setValue(Timeline.EventType.TIME);

        addEventButton = new Button("Add Event");
        addEventButton.setOnAction(e -> addEventToTimeline());

        addFutureEventButton = new Button("Add Future Event");
        addFutureEventButton.setOnAction(e -> addFutureEventToTimeline());

        // Create layout for event controls
        GridPane eventControlsGrid = new GridPane();
        eventControlsGrid.setHgap(10);
        eventControlsGrid.setVgap(10);
        eventControlsGrid.setPadding(new Insets(10, 0, 0, 0));

        eventControlsGrid.add(eventTimestampLabel, 0, 0);
        eventControlsGrid.add(eventTimestampField, 1, 0);
        eventControlsGrid.add(eventTypeLabel, 0, 1);
        eventControlsGrid.add(eventTypeComboBox, 1, 1);

        HBox eventButtonsBox = new HBox(10, addEventButton, addFutureEventButton, redrawButton);
        eventControlsGrid.add(eventButtonsBox, 1, 2);

        // Add components to content
        content.getChildren().addAll(
                descriptionLabel,
                timeline,
                timelineConfigBox,
                eventSectionLabel,
                eventControlsGrid
        );

        card.setBody(content);
        this.getChildren().add(card);
    }

    /**
     * Updates the timeline configuration based on the selected time unit and multiplier.
     * Preserves any events that were added to the previous timeline.
     */
    private void updateTimelineConfiguration() {
        // Create a new timeline with the current start and end times but new time unit and multiplier
        long startTimeMs = timeline.getStartTimeMs();
        long endTimeMs = timeline.getEndTimeMs();
        TimeUnit timeUnit = timeUnitComboBox.getValue();
        int multiplier = multiplierSpinner.getValue();

        // Replace the current timeline with a new one
        VBox parent = (VBox) timeline.getParent();
        int index = parent.getChildren().indexOf(timeline);

        // Create a new timeline with the updated configuration
        Timeline newTimeline = new Timeline(startTimeMs, endTimeMs, timeUnit, multiplier);

        // Replace the old timeline with the new one
        parent.getChildren().remove(index);
        parent.getChildren().add(index, newTimeline);

        // Update the reference
        timeline = newTimeline;
    }

    /**
     * Adds an event to the timeline based on the current input values.
     */
    private void addEventToTimeline() {
        try {
            long timestamp = Long.parseLong(eventTimestampField.getText().trim());
            Timeline.EventType eventType = eventTypeComboBox.getValue();

            // Create an event with a click handler that shows a message
            Consumer<Timeline.Event> clickHandler = event -> {
                Alert alert = new Alert(Alert.AlertType.INFORMATION);
                alert.setTitle("Event Clicked");
                alert.setHeaderText("Event Information");
                alert.setContentText("Event of type " + event.getType() + 
                        " at time " + Instant.ofEpochMilli(event.getTimestamp()));
                alert.showAndWait();
            };

            Timeline.Event event = new Timeline.Event(timestamp, eventType, clickHandler);

            // Insert the event into the timeline
            timeline.insertEvents(event);

        } catch (NumberFormatException e) {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Invalid Input");
            alert.setHeaderText("Invalid Timestamp");
            alert.setContentText("Please enter a valid timestamp (milliseconds from epoch).");
            alert.showAndWait();
        }
    }

    /**
     * Adds a future event to the timeline (beyond the current end time).
     */
    private void addFutureEventToTimeline() {
        try {
            // Calculate a timestamp that is beyond the current end time
            long futureTimestamp = timeline.getEndTimeMs() + 1800000; // 30 minutes beyond end time
            Timeline.EventType eventType = eventTypeComboBox.getValue();

            // Create an event with a click handler that shows a message
            Consumer<Timeline.Event> clickHandler = event -> {
                Alert alert = new Alert(Alert.AlertType.INFORMATION);
                alert.setTitle("Future Event Clicked");
                alert.setHeaderText("Future Event Information");
                alert.setContentText("This event was added beyond the original timeline end time, " +
                        "causing the timeline to automatically extend.\n\n" +
                        "Event of type " + event.getType() + 
                        " at time " + Instant.ofEpochMilli(event.getTimestamp()));
                alert.showAndWait();
            };

            Timeline.Event event = new Timeline.Event(futureTimestamp, eventType, clickHandler);

            // Update the timestamp field to show the future timestamp
            eventTimestampField.setText(String.valueOf(futureTimestamp));

            // Insert the event into the timeline (this will automatically extend the timeline)
            timeline.insertEvents(event);

        } catch (Exception e) {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Error");
            alert.setHeaderText("Error Adding Future Event");
            alert.setContentText("An error occurred: " + e.getMessage());
            alert.showAndWait();
        }
    }

    /**
     * Creates a demo instance of the TimelineDemo component.
     *
     * @return A new TimelineDemo instance
     */
    public static TimelineDemo createDemo() {
        return new TimelineDemo();
    }
}
