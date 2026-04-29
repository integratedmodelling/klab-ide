package org.integratedmodelling.klab.ide.components.cards;

import atlantafx.base.controls.Tile;
import atlantafx.base.theme.Styles;
import javafx.scene.layout.Border;
import org.integratedmodelling.common.utils.Utils;
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
//    tile.setBorder(Border.EMPTY);
    tile.setGraphic(Theme.getGraphics(asset));
//    tile.setEffect(null);
//    getStyleClass().add(Styles.ELEVATED_4);
    setHeader(tile);
  }
}
