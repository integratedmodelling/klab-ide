package org.integratedmodelling.klab.ide.pages;

import atlantafx.base.theme.Styles;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.geometry.Orientation;
import javafx.geometry.Side;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.geometry.Pos;
import javafx.scene.layout.*;
import org.integratedmodelling.klab.ide.KlabIDEController;
import org.integratedmodelling.klab.ide.api.DigitalTwinReactor;
import org.integratedmodelling.klab.ide.IDEContextScope;
import org.kordamp.ikonli.javafx.FontIcon;
import org.kordamp.ikonli.material2.Material2AL;
import javafx.util.Duration;
import org.integratedmodelling.klab.ide.Theme;
import org.integratedmodelling.klab.ide.components.DigitalTwinControlPanel;
import org.integratedmodelling.klab.ide.utils.NodeUtils;

import java.util.HashMap;
import java.util.Map;

/**
 * Editor for a first-class container - resource, digital twin or workspace.
 *
 * @param <A> the overall asset being edited
 * @param <T> the individual assets we create editors for
 */
public abstract class EditorPage<A, T> extends BorderPane implements DigitalTwinReactor {

  private final BorderPane browsingArea;
  private final TabPane editorTabs;
  //  private final Node menuArea;
  final Timeline clickTimeline = new Timeline();
  Duration clickDuration = Duration.millis(350);
  KeyFrame clickKeyFrame = new KeyFrame(clickDuration);
  boolean isClickTimelinePlaying = false;
  private Map<T, Tab> assetEditors = new HashMap<>();
  protected DigitalTwinControlPanel digitalTwinControlPanel;
  private TreeView<T> tree;
  //  private HBox toggleBar;
  private A currentAsset;

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

          var container = new VBox();
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
          KlabIDEController.instance().digitalTwinPanelHidden(this, digitalTwinControlPanel);

          //          this.toggleBar = new HBox();
          //          toggleBar.setStyle("-fx-background-color: -color-neutral-subtle; -fx-padding:
          // 4;");
          //          toggleBar.setAlignment(Pos.CENTER_LEFT);
          //          toggleBar.setPrefHeight(44);
          //
          //          var arrowIcon = new Button();
          //          arrowIcon.setGraphic(new FontIcon(Material2AL.ARROW_UPWARD));
          //          arrowIcon
          //              .onActionProperty()
          //              .set(
          //                  e -> {
          //                    showDigitalTwinControlPanel();
          //                    NodeUtils.toggleVisibility(toggleBar, false);
          //                  });
          //          arrowIcon.getStyleClass().addAll(Styles.BUTTON_CIRCLE, Styles.FLAT);
          //          this.digitalTwinLabel = new Label("Digital Twin Control");
          //          digitalTwinLabel.setAlignment(Pos.CENTER_LEFT);
          //          digitalTwinLabel.setStyle("-fx-font-size: 14;");
          //          digitalTwinLabel.setMaxWidth(Double.MAX_VALUE);
          //          HBox.setHgrow(digitalTwinLabel, Priority.ALWAYS);
          //          toggleBar.getChildren().addAll(digitalTwinLabel, arrowIcon);
          //          toggleBar.setMaxWidth(Double.MAX_VALUE);
          //          toggleBar.setOnMouseClicked(
          //              e -> {
          //                showDigitalTwinControlPanel();
          //                NodeUtils.toggleVisibility(toggleBar, false);
          //              });
          //          HBox.setHgrow(toggleBar, Priority.ALWAYS);

          showDigitalTwinControlPanel();
          NodeUtils.toggleVisibility(digitalTwinControlPanel, false);

          //          NodeUtils.toggleVisibility(toggleBar, true);

          var dtContainer = new StackPane();
          dtContainer.getChildren().addAll(digitalTwinControlPanel /*, toggleBar*/);

          container.getChildren().addAll(tree, dtContainer);

          browsingArea.setCenter(container);

          if (KlabIDEController.instance().getFocalScope() != null) {
            setDigitalTwin(KlabIDEController.instance().getFocalScope(), true);
          }
        });
  }

  public void showDigitalTwinControlPanel() {
    if (!digitalTwinControlPanel.isVisible()) {
      Platform.runLater(
          () -> {
            NodeUtils.toggleVisibility(digitalTwinControlPanel, true);
//            NodeUtils.toggleVisibility(
//                digitalTwinControlPanel.getParent().getChildrenUnmodifiable().get(1), false);
            KlabIDEController.instance().digitalTwinPanelShown(this, digitalTwinControlPanel);
          });
    }
  }

  public void hideDigitalTwinControlPanel() {
    if (digitalTwinControlPanel.isVisible()) {
      Platform.runLater(
          () -> {
            NodeUtils.toggleVisibility(digitalTwinControlPanel, false);
            KlabIDEController.instance().digitalTwinPanelHidden(this, digitalTwinControlPanel);
          });
    }
  }

  public void toggleDigitalTwinControlPanel() {
    if (digitalTwinControlPanel.isVisible()) {
      hideDigitalTwinControlPanel();
    } else {
      showDigitalTwinControlPanel();
    }
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
    if (focus) {
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
