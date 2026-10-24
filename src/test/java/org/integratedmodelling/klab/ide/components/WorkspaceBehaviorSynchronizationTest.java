package org.integratedmodelling.klab.ide.components;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Proxy;
import java.nio.file.*;
import java.util.List;
import java.util.concurrent.*;
import javafx.application.Platform;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import org.integratedmodelling.klab.api.authentication.CRUDOperation;
import org.integratedmodelling.klab.api.knowledge.KlabAsset;
import org.integratedmodelling.klab.api.knowledge.organization.impl.ProjectImpl;
import org.integratedmodelling.klab.api.knowledge.organization.impl.WorkspaceImpl;
import org.integratedmodelling.klab.api.lang.kactors.KActorsBehavior;
import org.integratedmodelling.klab.api.lang.kactors.impl.KActorsBehaviorImpl;
import org.integratedmodelling.klab.api.scope.UserScope;
import org.integratedmodelling.klab.api.services.ResourcesService;
import org.integratedmodelling.klab.api.services.resources.ResourceInfo;
import org.integratedmodelling.klab.api.services.resources.ResourceSet;
import org.integratedmodelling.klab.api.view.modeler.navigation.NavigableAsset;
import org.integratedmodelling.klab.ide.KlabIDEController;
import org.integratedmodelling.klab.modeler.model.NavigableWorkspace;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class WorkspaceBehaviorSynchronizationTest {
  @TempDir Path temporaryDirectory;

  @BeforeAll static void startFx() throws Exception {
    var ready = new CompletableFuture<Void>();
    try { Platform.startup(() -> { Platform.setImplicitExit(false); ready.complete(null); }); }
    catch (IllegalStateException started) { Platform.runLater(() -> ready.complete(null)); }
    ready.get(15, TimeUnit.SECONDS);
  }

  @Test void successiveWorkspaceSavesRefreshAnApplicationMirrorWithoutTreeChanges() throws Exception {
    var mirrors = new ManagedBehaviorMirrors(temporaryDirectory);
    var checkout = mirrors.checkout("resources", "project", "project.app", "original template");
    var application = new KActorsBehaviorImpl();
    application.setUrn("project.app"); application.setProjectName("project");
    application.setBehaviorType(KActorsBehavior.Type.APP);
    application.setSourceCode("original template");
    var project = new ProjectImpl(); project.setUrn("project"); project.setApps(List.of(application));
    var workspace = new WorkspaceImpl(); workspace.setUrn("workspace"); workspace.setProjects(List.of(project));
    var resources = (ResourcesService) Proxy.newProxyInstance(ResourcesService.class.getClassLoader(),
        new Class<?>[]{ResourcesService.class}, (self, method, args) -> switch (method.getName()) {
          case "retrieve" -> workspace;
          case "getFlows" -> List.of();
          case "lockProject" -> false;
          default -> null;
        });
    var info = new ResourceInfo(); info.setUrn("workspace");
    var previous = KlabIDEController.instance();
    var editor = fx(() -> {
      new KlabIDEController() {
        @Override public UserScope user() { return null; }
        @Override public void synchronizeManagedBehavior(ResourcesService service, KActorsBehavior behavior) {
          try { mirrors.synchronize("resources", behavior.getProjectName(), behavior.getUrn(), behavior.getSourceCode()); }
          catch (java.io.IOException failure) { throw new RuntimeException(failure); }
        }
      };
      var result = new WorkspaceEditor(resources, info, null);
      var root = new TreeItem<NavigableAsset>(new NavigableWorkspace(workspace));
      write(result, "root", root); write(result, "treeView", new TreeView<>(root));
      return result;
    });
    try {
      for (String saved : List.of("saved application v2", "saved application v3")) {
        fx(() -> {
          application.setSourceCode(saved);
          var change = new ResourceSet.Resource(); change.setResourceUrn(application.getUrn());
          change.setKnowledgeClass(KlabAsset.classify(application)); change.setOperation(CRUDOperation.UPDATE);
          var changes = new ResourceSet(); changes.getBehaviors().add(change);
          editor.updateWorkspace(new NavigableWorkspace(workspace), changes, List.of());
          return null;
        });
        fx(() -> null); // Drain the queued reconciliation.
        assertEquals(saved, Files.readString(checkout.file()));
      }
    } finally {
      fx(() -> {
        editor.close();
        var field = KlabIDEController.class.getDeclaredField("_this"); field.setAccessible(true); field.set(null, previous);
        return null;
      });
    }
  }

  private static void write(Object object, String name, Object value) throws Exception {
    var field = WorkspaceEditor.class.getDeclaredField(name); field.setAccessible(true); field.set(object, value);
  }
  private static <T> T fx(Callable<T> action) throws Exception {
    var result = new CompletableFuture<T>();
    Platform.runLater(() -> { try { result.complete(action.call()); } catch (Throwable failure) { result.completeExceptionally(failure); } });
    return result.get(10, TimeUnit.SECONDS);
  }
}
