package org.integratedmodelling.klab.ide.components.cards;

import java.lang.reflect.Array;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Consumer;
import javafx.beans.value.ChangeListener;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.OverrunStyle;
import javafx.scene.control.Tooltip;
import javafx.scene.image.ImageView;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import org.integratedmodelling.klab.api.geometry.Geometry;
import org.integratedmodelling.klab.api.geometry.Geometry.Dimension;
import org.integratedmodelling.klab.api.geometry.impl.GeometryImpl;
import org.integratedmodelling.klab.ide.components.generic.SatelliteImage;
import org.kordamp.ikonli.javafx.FontIcon;
import org.kordamp.ikonli.material2.Material2AL;

/**
 * Compact visualization for a k.LAB {@link Geometry}, focused on the spatial and temporal extents
 * that define observation scale and resolution.
 *
 * <p>TODO add on click (should check projection or alternatively, find the lat/lon bbox in
 *  metadata)
 *
 * <p>This will let you enter and display a bounding box on an OSM background:
 * https://linestrings.com/bbox/#12,52,14,53 (add bbox as url hash to share map with box)
 *
 * <p>Alternatively, I also found bboxfinder quite nice:
 * http://bboxfinder.com/#-16.636192,-69.433594,-1.581830,-51.503906
 *
 * <p>Timeline event marks can be supplied with {@link #setTimelineMarks(Collection)}. When a {@link
 * #setTimelineMarkClickHandler(Consumer)} handler is configured, clicking the timeline calls it with
 * the nearest visible mark to the left of the click position.
 */
public class GeometryCard extends BaseCard<Geometry> {

  private static final double DEFAULT_SIZE = 320;
  private static final double TIMELINE_HORIZONTAL_PADDING = 8;
  private static final double TIMELINE_BAR_Y = 13;
  private static final double TIMELINE_BAR_HEIGHT = 6;
  private static final DateTimeFormatter DATE_FORMATTER =
      DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(ZoneId.systemDefault());
  private static final DecimalFormat COORDINATE_FORMAT =
      new DecimalFormat("0.###", DecimalFormatSymbols.getInstance(Locale.US));

  private final List<Long> timelineMarks = new ArrayList<>();
  private Consumer<Long> timelineMarkClickHandler;
  private Canvas timelineCanvas;
  private StackPane timelineTrack;
  private TimeSummary timelineSummary;

  public GeometryCard(Geometry geometry) {
    this(geometry, false);
  }

  public GeometryCard(Geometry geometry, boolean extended) {
    super(geometry == null ? Geometry.EMPTY : geometry, null, extended, false);
    drawContent();
  }

  /**
   * Set visible event/change markers for the temporal extent.
   *
   * <p>Values are epoch milliseconds. Marks outside the visible temporal span are kept but not drawn
   * or selected until the card is used with a compatible geometry. Null values are ignored.
   */
  public void setTimelineMarks(Collection<Long> epochMilliseconds) {
    timelineMarks.clear();
    if (epochMilliseconds != null) {
      epochMilliseconds.stream()
          .filter(Objects::nonNull)
          .distinct()
          .sorted()
          .forEach(timelineMarks::add);
    }
    redrawTimeline();
  }

  /** Return the currently configured timeline marks as epoch milliseconds. */
  public List<Long> getTimelineMarks() {
    return List.copyOf(timelineMarks);
  }

  /**
   * Set the callback invoked when the user clicks on the timeline bar.
   *
   * <p>The callback receives the nearest visible mark at or to the left of the click. Clicks before
   * the first visible mark do not invoke the callback.
   */
  public void setTimelineMarkClickHandler(Consumer<Long> timelineMarkClickHandler) {
    this.timelineMarkClickHandler = timelineMarkClickHandler;
    updateTimelineInteractivity();
  }

