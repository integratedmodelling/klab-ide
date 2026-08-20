package org.integratedmodelling.klab.ide.pages;

import atlantafx.base.controls.ModalPane;
import atlantafx.base.theme.Styles;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.Side;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import org.integratedmodelling.klab.api.view.View;
import org.integratedmodelling.klab.ide.Theme;
import org.integratedmodelling.klab.ide.components.generic.IconLabel;
import org.kordamp.ikonli.Ikon;
import org.kordamp.ikonli.javafx.FontIcon;
import org.kordamp.ikonli.material2.Material2MZ;

/** The generic browser with a modal index on the left. */
public abstract class BrowsablePage<T extends Node, A> extends StackPane implements Page, View {

  protected static final int BROWSER_WIDTH = 280;
  private final TabPane tabPane;
  private final Label messageLabel;
  private final Label descriptionLabel;

  private static class Dialog extends VBox {

    public Dialog(int width, int height) {
      super();

      setSpacing(10);
      setAlignment(Pos.CENTER);
      setFillWidth(true);
      setMinSize(width, height);
      setMaxSize(width, height);
      setStyle("-fx-background-color: -color-bg-default;");
    }
  }

  private final ModalPane modalPane = new ModalPane();
  private Dialog browserArea;

  protected BrowsablePage() {
    this("", "");
  }

  protected BrowsablePage(String message) {
    this(message, "");
  }

  protected BrowsablePage(String message, String description) {
    super();
    this.browserArea = new Dialog(BROWSER_WIDTH, -1);
    this.browserArea.setAlignment(Pos.TOP_CENTER);
    this.browserArea.setPadding(new Insets(2.0));
    this.tabPane = new TabPane();
    this.tabPane.getStyleClass().addAll(Styles.DENSE, Styles.SMALL);
    this.messageLabel = new Label(message == null ? "" : message);
    this.messageLabel.getStyleClass().add(Styles.TITLE_2);
    this.messageLabel.setMaxWidth(Double.MAX_VALUE);
    this.messageLabel.setAlignment(Pos.CENTER);
    this.messageLabel.setPadding(new Insets(10, 10, 0, 10));
    this.messageLabel.setStyle("-fx-text-fill: -color-fg-subtle; -fx-opacity: 0.65;");
    this.descriptionLabel = new Label(description == null ? "" : description);
    this.descriptionLabel.getStyleClass().addAll(Styles.TEXT_SMALL, Styles.TEXT_MUTED);
    this.descriptionLabel.setMaxWidth(Double.MAX_VALUE);
    this.descriptionLabel.setAlignment(Pos.CENTER);
    this.descriptionLabel.setPadding(new Insets(0, 10, 10, 10));
    this.descriptionLabel.setWrapText(true);
    this.descriptionLabel.setStyle("-fx-opacity: 0.65;");
    var noEditors = Bindings.size(this.tabPane.getTabs()).isEqualTo(1);
    this.messageLabel
        .visibleProperty()
        .bind(this.messageLabel.textProperty().isNotEmpty().and(noEditors));
    this.messageLabel.managedProperty().bind(this.messageLabel.visibleProperty());
    this.descriptionLabel
        .visibleProperty()
        .bind(this.descriptionLabel.textProperty().isNotEmpty().and(noEditors));
    this.descriptionLabel.managedProperty().bind(this.descriptionLabel.visibleProperty());
    var menuTab = new Tab("");
    menuTab.setGraphic(
        new IconLabel(Material2MZ.MENU, 24, "-color-fg-default"));
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
    var messageOverlay = new VBox(6, messageLabel, descriptionLabel);
    messageOverlay.setAlignment(Pos.CENTER);
    messageOverlay.setMouseTransparent(true);
    messageOverlay
        .visibleProperty()
        .bind(messageLabel.visibleProperty().or(descriptionLabel.visibleProperty()));
    messageOverlay.managedProperty().bind(messageOverlay.visibleProperty());
    var editorArea = new StackPane(tabPane, messageOverlay);
    StackPane.setAlignment(messageOverlay, Pos.CENTER);
    getChildren().addAll(editorArea, modalPane);
  }

  public String getMessage() {
    return messageLabel.getText();
  }

  public void setMessage(String message) {
    messageLabel.setText(message == null ? "" : message);
  }

  public String getDescription() {
    return descriptionLabel.getText();
  }

  public void setDescription(String description) {
    descriptionLabel.setText(description == null ? "" : description);
  }

  protected void onTabClosed(Tab closedTab) {
    if (closedTab.getContent() instanceof EditorPage editor) {
      assetEditorClosed((T) editor);
      editor.onVisualize(false);
    }
  }

  protected void onTabSelected(Tab selectedTab) {
    if (selectedTab.getContent() instanceof EditorPage editor) {
      assetEditorSelected((T) editor);
      editor.onVisualize(true);
    }
  }

  protected A getSelectedAsset() {
    var editor = getSelectedEditor();
    if (editor != null) {
      return (A) editor.getEditedAsset();
    }
    return null;
  }

  public EditorPage<?, ?> getSelectedEditor() {
    Tab selectedTab = tabPane.getSelectionModel().getSelectedItem();
    if (selectedTab != null && selectedTab.getContent() instanceof EditorPage<?, ?> editor) {
      return editor;
    }
    return null;
  }

  protected abstract void assetEditorSelected(T assetEditor);

  protected abstract void assetEditorClosed(T assetEditor);

  protected record HeaderAction(Ikon icon, String tooltip, Runnable action) {
    public HeaderAction {}
  }

  protected Node makeHeader(String title, Runnable addAction) {
    return makeHeader(
        title, new HeaderAction(Theme.ADD_ASSET_ICON, "Create a new asset", addAction));
  }

  protected Node makeHeader(String title, HeaderAction... actions) {
    var workspacesLabel = new Label(title);
    workspacesLabel.getStyleClass().add(Styles.TITLE_4);
    workspacesLabel.setAlignment(Pos.CENTER_LEFT);
    workspacesLabel.setMaxWidth(Double.MAX_VALUE);
    workspacesLabel.setPadding(new Insets(0, 0, 0, 8));
    workspacesLabel.setStyle("-fx-text-fill: -color-fg-subtle;");
    HBox.setHgrow(workspacesLabel, Priority.ALWAYS);

    var buttons = new HBox(0);
    buttons.setAlignment(Pos.CENTER_RIGHT);
    for (var action : actions) {
      var button =
          new Button(
              "", new IconLabel(action.icon(), 16, Theme.CURRENT_THEME.getDefaultTextColor()));
      button.getStyleClass().addAll(Styles.FLAT, Styles.BUTTON_CIRCLE);
      button.setOnAction(event -> action.action().run());
      button.setTooltip(new Tooltip(action.tooltip()));
      buttons.getChildren().add(button);
    }
    var ret = new HBox(workspacesLabel, buttons);
    ret.setMaxWidth(Double.MAX_VALUE);
    ret.setPrefWidth(BROWSER_WIDTH);
    return ret;
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

  /** Replace the graphic of the tab hosting the supplied editor. */
  protected void setEditorGraphic(T editor, Node graphic) {
    Platform.runLater(
        () ->
            tabPane.getTabs().stream()
                .filter(tab -> tab.getContent() == editor)
                .findFirst()
                .ifPresent(tab -> tab.setGraphic(graphic)));
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

  @Override
  public void show() {}

  @Override
  public void hide() {}

  @Override
  public void enable() {}

  @Override
  public void disable() {}

  @Override
  public boolean isShown() {
    return false;
  }

  @Override
  public boolean isEnabled() {
    return false;
  }
}
