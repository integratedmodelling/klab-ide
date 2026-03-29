package org.integratedmodelling.klab.ide.components;

import javafx.scene.Node;
import javafx.scene.control.TextArea;
import javafx.scene.control.TreeView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import org.integratedmodelling.common.data.Tree;
import org.integratedmodelling.klab.api.cli.FormattedString;

import java.util.Collection;

public class CommandResult extends Components.BaseComponent {

  private final Object result;

  public CommandResult(Object result, String title) {
    super(Components.Type.Object, title, false);
    this.result = result;
    createContent();
  }

  @Override
  protected Node createContent() {
    var content = new Pane();
    var node =
        switch (result) {
          case FormattedString formattedString -> new TextArea(formattedString.toString());
          //      case Tree tree ->
          //          content.getChildren().add(new TreeView(tree));
          //      case Collection collection ->
          case null -> new TextArea("No result");
          default -> new TextArea("Unknown result type");
        };
    
    HBox.setHgrow(node, Priority.ALWAYS);
    HBox.setHgrow(content, Priority.ALWAYS);
    node.prefWidthProperty().bind(content.widthProperty());
    node.prefHeightProperty().bind(content.heightProperty());
    content.getChildren().add(node);
    getChildren().add(content);
    return content;
  }
}
