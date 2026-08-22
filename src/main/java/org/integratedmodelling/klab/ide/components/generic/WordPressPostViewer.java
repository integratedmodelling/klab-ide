package org.integratedmodelling.klab.ide.components.generic;

import atlantafx.base.theme.Styles;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import java.awt.Desktop;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

/** A compact carousel backed by the WordPress REST API. */
public class WordPressPostViewer extends CarouselBox {
  private static final double DEFAULT_CARD_HEIGHT = 200;
  private static final int DEFAULT_PAGE_SIZE = 10;

  private String siteUrl;
  private double cardHeight = DEFAULT_CARD_HEIGHT;
  private boolean showingPages;
  private List<String> tags = List.of();

  public WordPressPostViewer(Orientation orientation) { super(orientation); }

  public WordPressPostViewer(Orientation orientation, String siteUrl) {
    this(orientation);
    setSiteUrl(siteUrl);
  }

  public WordPressPostViewer(String siteUrl) {
    this(Orientation.VERTICAL);
    setSiteUrl(siteUrl);
  }

  public String getSiteUrl() { return siteUrl; }
  public void setSiteUrl(String siteUrl) { this.siteUrl = siteUrl == null ? null : siteUrl.trim(); }
  public double getCardHeight() { return cardHeight; }

  /** Sets the fixed maximum/preferred height used by each card. */
  public void setCardHeight(double cardHeight) {
    if (!Double.isFinite(cardHeight) || cardHeight <= 0) {
      throw new IllegalArgumentException("Card height must be positive");
    }
    this.cardHeight = cardHeight;
  }

  public boolean isShowingPages() { return showingPages; }
  public void setShowingPages(boolean showingPages) { this.showingPages = showingPages; }

  /** Returns the configured WordPress tag names. An empty list means no tag filter. */
  public List<String> getTags() { return tags; }

  /**
   * Sets the WordPress tag names used to filter posts. Names are resolved to tag IDs by the API,
   * and matching posts are requested with the REST API's {@code tags} filter.
   */
  public void setTags(Collection<String> tags) {
    if (tags == null) {
      this.tags = List.of();
      return;
    }
    Set<String> normalized = new LinkedHashSet<>();
    tags.stream().filter(tag -> tag != null && !tag.isBlank())
        .map(String::trim).forEach(normalized::add);
    this.tags = List.copyOf(normalized);
  }

  public void setTags(String... tags) {
    setTags(tags == null ? null : List.of(tags));
  }

  /** Fetches the configured post or page collection off the FX application thread. */
  public CompletableFuture<Void> load() {
    if (siteUrl == null || siteUrl.isBlank()) {
      return CompletableFuture.failedFuture(
          new IllegalStateException("WordPress site URL is not configured"));
    }
    boolean pages = showingPages;
    double height = cardHeight;
    List<String> selectedTags = tags;
    return CompletableFuture.supplyAsync(() -> fetch(pages, selectedTags)).thenAccept(items ->
      Platform.runLater(() -> setItems(items.stream()
          .map(item -> new WordPressPostComponent(item, height)).toList())));
  }

  private List<?> fetch(boolean pages, List<String> selectedTags) {
    try {
      List<Integer> tagIds = pages || selectedTags.isEmpty() ? List.of() : resolveTagIds(selectedTags);
      if (!pages && !selectedTags.isEmpty() && tagIds.isEmpty()) return List.of();
      String endpoint = apiBase(siteUrl) + "/wp/v2/" + (pages ? "pages" : "posts")
          + "?per_page=" + DEFAULT_PAGE_SIZE + "&page=1"
          + (tagIds.isEmpty() ? "" : "&tags=" + tagIds.stream().map(String::valueOf).reduce((a, b) -> a + "," + b).orElse(""));
      String responseBody = get(endpoint);
      List<WordPressItem> result = new ArrayList<>();
      for (JsonElement element : JsonParser.parseString(responseBody).getAsJsonArray()) {
        var json = element.getAsJsonObject();
        result.add(new WordPressItem(text(json, "title"), text(json, "excerpt"),
            text(json, "content"), json.has("link") ? URI.create(json.get("link").getAsString()) : null,
            pages ? "Page" : "Post"));
      }
      return result;
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("WordPress request was interrupted", e);
    } catch (Exception e) {
      throw new IllegalStateException("Unable to load WordPress content from " + siteUrl, e);
    }
  }

  private List<Integer> resolveTagIds(List<String> selectedTags) throws Exception {
    Set<Integer> ids = new LinkedHashSet<>();
    for (String tag : selectedTags) {
      String endpoint = apiBase(siteUrl) + "/wp/v2/tags?per_page=100&search=" + encode(tag);
      for (JsonElement element : JsonParser.parseString(get(endpoint)).getAsJsonArray()) {
        var json = element.getAsJsonObject();
        String name = json.has("name") ? json.get("name").getAsString() : "";
        String slug = json.has("slug") ? json.get("slug").getAsString() : "";
        if (tag.equalsIgnoreCase(name) || tag.equalsIgnoreCase(slug)) {
          ids.add(json.get("id").getAsInt());
        }
      }
    }
    return List.copyOf(ids);
  }

