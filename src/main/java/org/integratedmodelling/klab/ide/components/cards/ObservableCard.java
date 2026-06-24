package org.integratedmodelling.klab.ide.components.cards;

import org.integratedmodelling.klab.api.knowledge.Observable;

public class ObservableCard extends BaseCard<Observable> {

  public ObservableCard(Observable asset, boolean extended) {
    super(asset, null, extended);
  }

  @Override
  protected void drawContent() {}
}
