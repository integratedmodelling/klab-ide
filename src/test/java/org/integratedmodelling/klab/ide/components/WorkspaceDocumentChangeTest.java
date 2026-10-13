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
    var namespace = new KimNamespaceImpl();
    namespace.setUrn("imod"); namespace.setProjectName("imod"); namespace.setSourceCode("namespace");
    var project = new ProjectImpl(); project.setUrn("imod");
    project.setOntologies(List.of(ontology)); project.setNamespaces(List.of(namespace));
    var workspace = new WorkspaceImpl(); workspace.setUrn("test-workspace"); workspace.setProjects(List.of(project));
    var resources = (ResourcesService) Proxy.newProxyInstance(ResourcesService.class.getClassLoader(),
        new Class<?>[]{ResourcesService.class}, (self, method, args) -> switch (method.getName()) {
          case "retrieve" -> workspace;
          case "getFlows" -> List.of();
          case "lockProject" -> false;
          default -> null;
        });
    var info = new ResourceInfo(); info.setUrn("test-workspace");
    var previous = KlabIDEController.instance();
    WorkspaceEditor editor = null;
    try {
      editor = fx(() -> {
        new KlabIDEController() { @Override public UserScope user() { return null; } };
        var result = new WorkspaceEditor(resources, info, null);
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

  private static TreeItem<NavigableAsset> tree(NavigableAsset asset) {
    var root = new TreeItem<>(asset);
    for (var child : asset.children()) root.getChildren().add(tree(child));
    return root;
  }
  private static Object read(Object object, String name) throws Exception {
    var field = object.getClass().getDeclaredField(name); field.setAccessible(true); return field.get(object);
  }
  private static void write(Object object, String name, Object value) throws Exception {
    var field = object.getClass().getDeclaredField(name); field.setAccessible(true); field.set(object, value);
  }
  private static <T> T fx(Callable<T> action) throws Exception {
    var result = new CompletableFuture<T>();
    Platform.runLater(() -> { try { result.complete(action.call()); } catch (Throwable error) { result.completeExceptionally(error); } });
    return result.get(10, TimeUnit.SECONDS);
  }
}
