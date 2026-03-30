package org.integratedmodelling.klab.ide.components.generic;

import atlantafx.base.theme.Styles;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.util.Duration;
import org.kordamp.ikonli.Ikon;
import org.kordamp.ikonli.material2.Material2AL;
import org.kordamp.ikonli.material2.Material2MZ;

import java.util.ArrayList;
import java.util.List;

/**
 * A generic JavaFX notebook component that displays JavaFX nodes as collapsible, titled cards
 * arranged sequentially in a scrollable area. An index pane on the right shows a miniature
 * navigation card for each entry; clicking an index card scrolls the corresponding card into view.
 *
 * <p>Usage:
 *
 * <pre>
 * var notebook = new Notebook();
 * notebook.addCard("id1", Material2AL.INFO, "Title", "Optional subtitle", myNode);
 * notebook.focusCard("id1");
 * notebook.pinCard("id1");
 * </pre>
 */
public class Notebook extends BorderPane {

  // ---- style constants (AtlantaFX CSS variables — theme-adaptive) ----

  private static final String CARD_NORMAL =
      "-fx-border-color: -color-border-default; -fx-border-radius: 6; -fx-border-width: 1;"
          + " -fx-background-radius: 6; -fx-background-color: -color-bg-default;";
  private static final String CARD_ACTIVE =
      "-fx-border-color: -color-neutral-emphasis; -fx-border-width: 2; -fx-border-radius: 6;"
          + " -fx-background-radius: 6; -fx-background-color: -color-bg-default;";

  private static final String INDEX_NORMAL =
      "-fx-border-color: -color-border-default; -fx-border-radius: 4; -fx-border-width: 1;"
          + " -fx-background-radius: 4; -fx-background-color: -color-bg-subtle; -fx-cursor: hand;";
  private static final String INDEX_ACTIVE =
      "-fx-border-color: -color-neutral-emphasis; -fx-border-width: 2; -fx-border-radius: 4;"
          + " -fx-background-radius: 4; -fx-background-color: -color-neutral-subtle; -fx-cursor: hand;";

  private static final String HEADER_STYLE =
      "-fx-background-color: -color-bg-subtle; -fx-background-radius: 5 5 0 0;";

  // ---- state ----

  private final List<CardEntry> cards = new ArrayList<>();

  /** Holds pinned cards; sits above the scroll pane and does not move when scrolling. */
  private final VBox pinnedContainer;

  /** Holds unpinned cards inside the scroll pane. */
  private final VBox cardContainer;

  private final VBox indexContainer;
  private final ScrollPane mainScroll;
  private CardEntry activeCard;

  // ---- constructor ----

  public Notebook() {
    pinnedContainer = new VBox(10);
    pinnedContainer.setPadding(new Insets(12, 12, 0, 12));
    pinnedContainer.setFillWidth(true);
    pinnedContainer.setStyle("-fx-background-color: -color-bg-default;");

    cardContainer = new VBox(10);
    cardContainer.setPadding(new Insets(10, 12, 12, 12));
    cardContainer.setFillWidth(true);

    mainScroll = new ScrollPane(cardContainer);
    mainScroll.setFitToWidth(true);
    mainScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
    mainScroll.setStyle(
        "-fx-background: -color-bg-default; -fx-background-color: -color-bg-default;");

    // Pinned area sits above the scroll pane; only unpinned cards scroll
    BorderPane centerPane = new BorderPane();
    centerPane.setTop(pinnedContainer);
    centerPane.setCenter(mainScroll);
    centerPane.setStyle("-fx-background-color: -color-bg-default;");

    indexContainer = new VBox(8);
    indexContainer.setPadding(new Insets(10, 6, 10, 6));
    indexContainer.setFillWidth(true);

    ScrollPane indexScroll = new ScrollPane(indexContainer);
    indexScroll.setFitToWidth(true);
    indexScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
    indexScroll.setPrefWidth(120);
    indexScroll.setMinWidth(80);
    indexScroll.setMaxWidth(140);
    indexScroll.setStyle(
        "-fx-background: -color-bg-subtle; -fx-background-color: -color-bg-subtle;");

    setCenter(centerPane);
    setRight(indexScroll);
  }

  // ---- public API ----

  /**
   * Adds a card with a subtitle to the notebook.
   *
   * @param id unique card identifier; used by all other API methods
   * @param icon Ikonli icon shown in the header and index
   * @param title card title
   * @param subtitle secondary label shown below the title in lighter, smaller text; {@code null} or
   *     blank to omit
   * @param content the JavaFX node to display as the card body
   */
  public void addCard(String id, Ikon icon, String title, String subtitle, Node content) {
    CardEntry entry = new CardEntry(id, icon, title, subtitle, content);
    cards.add(entry);
    refreshView();
    focusCard(id);
  }

  /**
   * Adds a card without a subtitle.
   *
   * @param id unique card identifier
   * @param icon Ikonli icon shown in the header and index
   * @param title card title
   * @param content the JavaFX node to display as the card body
   */
  public void addCard(String id, Ikon icon, String title, Node content) {
    addCard(id, icon, title, null, content);
  }

