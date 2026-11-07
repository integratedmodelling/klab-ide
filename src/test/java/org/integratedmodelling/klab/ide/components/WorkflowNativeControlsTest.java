package org.integratedmodelling.klab.ide.components;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.ImageView;
import javafx.stage.Stage;
import javafx.stage.Window;
import javax.imageio.ImageIO;
import org.integratedmodelling.klab.api.services.resources.workflow.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Real JavaFX controls against an isolated service, never production flow data. */
class WorkflowNativeControlsTest {
  @BeforeAll static void start() { WorkflowProposalTransitionTest.start(); }
  @TempDir Path directory;

  @AfterEach void closeWindows() throws Exception {
    WorkflowProposalTransitionTest.fx(() -> Window.getWindows().stream().filter(Window::isShowing)
        .toList().forEach(Window::hide));
  }

  static WorkflowEditor savedEditor(ReviewEditorJourneyFixture fixture, Runnable closed,
      java.util.function.Consumer<Flow> deleted) {
    var initial = Flow.State.create();
    initial.setSchemaId("bootstrap"); initial.setOwner("fixture-editor");
    initial.setAssetUrn("fixture.hydrology"); initial.setTitle("Synthetic saved bootstrap");
    fixture.saved = WorkflowUIProvider.NONE.draftFlow(fixture.workflow, initial, fixture.scope);
    fixture.saved.setId("fixture-flow"); fixture.saved.setRevision(3);
    initial.setFlowId(fixture.saved.getId());
    return new WorkflowEditor(fixture.service, fixture.scope, fixture.workflow, fixture.copy(),
        (w,f,s,schema,ro,changed) -> ReviewStageEditor.create(w,f,s,fixture.service.getUrl()),
        closed, null, deleted);
  }

  static Stage show(WorkflowEditor editor) {
    var stage = new Stage(); stage.setScene(new Scene(editor, 1150, 800)); stage.show(); return stage;
  }

  static DialogPane diagram() {
    return Window.getWindows().stream().filter(Window::isShowing).map(w -> w.getScene().getRoot())
        .filter(DialogPane.class::isInstance).map(DialogPane.class::cast)
        .filter(pane -> pane.lookup("#workflow-export-png") != null).findFirst().orElseThrow();
  }

  static void answerDeletion(boolean delete) {
    Platform.runLater(() -> Window.getWindows().stream().filter(Window::isShowing).toList().forEach(window -> {
      if (window.getScene().getRoot() instanceof DialogPane pane) {
        var option = pane.getButtonTypes().stream().filter(type ->
            (delete ? "Delete flow" : "Cancel").equals(type.getText())).findFirst();
        option.ifPresent(type -> {
          var cancel = pane.getButtonTypes().stream().filter(t -> "Cancel".equals(t.getText())).findFirst().orElseThrow();
          assertTrue(((Button)pane.lookupButton(cancel)).isDefaultButton(), "Enter must default to Cancel");
          assertTrue(pane.getContentText().contains("cannot be undone"));
          assertTrue(pane.getContentText().contains("Flow: fixture-flow"));
          assertTrue(pane.getContentText().contains("Asset: fixture.hydrology"));
          assertTrue(pane.getContentText().contains("Project files and already exported archives are not deleted."));
          ((Button)pane.lookupButton(type)).fire();
        });
      }
    }));
  }

  static void await(java.util.function.BooleanSupplier condition) throws Exception {
    var done = new AtomicBoolean();
    for (int i=0; i<250 && !done.get(); i++) {
      WorkflowProposalTransitionTest.fx(() -> done.set(condition.getAsBoolean()));
      if (!done.get()) Thread.sleep(20);
    }
    assertTrue(done.get(), "UI operation did not finish");
  }

