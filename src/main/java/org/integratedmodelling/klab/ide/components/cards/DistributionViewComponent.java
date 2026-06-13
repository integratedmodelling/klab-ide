package org.integratedmodelling.klab.ide.components.cards;

import atlantafx.base.controls.Card;
import atlantafx.base.controls.ToggleSwitch;
import atlantafx.base.theme.Styles;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import org.integratedmodelling.klab.api.data.Version;
import org.integratedmodelling.klab.api.engine.distribution.Distribution;
import org.integratedmodelling.klab.api.engine.distribution.Stack;
import org.integratedmodelling.klab.ide.KlabIDEController;
import org.integratedmodelling.klab.ide.Theme;
import org.integratedmodelling.klab.ide.components.generic.CarouselBox;
import org.integratedmodelling.klab.ide.components.generic.IconButton;
import org.integratedmodelling.klab.ide.components.generic.IconLabel;
import org.integratedmodelling.klab.ide.components.generic.WaitButton;
import org.kordamp.ikonli.Ikon;
import org.kordamp.ikonli.evaicons.Evaicons;
import org.kordamp.ikonli.javafx.FontIcon;
import org.kordamp.ikonli.material2.Material2AL;
import org.kordamp.ikonli.materialdesign.MaterialDesign;

public class DistributionViewComponent extends BaseAssetViewComponent {

  //  private ComboBox<TagInfo> chooseTag;
  private CarouselBox productList;
  private Label tagLabel;
  private WaitButton downloadButton;

  private static class TagInfo {
    private final Stack.Tag tag;
    private final String description;
    private final IconLabel icon;

    public TagInfo(Stack.Tag tag) {
      this.tag = tag;
      var isDevelop = tag.version() == Version.HEAD;
      var isAvailable = tag.availableLocally();
      var date = parseDate(tag.build());
      this.description =
          (isDevelop ? "Source code " : tag.version().toString())
              + tag.release()
              + " distribution (built "
              + date
              + (tag.orphan() ? ", orphaned)" : ")");
      this.icon =
          new IconLabel(
              Theme.DEFINITION_ICON, 16, tag.availableLocally() ? Color.GREEN : Color.GREY);
    }

    private String parseDate(String buildId) {
      var year = buildId.substring(0, 4);
      var month = buildId.substring(4, 6);
      var day = buildId.substring(6, 8);
      var hour = buildId.substring(8, 10);
      var minute = buildId.substring(10, 12);
      return day + "/" + month + "/" + year + " " + hour + ":" + minute;
    }

    @Override
    public String toString() {
      return description;
    }
  }

  public DistributionViewComponent() {
    super(AssetViewComponent.Type.Distribution, "Software stack", true);
  }

  @Override
  public String getDescription() {
    return "Install, update and select software stack distributions";
  }

  @Override
  public Ikon getIcon() {
    return MaterialDesign.MDI_PACKAGE_VARIANT;
  }

