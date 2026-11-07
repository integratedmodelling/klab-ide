package org.integratedmodelling.klab.ide.components;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import javafx.application.Platform;
import javafx.scene.control.*;
import javafx.stage.Stage;
import javafx.stage.Window;
import org.integratedmodelling.klab.api.scope.UserScope;
import org.integratedmodelling.klab.api.services.ResourcesService;
import org.integratedmodelling.klab.api.services.resources.workflow.*;
import org.integratedmodelling.klab.ide.KlabIDEController;
import org.integratedmodelling.klab.ide.pages.EditorPage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** Delayed service boundary exercised through the production JavaFX hosts and actual buttons. */
class WorkflowProgressTest {
  @BeforeAll static void start() { WorkflowProposalTransitionTest.start(); }
  @AfterEach void closeWindows() throws Exception {
    WorkflowProposalTransitionTest.fx(() -> Window.getWindows().stream().filter(Window::isShowing)
        .toList().forEach(Window::hide));
  }

  private static final class DelayedService {
    final ReviewEditorJourneyFixture fixture = new ReviewEditorJourneyFixture();
    final CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
    final AtomicInteger calls = new AtomicInteger();
    final AtomicInteger definitionReads = new AtomicInteger();
    final ResourcesService service;
    volatile RuntimeException failure;
    volatile CountDownLatch nextRelease;

    DelayedService(String delayedMethod) {
      service = (ResourcesService) Proxy.newProxyInstance(getClass().getClassLoader(),
          new Class<?>[] {ResourcesService.class}, (proxy, method, args) -> {
            if (method.getName().equals(delayedMethod)) {
              assertFalse(Platform.isFxApplicationThread(), "Network work must not block JavaFX");
              var gate = nextRelease == null ? release : nextRelease;
              calls.incrementAndGet(); entered.countDown();
              assertTrue(gate.await(10, TimeUnit.SECONDS), "Test never released the delayed response");
              if (failure != null) throw failure;
            }
            if (method.getName().equals("getWorkflow")) { definitionReads.incrementAndGet(); return fixture.workflow; }
            try { return method.invoke(fixture.service, args); }
            catch (InvocationTargetException error) { throw error.getCause(); }
          });
    }

    WorkflowEditor editor() {
      WorkflowNativeControlsTest.savedEditor(fixture, () -> {}, ignored -> {}).close();
      return new WorkflowEditor(service, fixture.scope, fixture.workflow, fixture.copy(),
          (w, f, s, schema, ro, changed) -> ReviewStageEditor.create(w, f, s, service.getUrl()));
    }
  }

  @Test void saveShowsImmediateProgressCoalescesClicksAndKeepsFieldsOnFailure() throws Exception {
    var delayed = new DelayedService("updateFlowStateDetails");
    delayed.failure = new UnsupportedOperationException("Revision-checked stage details are not supported");
    var ref = new AtomicReference<WorkflowEditor>();
    try {
      WorkflowProposalTransitionTest.fx(() -> {
        var editor = delayed.editor(); ref.set(editor); WorkflowNativeControlsTest.show(editor);
        ReviewEditorJourneyTest.title(editor).setText("Retain my title");
        var save = (Button) editor.lookup("#review-save-details"); save.fire(); save.fire();
        assertBusy(editor, "Saving stage details");
        assertFalse(editor.requestClose(), "Do not discard accepted writes mid-flight");
      });
      assertTrue(delayed.entered.await(5, TimeUnit.SECONDS));
      // This second FX dispatch must complete while the network response is still blocked.
      WorkflowProposalTransitionTest.fx(() -> assertBusy(ref.get(), "Saving stage details"));
      assertEquals(1, delayed.calls.get());
      delayed.release.countDown();
      WorkflowNativeControlsTest.await(() -> !ref.get().isDisabled()
          && labels(ref.get()).contains("method declared by"));
      WorkflowProposalTransitionTest.fx(() -> {
        assertEquals("Retain my title", ReviewEditorJourneyTest.title(ref.get()).getText());
        assertTrue(labels(ref.get()).contains("Your fields are retained"));
        assertEquals(0, delayed.fixture.saves);
        delayed.failure = null;
        ((Button) ref.get().lookup("#review-save-details")).fire();
      });
      WorkflowNativeControlsTest.await(() -> delayed.fixture.saves == 1 && !ref.get().isDisabled());
      WorkflowProposalTransitionTest.fx(() -> {
        assertTrue(labels(ref.get()).contains("Stage details saved."));
        ref.get().close();
      });
    } finally { delayed.release.countDown(); }
  }

