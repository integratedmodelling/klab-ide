package org.integratedmodelling.klab.ide.components.cards;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import javafx.application.Platform;
import javafx.css.PseudoClass;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.layout.VBox;
import org.integratedmodelling.klab.api.collections.Parameters;
import org.integratedmodelling.klab.api.identities.UserIdentity;
import org.integratedmodelling.klab.api.scope.UserScope;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

class UserViewComponentTest {
  @TempDir Path directory;

  @BeforeAll static void startFx() throws Exception {
    var ready = new CountDownLatch(1);
    try { Platform.startup(ready::countDown); }
    catch (IllegalStateException started) { Platform.runLater(ready::countDown); }
    assertTrue(ready.await(5, TimeUnit.SECONDS));
    Platform.setImplicitExit(false);
  }

  static <T> T fx(Callable<T> work) throws Exception {
    var result = new CompletableFuture<T>();
    Platform.runLater(() -> {
      try { result.complete(work.call()); }
      catch (Throwable e) { result.completeExceptionally(e); }
    });
    return result.get(5, TimeUnit.SECONDS);
  }

  UserScope user(String name) {
    var scope = mock(UserScope.class);
    var identity = mock(UserIdentity.class);
    when(scope.getUser()).thenReturn(identity);
    when(identity.getUsername()).thenReturn(name);
    when(identity.getData()).thenReturn(Parameters.create());
    when(identity.getGroups()).thenReturn(Set.of());
    when(identity.isAuthenticated()).thenReturn(true);
    return scope;
  }

  static VBox zone(UserViewComponent view) {
    return (VBox) view.lookup(".user-certificate-drop-zone");
  }

  static String text(UserViewComponent view) {
    return ((Label) view.lookup(".user-certificate-drop-zone-label")).getText();
  }

  @Test void acceptsOneCertificateIncludingRenamedDownloads() throws Exception {
    var cert = Files.writeString(directory.resolve("klab.cert"), "data").toFile();
    var renamed = Files.writeString(directory.resolve("klab.cert (2)"), "data").toFile();
    var other = Files.writeString(directory.resolve("node.cert"), "data").toFile();
    assertTrue(UserViewComponent.acceptsCertificate(List.of(cert)));
    assertTrue(UserViewComponent.acceptsCertificate(List.of(renamed)));
    assertFalse(UserViewComponent.acceptsCertificate(List.of(other)));
    assertFalse(UserViewComponent.acceptsCertificate(List.of(cert, renamed)));
    assertFalse(UserViewComponent.acceptsCertificate(List.of(directory.toFile())));
    assertFalse(UserViewComponent.acceptsCertificate(List.of()));
  }

  @Test void showsProgressThenRefreshesIdentityAndCompletionMessage() throws Exception {
    var old = user("old"); var next = user("new");
    var pending = new CompletableFuture<UserScope>();
    var view = fx(() -> new UserViewComponent(old, () -> null, f -> pending));
    fx(() -> {
      view.installCertificate(directory.resolve("klab.cert").toFile());
      assertTrue(zone(view).getPseudoClassStates().contains(PseudoClass.getPseudoClass("busy")));
      assertTrue(zone(view).getChildren().stream().anyMatch(ProgressIndicator.class::isInstance));
      assertTrue(text(view).contains("authenticating"));
      return null;
    });
    pending.complete(next);
    fx(() -> {
      assertEquals("Certificate installed. Authenticated as new", text(view));
      assertFalse(zone(view).getPseudoClassStates().contains(PseudoClass.getPseudoClass("busy")));
      assertTrue(view.lookupAll(".label").stream().anyMatch(n -> n instanceof Label l && "new".equals(l.getText())));
      return null;
    });
  }

  @Test void failureRemovesProgressAndAllowsRetry() throws Exception {
    var scope = user("user");
    var pending = new CompletableFuture<UserScope>();
    var view = fx(() -> new UserViewComponent(scope, () -> null, f -> pending));
    fx(() -> { view.installCertificate(directory.resolve("klab.cert").toFile()); return null; });
    pending.completeExceptionally(new IllegalStateException("Stop all local services"));
    fx(() -> {
      assertTrue(text(view).contains("Stop all local services"));
      assertTrue(zone(view).getPseudoClassStates().contains(PseudoClass.getPseudoClass("error")));
      assertFalse(zone(view).getChildren().stream().anyMatch(ProgressIndicator.class::isInstance));
      assertFalse(zone(view).getPseudoClassStates().contains(PseudoClass.getPseudoClass("busy")));
      return null;
    });
  }
}