  protected Node createContent() {
    //      var card = new Card();

    var main = new HBox(20);
    var left = new VBox(10);
    var right = new VBox(10);

    //    this.chooseTag = new ComboBox<TagInfo>();
    //
    //    // Set custom cell factory to display icon and text
    //    this.chooseTag.setCellFactory(
    //        param ->
    //            new ListCell<TagInfo>() {
    //              @Override
    //              protected void updateItem(TagInfo item, boolean empty) {
    //                super.updateItem(item, empty);
    //                if (empty || item == null) {
    //                  setGraphic(null);
    //                  setText(null);
    //                } else {
    //                  HBox box = new HBox(5);
    //                  box.setAlignment(Pos.CENTER_LEFT);
    //                  box.getChildren().addAll(item.icon, new Label(item.toString()));
    //                  setGraphic(box);
    //                  setText(null);
    //                }
    //              }
    //            });
    //
    //    // Set custom button cell to display icon and text when selected
    //    this.chooseTag.setButtonCell(
    //        new ListCell<TagInfo>() {
    //          @Override
    //          protected void updateItem(TagInfo item, boolean empty) {
    //            super.updateItem(item, empty);
    //            if (empty || item == null) {
    //              setGraphic(null);
    //              setText(null);
    //            } else {
    //              HBox box = new HBox(5);
    //              box.setAlignment(Pos.CENTER_LEFT);
    //              box.getChildren().addAll(item.icon, new Label(item.toString()));
    //              setGraphic(box);
    //              setText(null);
    //            }
    //          }
    //        });

    this.productList = new CarouselBox(Orientation.HORIZONTAL);
    //    productList.
    //    productList.setStyle(
    //        "-fx-border-color: -color-border-default; -fx-border-radius: 6; -fx-border-width: 1;"
    //            + " -fx-background-radius: 4; -fx-background-color: -color-bg-subtle;");
    HBox.setHgrow(productList, Priority.ALWAYS);
    HBox.setHgrow(right, Priority.ALWAYS);
    //    var downloadMonitor = new HBox();
    //    HBox.setHgrow(downloadMonitor, Priority.ALWAYS);

    var label = new Label("Placeholder for download progress");
    label.setStyle(
        "-fx-font-size: 10px; -fx-text-alignment: left; -fx-text-fill: -color-fg-muted;");

    //    downloadMonitor.getChildren().add(label);

    //    downloadMonitor.setAlignment(Pos.CENTER);
    //    this.downloadButton = new WaitButton("Download");
    //    downloadButton.setPrefSize(120, 60);
    //      downloadMonitor.setStyle(
    //          "-fx-border-color: -color-border-default; -fx-border-radius: 6; -fx-border-width:
    // 1;" + " -fx-background-radius: 4; -fx-background-color: -color-bg-subtle;");
    int n = 0;
    for (Stack.Tag tag : KlabIDEController.instance().engine().getSoftwareStack().tags()) {
      productList.addItem(makeHorizontalCard(tag));
      //      chooseTag.getItems().add(new TagInfo(tag));
      //      if (KlabIDEController.instance().engine().getDistributionTag() == tag) {
      //        chooseTag.getSelectionModel().select(n);
      //        selectTag(tag);
      //      }
      //      n++;
    }

    //    chooseTag.setOnAction(
    //        e -> {
    //          selectTag(chooseTag.getValue().tag);
    //        });

    //    this.tagLabel = new Label("Choose a distribution to use, install or update");
    //    left.getChildren().addAll(tagLabel, chooseTag);
    //    right.getChildren().addAll(downloadMonitor, productList);
    //    tagLabel.setStyle(
    //        "-fx-font-size: 10px; -fx-text-alignment: left; -fx-text-fill: -color-fg-muted;");
    //
    main.getChildren().addAll(productList, right /*left, downloadButton, right*/);

    this.getChildren().add(main);

    return main;
  }

  private void selectTag(Stack.Tag tag) {

    // TODO arm buttons and descriptions

    productList.clear();
    var build = KlabIDEController.instance().engine().getSoftwareStack().build(tag);
    for (var product : build.getProducts()) {
      if (product.getType() == Distribution.Product.Type.CLI) {
        continue;
      }
      var productIcon =
          switch (product.getType()) {
            case RESOURCES_SERVICE ->
                new IconLabel(
                    Theme.RESOURCES_ICON, 24, tag.availableLocally() ? Color.GREEN : Color.GREY);
            case REASONER_SERVICE ->
                new IconLabel(
                    Theme.WORLDVIEW_ICON, 24, tag.availableLocally() ? Color.GREEN : Color.GREY);
            case RESOLVER_SERVICE ->
                new IconLabel(
                    Theme.KNOWLEDGE_GRAPH_ICON,
                    24,
                    tag.availableLocally() ? Color.GREEN : Color.GREY);
            case RUNTIME_SERVICE ->
                new IconLabel(
                    Theme.DIGITAL_TWINS_ICON,
                    24,
                    tag.availableLocally() ? Color.GREEN : Color.GREY);
            case LANGUAGE_SERVER ->
                new IconLabel(
                    Theme.LANGUAGE_SERVER_ICON,
                    24,
                    tag.availableLocally() ? Color.GREEN : Color.GREY);
            case DATABASE_SERVER ->
                new IconLabel(
                    Theme.DATABASE_ICON, 24, tag.availableLocally() ? Color.GREEN : Color.GREY);
            case AMQP_BROKER ->
                new IconLabel(
                    Theme.MESSAGING_ICON, 24, tag.availableLocally() ? Color.GREEN : Color.GREY);
            default -> null;
          };
      var tooltip = new Tooltip(product.getType().getName());
      tooltip.setShowDelay(javafx.util.Duration.millis(250));
      //      productIcon.setTooltip(tooltip);
      //      productList.getChildren().add(productIcon);
    }
  }

