package org.integratedmodelling.klab.ide.components;

import atlantafx.base.controls.ToggleSwitch;
import atlantafx.base.theme.Styles;
import javafx.beans.property.BooleanProperty;
import javafx.scene.control.CustomMenuItem;

/** Compact AtlantaFX switch that keeps a graph's relationship menu open while filtering. */
final class GraphLayerMenuItem extends CustomMenuItem {
  private final ToggleSwitch toggle;

  GraphLayerMenuItem(String label) {
    toggle = new ToggleSwitch(label);
    toggle.setLabelPosition(javafx.geometry.HorizontalDirection.RIGHT);
    toggle.getStyleClass().addAll(Styles.SMALL, Styles.TEXT_SMALL);
    toggle.setStyle("-fx-padding: 1 0 1 0;");
    toggle.setMaxWidth(Double.MAX_VALUE);
    toggle.setAccessibleText(label);
    setContent(toggle);
    setHideOnClick(false);
    getStyleClass().addAll(Styles.SMALL, Styles.DENSE);
    setStyle("-fx-padding: 2 8 2 8;");
  }

  BooleanProperty selectedProperty() { return toggle.selectedProperty(); }
  void setSelected(boolean selected) { toggle.setSelected(selected); }
}
