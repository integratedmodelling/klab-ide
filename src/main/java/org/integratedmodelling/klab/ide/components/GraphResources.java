package org.integratedmodelling.klab.ide.components;

import java.nio.file.Path;

/** Resolves graph assets shipped beside the application JAR by Conveyor. */
final class GraphResources {

  private GraphResources() {}

  static Path applicationFile(String filename) {
    String appDir = System.getProperty("app.dir");
    return (appDir == null || appDir.isBlank() ? Path.of(filename) : Path.of(appDir, filename))
        .toAbsolutePath();
  }

  static String iconFill(String filename) {
    return "-fx-fill: url(\"" + applicationFile(filename).toUri().toASCIIString() + "\");";
  }
}