  /**
   * Builds a vertically-compact card sized for the horizontal carousel (fixed 180 px width, height
   * determined by the carousel container).
   */
  private Card makeHorizontalCard(Stack.Tag tag) {

    String title = tag.release() + (tag.orphan() ? " (orphaned)" : "");
    String color =
        tag.orphan()
            ? colorToHex(Color.RED)
            : (tag.availableLocally() ? colorToHex(Color.GREEN) : colorToHex(Color.GREY));
    String desc = tag.release();
    var isDevelop = tag.version() == Version.HEAD;
    var isAvailable = tag.availableLocally();
    var date = parseDate(tag.build());
    var description = (isDevelop ? "Source code" : tag.version().toString()) + " :: built " + date;
    var icon =
        new IconLabel(Theme.DEFINITION_ICON, 12, tag.availableLocally() ? Color.GREEN : Color.GREY);

    // Colored dot accent
    Label dot = new Label();
    dot.setMinSize(16, 16);
    dot.setMaxSize(16, 16);
    dot.setStyle("-fx-background-color: " + color + "; " + "-fx-background-radius: 9px;");

    Label titleLabel = new Label(title);
    titleLabel.getStyleClass().addAll(Styles.TEXT_BOLD);
    titleLabel.setWrapText(false);
    //    HBox.setHgrow(titleLabel, Priority.ALWAYS);
    var currentSwitch = new ToggleSwitch("");
    currentSwitch.pseudoClassStateChanged(Styles.STATE_SUCCESS, true);
    // TODO set the switch and give it an action - must be in the card
    HBox header = new HBox(6, currentSwitch, titleLabel);
    header.setAlignment(Pos.CENTER_LEFT);
    //    HBox.setHgrow(header, Priority.ALWAYS);
    header.setPadding(new Insets(6, 8, 2, 8));

    if (KlabIDEController.instance().engine().getDistributionTag() == tag) {
      currentSwitch.setSelected(true);
      var tooltip = new Tooltip("This is the current distribution");
      tooltip.setShowDelay(javafx.util.Duration.millis(150));
      currentSwitch.setTooltip(tooltip);
    } else if (tag.availableLocally()) {
      currentSwitch.setSelected(false);
      var tooltip = new Tooltip("Select to make this the current distribution");
      tooltip.setShowDelay(javafx.util.Duration.millis(150));
      currentSwitch.setTooltip(tooltip);
    } else {
      currentSwitch.setDisable(true);
      var tooltip = new Tooltip("Synchronize this distribution to make it available");
      tooltip.setShowDelay(javafx.util.Duration.millis(150));
      titleLabel.setTooltip(tooltip);
    }

    Label descLabel = new Label("v." + tag.version());
    descLabel.setWrapText(true);
    descLabel.getStyleClass().addAll(Styles.TEXT_BOLDER, Styles.TITLE_3);
    descLabel.setMaxWidth(220);

    var buttons = new HBox(4);
    buttons
        .getChildren()
        .add(
            new IconButton(Evaicons.DOWNLOAD, 24, Color.DARKGOLDENROD, Color.DARKGREEN, false) {
              @Override
              protected void action() {
                System.out.println("Daje");
              }
            }.enabled(!tag.availableLocally()).styleClass(Styles.ROUNDED).tooltip("Synchronize"));
    buttons
        .getChildren()
        .add(
            new IconButton(Evaicons.FLAG, 24, Color.DARKGREEN, Color.DARKGOLDENROD, false) {
              @Override
              protected void action() {
                System.out.println("Daje");
              }
            }.enabled(tag.availableLocally())
                .styleClass(Styles.ROUNDED)
                .tooltip("Verify integrity"));
    buttons
        .getChildren()
        .add(
            new IconButton(Evaicons.TRASH, 24, Color.DARKGOLDENROD, Color.DARKRED, false) {
              @Override
              protected void action() {
                System.out.println("Daje");
              }
            }.enabled(tag.availableLocally())
                .styleClass(Styles.ROUNDED)
                .tooltip("Delete from disk"));

    VBox cardBody = new VBox(10, descLabel, buttons);
    cardBody.setPadding(new Insets(0, 8, 4, 8));

    //    FontIcon footerIcon = new FontIcon(Material2AL.LABEL_IMPORTANT);
    //    footerIcon.setIconSize(12);
    //    footerIcon.setStyle("-fx-icon-color: -color-fg-muted;");

    Label tagLabel = new Label(description);
    tagLabel.getStyleClass().addAll(Styles.TEXT_SMALL, Styles.TEXT_MUTED);

    HBox footer = new HBox(4, icon, tagLabel);
    footer.setAlignment(Pos.CENTER_LEFT);
    footer.setPadding(new Insets(2, 8, 6, 8));

    Card card = new Card();
    card.setHeader(header);
    card.setBody(cardBody);
    card.setFooter(footer);
    card.setPrefWidth(300);
    card.setPrefHeight(170);
    card.setUserData(title);

    return card;
  }

  private String parseDate(String buildId) {
    var year = buildId.substring(0, 4);
    var month = buildId.substring(4, 6);
    var day = buildId.substring(6, 8);
    var hour = buildId.substring(8, 10);
    var minute = buildId.substring(10, 12);
    return day + "/" + month + "/" + year + " " + hour + ":" + minute;
  }

  public String colorToHex(Color color) {
    return String.format(
        "#%02X%02X%02X",
        (int) (color.getRed() * 255),
        (int) (color.getGreen() * 255),
        (int) (color.getBlue() * 255));
  }
}
