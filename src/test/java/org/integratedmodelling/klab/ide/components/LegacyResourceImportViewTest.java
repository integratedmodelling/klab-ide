package org.integratedmodelling.klab.ide.components;

import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.util.ArrayList;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableView;
import org.integratedmodelling.klab.api.services.resources.ResourceInfo;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class LegacyResourceImportViewTest {
  @BeforeAll
  static void startFx() throws Exception {
    var ready = new CompletableFuture<Void>();
    try {
      Platform.startup(() -> ready.complete(null));
    } catch (IllegalStateException alreadyStarted) {
      Platform.runLater(() -> ready.complete(null));
    }
    ready.get(10, TimeUnit.SECONDS);
  }

  @Test
  @SuppressWarnings("unchecked")
  void resultTableColorsOutcomesAndOpensOnlySuccessfulResource() throws Exception {
    var checked = new CompletableFuture<Void>();
    Platform.runLater(() -> {
      try {
        var opened = new ArrayList<ResourceInfo>();
        var view = new LegacyResourceImportView(opened::add, "local-resources");
        var table = (TableView<LegacyResourceImport.Outcome>) view.getChildren().get(1);
        table.getItems().addAll(
            new LegacyResourceImport.Outcome(new File("a/resource.json"),
                LegacyResourceImport.Status.SUCCESS, "new:urn", ""),
            new LegacyResourceImport.Outcome(new File("b/resource.json"),
                LegacyResourceImport.Status.ERROR, "", "Missing adapter"));
        var scene = new Scene(view, 1400, 500);
        scene.getStylesheets().add(new atlantafx.base.theme.PrimerLight().getUserAgentStylesheet());
        view.applyCss();
        view.layout();
        var cells = table.lookupAll(".table-cell").stream()
            .filter(TableCell.class::isInstance).map(TableCell.class::cast).toList();
        assertTrue(cells.stream().anyMatch(cell -> "Imported".equals(cell.getText())
            && cell.getStyle().contains("-color-success-fg")));
        assertTrue(cells.stream().anyMatch(cell -> "Error".equals(cell.getText())
            && cell.getStyle().contains("-color-danger-fg")));
        var buttons = cells.stream().map(TableCell::getGraphic).filter(Button.class::isInstance)
            .map(Button.class::cast).toList();
        assertEquals(1, buttons.size());
        buttons.getFirst().fire();
        assertEquals(1, opened.size());
        assertEquals("new:urn", opened.getFirst().getUrn());
        assertEquals("local-resources", opened.getFirst().getServiceId());
        checked.complete(null);
      } catch (Throwable failure) {
        checked.completeExceptionally(failure);
      }
    });
    checked.get(10, TimeUnit.SECONDS);
  }
}
