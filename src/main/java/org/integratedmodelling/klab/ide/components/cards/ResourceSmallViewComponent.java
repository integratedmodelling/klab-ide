package org.integratedmodelling.klab.ide.components.cards;

import atlantafx.base.controls.Card;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.Tooltip;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import org.integratedmodelling.klab.api.data.Metadata;
import org.integratedmodelling.klab.api.services.ResourcesService;
import org.integratedmodelling.klab.api.services.resources.ResourceInfo;
import org.integratedmodelling.klab.ide.KlabIDEController;
import org.integratedmodelling.klab.ide.Theme;
import org.integratedmodelling.klab.ide.components.generic.IconLabel;
import org.kordamp.ikonli.javafx.FontIcon;
import org.kordamp.ikonli.material2.Material2AL;
import org.kordamp.ikonli.material2.Material2MZ;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class ResourceSmallViewComponent extends BaseAssetViewComponent {

  private final Consumer<ResourceInfo> selectHandler;
  private final Consumer<ResourceInfo> deleteHandler;
  private ResourceInfo descriptor;

  public ResourceSmallViewComponent(
      ResourceInfo descriptor,
      Consumer<ResourceInfo> selectHandler,
      Consumer<ResourceInfo> deleteHandler) {
    super(AssetViewComponent.Type.Object, descriptor.getUrn(), false);
    this.descriptor = descriptor;
    this.selectHandler = selectHandler;
    this.deleteHandler = deleteHandler;
    createContent();
  }

  protected Node createContent() {
    var card = new Card();
    VBox body = new VBox(10);
    var icon = new FontIcon(Theme.getIcon(descriptor.getKnowledgeClass()));

    // add @ serviceName to titl
    var service =
        KlabIDEController.instance()
            .user()
            .findService(
                ResourcesService.class, s -> s.serviceId().equals(descriptor.getServiceId()))
            .orElse(null); // TODO  handle

    var label = displayName(this.descriptor);
    if (service != null) {
      label += " @ " + service.serviceName();
    }

    var comment = description(this.descriptor);

    var tooltip = label + "\n" + this.descriptor.getUrn();

    Label title = new Label(label);
    title.setStyle(
        "-fx-font-weight: bold; -fx-font-size: 14px;"
            + (this.descriptor.isLocal() ? " -fx-text-fill:-color-success-emphasis;" : ""));
    title.setTooltip(new Tooltip(tooltip));
    title.setMaxWidth(180);

    HBox buttonContainer = new HBox();
    buttonContainer.setSpacing(4);
    buttonContainer.setAlignment(Pos.CENTER_RIGHT);

    if (selectHandler != null) {
      var openButton = new Label(null, new IconLabel(Material2MZ.OPEN_IN_NEW, 16, Color.DARKGREEN));
      openButton.setCursor(Cursor.HAND);
      openButton.setOnMouseClicked(
          e -> {
            e.consume();
            selectHandler.accept(this.descriptor);
          });
      buttonContainer.getChildren().add(openButton);
    }

    var linkButton =
        new Label(null, new IconLabel(Material2AL.CONTENT_COPY, 16, Color.DARKGOLDENROD));
    linkButton.setTooltip(new Tooltip("Copy URN to clipboard"));
    linkButton.setCursor(Cursor.HAND);
    linkButton.setOnMouseClicked(
        e -> {
          e.consume();
          final var clipboard = Clipboard.getSystemClipboard();
          final var ct = new ClipboardContent();
          ct.putString(descriptor.getUrn());
          clipboard.setContent(ct);
        });
    buttonContainer.getChildren().add(linkButton);

    if (deleteHandler != null) {
      var deleteButton =
          new Label(null, new IconLabel(Material2AL.DELETE_FOREVER, 16, Color.DARKRED));
      deleteButton.setCursor(Cursor.HAND);
      deleteButton.setOnMouseClicked(
          e -> {
            e.consume();
            deleteHandler.accept(this.descriptor);
          });
      buttonContainer.getChildren().add(deleteButton);
    }

    HBox.setHgrow(buttonContainer, Priority.ALWAYS);

    HBox header = new HBox(10, icon, title, buttonContainer);
    header.setAlignment(Pos.CENTER_LEFT);
    header.setMaxWidth(Double.MAX_VALUE);
    HBox.setHgrow(header, Priority.ALWAYS);

    TextArea description = new TextArea(comment);
    description.setWrapText(true);
    description.setEditable(false);
    description.setPrefRowCount(3);
    body.getChildren().add(description);

    Label status = new Label(details(this.descriptor));
    status.setStyle("-fx-font-size: 10px;");
    HBox footer = new HBox(status);
    footer.setAlignment(Pos.CENTER_RIGHT);

    card.setOnMouseClicked(
        event -> {
          if (selectHandler != null) {
            selectHandler.accept(this.descriptor);
          }
        });

    card.setBody(body);
    card.setHeader(header);
    card.setFooter(footer);
    this.getChildren().add(card);
    return card;
  }

  static String displayName(ResourceInfo descriptor) {
    return firstMetadata(
        descriptor,
        descriptor.getUrn(),
        Metadata.DC_LABEL,
        Metadata.DC_TITLE,
        Metadata.DC_NAME,
        Metadata.RDFS_LABEL);
  }

  static String description(ResourceInfo descriptor) {
    return firstMetadata(
        descriptor,
        "No description available",
        Metadata.DC_DESCRIPTION,
        Metadata.DC_DESCRIPTION_ABSTRACT,
        Metadata.DC_COMMENT,
        Metadata.RDFS_COMMENT);
  }

  static String details(ResourceInfo descriptor) {
    List<String> details = new ArrayList<>();
    if (descriptor.getType() != null) {
      details.add(descriptor.getType().name().toLowerCase().replace('_', ' '));
    }
    if (descriptor.getOwner() != null && !descriptor.getOwner().isBlank()) {
      details.add("by " + descriptor.getOwner());
    }
    Object adapter =
        descriptor.getMetadata() == null ? null : descriptor.getMetadata().get("im:adapter");
    if (adapter != null && !adapter.toString().isBlank()) {
      details.add(adapter.toString());
    }
    return details.isEmpty() ? "Resource" : String.join(" · ", details);
  }

  private static String firstMetadata(
      ResourceInfo descriptor, String fallback, String... metadataKeys) {
    if (descriptor.getMetadata() != null) {
      for (String key : metadataKeys) {
        Object value = descriptor.getMetadata().get(key);
        if (value != null && !value.toString().isBlank()) {
          return value.toString();
        }
      }
    }
    return fallback;
  }
}
