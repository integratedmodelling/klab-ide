package org.integratedmodelling.klab.ide.components.cards;

import atlantafx.base.controls.Card;
import atlantafx.base.theme.Styles;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import org.integratedmodelling.common.utils.Utils;
import org.integratedmodelling.klab.api.knowledge.observation.scale.time.TimeInstant;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.services.runtime.objects.ContextInfo;
import org.integratedmodelling.klab.ide.KlabIDEApplication;
import org.integratedmodelling.klab.ide.KlabIDEController;
import org.kordamp.ikonli.javafx.FontIcon;
import org.kordamp.ikonli.material2.Material2AL;
import org.kordamp.ikonli.material2.Material2MZ;

import java.time.Duration;
import java.util.function.Consumer;

public class DigitalTwinSmallViewComponent extends BaseAssetViewComponent {

  private final Consumer<ContextScope> deleteAction;
  ContextInfo digitalTwin;
  Consumer<ContextScope> selectAction;
  boolean local;

  public DigitalTwinSmallViewComponent(
      ContextInfo digitalTwin,
      Consumer<ContextScope> selectAction,
      Consumer<ContextScope> deleteAction,
      boolean local) {
    super(AssetViewComponent.Type.Object, digitalTwin.getConfiguration().getName(), false);
    this.digitalTwin = digitalTwin;
    this.selectAction = selectAction;
    this.deleteAction = deleteAction;
    this.local = local;
  }

  @Override
  public Node createContent() {
    var card = new Card();
    VBox content = new VBox(10);
    content.setPadding(new Insets(10));
    VBox.setVgrow(content, Priority.ALWAYS);
    content.setPrefWidth(280);

    Label title = new Label(digitalTwin.getConfiguration().getName());
    title.setStyle(
        "-fx-font-weight: bold; -fx-font-size: 14px;"
            + (local ? " -fx-text-fill:-color-success-emphasis;" : ""));
    title.setMaxWidth(Double.MAX_VALUE);

    Button openButton = new Button();
    openButton.getStyleClass().addAll(Styles.BUTTON_CIRCLE, Styles.FLAT);
    openButton.setGraphic(new FontIcon(Material2MZ.OPEN_IN_NEW));
    openButton.setOnAction(
        e -> {
          if (selectAction != null) {
            selectAction.accept(
                KlabIDEController.instance().user().connect(digitalTwin.getConfiguration()));
          }
        });

    Button linkButton = new Button();
    linkButton.getStyleClass().addAll(Styles.BUTTON_CIRCLE, Styles.FLAT);
    linkButton.setGraphic(new FontIcon(Material2AL.CONTENT_COPY));
    linkButton.setOnAction(
        e -> {
          if (selectAction != null) {
            final var clipboard = Clipboard.getSystemClipboard();
            final var ct = new ClipboardContent();
            ct.putString(digitalTwin.getConfiguration().getUrl().toString());
            clipboard.setContent(ct);
          }
        });

    Button deleteButton = new Button();
    deleteButton.getStyleClass().addAll(Styles.BUTTON_CIRCLE, Styles.FLAT);
    deleteButton.setGraphic(new FontIcon(Material2AL.DELETE_FOREVER));
    deleteButton.setOnAction(
        e -> {
          var peer =
              KlabIDEController.instance()
                  .getDigitalTwinPeer(digitalTwin.getConfiguration().getId());
          if (peer != null) {
            peer.close();
            deleteAction.accept(peer);
          } else {
            var scope = KlabIDEController.instance().user().connect(digitalTwin.getConfiguration());
            if (scope != null) {
              scope.close();
            }
            if (deleteAction != null) {
              deleteAction.accept(scope);
            }
          }
        });

    HBox buttonContainer = new HBox();
    buttonContainer.setSpacing(0);
    buttonContainer.setAlignment(Pos.CENTER_LEFT);
    buttonContainer.getChildren().addAll(openButton, linkButton, deleteButton);

    HBox titleBox = new HBox(5);
    titleBox.setAlignment(Pos.CENTER_LEFT);
    HBox.setHgrow(title, Priority.ALWAYS);
    titleBox.getChildren().addAll(title, buttonContainer);

    HBox.setHgrow(titleBox, Priority.ALWAYS);

    if (digitalTwin.getConfiguration().getOwner() != null
        && digitalTwin.getConfiguration().getOwner().contains("@")) {
      FontIcon federatedIcon = new FontIcon(Material2AL.CLOUD);
      federatedIcon.setStyle("-fx-font-size: 14px;");
      Tooltip.install(
          federatedIcon,
          new Tooltip("Federated user: " + digitalTwin.getConfiguration().getOwner()));
      titleBox.getChildren().add(federatedIcon);
    }

    Hyperlink url = new Hyperlink(digitalTwin.getConfiguration().getUrl().toString());
    url.setStyle("-fx-font-size: 10px;");
    url.setOnAction(
        e ->
            KlabIDEApplication.instance()
                .getHostServices()
                .showDocument(digitalTwin.getConfiguration().getUrl().toString()));

    Label size =
        new Label(
            String.format(
                "Created: %s;\nIdle: %s",
                TimeInstant.create(digitalTwin.getCreationTime()),
                Utils.Time.formatDuration(Duration.ofMillis(digitalTwin.getIdleTimeMs()))));
    size.setStyle("-fx-font-size: 12px;");

    TextArea description = new TextArea(digitalTwin.getConfiguration().getDescription());
    description.setWrapText(true);
    description.setEditable(false);
    description.setPrefRowCount(3);

    Label persistence =
        new Label("Persistence: " + digitalTwin.getConfiguration().getPersistence().description);
    persistence.setStyle("-fx-font-size: 12px;");

    content.getChildren().addAll(titleBox, url, size, description, persistence);

    card.setBody(content);
    VBox.setVgrow(card, Priority.ALWAYS);
    this.getChildren().add(card);
    return card;
  }
}
