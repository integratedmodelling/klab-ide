package org.integratedmodelling.klab.ide.components;

import static org.junit.jupiter.api.Assertions.*;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import javafx.application.Platform;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.DialogPane;
import javafx.stage.Window;
import org.integratedmodelling.klab.api.lang.kim.impl.KimNamespaceImpl;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class DocumentDeletionConfirmationTest {
  @BeforeAll
  static void startFx() throws Exception {
    var ready = new CompletableFuture<Void>();
    try {
      Platform.startup(() -> { Platform.setImplicitExit(false); ready.complete(null); });
    } catch (IllegalStateException alreadyStarted) {
      Platform.runLater(() -> ready.complete(null));
    }
    ready.get(15, TimeUnit.SECONDS);
  }

  @Test
  void cancelAndWindowCloseDoNotDeleteAndExplicitConfirmationDeletesOnce() throws Exception {
    var done = new CompletableFuture<Void>();
    Platform.runLater(() -> {
      try {
        var document = new KimNamespaceImpl();
        document.setUrn("demo.models");
        document.setProjectName("project");
        var deletions = new AtomicInteger();
        for (var choice : new String[] {"cancel", "close", "delete"}) {
          var checked = new CompletableFuture<Void>();
          Platform.runLater(() -> {
            Window window = null;
            try {
              window = Window.getWindows().stream()
                  .filter(w -> w.getScene().getRoot() instanceof DialogPane).findFirst().orElseThrow();
              var pane = (DialogPane) window.getScene().getRoot();
              var deleteType = pane.getButtonTypes().stream()
                  .filter(type -> type.getButtonData() == ButtonBar.ButtonData.YES).findFirst().orElseThrow();
              var cancelType = pane.getButtonTypes().stream()
                  .filter(type -> type.getButtonData() == ButtonBar.ButtonData.CANCEL_CLOSE).findFirst().orElseThrow();
              assertEquals(0, deletions.get(), "API action must wait for explicit confirmation");
              assertTrue(pane.getHeaderText().contains(document.getUrn()));
              assertTrue(pane.getContentText().contains(document.getProjectName()));
              assertFalse(((Button) pane.lookupButton(deleteType)).isDefaultButton());
              assertTrue(((Button) pane.lookupButton(cancelType)).isDefaultButton());
              if (choice.equals("close")) window.hide();
              else ((Button) pane.lookupButton(choice.equals("delete") ? deleteType : cancelType)).fire();
              checked.complete(null);
            } catch (Throwable error) {
              if (window != null) window.hide();
              checked.completeExceptionally(error);
            }
          });
          WorkspaceEditor.confirmDocumentDeletion(document, null, deletions::incrementAndGet);
          checked.join();
          assertEquals(choice.equals("delete") ? 1 : 0, deletions.get());
        }
        done.complete(null);
      } catch (Throwable error) {
        done.completeExceptionally(error);
      }
    });
    done.get(20, TimeUnit.SECONDS);
  }
}
