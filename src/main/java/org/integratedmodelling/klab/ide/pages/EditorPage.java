package org.integratedmodelling.klab.ide.pages;

import atlantafx.base.theme.Styles;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.geometry.Orientation;
import javafx.geometry.Side;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.util.Duration;
import org.integratedmodelling.klab.ide.IDEContextScope;
import org.integratedmodelling.klab.ide.KlabIDEController;
import org.integratedmodelling.klab.ide.Theme;
import org.integratedmodelling.klab.ide.api.DigitalTwinReactor;
import org.integratedmodelling.klab.ide.components.DigitalTwinControlPanel;
import org.integratedmodelling.klab.ide.components.treeviews.TreeViewClickBehavior;

/**
 * Editor for a first-class container - resource, digital twin or workspace. Editor has a treeview
 * index and an editing area. Each editor can host the control panel for the currently focused
 * digital twin, if any.
 *
 * @param <A> the overall asset being edited
 * @param <T> the individual assets we create editors for
 */
public abstract class EditorPage<A, T> extends BorderPane implements DigitalTwinReactor {

  private final BorderPane browsingArea;
  private final TabPane editorTabs;
  final Timeline clickTimeline = new Timeline();
  Duration clickDuration = Duration.millis(350);
  KeyFrame clickKeyFrame = new KeyFrame(clickDuration);
  boolean isClickTimelinePlaying = false;
  private Map<T, Tab> assetEditors = new HashMap<>();
  private final Map<String, Tab> auxiliaryEditors = new HashMap<>();
  protected DigitalTwinControlPanel digitalTwinControlPanel;
  private TreeView<T> tree;
  private A currentAsset;
  private VBox container;

  //  private boolean isContentShown = false;
  //  private Label digitalTwinLabel;

  public EditorPage(A asset) {
    this.currentAsset = asset;
    this.browsingArea = new BorderPane();
    this.editorTabs = new TabPane();
    this.editorTabs.getStyleClass().add(Styles.TABS_CLASSIC);
    this.editorTabs.setSide(Side.BOTTOM);

    SplitPane splitPane = new SplitPane();
    splitPane.setOrientation(Orientation.HORIZONTAL);
    splitPane.getItems().addAll(editorTabs, browsingArea);
    splitPane.setDividerPositions(0.7);
    this.setCenter(splitPane);

    KlabIDEController.instance().registerDigitalTwinReactor(this);

    sceneProperty()
        .addListener(
            (obs, oldScene, newScene) -> {
              if (newScene != null) {
                Platform.runLater(() -> onVisualize(true));
              }
            });

    focusedProperty()
        .addListener(
            (obs, oldFocused, newFocused) -> {
              if (newFocused) {
                Platform.runLater(() -> onVisualize(true));
              }
            });

    clickTimeline.getKeyFrames().add(clickKeyFrame);
  }

  protected void configureDigitalTwinWidget(DigitalTwinControlPanel digitalTwinMinified) {}

  /**
   * Called when this editor page is shown on screen or gains focus (wit true value) or is closed
   * (with false value)
   */
  protected abstract void onVisualize(boolean visibleAfterCall);

  protected void showContent() {
    Platform.runLater(
        () -> {
          this.tree = createContentTree();
          TreeViewClickBehavior.disableBranchToggleOnDoubleClick(this.tree);
          this.tree.setOnMouseClicked(
              event -> {
                // painful
                TreeItem<?> item = tree.getSelectionModel().getSelectedItem();
                if (item == null) return;
                if (isClickTimelinePlaying) {
                  // when clicking the second time before the time line finishes
                  isClickTimelinePlaying = false;
                  onDoubleClickItemSelection((T) item.getValue());
                  clickTimeline.stop();
                  event.consume();
                } else {
                  // when clicking for the first time
                  isClickTimelinePlaying = true;
                  // start the timeline
                  // if timeline finises without receiving a second click, consider it a single
                  // click
                  clickTimeline.setOnFinished(
                      x -> {
                        if (item != null) {
                          onSingleClickItemSelection((T) item.getValue());
                        }
                        isClickTimelinePlaying = false;
                      });
                  clickTimeline.play();
                }
              });

          this.container = new VBox();
          Node browsingContent = createBrowsingContent(tree);
          VBox.setVgrow(browsingContent, Priority.ALWAYS);
          container.setMaxWidth(Double.MAX_VALUE);
          if (browsingContent instanceof Region region) {
            region.setMaxWidth(Double.MAX_VALUE);
          }
          digitalTwinControlPanel =
              new DigitalTwinControlPanel(browsingArea.widthProperty().intValue(), this);
          digitalTwinControlPanel.prefWidthProperty().bind(browsingArea.widthProperty());
          digitalTwinControlPanel
              .prefHeightProperty()
              .bind(digitalTwinControlPanel.widthProperty());
          configureDigitalTwinWidget(digitalTwinControlPanel);
          //          var dtContainer = new StackPane();
          // dtContainer.getChildren().addAll(digitalTwinControlPanel /*, toggleBar*/);

          container.getChildren().add(browsingContent);

          var topMenu = createTopMenu();
          if (topMenu != null) {
            browsingArea.setTop(topMenu);
          }

          browsingArea.setCenter(container);

          if (KlabIDEController.instance().getFocalScope() != null) {
            setDigitalTwin(KlabIDEController.instance().getFocalScope(), true);
          }
          KlabIDEController.instance().digitalTwinPanelStateChanged(this);
        });
  }

