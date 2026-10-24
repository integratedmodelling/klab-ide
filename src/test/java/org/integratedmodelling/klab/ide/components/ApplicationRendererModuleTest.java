package org.integratedmodelling.klab.ide.components;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.concurrent.TimeUnit;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Matches an IDE rebuild: a named IDE module, no unpacked assets, a runtime-only asset JAR. */
class ApplicationRendererModuleTest {
  @TempDir Path directory;

  @Test void namedModuleLoadsRendererFromRuntimeClasspathWithoutUnpackedCopies() throws Exception {
    var classes = directory.resolve("classes"); Files.createDirectories(classes);
    var module = directory.resolve("module-info.java");
    Files.writeString(module, "module renderer.probe {}", StandardCharsets.UTF_8);
    var probe = directory.resolve("RendererProbe.java");
    Files.writeString(probe, """
        package org.integratedmodelling.klab.ide.components;
        public class RendererProbe {
          public static void main(String[] args) throws Exception {
            if (!ApplicationRenderer.class.getModule().isNamed()) throw new AssertionError("Not a modular launch");
            if (ApplicationRenderer.class.getResourceAsStream("/META-INF/klab/ui-embedded/renderer.js") != null)
              throw new AssertionError("Test accidentally contains unpacked assets");
            String html = ApplicationRenderer.load();
            if (!html.contains("rendererClasspathProbe") || !html.contains("color:blue"))
              throw new AssertionError("Runtime renderer assets were not assembled");
            System.out.println("Loaded renderer from asset-only runtime classpath");
          }
        }
        """, StandardCharsets.UTF_8);
    var packagePath = classes.resolve("org/integratedmodelling/klab/ide/components");
    Files.createDirectories(packagePath);
    try (var implementation = ApplicationRenderer.class.getResourceAsStream("ApplicationRenderer.class")) {
      assertNotNull(implementation);
      Files.copy(implementation, packagePath.resolve("ApplicationRenderer.class"));
    }
    Files.writeString(classes.resolve("application-ui.html"),
        "<html><head><style>/*APPLICATION_CSS*/</style></head><script>/*APPLICATION_SCRIPT*/</script></html>");
    // Patch the compiled IDE renderer into this minimal module; no IDE/JavaFX dependencies are needed.
    run("javac", "--patch-module", "renderer.probe=" + classes, "-d", classes.toString(), module.toString(), probe.toString());
    var assets = directory.resolve("application-ui.jar");
    try (var jar = new JarOutputStream(Files.newOutputStream(assets))) {
      entry(jar, "META-INF/klab/ui-embedded/renderer.css", "body{color:blue}");
      entry(jar, "META-INF/klab/ui-embedded/renderer.js", "window.rendererClasspathProbe=true;");
    }
    String output = run("java", "--module-path", classes.toString(), "--class-path", assets.toString(),
        "--module", "renderer.probe/org.integratedmodelling.klab.ide.components.RendererProbe");
    assertTrue(output.contains("Loaded renderer from asset-only runtime classpath"), output);
  }

  private static void entry(JarOutputStream jar, String name, String contents) throws Exception {
    jar.putNextEntry(new JarEntry(name)); jar.write(contents.getBytes(StandardCharsets.UTF_8)); jar.closeEntry();
  }

  private static String run(String tool, String... arguments) throws Exception {
    String executable = Path.of(System.getProperty("java.home"), "bin",
        tool + (System.getProperty("os.name").startsWith("Windows") ? ".exe" : "")).toString();
    var command = new java.util.ArrayList<String>(); command.add(executable); command.addAll(java.util.List.of(arguments));
    var process = new ProcessBuilder(command).redirectErrorStream(true).start();
    try {
      assertTrue(process.waitFor(20, TimeUnit.SECONDS), "Modular renderer probe timed out");
      String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
      assertEquals(0, process.exitValue(), output); return output;
    } finally { if (process.isAlive()) process.destroyForcibly(); }
  }
}