  @Test void slowDiscoveryCanBeCancelledWithoutExecutingOrLosingDraft() throws Exception {
    var delayed = new DelayedService("getFlowActions");
    var ref = new AtomicReference<WorkflowEditor>();
    try {
      WorkflowProposalTransitionTest.fx(() -> {
        var editor = delayed.editor(); ref.set(editor); WorkflowNativeControlsTest.show(editor);
        var prepare = (Button) editor.lookup("#workflow-action-prepare-review-draft");
        prepare.fire(); prepare.fire(); assertBusy(editor, "Checking review preparation");
      });
      assertTrue(delayed.entered.await(5, TimeUnit.SECONDS));
      WorkflowProposalTransitionTest.fx(() -> assertBusy(ref.get(), "Checking review preparation"));
      assertEquals(1, delayed.calls.get()); delayed.release.countDown();
      WorkflowNativeControlsTest.await(() -> actionDialog() != null);
      WorkflowProposalTransitionTest.fx(() -> {
        var pane = actionDialog();
        ((Button) pane.lookupButton(ButtonType.CANCEL)).fire();
      });
      WorkflowNativeControlsTest.await(() -> !ref.get().isDisabled());
      WorkflowProposalTransitionTest.fx(() -> {
        assertEquals(0, delayed.fixture.preparations);
        assertEquals("Synthetic saved bootstrap", ReviewEditorJourneyTest.title(ref.get()).getText());
        ref.get().close();
      });
    } finally { delayed.release.countDown(); }
  }

  @Test void saveResponsePreservesNewerLocalFields() throws Exception {
    var delayed = new DelayedService("updateFlowStateDetails");
    var ref = new AtomicReference<WorkflowEditor>();
    try {
      WorkflowProposalTransitionTest.fx(() -> {
        var editor = delayed.editor(); ref.set(editor); WorkflowNativeControlsTest.show(editor);
        ReviewEditorJourneyTest.title(editor).setText("Submitted title");
        ((Button) editor.lookup("#review-save-details")).fire();
      });
      assertTrue(delayed.entered.await(5, TimeUnit.SECONDS));
      WorkflowProposalTransitionTest.fx(() -> {
        // Model/editor callbacks can arrive while controls are locked; do not overwrite them.
        ReviewEditorJourneyTest.title(ref.get()).setText("Newer local title");
      });
      delayed.release.countDown();
      WorkflowNativeControlsTest.await(() -> !ref.get().isDisabled());
      WorkflowProposalTransitionTest.fx(() -> {
        assertEquals("Submitted title", delayed.fixture.saved.getStates().values().iterator().next().getTitle());
        assertEquals("Newer local title", ReviewEditorJourneyTest.title(ref.get()).getText());
        assertTrue(labels(ref.get()).contains("Other local edits remain unsaved"));
        ref.get().close();
      });
    } finally { delayed.release.countDown(); }
  }

  @Test void slowPreparationShowsProgressAndLateCompletionCannotReplaceDisposedEditor() throws Exception {
    var delayed = new DelayedService("executeFlowAction");
    var ref = new AtomicReference<WorkflowEditor>();
    try {
      WorkflowProposalTransitionTest.fx(() -> {
        var editor = delayed.editor(); ref.set(editor); WorkflowNativeControlsTest.show(editor);
        ((Button) editor.lookup("#workflow-action-prepare-review-draft")).fire();
      });
      WorkflowNativeControlsTest.await(() -> actionDialog() != null);
      WorkflowProposalTransitionTest.fx(() -> {
        var pane = actionDialog();
        pane.lookupAll(".text-field").stream().filter(TextField.class::isInstance)
            .map(TextField.class::cast).forEach(field -> field.setText("Synthetic review"));
        var run = pane.getButtonTypes().stream().filter(type -> "Run".equals(type.getText())).findFirst().orElseThrow();
        ((Button) pane.lookupButton(run)).fire();
      });
      assertTrue(delayed.entered.await(5, TimeUnit.SECONDS));
      WorkflowProposalTransitionTest.fx(() -> {
        assertBusy(ref.get(), "Preparing review draft");
        ((Button) ref.get().lookup("#workflow-action-prepare-review-draft")).fire();
        assertEquals(1, delayed.calls.get());
        ref.get().close();
      });
      delayed.release.countDown();
      WorkflowNativeControlsTest.await(() -> delayed.fixture.preparations == 1);
      WorkflowProposalTransitionTest.fx(() -> assertEquals(3, ref.get().getFlow().getRevision(),
          "A late result must not alter a disposed editor"));
    } finally { delayed.release.countDown(); }
  }