  @Override
  protected void drawContent() {
    getStyleClass().add("geometry-card");
    setPadding(new Insets(8));
    setMinSize(210, 230);
    setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
    if (extended) {
      setPrefSize(Region.USE_COMPUTED_SIZE, Region.USE_COMPUTED_SIZE);
    } else {
      setPrefSize(DEFAULT_SIZE, DEFAULT_SIZE);
      widthProperty()
          .addListener(
              (obs, oldVal, newVal) -> {
                double val = newVal.doubleValue();
                if (val > 0 && Math.abs(val - getHeight()) > 1.0) {
                  setPrefHeight(val);
                }
              });
      heightProperty()
          .addListener(
              (obs, oldVal, newVal) -> {
                double val = newVal.doubleValue();
                if (val > 0 && Math.abs(val - getWidth()) > 1.0) {
                  setPrefWidth(val);
                }
              });
    }

    SpatialSummary spatial = SpatialSummary.from(asset.dimension(Dimension.Type.SPACE));
    TimeSummary temporal = TimeSummary.from(asset.dimension(Dimension.Type.TIME));

    VBox content = new VBox(7);
    content.getStyleClass().add("geometry-card-content");
    content.setFillWidth(true);

    Node spatialPreview = createSpatialPreview(spatial);
    Node temporalPreview = createTemporalPreview(temporal);
    FlowPane tags = createTags(spatial, temporal);

    content.getChildren().addAll(createHeader(), spatialPreview, temporalPreview, tags);
    VBox.setVgrow(spatialPreview, Priority.ALWAYS);

    setCenter(content);
  }

  private Node createHeader() {
    Button copyButton = createCopyButton();

    Label encoding = new Label(asset.encode());
    encoding.getStyleClass().add("geometry-card-code");
    encoding.setTextOverrun(OverrunStyle.CENTER_ELLIPSIS);
    encoding.setTooltip(new Tooltip(asset.encode()));
    HBox.setHgrow(encoding, Priority.ALWAYS);

    Label size = chip(sizeLabel(asset));
    size.setTooltip(new Tooltip("Total geometry size"));

    HBox header = new HBox(6, copyButton, encoding, size);
    header.setAlignment(Pos.CENTER_LEFT);
    header.setMinHeight(24);
    return header;
  }

  private Button createCopyButton() {
    FontIcon icon = new FontIcon(Material2AL.CONTENT_COPY);
    icon.setIconSize(13);
    icon.getStyleClass().add("geometry-card-copy-icon");

    Button copyButton = new Button(null, icon);
    copyButton.getStyleClass().add("geometry-card-copy");
    copyButton.setAccessibleText("Copy encoded geometry");
    copyButton.setTooltip(new Tooltip("Copy encoded geometry"));
    copyButton.setCursor(Cursor.HAND);
    copyButton.setFocusTraversable(false);
    copyButton.setMinSize(24, 24);
    copyButton.setPrefSize(24, 24);
    copyButton.setMaxSize(24, 24);
    copyButton.setOnAction(
        event -> {
          ClipboardContent content = new ClipboardContent();
          content.putString(asset.encode());
          Clipboard.getSystemClipboard().setContent(content);
        });
    return copyButton;
  }

  private Node createSpatialPreview(SpatialSummary summary) {
    StackPane frame = new StackPane();
    frame.getStyleClass().add("geometry-card-spatial");
    frame.setAlignment(Pos.CENTER);
    frame.setMinHeight(90);
    frame.setPrefHeight(178);
    frame.setMaxHeight(Double.MAX_VALUE);

    Canvas overlay = new Canvas();
    overlay.setManaged(false);
    overlay.setMouseTransparent(true);

    ImageView image = null;
    double imageAspect = 1.0;
    if (summary.canUseSatellite()) {
      BoundingBox mapBounds = summary.bounds().expandedToAspect(1.0, 0.18);
      imageAspect = mapBounds.aspectRatio();
      image =
          new SatelliteImage(
              mapBounds.minX(), mapBounds.minY(), mapBounds.maxX(), mapBounds.maxY(), 640, 640);
      image.setPreserveRatio(true);
      image.setSmooth(true);
      image.setManaged(false);
      image.setMouseTransparent(true);
      frame.getChildren().add(image);
    } else {
      frame.getStyleClass().add("geometry-card-spatial-empty");
    }

    ImageView finalImage = image;
    double finalImageAspect = imageAspect;
    ChangeListener<Number> redraw =
        (obs, oldValue, newValue) -> {
          layoutUnmanagedCanvas(overlay, frame);
          if (finalImage != null) {
            layoutSpatialImage(finalImage, frame, finalImageAspect);
          }
          drawSpatialOverlay(overlay, summary);
        };
    frame.widthProperty().addListener(redraw);
    frame.heightProperty().addListener(redraw);

    frame.getChildren().add(overlay);
    Tooltip.install(frame, new Tooltip(summary.tooltip()));
    return frame;
  }

