package org.integratedmodelling.klab.ide.components;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GraphResourcesTest {

  @TempDir Path temp;

  @Test
  void installedAssetsResolveOutsideWorkingDirectoryWithEscapedIconUrls() throws Exception {
    var previous = System.getProperty("app.dir");
    var install = Files.createDirectory(temp.resolve("Modeler app #1"));
    try {
      System.setProperty("app.dir", install.toString());
      for (String name : new String[] {
        "smartgraph.css", "smartgraph-dark.css", "smartgraph.properties",
        "icons8-home-24.png", "icons8-update-30.png", "icons8-box-30.png"
      }) {
        Files.writeString(install.resolve(name), "installed asset");
        assertEquals("installed asset", Files.readString(GraphResources.applicationFile(name)));
        if (name.endsWith(".png")) {
          String fill = GraphResources.iconFill(name);
          String url = fill.substring(fill.indexOf('"') + 1, fill.lastIndexOf('"'));
          assertEquals(install.resolve(name), Path.of(URI.create(url)));
          assertTrue(url.contains("%20"));
          assertTrue(url.contains("%23"));
        }
      }
    } finally {
      if (previous == null) System.clearProperty("app.dir");
      else System.setProperty("app.dir", previous);
    }
  }
}
