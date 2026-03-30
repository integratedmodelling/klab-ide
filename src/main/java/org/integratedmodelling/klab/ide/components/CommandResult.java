package org.integratedmodelling.klab.ide.components;

import atlantafx.base.controls.Message;
import atlantafx.base.theme.Styles;
import atlantafx.base.theme.Tweaks;
import atlantafx.base.util.BBCodeParser;
import java.util.Collection;
import java.util.Map;
import javafx.beans.property.SimpleStringProperty;
import javafx.scene.Node;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeTableColumn;
import javafx.scene.control.TreeTableView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import org.integratedmodelling.common.data.Tree;
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
    Region node = null;

    if (result instanceof Tree<?>) {
      node = createTreeContent((Tree<Object>) result);
    } else if (result instanceof Collection<?>) {
      node = createCollectionContent((Collection<?>) result);
    } else {

      node =
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
    }

    HBox.setHgrow(node, Priority.ALWAYS);
    HBox.setHgrow(content, Priority.ALWAYS);
    node.prefWidthProperty().bind(content.widthProperty());
    node.prefHeightProperty().bind(content.heightProperty());
    content.getChildren().add(node);
    getChildren().add(content);
    return content;
  }

  private Region createCollectionContent(Collection<?> result) {

    if (result.isEmpty()) {
      return new Message(null, "No results");
    }

    TableView<Object> tableView = new TableView<>();
    //    tableView.setColumnResizePolicy(TreeTableView.UNCONSTRAINED_RESIZE_POLICY);
    tableView
        .getStyleClass()
        .addAll(Styles.DENSE, Styles.STRIPED, Tweaks.EDGE_TO_EDGE, Tweaks.NO_HEADER);

    tableView.setMinHeight(Math.min(360, result.size() * 40));

    if (result.isEmpty()) {
      return tableView;
    }

    // Get fields from the first object to determine columns
    Object firstItem = result.iterator().next();
    Map<String, String> fields = getFieldsFromObject(firstItem);

    // Create columns without headers
    for (Map.Entry<String, String> field : fields.entrySet()) {
      TableColumn<Object, String> column = new TableColumn<>();
      String fieldName = field.getKey();

      column.setCellValueFactory(
          param -> {
            if (param.getValue() != null) {
              Map<String, String> objectFields = getFieldsFromObject(param.getValue());
              return new SimpleStringProperty(objectFields.getOrDefault(fieldName, ""));
            }
            return new SimpleStringProperty("");
          });

      tableView.getColumns().add(column);
    }

    // Add all items from the collection to the table
    tableView.getItems().addAll(result);

    return tableView;
  }

  private Region createTreeContent(Tree<Object> result) {

    if (result.vertexSet().isEmpty()) {
      return new Message(null, "No results");
    }

    TreeTableView<Object> treeTableView = new TreeTableView<>();
    treeTableView.setShowRoot(true);
    treeTableView.setColumnResizePolicy(TreeTableView.UNCONSTRAINED_RESIZE_POLICY);
    treeTableView
        .getStyleClass()
        .addAll(Styles.DENSE, Styles.STRIPED, Tweaks.EDGE_TO_EDGE, Tweaks.NO_HEADER);

    treeTableView.setMinHeight(260);

    // Create root tree item
    TreeItem<Object> rootItem = createTreeItem(result.root(), result);
    treeTableView.setRoot(rootItem);

    // Get fields from the first object to determine columns
    Map<String, String> fields = getFieldsFromObject(result.root());

    // Create columns without headers
    for (Map.Entry<String, String> field : fields.entrySet()) {
      TreeTableColumn<Object, String> column = new TreeTableColumn<>();
      String fieldName = field.getKey();

      column.setCellValueFactory(
          param -> {
            if (param.getValue() != null && param.getValue().getValue() != null) {
              Map<String, String> objectFields = getFieldsFromObject(param.getValue().getValue());
              return new SimpleStringProperty(objectFields.getOrDefault(fieldName, ""));
            }
            return new SimpleStringProperty("");
          });

      treeTableView.getColumns().add(column);
    }

    return treeTableView;
  }

  private TreeItem<Object> createTreeItem(Object value, Tree<Object> tree) {
    TreeItem<Object> item = new TreeItem<>(value);

    for (var child : tree.children(value)) {
      item.getChildren().add(createTreeItem(child, tree));
    }

    item.setExpanded(true);
    return item;
  }

  /**
   * Returns a map of field names to field values for the given object. TODO this is a stub
   * implementation that should be customized based on the actual object types being displayed in
   * the tree.
   *
   * @param object the object to extract fields from
   * @return a map of field names to their string representations
   */
  private Map<String, String> getFieldsFromObject(Object object) {
    // Stub implementation - returns a single field with the object's toString()
    if (object == null) {
      return Map.of("Value", "");
    }
    return Map.of("Value", object.toString());
  }
}