  /**
   * Removes the card with the given id. No-op if the id is not found.
   *
   * @param id card identifier
   */
  public void removeCard(String id) {
    cards.removeIf(e -> e.id.equals(id));
    if (activeCard != null && activeCard.id.equals(id)) {
      activeCard = null;
    }
    refreshView();
  }

  public boolean hasCard(String id) {
    return cards.stream().anyMatch(e -> e.id.equals(id));
  }

  /**
   * Scrolls the card with the given id into view so that the top of the card aligns with the top of
   * the notebook's visible area. Marks the card as active and expands it if it was collapsed.
   *
   * @param id card identifier
   */
  public void focusCard(String id) {
    CardEntry target = findCard(id);
    if (target == null) {
      return;
    }
    if (target.collapsed) {
      target.cardView.setCollapsed(false);
    }
    setActiveCard(target);
    // Pinned cards are always visible above the scroll pane — no scrolling needed
    if (!target.pinned) {
      Platform.runLater(
          () -> {
            double cardY = target.cardView.getBoundsInParent().getMinY();
            double containerH = cardContainer.getHeight();
            double viewportH = mainScroll.getViewportBounds().getHeight();
            double scrollable = containerH - viewportH;
            if (scrollable > 0) {
              mainScroll.setVvalue(Math.max(0.0, Math.min(1.0, cardY / scrollable)));
            }
          });
    }
  }

  /**
   * Pins the card with the given id to the top of the notebook. Any previously pinned card is
   * automatically unpinned first, since only one card may be pinned at a time. No-op if the card is
   * already pinned.
   *
   * @param id card identifier
   */
  public void pinCard(String id) {
    CardEntry entry = findCard(id);
    if (entry != null && !entry.pinned) {
      // Enforce single-pin constraint: unpin whichever card is currently pinned
      for (CardEntry e : cards) {
        if (e.pinned) {
          e.pinned = false;
          e.cardView.updatePinButton(false);
        }
      }
      entry.pinned = true;
      entry.cardView.updatePinButton(true);
      refreshView();
    }
  }

  /**
   * Unpins the card with the given id, returning it to its insertion-order position among unpinned
   * cards. No-op if the card is not pinned.
   *
   * @param id card identifier
   */
  public void unpinCard(String id) {
    CardEntry entry = findCard(id);
    if (entry != null && entry.pinned) {
      entry.pinned = false;
      entry.cardView.updatePinButton(false);
      refreshView();
    }
  }

  // ---- private helpers ----

  private CardEntry findCard(String id) {
    return cards.stream().filter(e -> e.id.equals(id)).findFirst().orElse(null);
  }

  private void setActiveCard(CardEntry target) {
    if (activeCard != null && activeCard != target) {
      activeCard.cardView.setActive(false);
      activeCard.indexCard.setActive(false);
    }
    activeCard = target;
    if (activeCard != null) {
      activeCard.cardView.setActive(true);
      activeCard.indexCard.setActive(true);
    }
  }

  private void refreshView() {
    pinnedContainer.getChildren().clear();
    cardContainer.getChildren().clear();
    indexContainer.getChildren().clear();

    boolean hasPinned = false;
    boolean hasUnpinned = false;

    // Pinned cards go into the fixed area above the scroll pane
    for (CardEntry e : cards) {
      if (e.pinned) {
        pinnedContainer.getChildren().add(e.cardView);
        indexContainer.getChildren().add(e.indexCard);
        hasPinned = true;
      }
    }
    // Unpinned cards go inside the scroll pane
    for (CardEntry e : cards) {
      if (!e.pinned) {
        cardContainer.getChildren().add(e.cardView);
        indexContainer.getChildren().add(e.indexCard);
        hasUnpinned = true;
      }
    }

    // Show a subtle divider between the pinned and scrollable zones when both are present
    pinnedContainer.setStyle(
        hasPinned && hasUnpinned
            ? "-fx-background-color: -color-bg-default;"
                + " -fx-border-color: transparent transparent transparent transparent;"
                + " -fx-border-width: 0 0 1 0; -fx-padding: 0 0 6 0;"
            : "-fx-background-color: -color-bg-default;");
  }

  /** Collapses all cards in the notebook, except for the pinned one if any. */
  public void collapseAll() {
    for (CardEntry e : cards) {
      if (!e.pinned) {
        e.cardView.setCollapsed(true);
      }
    }
  }

  // ---- inner classes ----

  private class CardEntry {
    final String id;
    final CardView cardView;
    final IndexCard indexCard;
    boolean pinned;
    boolean collapsed;

    CardEntry(String id, Ikon icon, String title, String subtitle, Node content) {
      this.id = id;
      this.pinned = false;
      this.collapsed = false;
      this.cardView = new CardView(this, icon, title, subtitle, content);
      this.indexCard = new IndexCard(this, icon, title);
    }
  }

  /** Full card displayed in the main scrollable area. */
  private class CardView extends VBox {