  @Test void persistedFlowUsesOriginalPngAndKeepsDescriptionsWithRetryAndSingleWindow() throws Exception {
    var fixture = new ReviewEditorJourneyFixture(); fixture.failDiagram = true;
    var ref = new AtomicReference<WorkflowEditor>();
    WorkflowProposalTransitionTest.fx(() -> {
      var editor = savedEditor(fixture, () -> {}, ignored -> {}); ref.set(editor); show(editor);
      ((Button)editor.lookup("#workflow-diagram-button")).fire();
      ((Button)editor.lookup("#workflow-diagram-button")).fire();
      var tabs = (TabPane)diagram().lookup("#workflow-diagram-tabs");
      assertEquals(2, tabs.getTabs().size());
      assertEquals("Descriptions and history", tabs.getTabs().get(1).getText());
      assertTrue(tabs.getTabs().get(1).getContent() instanceof ScrollPane);
    });
    await(() -> diagram().lookup("#workflow-diagram-error") != null);
    WorkflowProposalTransitionTest.fx(() -> {
      assertEquals(1, fixture.diagrams);
      assertTrue(((Button)diagram().lookup("#workflow-export-png")).isDisabled());
      fixture.failDiagram = false;
      ((Button)diagram().lookup("#workflow-refresh-diagram")).fire();
    });
    await(() -> diagram().lookup("#workflow-diagram-image") != null);
    WorkflowProposalTransitionTest.fx(() -> {
      assertEquals(2, fixture.diagrams);
      assertEquals(900, ((ImageView)diagram().lookup("#workflow-diagram-image")).getImage().getWidth());
      assertFalse(((Button)diagram().lookup("#workflow-export-png")).isDisabled());
      ref.get().close();
    });
    WorkflowProposalTransitionTest.fx(() -> {
      ((Button)ref.get().lookup("#workflow-diagram-button")).fire();
    });
    await(() -> diagram().lookup("#workflow-diagram-image") != null);
    assertEquals(3, fixture.diagrams);
    WorkflowProposalTransitionTest.fx(() -> ref.get().close());
  }

  @Test void pngWriterPreservesPixelsAndReportsFilesystemFailure() throws Exception {
    var image = new java.awt.image.BufferedImage(25, 12, java.awt.image.BufferedImage.TYPE_INT_ARGB);
    image.setRGB(4, 5, 0xff2468ac);
    var target = directory.resolve("workflow.png");
    WorkflowEditor.writeDiagramPng(image, target);
    var decoded = ImageIO.read(target.toFile());
    assertEquals(25, decoded.getWidth()); assertEquals(12, decoded.getHeight());
    assertEquals(image.getRGB(4, 5), decoded.getRGB(4, 5));
    assertThrows(java.io.IOException.class,
        () -> WorkflowEditor.writeDiagramPng(image, directory.resolve("missing").resolve("workflow.png")));
  }

  @Test void unsavedDraftRendersPinnedWorkflowAndHasNoDeleteControl() throws Exception {
    var fixture = new ReviewEditorJourneyFixture();
    var ref = new AtomicReference<WorkflowEditor>();
    WorkflowProposalTransitionTest.fx(() -> {
      var editor = fixture.editor(() -> {}); ref.set(editor); show(editor);
      assertFalse(editor.lookup("#workflow-delete-button").isVisible());
      ((Button)editor.lookup("#workflow-diagram-button")).fire();
    });
    await(() -> diagram().lookup("#workflow-diagram-image") != null);
    WorkflowProposalTransitionTest.fx(() -> {
      assertEquals(1, fixture.diagrams);
      assertTrue(diagram().lookupAll(".label").stream().filter(Label.class::isInstance)
          .map(Label.class::cast).anyMatch(label -> label.getText().contains("Pinned workflow definition 0.3")));
      assertNull(fixture.saved);
      ref.get().close();
    });
  }

  @Test void cancellationAndFalseResultRetainDraftAndNeverCloseOrNotifyDeletion() throws Exception {
    var fixture = new ReviewEditorJourneyFixture(); fixture.rejectDeletion = true;
    var closed = new AtomicInteger(); var deleted = new AtomicInteger();
    var ref = new AtomicReference<WorkflowEditor>();
    WorkflowProposalTransitionTest.fx(() -> {
      var editor = savedEditor(fixture, closed::incrementAndGet, ignored -> deleted.incrementAndGet());
      ref.set(editor); show(editor);
      ((TextField)editor.lookup("#review-stage-title")).setText("Unsaved title retained");
      answerDeletion(false); ((Button)editor.lookup("#workflow-delete-button")).fire();
      assertEquals(0, fixture.deletions); assertFalse(editor.isDisabled());
      answerDeletion(true); ((Button)editor.lookup("#workflow-delete-button")).fire();
    });
    await(() -> fixture.deletions == 1 && !ref.get().isDisabled());
    WorkflowProposalTransitionTest.fx(() -> {
      assertEquals(0, deleted.get()); assertEquals(0, closed.get());
      assertEquals("Unsaved title retained", ((TextField)ref.get().lookup("#review-stage-title")).getText());
      assertNotNull(fixture.saved); assertFalse(((Button)ref.get().lookup("#workflow-delete-button")).isDisabled());
    });
  }

