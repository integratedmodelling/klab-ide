package org.integratedmodelling.klab.ide.components.cards;

import io.github.makbn.jlmap.fx.JLMapView;
import io.github.makbn.jlmap.listener.JLAction;
import io.github.makbn.jlmap.listener.event.ClickEvent;
import io.github.makbn.jlmap.map.JLMapProvider;
import io.github.makbn.jlmap.model.JLBounds;
import io.github.makbn.jlmap.model.JLImageOverlay;
import io.github.makbn.jlmap.model.JLLatLng;
import io.github.makbn.jlmap.model.JLOptions;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicLong;
import javafx.application.Platform;
import javafx.concurrent.Worker;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import org.integratedmodelling.klab.api.collections.Parameters;
import org.integratedmodelling.klab.api.geometry.Geometry;
import org.integratedmodelling.klab.api.geometry.Geometry.Dimension;
import org.integratedmodelling.klab.api.geometry.impl.GeometryImpl;
import org.integratedmodelling.klab.api.knowledge.KlabAsset;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.services.RuntimeService;
import org.integratedmodelling.klab.ide.IDEContextScope;

/**
 * Interactive content view for a quality observation.
 *
 * <p>Spatially distributed two-dimensional qualities are rendered through the runtime export
 * service. Other geometries intentionally render a descriptive stub until a suitable renderer is
 * registered. Image export and point-value lookup are independent, injectable contracts so this
 * view can be reused in the inspector and in digital-twin tabs.
 */
public class ValueCard extends BaseCard<Observation> {

