package org.integratedmodelling.klab.ide.components;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.charset.StandardCharsets;
import java.util.EnumSet;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.TextArea;
import javafx.scene.control.TreeView;
import javafx.scene.control.TreeCell;
import javafx.scene.control.DialogPane;
import javafx.scene.control.ButtonType;
import org.integratedmodelling.klab.ide.components.generic.IconButton;
import org.integratedmodelling.klab.ide.components.generic.IconLabel;
import org.integratedmodelling.klab.api.authentication.CRUDOperation;
import org.integratedmodelling.klab.api.knowledge.organization.ProjectMaterial;
import org.integratedmodelling.klab.api.services.ResourcesService;
import org.integratedmodelling.klab.api.services.resources.ResourceInfo;
import org.integratedmodelling.klab.api.services.resources.ResourceSet;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

@org.junit.jupiter.api.parallel.Execution(org.junit.jupiter.api.parallel.ExecutionMode.SAME_THREAD)
class ProjectMaterialBrowserFxTest {
  @BeforeAll static void startFx() throws Exception {
    var ready = new CompletableFuture<Void>();
    try { Platform.startup(() -> { Platform.setImplicitExit(false); ready.complete(null); }); }
    catch (IllegalStateException started) { Platform.runLater(() -> ready.complete(null)); }
    ready.get(15, TimeUnit.SECONDS);
  }

  @Test void browsesAndSavesTextThenClearsDownloadedMaterialWhenWriteAccessIsRevoked() throws Exception {
    var writer = new AtomicBoolean(true);
    var rejectSave = new AtomicBoolean(true);
    var closeRequests = new java.util.concurrent.atomic.AtomicInteger();
    var contents = new AtomicReference<>("body {}\n");
    var service = (ResourcesService) java.lang.reflect.Proxy.newProxyInstance(getClass().getClassLoader(),
        new Class<?>[] {ResourcesService.class}, (proxy, method, args) -> {
          switch (method.getName()) {
            case "info": {
              var info = new ResourceInfo(); info.setPermissions(writer.get()
                  ? EnumSet.of(CRUDOperation.READ, CRUDOperation.UPDATE, CRUDOperation.CREATE, CRUDOperation.DELETE)
                  : EnumSet.of(CRUDOperation.READ));
              return info;
            }
            case "retrieve": {
              var value = ProjectMaterial.coordinates((String) args[0]);
              if (value.getPath().isEmpty()) value.getEntries().add(ProjectMaterial.coordinates("project/assets/"));
              else if (value.isDirectory()) value.getEntries().add(new ProjectMaterial("project", "assets/notes.txt", null));
              else value.setContent(contents.get().getBytes(StandardCharsets.UTF_8));
              return value;
            }
            case "submit": {
              if (rejectSave.get()) throw new IllegalStateException("Read-only project storage");
              assertEquals(ResourcesService.SubmissionMode.UPDATE, args[1]);
              var payload = (ProjectMaterial) args[0];
              assertEquals("assets/notes.txt", payload.getPath());
              contents.set(new String(payload.getContent(), StandardCharsets.UTF_8));
              return List.of(new ResourceSet());
            }
            default: return null;
          }
        });
    var browser = new AtomicReference<ProjectMaterialBrowser>();
    var tree = new AtomicReference<TreeView<ProjectMaterial>>();
    fx(() -> {
      var view = new ProjectMaterialBrowser(service, null, "project", result -> {}, closeRequests::incrementAndGet);
      view.getStylesheets().add(new atlantafx.base.theme.PrimerLight().getUserAgentStylesheet());
      browser.set(view); new Scene(view, 1000, 650); view.applyCss(); view.layout();
      @SuppressWarnings("unchecked") var folders = (TreeView<ProjectMaterial>) view.lookup(".tree-view");
      tree.set(folders);
    });
    try {
      await(() -> tree.get().getRoot() != null && tree.get().getRoot().getChildren().size() == 1);
      fx(() -> {
        browser.get().applyCss(); browser.get().layout();
        var cells = browser.get().lookupAll(".tree-cell").stream().filter(node -> node instanceof TreeCell<?> cell && !cell.isEmpty())
            .map(node -> (TreeCell<?>) node).toList();
        assertFalse(cells.isEmpty());
        for (var cell : cells) {
          assertFalse(cell.getText().startsWith("▸"));
          assertInstanceOf(IconLabel.class, cell.getGraphic());
        }
        for (var id : List.of("folder", "file", "import", "export", "delete", "save", "refresh", "close")) {
          var button = assertInstanceOf(IconButton.class, browser.get().lookup("#material-" + id));
          assertEquals(28, button.getPrefHeight());
          assertNotNull(button.getAccessibleText());
        }
      });
      fx(() -> tree.get().getSelectionModel().select(tree.get().getRoot().getChildren().getFirst()));
      await(() -> tree.get().getSelectionModel().getSelectedItem().getChildren().size() == 1);
      fx(() -> tree.get().getSelectionModel().select(tree.get().getSelectionModel().getSelectedItem().getChildren().getFirst()));
      await(() -> !browser.get().isBusy() && ((TextArea) browser.get().lookup(".text-area")).isEditable());
      fx(() -> {
        ((TextArea) browser.get().lookup(".text-area")).setText("body { color: blue; }\n");
        ((IconButton) browser.get().lookup("#material-save")).fire();
      });
      await(() -> !browser.get().isBusy());
      assertEquals("body {}\n", contents.get());
      fx(() -> {
        assertEquals("body { color: blue; }\n", ((TextArea) browser.get().lookup(".text-area")).getText());
        Platform.runLater(() -> {
          var pane = (DialogPane) javafx.stage.Window.getWindows().stream()
              .filter(window -> window.getScene().getRoot() instanceof DialogPane)
              .findFirst().orElseThrow().getScene().getRoot();
          ((Button) pane.lookupButton(ButtonType.CANCEL)).fire();
        });
        ((IconButton) browser.get().lookup("#material-close")).fire();
        assertEquals(0, closeRequests.get(), "Cancel must keep the browser and draft open");
        rejectSave.set(false);
        ((IconButton) browser.get().lookup("#material-save")).fire();
      });
      await(() -> !browser.get().isBusy() && contents.get().contains("blue"));
      writer.set(false); fx(() -> browser.get().refresh());
      await(() -> !browser.get().isBusy() && tree.get().getRoot() == null);
      fx(() -> {
        assertEquals("", ((TextArea) browser.get().lookup(".text-area")).getText());
        ((IconButton) browser.get().lookup("#material-close")).fire();
        assertEquals(1, closeRequests.get(), "The browser must be closable even after access is revoked");
      });
    } finally { fx(() -> browser.get().close()); }
  }

