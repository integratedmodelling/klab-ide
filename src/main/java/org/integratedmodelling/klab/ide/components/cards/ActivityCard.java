package org.integratedmodelling.klab.ide.components.cards;

import atlantafx.base.controls.Tile;
import javafx.application.Platform;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import java.io.ByteArrayInputStream;
import java.util.concurrent.CompletableFuture;
import org.integratedmodelling.common.utils.Utils;
import org.integratedmodelling.klab.api.services.RuntimeService;
import atlantafx.base.theme.Styles;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import org.integratedmodelling.klab.api.data.Metadata;
import org.integratedmodelling.klab.api.documentation.FlowChart;
import org.integratedmodelling.klab.ide.ActivityPresentation;
import org.integratedmodelling.klab.api.knowledge.observation.scale.time.TimeInstant;
import org.integratedmodelling.klab.api.provenance.Activity;
import org.integratedmodelling.klab.ide.IDEContextScope;
import org.integratedmodelling.klab.ide.Theme;
import org.integratedmodelling.klab.ide.components.TextResult;

public class ActivityCard extends BaseCard<Activity> {

  public ActivityCard(Activity asset, IDEContextScope scope, boolean extended) {
    super(asset, scope, extended);
  }

  @Override
  protected void drawContent() {
    getStyleClass().add("observation-card");
    var tile = new Tile();

    tile.setTitle(ActivityPresentation.type(asset));
    tile.setDescription(ActivityPresentation.description(asset));
    tile.setGraphic(Theme.getGraphics(asset));
    setTop(tile);
    setCenter(createBody());
    setBottom(createFooter());
    if (extended) {
      getStyleClass().add(Styles.ELEVATED_2);
      setPadding(new Insets(2, 10, 4, 10));
    }
  }

  private Node createFooter() {
    return new Label(
        (asset.getStart() > 0 ? TimeInstant.create(asset.getStart()).toString() : "Start unknown")
            + (asset.getEnd() > 0 ? " to " + TimeInstant.create(asset.getEnd()) : " - running"));
  }

  private Node createBody() {
    var body = new VBox(6);
    body.getChildren().add(new Label("Outcome: "
        + (asset.getOutcome() == null ? "Running" : asset.getOutcome().name())));
    if (extended) {
      if (asset.getServiceName() != null)
        body.getChildren().add(new Label("Service: " + asset.getServiceName()));
      if (asset.getObservationUrn() != null)
        body.getChildren().add(new Label("Observation: " + asset.getObservationUrn()));
      if (asset.getStackTrace() != null) {
        var trace = new TextResult(asset.getStackTrace());
        trace.setPrefHeight(230);
        trace.setMaxHeight(230);
        trace.setMessageStyle(Styles.BORDERED, Styles.DANGER, Styles.SMALL);
        body.getChildren().add(trace);
      }
    }
    body.setPadding(new Insets(10));
    if (!extended) return body;
    body.setMinWidth(180);
    body.setPrefWidth(220);
    body.setMaxWidth(280);
    var diagrams = new TabPane();
    diagrams.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
    if (asset.getMetadata() != null) {
      addChart(diagrams, Metadata.IM_RESOLUTION_GRAPH, "Resolution graph");
      addChart(diagrams, Metadata.IM_DATAFLOW_GRAPH, "Dataflow");
    }
    Node center = diagrams;
    if (diagrams.getTabs().isEmpty()) {
      var empty = new StackPane(new Label("No resolution graph or dataflow available"));
      empty.getStyleClass().add("observation-content-stub");
      center = empty;
    }
    var centerColumn = new VBox(center);
    centerColumn.setMinWidth(180);
    centerColumn.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
    VBox.setVgrow(center, Priority.ALWAYS);
    HBox.setHgrow(centerColumn, Priority.ALWAYS);
    var layout = new HBox(10, body, centerColumn);
    layout.setPadding(new Insets(10));
    return layout;
  }

  private void addChart(TabPane tabs, String key, String label) {
    Object chart = asset.getMetadata().get(key);
    // Untyped metadata may arrive from JSON as a map rather than a FlowChart bean.
    if (!(chart instanceof FlowChart) && !(chart instanceof java.util.Map<?, ?>)) return;
    var status = new Label(scope == null ? "Open the activity's digital twin to load the diagram"
        : "Loading " + label.toLowerCase() + "...");
    var pane = new StackPane(status);
    pane.setMinSize(180, 180);
    pane.setPrefHeight(320);
    pane.getStyleClass().add("observation-content-stub");
    tabs.getTabs().add(new Tab(label, pane));
    if (scope == null) return;
    CompletableFuture.supplyAsync(() -> {
      var service = scope.getService(RuntimeService.class);
      if (service == null) throw new IllegalStateException("Runtime service unavailable");
      var bytes = service.adapt(Utils.Json.asString(chart), "image/png", null, scope);
      if (bytes == null || bytes.length == 0) throw new IllegalStateException("Empty diagram");
      return bytes;
    }).whenComplete((bytes, failure) -> Platform.runLater(() -> {
      if (failure != null) {
        status.setText("Unable to load " + label.toLowerCase());
        scope.warn("Unable to render activity diagram", failure);
        return;
      }
      var image = new Image(new ByteArrayInputStream(bytes));
      if (image.isError()) {
        status.setText("Invalid diagram image");
        return;
      }
      var view = new ImageView(image);
      view.setPreserveRatio(true);
      view.setSmooth(true);
      var scroll = new ScrollPane(view);
      scroll.setPannable(true);
      scroll.setFitToWidth(true);
      view.fitWidthProperty().bind(javafx.beans.binding.Bindings.createDoubleBinding(
          () -> Math.max(1, Math.min(image.getWidth(), scroll.getViewportBounds().getWidth())),
          scroll.viewportBoundsProperty()));
      pane.getChildren().setAll(scroll);
    }));
  }
}