  @Test void uncertainFailureRequiresRefreshBeforeExplicitRetryAndPreservesDraft() throws Exception {
    var fixture = new ReviewEditorJourneyFixture(); fixture.failDeletion = true;
    var ref = new AtomicReference<WorkflowEditor>(); var deleted = new AtomicInteger();
    WorkflowProposalTransitionTest.fx(() -> {
      var editor = savedEditor(fixture, () -> {}, ignored -> deleted.incrementAndGet()); ref.set(editor); show(editor);
      ((TextField)editor.lookup("#review-stage-title")).setText("Keep after connection failure");
      answerDeletion(true); ((Button)editor.lookup("#workflow-delete-button")).fire();
    });
    await(() -> fixture.deletions == 1 && !ref.get().isDisabled());
    WorkflowProposalTransitionTest.fx(() -> {
      var editor = ref.get();
      assertTrue(((Button)editor.lookup("#workflow-delete-button")).isDisabled());
      assertEquals("Keep after connection failure", ((TextField)editor.lookup("#review-stage-title")).getText());
      ((Button)editor.lookup("#workflow-delete-button")).fire(); assertEquals(1, fixture.deletions);
      WorkflowProposalTransitionTest.answer(ButtonType.OK);
      ((Button)editor.lookup("#workflow-refresh")).fire();
    });
    await(() -> !ref.get().isDisabled());
    WorkflowProposalTransitionTest.fx(() -> {
      var editor = ref.get();
      assertFalse(((Button)editor.lookup("#workflow-delete-button")).isDisabled());
      assertTrue(editor.lookupAll(".label").stream().filter(Label.class::isInstance)
          .map(Label.class::cast).noneMatch(label -> label.getText().contains("Deletion was not confirmed")));
      fixture.failDeletion = false;
      answerDeletion(true); ((Button)editor.lookup("#workflow-delete-button")).fire();
    });
    await(() -> deleted.get() == 1);
    assertEquals(2, fixture.deletions); assertNull(fixture.saved);
  }

  @Test void successClosesOnceAndBlocksRepeatedClicksAndCloseDuringDeletion() throws Exception {
    var fixture = new ReviewEditorJourneyFixture();
    var entered = new CountDownLatch(1); var release = new CountDownLatch(1);
    fixture.beforeDelete = () -> { entered.countDown(); try { assertTrue(release.await(10, TimeUnit.SECONDS)); }
      catch (InterruptedException e) { throw new RuntimeException(e); } };
    var ref = new AtomicReference<WorkflowEditor>(); var closed = new AtomicInteger(); var deleted = new AtomicInteger();
    WorkflowProposalTransitionTest.fx(() -> {
      var editor = savedEditor(fixture, closed::incrementAndGet, ignored -> deleted.incrementAndGet()); ref.set(editor); show(editor);
      ((Button)editor.lookup("#workflow-diagram-button")).fire();
      answerDeletion(true); ((Button)editor.lookup("#workflow-delete-button")).fire();
    });
    assertTrue(entered.await(5, TimeUnit.SECONDS));
    WorkflowProposalTransitionTest.fx(() -> {
      assertFalse(ref.get().requestClose());
      ((Button)ref.get().lookup("#workflow-delete-button")).fire();
      assertEquals(1, fixture.deletions);
    });
    release.countDown(); await(() -> closed.get() == 1);
    WorkflowProposalTransitionTest.fx(() -> {
      assertEquals(1, deleted.get()); assertTrue(ref.get().requestClose()); assertNull(fixture.saved);
      assertTrue(Window.getWindows().stream().noneMatch(w -> w.isShowing() && w.getScene().getRoot() instanceof DialogPane));
    });
  }

