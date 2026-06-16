package org.integratedmodelling.klab.ide.test;

import atlantafx.base.controls.Card;
import atlantafx.base.theme.PrimerLight;
import atlantafx.base.theme.Styles;
import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import org.integratedmodelling.klab.ide.components.generic.CarouselBox;
import org.kordamp.ikonli.javafx.FontIcon;
import org.kordamp.ikonli.material2.Material2AL;
import org.kordamp.ikonli.material2.Material2MZ;

/**
 * Standalone showcase for the {@link CarouselBox} component.
 *
 * <p>The window contains:
 *
 * <ul>
 *   <li>A <b>horizontal</b> carousel holding 8 AtlantaFX {@link Card} items (wider than the window,
 *       so the nav strips are visible from the start).
 *   <li>A <b>vertical</b> carousel on the right holding 6 cards (taller than its allocated height).
 *   <li>Buttons to add a card, remove the selected card, and clear the horizontal carousel.
 *   <li>A status bar showing the name of the currently selected item.
 * </ul>
 *
 * <p>Run via {@link #main(String[])}.
 */
public class CarouselBoxTest extends Application {

  // ── Sample data ───────────────────────────────────────────────────────────────

  private static final String[] TITLES = {
    "Observation Model",
    "Spatial Dataset",
    "Time Series",
    "Agent Network",
    "Knowledge Graph",
    "Simulation Run",
    "Remote Sensor",
    "Analysis Report"
  };

  private static final String[] DESCRIPTIONS = {
    "Continuous coverage observable mapped to the study area at 30 m resolution.",
    "Raster dataset with daily precipitation values for 2020–2024.",
    "Agent-based model tracking resource flows across urban districts.",
    "Semantic network linking observable concepts across 14 ontologies.",
    "Stochastic ensemble run with 500 Monte Carlo iterations.",
    "Multi-spectral imagery acquired from the Sentinel-2 constellation.",
    "Statistical analysis of land-use change detection results.",
    "Integrated report covering three seasonal observation windows."
  };

  private static final String[] ACCENT_COLORS = {
    "#3b82f6", "#10b981", "#f59e0b", "#ef4444",
    "#8b5cf6", "#06b6d4", "#84cc16", "#f97316"
  };

  // ── Component references ──────────────────────────────────────────────────────

  private CarouselBox horizontalCarousel;
  private CarouselBox verticalCarousel;
  private Label statusLabel;
  private int addCounter = 0;

  // ── Application entry point ───────────────────────────────────────────────────

  @Override
  public void start(Stage stage) {
    Application.setUserAgentStylesheet(new PrimerLight().getUserAgentStylesheet());

    // ── Status bar ─────────────────────────────────────────────────────────────
    statusLabel = new Label("No item selected");
    statusLabel.getStyleClass().addAll(Styles.TEXT_MUTED, Styles.TEXT_SMALL);

    HBox statusBar = new HBox(statusLabel);
    statusBar.setAlignment(Pos.CENTER_LEFT);
    statusBar.setPadding(new Insets(4, 10, 4, 10));
    statusBar.setStyle(
        "-fx-background-color: -color-bg-subtle; "
            + "-fx-border-color: -color-border-default; "
            + "-fx-border-width: 1 0 0 0;");

    // ── Horizontal carousel ────────────────────────────────────────────────────
    Label hTitle = sectionTitle("Horizontal Carousel — 8 cards, 180 px each (overflows → arrows)");

    horizontalCarousel = new CarouselBox(Orientation.HORIZONTAL);
    horizontalCarousel.setPrefHeight(175);
    horizontalCarousel.setMaxWidth(Double.MAX_VALUE);
    HBox.setHgrow(horizontalCarousel, Priority.ALWAYS);

    for (int i = 0; i < 8; i++) {
      horizontalCarousel.addItem(makeHorizontalCard(i));
    }
    horizontalCarousel.setSelectionListener(this::reportSelection);

    Button addBtn = new Button("+ Add card");
    addBtn.getStyleClass().add(Styles.ACCENT);
    addBtn.setOnAction(
        e -> {
          int idx = addCounter++ % TITLES.length;
          horizontalCarousel.addItem(makeHorizontalCard(idx));
        });

    Button removeBtn = new Button("Remove selected");
    removeBtn.setOnAction(
        e -> {
          Node sel = horizontalCarousel.getSelectedItem();
          if (sel != null) horizontalCarousel.removeItem(sel);
        });

    Button clearBtn = new Button("Clear all");
    clearBtn.getStyleClass().add(Styles.DANGER);
    clearBtn.setOnAction(e -> horizontalCarousel.clear());

    HBox hControls = new HBox(8, addBtn, removeBtn, clearBtn);
    hControls.setAlignment(Pos.CENTER_LEFT);

    VBox hSection = new VBox(8, hTitle, horizontalCarousel, hControls);
    HBox.setHgrow(hSection, Priority.ALWAYS);

    // ── Vertical carousel ──────────────────────────────────────────────────────
    Label vTitle = sectionTitle("Vertical — 6 cards");

    verticalCarousel = new CarouselBox(Orientation.VERTICAL);
    verticalCarousel.setPrefWidth(260);
    verticalCarousel.setPrefHeight(260);
    VBox.setVgrow(verticalCarousel, Priority.ALWAYS);

    for (int i = 0; i < 6; i++) {
      verticalCarousel.addItem(makeTagControl(i));
    }
    verticalCarousel.setSelectionListener(this::reportSelection);

    VBox vSection = new VBox(8, vTitle, verticalCarousel);
    vSection.setMinWidth(260);

    // ── Main body ──────────────────────────────────────────────────────────────
    HBox columns = new HBox(16, hSection, new Separator(Orientation.VERTICAL), vSection);
    columns.setAlignment(Pos.TOP_LEFT);
    VBox.setVgrow(columns, Priority.ALWAYS);

    VBox body = new VBox(14, columns);
    body.setPadding(new Insets(16));
    VBox.setVgrow(body, Priority.ALWAYS);

    VBox root = new VBox(body, statusBar);
    VBox.setVgrow(body, Priority.ALWAYS);

    // ── Scene ──────────────────────────────────────────────────────────────────
    Scene scene = new Scene(root, 920, 460);
    scene
        .getStylesheets()
        .add(
            CarouselBoxTest.class
                .getResource("/org/integratedmodelling/klab/ide/custom.css")
                .toExternalForm());

    stage.setTitle("CarouselBox Showcase");
    stage.setScene(scene);
    stage.show();
  }

