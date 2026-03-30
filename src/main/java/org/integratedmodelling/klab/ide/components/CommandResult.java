package org.integratedmodelling.klab.ide.components;

import atlantafx.base.controls.Message;
import atlantafx.base.theme.Styles;
import atlantafx.base.util.BBCodeParser;
import javafx.scene.Node;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import org.integratedmodelling.common.utils.Utils;
import org.integratedmodelling.klab.api.cli.FormattedString;
import org.integratedmodelling.klab.api.exceptions.KlabCommandLineError;
import org.integratedmodelling.klab.ide.components.generic.BBCodeRenderer;
import org.kordamp.ikonli.Ikon;
import org.kordamp.ikonli.materialdesign.MaterialDesign;

public class CommandResult extends Components.BaseComponent {

  private final Object result;

  public CommandResult(Object result, String title) {
    super(Components.Type.Object, title, false);
    this.result = result;
    createContent();
  }

  @Override
  public Ikon getIcon() {
    return MaterialDesign.MDI_CONSOLE;
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
          case Throwable throwable -> {
            var title =
                throwable instanceof KlabCommandLineError
                    ? null
                    : Utils.Paths.getLast(throwable.getClass().getCanonicalName(), '.');
            var message =
                throwable instanceof KlabCommandLineError
                    ? throwable.getMessage()
                    : Utils.Exceptions.stackTrace(throwable);
            var ret = new TextResult(message, title);
            ret.setMessageStyle(Styles.DANGER);
            yield ret;
          }
          case FormattedString formattedString ->
              BBCodeParser.createLayout(formattedString.render(BBCodeRenderer.INSTANCE));
          //      case Tree tree ->
          //          content.getChildren().add(new TreeView(tree));
          //      case Collection collection ->
          case null -> new Message(null, "No result");
          default -> new TextResult("Unknown result type: " + result.getClass().getSimpleName());
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