  private void layoutUnmanagedCanvas(Canvas canvas, Region owner) {
    canvas.setWidth(Math.max(0, owner.getWidth()));
    canvas.setHeight(Math.max(0, owner.getHeight()));
  }

  private void layoutSpatialImage(ImageView image, Region frame, double aspectRatio) {
    Rect area =
        imageArea(Math.max(1, frame.getWidth()), Math.max(1, frame.getHeight()), aspectRatio);
    image.setFitWidth(area.width());
    image.setFitHeight(area.height());
    image.setLayoutX(area.x());
    image.setLayoutY(area.y());
  }

  private void drawSpatialOverlay(Canvas canvas, SpatialSummary summary) {
    GraphicsContext gc = canvas.getGraphicsContext2D();
    double width = canvas.getWidth();
    double height = canvas.getHeight();
    gc.clearRect(0, 0, width, height);
    if (width <= 2 || height <= 2) {
      return;
    }

    if (summary.bounds() == null) {
      drawSpatialPlaceholder(gc, width, height, summary);
      return;
    }

    BoundingBox mapBounds =
        summary.canUseSatellite() ? summary.bounds().expandedToAspect(1.0, 0.18) : summary.bounds();
    Rect imageArea = imageArea(width, height, mapBounds.aspectRatio());
    Rect box = mapBounds.project(summary.bounds(), imageArea);

    if (!summary.canUseSatellite()) {
      drawSpatialPlaceholder(gc, width, height, summary);
    }

    gc.setFill(Color.rgb(11, 18, 32, 0.18));
    gc.fillRect(imageArea.x(), imageArea.y(), imageArea.width(), box.y() - imageArea.y());
    gc.fillRect(
        imageArea.x(),
        box.y() + box.height(),
        imageArea.width(),
        imageArea.y() + imageArea.height() - (box.y() + box.height()));
    gc.fillRect(imageArea.x(), box.y(), box.x() - imageArea.x(), box.height());
    gc.fillRect(
        box.x() + box.width(),
        box.y(),
        imageArea.x() + imageArea.width() - (box.x() + box.width()),
        box.height());

    if (summary.isGrid()) {
      drawGrid(gc, box, summary.xCells(), summary.yCells());
    }

    gc.setStroke(Color.WHITE);
    gc.setLineWidth(3.0);
    gc.strokeRoundRect(box.x(), box.y(), box.width(), box.height(), 5, 5);
    gc.setStroke(Color.web("#256f5b"));
    gc.setLineWidth(1.4);
    gc.strokeRoundRect(box.x(), box.y(), box.width(), box.height(), 5, 5);
  }

  private void drawSpatialPlaceholder(
      GraphicsContext gc, double width, double height, SpatialSummary summary) {
    gc.setFill(Color.web("#eef2f5"));
    gc.fillRoundRect(0.5, 0.5, width - 1, height - 1, 7, 7);
    gc.setStroke(Color.web("#d4dbe3"));
    gc.setLineWidth(1);
    for (int i = 1; i < 5; i++) {
      double x = width * i / 5.0;
      double y = height * i / 5.0;
      gc.strokeLine(x, 0, x, height);
      gc.strokeLine(0, y, width, y);
    }

    String text = summary.dimensionLabel();
    gc.setFill(Color.web("#57606a"));
    gc.setFont(javafx.scene.text.Font.font("System", 10));
    gc.fillText(text, 10, Math.max(18, height - 12));
  }

  private void drawGrid(GraphicsContext gc, Rect box, long xCells, long yCells) {
    int xLines = (int) Math.min(12, Math.max(1, xCells));
    int yLines = (int) Math.min(12, Math.max(1, yCells));
    gc.setStroke(Color.rgb(255, 255, 255, 0.45));
    gc.setLineWidth(0.7);
    for (int i = 1; i < xLines; i++) {
      double x = box.x() + (box.width() * i / xLines);
      gc.strokeLine(x, box.y(), x, box.y() + box.height());
    }
    for (int i = 1; i < yLines; i++) {
      double y = box.y() + (box.height() * i / yLines);
      gc.strokeLine(box.x(), y, box.x() + box.width(), y);
    }
  }

