package org.integratedmodelling.klab.ide.components;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/** Precompiled shared Quasar nodes; no CDN or browser module loader is required. */
final class ApplicationRenderer {
  static String load() throws IOException {
    return assemble(resource("/application-ui.html"), resource("/META-INF/klab/ui-embedded/renderer.css"),
        resource("/META-INF/klab/ui-embedded/renderer.js"));
  }
  static String assemble(String html, String css, String script) {
    return html.replace("/*APPLICATION_CSS*/", css.replace("</style", "<\\/style"))
        .replace("/*APPLICATION_SCRIPT*/", script.replace("</script", "<\\/script"));
  }
  private static String resource(String name) throws IOException {
    // A named IDE module cannot find an asset-only runtime dependency through Class.getResource.
    // IntelliJ rebuilds can remove Maven-unpacked copies while leaving that JAR on the classpath.
    var packaged = ApplicationRenderer.class.getResourceAsStream(name);
    if (packaged == null) {
      var loader = ApplicationRenderer.class.getClassLoader();
      String resource = name.startsWith("/") ? name.substring(1) : name;
      packaged = loader == null ? ClassLoader.getSystemResourceAsStream(resource)
          : loader.getResourceAsStream(resource);
    }
    try (var source = packaged) {
      if (source == null) throw new IOException("Missing packaged Quasar renderer: " + name
          + ". Build/install klab.core.services with -Pwebui, then rebuild and restart the IDE.");
      return new String(source.readAllBytes(), StandardCharsets.UTF_8);
    }
  }
}
