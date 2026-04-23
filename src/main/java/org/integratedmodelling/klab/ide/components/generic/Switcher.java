package org.integratedmodelling.klab.ide.components.generic;

import java.util.Map;
import javafx.scene.Node;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;

public class Switcher extends Pane {

  private final Map<String, Region> children;

  /**
   * Add the components. Use a linked hash map if order is important. The first component will be
   * the one visualized at the beginning.
   *
   * @param children
   */
  public Switcher(Map<String, Region> children) {
    super();
    this.children = children;
    var first = true;
    for (Node child : children.values()) {
      child.setVisible(first);
      getChildren().add(child);
      if (first) {
        first = false;
      }
    }
  }

  public void show(String id) {
    children.get(id).setVisible(true);
    for (var childId : children.keySet()) {
      children.get(childId).setVisible(childId.equals(id));
    }
  }
}