  @Test void actualWorkspaceOpenIsVisibleCoalescedCancellableAndPreservesExistingFields() throws Exception {
    var delayed = new DelayedService("getWorkflow");
    var previous = KlabIDEController.instance();
    var hostRef = new AtomicReference<WorkspaceEditor>();
    var pairRequests = new AtomicInteger();
    var reopenGate = new CountDownLatch(1);
    try {
      WorkflowProposalTransitionTest.fx(() -> {
        new KlabIDEController() {
          @Override public UserScope user() { return delayed.fixture.scope; }
          @Override public boolean handleNotification(org.integratedmodelling.klab.api.services.runtime.Notification notice) {
            fail("Unexpected notification: " + notice); return false;
          }
        };
        delayed.editor().close();
        var workspace = new org.integratedmodelling.klab.api.knowledge.organization.impl.WorkspaceImpl();
        workspace.setUrn("synthetic-workspace"); workspace.setProjects(List.of());
        var prepared = new WorkspaceOpening.Prepared(new org.integratedmodelling.klab.modeler.model.NavigableWorkspace(workspace),
            new WorkspaceOpening.Access(Set.of(), Set.of(), List.of()));
        WorkflowUIProvider provider = new WorkflowUIProvider() {
          public List<Workflow> availableWorkflows(org.integratedmodelling.klab.api.knowledge.KlabAsset asset, UserScope scope) { return List.of(); }
          public WorkflowEditor.StageEditor stageEditor(Workflow w, Flow f, Flow.State s, Workflow.StateSchema schema,
              boolean ro, Runnable changed) { return ReviewStageEditor.create(w, f, s, delayed.service.getUrl()); }
        };
        var host = new WorkspaceEditor(delayed.service, prepared, null, provider); hostRef.set(host);
        var stage = new Stage(); stage.setScene(new javafx.scene.Scene(host, 1180, 900)); stage.show();
        invoke(host, "openWorkflow", new Class<?>[] {Flow.class, Workflow.class}, delayed.fixture.copy(), null);
        invoke(host, "openWorkflow", new Class<?>[] {Flow.class, Workflow.class}, delayed.fixture.copy(), null);
        var tabs = tabs(host);
        assertEquals(1, tabs.size());
        assertInstanceOf(WorkflowOpening.class, tabs.values().iterator().next().getContent());
      });
      assertTrue(delayed.entered.await(5, TimeUnit.SECONDS)); assertEquals(1, delayed.calls.get());
      WorkflowProposalTransitionTest.fx(() -> {
        var opening = tabs(hostRef.get()).values().iterator().next().getContent();
        ((Button) opening.lookup("#workflow-cancel-opening")).fire(); assertTrue(tabs(hostRef.get()).isEmpty());
      });
      delayed.release.countDown();
      WorkflowProposalTransitionTest.fx(() -> {
        var host = hostRef.get(); assertTrue(tabs(host).isEmpty());
        delayed.nextRelease = reopenGate;
        invoke(host, "openWorkflow", new Class<?>[] {Flow.class, Workflow.class}, delayed.fixture.copy(), null);
        invoke(host, "openWorkflow", new Class<?>[] {Flow.class, Workflow.class, Runnable.class},
            delayed.fixture.copy(), null, (Runnable) pairRequests::incrementAndGet);
        invoke(host, "openWorkflow", new Class<?>[] {Flow.class, Workflow.class, Runnable.class},
            delayed.fixture.copy(), null, (Runnable) pairRequests::incrementAndGet);
      });
      WorkflowNativeControlsTest.await(() -> delayed.calls.get() == 2);
      assertEquals(0, pairRequests.get(), "Pairing must wait for the delayed editor load");
      reopenGate.countDown();
      WorkflowNativeControlsTest.await(() -> tabs(hostRef.get()).values().stream()
          .anyMatch(tab -> tab.getContent() instanceof WorkflowEditor));
      WorkflowProposalTransitionTest.fx(() -> {
        var host = hostRef.get();
        host.applyCss(); host.layout();
        assertEquals(1, pairRequests.get(), "Later side-by-side intent must run once after a coalesced open");
        var editor = (WorkflowEditor) tabs(host).values().iterator().next().getContent();
        ReviewEditorJourneyTest.title(editor).setText("Unsaved title survives reopen");
        invoke(host, "openWorkflow", new Class<?>[] {Flow.class, Workflow.class}, delayed.fixture.copy(), null);
        assertSame(editor, tabs(host).values().iterator().next().getContent());
        assertEquals("Unsaved title survives reopen", ReviewEditorJourneyTest.title(editor).getText());
        assertEquals(2, delayed.calls.get(), "Already open means no additional service request");
        // The tab began with a placeholder. Its close handler must consult the installed editor.
        var tab = tabs(host).values().iterator().next();
        WorkflowProposalTransitionTest.answer(ButtonType.CANCEL);
        var event = new javafx.event.Event(Tab.TAB_CLOSE_REQUEST_EVENT);
        tab.getOnCloseRequest().handle(event);
        assertTrue(event.isConsumed(), "Cancelling dirty-close must retain the installed editor");
        // A workflow-hosted side-by-side view stores its original editor in pairedContents.
        // Closing that SplitPane host must apply the same dirty and in-flight guards.
        try {
          var field = EditorPage.class.getDeclaredField("pairedContents"); field.setAccessible(true);
          @SuppressWarnings("unchecked") var paired = (Map<Tab, javafx.scene.Node>) field.get(host);
          paired.put(tab, editor);
          tab.setContent(new SplitPane(editor, new Label("Synthetic paired source")));
          WorkflowProposalTransitionTest.answer(ButtonType.CANCEL);
          var dirtyClose = new javafx.event.Event(Tab.TAB_CLOSE_REQUEST_EVENT);
          tab.getOnCloseRequest().handle(dirtyClose);
          assertTrue(dirtyClose.isConsumed(), "Paired workflow close must preserve unsaved details");
          ((Button) editor.lookup("#review-save-details")).fire();
          var busyClose = new javafx.event.Event(Tab.TAB_CLOSE_REQUEST_EVENT);
          tab.getOnCloseRequest().handle(busyClose);
          assertTrue(busyClose.isConsumed(), "Paired workflow close must not bypass an accepted save");
          ((SplitPane) tab.getContent()).getItems().clear();
          paired.remove(tab); tab.setContent(editor);
        } catch (ReflectiveOperationException error) { throw new RuntimeException(error); }
      });
    } finally {
      delayed.release.countDown();
      reopenGate.countDown();
      WorkflowProposalTransitionTest.fx(() -> {
        if (hostRef.get() != null) hostRef.get().close();
        try { var singleton = KlabIDEController.class.getDeclaredField("_this"); singleton.setAccessible(true); singleton.set(null, previous); }
        catch (ReflectiveOperationException error) { throw new RuntimeException(error); }
      });
    }
  }

