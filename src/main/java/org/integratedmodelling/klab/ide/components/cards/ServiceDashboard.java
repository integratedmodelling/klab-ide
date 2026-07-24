package org.integratedmodelling.klab.ide.components.cards;

import javafx.scene.Node;
import org.integratedmodelling.klab.api.services.KlabService;

/**
 * TODO needs:
 *
 * <p>Links to http-accessible pages (API, capabilities, status, health) and health/status
 * indicators;
 *
 * <p>Graphs for load and memory (update at intervals based on local status and config)
 *
 * <p>Component carousel widget with status and update buttons
 *
 * <p>Counter of active connections and requests (if admin)
 */
public class ServiceDashboard extends BaseAssetViewComponent {

  public ServiceDashboard(KlabService service, String title, boolean initialize) {
    super(Type.Object, title, initialize);
  }

  @Override
  protected Node createContent() {
    return null;
  }
}
