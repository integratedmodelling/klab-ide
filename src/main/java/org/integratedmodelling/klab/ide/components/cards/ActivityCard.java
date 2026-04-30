package org.integratedmodelling.klab.ide.components.cards;

import atlantafx.base.controls.Tile;
import atlantafx.base.theme.Styles;
import atlantafx.base.util.BBCodeParser;
import javafx.scene.Node;
import javafx.scene.control.Label;
import org.integratedmodelling.klab.api.knowledge.observation.scale.time.TimeInstant;
import org.integratedmodelling.klab.api.provenance.Activity;
import org.integratedmodelling.klab.ide.Theme;

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
    setHeader(tile);
    setBody(createBody());
    setFooter(createFooter());
  }

  private Node createFooter() {
    return new Label(
        TimeInstant.create(asset.getStart()) + " to" + TimeInstant.create(asset.getEnd()));
  }

  private Node createBody() {
    if (extended) {
      if (asset.getMetadata().containsKey("dataflow")) {
        var ret =
            BBCodeParser.createFormattedText(asset.getMetadata().get("dataflow", String.class));
        return ret;
      }
    }
    // TODO
    return null;
  }
}