  private static String get(String endpoint) throws Exception {
    HttpRequest request = HttpRequest.newBuilder(URI.create(endpoint))
        .timeout(Duration.ofSeconds(20)).header("Accept", "application/json").GET().build();
    HttpResponse<String> response = HttpClient.newHttpClient().send(request,
        HttpResponse.BodyHandlers.ofString());
    if (response.statusCode() < 200 || response.statusCode() >= 300) {
      throw new IllegalStateException("WordPress returned HTTP " + response.statusCode());
    }
    return response.body();
  }

  private static String encode(String value) {
    return URLEncoder.encode(value, StandardCharsets.UTF_8);
  }

  private static String text(com.google.gson.JsonObject object, String property) {
    if (!object.has(property) || !object.get(property).isJsonObject()) return "";
    var value = object.getAsJsonObject(property).get("rendered");
    return value == null ? "" : value.getAsString();
  }

  private record WordPressItem(String title, String excerpt, String content, URI link, String type) {
    public Object getTitle() { return title; }
    public Object getExcerpt() { return excerpt; }
    public Object getContent() { return content; }
    public URI getLink() { return link; }
    public String getType() { return type; }
  }

  private static String apiBase(String url) {
    String normalized = url.replaceAll("/+$", "");
    return normalized.endsWith("/wp-json") ? normalized : normalized + "/wp-json";
  }

  /** A 280px-wide, height-bounded representation of a WordPress post or page. */
  public static class WordPressPostComponent extends Region {
    private static final double CARD_WIDTH = 480;
    private final VBox body = new VBox(6);

    public WordPressPostComponent(Object item) { this(item, DEFAULT_CARD_HEIGHT); }

    private WordPressPostComponent(Object item, double height) {
      this(property(item, "getTitle"), property(item, "getExcerpt"), property(item, "getContent"),
          uri(property(item, "getLink")), value(property(item, "getType"), "Post"), height);
    }

    private WordPressPostComponent(Object title, Object excerpt, Object content, URI link,
        String type, double height) {
      // Keep a compact preferred width for horizontal carousels, but allow the
      // vertical carousel wrapper to stretch the card across its full width.
      setPrefWidth(CARD_WIDTH);
      setMinWidth(0);
      setMaxWidth(Double.MAX_VALUE);
      setMinHeight(height); setMaxHeight(height);
      getStyleClass().addAll("wordpress-card", Styles.BG_DEFAULT);
      body.getStyleClass().add("wordpress-card-content");
      body.setPadding(new Insets(8));
      Label kind = new Label(type.toUpperCase());
      kind.getStyleClass().add("wordpress-card-type");
      Label heading = new Label(value(title, "Untitled"));
      heading.setWrapText(true); heading.getStyleClass().add("wordpress-card-title");
      Label summary = new Label(abbreviate(value(excerpt, value(content, "No content available.")), height));
      summary.setWrapText(true); summary.getStyleClass().add("wordpress-card-summary");
      VBox.setVgrow(summary, Priority.ALWAYS);
      body.getChildren().addAll(kind, heading, summary);
      if (link != null) {
        Hyperlink more = new Hyperlink("Read more");
        more.getStyleClass().add("wordpress-card-link");
        more.setOnAction(e -> open(link));
        body.getChildren().add(more);
        setOnMouseClicked(e -> {
          if (e.getButton() == MouseButton.PRIMARY && e.getClickCount() == 2) {
            open(link);
            e.consume();
          }
        });
      }
      getChildren().add(body);
    }

    @Override protected void layoutChildren() { body.resizeRelocate(0, 0, getWidth(), getHeight()); }

    private static String value(Object value, String fallback) {
      if (value == null) return fallback;
      try {
        Object rendered = value.getClass().getMethod("getRendered").invoke(value);
        return clean(rendered == null ? value.toString() : rendered.toString());
      } catch (ReflectiveOperationException e) { return clean(value.toString()); }
    }

    private static Object property(Object item, String method) {
      if (item == null) return null;
      try { return item.getClass().getMethod(method).invoke(item); }
      catch (ReflectiveOperationException e) { return null; }
    }

    private static URI uri(Object value) {
      return value instanceof URI ? (URI) value : value == null ? null : URI.create(value.toString());
    }

    private static String clean(String value) {
      return value.replaceAll("<[^>]*>", "").replaceAll("\\s+", " ").trim();
    }

    private static String abbreviate(String value, double height) {
      int limit = Math.max(80, (int) ((height - 105) * 2.5));
      return value.length() <= limit ? value : value.substring(0, limit - 1).trim() + "…";
    }

    private static void open(URI link) {
      try { if (Desktop.isDesktopSupported()) Desktop.getDesktop().browse(link); }
      catch (Exception ignored) { }
    }
  }
}