  private static final int DEFAULT_VIEWPORT_SIZE = 800;
  private static final DateTimeFormatter TIME_FORMATTER =
      DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());
  private static final DecimalFormat COORDINATE_FORMAT =
      new DecimalFormat("0.####", DecimalFormatSymbols.getInstance(Locale.US));

  private final Options options;
  private final AtomicLong requestGeneration = new AtomicLong();
  private final StackPane mapFrame = new StackPane();
  private final Label stateLabel = new Label();
  private final ProgressIndicator progress = new ProgressIndicator();
  private JLMapView mapView;
  private JLImageOverlay imageOverlay;
  private byte[] pendingImage;
  private Throwable pendingError;
  private long pendingGeneration;
  private boolean mapReady;
  private Long selectedTimestamp;

  public ValueCard(Observation asset, IDEContextScope scope, boolean extended) {
    this(asset, scope, extended, Options.runtimeDefaults(scope));
  }

  public ValueCard(
      Observation asset, IDEContextScope scope, boolean extended, Options options) {
    super(asset, scope, extended, false);
    this.options = Objects.requireNonNull(options);
    this.selectedTimestamp = initialTimestamp(asset);
    drawContent();
  }

  @Override
  protected void drawContent() {
    getStyleClass().add("observation-value-card");
    setMinSize(180, 180);
    setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);

    if (!supportsMap(asset.getGeometry())) {
      setCenter(createUnsupportedGeometryStub(asset.getGeometry()));
      return;
    }

    configureMapFrame();
    setCenter(mapFrame);
    setBottom(createStateBar());
    refresh();
  }

  /** Select a temporal state and reload the map. Re-selecting the current state is a no-op. */
  public void selectTimestamp(Long timestamp) {
    if (Objects.equals(selectedTimestamp, timestamp)) {
      return;
    }
    selectedTimestamp = timestamp;
    refresh();
  }

  public Long getSelectedTimestamp() {
    return selectedTimestamp;
  }

  private void configureMapFrame() {
    mapFrame.getStyleClass().add("observation-map-frame");
    mapFrame.setAlignment(Pos.CENTER);
    mapFrame.setMinSize(0, 0);
    mapFrame.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);

    List<Double> bounds = boundingBox(asset.getGeometry());
    mapView =
        JLMapView.builder()
            .jlMapProvider(JLMapProvider.getDefault())
            .startCoordinate(center(bounds))
            .showZoomController(true)
            .build();
    mapView.setMinSize(0, 0);
    mapView.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
    mapView.setOnActionListener(
        (source, event) -> {
          if (event instanceof ClickEvent clickEvent
              && clickEvent.action() == JLAction.CLICK
              && clickEvent.center() != null) {
            queryPoint(clickEvent.center().getLng(), clickEvent.center().getLat());
          }
        });

    var worker = mapView.getWebView().getEngine().getLoadWorker();
    worker.stateProperty().addListener((observable, oldState, newState) -> onMapState(newState));

    progress.setMaxSize(36, 36);
    progress.setVisible(false);
    mapFrame.getChildren().addAll(mapView, progress);
    onMapState(worker.getState());
  }

  private void onMapState(Worker.State state) {
    if (state == Worker.State.SUCCEEDED) {
      mapReady = true;
      renderPendingImage();
    } else if (state == Worker.State.FAILED || state == Worker.State.CANCELLED) {
      mapReady = false;
      progress.setVisible(false);
      updateState("Map engine unavailable");
    }
  }

  private Node createStateBar() {
    stateLabel.getStyleClass().add("observation-map-state");
    stateLabel.setMaxWidth(Double.MAX_VALUE);
    HBox.setHgrow(stateLabel, Priority.ALWAYS);
    updateState("Click the map to retrieve the value at a point");

    HBox bar = new HBox(stateLabel);
    bar.getStyleClass().add("observation-map-state-bar");
    bar.setAlignment(Pos.CENTER_LEFT);
    bar.setPadding(new Insets(5, 8, 5, 8));
    return bar;
  }

  private Node createUnsupportedGeometryStub(Geometry geometry) {
    Dimension space = geometry == null ? null : geometry.dimension(Dimension.Type.SPACE);
    String explanation;
    if (space == null) {
      explanation = "This quality has no spatial dimension.";
    } else if (space.getDimensionality() != 2) {
      explanation =
          "Map visualization is not available for "
              + space.getDimensionality()
              + "-dimensional space.";
    } else {
      explanation = "Map visualization is only available for spatially distributed qualities.";
    }

    Label title = new Label("Quality visualization");
    title.getStyleClass().add("observation-content-stub-title");
    Label detail = new Label(explanation);
    detail.getStyleClass().add("observation-content-stub-detail");
    detail.setWrapText(true);
    var box = new javafx.scene.layout.VBox(6, title, detail);
    box.getStyleClass().add("observation-content-stub");
    box.setAlignment(Pos.CENTER);
    box.setPadding(new Insets(16));
    return box;
  }

  private void refresh() {
    long generation = requestGeneration.incrementAndGet();
    pendingGeneration = generation;
    pendingImage = null;
    pendingError = null;
    progress.setVisible(true);
    if (imageOverlay != null) {
      imageOverlay.setJLObjectOpacity(0.45);
    }
    updateState("Loading " + temporalLabel(selectedTimestamp) + "...");

    var request =
        new MapRequest(
            asset,
            selectedTimestamp,
            options.viewportWidth(),
            options.viewportHeight());
    options
        .mapImageProvider()
        .load(request)
        .whenComplete(
            (bytes, error) ->
                Platform.runLater(
                    () -> {
                      if (generation != requestGeneration.get()) {
                        return;
                      }
                      pendingGeneration = generation;
                      pendingImage = bytes;
                      pendingError = error;
                      renderPendingImage();
                    }));
  }

  private void renderPendingImage() {
    if (!mapReady || pendingGeneration != requestGeneration.get()) {
      return;
    }
    progress.setVisible(false);
    if (pendingError != null || pendingImage == null || pendingImage.length == 0) {
      if (imageOverlay != null) {
        imageOverlay.remove();
        imageOverlay = null;
      }
      updateState("Map unavailable: " + errorMessage(pendingError));
      return;
    }

    try {
      if (imageOverlay != null) {
        imageOverlay.remove();
      }
      imageOverlay =
          mapView
              .getUiLayer()
              .addImage(
                  mapBounds(asset.getGeometry()),
                  pngDataUrl(pendingImage),
                  JLOptions.DEFAULT);
      fitBounds(mapView, mapBounds(asset.getGeometry()));
      pendingImage = null;
      pendingError = null;
      updateState(temporalLabel(selectedTimestamp) + " \u2022 click the map to retrieve a value");
    } catch (RuntimeException e) {
      updateState("Unable to display map overlay: " + errorMessage(e));
    }
  }

  private void queryPoint(double longitude, double latitude) {
    if (imageOverlay == null) {
      return;
    }
    MapPoint point = mapPoint(asset.getGeometry(), longitude, latitude);
    if (point.normalizedX() < 0
        || point.normalizedX() > 1
        || point.normalizedY() < 0
        || point.normalizedY() > 1) {
      return;
    }
    long generation = requestGeneration.get();
    Long timestamp = selectedTimestamp;
    updateState("Retrieving value at " + point.label() + "...");
    options
        .pointValueProvider()
        .query(new PointQuery(asset, selectedTimestamp, point))
        .whenComplete(
            (value, error) ->
                Platform.runLater(
                    () -> {
                      if (generation != requestGeneration.get()
                          || !Objects.equals(timestamp, selectedTimestamp)) {
                        return;
                      }
                      if (error != null) {
                        updateState("Value unavailable at " + point.label() + ": " + errorMessage(error));
                      } else {
                        updateState(point.label() + " \u2022 " + Objects.toString(value, "no data"));
                      }
                    }));
  }

  private void updateState(String text) {
    stateLabel.setText(text);
    stateLabel.setTooltip(new Tooltip(text));
  }

  static boolean supportsMap(Geometry geometry) {
    if (geometry == null) {
      return false;
    }
    Dimension space = geometry.dimension(Dimension.Type.SPACE);
    return space != null && space.getDimensionality() == 2 && space.size() > 1;
  }

  static List<Long> temporalStates(Observation observation) {
    LinkedHashSet<Long> states = new LinkedHashSet<>();
    if (observation != null) {
      addTemporalStates(states, observation.getEventTimestamps());
      addTemporalStates(
          states,
          observation.getHistograms() == null
              ? null
              : observation.getHistograms().keySet());
    }
    return states.stream().sorted().toList();
  }

  /**
   * Normalize timestamp collections after deserialization. JSON map keys are strings and older
   * payloads may deserialize integral array values as Integer even though the observation contract
   * declares Long.
   */
  private static void addTemporalStates(Set<Long> states, Collection<?> values) {
    if (values == null) {
      return;
    }
    for (var value : values) {
      switch (value) {
        case Number number -> states.add(number.longValue());
        case String string -> {
          try {
            states.add(Long.parseLong(string));
          } catch (NumberFormatException ignored) {
            // Ignore malformed temporal metadata without losing the rest of the observation card.
          }
        }
        case null, default -> {
          // Null and unknown metadata values are not temporal states.
        }
      }
    }
  }

  private static Long initialTimestamp(Observation observation) {
    List<Long> states = temporalStates(observation);
    return states.isEmpty() ? null : states.getFirst();
  }

  static Optional<NormalizedPoint> normalizedPoint(
      double x,
      double y,
      double viewWidth,
      double viewHeight,
      double imageWidth,
      double imageHeight) {
    if (viewWidth <= 0 || viewHeight <= 0 || imageWidth <= 0 || imageHeight <= 0) {
      return Optional.empty();
    }
    double scale = Math.min(viewWidth / imageWidth, viewHeight / imageHeight);
    double renderedWidth = imageWidth * scale;
    double renderedHeight = imageHeight * scale;
    double left = (viewWidth - renderedWidth) / 2.0;
    double top = (viewHeight - renderedHeight) / 2.0;
    if (x < left || y < top || x > left + renderedWidth || y > top + renderedHeight) {
      return Optional.empty();
    }
    return Optional.of(
        new NormalizedPoint((x - left) / renderedWidth, (y - top) / renderedHeight));
  }

  static MapPoint mapPoint(Geometry geometry, NormalizedPoint point) {
    List<Double> bounds = boundingBox(geometry);
    if (bounds.size() < 4) {
      return new MapPoint(point.x(), point.y(), null, null);
    }
    double minX = Math.min(bounds.get(0), bounds.get(1));
    double maxX = Math.max(bounds.get(0), bounds.get(1));
    double minY = Math.min(bounds.get(2), bounds.get(3));
    double maxY = Math.max(bounds.get(2), bounds.get(3));
    double longitude = minX + point.x() * (maxX - minX);
    double latitude = maxY - point.y() * (maxY - minY);
    return new MapPoint(point.x(), point.y(), longitude, latitude);
  }

  static MapPoint mapPoint(Geometry geometry, double longitude, double latitude) {
    List<Double> bounds = boundingBox(geometry);
    if (bounds.size() < 4) {
      return new MapPoint(0, 0, longitude, latitude);
    }
    double minX = Math.min(bounds.get(0), bounds.get(1));
    double maxX = Math.max(bounds.get(0), bounds.get(1));
    double minY = Math.min(bounds.get(2), bounds.get(3));
    double maxY = Math.max(bounds.get(2), bounds.get(3));
    double normalizedX = maxX == minX ? 0 : (longitude - minX) / (maxX - minX);
    double normalizedY = maxY == minY ? 0 : (maxY - latitude) / (maxY - minY);
    return new MapPoint(normalizedX, normalizedY, longitude, latitude);
  }

  private static String pngDataUrl(byte[] bytes) {
    return "data:image/png;base64," + Base64.getEncoder().encodeToString(bytes);
  }

  private static JLLatLng center(List<Double> bounds) {
    if (bounds.size() < 4) {
      return new JLLatLng(0, 0);
    }
    return new JLLatLng(
        (bounds.get(2) + bounds.get(3)) / 2.0,
        (bounds.get(0) + bounds.get(1)) / 2.0);
  }

  private static JLBounds mapBounds(Geometry geometry) {
    List<Double> bounds = boundingBox(geometry);
    if (bounds.size() < 4) {
      throw new IllegalStateException("The observation geometry has no geographic bounds");
    }
    double west = Math.min(bounds.get(0), bounds.get(1));
    double east = Math.max(bounds.get(0), bounds.get(1));
    double south = Math.min(bounds.get(2), bounds.get(3));
    double north = Math.max(bounds.get(2), bounds.get(3));
    return JLBounds.builder()
        .southWest(new JLLatLng(south, west))
        .northEast(new JLLatLng(north, east))
        .build();
  }

  private static void fitBounds(JLMapView map, JLBounds bounds) {
    map.getControlLayer().fitBounds(bounds);
  }

  private static List<Double> boundingBox(Geometry geometry) {
    if (geometry == null) {
      return List.of();
    }
    Dimension space = geometry.dimension(Dimension.Type.SPACE);
    if (space == null) {
      return List.of();
    }
    Object value = space.getParameters().get(GeometryImpl.PARAMETER_SPACE_BOUNDINGBOX);
    List<Double> ret = new ArrayList<>();
    collectDoubles(value, ret);
    return ret;
  }

  private static void collectDoubles(Object value, List<Double> values) {
    if (value == null) {
      return;
    }
    if (value instanceof Number number) {
      values.add(number.doubleValue());
    } else if (value instanceof Iterable<?> iterable) {
      for (Object item : iterable) {
        collectDoubles(item, values);
      }
    } else if (value.getClass().isArray()) {
      int length = java.lang.reflect.Array.getLength(value);
      for (int i = 0; i < length; i++) {
        collectDoubles(java.lang.reflect.Array.get(value, i), values);
      }
    } else {
      for (String token : value.toString().replace("[", " ").replace("]", " ").split("[,\\s]+")) {
        if (!token.isBlank()) {
          try {
            values.add(Double.parseDouble(token));
          } catch (NumberFormatException ignored) {
            // Geometry parameters may carry units or projection text.
          }
        }
      }
    }
  }

  private static String temporalLabel(Long timestamp) {
    if (timestamp == null) {
      return "current state";
    }
    if (timestamp == 0) {
      return "initial state";
    }
    return TIME_FORMATTER.format(Instant.ofEpochMilli(timestamp));
  }

  private static String errorMessage(Throwable error) {
    if (error == null) {
      return "empty response";
    }
    Throwable cause =
        error instanceof CompletionException && error.getCause() != null ? error.getCause() : error;
    return cause.getMessage() == null ? cause.getClass().getSimpleName() : cause.getMessage();
  }

  public record MapRequest(
      Observation observation, Long timestamp, int viewportWidth, int viewportHeight) {}

  public record NormalizedPoint(double x, double y) {}

  public record MapPoint(
      double normalizedX, double normalizedY, Double longitude, Double latitude) {

    public String label() {
      if (longitude != null && latitude != null) {
        return COORDINATE_FORMAT.format(longitude)
            + ", "
            + COORDINATE_FORMAT.format(latitude);
      }
      return Math.round(normalizedX * 100) + "%, " + Math.round(normalizedY * 100) + "%";
    }
  }

  public record PointQuery(Observation observation, Long timestamp, MapPoint point) {}

  @FunctionalInterface
  public interface MapImageProvider {
    CompletableFuture<byte[]> load(MapRequest request);
  }

  @FunctionalInterface
  public interface PointValueProvider {
    CompletableFuture<Object> query(PointQuery query);
  }

  public record Options(
      MapImageProvider mapImageProvider,
      PointValueProvider pointValueProvider,
      int viewportWidth,
      int viewportHeight) {

    public Options {
      Objects.requireNonNull(mapImageProvider);
      Objects.requireNonNull(pointValueProvider);
      if (viewportWidth <= 0 || viewportHeight <= 0) {
        throw new IllegalArgumentException("Viewport dimensions must be positive");
      }
    }

    public static Options runtimeDefaults(IDEContextScope scope) {
      Objects.requireNonNull(scope);
      RuntimeService runtime = scope.getService(RuntimeService.class);
      return new Options(
          request ->
              CompletableFuture.supplyAsync(
                  () ->
                      readAll(
                          runtime.exportAsset(
                              request.observation().getUrn(),
                              KlabAsset.KnowledgeClass.OBSERVATION,
                              "image/png",
                              exportParameters(
                                  request.timestamp(),
                                  request.viewportWidth(),
                                  request.viewportHeight()),
                              scope))),
          query ->
              CompletableFuture.supplyAsync(
                  () ->
                      new String(
                              readAll(
                                  runtime.exportAsset(
                                      query.observation().getUrn(),
                                      KlabAsset.KnowledgeClass.OBSERVATION,
                                      "text/plain",
                                      pointParameters(query),
                                      scope)),
                              StandardCharsets.UTF_8)
                          .trim()),
          DEFAULT_VIEWPORT_SIZE,
          DEFAULT_VIEWPORT_SIZE);
    }
  }

  static Parameters<String> exportParameters(Long timestamp, int width, int height) {
    Parameters<String> ret =
        Parameters.create("viewportX", width, "viewportY", height);
    if (timestamp != null) {
      ret.put("timestamp", timestamp);
      ret.put("time", timestamp);
    }
    return ret;
  }

  static Parameters<String> pointParameters(PointQuery query) {
    Parameters<String> ret =
        exportParameters(query.timestamp(), DEFAULT_VIEWPORT_SIZE, DEFAULT_VIEWPORT_SIZE);
    ret.put("normalizedX", query.point().normalizedX());
    ret.put("normalizedY", query.point().normalizedY());
    if (query.point().longitude() != null && query.point().latitude() != null) {
      ret.put("longitude", query.point().longitude());
      ret.put("latitude", query.point().latitude());
    }
    return ret;
  }

  private static byte[] readAll(InputStream input) {
    if (input == null) {
      throw new CompletionException(new IOException("The runtime returned no content"));
    }
    try (input) {
      return input.readAllBytes();
    } catch (IOException e) {
      throw new CompletionException(e);
    }
  }
}
