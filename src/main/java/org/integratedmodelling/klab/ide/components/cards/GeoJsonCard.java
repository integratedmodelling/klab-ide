package org.integratedmodelling.klab.ide.components.cards;

import io.github.makbn.jlmap.fx.JLMapView;
import io.github.makbn.jlmap.map.JLMapProvider;
import io.github.makbn.jlmap.model.JLBounds;
import io.github.makbn.jlmap.model.JLLatLng;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
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
import javafx.scene.layout.VBox;
import org.integratedmodelling.klab.api.collections.Parameters;
import org.integratedmodelling.klab.api.geometry.Geometry;
import org.integratedmodelling.klab.api.geometry.Geometry.Dimension;
import org.integratedmodelling.klab.api.geometry.impl.GeometryImpl;
import org.integratedmodelling.klab.api.data.RuntimeAsset;
import org.integratedmodelling.klab.api.knowledge.Cohort;
import org.integratedmodelling.klab.api.knowledge.KlabAsset;
import org.integratedmodelling.klab.api.knowledge.SemanticType;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.services.RuntimeService;
import org.integratedmodelling.klab.ide.IDEContextScope;

/** Zoomable GeoJSON map for shape-bearing substantial observations. */
public class GeoJsonCard extends BaseCard<RuntimeAsset> {

  private final Options options;
  private final Label stateLabel = new Label();
  private final ProgressIndicator progress = new ProgressIndicator();
  private JLMapView mapView;
  private String geoJson;
  private Throwable loadError;
  private boolean mapReady;
  private boolean rendered;

  public GeoJsonCard(Observation asset, IDEContextScope scope, boolean extended) {
    this(asset, scope, extended, Options.runtimeDefaults(scope));
  }

  public GeoJsonCard(Cohort asset, IDEContextScope scope, boolean extended) {
    this(asset, scope, extended, Options.runtimeDefaults(scope));
  }

  GeoJsonCard(RuntimeAsset asset, IDEContextScope scope, boolean extended, Options options) {
    super(asset, scope, extended, false);
    this.options = Objects.requireNonNull(options);
    drawContent();
  }

  @Override
  protected void drawContent() {
    getStyleClass().add("observation-value-card");
    setMinSize(180, 180);
    setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);

    if (!supportsAsset(asset)) {
      setCenter(createUnsupportedGeometryStub());
      return;
    }

