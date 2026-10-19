package org.integratedmodelling.klab.ide.components;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import javafx.application.Platform;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import org.integratedmodelling.klab.api.authentication.CRUDOperation;
import org.integratedmodelling.klab.api.knowledge.KlabAsset.KnowledgeClass;
import org.integratedmodelling.klab.api.knowledge.organization.impl.ProjectImpl;
import org.integratedmodelling.klab.api.knowledge.organization.impl.WorkspaceImpl;
import org.integratedmodelling.klab.api.lang.kim.impl.KimNamespaceImpl;
import org.integratedmodelling.klab.api.lang.kim.impl.KimOntologyImpl;
import org.integratedmodelling.klab.api.scope.UserScope;
import org.integratedmodelling.klab.api.services.ResourcesService;
import org.integratedmodelling.klab.api.services.resources.ResourceInfo;
import org.integratedmodelling.klab.api.services.resources.ResourceSet;
import org.integratedmodelling.klab.api.view.modeler.navigation.NavigableAsset;
import org.integratedmodelling.klab.ide.KlabIDEController;
import org.integratedmodelling.klab.modeler.model.NavigableWorkspace;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class WorkspaceDocumentChangeTest {
  @BeforeAll static void startFx() throws Exception {
    var ready = new CompletableFuture<Void>();
    try { Platform.startup(() -> { Platform.setImplicitExit(false); ready.complete(null); }); }
    catch (IllegalStateException started) { Platform.runLater(() -> ready.complete(null)); }
    ready.get(15, TimeUnit.SECONDS);
  }

  @Test void actualOntologyUpdateKeepsCollidingProjectAndNamespaceAndQueuesSavedSource() throws Exception {
    var ontology = new KimOntologyImpl();
    ontology.setUrn("imod"); ontology.setProjectName("imod"); ontology.setSourceCode("old ontology");
    var declaration = new org.integratedmodelling.klab.api.lang.kim.impl.KimConceptStatementImpl();
    declaration.setUrn("imod:Thing");
    ontology.setStatements(List.of(declaration));
    var namespace = new KimNamespaceImpl();
    namespace.setUrn("imod"); namespace.setProjectName("imod"); namespace.setSourceCode("namespace");
    var project = new ProjectImpl(); project.setUrn("imod");
    project.setOntologies(List.of(ontology)); project.setNamespaces(List.of(namespace));
    var workspace = new WorkspaceImpl(); workspace.setUrn("test-workspace"); workspace.setProjects(List.of(project));
    var submissions = new CopyOnWriteArrayList<ResourcesService.SubmissionMode>();
    var resources = (ResourcesService) Proxy.newProxyInstance(ResourcesService.class.getClassLoader(),
        new Class<?>[]{ResourcesService.class}, (self, method, args) -> switch (method.getName()) {
          case "retrieve" -> workspace;
          case "getFlows" -> List.of();
          case "lockProject" -> false;
          case "submit" -> { submissions.add((ResourcesService.SubmissionMode) args[1]); yield List.of(); }
          default -> null;
        });
    var info = new ResourceInfo(); info.setUrn("test-workspace");
    var previous = KlabIDEController.instance();
    WorkspaceEditor editor = null;
    try {
      editor = fx(() -> {
        new KlabIDEController() {
          @Override public UserScope user() { return null; }
          @Override public boolean handleResultSets(List<ResourceSet> changes) { return true; }
        };
        var result = new NavigationEditor(resources, info);
        var root = tree((NavigableWorkspace) read(result, "workspace"));
        write(result, "root", root); write(result, "treeView", new TreeView<>(root));
        return result;
      });
      var target = editor;
      var root = fx(() -> (TreeItem<NavigableAsset>) read(target, "root"));
      var projectNode = WorkspaceEditor.findTreeNode(root, "imod", KnowledgeClass.PROJECT);
      var namespaceNode = WorkspaceEditor.findTreeNode(root, "imod", KnowledgeClass.NAMESPACE);
      var ontologyNode = WorkspaceEditor.findTreeNode(root, "imod", KnowledgeClass.ONTOLOGY);
      var originalProject = projectNode.getValue();
      var originalNamespace = namespaceNode.getValue();
      fx(() -> {
        projectNode.setExpanded(true);
        ((TreeView<NavigableAsset>) read(target, "treeView")).getSelectionModel().select(namespaceNode);
        // The resource merger updates delegates before the workspace change notification.
        ontology.setSourceCode("saved ontology"); ontology.setLastUpdateTimestamp(2);
        var updated = new NavigableWorkspace(workspace);
        var change = new ResourceSet.Resource(); change.setResourceUrn("imod");
        change.setKnowledgeClass(KnowledgeClass.ONTOLOGY); change.setOperation(CRUDOperation.UPDATE);
        var changes = new ResourceSet(); changes.getOntologies().add(change);
        target.updateWorkspace(updated, changes, List.of(updated));
        return null;
      });
      fx(() -> {
        assertSame(originalProject, projectNode.getValue());
        assertSame(originalNamespace, namespaceNode.getValue());
        assertSame(projectNode, ontologyNode.getParent());
        assertTrue(projectNode.isExpanded());
        assertSame(namespaceNode, ((TreeView<NavigableAsset>) read(target, "treeView")).getSelectionModel().getSelectedItem());
        var updates = (Map<String, WorkspaceSemanticValidation.Update>) read(target, "semanticUpdates");
        assertEquals(2, updates.size());
        assertEquals("saved ontology", updates.get("imod:ontology:imod").request().document().getSourceCode());
        assertEquals("namespace", updates.get("imod:namespace:imod").request().document().getSourceCode());
        verifyRepeatedNavigationKeepsTreePosition(target, namespaceNode, ontologyNode);
        verifyValidationCellGraphics(target, ontologyNode, updates.get("imod:ontology:imod"));
        verifyCursorCallbacksUseCurrentParse((NavigationEditor) target, ontologyNode, namespaceNode);
        return null;
      });
      verifyRealPulses(target, ontologyNode);
      fx(() -> {
        target.createTopMenu();
        var automatic = (org.integratedmodelling.klab.ide.components.generic.IconButton) read(target, "automaticValidationControl");
        var manual = (org.integratedmodelling.klab.ide.components.generic.IconButton) read(target, "validateNowControl");
        assertTrue(automatic.isSelected()); assertTrue(manual.isDisabled());
        automatic.fire();
        assertFalse(automatic.isSelected()); assertFalse(manual.isDisabled());
        var save = WorkspaceEditor.class.getDeclaredMethod("saveDocument", String.class, NavigableAsset.class);
        save.setAccessible(true); save.invoke(target, "saved while paused", originalNamespace);
        manual.fire();
        assertTrue(manual.isDisabled());
        return null;
      });
      long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
      while (fx(() -> (boolean) read(target, "validationCommandPending"))) {
        if (System.nanoTime() > deadline) fail("Manual validation did not finish");
        Thread.sleep(20);
      }
      assertEquals(List.of(ResourcesService.SubmissionMode.UPDATE_DEFERRED, ResourcesService.SubmissionMode.VALIDATE), submissions);
      fx(() -> {
        assertFalse((boolean) read(target, "automaticValidation"));
        assertFalse(((org.integratedmodelling.klab.ide.components.generic.IconButton) read(target, "validateNowControl")).isDisabled());
        ((java.util.concurrent.ExecutorService) read(target, "validationCommands")).shutdown();
        return null;
      });
    } finally {
      var target = editor;
      fx(() -> {
        if (target != null) ((WorkspaceSemanticValidation) read(target, "semanticValidation")).close();
        var field = KlabIDEController.class.getDeclaredField("_this"); field.setAccessible(true); field.set(null, previous);
        return null;
      });
    }
  }

  private static void verifyRealPulses(WorkspaceEditor editor, TreeItem<NavigableAsset> ontology) throws Exception {
    var stage = fx(() -> {
      var tree = (TreeView<NavigableAsset>) read(editor, "treeView");
      tree.setShowRoot(false);
      for (int i = 0; i < 60; i++) {
        var document = new KimOntologyImpl(); document.setUrn("extra" + i); document.setProjectName("imod");
        ontology.getParent().getChildren().add(tree(new org.integratedmodelling.klab.modeler.model.NavigableKimOntology(document, null)));
      }
      var window = new javafx.stage.Stage();
      window.setTitle("Workspace tree pulse regression");
      var scene = tree.getScene();
      var content = scene.getRoot();
      scene.setRoot(new javafx.scene.layout.StackPane());
      var tabs = new org.integratedmodelling.klab.ide.components.generic.DockableTabPane();
      tabs.getTabs().add(new javafx.scene.control.Tab("Workspace", content));
      scene.setRoot(new javafx.scene.layout.StackPane(tabs));
      window.setScene(scene); window.show();
      return window;
    });
    try {
      for (int cycle = 0; cycle < 8; cycle++) {
        int iteration = cycle;
        fx(() -> {
          var tree = (TreeView<NavigableAsset>) read(editor, "treeView");
          tree.scrollTo(iteration % 2 == 0 ? 0 : 35);
          var current = (org.integratedmodelling.klab.modeler.model.NavigableKimOntology) ontology.getValue();
          WorkspaceEditor.updateTree(ontology, new org.integratedmodelling.klab.modeler.model.NavigableKimOntology(current.getDelegate(), null));
          ontology.setExpanded(iteration % 2 == 0);
          var updates = (Map<String, WorkspaceSemanticValidation.Update>) read(editor, "semanticUpdates");
          var update = updates.get("imod:ontology:imod");
          var completed = WorkspaceEditor.class.getDeclaredMethod("semanticValidationCompleted", String.class, WorkspaceSemanticValidation.Update.class);
          completed.setAccessible(true);
          var response = new org.integratedmodelling.klab.api.services.reasoner.objects.SemanticValidationResponse();
          response.setStatus(org.integratedmodelling.klab.api.services.reasoner.objects.SemanticValidationResponse.Status.UNAVAILABLE);
          completed.invoke(editor, "imod:ontology:imod", new WorkspaceSemanticValidation.Update(update.request(), iteration % 2 == 0 ? null : response));
          return null;
        });
        var rendered = new CompletableFuture<Void>();
        fx(() -> {
          var scene = stage.getScene();
          Runnable check = new Runnable() {
            int pulses;
            @Override public void run() {
              if (++pulses < 3) { Platform.requestNextPulse(); return; }
              scene.removePostLayoutPulseListener(this);
              try {
                var tree = (TreeView<NavigableAsset>) read(editor, "treeView");
                int visible = 0;
                for (var node : tree.lookupAll(".tree-cell")) {
                  var cell = (javafx.scene.control.TreeCell<?>) node;
                  boolean showing = true;
                  for (javafx.scene.Node ancestor = cell; ancestor != null; ancestor = ancestor.getParent()) showing &= ancestor.isVisible();
                  if (!showing || cell.isEmpty() || !cell.localToScene(cell.getBoundsInLocal()).intersects(tree.localToScene(tree.getBoundsInLocal()))) continue;
                  visible++;
                  assertSame(tree.getTreeItem(cell.getIndex()), cell.getTreeItem());
                  assertSame(cell.getTreeItem().getValue(), cell.getItem(), "Equal reparsed assets must replace cell items");
                  var icon = (javafx.scene.control.Label) ((javafx.scene.layout.HBox) cell.getGraphic()).getChildren().getFirst();
                  var expectedIcon = org.integratedmodelling.klab.ide.Theme.getGraphics(cell.getItem());
                  assertEquals(expectedIcon.getFont().getFamily(), icon.getFont().getFamily(), "CSS must preserve the icon font for " + cell.getItem());
                  assertEquals(expectedIcon.getText(), icon.getText());
                  var glyph = (javafx.scene.text.Text) icon.lookup(".text");
                  assertNotNull(glyph);
                  assertEquals(icon.getText(), glyph.getText(), "The icon skin must actually paint its glyph");
                  for (var label : cell.getGraphic().lookupAll(".label")) if (label.isVisible()) assertTrue(label.getLayoutBounds().getWidth() > 0);
                  for (var spinner : cell.getGraphic().lookupAll(".progress-indicator")) if (spinner.isVisible()) {
                    assertEquals(12, spinner.getLayoutBounds().getWidth(), 0.1);
                    assertEquals(12, spinner.getLayoutBounds().getHeight(), 0.1);
                  }
                }
                assertTrue(visible > 0);
                rendered.complete(null);
              } catch (Throwable error) { rendered.completeExceptionally(error); }
            }
          };
          scene.addPostLayoutPulseListener(check); Platform.requestNextPulse();
          return null;
        });
        rendered.get(10, TimeUnit.SECONDS);
      }
    } finally { fx(() -> { stage.close(); return null; }); }
  }

  private static final class NavigationEditor extends WorkspaceEditor {
    org.integratedmodelling.klabeditor.MonacoEditorView navigationEditor;
    NavigationEditor(ResourcesService resources, ResourceInfo info) { super(resources, info, null); }
    @Override protected javafx.scene.Node getEditor(NavigableAsset asset) {
      return navigationEditor == null ? super.getEditor(asset) : navigationEditor;
    }
    @Override protected boolean isEditorSelected(NavigableAsset asset) {
      return navigationEditor != null || super.isEditorSelected(asset);
    }
  }

  private static void verifyCursorCallbacksUseCurrentParse(NavigationEditor editor,
      TreeItem<NavigableAsset> ontology, TreeItem<NavigableAsset> sibling) throws Exception {
    var opened = (org.integratedmodelling.klab.modeler.model.NavigableKlabDocument<?, ?>) ontology.getValue();
    var parsed = new KimOntologyImpl();
    parsed.setUrn("imod"); parsed.setProjectName("imod"); parsed.setSourceCode("saved source");
    var first = new org.integratedmodelling.klab.api.lang.kim.impl.KimConceptStatementImpl();
    first.setUrn("imod:First"); first.setOffsetInDocument(1); first.setLength(9);
    var second = new org.integratedmodelling.klab.api.lang.kim.impl.KimConceptStatementImpl();
    second.setUrn("imod:Second"); second.setOffsetInDocument(10); second.setLength(10);
    parsed.setStatements(List.of(first, second));
    WorkspaceEditor.updateTree(ontology, new org.integratedmodelling.klab.modeler.model.NavigableKimOntology(parsed, null));
    var tree = (TreeView<NavigableAsset>) read(editor, "treeView");
    editor.navigationEditor = org.mockito.Mockito.mock(org.integratedmodelling.klabeditor.MonacoEditorView.class);
    org.mockito.Mockito.when(editor.navigationEditor.getText()).thenReturn("saved source");
    var callback = WorkspaceEditor.class.getDeclaredMethod("cursorPositionChanged",
        org.integratedmodelling.klabeditor.MonacoEditorView.class,
        org.integratedmodelling.klab.modeler.model.NavigableKlabDocument.class, int.class);
    callback.setAccessible(true);
    callback.invoke(editor, editor.navigationEditor, opened, 2);
    assertSame(ontology.getChildren().getFirst(), tree.getSelectionModel().getSelectedItem());
    tree.getSelectionModel().select(sibling);
    callback.invoke(editor, editor.navigationEditor, opened, 2);
    assertSame(sibling, tree.getSelectionModel().getSelectedItem(), "Save replay must respect subsequent tree browsing");
    callback.invoke(editor, editor.navigationEditor, opened, 12);
    assertSame(ontology.getChildren().getLast(), tree.getSelectionModel().getSelectedItem(), "Navigate using reparsed offsets");
    org.mockito.Mockito.when(editor.navigationEditor.getText()).thenReturn("newer unsaved edits");
    callback.invoke(editor, editor.navigationEditor, opened, 3);
    assertSame(ontology.getChildren().getLast(), tree.getSelectionModel().getSelectedItem(), "Do not apply stale offsets to unsaved content");
    editor.navigationEditor = null;
  }

  private static void verifyRepeatedNavigationKeepsTreePosition(WorkspaceEditor editor,
      TreeItem<NavigableAsset> selected, TreeItem<NavigableAsset> other) throws Exception {
    var tree = (TreeView<NavigableAsset>) read(editor, "treeView");
    tree.setSkin(new javafx.scene.control.skin.TreeViewSkin<>(tree));
    for (var item = selected.getParent(); item != null; item = item.getParent()) item.setExpanded(true);
    tree.getSelectionModel().select(selected);
    tree.getFocusModel().focus(tree.getRow(other));
    var scrolls = new java.util.concurrent.atomic.AtomicInteger();
    tree.addEventHandler(javafx.scene.control.ScrollToEvent.scrollToTopIndex(), event -> scrolls.incrementAndGet());
    var navigate = WorkspaceEditor.class.getDeclaredMethod("focusTreeOn", List.class);
    navigate.setAccessible(true);
    for (int callback = 0; callback < 3; callback++) navigate.invoke(editor, List.of(selected.getValue()));
    assertEquals(0, scrolls.get(), "Repeated cursor notifications must not scroll the tree");
    assertSame(selected, tree.getSelectionModel().getSelectedItem());
    assertSame(other, tree.getFocusModel().getFocusedItem(), "Repeated notifications must not steal tree focus");
    navigate.invoke(editor, List.of(other.getValue()));
    assertEquals(1, scrolls.get(), "Moving to another declaration must still reveal it");
    navigate.invoke(editor, List.of(other.getValue()));
    assertEquals(1, scrolls.get());
    tree.getSelectionModel().select(selected);
  }

  private static void verifyValidationCellGraphics(WorkspaceEditor editor,
      TreeItem<NavigableAsset> ontology, WorkspaceSemanticValidation.Update update) throws Exception {
    var tree = (TreeView<NavigableAsset>) read(editor, "treeView");
    for (var item = ontology.getParent(); item != null; item = item.getParent()) item.setExpanded(true);
    var type = Class.forName(WorkspaceEditor.class.getName() + "$AssetTreeCell");
    var constructor = type.getDeclaredConstructor(WorkspaceEditor.class);
    constructor.setAccessible(true);
    var cell = (javafx.scene.control.TreeCell<NavigableAsset>) constructor.newInstance(editor);
    cell.updateTreeView(tree);
    cell.updateIndex(tree.getRow(ontology));
    cell.setSkin(new javafx.scene.control.skin.TreeCellSkin<>(cell));
    var ontologyIcon = (javafx.scene.control.Label) ((javafx.scene.layout.HBox) cell.getGraphic()).getChildren().getFirst();
    var fontChanges = new java.util.concurrent.atomic.AtomicInteger();
    ontologyIcon.fontProperty().addListener((observable, before, after) -> fontChanges.incrementAndGet());
    var detachedWhilePublished = new java.util.ArrayList<javafx.scene.Node>();
    cell.graphicProperty().addListener((observable, oldGraphic, graphic) -> {
      if (graphic != null) graphic.parentProperty().addListener((property, oldParent, parent) -> {
        if (cell.getGraphic() == graphic && parent != cell) detachedWhilePublished.add(graphic);
      });
    });
    var completed = WorkspaceEditor.class.getDeclaredMethod("semanticValidationCompleted",
        String.class, WorkspaceSemanticValidation.Update.class);
    completed.setAccessible(true);
    var selected = tree.getSelectionModel().getSelectedItem();
    var focused = tree.getFocusModel().getFocusedItem();
    var events = new java.util.concurrent.atomic.AtomicInteger();
    tree.getRoot().addEventHandler(TreeItem.treeNotificationEvent(), event -> events.incrementAndGet());
    var response = new org.integratedmodelling.klab.api.services.reasoner.objects.SemanticValidationResponse();
    response.setStatus(org.integratedmodelling.klab.api.services.reasoner.objects.SemanticValidationResponse.Status.FAILED);
    var waiting = new org.integratedmodelling.klab.api.services.reasoner.objects.SemanticValidationResponse();
    waiting.setStatus(org.integratedmodelling.klab.api.services.reasoner.objects.SemanticValidationResponse.Status.UNAVAILABLE);
    completed.invoke(editor, "imod:ontology:imod", new WorkspaceSemanticValidation.Update(update.request(), waiting, true));
    var badge = (javafx.scene.control.Label) ((javafx.scene.layout.HBox) cell.getGraphic()).getChildren().getLast();
    assertTrue(badge.isVisible());assertEquals("Validating", badge.getText());
    assertEquals("Validating", badge.getAccessibleText());
    assertEquals(org.integratedmodelling.klab.api.services.reasoner.objects.SemanticValidationResponse.Status.UNAVAILABLE,
        waiting.getStatus(), "The badge must not rewrite semantic response status");
    completed.invoke(editor, "imod:ontology:imod", new WorkspaceSemanticValidation.Update(update.request(), waiting));
    assertTrue(badge.isVisible());assertEquals("Unavailable", badge.getText());
    for (int cycle = 0; cycle < 3; cycle++) {
      completed.invoke(editor, "imod:ontology:imod", new WorkspaceSemanticValidation.Update(update.request(), response));
      completed.invoke(editor, "imod:ontology:imod", new WorkspaceSemanticValidation.Update(update.request(), null));
      assertSame(ontology, cell.getTreeItem());
      assertSame(ontology.getValue(), cell.getItem());
      assertSame(cell, cell.getGraphic().getParent());
      var graphic = (javafx.scene.layout.HBox) cell.getGraphic();
      var visible = graphic.getChildren().stream().filter(javafx.scene.Node::isVisible).toList();
      assertEquals(2, visible.size());
      assertInstanceOf(javafx.scene.control.ProgressIndicator.class, visible.get(1));
    }
    assertTrue(detachedWhilePublished.isEmpty(), "A published graphic must not be stolen from the cell skin");
    assertEquals(0, fontChanges.get(), "Validation must not reset the ontology icon's font or layout metrics");
    assertEquals(0, events.get(), "Validation must not modify the tree model");
    assertSame(selected, tree.getSelectionModel().getSelectedItem());
    assertSame(focused, tree.getFocusModel().getFocusedItem());
    // Recycle the cell onto another document, then deliver a result for its previous row.
    var sibling = WorkspaceEditor.findTreeNode(tree.getRoot(), "imod", KnowledgeClass.NAMESPACE);
    cell.updateIndex(tree.getRow(sibling));
    var siblingGraphic = cell.getGraphic();
    completed.invoke(editor, "imod:ontology:imod", new WorkspaceSemanticValidation.Update(update.request(), response));
    assertSame(sibling, cell.getTreeItem());
    assertSame(siblingGraphic, cell.getGraphic());
    cell.updateIndex(tree.getRow(ontology));
    assertEquals("Semantic validation failed",
        ((javafx.scene.layout.HBox) cell.getGraphic()).getChildren().stream()
            .filter(node -> node.isVisible() && !(node instanceof org.integratedmodelling.klab.ide.components.generic.IconLabel))
            .findFirst().orElseThrow().getAccessibleText());
    response = new org.integratedmodelling.klab.api.services.reasoner.objects.SemanticValidationResponse();
    response.setStatus(org.integratedmodelling.klab.api.services.reasoner.objects.SemanticValidationResponse.Status.COMPLETE);
    completed.invoke(editor, "imod:ontology:imod", new WorkspaceSemanticValidation.Update(update.request(), response));
    assertEquals(1, ((javafx.scene.layout.HBox) cell.getGraphic()).getChildren().stream()
        .filter(javafx.scene.Node::isVisible).count());
    cell.updateIndex(-1);
    assertNull(cell.getGraphic());
    assertNull(cell.getTooltip());
    verifyLaidOutTree(editor, tree, constructor, completed, update);
  }

  private static void verifyLaidOutTree(WorkspaceEditor editor, TreeView<NavigableAsset> tree,
      java.lang.reflect.Constructor<?> constructor, java.lang.reflect.Method completed,
      WorkspaceSemanticValidation.Update update) throws Exception {
    tree.setCellFactory(ignored -> {
      try { return (javafx.scene.control.TreeCell<NavigableAsset>) constructor.newInstance(editor); }
      catch (Exception error) { throw new RuntimeException(error); }
    });
    tree.getStyleClass().addAll(atlantafx.base.theme.Styles.DENSE, atlantafx.base.theme.Tweaks.EDGE_TO_EDGE);
    var pane = new javafx.scene.layout.StackPane(tree);
    var scene = new javafx.scene.Scene(pane, 340, 500);
    javafx.application.Application.setUserAgentStylesheet(org.integratedmodelling.klab.ide.Theme.CURRENT_THEME.getStylesheet());
    scene.getStylesheets().add(org.integratedmodelling.klab.ide.KlabIDEApplication.class.getResource("custom.css").toExternalForm());
    pane.resize(340, 500);
    var ontology = WorkspaceEditor.findTreeNode(tree.getRoot(), "imod", KnowledgeClass.ONTOLOGY);
    var graphics = new java.util.IdentityHashMap<javafx.scene.control.TreeCell<?>, javafx.scene.Node>();
    for (int cycle = 0; cycle < 12; cycle++) {
      // A save reparses the document into a different wrapper with equal navigation identity.
      var parsed = new org.integratedmodelling.klab.modeler.model.NavigableKimOntology(
          (KimOntologyImpl) ontology.getValue().getDelegate(), null);
      WorkspaceEditor.updateTree(ontology, parsed);
      var response = new org.integratedmodelling.klab.api.services.reasoner.objects.SemanticValidationResponse();
      response.setStatus(org.integratedmodelling.klab.api.services.reasoner.objects.SemanticValidationResponse.Status.UNAVAILABLE);
      completed.invoke(editor, "imod:ontology:imod", new WorkspaceSemanticValidation.Update(update.request(), cycle % 2 == 0 ? null : response));
      pane.applyCss();
      pane.layout();
      for (var node : tree.lookupAll(".tree-cell")) {
        var row = (javafx.scene.control.TreeCell<?>) node;
        if (row.isEmpty() || row.getTreeItem() == null || !row.isVisible() || row.getWidth() == 0) continue;
        assertSame(tree.getTreeItem(row.getIndex()), row.getTreeItem());
        assertSame(row, row.getGraphic().getParent());
        var previous = graphics.put(row, row.getGraphic());
        if (previous != null) assertSame(previous, row.getGraphic(), "Cell controls must survive layout and validation");
        for (var spinner : row.getGraphic().lookupAll(".progress-indicator")) {
          assertTrue(spinner.getLayoutBounds().getWidth() <= 12.1, "Oversized spinner: " + spinner.getLayoutBounds());
          assertTrue(spinner.getLayoutBounds().getHeight() <= 12.1);
        }
        for (var label : row.getGraphic().lookupAll(".label")) {
          if (!label.isVisible()) { assertFalse(label.isManaged()); continue; }
          assertTrue(label.getBoundsInLocal().getWidth() > 0, "Invisible label: " + label + " cycle=" + cycle + " row=" + row.getIndex() + " bounds=" + row.getLayoutBounds() + " graphic=" + row.getGraphic().getLayoutBounds());
          assertTrue(label.getBoundsInLocal().getHeight() > 0, "Invisible label: " + label);
        }
      }
      int collapsedCount = tree.getExpandedItemCount();
      ontology.setExpanded(true);
      pane.applyCss(); pane.layout();
      assertEquals(collapsedCount + 1, tree.getExpandedItemCount());
      assertEquals(tree.getRow(ontology) + 1, tree.getRow(ontology.getChildren().getFirst()));
      ontology.setExpanded(false);
      pane.applyCss(); pane.layout();
      assertEquals(collapsedCount, tree.getExpandedItemCount());
    }
  }

  private static TreeItem<NavigableAsset> tree(NavigableAsset asset) {
    var root = new TreeItem<>(asset);
    for (var child : asset.children()) root.getChildren().add(tree(child));
    return root;
  }
  private static Object read(Object object, String name) throws Exception {
    var field = WorkspaceEditor.class.getDeclaredField(name); field.setAccessible(true); return field.get(object);
  }
  private static void write(Object object, String name, Object value) throws Exception {
    var field = WorkspaceEditor.class.getDeclaredField(name); field.setAccessible(true); field.set(object, value);
  }
  private static <T> T fx(Callable<T> action) throws Exception {
    var result = new CompletableFuture<T>();
    Platform.runLater(() -> { try { result.complete(action.call()); } catch (Throwable error) { result.completeExceptionally(error); } });
    return result.get(10, TimeUnit.SECONDS);
  }
}
