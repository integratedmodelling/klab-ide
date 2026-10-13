package org.integratedmodelling.klab.ide.pages;

import static org.junit.jupiter.api.Assertions.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.TreeView;
import javafx.scene.layout.VBox;
import org.integratedmodelling.klab.ide.KlabIDEController;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class BrowsablePageOpeningTest {
  @BeforeAll static void startFx() throws Exception {
    var ready = new CompletableFuture<Void>();
    try { Platform.startup(() -> { Platform.setImplicitExit(false); ready.complete(null); }); }
    catch (IllegalStateException started) { Platform.runLater(() -> ready.complete(null)); }
    ready.get(15, TimeUnit.SECONDS);
  }

  private static class Editor extends EditorPage<String, String> {
    int shown;
    int disposed;
    Editor() { super("source"); }
    @Override protected void showContent() { shown++; }
    @Override public boolean isAffectedBy(org.integratedmodelling.klab.ide.IDEContextScope scope) { return false; }
    @Override public void closeDigitalTwin(org.integratedmodelling.klab.ide.IDEContextScope scope) {}
    @Override protected void onVisualize(boolean visible) {}
    @Override protected Node createEditor(String asset) { return null; }
    @Override protected void onSingleClickItemSelection(String value) {}
    @Override protected void onDoubleClickItemSelection(String value) {}
    @Override protected TreeView<String> createContentTree() { return null; }
    @Override public void close() { disposed++; super.close(); }
  }

  private static class Browser extends BrowsablePage<Editor, String> {
    int attached;
    int discarded;
    @Override protected void assetEditorSelected(Editor editor) {}
    @Override protected void assetEditorClosed(Editor editor) {}
    @Override protected void defineBrowser(VBox box) {}
    @Override public String getName() { return "Test browser"; }
    @Override public Parent getView() { return this; }
    @Override public void reset() {}
    void opening(Editor editor, AtomicBoolean current) {
      addEditor(editor, "source", null, true, current::get,
          () -> attached++, () -> { discarded++; editor.close(); });
    }
  }

  @Test void cancellationBetweenRetrievalAndDeferredTabAttachmentCannotResurrectAClosedWorkspace() throws Exception {
    var previous = KlabIDEController.instance();
    try {
      var objects = fx(() -> {
        new KlabIDEController();
        var browser = new Browser(); var editor = new Editor(); var current = new AtomicBoolean(true);
        browser.opening(editor, current);
        current.set(false); // The view closed after retrieval but before its queued tab attach.
        return new Object[] {browser, editor};
      });
      fx(() -> {
        var browser = (Browser) objects[0]; var editor = (Editor) objects[1];
        assertEquals(0, browser.attached); assertEquals(1, browser.discarded);
        assertEquals(0, editor.shown); assertEquals(1, editor.disposed);
        return null;
      });
    } finally { restore(previous); }
  }

  @Test void currentWorkspaceAttachesAndShowsExactlyOnce() throws Exception {
    var previous = KlabIDEController.instance();
    try {
      var objects = fx(() -> {
        new KlabIDEController(); var browser = new Browser(); var editor = new Editor();
        browser.opening(editor, new AtomicBoolean(true));
        return new Object[] {browser, editor};
      });
      fx(() -> {
        var browser = (Browser) objects[0]; var editor = (Editor) objects[1];
        assertEquals(1, browser.attached); assertEquals(0, browser.discarded);
        assertEquals(1, editor.shown); assertEquals(0, editor.disposed);
        editor.close(); return null;
      });
    } finally { restore(previous); }
  }

  private static void restore(KlabIDEController previous) throws Exception {
    fx(() -> { var field = KlabIDEController.class.getDeclaredField("_this");
      field.setAccessible(true); field.set(null, previous); return null; });
  }

  private static <T> T fx(Callable<T> action) throws Exception {
    var result = new CompletableFuture<T>();
    Platform.runLater(() -> { try { result.complete(action.call()); }
      catch (Throwable error) { result.completeExceptionally(error); } });
    return result.get(10, TimeUnit.SECONDS);
  }
}