  private Node createTemporalPreview(TimeSummary summary) {
    VBox box = new VBox(2);
    box.getStyleClass().add("geometry-card-time");
    box.setMinHeight(48);
    box.setPrefHeight(54);
    box.setMaxHeight(62);

    StackPane track = new StackPane();
    track.setMinHeight(30);
    track.setPrefHeight(30);
    track.setMaxHeight(30);
    track.setPickOnBounds(true);

    Canvas canvas = new Canvas();
    canvas.setManaged(false);
    canvas.setMouseTransparent(true);
    timelineTrack = track;
    timelineCanvas = canvas;
    timelineSummary = summary;
    ChangeListener<Number> redraw =
        (obs, oldValue, newValue) -> {
          redrawTimeline();
        };
    track.widthProperty().addListener(redraw);
    track.heightProperty().addListener(redraw);
    track.setOnMouseClicked(
        event -> {
          Long mark = nearestTimelineMarkToLeft(event.getX());
          if (mark != null && timelineMarkClickHandler != null) {
            timelineMarkClickHandler.accept(mark);
            event.consume();
          }
        });
    track.getChildren().add(canvas);

    Label start = caption(summary.startLabel());
    Label resolution = caption(summary.resolutionLabel());
    resolution.setAlignment(Pos.CENTER);
    Label end = caption(summary.endLabel());
    end.setAlignment(Pos.CENTER_RIGHT);
    HBox.setHgrow(start, Priority.ALWAYS);
    HBox.setHgrow(resolution, Priority.ALWAYS);
    HBox.setHgrow(end, Priority.ALWAYS);

    HBox labels = new HBox(4, start, resolution, end);
    labels.setAlignment(Pos.CENTER);
    box.getChildren().addAll(track, labels);
    Tooltip.install(box, new Tooltip(summary.tooltip()));
    updateTimelineInteractivity();
    return box;
  }

  private void redrawTimeline() {
    if (timelineCanvas == null || timelineTrack == null || timelineSummary == null) {
      return;
    }
    layoutUnmanagedCanvas(timelineCanvas, timelineTrack);
    drawTimeline(timelineCanvas, timelineSummary);
    updateTimelineInteractivity();
  }

  private void drawTimeline(Canvas canvas, TimeSummary summary) {
    GraphicsContext gc = canvas.getGraphicsContext2D();
    double width = canvas.getWidth();
    double height = canvas.getHeight();
    gc.clearRect(0, 0, width, height);
    if (width <= 2 || height <= 2) {
      return;
    }

    double left = TIMELINE_HORIZONTAL_PADDING;
    double right = width - TIMELINE_HORIZONTAL_PADDING;
    double trackWidth = Math.max(1, right - left);
    double y = TIMELINE_BAR_Y;
    double trackHeight = TIMELINE_BAR_HEIGHT;

    gc.setFill(Color.web("#e6e8ec"));
    gc.fillRoundRect(left, y, trackWidth, trackHeight, 6, 6);

    if (!summary.present()) {
      gc.setStroke(Color.web("#9aa4ad"));
      gc.setLineWidth(1.2);
      gc.setLineDashes(3, 4);
      gc.strokeLine(left, y + trackHeight / 2.0, right, y + trackHeight / 2.0);
      gc.setLineDashes();
      return;
    }

    gc.setFill(Color.web("#2f7f6f"));
    gc.fillRoundRect(left, y, trackWidth, trackHeight, 6, 6);

    if (summary.hasLocatedSpan()) {
      drawTransitionTicks(gc, summary, left, right, y, trackHeight);
      drawTimelineMarks(gc, summary, left, right, y, trackHeight);
    } else {
      gc.setStroke(Color.web("#2f7f6f"));
      gc.setLineWidth(1.2);
      gc.setLineDashes(4, 4);
      gc.strokeLine(
          left + trackWidth * 0.15,
          y + trackHeight + 6,
          right - trackWidth * 0.15,
          y + trackHeight + 6);
      gc.setLineDashes();
    }

    gc.setFill(Color.WHITE);
    gc.fillOval(left - 3, y - 2, 10, 10);
    gc.fillOval(right - 7, y - 2, 10, 10);
    gc.setStroke(Color.web("#2f7f6f"));
    gc.setLineWidth(1.2);
    gc.strokeOval(left - 3, y - 2, 10, 10);
    gc.strokeOval(right - 7, y - 2, 10, 10);
  }

