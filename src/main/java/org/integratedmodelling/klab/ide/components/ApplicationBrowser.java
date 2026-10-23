package org.integratedmodelling.klab.ide.components;

import java.awt.Desktop;
import java.io.IOException;
import java.net.URI;
import java.nio.file.*;
import java.util.*;

/** Browser commands are argument lists: no URL or executable is passed through a shell. */
record ApplicationBrowser(String name, List<String> command) {
  ApplicationBrowser {
    command = List.copyOf(command);
  }

  @Override
  public String toString() {
    return name;
  }

  void open(URI url) throws IOException {
    if (command.isEmpty()) {
      if (!Desktop.isDesktopSupported() || !Desktop.getDesktop().isSupported(Desktop.Action.BROWSE))
        throw new IOException("No default browser is available; choose a browser executable");
      Desktop.getDesktop().browse(url);
    } else {
      var arguments = new ArrayList<>(command);
      arguments.add(url.toASCIIString());
      new ProcessBuilder(arguments).start();
    }
  }

  static List<ApplicationBrowser> installed() {
    var browsers = new ArrayList<ApplicationBrowser>();
    browsers.add(new ApplicationBrowser("Default browser", List.of()));
    String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
    if (os.contains("win")) {
      var candidates =
          Map.of(
              "Google Chrome", "Google/Chrome/Application/chrome.exe",
              "Microsoft Edge", "Microsoft/Edge/Application/msedge.exe",
              "Mozilla Firefox", "Mozilla Firefox/firefox.exe",
              "Brave", "BraveSoftware/Brave-Browser/Application/brave.exe",
              "Vivaldi", "Vivaldi/Application/vivaldi.exe",
              "Opera", "Programs/Opera/opera.exe");
      candidates.entrySet().stream()
          .sorted(Map.Entry.comparingByKey())
          .forEach(
              entry -> {
                for (String variable :
                    List.of("ProgramFiles", "ProgramFiles(x86)", "LOCALAPPDATA")) {
                  String root = System.getenv(variable);
                  if (root == null) continue;
                  Path executable = Path.of(root).resolve(entry.getValue());
                  if (Files.isRegularFile(executable)) {
                    browsers.add(
                        new ApplicationBrowser(entry.getKey(), List.of(executable.toString())));
                    break;
                  }
                }
              });
    } else if (os.contains("mac")) {
      for (String name :
          List.of(
              "Safari",
              "Google Chrome",
              "Firefox",
              "Microsoft Edge",
              "Brave Browser",
              "Opera",
              "Vivaldi")) {
        for (Path root :
            List.of(
                Path.of("/Applications"),
                Path.of(System.getProperty("user.home"), "Applications"))) {
          Path bundle = root.resolve(name + ".app");
          if (Files.isDirectory(bundle)) {
            browsers.add(
                new ApplicationBrowser(name, List.of("/usr/bin/open", "-a", bundle.toString())));
            break;
          }
        }
      }
    } else {
      for (String name :
          List.of(
              "google-chrome",
              "chromium",
              "chromium-browser",
              "firefox",
              "microsoft-edge",
              "brave-browser",
              "vivaldi",
              "opera")) {
        for (String directory :
            System.getenv().getOrDefault("PATH", "").split(java.io.File.pathSeparator)) {
          Path executable = Path.of(directory).resolve(name);
          if (Files.isRegularFile(executable) && Files.isExecutable(executable)) {
            browsers.add(new ApplicationBrowser(name, List.of(executable.toString())));
            break;
          }
        }
      }
    }
    return browsers;
  }
}
