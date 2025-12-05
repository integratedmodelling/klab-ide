package org.integratedmodelling.klab.ide.pages;

import atlantafx.base.controls.ModalPane;
import atlantafx.base.theme.Styles;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.Side;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import org.integratedmodelling.klab.ide.Theme;
import org.integratedmodelling.klab.ide.components.generic.IconLabel;
import org.jline.reader.Editor;
import org.kordamp.ikonli.javafx.FontIcon;
import org.kordamp.ikonli.material2.Material2MZ;

/** The generic browser with a modal index on the left. */
public abstract class BrowsablePage<T extends Node, A> extends StackPane implements Page {

  protected static final int BROWSER_WIDTH = 280;
  private final TabPane tabPane;

  private static class Dialog extends VBox {

    public Dialog(int width, int height) {
      super();

      setSpacing(10);
      setAlignment(Pos.CENTER);
      setMinSize(width, height);
      setMaxSize(width, height);
      setStyle("-fx-background-color: -color-bg-default;");
    }
  }

  private final ModalPane modalPane = new ModalPane();
  private Dialog browserArea;

  protected BrowsablePage() {
    super();
    this.browserArea = new Dialog(BROWSER_WIDTH, -1);
    this.browserArea.setAlignment(Pos.TOP_CENTER);
    this.browserArea.setPadding(new Insets(2.0));
    this.tabPane = new TabPane();
    var menuTab = new Tab("");
    menuTab.setGraphic(
        new IconLabel(Material2MZ.MENU, 24, Theme.CURRENT_THEME.getDefaultTextColor()));
    menuTab.setClosable(false);
    menuTab.setDisable(true);
    menuTab
        .getGraphic()
        .setOnMouseClicked(
            event -> {
              showBrowser();
            });
    this.tabPane.getTabs().add(menuTab);
    this.tabPane
        .getTabs()
        .addListener(
            (javafx.collections.ListChangeListener.Change<? extends Tab> c) -> {
              while (c.next()) {
                if (c.wasRemoved()) {
                  onTabClosed(c.getRemoved().getFirst());
                }
              }
            });
    this.tabPane
        .getSelectionModel()
        .selectedItemProperty()
        .addListener(
            (observable, oldValue, newValue) -> {
              if (newValue != null) {
                onTabSelected(newValue);
              }
            });
    getChildren().addAll(tabPane, modalPane);
  }

  protected void onTabClosed(Tab closedTab) {
    if (closedTab.getContent() instanceof EditorPage<?, ?> editor) {
      var asset = editor.getEditedAsset();
      if (asset != null) {
        assetEditorClosed((A) asset);
      }
      editor.onVisualize(false);
    }
  }

  protected void onTabSelected(Tab selectedTab) {
    if (selectedTab.getContent() instanceof EditorPage<?, ?> editor) {
      var asset = editor.getEditedAsset();
      if (asset != null) {
        assetEditorSelected((A) asset);
      }
      editor.onVisualize(true);
    }
  }

  protected A getSelectedAsset() {
    Tab selectedTab = tabPane.getSelectionModel().getSelectedItem();
    if (selectedTab != null && selectedTab.getContent() instanceof EditorPage<?, ?> editor) {
      return (A) editor.getEditedAsset();
    }
    return null;
  }

  protected abstract void assetEditorSelected(A asset);

  protected abstract void assetEditorClosed(A asset);

  protected Node makeHeader(String title, Runnable addAction) {

    javafx.scene.control.Label workspacesLabel = new javafx.scene.control.Label(title);
    workspacesLabel.setPrefWidth(BrowsablePage.BROWSER_WIDTH - 40);
    workspacesLabel.getStyleClass().add(Styles.TITLE_4);
    workspacesLabel.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
    workspacesLabel.setPadding(new javafx.geometry.Insets(0, 0, 0, 8));
    workspacesLabel.setStyle("-fx-text-fill: -color-fg-subtle;");
    javafx.scene.control.Button addButton =
        new Button(
            "", new IconLabel(Theme.ADD_ASSET_ICON, 20, Theme.CURRENT_THEME.getDefaultTextColor()));
    addButton.getStyleClass().addAll(Styles.FLAT, Styles.BUTTON_CIRCLE);
    HBox alignedButton = new HBox(addButton);
    alignedButton.setAlignment(javafx.geometry.Pos.CENTER_RIGHT);
    addButton.setOnAction(event -> addAction.run());
    return new HBox(workspacesLabel, alignedButton);
  }

  public void selectEditor(EditorPage<?, ?> node) {
    for (var tab : tabPane.getTabs()) {
      if (tab.getContent() == node) {
        tabPane.getSelectionModel().select(tab);
        break;
      }
    }
  }

  public void addEditor(EditorPage<?, ?> node, String title, FontIcon icon) {
    var tab = new Tab(title, node);
    tab.setGraphic(icon);
    Platform.runLater(
        () -> {
          this.tabPane.getTabs().add(tab);
          this.tabPane.getSelectionModel().select(tab);
          node.showContent();
        });
  }

  public boolean isEmpty() {
    return tabPane.getTabs().size() == 1;
  }

  public void removeEditor(EditorPage<?, ?> node) {
    Platform.runLater(
        () -> {
          Tab tab =
              this.tabPane.getTabs().stream()
                  .filter(t -> t.getContent() == node)
                  .findFirst()
                  .orElse(null);
          if (tab != null) {
            tabPane.getTabs().remove(tab);
          }
        });
  }

  protected abstract void defineBrowser(VBox vBox);

  public void hideBrowser() {
    if (modalPane.contentProperty().isBound()) {
      return;
    }
    Platform.runLater(modalPane::hide);
  }

  public void updateBrowser() {

    Platform.runLater(
        () -> {
          this.browserArea.getChildren().removeAll();
          defineBrowser(this.browserArea);
        });
  }

  public void showBrowser() {

    if (modalPane.contentProperty().isBound()) {
      return;
    }

    Platform.runLater(this::doShowBrowser);
  }

  public void doShowBrowser() {
    this.browserArea.getChildren().removeAll();
    defineBrowser(this.browserArea);
    modalPane.setAlignment(Pos.TOP_LEFT);
    modalPane.usePredefinedTransitionFactories(Side.LEFT);
    modalPane.show(browserArea);
  }
}