  private void drawTransitionTicks(
      GraphicsContext gc, TimeSummary summary, double left, double right, double y, double height) {
    long duration = summary.end() - summary.start();
    if (duration <= 0) {
      return;
    }

    gc.setStroke(Color.rgb(255, 255, 255, 0.82));
    gc.setLineWidth(1);
    if (summary.transitions().size() > 1) {
      for (long transition : summary.transitions()) {
        if (transition <= summary.start() || transition >= summary.end()) {
          continue;
        }
        double x = left + ((double) (transition - summary.start()) / duration) * (right - left);
        gc.strokeLine(x, y - 4, x, y + height + 4);
      }
    } else if (summary.steps() > 1) {
      int ticks = (int) Math.min(48, summary.steps() - 1);
      for (int i = 1; i <= ticks; i++) {
        double x = left + ((right - left) * i / (ticks + 1.0));
        gc.strokeLine(x, y - 3, x, y + height + 3);
      }
    }
  }

  private void drawTimelineMarks(
      GraphicsContext gc, TimeSummary summary, double left, double right, double y, double height) {
    List<Long> visibleMarks = visibleTimelineMarks(summary);
    if (visibleMarks.isEmpty()) {
      return;
    }

    gc.setStroke(Color.rgb(255, 255, 255, 0.92));
    gc.setLineWidth(3.0);
    for (long mark : visibleMarks) {
      double x = timelineX(summary, mark, left, right);
      gc.strokeLine(x, y - 6, x, y + height + 8);
    }

    gc.setStroke(Color.web("#9a3412"));
    gc.setLineWidth(1.2);
    for (long mark : visibleMarks) {
      double x = timelineX(summary, mark, left, right);
      gc.strokeLine(x, y - 6, x, y + height + 8);
    }

    gc.setFill(Color.web("#f97316"));
    gc.setStroke(Color.WHITE);
    gc.setLineWidth(1.0);
    for (long mark : visibleMarks) {
      double x = timelineX(summary, mark, left, right);
      double[] xs = {x, x + 3.8, x, x - 3.8};
      double[] ys = {y - 8, y - 4, y, y - 4};
      gc.fillPolygon(xs, ys, 4);
      gc.strokePolygon(xs, ys, 4);
    }
  }

  private List<Long> visibleTimelineMarks(TimeSummary summary) {
    if (!summary.hasLocatedSpan() || timelineMarks.isEmpty()) {
      return List.of();
    }
    List<Long> ret = new ArrayList<>();
    for (Long mark : timelineMarks) {
      if (mark >= summary.start() && mark <= summary.end()) {
        ret.add(mark);
      }
    }
    return ret;
  }

  private double timelineX(TimeSummary summary, long epochMilliseconds, double left, double right) {
    long duration = summary.end() - summary.start();
    if (duration <= 0) {
      return left;
    }
    double fraction = (double) (epochMilliseconds - summary.start()) / duration;
    return left + fraction * (right - left);
  }

  private Long nearestTimelineMarkToLeft(double x) {
    if (timelineTrack == null
        || timelineSummary == null
        || timelineMarkClickHandler == null
        || !timelineSummary.hasLocatedSpan()) {
      return null;
    }
    double left = TIMELINE_HORIZONTAL_PADDING;
    double right = Math.max(left + 1, timelineTrack.getWidth() - TIMELINE_HORIZONTAL_PADDING);
    if (x < left) {
      return null;
    }

    double clampedX = Math.min(x, right);
    double fraction = (clampedX - left) / (right - left);
    double clickedEpoch =
        timelineSummary.start() + fraction * (timelineSummary.end() - timelineSummary.start());
    Long selected = null;
    for (long mark : visibleTimelineMarks(timelineSummary)) {
      if (mark <= clickedEpoch) {
        selected = mark;
      } else {
        break;
      }
    }
    return selected;
  }

  private void updateTimelineInteractivity() {
    if (timelineTrack == null || timelineSummary == null) {
      return;
    }
    boolean clickable =
        timelineMarkClickHandler != null && !visibleTimelineMarks(timelineSummary).isEmpty();
    timelineTrack.setCursor(clickable ? Cursor.HAND : Cursor.DEFAULT);
  }

