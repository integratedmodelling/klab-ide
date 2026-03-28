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

  /**
   * Creates an IconLabel whose colour is specified as a CSS expression rather than a Java
   * {@link Color}. This allows AtlantaFX CSS lookup values (e.g. {@code "-color-accent-fg"}) to
   * be used, so the icon colour follows the active theme.
   *
   * <p>Unlike the {@link #IconLabel(Ikon, int, Color)} constructor, this variant does NOT call
   * {@link #setTextFill}, which would override CSS. Instead it applies the expression via
   * {@link #setStyle} so the CSS engine resolves it at render time.
   *
   * @param ikon      the Ikonli icon to render
   * @param size      font size in points
   * @param cssColor  a CSS color value or lookup, e.g. {@code "-color-accent-fg"}
   */
  public IconLabel(Ikon ikon, int size, String cssColor) {
    setIconText(ikon, size);
    setStyle("-fx-text-fill: " + cssColor + ";");
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

  private void setIconText(Ikon ikon, int size) {
    IkonHandler ikonHandler = IkonResolver.getInstance().resolve(ikon.getDescription());
    Font font = (Font) ikonHandler.getFont();
    setFont(new Font(font.getFamily(), size));
    int code = ikon.getCode();
    if (code <= '\uFFFF') {
      setText(String.valueOf((char) code));
    } else {
      setText(new String(Character.toChars(code)));
    }
  }

}
