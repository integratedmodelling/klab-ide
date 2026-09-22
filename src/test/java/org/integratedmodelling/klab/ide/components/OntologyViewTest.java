package org.integratedmodelling.klab.ide.components;

import static org.junit.jupiter.api.Assertions.*;
import java.lang.reflect.Proxy;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.function.Supplier;
import javafx.application.Platform;
import org.integratedmodelling.klab.api.knowledge.Worldview;
import org.integratedmodelling.klab.api.knowledge.impl.WorldviewImpl;
import org.integratedmodelling.klab.api.services.Reasoner;
import org.integratedmodelling.klab.ide.KlabIDEController;
import org.integratedmodelling.klab.ide.components.generic.DockableTabPane;
import org.integratedmodelling.klab.ide.pages.BrowsablePage;
import org.junit.jupiter.api.*;

class OntologyViewTest {
  private KlabIDEController previousController;
  private OntologyView view;

  @BeforeAll static void startFx() throws Exception {
    var ready = new CompletableFuture<Void>();
    try { Platform.startup(() -> { Platform.setImplicitExit(false); ready.complete(null); }); }
    catch (IllegalStateException started) { Platform.runLater(() -> ready.complete(null)); }
    ready.get(15, TimeUnit.SECONDS);
  }

  @BeforeEach void controller() throws Exception {
    previousController = KlabIDEController.instance();
    fx(() -> { new KlabIDEController(); return null; });
  }

  @AfterEach void close() throws Exception {
    fx(() -> {
      if (view != null) view.close();
      try {
        var singleton = KlabIDEController.class.getDeclaredField("_this");
        singleton.setAccessible(true); singleton.set(null, previousController);
      } catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
      return null;
    });
  }

  private OntologyView create(Supplier<WorldviewSource.Snapshot> source, boolean monitor) {
    return new OntologyView(source, monitor) {
      @Override protected WorldviewEditor createWorldviewEditor(Worldview worldview, Reasoner reasoner) {
        return new WorldviewEditor(worldview, reasoner) {
          @Override protected void showContent() {} // Test the real outer tab lifecycle independently.
          @Override protected void onVisualize(boolean visible) {}
        };
      }
    };
  }

  private WorldviewSource.Snapshot available() {
    var worldview = new WorldviewImpl(); worldview.setUrn("certificate-worldview");
    var reasoner = (Reasoner) Proxy.newProxyInstance(Reasoner.class.getClassLoader(), new Class<?>[]{Reasoner.class},
        (self, method, args) -> null);
    return new WorldviewSource.Snapshot(reasoner, null, worldview, 1, "content", false);
  }

  @Test void activationOpensAndReselectsExactlyOneNonCloseableWorldviewTab() throws Exception {
    var snapshot = available();
    view = fx(() -> create(() -> snapshot, false));
    fx(() -> { view.show(); return null; });
    await(() -> view.getSelectedEditor() != null);
    var editor = fx(view::getSelectedEditor);
    fx(() -> {
      var tabs = tabs();
      assertEquals(2, tabs.allTabs().size()); // Drawer icon plus the sole worldview.
      assertFalse(tabs.selectedTab().isClosable());
      tabs.getSelectionModel().clearSelection();
      view.show();
      assertSame(editor, view.getSelectedEditor());
      assertEquals(2, tabs.allTabs().size());
      var drawer = new javafx.scene.layout.VBox();
      view.defineBrowser(drawer);
      assertEquals(2, drawer.getChildren().size());
      assertInstanceOf(javafx.scene.control.Label.class, drawer.getChildren().get(1));
      return null;
    });
  }

  @Test void worldviewAppearsOnAvailabilityWithoutOpeningTheDrawerOrSelectingAnything() throws Exception {
    var source = new AtomicReference<WorldviewSource.Snapshot>();
    view = fx(() -> create(source::get, true));
    assertTrue(fx(view::isEmpty));
    source.set(available());
    await(() -> view.getSelectedEditor() != null);
    fx(() -> { assertFalse(tabs().selectedTab().isClosable()); return null; });
  }

  @Test void queuedServiceNotificationsCannotStarveACompletedLoad() throws Exception {
    var started = new CountDownLatch(1);
    var first = new CountDownLatch(1);
    var second = new CountDownLatch(1);
    var calls = new AtomicInteger();
    var snapshot = available();
    view = fx(() -> create(() -> {
      try {
        if (calls.incrementAndGet() == 1) { started.countDown(); first.await(10, TimeUnit.SECONDS); }
        else second.await(10, TimeUnit.SECONDS);
      } catch (InterruptedException error) { Thread.currentThread().interrupt(); }
      return snapshot;
    }, false));
    try {
      fx(() -> { view.refresh(); return null; });
      assertTrue(started.await(10, TimeUnit.SECONDS));
      fx(() -> { view.refresh(); view.refresh(); return null; });
      first.countDown();
      await(() -> view.getSelectedEditor() != null);
      assertEquals(1L, second.getCount()); // The follow-up is still blocked.
    } finally { first.countDown(); second.countDown(); }
  }

  private DockableTabPane tabs() {
    try {
      var field = BrowsablePage.class.getDeclaredField("tabPane"); field.setAccessible(true);
      return (DockableTabPane) field.get(view);
    } catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
  }
  private static void await(Supplier<Boolean> predicate) throws Exception {
    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(12);
    while (System.nanoTime() < deadline) { if (fx(predicate)) return; Thread.sleep(20); }
    fail("Worldview did not open automatically");
  }
  private static <T> T fx(Supplier<T> action) throws Exception {
    var result = new CompletableFuture<T>();
    Platform.runLater(() -> { try { result.complete(action.get()); } catch (Throwable error) { result.completeExceptionally(error); } });
    return result.get(15, TimeUnit.SECONDS);
  }
}