  @Test void openingFailureOffersRetryAndClosingSuppressesTheRetryResponse() throws Exception {
    var gate = new CountDownLatch(1);
    var calls = new AtomicInteger();
    var opened = new AtomicInteger();
    var ref = new AtomicReference<WorkflowOpening>();
    try {
      WorkflowProposalTransitionTest.fx(() -> {
        var view = new WorkflowOpening(() -> {}); ref.set(view);
        view.start(() -> {
          assertFalse(Platform.isFxApplicationThread());
          if (calls.incrementAndGet() == 1) throw new IllegalStateException("Synthetic unavailable definition");
          try { assertTrue(gate.await(10, TimeUnit.SECONDS)); }
          catch (InterruptedException error) { throw new RuntimeException(error); }
          return ReviewEditorJourneyFixture.definition("0.4");
        }, ignored -> opened.incrementAndGet());
      });
      WorkflowNativeControlsTest.await(() -> ref.get().lookup("#workflow-retry-opening").isVisible());
      WorkflowProposalTransitionTest.fx(() -> {
        assertTrue(((Label) ref.get().lookup("#workflow-opening-status")).getText().contains("Synthetic unavailable"));
        ((Button) ref.get().lookup("#workflow-retry-opening")).fire();
        ref.get().close();
      });
      gate.countDown();
      WorkflowProposalTransitionTest.fx(() -> assertEquals(0, opened.get()));
    } finally { gate.countDown(); }
  }

