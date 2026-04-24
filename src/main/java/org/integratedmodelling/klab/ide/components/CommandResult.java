package org.integratedmodelling.klab.ide.components;

import atlantafx.base.controls.Message;
import atlantafx.base.theme.Styles;
import atlantafx.base.theme.Tweaks;
import atlantafx.base.util.BBCodeParser;
import com.google.common.collect.Table;
import java.util.Collection;
import java.util.Map;

import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.scene.Node;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeTableColumn;
import javafx.scene.control.TreeTableView;
import javafx.scene.layout.*;
import org.integratedmodelling.common.data.Tree;
import org.integratedmodelling.common.utils.Utils;
import org.integratedmodelling.klab.api.cli.FormattedString;
import org.integratedmodelling.klab.api.configuration.Setting;
import org.integratedmodelling.klab.api.exceptions.KlabCommandLineError;
import org.integratedmodelling.klab.api.knowledge.Resource;
import org.integratedmodelling.klab.api.knowledge.Urn;
import org.integratedmodelling.klab.api.lang.kim.KimObservable;
import org.integratedmodelling.klab.api.services.ResourcesService;
import org.integratedmodelling.klab.ide.KlabIDEController;
import org.integratedmodelling.klab.ide.Theme;
import org.integratedmodelling.klab.ide.components.cards.AssetViewComponent;
import org.integratedmodelling.klab.ide.components.cards.BaseAssetViewComponent;
import org.integratedmodelling.klab.ide.components.generic.BBCodeRenderer;
import org.kordamp.ikonli.Ikon;
import org.kordamp.ikonli.materialdesign.MaterialDesign;

public class CommandResult extends BaseAssetViewComponent {

  private final Object result;

  public CommandResult(Object result, String title) {
    super(AssetViewComponent.Type.Object, title, false);
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

    if (Utils.Data.isPOD(result)) {
      node = new TextResult(result.toString());
    } else if (result instanceof Tree<?>) {
      node = createTreeContent((Tree<Object>) result);
    } else if (result instanceof Collection<?>) {
      node = createCollectionContent((Collection<?>) result);
    } else {

      node =
          switch (result) {
            case Region n -> n;
            case Throwable throwable -> {
              /*
               * Check for knowable URNs first
               */
              if (throwable instanceof KlabCommandLineError klabError
                  && KlabIDEController.scope() != null) {
                // check if the command line can be parsed as a URN
                var possibleType = Urn.classify(klabError.getCommandLine());
                switch (possibleType) {
                  case RESOURCE -> {
                    // TODO this must use all services somehow. Probably loop and stop at the first
                    //  non-null result
                    var resource =
                        KlabIDEController.scope()
                            .getService(ResourcesService.class)
                            .retrieve(
                                klabError.getCommandLine(),
                                Resource.class,
                                KlabIDEController.scope());
                    if (resource != null) {
                      // TODO produce the resource view component here
                    }
                  }
                  case KIM_OBJECT -> {
                    // TODO must know the type of KimObject to produce the view - currently can be
                    //  namespace, model, concept definition and symbol definition. It may or may
                    // not
                    //  be cached in the service client so there is no automatic
                  }
                  case OBSERVABLE -> {
                    // TODO this must use all services somehow. Probably loop and stop at the first
                    //  non-null result
                    var observable =
                        KlabIDEController.scope()
                            .getService(ResourcesService.class)
                            .retrieve(
                                klabError.getCommandLine(),
                                KimObservable.class,
                                KlabIDEController.scope());
                    if (observable != null) {
                      // TODO produce the observable view component here
                    }
                  }
                  default -> {}
                }
              }
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
            case FormattedString formattedString -> {
              var ret = BBCodeParser.createLayout(formattedString.render(BBCodeRenderer.INSTANCE));
              ret.setStyle(
                  "-fx-font-family: '"
                      + KlabIDEController.instance()
                          .engine()
                          .getSettings()
                          .get(Setting.MONOSPACE_FONT, String.class)
                      + "';");
              yield ret;
            }
            case Table<?, ?, ?> table -> {
              // TODO implement table view
              yield null;
            }
            //      case Tree tree ->
            //          content.getChildren().add(new TreeView(tree));
            //      case Collection collection ->
            case null -> new Message(null, "No result");
            default -> new TextResult("Unknown result type: " + result.getClass().getSimpleName());
          };
    }

    HBox.setHgrow(node, Priority.ALWAYS);
    HBox.setHgrow(content, Priority.ALWAYS);
    VBox.setVgrow(content, Priority.ALWAYS);
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
    Map<String, Object> fields = getFieldsFromObject(firstItem);

    // Create columns without headers
    for (Map.Entry<String, Object> field : fields.entrySet()) {
      TableColumn<Object, Object> column = new TableColumn<>();
      String fieldName = field.getKey();

      column.setCellValueFactory(
          param -> {
            if (param.getValue() != null) {
              Map<String, Object> objectFields = getFieldsFromObject(param.getValue());
              return new SimpleObjectProperty<Object>(objectFields.getOrDefault(fieldName, ""));
            }
            return new SimpleObjectProperty<>("");
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
    var fields = getFieldsFromObject(result.root());

    // Create columns without headers
    for (var field : fields.entrySet()) {
      TreeTableColumn<Object, Object> column = new TreeTableColumn<>();
      String fieldName = field.getKey();

      column.setCellValueFactory(
          param -> {
            if (param.getValue() != null && param.getValue().getValue() != null) {
              var objectFields = getFieldsFromObject(param.getValue().getValue());
              return new SimpleObjectProperty<>(objectFields.getOrDefault(fieldName, ""));
            }
            return new SimpleObjectProperty<>("");
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
  private Map<String, Object> getFieldsFromObject(Object object) {
    // Stub implementation - returns a single field with the object's toString()
    if (object == null) {
      return Map.of("Value", "");
    }
    return Map.of("Value", Theme.getDisplayObject(object, Theme.Detail.ONE_LINER));
  }
}