  private FlowPane createTags(SpatialSummary spatial, TimeSummary temporal) {
    FlowPane tags = new FlowPane(4, 4);
    tags.getStyleClass().add("geometry-card-tags");

    tags.getChildren().add(chip(spatial.dimensionLabel()));
    if (spatial.isGrid()) {
      tags.getChildren().add(chip(spatial.gridLabel()));
    }
    if (!spatial.resolutionLabel().isBlank()) {
      tags.getChildren().add(chip(spatial.resolutionLabel()));
    }
    tags.getChildren().add(chip(temporal.dimensionLabel()));
    if (!temporal.resolutionLabel().isBlank()) {
      tags.getChildren().add(chip(temporal.resolutionLabel()));
    }
    return tags;
  }

  private static Label chip(String text) {
    Label label = new Label(text);
    label.getStyleClass().add("geometry-card-chip");
    label.setTextOverrun(OverrunStyle.ELLIPSIS);
    label.setMinWidth(Region.USE_PREF_SIZE);
    return label;
  }

  private static Label caption(String text) {
    Label label = new Label(text);
    label.getStyleClass().add("geometry-card-caption");
    label.setMinWidth(0);
    label.setMaxWidth(Double.MAX_VALUE);
    label.setTextOverrun(OverrunStyle.ELLIPSIS);
    return label;
  }

  private static String sizeLabel(Geometry geometry) {
    long size = geometry.size();
    if (size == Geometry.UNDEFINED) {
      return "size ?";
    }
    if (size == Geometry.INFINITE_SIZE) {
      return "size inf";
    }
    return "n " + size;
  }

  private static Rect imageArea(double width, double height, double aspectRatio) {
    double areaWidth = width;
    double areaHeight = height;
    double viewAspect = width / height;
    if (viewAspect > aspectRatio) {
      areaHeight = height;
      areaWidth = height * aspectRatio;
    } else {
      areaWidth = width;
      areaHeight = width / aspectRatio;
    }
    return new Rect((width - areaWidth) / 2.0, (height - areaHeight) / 2.0, areaWidth, areaHeight);
  }

  private static List<Double> readDoubles(Object value) {
    List<Double> values = new ArrayList<>();
    collectDoubles(value, values);
    return values;
  }

  private static List<Long> readLongs(Object value) {
    List<Long> values = new ArrayList<>();
    collectLongs(value, values);
    return values;
  }

  private static void collectDoubles(Object value, List<Double> values) {
    if (value == null) {
      return;
    }
    if (value instanceof Number number) {
      values.add(number.doubleValue());
    } else if (value instanceof Collection<?> collection) {
      for (Object item : collection) {
        collectDoubles(item, values);
      }
    } else if (value.getClass().isArray()) {
      int length = Array.getLength(value);
      for (int i = 0; i < length; i++) {
        collectDoubles(Array.get(value, i), values);
      }
    } else {
      for (String token : value.toString().replace("[", " ").replace("]", " ").split("[,\\s]+")) {
        if (!token.isBlank()) {
          try {
            values.add(Double.parseDouble(token));
          } catch (NumberFormatException ignored) {
            // Geometry parameters may include textual units; ignore those for numeric extraction.
          }
        }
      }
    }
  }

  private static void collectLongs(Object value, List<Long> values) {
    if (value == null) {
      return;
    }
    if (value instanceof Number number) {
      values.add(number.longValue());
    } else if (value instanceof Collection<?> collection) {
      for (Object item : collection) {
        collectLongs(item, values);
      }
    } else if (value.getClass().isArray()) {
      int length = Array.getLength(value);
      for (int i = 0; i < length; i++) {
        collectLongs(Array.get(value, i), values);
      }
    } else {
      for (String token : value.toString().replace("[", " ").replace("]", " ").split("[,\\s]+")) {
        if (!token.isBlank()) {
          try {
            values.add((long) Double.parseDouble(token));
          } catch (NumberFormatException ignored) {
            // Temporal parameters may include labels; ignore them for numeric extraction.
          }
        }
      }
    }
  }

  private static Long readFirstLong(Object value) {
    List<Long> values = readLongs(value);
    return values.isEmpty() ? null : values.getFirst();
  }

  private static String compactDate(Long epochMs) {
    return epochMs == null ? "open" : DATE_FORMATTER.format(Instant.ofEpochMilli(epochMs));
  }

  private static String formatCoordinate(double coordinate) {
    return COORDINATE_FORMAT.format(coordinate);
  }

