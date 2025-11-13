package org.integratedmodelling.klab.ide.components;

import atlantafx.base.theme.Styles;

import javafx.application.HostServices;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Separator;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;
import netscape.javascript.JSObject;
import org.integratedmodelling.common.logging.Logging;
import org.integratedmodelling.klab.api.data.RuntimeAsset;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.services.runtime.Notification;
import org.integratedmodelling.klab.ide.KlabIDEController;
import org.integratedmodelling.klab.ide.Theme;
import org.integratedmodelling.klab.ide.utils.AppContext;

import java.awt.Desktop;
import java.net.URI;

/**
 * AssetViewer is a reusable JavaFX component that visualizes a {@link RuntimeAsset}. It shows a
 * thin header with the asset icon/title, a main WebView that loads a URL provided via constructor,
 * and a details panel below that adapts to the concrete asset type. The first specialized
 * implementation is for {@link Observation} assets.
 */
public class AssetViewer extends BorderPane {

  private final RuntimeAsset asset;
  private final String url;

  private final WebView webView = new WebView();
  private final VBox detailsBox = new VBox(8);

  public AssetViewer(RuntimeAsset asset, String url) {
    this.asset = asset;
    this.url = url;

    setTop(createHeader(asset));
    setCenter(createWebView(url));
    setBottom(createDetailsPanel(asset));
  }

  private Node createHeader(RuntimeAsset asset) {
    HBox header = new HBox(8);
    header.setAlignment(Pos.CENTER_LEFT);
    header.setPadding(new Insets(6, 10, 6, 10));

    Node icon = Theme.getGraphics(asset);

    Label title = new Label(Theme.getLabel(asset));
    title.getStyleClass().add("asset-viewer-title");

    Label type = new Label("(" + asset.classify().name() + ")");
    type.getStyleClass().add("asset-viewer-type");

    Region spacer = new Region();
    HBox.setHgrow(spacer, Priority.ALWAYS);

    Button worldIcon = new Button();
    worldIcon.setGraphic(new IconLabel(Theme.OPEN_IN_BROWSER, 18, Color.GREEN));
    worldIcon.setOnAction(e -> openInBrowser(url));
    worldIcon.getStyleClass().addAll(Styles.FLAT, Styles.BUTTON_CIRCLE);

    header.getChildren().addAll(icon, title, type, spacer, worldIcon);

    // a very thin style: rely on external stylesheet if available
    header.getStyleClass().add("asset-viewer-header");
    return header;
  }

  private Node createWebView(String url) {
    WebEngine engine = webView.getEngine();
    engine.setJavaScriptEnabled(true);
    engine
        .getLoadWorker()
        .stateProperty()
        .addListener(
            (ob, o, n) -> {
              JSObject window = (JSObject) engine.executeScript("window");
              window.setMember("javaOut", System.out);
              window.setMember("javaErr", System.err);
              webView
                  .getEngine()
                  .executeScript(
                      "console.log = function(message) { javaOut.println(message); };\n"
                          + "console.error = function(message) {javaErr.println(message); };");
            });
    if (url != null && !url.isBlank()) {
      Logging.INSTANCE.debug("Opening page: "+url);
      engine.load(url);
    }
    webView.setContextMenuEnabled(true);
    return webView;
  }

  private Node createDetailsPanel(RuntimeAsset asset) {
    detailsBox.setPadding(new Insets(8));

    // A small separator between the web content and details
    VBox container = new VBox(6);
    Separator sep = new Separator();

    Node detailsContent = renderDetails(asset);

    ScrollPane scroller = new ScrollPane(detailsContent);
    scroller.setFitToWidth(true);
    scroller.setFitToHeight(false);
    scroller.setPrefViewportHeight(160);

    container.getChildren().addAll(sep, scroller);
    return container;
  }

  private Node renderDetails(RuntimeAsset asset) {
    GridPane grid = new GridPane();
    grid.setHgap(10);
    grid.setVgap(6);

    int r = 0;

    // Common fields available for all RuntimeAsset
    grid.add(label("ID:"), 0, r);
    grid.add(value(String.valueOf(asset.getId())), 1, r++);

    grid.add(label("Parent ID:"), 0, r);
    grid.add(value(String.valueOf(asset.getParentId())), 1, r++);

    grid.add(label("Type:"), 0, r);
    grid.add(value(asset.classify().name()), 1, r++);

    // Specialized for Observation when available
    if (asset instanceof Observation obs) {
      grid.add(section("Observation"), 0, r++, 2, 1);

      Object observable = obs.getObservable();
      grid.add(label("Observable:"), 0, r);
      grid.add(value(observable != null ? observable.toString() : "-"), 1, r++);
    }

    return grid;
  }

  private Label label(String text) {
    Label l = new Label(text);
    l.getStyleClass().add("asset-viewer-label");
    return l;
  }

  private Label value(String text) {
    Label l = new Label(text);
    l.getStyleClass().add("asset-viewer-value");
    return l;
  }

  private Label section(String text) {
    Label l = new Label(text);
    l.getStyleClass().add("asset-viewer-section");
    return l;
  }

  public RuntimeAsset getAsset() {
    return asset;
  }

  public String getUrl() {
    return url;
  }

  public WebView getWebView() {
    return webView;
  }

  public void reload() {
    if (url != null && !url.isBlank()) {
      webView.getEngine().load(url);
    }
  }

  private void openInBrowser(String url) {
    try {
      HostServices hs = AppContext.getHostServices();
      if (hs != null) {
        hs.showDocument(url);
      } else {
        // Fallback for Linux/Windows if something went wrong
        if (System.getProperty("os.name").toLowerCase().contains("linux")) {
          new ProcessBuilder("xdg-open", url).start();
        } else {
          Desktop.getDesktop().browse(new URI(url));
        }
      }
    } catch (Exception ex) {
      KlabIDEController.instance().handleNotification(Notification.error(ex));
    }
  }
}
