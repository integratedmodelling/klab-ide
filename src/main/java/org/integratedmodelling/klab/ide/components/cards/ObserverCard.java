package org.integratedmodelling.klab.ide.components.cards;

import io.github.makbn.jlmap.fx.JLMapView;
import io.github.makbn.jlmap.map.JLMapProvider;
import io.github.makbn.jlmap.model.JLLatLng;
import java.util.concurrent.CompletableFuture;
import javafx.application.Platform;
import javafx.concurrent.Worker;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import org.integratedmodelling.common.utils.Utils;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.services.RuntimeService;
import org.integratedmodelling.klab.api.services.runtime.objects.ObserverGeometryUpdate;
import org.integratedmodelling.klab.api.services.runtime.objects.ObserverGeometryView;
import org.integratedmodelling.klab.ide.IDEContextScope;
import org.integratedmodelling.klab.ide.Theme;

/** Audits one agent, independently of the selected observer. Edits replace perceived space only. */
public class ObserverCard extends VBox implements AutoCloseable {
  private final long observerId;
  private final IDEContextScope scope;
  private final RuntimeService runtime;
  private final SpatialPane perceived = new SpatialPane();
  private final SpatialPane occupied = new SpatialPane();
  private final Label status = new Label("Loading observer geometry...");
  private final Label identity = new Label();
  private final HBox details = new HBox(12);
  private final Button useView = new Button("Use map view");
  private final Button save = new Button("Save perceived space");
  private final Button reset = new Button("Discard draft and reload");
  private ObserverGeometryView snapshot;
  private ObserverGeometryUpdate draft;
  private boolean busy, closed, refreshPending;
  private long generation;

  public ObserverCard(Observation observer, IDEContextScope scope, RuntimeService runtime) {
    this.observerId = observer.getId();
    this.scope = scope;
    this.runtime = runtime;
    setSpacing(10);
    setPadding(new Insets(12));
    identity.setText(Theme.getLabel(observer));
    identity.setWrapText(true);
    var policy = new Label("Automatic perception: union. Future observations can expand an edited extent.");
    policy.setWrapText(true);
    var instructions = new Label("Pan or zoom the perceived map, choose Use map view to preview a rectangle, then save.");
    instructions.setWrapText(true);
    var perceivedColumn = new VBox(6, new Label("Perceived geometry — editable spatial extent"), perceived);
    var occupiedColumn = new VBox(6, new Label("Occupied geometry — read only"), occupied);
    VBox.setVgrow(perceived, Priority.ALWAYS);
    VBox.setVgrow(occupied, Priority.ALWAYS);
    perceivedColumn.setMinWidth(220);
    occupiedColumn.setMinWidth(220);
    var maps = new SplitPane(perceivedColumn, occupiedColumn);
    maps.setDividerPositions(0.6);
    maps.setMinHeight(260);
    VBox.setVgrow(maps, Priority.ALWAYS);
    var audit = new TitledPane("Geometry details, including time (read only)", details);
    audit.setExpanded(false);
    useView.setOnAction(event -> stageView());
    save.setOnAction(event -> save());
    reset.setOnAction(event -> { draft = null; reload(); });
    status.setWrapText(true);
    getChildren().addAll(identity, policy, instructions, maps, new HBox(8, useView, save, reset), status, audit);
    updateButtons();
    reload();
  }

  /** Graph events never replace an unsaved draft. Explicit reload discards it. */
  public void geometryChanged() {
    if (closed) return;
    if (busy) { refreshPending = true; return; }
    if (draft != null) status.setText("The twin changed. Your draft is retained; saving checks for concurrent perception changes.");
    else reload();
  }

  private void reload() {
    if (closed) return;
    long request = ++generation;
    refreshPending = false;
    busy = true;
    updateButtons();
    CompletableFuture.supplyAsync(() -> runtime.getObserverGeometry(observerId, scope))
        .whenComplete((view, error) -> Platform.runLater(() -> {
          if (closed || request != generation) return;
          busy = false;
          if (error != null || view == null || view.getObserver() == null
              || view.getObserver().getId() != observerId) {
            status.setText("Cannot load observer geometry: " + errorMessage(error));
          } else {
            snapshot = view;
            identity.setText(Theme.getLabel(view.getObserver()) + " · " + view.getObserver().getObservable().getUrn());
            perceived.render(view.getPerceivedGeoJson());
            occupied.render(view.getOccupiedGeoJson());
            details.getChildren().setAll(
                new VBox(4, new Label("Perceived"), new GeometryCard(
                    view.getObserver().geometry(Observation.GeometryRelationship.PERCEIVES), true)),
                new VBox(4, new Label("Occupied"), new GeometryCard(view.getObserver().getGeometry(), true)));
            status.setText("Blue: saved geometry. An amber rectangle previews an unsaved edit. Time is preserved when saving.");
          }
          updateButtons();
          if (refreshPending && draft == null) reload();
        }));
  }

  private void stageView() {
    if (busy || snapshot == null) return;
    try {
      var bounds = perceived.viewport();
      draft = request(snapshot.getObserver(), bounds);
      perceived.preview(draft);
      status.setText(String.format(java.util.Locale.ROOT,
          "Unsaved rectangle: west %.4f°, south %.4f°, east %.4f°, north %.4f°. Save replaces perceived space only.",
          draft.getWest(), draft.getSouth(), draft.getEast(), draft.getNorth()));
    } catch (RuntimeException e) {
      status.setText(errorMessage(e));
    }
    updateButtons();
  }

