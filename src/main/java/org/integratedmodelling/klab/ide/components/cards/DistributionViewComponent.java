package org.integratedmodelling.klab.ide.components.cards;

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
import org.integratedmodelling.klab.ide.components.generic.IconLabel;
import org.integratedmodelling.klab.ide.components.generic.WaitButton;
import org.kordamp.ikonli.Ikon;
import org.kordamp.ikonli.materialdesign.MaterialDesign;

public class DistributionViewComponent extends BaseAssetViewComponent {

  private ComboBox<TagInfo> chooseTag;
  private HBox productList;
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

    this.chooseTag = new ComboBox<TagInfo>();

    // Set custom cell factory to display icon and text
    this.chooseTag.setCellFactory(
        param ->
            new ListCell<TagInfo>() {
              @Override
              protected void updateItem(TagInfo item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                  setGraphic(null);
                  setText(null);
                } else {
                  HBox box = new HBox(5);
                  box.setAlignment(Pos.CENTER_LEFT);
                  box.getChildren().addAll(item.icon, new Label(item.toString()));
                  setGraphic(box);
                  setText(null);
                }
              }
            });

    // Set custom button cell to display icon and text when selected
    this.chooseTag.setButtonCell(
        new ListCell<TagInfo>() {
          @Override
          protected void updateItem(TagInfo item, boolean empty) {
            super.updateItem(item, empty);
            if (empty || item == null) {
              setGraphic(null);
              setText(null);
            } else {
              HBox box = new HBox(5);
              box.setAlignment(Pos.CENTER_LEFT);
              box.getChildren().addAll(item.icon, new Label(item.toString()));
              setGraphic(box);
              setText(null);
            }
          }
        });

    this.productList = new HBox(10);
    productList.setAlignment(Pos.CENTER);
    productList.setStyle(
        "-fx-border-color: -color-border-default; -fx-border-radius: 6; -fx-border-width: 1;"
            + " -fx-background-radius: 4; -fx-background-color: -color-bg-subtle;");
    HBox.setHgrow(productList, Priority.ALWAYS);
    HBox.setHgrow(right, Priority.ALWAYS);
    var downloadMonitor = new HBox();
    HBox.setHgrow(downloadMonitor, Priority.ALWAYS);

    var label = new Label("Placeholder for download progress");
    label.setStyle(
        "-fx-font-size: 10px; -fx-text-alignment: left; -fx-text-fill: -color-fg-muted;");

    downloadMonitor.getChildren().add(label);

    downloadMonitor.setAlignment(Pos.CENTER);
    this.downloadButton = new WaitButton("Download");
    downloadButton.setPrefSize(120, 60);
    //      downloadMonitor.setStyle(
    //          "-fx-border-color: -color-border-default; -fx-border-radius: 6; -fx-border-width:
    // 1;" + " -fx-background-radius: 4; -fx-background-color: -color-bg-subtle;");
    int n = 0;
    for (Stack.Tag tag : KlabIDEController.instance().engine().getSoftwareStack().tags()) {
      chooseTag.getItems().add(new TagInfo(tag));
      if (KlabIDEController.instance().engine().getDistributionTag() == tag) {
        chooseTag.getSelectionModel().select(n);
        selectTag(tag);
      }
      n++;
    }

    chooseTag.setOnAction(
        e -> {
          selectTag(chooseTag.getValue().tag);
        });

    this.tagLabel = new Label("Choose a distribution to use, install or update");
    left.getChildren().addAll(tagLabel, chooseTag);
    right.getChildren().addAll(downloadMonitor, productList);
    tagLabel.setStyle(
        "-fx-font-size: 10px; -fx-text-alignment: left; -fx-text-fill: -color-fg-muted;");

    main.getChildren().addAll(left, downloadButton, right);

    this.getChildren().add(main);

    return main;
  }

  private void selectTag(Stack.Tag tag) {

    // TODO arm buttons and descriptions

    productList.getChildren().clear();
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
      productIcon.setTooltip(tooltip);
      productList.getChildren().add(productIcon);
    }
  }
}
