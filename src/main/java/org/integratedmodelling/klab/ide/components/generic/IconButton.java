package org.integratedmodelling.klab.ide.components.generic;

import java.util.concurrent.Callable;
import javafx.scene.Cursor;
import javafx.scene.paint.Color;
import org.integratedmodelling.klab.api.services.runtime.Notification;
import org.integratedmodelling.klab.ide.KlabIDEController;
import org.kordamp.ikonli.Ikon;

public abstract class IconButton extends IconLabel {

  private boolean toggle = false;
  private boolean pressed = false;
  private Color onColor;
  private String onCssColor;

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

  public void setToggled(boolean toggled) {
    // TODO adjust colors
    if (onCssColor != null) {
      if (toggled) {
        setStyle("-fx-background-color: " + onCssColor);
      } else {
        // ehm
        setStyle("-fx-background-color: " + getStyle().split(": ")[1]);
      }
    } else if (onColor != null) {
      if (toggled) {
        setStyle("-fx-background-color: " + onColor.toString());
      } else {
        setStyle("-fx-background-color: " + getStyle().split(": ")[1]);
      }
    }
  }

  public IconButton(Ikon ikon, int size, Color color, Color offColor, boolean toggle) {
    super(ikon, size, offColor);
    this.toggle = toggle;
    this.onColor = color;
    initializeInteraction();
  }

  public IconButton(Ikon ikon, int size, String cssColor, String offCssColor, boolean toggle) {
    super(ikon, size, cssColor);
    this.toggle = toggle;
    this.onCssColor = offCssColor;
    initializeInteraction();
  }

  private void initializeInteraction() {
    setOnMouseEntered(event -> setOpacity(0.8));
    setOnMouseExited(event -> setOpacity(1.0));
    setCursor(Cursor.HAND);
    setOnMouseClicked(
        event -> {
          if (toggle) {
            pressed = !pressed;
            setToggled(pressed);
          }
          action();
        });
  }

  protected abstract void action();
}
