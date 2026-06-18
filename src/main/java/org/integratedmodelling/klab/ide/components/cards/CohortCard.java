package org.integratedmodelling.klab.ide.components.cards;

import atlantafx.base.controls.Tile;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import org.integratedmodelling.klab.api.digitaltwin.GraphModel;
import org.integratedmodelling.klab.api.knowledge.Cohort;

public class CohortCard extends BaseCard<Cohort> {

  public CohortCard(Cohort asset, boolean extended) {
    super(asset, extended);
  }

  @Override
  protected void drawContent() {
    var tile = new Tile();

    //    tile.setTitle("Observation " + Theme.getLabel(asset));
    //    tile.setDescription(Theme.getDescription(asset));
    //    tile.setGraphic(Theme.getGraphics(asset));
    //    setTop(tile);
    setCenter(createBody());
    //    setBottom(createFooter());
    //    if (extended) {
    //      getStyleClass().add(Styles.ELEVATED_2);
    //      setPadding(new Insets(2, 10, 4, 10));
    //    }
  }

  private Node createFooter() {
    var ret = new HBox();
    ret.setSpacing(10);
    ret.setPadding(new Insets(10));
    return ret;
  }

  private Node createBody() {
    var ret = new HBox();
    ret.setSpacing(10);
    ret.setPadding(new Insets(10));
    var geom = new GeometryCard(asset.getGeometry(), true);
    geom.setPrefWidth(200);
    geom.setPrefHeight(200);
    ret.getChildren().add(geom);
    var value = new RelatedCard(asset, GraphModel.Relationship.Direction.OUTGOING);
    value.setPrefHeight(200);
    HBox.setHgrow(value, Priority.ALWAYS);
    ret.getChildren().add(value);
    ret.getChildren().add(new MetadataCard(asset.getMetadata()));
    return ret;
  }
}
