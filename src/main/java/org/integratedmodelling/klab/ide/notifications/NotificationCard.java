package org.integratedmodelling.klab.ide.notifications;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.Tooltip;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import org.kordamp.ikonli.javafx.FontIcon;
import org.kordamp.ikonli.material2.Material2AL;
import org.kordamp.ikonli.material2.Material2MZ;

/**
 * A compact notification card with a bounded, selectable full-message view.
 *
 * <p>The normal card deliberately shows only a short preview. Long and multi-line messages can be
 * expanded into a fixed-height text area which has its own scrollbar, so one notification can never
 * consume the whole notification drawer.
 */
public class NotificationCard extends VBox {

  /** Maximum length of the message shown in the compact card. */
  static final int PREVIEW_CHARACTERS = 80;

  private static final double EXPANDED_MESSAGE_HEIGHT = 140;
  private static final double MAIN_ICON_SIZE = 18;
  private static final double ACTION_SIZE = 22;

  private final String message;
  private final Label previewLabel = new Label();
  private final TextArea fullMessage = new TextArea();
  private final Button expandButton = iconButton(Material2AL.EXPAND_MORE, "Show full message");
  private final Button copyButton = iconButton(Material2AL.CONTENT_COPY, "Copy message");
  private final Button closeButton =
      iconButton(Material2MZ.REMOVE_CIRCLE_OUTLINE, "Dismiss notification");
  private boolean expanded;
  private Runnable onClose = () -> {};

  public NotificationCard(String title, String message, Node icon) {
    super(7);
    this.message = message == null ? "" : message;

    setPadding(new Insets(10));
    setMinWidth(0);
    setMaxWidth(Double.MAX_VALUE);
    getStyleClass().addAll("card", "notification-card");

    var cardTitle = title == null ? "" : title;
    var titleLabel = new Label(cardTitle);
    titleLabel.getStyleClass().add("notification-card-title");
    titleLabel.setTooltip(new Tooltip(cardTitle));
    // Labels otherwise use their text width as the minimum and squeeze adjacent icons.
    titleLabel.setMinWidth(0);
    titleLabel.setMaxWidth(Double.MAX_VALUE);
    titleLabel.setTextOverrun(javafx.scene.control.OverrunStyle.ELLIPSIS);
    HBox.setHgrow(titleLabel, Priority.ALWAYS);

    var header = new HBox(6);
    header.setAlignment(Pos.CENTER_LEFT);
    header.setMinWidth(0);
    if (icon != null) {
      icon.getStyleClass().add("notification-card-main-icon");
      icon.setMouseTransparent(true);
      if (icon instanceof Region region) {
        region.setMinSize(MAIN_ICON_SIZE, MAIN_ICON_SIZE);
        region.setPrefSize(MAIN_ICON_SIZE, MAIN_ICON_SIZE);
        region.setMaxSize(MAIN_ICON_SIZE, MAIN_ICON_SIZE);
      }
      header.getChildren().add(icon);
    }
    header.getChildren().addAll(titleLabel, expandButton, copyButton, closeButton);

    previewLabel.setText(preview(this.message));
    previewLabel.getStyleClass().add("notification-card-message");
    previewLabel.setWrapText(true);
    previewLabel.setMaxWidth(Double.MAX_VALUE);

    fullMessage.setText(this.message);
    fullMessage.getStyleClass().add("notification-card-message");
    fullMessage.setEditable(false);
    fullMessage.setWrapText(true);
    fullMessage.setMinHeight(EXPANDED_MESSAGE_HEIGHT);
    fullMessage.setPrefHeight(EXPANDED_MESSAGE_HEIGHT);
    fullMessage.setMaxHeight(EXPANDED_MESSAGE_HEIGHT);
    fullMessage.setMaxWidth(Double.MAX_VALUE);
    fullMessage.setVisible(false);
    fullMessage.setManaged(false);
    VBox.setVgrow(fullMessage, Priority.NEVER);

    boolean expandable = isExpandable(this.message);
    expandButton.setVisible(expandable);
    expandButton.setManaged(expandable);
    expandButton.setOnAction(event -> setExpanded(!expanded));
    copyButton.setOnAction(event -> copyMessage());
    closeButton.setOnAction(event -> onClose.run());

    // Match the log viewer: reserve the small copy action but reveal it only on interaction.
    copyButton.setOpacity(0);
    setOnMouseEntered(event -> copyButton.setOpacity(1));
    setOnMouseExited(event -> copyButton.setOpacity(copyButton.isFocused() ? 1 : 0));
    copyButton
        .focusedProperty()
        .addListener((observable, oldValue, focused) -> copyButton.setOpacity(focused ? 1 : 0));

    getChildren().addAll(header, previewLabel, fullMessage);
  }

  public void setOnCloseAction(Runnable onClose) {
    this.onClose = onClose == null ? () -> {} : onClose;
  }

  public String getMessage() {
    return message;
  }

  public boolean isExpanded() {
    return expanded;
  }

  public void setExpanded(boolean expanded) {
    if (!expandButton.isManaged()) {
      return;
    }
    this.expanded = expanded;
    previewLabel.setVisible(!expanded);
    previewLabel.setManaged(!expanded);
    fullMessage.setVisible(expanded);
    fullMessage.setManaged(expanded);
    setButtonIcon(expandButton, expanded ? Material2AL.EXPAND_LESS : Material2AL.EXPAND_MORE);
    expandButton.setTooltip(new Tooltip(expanded ? "Collapse message" : "Show full message"));
  }

  static boolean isExpandable(String message) {
    return message != null
        && (message.length() > PREVIEW_CHARACTERS
            || message.indexOf('\n') >= 0
            || message.indexOf('\r') >= 0);
  }

  static String preview(String message) {
    if (message == null || message.isBlank()) {
      return "";
    }
    var singleLine = message.strip().replaceAll("\\s+", " ");
    if (singleLine.length() <= PREVIEW_CHARACTERS) {
      return singleLine;
    }
    return singleLine.substring(0, PREVIEW_CHARACTERS).stripTrailing() + "\u2026";
  }

  private void copyMessage() {
    var content = new ClipboardContent();
    content.putString(message);
    Clipboard.getSystemClipboard().setContent(content);
  }

  private static Button iconButton(org.kordamp.ikonli.Ikon icon, String tooltip) {
    var button = new Button();
    setButtonIcon(button, icon);
    button.getStyleClass().addAll("button-icon", "flat", "notification-card-action");
    button.setFocusTraversable(false);
    button.setMinSize(ACTION_SIZE, ACTION_SIZE);
    button.setPrefSize(ACTION_SIZE, ACTION_SIZE);
    button.setMaxSize(ACTION_SIZE, ACTION_SIZE);
    button.setTooltip(new Tooltip(tooltip));
    return button;
  }

  private static void setButtonIcon(Button button, org.kordamp.ikonli.Ikon icon) {
    var fontIcon = new FontIcon(icon);
    fontIcon.setIconSize(12);
    button.setGraphic(fontIcon);
  }
}
