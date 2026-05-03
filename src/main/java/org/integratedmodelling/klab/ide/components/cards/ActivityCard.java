package org.integratedmodelling.klab.ide.components.cards;

import atlantafx.base.controls.Tile;
import atlantafx.base.theme.Styles;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Label;
import org.integratedmodelling.klab.api.knowledge.observation.scale.time.TimeInstant;
import org.integratedmodelling.klab.api.provenance.Activity;
import org.integratedmodelling.klab.ide.Theme;
import org.integratedmodelling.klab.ide.components.TextResult;

public class ActivityCard extends BaseCard<Activity> {

  public ActivityCard(Activity asset, boolean extended) {
    super(asset, extended);
  }

  @Override
  protected void drawContent() {
    var tile = new Tile();

    tile.setTitle("Activity");
    tile.setDescription(asset.getDescription());
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
        TimeInstant.create(asset.getStart()) + " to " + TimeInstant.create(asset.getEnd()));
  }

  private Node createBody() {
    if (extended) {
      if (asset.getMetadata().containsKey("dataflow")) {
        var ret = new TextResult(asset.getMetadata().get("dataflow", String.class));
        ret.setPrefHeight(230);
        ret.setMaxHeight(230);
        ret.setMessageStyle(Styles.BORDERED, Styles.SUCCESS, Styles.SMALL);
        return ret;
      }
    }
    // TODO
    return null;
  }
}
