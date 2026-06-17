package org.integratedmodelling.klab.ide.components.cards;

import atlantafx.base.controls.Tile;
import atlantafx.base.theme.Styles;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.layout.HBox;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.ide.Theme;

public class ObservationCard extends BaseCard<Observation> {

  public ObservationCard(Observation asset, boolean extended) {
    super(asset, extended);
  }

  @Override
  protected void drawContent() {
    var tile = new Tile();

    tile.setTitle("Observation " + Theme.getLabel(asset));
    tile.setDescription(Theme.getDescription(asset));
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
    var ret = new HBox();
    ret.setSpacing(10);
    ret.setPadding(new Insets(10));
    return ret;
  }

  private Node createBody() {
    var ret = new HBox();
    ret.setSpacing(10);
    ret.setPadding(new Insets(10));
    ret.getChildren().add(new GeometryCard(asset.getGeometry(), true));
    ret.getChildren().add(new MetadataCard(asset.getMetadata()));
    return ret;
  }
}