  /**
   * Build the right-hand browsing area. Editors that need more than a tree may override this and
   * compose the supplied tree with metadata, debugger, or other auxiliary panes.
   */
  protected Node createBrowsingContent(TreeView<T> tree) {
    return tree;
  }

  /**
   * Redefine to add a menu on top of the tree view. Default has no menu.
   *
   * @return
   */
  protected Node createTopMenu() {
    return null;
  }

  public void showDigitalTwinControlPanel() {
    //    if (!digitalTwinControlPanel.isVisible()) {
    Platform.runLater(
        () -> {
          if (!hasDigitalTwinControlPanel()) {
            KlabIDEController.instance().digitalTwinPanelStateChanged(this);
            return;
          }
          //            NodeUtils.toggleVisibility(digitalTwinControlPanel, true);
          //            NodeUtils.toggleVisibility(
          //                digitalTwinControlPanel.getParent().getChildrenUnmodifiable().get(0),
          // false);
          if (!container.getChildren().contains(digitalTwinControlPanel)) {
            this.container.getChildren().add(digitalTwinControlPanel);
          }
          KlabIDEController.instance().digitalTwinPanelShown(this, digitalTwinControlPanel);
        });
    //    }
  }

  public void hideDigitalTwinControlPanel() {
    //    if (digitalTwinControlPanel.isVisible()) {
    Platform.runLater(
        () -> {
          if (!hasDigitalTwinControlPanel()) {
            KlabIDEController.instance().digitalTwinPanelStateChanged(this);
            return;
          }
          //          digitalTwinControlPanel.setStatus(DigitalTwinControlPanel.Status.IDLE);
          //          NodeUtils.toggleVisibility(digitalTwinControlPanel, false);
          if (container.getChildren().contains(digitalTwinControlPanel)) {
            this.container.getChildren().remove(digitalTwinControlPanel);
          }
          KlabIDEController.instance().digitalTwinPanelHidden(this, digitalTwinControlPanel);
        });
    //    }
  }

  public void toggleDigitalTwinControlPanel() {
    if (!hasDigitalTwinControlPanel()) {
      return;
    }
    if (isDigitalTwinControlPanelShown()) {
      hideDigitalTwinControlPanel();
    } else {
      showDigitalTwinControlPanel();
    }
  }

  public boolean hasDigitalTwinControlPanel() {
    return container != null && digitalTwinControlPanel != null;
  }

  public boolean isDigitalTwinControlPanelShown() {
    return hasDigitalTwinControlPanel()
        && container.getChildren().contains(digitalTwinControlPanel);
  }

  public void edit(T asset) {
    if (!assetEditors.containsKey(asset)) {
      var editor = createEditor(asset);
      if (editor != null) {
        var tab = new Tab(Theme.getLabel(asset), editor);
        tab.setGraphic(Theme.getGraphics(asset));
        tab.setOnClosed(
            event -> {
              if (assetEditors.remove(asset, tab)) {
                disposeEditor(asset, editor);
              }
            });
        editorTabs.getTabs().add(tab);
        assetEditors.put(asset, tab);
        editorTabs.getSelectionModel().select(tab);
      }
    }
    if (assetEditors.containsKey(asset)) {
      editorTabs.getSelectionModel().select(assetEditors.get(asset));
    }
  }

  /** Return the editor node for an asset, or {@code null} when the asset is not open. */
  protected Node getEditor(T asset) {
    var tab = assetEditors.get(asset);
    return tab == null ? null : tab.getContent();
  }

  /** Return true when the asset is open in the current foreground editor tab. */
  protected boolean isEditorSelected(T asset) {
    var tab = assetEditors.get(asset);
    return tab != null && editorTabs.getSelectionModel().getSelectedItem() == tab;
  }

  /** Replace the graphic of the editor tab associated with an asset. */
  protected void setEditorGraphic(T asset, Node graphic) {
    var tab = assetEditors.get(asset);
    if (tab != null) {
      tab.setGraphic(graphic);
    }
  }

  /**
   * Show an editor tab that is not associated with an asset in the page tree. If a tab with the
   * same key is already open, its content is replaced instead of adding another tab.
   */
  protected Tab showAuxiliaryEditor(String key, String title, Node editor) {
    var tab = auxiliaryEditors.get(key);
    if (tab == null) {
      tab = new Tab(title, editor);
      var newTab = tab;
      tab.setOnClosed(event -> auxiliaryEditors.remove(key, newTab));
      auxiliaryEditors.put(key, tab);
      editorTabs.getTabs().add(tab);
      editorTabs.getSelectionModel().select(tab);
    } else {
      tab.setText(title);
      if (tab.getContent() != editor) {
        tab.setContent(editor);
      }
    }
    return tab;
  }