  private record SpatialSummary(
      Dimension dimension,
      BoundingBox bounds,
      String projection,
      String resolution,
      List<Long> shape) {

    static SpatialSummary from(Dimension dimension) {
      if (dimension == null) {
        return new SpatialSummary(null, null, "", "", List.of());
      }

      Object bboxValue = dimension.getParameters().get(GeometryImpl.PARAMETER_SPACE_BOUNDINGBOX);
      BoundingBox bounds = null;
      List<Double> bbox = readDoubles(bboxValue);
      if (bbox.size() >= 4) {
        bounds = BoundingBox.of(bbox.get(0), bbox.get(1), bbox.get(2), bbox.get(3));
      }

      String projection =
          Objects.toString(
              dimension.getParameters().get(GeometryImpl.PARAMETER_SPACE_PROJECTION), "");
      String resolution =
          Objects.toString(
              dimension.getParameters().get(GeometryImpl.PARAMETER_SPACE_GRIDRESOLUTION), "");
      List<Long> shape = dimension.getShape() == null ? List.of() : dimension.getShape();
      return new SpatialSummary(dimension, bounds, projection, resolution, shape);
    }

    boolean canUseSatellite() {
      if (bounds == null || !bounds.isGeographic()) {
        return false;
      }
      if (projection == null || projection.isBlank()) {
        return true;
      }
      String normalized = projection.toLowerCase(Locale.ROOT);
      return normalized.contains("4326")
          || normalized.contains("crs84")
          || normalized.contains("wgs84");
    }

    boolean isGrid() {
      return dimension != null && dimension.isRegular() && shape.size() >= 2;
    }

    long xCells() {
      return shape.isEmpty() ? 1 : Math.max(1, shape.get(0));
    }

    long yCells() {
      return shape.size() < 2 ? 1 : Math.max(1, shape.get(1));
    }

    String dimensionLabel() {
      if (dimension == null) {
        return "no space";
      }
      String prefix = dimension.isRegular() ? "regular S" : "irregular S";
      return prefix + dimension.getDimensionality();
    }

    String gridLabel() {
      if (!isGrid()) {
        return "";
      }
      return xCells() + " x " + yCells();
    }

    String resolutionLabel() {
      return resolution == null ? "" : resolution;
    }

    String tooltip() {
      if (dimension == null) {
        return "No spatial extent";
      }
      StringBuilder builder = new StringBuilder(dimensionLabel());
      if (bounds != null) {
        builder
            .append("\n")
            .append("bbox ")
            .append(formatCoordinate(bounds.minX()))
            .append(", ")
            .append(formatCoordinate(bounds.minY()))
            .append(" to ")
            .append(formatCoordinate(bounds.maxX()))
            .append(", ")
            .append(formatCoordinate(bounds.maxY()));
      }
      if (!resolutionLabel().isBlank()) {
        builder.append("\nresolution ").append(resolutionLabel());
      }
      if (!projection.isBlank()) {
        builder.append("\nprojection ").append(projection);
      }
      return builder.toString();
    }
  }

