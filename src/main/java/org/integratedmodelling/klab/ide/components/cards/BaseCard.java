package org.integratedmodelling.klab.ide.components.cards;

import javafx.scene.layout.BorderPane;
import javafx.scene.layout.Region;

/**
 * Base class for cards that describe an asset. Cards can be created in extended or compact mode:
 * the compact must be fast to draw and with size suitable for a tooltip, while the extended mode is
 * more detailed and meant to be sized for the knowledge inspector, with a compatible height and
 * full-width display.
 *
 * @param <T>
 */
public abstract class BaseCard<T> extends BorderPane {

  protected final T asset;
  protected final boolean extended;

  protected BaseCard(T asset, boolean extended) {
    this(asset, extended, true);
  }

  protected BaseCard(T asset, boolean extended, boolean initialize) {
    this.asset = asset;
    this.extended = extended;
    if (extended) {
      setMinSize(0, 0);
      setPrefSize(Region.USE_COMPUTED_SIZE, Region.USE_COMPUTED_SIZE);
      setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
      getStyleClass().add("inspector-card");
    } else {
      setPrefSize(300, 220);
    }
    if (initialize) {
      drawContent();
    }
  }

  protected abstract void drawContent();
}
