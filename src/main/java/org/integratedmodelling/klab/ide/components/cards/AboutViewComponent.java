package org.integratedmodelling.klab.ide.components.cards;

import atlantafx.base.theme.Styles;
import atlantafx.base.theme.Tweaks;
import java.io.IOException;
import java.time.Year;
import java.util.List;
import java.util.Objects;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.geometry.VPos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.shape.Circle;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import org.integratedmodelling.klab.api.data.Version;
import org.integratedmodelling.klab.ide.KlabIDEApplication;
import org.integratedmodelling.klab.ide.Theme;
import org.integratedmodelling.klab.ide.components.generic.IconLabel;
import org.integratedmodelling.klab.ide.components.generic.WordPressPostViewer;
import org.kordamp.ikonli.Ikon;
import org.kordamp.ikonli.javafx.FontIcon;
import org.kordamp.ikonli.material2.Material2AL;

public class AboutViewComponent extends BaseAssetViewComponent {

  private static final String WELCOME_PANEL_STYLE =
      "-fx-background-color: -color-bg-subtle;"
          + " -fx-background-radius: 8;"
          + " -fx-border-color: -color-border-subtle;"
          + " -fx-border-radius: 8;"
          + " -fx-border-width: 1;";

  // TODO integrate these in a collapsible "Credits" box.
  public static String[] credits = {
    "Sentinel-2 cloudless - https://s2maps.eu by EOX IT Services GmbH "
        + "(Contains modified Copernicus Sentinel data 2024)"
  };

  public AboutViewComponent() {
    super(AssetViewComponent.Type.About, "About k.LAB", true);
  }

  @Override
  public String getDescription() {
    return "General information, resources, news and services";
  }

  @Override
  public Ikon getIcon() {
    return Material2AL.INFO;
  }

  @Override
  protected Node createContent() {
    VBox content = new VBox(18);
    content.setPadding(new Insets(18));
    content.setFillWidth(true);

    content.getChildren().addAll(createWelcomePanel(), createMainBody(), createFooter());

    getChildren().add(content);
    return content;
  }

  private Node createWelcomePanel() {
    ImageView logoView;
    try (var logoStream =
        Objects.requireNonNull(
            getClass()
                .getResourceAsStream(
                    "/org/integratedmodelling/klab/ide/icons/klab-elephant.png"),
            "Missing k.LAB elephant logo")) {
      Image logo = new Image(logoStream, 196, 196, true, true);
      logoView = new ImageView(logo);
      logoView.setPreserveRatio(true);
      logoView.setFitWidth(180);
    } catch (IOException e) {
      throw new IllegalStateException("Unable to load the k.LAB logo", e);
    }

    Circle logoBackground = new Circle(112);
    logoBackground.setStyle("-fx-fill: -color-accent-subtle;");
    StackPane logo = new StackPane(logoBackground, logoView);
    logo.setMinSize(250, 230);
    logo.setPrefSize(250, 230);
    logo.setMaxSize(250, 230);

    VBox logoBox = new VBox(logo);
    logoBox.setAlignment(Pos.CENTER);
    logoBox.setMinWidth(250);

    Label eyebrow = new Label("INTEGRATED MODELLING");
    eyebrow.getStyleClass().addAll(Styles.TEXT_CAPTION, Styles.TEXT_MUTED, Styles.TEXT_BOLD);

    Label title = new Label("Knowledge, connected.");
    title.getStyleClass().add(Styles.TITLE_2);

    Label description =
        new Label(
            "k.LAB is a distributed semantic modelling platform for integrating diverse "
                + "knowledge. It combines strong semantics with practical modelling to support "
                + "modularity, interoperability, reuse, and multiple paradigms and scales.");
    description.setWrapText(true);
    description.getStyleClass().add(Styles.TEXT_MUTED);
    description.setMaxWidth(650);

    VBox introduction = new VBox(7, eyebrow, title, description);
    introduction.setId("introduction");
    introduction.setAlignment(Pos.CENTER_LEFT);

    var themeToggle = createThemeToggle();
    StackPane introductionOverlay = new StackPane(introduction, themeToggle);
    StackPane.setAlignment(introduction, Pos.CENTER_LEFT);
    StackPane.setAlignment(themeToggle, Pos.TOP_RIGHT);
    HBox.setHgrow(introductionOverlay, Priority.ALWAYS);

    HBox panel = new HBox(24, logoBox, introductionOverlay);
    panel.setAlignment(Pos.CENTER_LEFT);
    panel.setPadding(new Insets(22, 26, 22, 22));
    panel.setMaxWidth(Double.MAX_VALUE);
    panel.setStyle(WELCOME_PANEL_STYLE);
    return panel;
  }

  private MenuButton createThemeToggle() {
    var menu =
        new MenuButton(null, new IconLabel(Material2AL.BRIGHTNESS_4, 20, "-color-fg-default"));
    menu.getStyleClass().addAll(Styles.FLAT, Styles.BUTTON_ICON, Tweaks.NO_ARROW);
    menu.setTooltip(new Tooltip("Select application theme"));
    for (Theme theme : Theme.values()) {
      MenuItem item = new MenuItem(theme.description);
      item.setOnAction(event -> Theme.setCurrentTheme(theme));
      menu.getItems().add(item);
    }
    return menu;
  }

  private Node createMainBody() {
    VBox body = new VBox(26, createResourcesSection(), createExtensionSections());
    body.setPadding(new Insets(4, 8, 2, 8));
    body.setFillWidth(true);
    return body;
  }

