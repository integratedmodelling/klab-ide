package org.integratedmodelling.klab.ide.components.cards;

import atlantafx.base.controls.Card;
import javafx.scene.layout.Border;

/**
 * Base class for cards that describe an asset. Cards can be created in extended or compact mode:
 * the compact must be fast to draw and with size suitable for a tooltip, while the extended mode is
 * more detailed and meant to be sized for the knowledge inspector, with a compatible height and
 * full-width display.
 *
 * @param <T>
 */
public abstract class BaseCard<T> extends Card {

  protected final T asset;
  protected final boolean extended;

  protected BaseCard(T asset, boolean extended) {
    this.asset = asset;
    this.extended = extended;
//    setBorder(Border.EMPTY);
    setMinSize(extended ? 800 : 300, extended ? 400 : 220);
    setMaxSize(extended ? Double.MAX_VALUE : 300, extended ? 400 : 220);
    drawContent();
  }

  protected abstract void drawContent();
}
