package org.integratedmodelling.klab.ide.components;

import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import org.integratedmodelling.klab.ide.Theme;

public class InspectorView extends HBox {

  public Object currentObject = null;

  public InspectorView() {
    super();
    setMinHeight(300);
    setMaxHeight(300);
    setPrefWidth(Double.MAX_VALUE);
  }

  public Object getCurrentObject() {
    return currentObject;
  }

  public void inspect(Object value) {

    currentObject = value;

    Platform.runLater(
        () -> {
          // TODO keep previous for navigation
          getChildren().clear();

          if (value == null) {
            return;
          }

          var component = Theme.getDisplayObject(value, Theme.Detail.CARD);
          if (component instanceof Node node) {
            HBox.setHgrow(node, Priority.ALWAYS);
            // TODO set up navigation for previous components
            getChildren().add(node);
          }
        });
  }
}