  private Node createResourcesSection() {
    FlowPane links = new FlowPane(10, 10);
    links.setPrefWrapLength(720);
    links
        .getChildren()
        .addAll(
            createResourceLinkButton(
                "Documentation", Material2AL.DESCRIPTION, "https://docs.integratedmodelling.org"),
            createResourceLinkButton(
                "Source code",
                Material2AL.CODE,
                "https://github.com/integratedmodelling/klab-services"),
            createResourceLinkButton(
                "Integrated Modelling",
                Material2AL.LANGUAGE,
                "https://www.integratedmodelling.org"),
            createResourceLinkButton(
                "AGPL-3.0 license",
                Material2AL.GAVEL,
                "https://www.gnu.org/licenses/agpl-3.0.en.html"));

    return links;
  }

  private Node createExtensionSections() {
    WordPressPostViewer newsViewer =
        new WordPressPostViewer(Orientation.VERTICAL, "https://klab.integratedmodelling.org");
    newsViewer.setCardHeight(160);
    newsViewer.setPrefHeight(340);
    newsViewer.load();

    WordPressPostViewer servicesViewer =
        new WordPressPostViewer(Orientation.VERTICAL, "https://aries.integratedmodelling.org");
//    servicesViewer.setShowingPages(true);
    servicesViewer.setCardHeight(160);
    servicesViewer.setPrefHeight(340);
    servicesViewer.load();

    VBox news =
        createSection(
            "k.LAB posts", "Latests technical posts about k.LAB.", Material2AL.ARTICLE, newsViewer);
    VBox services =
        createSection(
            "ARIES news",
            "Latest news from the ARIES community.",
            Material2AL.CLOUD_QUEUE,
            servicesViewer);

    news.setMinWidth(280);
    services.setMinWidth(280);
    GridPane.setHgrow(news, Priority.ALWAYS);
    GridPane.setHgrow(services, Priority.ALWAYS);

    GridPane columns = new GridPane();
    columns.setHgap(36);
    columns.add(news, 0, 0);
    columns.add(services, 1, 0);
    return columns;
  }

  private VBox createSection(String title, String subtitle, Ikon icon, Node body) {

    var sectionIcon = new IconLabel(icon, 24, "-color-accent-fg");

    Label titleLabel = new Label(title);
    titleLabel.getStyleClass().addAll(Styles.TITLE_4, Styles.TEXT_BOLD);

    Label subtitleLabel = new Label(subtitle);
    subtitleLabel.setWrapText(true);
    subtitleLabel.getStyleClass().addAll(Styles.TEXT_SMALL, Styles.TEXT_MUTED);

    VBox headingText = new VBox(2, titleLabel, subtitleLabel);
    HBox heading = new HBox(10, sectionIcon, headingText);
    heading.setAlignment(Pos.CENTER_LEFT);

    VBox section = new VBox(4, heading, body);
    section.setMaxWidth(Double.MAX_VALUE);
    VBox.setVgrow(body, Priority.ALWAYS);
    section.getStyleClass().add("about-section");
    return section;
  }

  private Button createResourceLinkButton(String text, Ikon icon, String url) {
    Button button = createLinkButton(text, icon, url);
    button.getStyleClass().add(Styles.BG_NEUTRAL_SUBTLE);
    return button;
  }

  private Button createLinkButton(String text, Ikon icon, String url) {
    FontIcon linkIcon = new FontIcon(icon);
    linkIcon.setIconSize(16);

    Button button = new Button(text, linkIcon);
    button.getStyleClass().add(Styles.BUTTON_OUTLINED);
    button.setStyle("-fx-cursor: hand; -fx-padding: 7 12;");
    button.setOnAction(e -> openUrl(url));
    return button;
  }

  private Node createFooter() {
    Label version = new Label("Version " + Version.CURRENT);
    version.getStyleClass().addAll(Styles.TEXT_SMALL, Styles.TEXT_BOLD);

    Label copyright =
        new Label(
            "© "
                + Year.now().getValue()
                + " Integrated Modelling Partnership · Licensed under AGPL-3.0");
    copyright.getStyleClass().addAll(Styles.TEXT_SMALL, Styles.TEXT_MUTED);

    VBox productInfo = new VBox(2, version, copyright);
    HBox.setHgrow(productInfo, Priority.ALWAYS);

    Label contributorLabel = new Label("Contributors:");
    contributorLabel.getStyleClass().addAll(Styles.TEXT_SMALL, Styles.TEXT_MUTED);

    FlowPane contributorRow = new FlowPane(8, 6);
    contributorRow.setPrefWrapLength(600);
    contributorRow.setRowValignment(VPos.CENTER);
    contributorRow.getChildren().add(contributorLabel);
    for (String developer :
        List.of("Ferdinando Villa", "Enrico Girotto", "Andrea Antonello", "Arnab Moitra")) {
      Label chip = new Label(developer);
      chip.getStyleClass().addAll(Styles.BG_NEUTRAL_SUBTLE, Styles.ROUNDED, Styles.TEXT_SMALL);
      chip.setPadding(new Insets(3, 9, 3, 9));
      contributorRow.getChildren().add(chip);
    }

//    Region separator = new Region();
//    separator.setMinHeight(1);
//    separator.setMaxWidth(Double.MAX_VALUE);
//    separator.setStyle("-fx-background-color: -color-border-subtle;");

    HBox footerContent = new HBox(22, productInfo, contributorRow);
    footerContent.setAlignment(Pos.BOTTOM_LEFT);
    return new VBox(12, /*separator,*/ footerContent);
  }

  private void openUrl(String url) {
    KlabIDEApplication.instance().getHostServices().showDocument(url);
  }
}
