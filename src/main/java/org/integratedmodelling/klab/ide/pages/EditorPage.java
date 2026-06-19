package org.integratedmodelling.klab.ide.pages;

import atlantafx.base.theme.Styles;
import java.util.HashMap;
import java.util.Map;
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
          VBox.setVgrow(tree, Priority.ALWAYS);
          container.setMaxWidth(Double.MAX_VALUE);
          tree.setMaxWidth(Double.MAX_VALUE);
          digitalTwinControlPanel =
              new DigitalTwinControlPanel(tree.widthProperty().intValue(), this);
          digitalTwinControlPanel.prefWidthProperty().bind(tree.widthProperty());
          digitalTwinControlPanel
              .prefHeightProperty()
              .bind(digitalTwinControlPanel.widthProperty());
          configureDigitalTwinWidget(digitalTwinControlPanel);
          //          var dtContainer = new StackPane();
          // dtContainer.getChildren().addAll(digitalTwinControlPanel /*, toggleBar*/);

          container.getChildren().addAll(tree);

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
        editorTabs.getTabs().add(tab);
        assetEditors.put(asset, tab);
        editorTabs.getSelectionModel().select(tab);
      }
    }
    if (assetEditors.containsKey(asset)) {
      editorTabs.getSelectionModel().select(assetEditors.get(asset));
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

  protected abstract Node createEditor(T asset);

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
    digitalTwinControlPanel.close();
    KlabIDEController.instance().unregisterDigitalTwinReactor(this);
  }
}
