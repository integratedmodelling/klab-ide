package org.integratedmodelling.klab.ide.components.generic;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.concurrent.*;
import java.util.logging.*;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.TreeView;
import javafx.scene.layout.VBox;
import org.integratedmodelling.klab.ide.Theme;
import org.junit.jupiter.api.*;

class CssRegressionTest {
  @BeforeAll static void startFx() throws Exception {
    var ready = new CompletableFuture<Void>();
    try { Platform.startup(() -> ready.complete(null)); }
    catch (IllegalStateException running) { Platform.runLater(() -> ready.complete(null)); }
    ready.get(10, TimeUnit.SECONDS);
    Platform.setImplicitExit(false);
  }

  @Test void customStylesAndSearchIconParseWithoutWarnings() throws Exception {
    var result = new CompletableFuture<Void>();
    Platform.runLater(() -> {
      var messages = new ArrayList<String>();
      var logger = Logger.getLogger("javafx.css");
      var handler = new Handler() {
        public void publish(LogRecord record) { messages.add(record.getMessage()); }
        public void flush() {}
        public void close() {}
      };
      var previous = Application.getUserAgentStylesheet();
      LogViewer logs = null;
      logger.addHandler(handler);
      try {
        Application.setUserAgentStylesheet(Theme.LIGHT_DEFAULT.getStylesheet());
        var root = new VBox(new TreeSearchField<>(new TreeView<String>(), (text, item) -> true));
        var scene = new Scene(root, 800, 600);
        scene.getStylesheets().add(Theme.class.getResource("custom.css").toExternalForm());
        logs = new LogViewer(java.nio.file.Path.of("target", "css-regression-missing.log"));
        logs.shutdown();
        for (String level : new String[]{"TRACE", "DEBUG", "INFO", "WARN", "ERROR", "FATAL"}) {
          logs.getEntries().add(new LogViewer.LogEntry("", level, "", "", "test", "Message\nDetails"));
        }
        root.getChildren().add(logs);
        root.applyCss(); root.layout();
        root.getChildren().remove(logs);
        root.applyCss(); root.layout();
        assertTrue(messages.isEmpty(), String.join("\n", messages));
        result.complete(null);
      } catch (Throwable failure) { result.completeExceptionally(failure); }
      finally {
        if (logs != null) logs.shutdown();
        logger.removeHandler(handler);
        Application.setUserAgentStylesheet(previous);
      }
    });
    result.get(15, TimeUnit.SECONDS);
  }
}
