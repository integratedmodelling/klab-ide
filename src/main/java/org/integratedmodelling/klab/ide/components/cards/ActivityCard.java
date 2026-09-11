package org.integratedmodelling.klab.ide.components.cards;

import atlantafx.base.controls.Tile;
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
      if (asset.getMetadata() != null) {
        addChart(body, Metadata.IM_RESOLUTION_GRAPH, "Resolution graph");
        addChart(body, Metadata.IM_DATAFLOW_GRAPH, "Contextualization plan");
      }
      if (asset.getStackTrace() != null) {
        var trace = new TextResult(asset.getStackTrace());
        trace.setPrefHeight(230);
        trace.setMaxHeight(230);
        trace.setMessageStyle(Styles.BORDERED, Styles.DANGER, Styles.SMALL);
        body.getChildren().add(trace);
      }
    }
    return body;
  }

  private void addChart(VBox body, String key, String label) {
    if (asset.getMetadata().get(key) instanceof FlowChart) {
      body.getChildren().add(new Label(label + " available (rendering not yet supported)"));
    }
  }
}
