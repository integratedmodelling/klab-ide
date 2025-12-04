package org.integratedmodelling.klab.ide.components.generic;

import javafx.scene.control.Label;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import org.kordamp.ikonli.Ikon;
import org.kordamp.ikonli.IkonHandler;
import org.kordamp.ikonli.javafx.IkonResolver;

/**
 * Trick class to use Ikonli without FontIcon, whose CSS specs are hard-coded and can only be
 * changed globally.
 */
public class IconLabel extends Label {

  public IconLabel(Ikon ikon, int size, Color color) {
    set(ikon, size, color);
  }

  public void set(Ikon ikon, int size, Color color) {
    IkonHandler ikonHandler = IkonResolver.getInstance().resolve(ikon.getDescription());
    Font font = (Font) ikonHandler.getFont();
    Font sizedFont = new Font(font.getFamily(), size);
    setFont(sizedFont);
    setTextFill(color);
    int code = ikon.getCode();
    if (code <= '\uFFFF') {
      setText(String.valueOf((char) code));
    } else {
      char[] charPair = Character.toChars(code);
      String symbol = new String(charPair);
      setText(symbol);
    }
  }

}
