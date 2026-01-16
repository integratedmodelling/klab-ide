package org.integratedmodelling.klab.ide.components.cards;

import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.provenance.Activity;

public class ObservationCard extends BaseCard<Observation> {

  public ObservationCard(Observation asset, boolean extended) {
    super(asset, extended);
  }

  @Override
  protected void drawContent() {}
}