  /** Select an open auxiliary editor tab without creating or changing it. */
  protected void selectAuxiliaryEditor(String key) {
    var tab = auxiliaryEditors.get(key);
    if (tab != null) {
      editorTabs.getSelectionModel().select(tab);
    }
  }

  /** Close an auxiliary editor tab if it is currently open. */
  protected void closeAuxiliaryEditor(String key) {
    var tab = auxiliaryEditors.remove(key);
    if (tab != null) {
      editorTabs.getTabs().remove(tab);
    }
  }

  /**
   * Return the asset being edited
   *
   * @return
   */
  public A getEditedAsset() {
    return currentAsset;
  }

  /** Return a stable snapshot of the assets with an open editor tab. */
  protected Set<T> getOpenEditorAssets() {
    return Set.copyOf(assetEditors.keySet());
  }

  /**
   * Replace an open asset and its editor in place, retaining the tab position and selection.
   *
   * @return true when the old asset had an open editor and the replacement was installed
   */
  protected boolean refreshEditor(T oldAsset, T refreshedAsset) {
    var tab = assetEditors.get(oldAsset);
    if (tab == null) {
      return false;
    }
    var refreshedEditor = createEditor(refreshedAsset);
    if (refreshedEditor == null) {
      return false;
    }
    var previousEditor = tab.getContent();
    if (previousEditor != null) {
      disposeEditor(oldAsset, previousEditor);
    }
    assetEditors.remove(oldAsset);
    assetEditors.put(refreshedAsset, tab);
    tab.setText(Theme.getLabel(refreshedAsset));
    tab.setGraphic(Theme.getGraphics(refreshedAsset));
    tab.setContent(refreshedEditor);
    tab.setOnClosed(
        event -> {
          if (assetEditors.remove(refreshedAsset, tab)) {
            disposeEditor(refreshedAsset, refreshedEditor);
          }
        });
    return true;
  }

  /** Update the logical asset after an editor reparses or otherwise replaces it in place. */
  protected void setEditedAsset(A asset) {
    this.currentAsset = asset;
  }

  protected abstract Node createEditor(T asset);

  /**
   * Release resources owned by an individual editor. This is called only when its tab is actually
   * closed, not when JavaFX temporarily detaches the tab content from a scene.
   */
  protected void disposeEditor(T asset, Node editor) {}

  /**
   * Handle a single click in the browse tree. Note: runs inside the platform UI thread
   *
   * @param value
   */
  protected abstract void onSingleClickItemSelection(T value);

  @Override
  public void setDigitalTwin(IDEContextScope contextScope, boolean focus) {
    // TODO should have a switcher if not dedicated.
    if (hasDigitalTwinControlPanel() && (focus || contextScope == null)) {
      digitalTwinControlPanel.setDigitalTwin(contextScope, focus);
      //      digitalTwinLabel.setText(contextScope == null ? "" : contextScope.getName());
    }
  }

  @Override
  public void unsetDigitalTwin(IDEContextScope focalScope) {
    setDigitalTwin(null, true);
    Platform.runLater(
        () -> {
          // Keep the panel visible while switching to another twin, but do not leave an
          // orphaned panel on screen when the closed twin was the last available one.
          if (KlabIDEController.instance().getFocalScope() == null) {
            hideDigitalTwinControlPanel();
          }
        });
  }

  /**
   * Handle a double click in the browse tree. Note: runs inside the platform UI thread
   *
   * @param value
   */
  protected abstract void onDoubleClickItemSelection(T value);

  protected abstract TreeView<T> createContentTree();

  public void deleteScope(IDEContextScope scope) {

    var alert = new Alert(Alert.AlertType.WARNING);
    alert.setTitle("Remove digital twin");
    alert.setHeaderText("You are about to remove the digital twin. Please confirm");
    alert.setContentText(
        "Removing this digital twin will also remove all assets, storage and schedule. "
            + "All data will be deleted permanently. " /*
                                                       + "There are currently 0 users connected to this besides yourself."*/);

    ButtonType yesBtn = new ButtonType("Confirm", ButtonBar.ButtonData.YES);
    ButtonType cancelBtn = new ButtonType("Cancel", ButtonBar.ButtonData.CANCEL_CLOSE);

    alert.getButtonTypes().setAll(yesBtn, cancelBtn);
    alert.initOwner(getScene().getWindow());
    var result = alert.showAndWait();
    if (!result.isEmpty() && result.get().getButtonData() == ButtonBar.ButtonData.YES) {
      scope.close();
    }
  }

  @Override
  public void close() {
    for (var entry : Map.copyOf(assetEditors).entrySet()) {
      var editor = entry.getValue().getContent();
      if (editor != null) {
        disposeEditor(entry.getKey(), editor);
      }
    }
    assetEditors.clear();
    auxiliaryEditors.clear();
    if (digitalTwinControlPanel != null) {
      digitalTwinControlPanel.close();
    }
    KlabIDEController.instance().unregisterDigitalTwinReactor(this);
  }
}
