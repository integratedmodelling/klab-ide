package org.integratedmodelling.klab.ide.components.cards;

import atlantafx.base.controls.Tile;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import org.integratedmodelling.klab.api.digitaltwin.GraphModel;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.ide.IDEContextScope;
import org.integratedmodelling.klab.ide.KlabIDEController;

public class ObservationCard extends BaseCard<Observation> {

  public ObservationCard(Observation asset, IDEContextScope scope, boolean extended) {
    super(asset, scope, extended);
  }

  @Override
  protected void drawContent() {
    var tile = new Tile();
    setCenter(createBody());
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

    //    ret.getChildren().add(new AssetIdentityCard(asset, true));
    var leftBox = new VBox();
    var geom = new GeometryCard(asset.getGeometry(), true);
    geom.setPrefWidth(200);
    geom.setPrefHeight(200);
    leftBox.getChildren().add(geom);
    var relationshipCard =
        new RelationshipCard(
            asset,
            KlabIDEController.instance().getFocalScope(),
            GraphModel.Relationship.Direction.INCOMING,
            GraphModel.Relationship.Direction.OUTGOING);
    relationshipCard.setPrefWidth(200);
    VBox.setVgrow(relationshipCard, Priority.ALWAYS);
    leftBox.getChildren().add(relationshipCard);
    var value = new HBox(); // ValueCard(asset, KlabIDEController.instance().getFocalScope(), true);

    HBox.setHgrow(value, Priority.ALWAYS);
    VBox.setVgrow(value, Priority.ALWAYS);
    ret.getChildren().add(leftBox);
    ret.getChildren().add(value);
    ret.getChildren()
        .add(
            new MetadataCard(
                asset.getMetadata(),
                new MetadataCard.Options().title("Metadata").emptyTitle("Empty metadata")));
    return ret;
  }
}