  @Test void deletionIsAdminOnlyIncludingCreatorAndProgressedOrClosedFlows() {
    var fixture = new ReviewEditorJourneyFixture();
    var flow = Flow.create(); flow.setOwner("fixture-editor");
    var owner = new WorkflowParticipant(); owner.setIdentity("fixture-editor");
    owner.setRoles(Set.of(WorkflowRole.EDITOR)); owner.setPermittedWorkflows(Set.of("*"));
    assertNotNull(WorkflowEditor.deletionProblem(fixture.workflow, flow, owner));
    var admin = WorkflowParticipant.from(fixture.scope);
    assertNull(WorkflowEditor.deletionProblem(fixture.workflow, flow, admin));
    flow.setStatus(Flow.Status.CLOSED); flow.setPublicRead(true);
    assertNull(WorkflowEditor.deletionProblem(fixture.workflow, flow, admin));
    fixture.workflow.getMetadata().clear();
    assertNotNull(WorkflowEditor.deletionProblem(fixture.workflow, flow, owner));
    assertNull(WorkflowEditor.deletionProblem(fixture.workflow, flow, admin));
  }

  @Test void plainClientScopeUsesAuthenticatedServiceAdminCapabilityNeverLocalhostOrOwnership() throws Exception {
    for (boolean administrator : new boolean[] {true, false}) {
      var fixture = new ReviewEditorJourneyFixture(); var calls = new AtomicInteger();
      var clientScope = (org.integratedmodelling.klab.api.scope.UserScope)java.lang.reflect.Proxy.newProxyInstance(
          getClass().getClassLoader(), new Class<?>[] {org.integratedmodelling.klab.api.scope.UserScope.class},
          (p,m,a) -> "getUser".equals(m.getName()) ? fixture.scope.getUser() : null);
      assertFalse(WorkflowParticipant.from(clientScope).getRoles().contains(WorkflowRole.ADMIN));
      var capabilities = new org.integratedmodelling.common.services.ResourcesCapabilitiesImpl();
      capabilities.setPermissions(administrator ? Set.of(org.integratedmodelling.klab.api.authentication.CRUDOperation.ADMINISTER) : Set.of());
      var service = (org.integratedmodelling.klab.api.services.ResourcesService)java.lang.reflect.Proxy.newProxyInstance(
          getClass().getClassLoader(), new Class<?>[] {org.integratedmodelling.klab.api.services.ResourcesService.class},
          (p,m,a) -> {
            if ("capabilities".equals(m.getName())) { assertSame(clientScope, a[0]); calls.incrementAndGet(); return capabilities; }
            if ("isLocal".equals(m.getName())) throw new AssertionError("Locality is not administrator authorization");
            return m.invoke(fixture.service, a);
          });
      var ref = new AtomicReference<WorkflowEditor>();
      WorkflowProposalTransitionTest.fx(() -> {
        savedEditor(fixture, () -> {}, ignored -> {}).close();
        var editor = new WorkflowEditor(service, clientScope, fixture.workflow, fixture.copy(), null);
        ref.set(editor); show(editor);
      });
      await(() -> calls.get() == 1 && (!administrator || !ref.get().lookup("#workflow-delete-button").isDisabled()));
      WorkflowProposalTransitionTest.fx(() -> {
        assertEquals(!administrator, ref.get().lookup("#workflow-delete-button").isDisabled());
        ref.get().close(); ((Stage)ref.get().getScene().getWindow()).close();
      });
    }
  }

