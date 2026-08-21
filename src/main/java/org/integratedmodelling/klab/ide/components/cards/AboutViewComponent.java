package org.integratedmodelling.klab.ide.components.cards;

import atlantafx.base.theme.Styles;
import java.io.IOException;
import java.time.Year;
import java.util.List;
import java.util.Objects;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.VPos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
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
import org.integratedmodelling.klab.ide.components.generic.IconButton;
import org.integratedmodelling.klab.ide.components.generic.IconLabel;
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

  private VBox newsContent;
  private VBox servicesContent;

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
            getClass().getResourceAsStream("/org/integratedmodelling/klab/ide/icons/klab-im.png"),
            "Missing k.LAB logo")) {
      Image logo = new Image(logoStream, 250, 105, true, true);
      logoView = new ImageView(logo);
      logoView.setPreserveRatio(true);
      logoView.setFitWidth(230);
    } catch (IOException e) {
      throw new IllegalStateException("Unable to load the k.LAB logo", e);
    }

    VBox logoBox = new VBox(logoView);
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

  private IconButton createThemeToggle() {
    Theme[] themes = Theme.values();
    var toggle =
        IconButton.toggle(
            Material2AL.BRIGHTNESS_4,
            20,
            "-color-fg-default",
            "-color-fg-muted",
            () -> {
              int nextTheme = (Theme.CURRENT_THEME.ordinal() + 1) % themes.length;
              Theme.setCurrentTheme(themes[nextTheme]);
              return true;
            });
    toggle.setToggled(Theme.CURRENT_THEME.isDark());
    toggle.tooltip("Cycle through available themes");
    return toggle;
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
    //    return createSection(
    //        "Resources",
    //        "Documentation, community resources and project information.",
    //        Material2AL.FOLDER_OPEN,
    //        links);
  }

  private Node createExtensionSections() {
    newsContent = new VBox(10);
    setNewsItems(List.of());

    servicesContent = new VBox(10);
    setServiceItems(List.of());

    VBox news =
        createSection(
            "News",
            "Updates and announcements from the k.LAB community.",
            Material2AL.ARTICLE,
            newsContent);
    VBox services =
        createSection(
            "Services",
            "Quick access to connected k.LAB services.",
            Material2AL.CLOUD_QUEUE,
            servicesContent);

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

  /**
   * Replaces the news area. This is the integration point for the future news API.
   *
   * @param items news entries in newest-first order
   */
  public void setNewsItems(List<NewsItem> items) {
    if (newsContent == null) {
      return;
    }
    newsContent.getChildren().clear();
    if (items == null || items.isEmpty()) {
      newsContent
          .getChildren()
          .add(
              createEmptyState(
                  "News will appear here",
                  "This area is ready for updates from the future news service."));
      return;
    }
    items.forEach(item -> newsContent.getChildren().add(createNewsItem(item)));
  }

  /**
   * Replaces the services area. Future connected services can be exposed here without changing the
   * page layout.
   *
   * @param items services available to the current user
   */
  public void setServiceItems(List<ServiceItem> items) {
    if (servicesContent == null) {
      return;
    }
    servicesContent.getChildren().clear();
    if (items == null || items.isEmpty()) {
      servicesContent
          .getChildren()
          .add(
              createEmptyState(
                  "More services are coming",
                  "Connected tools and services will be available from this area."));
      return;
    }
    items.forEach(item -> servicesContent.getChildren().add(createServiceItem(item)));
  }

  private Node createNewsItem(NewsItem item) {
    Label date = new Label(item.date());
    date.getStyleClass().addAll(Styles.TEXT_CAPTION, Styles.TEXT_MUTED);

    Label title = new Label(item.title());
    title.getStyleClass().add(Styles.TEXT_BOLD);

    Label summary = new Label(item.summary());
    summary.setWrapText(true);
    summary.getStyleClass().addAll(Styles.TEXT_SMALL, Styles.TEXT_MUTED);

    VBox text = new VBox(3, date, title, summary);
    if (item.url() != null && !item.url().isBlank()) {
      Button readMore = createLinkButton("Read more", Material2AL.ARROW_FORWARD, item.url());
      readMore.getStyleClass().add(Styles.SMALL);
      text.getChildren().add(readMore);
    }
    return text;
  }

  private Node createServiceItem(ServiceItem item) {
    Label title = new Label(item.name());
    title.getStyleClass().add(Styles.TEXT_BOLD);

    Label description = new Label(item.description());
    description.setWrapText(true);
    description.getStyleClass().addAll(Styles.TEXT_SMALL, Styles.TEXT_MUTED);

    VBox text = new VBox(3, title, description);
    HBox.setHgrow(text, Priority.ALWAYS);

    HBox row = new HBox(10, text);
    row.setAlignment(Pos.CENTER_LEFT);
    if (item.url() != null && !item.url().isBlank()) {
      Button open = createLinkButton("Open", Material2AL.ARROW_FORWARD, item.url());
      open.getStyleClass().add(Styles.SMALL);
      row.getChildren().add(open);
    }
    return row;
  }

  private Node createEmptyState(String title, String description) {
    Label titleLabel = new Label(title);
    titleLabel.getStyleClass().add(Styles.TEXT_BOLD);

    Label descriptionLabel = new Label(description);
    descriptionLabel.setWrapText(true);
    descriptionLabel.getStyleClass().addAll(Styles.TEXT_SMALL, Styles.TEXT_MUTED);

    VBox emptyState = new VBox(4, titleLabel, descriptionLabel);
    emptyState.setPadding(new Insets(10, 0, 4, 0));
    return emptyState;
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

    Region separator = new Region();
    separator.setMinHeight(1);
    separator.setMaxWidth(Double.MAX_VALUE);
    separator.setStyle("-fx-background-color: -color-border-subtle;");

    HBox footerContent = new HBox(22, productInfo, contributorRow);
    footerContent.setAlignment(Pos.BOTTOM_LEFT);
    return new VBox(12, separator, footerContent);
  }

  private void openUrl(String url) {
    KlabIDEApplication.instance().getHostServices().showDocument(url);
  }

  public record NewsItem(String date, String title, String summary, String url) {}

  public record ServiceItem(String name, String description, String url) {}
}
