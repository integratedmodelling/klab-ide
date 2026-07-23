package org.integratedmodelling.klab.ide.components.generic;

import java.util.concurrent.Callable;
import javafx.scene.Cursor;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.Tooltip;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import org.integratedmodelling.klab.api.services.runtime.Notification;
import org.integratedmodelling.klab.ide.KlabIDEController;
import org.kordamp.ikonli.Ikon;
import org.kordamp.ikonli.IkonHandler;
import org.kordamp.ikonli.javafx.IkonResolver;

/** A button displaying an Ikonli icon, optionally behaving as a toggle button. */
public abstract class IconButton extends ToggleButton {

  private final boolean toggle;
  private final Color onColor;
  private final Color offColor;
  private final String onCssColor;
  private final String offCssColor;

  public static IconButton toggle(
      Ikon ikon, int size, Color colorOn, Color colorOff, Callable<Boolean> toggleAction) {
    return new IconButton(ikon, size, colorOn, colorOff, true) {

      @Override
      protected void action() {
        try {
          toggleAction.call();
        } catch (Exception e) {
          KlabIDEController.instance().handleNotification(Notification.error(e));
        }
      }
    };
  }

  public static IconButton toggle(
      Ikon ikon, int size, String cssColorOn, String cssColorOff, Callable<Boolean> toggleAction) {
    return new IconButton(ikon, size, cssColorOn, cssColorOff, true) {

      @Override
      protected void action() {
        try {
          toggleAction.call();
        } catch (Exception e) {
          KlabIDEController.instance().handleNotification(Notification.error(e));
        }
      }
    };
  }

  public static IconButton of(
      Ikon ikon, int size, Color colorOn, Color colorOff, Callable<Boolean> action) {
    return new IconButton(ikon, size, colorOn, colorOff, false) {

      @Override
      protected void action() {
        try {
          action.call();
        } catch (Exception e) {
          KlabIDEController.instance().handleNotification(Notification.error(e));
        }
      }
    };
  }

  public static IconButton of(
      Ikon ikon, int size, String cssColorOn, String cssColorOff, Callable<Boolean> action) {
    return new IconButton(ikon, size, cssColorOn, cssColorOff, false) {

      @Override
      protected void action() {
        try {
          action.call();
        } catch (Exception e) {
          KlabIDEController.instance().handleNotification(Notification.error(e));
        }
      }
    };
  }

  public IconButton(Ikon ikon, int size, Color colorOn, Color colorOff, boolean toggle) {
    this.toggle = toggle;
    onColor = colorOn;
    offColor = colorOff;
    onCssColor = null;
    offCssColor = null;
    setIconText(ikon, size);
    initializeInteraction();
  }

  public IconButton(Ikon ikon, int size, String cssColorOn, String cssColorOff, boolean toggle) {
    this.toggle = toggle;
    onColor = null;
    offColor = null;
    onCssColor = cssColorOn;
    offCssColor = cssColorOff;
    setIconText(ikon, size);
    initializeInteraction();
  }

  /** Sets whether this button is toggled. Equivalent to {@link #setSelected(boolean)}. */
  public void setToggled(boolean toggled) {
    setSelected(toggled);
  }

  /** Returns whether this button is toggled. Equivalent to {@link #isSelected()}. */
  public boolean isToggled() {
    return isSelected();
  }

  public IconButton styleClass(String... styles) {
    getStyleClass().addAll(styles);
    return this;
  }

  public IconButton tooltip(String tooltip) {
    var tool = new Tooltip(tooltip);
    tool.setShowDelay(new javafx.util.Duration(100));
    Tooltip.install(this, tool);
    return this;
  }

  public IconButton enabled(boolean enabled) {
    setDisable(!enabled);
    return this;
  }

  protected abstract void action();

  private void initializeInteraction() {
    getStyleClass().add("klab-icon-button");
    if (!toggle) {
      getStyleClass().remove("toggle-button");
      getStyleClass().add("button");
    }
    setCursor(Cursor.HAND);
    selectedProperty().addListener((observable, oldValue, toggled) -> updateIconColor(toggled));
    updateIconColor(isSelected());
    setOnAction(
        event -> {
          if (!toggle) {
            setSelected(false);
          }
          action();
        });
  }

  private void updateIconColor(boolean toggled) {
    if (onColor != null) {
      setTextFill(toggled ? onColor : offColor);
    } else {
      setStyle("-fx-text-fill: " + (toggled ? onCssColor : offCssColor) + ";");
    }
  }

  private void setIconText(Ikon ikon, int size) {
    IkonHandler ikonHandler = IkonResolver.getInstance().resolve(ikon.getDescription());
    Font font = (Font) ikonHandler.getFont();
    setFont(new Font(font.getFamily(), size));
    int code = ikon.getCode();
    setText(code <= '\uFFFF' ? String.valueOf((char) code) : new String(Character.toChars(code)));
  }
}
