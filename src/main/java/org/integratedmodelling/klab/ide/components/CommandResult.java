package org.integratedmodelling.klab.ide.components;

import atlantafx.base.controls.Message;
import atlantafx.base.util.BBCodeParser;
import javafx.scene.Node;
import javafx.scene.control.TextArea;
import javafx.scene.control.TreeView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import org.integratedmodelling.common.data.Tree;
import org.integratedmodelling.klab.api.cli.FormattedString;
import org.integratedmodelling.klab.ide.components.generic.BBCodeRenderer;

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
    /**
     * TODO collections should become tables and trees should become tree tables. We can install
     * renderers for other classes to become JavaFX components.
     */
    var node =
        switch (result) {
          case FormattedString formattedString ->
              BBCodeParser.createLayout(formattedString.render(BBCodeRenderer.INSTANCE));
          //      case Tree tree ->
          //          content.getChildren().add(new TreeView(tree));
          //      case Collection collection ->
          case null -> new Message(null, "No result");
          default -> new TextResult("Unknown result type");
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