  // ── Card factories ────────────────────────────────────────────────────────────

  /**
   * Builds a vertically-compact card sized for the horizontal carousel (fixed 180 px width, height
   * determined by the carousel container).
   */
  private Card makeHorizontalCard(int index) {
    String title = TITLES[index % TITLES.length];
    String color = ACCENT_COLORS[index % ACCENT_COLORS.length];
    String desc = DESCRIPTIONS[index % DESCRIPTIONS.length];

    // Colored dot accent
    Label dot = new Label();
    dot.setMinSize(10, 10);
    dot.setMaxSize(10, 10);
    dot.setStyle("-fx-background-color: " + color + "; " + "-fx-background-radius: 5px;");

    Label titleLabel = new Label(title);
    titleLabel.getStyleClass().add(Styles.TEXT_BOLD);
    titleLabel.setWrapText(false);

    HBox header = new HBox(6, dot, titleLabel);
    header.setAlignment(Pos.CENTER_LEFT);
    header.setPadding(new Insets(6, 8, 2, 8));

    Label descLabel = new Label(desc);
    descLabel.setWrapText(true);
    descLabel.getStyleClass().add(Styles.TEXT_SMALL);
    descLabel.setMaxWidth(160);

    VBox cardBody = new VBox(4, descLabel);
    cardBody.setPadding(new Insets(0, 8, 4, 8));

    FontIcon footerIcon = new FontIcon(Material2AL.LABEL_IMPORTANT);
    footerIcon.setIconSize(12);
    footerIcon.setStyle("-fx-icon-color: -color-fg-muted;");

    Label tagLabel = new Label("Item #" + (index + 1));
    tagLabel.getStyleClass().addAll(Styles.TEXT_SMALL, Styles.TEXT_MUTED);

    HBox footer = new HBox(4, footerIcon, tagLabel);
    footer.setAlignment(Pos.CENTER_LEFT);
    footer.setPadding(new Insets(2, 8, 6, 8));

    Card card = new Card();
    card.setHeader(header);
    card.setBody(cardBody);
    card.setFooter(footer);
    card.setPrefWidth(180);
    card.setPrefHeight(170);
    card.setUserData(title);

    return card;
  }

  /**
   * Builds a compact row-style card for the vertical carousel (full width, fixed ~90 px height).
   */
  private Card makeTagControl(int index) {
    int shifted = (index + 3) % TITLES.length;
    String title = TITLES[shifted];
    String color = ACCENT_COLORS[shifted % ACCENT_COLORS.length];
    String desc = DESCRIPTIONS[(index + 1) % DESCRIPTIONS.length];

    FontIcon icon = new FontIcon(Material2MZ.MEMORY);
    icon.setIconSize(16);
    icon.setStyle("-fx-icon-color: " + color + ";");

    Label titleLabel = new Label(title);
    titleLabel.getStyleClass().add(Styles.TEXT_BOLD);

    HBox header = new HBox(6, icon, titleLabel);
    header.setAlignment(Pos.CENTER_LEFT);

    Label descLabel = new Label(desc);
    descLabel.getStyleClass().addAll(Styles.TEXT_SMALL, Styles.TEXT_MUTED);
    descLabel.setWrapText(true);

    Card card = new Card();
    card.setHeader(header);
    card.setBody(descLabel);
    card.setPrefHeight(90);
    card.setMaxWidth(Double.MAX_VALUE);
    card.setUserData(title);

    return card;
  }

  // ── Helpers ───────────────────────────────────────────────────────────────────

  private static Label sectionTitle(String text) {
    Label label = new Label(text);
    label.getStyleClass().addAll(Styles.TEXT_BOLD, Styles.TEXT_SMALL);
    return label;
  }

  private void reportSelection(Node node) {
    if (node == null) {
      statusLabel.setText("No item selected");
    } else {
      Object data = node.getUserData();
      statusLabel.setText("Selected: " + (data != null ? data : node.getClass().getSimpleName()));
    }
  }

  public static void main(String[] args) {
    launch(args);
  }
}