  @Test void actualWorkspaceMenuLoadsOffThreadWithoutFetchingEveryDefinition() throws Exception {
    var delayed = new DelayedService("getFlows");
    var previous = KlabIDEController.instance();
    var hostRef = new AtomicReference<WorkspaceEditor>();
    var menuRef = new AtomicReference<ContextMenu>();
    try {
      WorkflowProposalTransitionTest.fx(() -> {
        new KlabIDEController() {
          @Override public UserScope user() { return delayed.fixture.scope; }
          @Override public boolean handleNotification(org.integratedmodelling.klab.api.services.runtime.Notification notice) {
            fail("Unexpected notification: " + notice); return false;
          }
        };
        delayed.editor().close();
        var workspace = new org.integratedmodelling.klab.api.knowledge.organization.impl.WorkspaceImpl();
        workspace.setUrn(delayed.fixture.saved.getAssetUrn()); workspace.setProjects(List.of());
        var prepared = new WorkspaceOpening.Prepared(new org.integratedmodelling.klab.modeler.model.NavigableWorkspace(workspace),
            new WorkspaceOpening.Access(Set.of(), Set.of(), List.of()));
        var host = new WorkspaceEditor(delayed.service, prepared, null, WorkflowUIProvider.NONE); hostRef.set(host);
        var menu = new ContextMenu(); menuRef.set(menu);
        invoke(host, "setupWorkflowMenu", new Class<?>[] {ContextMenu.class,
            org.integratedmodelling.klab.api.view.modeler.navigation.NavigableAsset.class}, menu, host.getEditedAsset());
        assertEquals("Loading workflows...", menu.getItems().getFirst().getText());
      });
      assertTrue(delayed.entered.await(5, TimeUnit.SECONDS));
      WorkflowProposalTransitionTest.fx(() -> assertTrue(menuRef.get().getItems().getFirst().isDisable()));
      delayed.release.countDown();
      WorkflowNativeControlsTest.await(() -> menuRef.get().getItems().stream().anyMatch(item -> "Open flows".equals(item.getText())));
      assertEquals(0, delayed.definitionReads.get());
    } finally {
      delayed.release.countDown();
      WorkflowProposalTransitionTest.fx(() -> {
        if (hostRef.get() != null) hostRef.get().close();
        try { var singleton = KlabIDEController.class.getDeclaredField("_this"); singleton.setAccessible(true); singleton.set(null, previous); }
        catch (ReflectiveOperationException error) { throw new RuntimeException(error); }
      });
    }
  }

  private static void assertBusy(WorkflowEditor editor, String message) {
    assertTrue(editor.isDisabled());
    assertTrue(editor.lookup("#workflow-operation-status").isVisible());
    assertTrue(((Label) editor.lookup("#workflow-operation-message")).getText().contains(message));
  }
  private static String labels(WorkflowEditor editor) {
    return editor.lookupAll(".label").stream().filter(Label.class::isInstance).map(Label.class::cast)
        .map(Label::getText).reduce("", (a, b) -> a + " " + b);
  }
  private static DialogPane actionDialog() {
    return Window.getWindows().stream().filter(Window::isShowing).map(w -> w.getScene().getRoot())
        .filter(DialogPane.class::isInstance).map(DialogPane.class::cast)
        .filter(pane -> pane.getButtonTypes().stream().anyMatch(type -> "Run".equals(type.getText()))).findFirst().orElse(null);
  }
  @SuppressWarnings("unchecked") private static Map<String, Tab> tabs(WorkspaceEditor host) {
    try { var field = EditorPage.class.getDeclaredField("auxiliaryEditors"); field.setAccessible(true); return (Map<String, Tab>) field.get(host); }
    catch (ReflectiveOperationException error) { throw new RuntimeException(error); }
  }
  private static void invoke(WorkspaceEditor host, String name, Class<?>[] types, Object... values) {
    try { var method = WorkspaceEditor.class.getDeclaredMethod(name, types); method.setAccessible(true); method.invoke(host, values); }
    catch (ReflectiveOperationException error) { throw new RuntimeException(error); }
  }
}