    private final CardEntry entry;
    private final Node bodyWrapper;
    private final Button collapseBtn;
    private final Button pinBtn;
    private boolean collapsed;

    CardView(CardEntry entry, Ikon icon, String title, String subtitle, Node body) {
      this.entry = entry;
      this.collapsed = false;

      setStyle(CARD_NORMAL);

      // ---- header ----
      HBox header = new HBox(8);
      header.setAlignment(Pos.CENTER_LEFT);
      header.setPadding(new Insets(7, 10, 7, 10));
      header.setStyle(HEADER_STYLE);

      IconLabel iconLabel = new IconLabel(icon, 18, "-color-fg-default");

      VBox titleBox = new VBox(2);
      HBox.setHgrow(titleBox, Priority.ALWAYS);

      Label titleLabel = new Label(title);
      titleLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 13px;");
      titleBox.getChildren().add(titleLabel);

      if (subtitle != null && !subtitle.isBlank()) {
        Label subtitleLabel = new Label(subtitle);
        subtitleLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: -color-fg-muted;");
        titleBox.getChildren().add(subtitleLabel);
      }

      collapseBtn = makeIconButton(Material2AL.EXPAND_LESS, "Collapse / Expand");
      collapseBtn.setOnAction(e -> setCollapsed(!this.collapsed));

      pinBtn = makeIconButton(Material2MZ.PUSH_PIN, "Pin to top");
      pinBtn.setOnAction(
          e -> {
            if (entry.pinned) {
              unpinCard(entry.id);
            } else {
              pinCard(entry.id);
            }
          });

      Button deleteBtn = makeIconButton(Material2AL.CLOSE, "Remove from notebook");
      deleteBtn.setOnAction(e -> removeCard(entry.id));

      header.getChildren().addAll(iconLabel, titleBox, pinBtn, collapseBtn, deleteBtn);
      // Single-click activates the card in the index; double-click also toggles collapse
      header.setOnMouseClicked(
          e -> {
            setActiveCard(entry);
            if (e.getClickCount() == 2) {
              setCollapsed(!this.collapsed);
            }
          });

      // ---- body ----
      VBox wrapper = new VBox(body);
      wrapper.setPadding(new Insets(8, 10, 10, 10));
      this.bodyWrapper = wrapper;

      getChildren().addAll(header, bodyWrapper);
    }

    void setCollapsed(boolean collapse) {
      this.collapsed = collapse;
      entry.collapsed = collapse;
      bodyWrapper.setVisible(!collapse);
      bodyWrapper.setManaged(!collapse);
      collapseBtn.setGraphic(
          new IconLabel(
              collapse ? Material2AL.EXPAND_MORE : Material2AL.EXPAND_LESS, 14, "-color-fg-muted"));
    }

    void setActive(boolean active) {
      setStyle(active ? CARD_ACTIVE : CARD_NORMAL);
    }

    void updatePinButton(boolean pinned) {
      pinBtn.setGraphic(
          new IconLabel(
              Material2MZ.PUSH_PIN, 14, pinned ? "-color-warning-fg" : "-color-fg-muted"));
      pinBtn.setTooltip(new Tooltip(pinned ? "Unpin" : "Pin to top"));
    }
  }

  /** Miniature card shown in the index pane on the right. */
  private class IndexCard extends VBox {

    IndexCard(CardEntry entry, Ikon icon, String title) {
      setAlignment(Pos.CENTER);
      setPadding(new Insets(6, 4, 6, 4));
      setSpacing(4);
      setMaxWidth(Double.MAX_VALUE);
      setStyle(INDEX_NORMAL);

      IconLabel iconLabel = new IconLabel(icon, 16, "-color-fg-default");

      String abbrev = title.length() > 14 ? title.substring(0, 13) + "\u2026" : title;
      Label titleLabel = new Label(abbrev);
      titleLabel.setWrapText(true);
      titleLabel.setStyle(
          "-fx-font-size: 10px; -fx-text-alignment: center; -fx-text-fill: -color-fg-muted;");
      //      titleLabel.setMaxWidth(74);
      HBox.setHgrow(titleLabel, Priority.ALWAYS);
      getChildren().addAll(iconLabel, titleLabel);
      var tooltip = new Tooltip(title);
      tooltip.setShowDelay(Duration.millis(200));
      titleLabel.setTooltip(tooltip);

      setOnMouseClicked(e -> focusCard(entry.id));
    }

    void setActive(boolean active) {
      setStyle(active ? INDEX_ACTIVE : INDEX_NORMAL);
    }
  }

  // ---- utilities ----

  private static Button makeIconButton(Ikon icon, String tooltip) {
    Button btn = new Button();
    btn.setGraphic(new IconLabel(icon, 14, "-color-fg-muted"));
    btn.getStyleClass().addAll(Styles.BUTTON_ICON, Styles.FLAT);
    btn.setTooltip(new Tooltip(tooltip));
    btn.setStyle("-fx-padding: 2;");
    return btn;
  }
}