  static ObserverGeometryUpdate request(Observation observer, double[] bounds) {
    if (bounds == null || bounds.length != 4) throw new IllegalArgumentException("Map bounds unavailable");
    var update = new ObserverGeometryUpdate();
    update.setObserverId(observer.getId());
    var geometry = observer.geometry(Observation.GeometryRelationship.PERCEIVES);
    update.setExpectedGeometry(geometry == null ? null : geometry.encode());
    update.setWest(bounds[0]); update.setSouth(bounds[1]);
    update.setEast(bounds[2]); update.setNorth(bounds[3]);
    update.validate();
    return update;
  }

  private void save() {
    if (draft == null || busy) return;
    var edit = draft;
    busy = true;
    long request = ++generation;
    updateButtons();
    status.setText("Saving perceived space...");
    CompletableFuture.supplyAsync(() -> runtime.updateObserverGeometry(edit, scope))
        .whenComplete((updated, error) -> Platform.runLater(() -> {
          if (closed || request != generation) return;
          busy = false;
          if (error != null || updated == null || updated.getId() != observerId) {
            status.setText("Edit was not saved. Your draft is retained. If perception changed, discard and reload before retrying. " + errorMessage(error));
            updateButtons();
          } else {
            draft = null;
            reload();
          }
        }));
  }

  private void updateButtons() {
    useView.setDisable(busy || snapshot == null);
    save.setDisable(busy || draft == null);
    reset.setDisable(busy);
  }

  private static String errorMessage(Throwable error) {
    if (error == null) return "No response from the runtime";
    while (error.getCause() != null) error = error.getCause();
    return error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
  }

  @Override public void close() {
    closed = true;
    generation++;
    perceived.close(); occupied.close();
  }

  private static class SpatialPane extends BorderPane {
    private final JLMapView map = JLMapView.builder().jlMapProvider(JLMapProvider.getDefault())
        .startCoordinate(new JLLatLng(0, 0)).showZoomController(true).build();
    private final Label state = new Label("Loading map...");
    private boolean ready, closed, fitted;
    private String json;
    SpatialPane() {
      map.setMinSize(0, 0);
      map.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
      setCenter(map);
      var fit = new Button("Fit saved extent");
      fit.setOnAction(event -> {
        if (ready) map.getWebView().getEngine().executeScript(
            "if(window.observerShape && window.observerShape.getBounds().isValid()){this.map.fitBounds(window.observerShape.getBounds(),{maxZoom:15});}else{this.map.fitWorld();}");
      });
      setBottom(new HBox(8, fit, state));
      var worker = map.getWebView().getEngine().getLoadWorker();
      worker.stateProperty().addListener((value, oldState, newState) -> mapState(newState));
      mapState(worker.getState());
    }
    private void mapState(Worker.State state) {
      if (closed) return;
      ready = state == Worker.State.SUCCEEDED;
      if (ready) render(json);
      else if (state == Worker.State.FAILED || state == Worker.State.CANCELLED) this.state.setText("Map engine unavailable");
    }
    void render(String json) {
      this.json = json;
      if (!ready || closed) return;
      try {
        // This is the same Leaflet map used by JLControlLayer. Encode data as a JS string literal.
        map.getWebView().getEngine().executeScript(
            "if(window.observerShape){this.map.removeLayer(window.observerShape);window.observerShape=null;}"
                + "if(window.observerDraft){this.map.removeLayer(window.observerDraft);window.observerDraft=null;}"
                + (json == null ? (fitted ? "" : "this.map.fitWorld();")
                    : "window.observerShape=L.geoJSON(JSON.parse(" + Utils.Json.asString(json)
                        + "),{style:{color:'#2563eb',fillOpacity:0.15}}).addTo(this.map);"
                        + (fitted ? "" : "if(window.observerShape.getBounds().isValid()){this.map.fitBounds(window.observerShape.getBounds(),{maxZoom:15});}")));
        if (json != null) fitted = true;
        state.setText(json == null ? "No spatial extent recorded" : "Saved spatial extent");
      } catch (RuntimeException e) { state.setText("Cannot display geometry: " + errorMessage(e)); }
    }
    double[] viewport() {
      if (!ready) throw new IllegalStateException("Wait until the map is ready");
      var encoded = map.getWebView().getEngine().executeScript(
          "JSON.stringify([this.map.getBounds().getWest(),this.map.getBounds().getSouth(),this.map.getBounds().getEast(),this.map.getBounds().getNorth()])");
      return Utils.Json.parseObject(encoded.toString(), double[].class);
    }
    void preview(ObserverGeometryUpdate edit) {
      map.getWebView().getEngine().executeScript(
          "if(window.observerDraft){this.map.removeLayer(window.observerDraft);}"
              + "window.observerDraft=L.rectangle([[" + edit.getSouth() + "," + edit.getWest()
              + "],[" + edit.getNorth() + "," + edit.getEast()
              + "]],{color:'#d97706',dashArray:'6 4',fillOpacity:0.12}).addTo(this.map);");
    }
    void close() { closed = true; map.getWebView().getEngine().load(null); }
  }
}