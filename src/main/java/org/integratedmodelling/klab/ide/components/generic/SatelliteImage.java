package org.integratedmodelling.klab.ide.components.generic;

import java.util.List;
import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.image.ImageView;
import javafx.scene.layout.TilePane;
import javafx.stage.Stage;
import org.integratedmodelling.klab.common.data.ExportFileCache;

public class SatelliteImage extends ImageView {

  static ExportFileCache cache;
  static final double pixelResolutionLinear = 640;
  static String layer = "s2cloudless,osm";

  public SatelliteImage(
      double minX, double minY, double maxX, double maxY, int viewportWidth, int viewportHeight) {
    super(url(minX, minY, maxX, maxY));
  }

  static {
    //      cache = new
    // ExportFileCache(KlabIDEController.instance().engine().getSettings().get())...
  }

  private static String generateKey(double minX, double minY, double maxX, double maxY) {
    StringBuffer key = new StringBuffer();
    key.append("img-");
    List.of(minX, minY, maxX, maxY).stream()
        .map(x -> Math.round(x * 100) / 100)
        .forEach(c -> key.append(c).append("-"));
    return key.toString().substring(0, key.length() - 1);
  }

  private static String url(double minX, double minY, double maxX, double maxY) {

    var key = generateKey(minX, minY, maxX, maxY);
    // Round coordinates to 2 decimal places
    double rMinX = Math.round(minX * 100.0) / 100.0;
    double rMinY = Math.round(minY * 100.0) / 100.0;
    double rMaxX = Math.round(maxX * 100.0) / 100.0;
    double rMaxY = Math.round(maxY * 100.0) / 100.0;

    // Simple file-based cache (temp directory), using key + .png TODO switch to ExportFileCache
    java.nio.file.Path cacheDir =
        java.nio.file.Paths.get(
            System.getProperty("java.io.tmpdir"), "klab-ide", "satellite-cache");
    java.nio.file.Path cachedFile = cacheDir.resolve(key + ".png");

    try {
      if (java.nio.file.Files.exists(cachedFile)) {
        return cachedFile.toUri().toString();
      }
    } catch (Exception ignore) {
      // fall through to remote fetch
    }

    // Compute requested image size guided by pixelResolutionLinear while preserving aspect ratio
    double dx = Math.max(1e-9, rMaxX - rMinX);
    double dy = Math.max(1e-9, rMaxY - rMinY);
    double ratio = dx / dy;
    int width;
    int height;
    if (ratio >= 1.0) {
      width = (int) Math.max(1, Math.round(pixelResolutionLinear));
      height = (int) Math.max(1, Math.round(pixelResolutionLinear / ratio));
    } else {
      height = (int) Math.max(1, Math.round(pixelResolutionLinear));
      width = (int) Math.max(1, Math.round(pixelResolutionLinear * ratio));
    }

    // Build WMS GetMap URL (EOX s2cloudless layer), WMS 1.1.1, EPSG:4326
    String wmsBase = "https://tiles.maps.eox.at/wms";
    String wmsUrl =
        wmsBase
            + "?service=WMS"
            + "&request=GetMap"
            + "&version=1.1.1"
            + "&format=image/png"
            + "&transparent=true"
            + "&srs=EPSG:4326"
            + "&styles="
            + "&layers="
            + layer
            + "&bbox="
            + rMinX
            + ","
            + rMinY
            + ","
            + rMaxX
            + ","
            + rMaxY
            + "&width="
            + width
            + "&height="
            + height;

    // Start background caching: download and save PNG without blocking
    new Thread(
            () -> {
              try {
                java.nio.file.Files.createDirectories(cacheDir);
                java.net.URL urlObj = new java.net.URL(wmsUrl);
                try (java.io.InputStream in = urlObj.openStream()) {
                  java.nio.file.Files.copy(
                      in, cachedFile, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                }
              } catch (Exception e) {
                // Ignore errors in background caching
              }
            },
            "satellite-cache-" + key)
        .start();

    // Return the remote URL immediately for ImageView to load
    return wmsUrl;
  }

  /**
   * Quick manual test harness that opens a JavaFX window and shows a few SatelliteImage instances
   * for different world regions.
   */
  public static void main(String[] args) {
    Application.launch(TestApp.class, args);
  }

  public static class TestApp extends Application {
    @Override
    public void start(Stage stage) {
      TilePane root = new TilePane();
      root.setHgap(10);
      root.setVgap(10);
      root.setPadding(new Insets(12));

      // A few sample bounding boxes (minX, minY, maxX, maxY)
      SatelliteImage europe = new SatelliteImage(-10, 35, 30, 60, 800, 600);
      SatelliteImage northAmerica = new SatelliteImage(-130, 24, -65, 50, 800, 600);
      SatelliteImage africa = new SatelliteImage(-20, -35, 55, 37, 800, 600);
      SatelliteImage australia = new SatelliteImage(112, -44, 154, -10, 800, 600);

      // Make them fit nicely in a grid while preserving aspect ratio
      for (SatelliteImage img : new SatelliteImage[] {europe, northAmerica, africa, australia}) {
        img.setPreserveRatio(true);
        img.setFitWidth(320);
        root.getChildren().add(img);
      }

      Scene scene = new Scene(root, 700, 700);
      stage.setTitle("SatelliteImage test viewer");
      stage.setScene(scene);
      stage.show();
    }
  }
}