    List<Double> bounds = boundingBox(geometry(asset));
    JLLatLng center = center(bounds);
    mapView =
        JLMapView.builder()
            .jlMapProvider(JLMapProvider.getDefault())
            .startCoordinate(center)
            .showZoomController(true)
            .build();
    mapView.setMinSize(0, 0);
    mapView.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);

    progress.setMaxSize(36, 36);
    var frame = new StackPane(mapView, progress);
    frame.getStyleClass().add("observation-map-frame");
    frame.setAlignment(Pos.CENTER);
    frame.setMinSize(0, 0);
    frame.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
    setCenter(frame);
    setBottom(createStateBar());

    var worker = mapView.getWebView().getEngine().getLoadWorker();
    worker.stateProperty().addListener((observable, oldState, newState) -> onMapState(newState));
    onMapState(worker.getState());
    loadGeoJson();
  }

  private void onMapState(Worker.State state) {
    if (state == Worker.State.SUCCEEDED) {
      mapReady = true;
      renderWhenReady();
    } else if (state == Worker.State.FAILED || state == Worker.State.CANCELLED) {
      mapReady = false;
      progress.setVisible(false);
      updateState("Map engine unavailable");
    }
  }

  private void loadGeoJson() {
    progress.setVisible(true);
    updateState("Loading spatial features...");
    options
        .geoJsonProvider()
        .load(asset)
        .whenComplete(
            (content, error) ->
                Platform.runLater(
                    () -> {
                      geoJson = content;
                      loadError = error;
                      renderWhenReady();
                    }));
  }

  private void renderWhenReady() {
    if (rendered || !mapReady || (geoJson == null && loadError == null)) {
      return;
    }
    progress.setVisible(false);
    if (loadError != null || geoJson == null || geoJson.isBlank()) {
      updateState("Spatial features unavailable: " + errorMessage(loadError));
      return;
    }
    try {
      mapView.getGeoJsonLayer().addFromContent(geoJson);
      fitBounds(mapView, boundingBox(geometry(asset)));
      rendered = true;
      updateState("Use the map controls to pan and zoom");
    } catch (RuntimeException e) {
      updateState("Unable to display spatial features: " + errorMessage(e));
    }
  }

  private Node createStateBar() {
    stateLabel.getStyleClass().add("observation-map-state");
    stateLabel.setMaxWidth(Double.MAX_VALUE);
    HBox.setHgrow(stateLabel, Priority.ALWAYS);
    var bar = new HBox(stateLabel);
    bar.getStyleClass().add("observation-map-state-bar");
    bar.setAlignment(Pos.CENTER_LEFT);
    bar.setPadding(new Insets(5, 8, 5, 8));
    return bar;
  }

  private Node createUnsupportedGeometryStub() {
    var title = new Label("Spatial features");
    title.getStyleClass().add("observation-content-stub-title");
    var detail = new Label("This substantial observation has no two-dimensional spatial geometry.");
    detail.getStyleClass().add("observation-content-stub-detail");
    detail.setWrapText(true);
    var box = new VBox(6, title, detail);
    box.getStyleClass().add("observation-content-stub");
    box.setAlignment(Pos.CENTER);
    box.setPadding(new Insets(16));
    return box;
  }

  private void updateState(String text) {
    stateLabel.setText(text);
    stateLabel.setTooltip(new Tooltip(text));
  }

  static boolean supportsObservation(Observation observation) {
    return observation != null
        && observation.getObservable() != null
        && observation.getObservable().getSemantics() != null
        && SemanticType.isEnumerableSubstantial(
            observation.getObservable().getSemantics().getType())
        && supportsSpatialGeometry(observation.getGeometry());
  }

  static boolean supportsCohort(Cohort cohort) {
    return cohort != null && supportsSpatialGeometry(cohort.getGeometry());
  }

  private static boolean supportsAsset(RuntimeAsset asset) {
    return switch (asset) {
      case Observation observation -> supportsObservation(observation);
      case Cohort cohort -> supportsCohort(cohort);
      default -> false;
    };
  }

  private static Geometry geometry(RuntimeAsset asset) {
    return switch (asset) {
      case Observation observation -> observation.getGeometry();
      case Cohort cohort -> cohort.getGeometry();
      default -> null;
    };
  }

  private static String urn(RuntimeAsset asset) {
    String urn = switch (asset) {
      case Observation observation -> observation.getUrn();
      case Cohort cohort -> cohort.getUrn();
      default -> throw new IllegalArgumentException("Unsupported GeoJSON asset " + asset);
    };
    if (urn == null || urn.isBlank()) {
      throw new IllegalStateException("The spatial asset has no export URN");
    }
    return urn;
  }

  static boolean supportsSpatialGeometry(Geometry geometry) {
    if (geometry == null) {
      return false;
    }
    Dimension space = geometry.dimension(Dimension.Type.SPACE);
    return space != null && space.getDimensionality() == 2;
  }

  private static JLLatLng center(List<Double> bounds) {
    if (bounds.size() < 4) {
      return new JLLatLng(0, 0);
    }
    return new JLLatLng(
        (bounds.get(2) + bounds.get(3)) / 2.0,
        (bounds.get(0) + bounds.get(1)) / 2.0);
  }

  private static void fitBounds(JLMapView map, List<Double> bounds) {
    if (bounds.size() < 4) {
      return;
    }
    double west = Math.min(bounds.get(0), bounds.get(1));
    double east = Math.max(bounds.get(0), bounds.get(1));
    double south = Math.min(bounds.get(2), bounds.get(3));
    double north = Math.max(bounds.get(2), bounds.get(3));
    // Leaflet cannot derive a useful viewport from a zero-area point extent.
    double longitudePadding = west == east ? 0.01 : 0;
    double latitudePadding = south == north ? 0.01 : 0;
    map.getControlLayer()
        .fitBounds(
            JLBounds.builder()
                .northEast(new JLLatLng(north + latitudePadding, east + longitudePadding))
                .southWest(new JLLatLng(south - latitudePadding, west - longitudePadding))
                .build());
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
    var values = new java.util.ArrayList<Double>();
    collectDoubles(value, values);
    return values;
  }

  private static void collectDoubles(Object value, List<Double> values) {
    if (value instanceof Number number) {
      values.add(number.doubleValue());
    } else if (value instanceof Iterable<?> iterable) {
      iterable.forEach(item -> collectDoubles(item, values));
    } else if (value != null && value.getClass().isArray()) {
      for (int i = 0; i < java.lang.reflect.Array.getLength(value); i++) {
        collectDoubles(java.lang.reflect.Array.get(value, i), values);
      }
    } else if (value != null) {
      for (String token : value.toString().replace("[", " ").replace("]", " ").split("[,\\s]+")) {
        if (!token.isBlank()) {
          try {
            values.add(Double.parseDouble(token));
          } catch (NumberFormatException ignored) {
            // Ignore projection/unit text in serialized geometry parameters.
          }
        }
      }
    }
  }

  private static String errorMessage(Throwable error) {
    if (error == null) {
      return "empty response";
    }
    Throwable cause =
        error instanceof CompletionException && error.getCause() != null ? error.getCause() : error;
    return cause.getMessage() == null ? cause.getClass().getSimpleName() : cause.getMessage();
  }

  @FunctionalInterface
  interface GeoJsonProvider {
    CompletableFuture<String> load(RuntimeAsset asset);
  }

  record Options(GeoJsonProvider geoJsonProvider) {
    Options {
      Objects.requireNonNull(geoJsonProvider);
    }

    static Options runtimeDefaults(IDEContextScope scope) {
      Objects.requireNonNull(scope);
      RuntimeService runtime = scope.getService(RuntimeService.class);
      return new Options(
          asset ->
              CompletableFuture.supplyAsync(
                  () ->
                      readUtf8(
                          runtime.exportAsset(
                              urn(asset),
                              KlabAsset.KnowledgeClass.OBSERVATION,
                              "application/geo+json",
                              Parameters.create(),
                              scope))));
    }
  }

  private static String readUtf8(InputStream input) {
    if (input == null) {
      throw new CompletionException(new IOException("The runtime returned no content"));
    }
    try (input) {
      return new String(input.readAllBytes(), StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new CompletionException(e);
    }
  }
}