  private static void fx(Runnable action) throws Exception {
    var done = new CompletableFuture<Void>();
    Platform.runLater(() -> { try { action.run(); done.complete(null); } catch (Throwable error) { done.completeExceptionally(error); } });
    done.get(10, TimeUnit.SECONDS);
  }

  @Test void structuredTextUsesMonacoAndAcknowledgesTheStagedApiSave() throws Exception {
    for (String filename : List.of("style.css", "Guide.md")) checkStructuredFile(filename);
  }

  private void checkStructuredFile(String filename) throws Exception {
    String path = "assets/" + filename;
    String initial = filename.endsWith(".md") ? "# Guide\n\n| A | B |\n|---|---|\n| 1 | 2 |\n" : "body {}\n";
    String modified = filename.endsWith(".md") ? "# Changed\n\n- [x] Done\n" : "body { color: blue; }\n";
    var saved = new AtomicReference<String>();
    var service = (ResourcesService) java.lang.reflect.Proxy.newProxyInstance(getClass().getClassLoader(),
        new Class<?>[] {ResourcesService.class}, (proxy, method, args) -> {
          switch (method.getName()) {
            case "serviceId": return "resources";
            case "info": {
              var info = new ResourceInfo(); info.setPermissions(EnumSet.of(CRUDOperation.READ, CRUDOperation.UPDATE)); return info;
            }
            case "retrieve": {
              var material = ProjectMaterial.coordinates((String) args[0]);
              if (material.getPath().isEmpty()) material.getEntries().add(ProjectMaterial.coordinates("project/assets/"));
              else if (material.isDirectory()) material.getEntries().add(new ProjectMaterial("project", path, null));
              else material.setContent(initial.getBytes(StandardCharsets.UTF_8));
              return material;
            }
            case "submit": {
              var material = (ProjectMaterial) args[0];
              assertEquals(ResourcesService.SubmissionMode.UPDATE, args[1]);
              assertEquals(path, material.getPath());
              saved.set(new String(material.getContent(), StandardCharsets.UTF_8));
              var state = new org.integratedmodelling.klab.api.data.RepositoryState(); state.setCurrentBranch("main");
              state.getModifiedPaths().add(path);
              var change = new ResourceSet.Resource(); change.setResourceUrn("project"); change.setRepositoryState(state);
              var result = new ResourceSet(); result.getProjects().add(change); return List.of(result);
            }
            default: return null;
          }
        });
    var browser = new AtomicReference<ProjectMaterialBrowser>();
    var stage = new AtomicReference<javafx.stage.Stage>();
    var tree = new AtomicReference<TreeView<ProjectMaterial>>();
    var published = new java.util.concurrent.atomic.AtomicInteger();
    fx(() -> {
      var view = new ProjectMaterialBrowser(service, null, "project", result -> {}, () -> {});
      view.getStylesheets().add(new atlantafx.base.theme.PrimerLight().getUserAgentStylesheet());
      browser.set(view);
      var window = new javafx.stage.Stage(); stage.set(window); window.setX(-8000);
      window.setScene(new Scene(view, 1000, 650)); window.show();
      @SuppressWarnings("unchecked") var folders = (TreeView<ProjectMaterial>) view.lookup(".tree-view"); tree.set(folders);
      view.setOnPublish(published::incrementAndGet);
    });
    try {
      await(() -> tree.get().getRoot() != null && tree.get().getRoot().getChildren().size() == 1);
      fx(() -> tree.get().getSelectionModel().select(tree.get().getRoot().getChildren().getFirst()));
      await(() -> tree.get().getSelectionModel().getSelectedItem().getChildren().size() == 1);
      fx(() -> tree.get().getSelectionModel().select(tree.get().getSelectionModel().getSelectedItem().getChildren().getFirst()));
      await(() -> !browser.get().isBusy());
      fx(() -> {
        if (!filename.endsWith(".md")) assertFalse(((TextArea) browser.get().lookup(".text-area")).isEditable());
        ((IconButton) browser.get().lookup("#material-edit")).fire();
      });
      await(() -> browser.get().lookup(".monaco-editor-view") instanceof org.integratedmodelling.klabeditor.MonacoEditorView monaco
          && monaco.getText().equals(initial));
      fx(() -> ((org.integratedmodelling.klabeditor.MonacoEditorView) browser.get().lookup(".monaco-editor-view"))
          .setText(modified));
      await(() -> !browser.get().lookup("#material-save").isDisabled());
      if (filename.endsWith(".md")) {
        fx(() -> ((IconButton) browser.get().lookup("#material-view")).fire());
        await(() -> browser.get().lookup(".web-view") instanceof javafx.scene.web.WebView preview
            && preview.getEngine().getDocument() != null
            && preview.getEngine().getDocument().getElementsByTagName("h1").getLength() == 1
            && preview.getEngine().getDocument().getElementsByTagName("h1").item(0).getTextContent().equals("Changed"));
      }
      fx(() -> ((IconButton) browser.get().lookup("#material-save")).fire());
      await(() -> !browser.get().isBusy() && saved.get() != null && browser.get().lookup("#material-save").isDisabled());
      assertEquals(modified, saved.get());
      fx(() -> {
        browser.get().applyCss(); browser.get().layout();
        assertTrue(browser.get().lookupAll(".tree-cell").stream()
            .anyMatch(node -> node instanceof TreeCell<?> cell && ("> " + filename).equals(cell.getText())));
        ((IconButton) browser.get().lookup("#material-publish")).fire();
        assertEquals(1, published.get());
      });
    } finally { fx(() -> { browser.get().close(); stage.get().close(); }); }
  }
  private static void await(BooleanSupplier condition) throws Exception {
    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
    while (System.nanoTime() < deadline) {
      var result = new AtomicBoolean(); fx(() -> result.set(condition.getAsBoolean()));
      if (result.get()) return;
      Thread.sleep(25);
    }
    fail("Timed out waiting for project material UI");
  }
}