  private record TimeSummary(
      Dimension dimension,
      Long start,
      Long end,
      long steps,
      List<Long> transitions,
      String representation,
      String resolution) {

    static TimeSummary from(Dimension dimension) {
      if (dimension == null) {
        return new TimeSummary(null, null, null, 0, List.of(), "", "");
      }

      Long start = readFirstLong(dimension.getParameters().get(GeometryImpl.PARAMETER_TIME_START));
      Long end = readFirstLong(dimension.getParameters().get(GeometryImpl.PARAMETER_TIME_END));
      List<Long> period =
          readLongs(dimension.getParameters().get(GeometryImpl.PARAMETER_TIME_PERIOD));
      if (period.size() >= 2) {
        start = period.get(0);
        end = period.get(1);
      }

      List<Long> transitions =
          readLongs(dimension.getParameters().get(GeometryImpl.PARAMETER_TIME_TRANSITIONS));
      if (!transitions.isEmpty()) {
        if (start == null) {
          start = transitions.getFirst();
        }
        if (end == null) {
          end = transitions.getLast();
        }
      }

      long steps =
          dimension.getShape() == null || dimension.getShape().isEmpty()
              ? 0
              : Math.max(0, dimension.getShape().getFirst());

      String gridResolution =
          Objects.toString(
              dimension.getParameters().get(GeometryImpl.PARAMETER_TIME_GRIDRESOLUTION), "");
      String scope =
          Objects.toString(dimension.getParameters().get(GeometryImpl.PARAMETER_TIME_SCOPE), "");
      String unit =
          Objects.toString(
              dimension.getParameters().get(GeometryImpl.PARAMETER_TIME_SCOPE_UNIT), "");
      String resolution = !gridResolution.isBlank() ? gridResolution : joinResolution(scope, unit);
      String representation =
          Objects.toString(
              dimension.getParameters().get(GeometryImpl.PARAMETER_TIME_REPRESENTATION), "");

      return new TimeSummary(dimension, start, end, steps, transitions, representation, resolution);
    }

    boolean present() {
      return dimension != null;
    }

    boolean hasLocatedSpan() {
      return start != null && end != null && end > start;
    }

    String dimensionLabel() {
      if (dimension == null) {
        return "no time";
      }
      String prefix = dimension.isRegular() ? "regular T" : "irregular T";
      return prefix + dimension.getDimensionality();
    }

    String startLabel() {
      return present() ? compactDate(start) : "no start";
    }

    String endLabel() {
      return present() ? compactDate(end) : "no end";
    }

    String resolutionLabel() {
      if (resolution != null && !resolution.isBlank()) {
        return resolution;
      }
      if (steps > 1) {
        return steps + " steps";
      }
      return representation == null || representation.isBlank()
          ? ""
          : representation.toLowerCase(Locale.ROOT);
    }

    String tooltip() {
      if (dimension == null) {
        return "No temporal extent";
      }
      StringBuilder builder = new StringBuilder(dimensionLabel());
      builder.append("\n").append(startLabel()).append(" to ").append(endLabel());
      if (!resolutionLabel().isBlank()) {
        builder.append("\nresolution ").append(resolutionLabel());
      }
      if (steps > 0) {
        builder.append("\nsteps ").append(steps);
      }
      return builder.toString();
    }

    private static String joinResolution(String scope, String unit) {
      if (scope.isBlank() && unit.isBlank()) {
        return "";
      }
      if (scope.isBlank()) {
        return unit;
      }
      if (unit.isBlank()) {
        return scope;
      }
      return scope + " " + unit;
    }
  }

  private record BoundingBox(double minX, double maxX, double minY, double maxY) {

    static BoundingBox of(double x1, double x2, double y1, double y2) {
      return new BoundingBox(
          Math.min(x1, x2), Math.max(x1, x2), Math.min(y1, y2), Math.max(y1, y2));
    }

    double width() {
      return Math.max(1e-6, maxX - minX);
    }

    double height() {
      return Math.max(1e-6, maxY - minY);
    }

    double aspectRatio() {
      return width() / height();
    }

    boolean isGeographic() {
      return minX >= -180 && maxX <= 180 && minY >= -90 && maxY <= 90;
    }

    BoundingBox expandedToAspect(double targetAspect, double paddingFraction) {
      double spanX = Math.max(0.01, width());
      double spanY = Math.max(0.01, height());
      spanX *= 1.0 + paddingFraction;
      spanY *= 1.0 + paddingFraction;

      double currentAspect = spanX / spanY;
      if (currentAspect > targetAspect) {
        spanY = spanX / targetAspect;
      } else {
        spanX = spanY * targetAspect;
      }

      double centerX = (minX + maxX) / 2.0;
      double centerY = (minY + maxY) / 2.0;
      return BoundingBox.of(
              centerX - spanX / 2.0,
              centerX + spanX / 2.0,
              centerY - spanY / 2.0,
              centerY + spanY / 2.0)
          .clampedGeographic();
    }

    BoundingBox clampedGeographic() {
      double x1 = Math.max(-180, minX);
      double x2 = Math.min(180, maxX);
      double y1 = Math.max(-90, minY);
      double y2 = Math.min(90, maxY);
      if (x2 <= x1) {
        x2 = Math.min(180, x1 + 0.01);
      }
      if (y2 <= y1) {
        y2 = Math.min(90, y1 + 0.01);
      }
      return new BoundingBox(x1, x2, y1, y2);
    }

    Rect project(BoundingBox child, Rect area) {
      double x = area.x() + ((child.minX() - minX) / width()) * area.width();
      double w = (child.width() / width()) * area.width();
      double y = area.y() + ((maxY - child.maxY()) / height()) * area.height();
      double h = (child.height() / height()) * area.height();
      return new Rect(x, y, Math.max(3, w), Math.max(3, h));
    }
  }

  private record Rect(double x, double y, double width, double height) {}
}