  @Test void actualWorkspaceHostRemovesDeletedFlowFromSelectedTabRegistryAndAssetIndicator() throws Exception {
    var fixture = new ReviewEditorJourneyFixture();
    var previous = org.integratedmodelling.klab.ide.KlabIDEController.instance();
    var hostRef = new AtomicReference<WorkspaceEditor>();
    var registryRef = new AtomicReference<java.util.Map<String, WorkflowEditor>>();
    try {
      WorkflowProposalTransitionTest.fx(() -> {
        new org.integratedmodelling.klab.ide.KlabIDEController() {
          @Override public org.integratedmodelling.klab.api.scope.UserScope user() { return fixture.scope; }
          @Override public boolean handleNotification(org.integratedmodelling.klab.api.services.runtime.Notification notification) {
            fail("Unexpected workspace notification: " + notification); return false;
          }
        };
        var temporary = savedEditor(fixture, () -> {}, ignored -> {}); temporary.close();
        var workspace = new org.integratedmodelling.klab.api.knowledge.organization.impl.WorkspaceImpl();
        workspace.setUrn("synthetic-workspace"); workspace.setProjects(java.util.List.of());
        var prepared = new WorkspaceOpening.Prepared(new org.integratedmodelling.klab.modeler.model.NavigableWorkspace(workspace),
            new WorkspaceOpening.Access(Set.of(fixture.saved.getAssetUrn()), Set.of(), java.util.List.of()));
        var host = new WorkspaceEditor(fixture.service, prepared, null, WorkflowUIProvider.NONE);
        hostRef.set(host);
        try {
          var open = WorkspaceEditor.class.getDeclaredMethod("openWorkflow", Flow.class, Workflow.class);
          open.setAccessible(true); open.invoke(host, fixture.copy(), fixture.workflow);
          @SuppressWarnings("unchecked") var registry = (java.util.Map<String, WorkflowEditor>)field(host, WorkspaceEditor.class, "workflowEditors");
          registryRef.set(registry);
          assertEquals(1, registry.size());
          var editor = registry.values().iterator().next();
          var tabs = (org.integratedmodelling.klab.ide.components.generic.DockableTabPane)
              field(host, org.integratedmodelling.klab.ide.pages.EditorPage.class, "editorTabs");
          assertEquals(1, tabs.getTabs().size()); assertSame(editor, tabs.getSelectionModel().getSelectedItem().getContent());
          var duplicate = new WorkflowEditor(fixture.service, fixture.scope, fixture.workflow, fixture.copy(), null);
          registry.put("workflow:draft-original-key", duplicate);
          var attach = org.integratedmodelling.klab.ide.pages.EditorPage.class.getDeclaredMethod(
              "showAuxiliaryEditor", String.class, String.class, javafx.scene.Node.class);
          attach.setAccessible(true); attach.invoke(host, "workflow:draft-original-key", "Original draft tab", duplicate);
          assertEquals(2, tabs.getTabs().size());
          answerDeletion(true); ((Button)editor.lookup("#workflow-delete-button")).fire();
        } catch (ReflectiveOperationException error) { throw new RuntimeException(error); }
      });
      await(() -> registryRef.get().isEmpty());
      WorkflowProposalTransitionTest.fx(() -> {
        try {
          var host = hostRef.get();
          assertTrue(((java.util.Set<?>)field(host, WorkspaceEditor.class, "assetsWithFlows")).isEmpty());
          assertTrue(((java.util.Map<?,?>)field(host, org.integratedmodelling.klab.ide.pages.EditorPage.class, "auxiliaryEditors")).isEmpty());
          var tabs = (org.integratedmodelling.klab.ide.components.generic.DockableTabPane)
              field(host, org.integratedmodelling.klab.ide.pages.EditorPage.class, "editorTabs");
          assertTrue(tabs.getTabs().isEmpty()); assertNull(tabs.getSelectionModel().getSelectedItem());
        } catch (ReflectiveOperationException error) { throw new RuntimeException(error); }
      });
    } finally {
      WorkflowProposalTransitionTest.fx(() -> {
        try {
          if (hostRef.get() != null) hostRef.get().close();
          var singleton = org.integratedmodelling.klab.ide.KlabIDEController.class.getDeclaredField("_this");
          singleton.setAccessible(true); singleton.set(null, previous);
        } catch (ReflectiveOperationException error) { throw new RuntimeException(error); }
      });
    }
  }

  private static Object field(Object target, Class<?> owner, String name) throws ReflectiveOperationException {
    var field = owner.getDeclaredField(name); field.setAccessible(true); return field.get(target);
  }
}
