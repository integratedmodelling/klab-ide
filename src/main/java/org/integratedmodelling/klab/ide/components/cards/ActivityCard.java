package org.integratedmodelling.klab.ide.components.cards;

import atlantafx.base.controls.Tile;
import org.integratedmodelling.klab.api.provenance.Activity;

public class ActivityCard extends BaseCard<Activity> {

  public ActivityCard(Activity asset, boolean extended) {
    super(asset, extended);
  }

  @Override
  protected void drawContent() {
    var tile = new Tile();
    tile.setTitle("Activity");
    tile.setDescription(asset.getDescription());
    setHeader(tile);
  }
}
