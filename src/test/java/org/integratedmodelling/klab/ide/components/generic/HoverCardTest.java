package org.integratedmodelling.klab.ide.components.generic;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.application.Application;
import javafx.application.HostServices;
import javafx.event.EventType;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import javafx.util.Duration;
import org.integratedmodelling.common.knowledge.ConceptImpl;
import org.integratedmodelling.klab.api.data.Metadata;
import org.integratedmodelling.klab.api.knowledge.SemanticType;
import org.integratedmodelling.klab.ide.components.cards.ObservableCard;
import org.integratedmodelling.klab.ide.utils.AppContext;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class HoverCardTest {
  private record Fixture(Stage stage, Label anchor, HoverCard popup) {}

  @BeforeAll
  static void startFx() throws Exception {
    var ready = new CompletableFuture<Void>();
    try { Platform.startup(() -> { Platform.setImplicitExit(false); ready.complete(null); }); }
    catch (IllegalStateException started) { Platform.runLater(() -> ready.complete(null)); }
    ready.get(15, TimeUnit.SECONDS);
    fx(() -> {
      Application.setUserAgentStylesheet(new atlantafx.base.theme.PrimerLight().getUserAgentStylesheet());
      return null;
    });
  }

  @Test
  void pointerCanCrossIntoCardAndUseLinksAndScrolling() throws Exception {
    var fixture = fixture();
    try {
      open(fixture);
      fx(() -> {
        fire(fixture.anchor(), MouseEvent.MOUSE_EXITED);
        fire(fixture.popup().getContent().getFirst(), MouseEvent.MOUSE_ENTERED);
        return null;
      });
      pause(300); // Exceed the exit grace period while the pointer is over the card.
      fx(() -> {
        assertTrue(fixture.popup().isShowing());
        var content = fixture.popup().getContent().getFirst();
        var scroll = (ScrollPane) content.lookup(".scroll-pane");
        var link = (Hyperlink) content.lookup(".hyperlink");
        assertNotNull(link);
        var browser = mock(HostServices.class);
        var previousBrowser = AppContext.getHostServices();
        AppContext.setHostServices(browser);
        try {
          fire(link, MouseEvent.MOUSE_PRESSED);
          link.fire();
          verify(browser).showDocument("https://example.com/");
        } finally {
          AppContext.setHostServices(previousBrowser);
        }
        assertTrue(fixture.popup().isShowing(), "Clicking inside must not dismiss the card");
        assertTrue(scroll.getContent().getBoundsInParent().getHeight()
            > scroll.getViewportBounds().getHeight());
        scroll.setVvalue(1);
        assertEquals(1, scroll.getVvalue());
        fire(content, MouseEvent.MOUSE_EXITED);
        return null;
      });
      pause(300);
      assertFalse(fx(fixture.popup()::isShowing));
    } finally { close(fixture); }
  }

  @Test
  void returningToAnchorCancelsDismissalAndHideCancelsPendingOpening() throws Exception {
    var fixture = fixture();
    try {
      open(fixture);
      fx(() -> {
        fire(fixture.anchor(), MouseEvent.MOUSE_EXITED);
        fire(fixture.anchor(), MouseEvent.MOUSE_ENTERED);
        return null;
      });
      pause(300);
      assertTrue(fx(fixture.popup()::isShowing));
      fx(() -> {
        fixture.popup().hide();
        fire(fixture.anchor(), MouseEvent.MOUSE_ENTERED);
        fixture.popup().hide();
        return null;
      });
      pause(300);
      assertFalse(fx(fixture.popup()::isShowing));
    } finally { close(fixture); }
  }

  @Test
  void escapeAndOutsidePressDismissAndOwnerCloseCleansUp() throws Exception {
    var fixture = fixture();
    try {
      open(fixture);
      fx(() -> {
        fixture.stage().getScene().getRoot().fireEvent(new KeyEvent(
            KeyEvent.KEY_PRESSED, "", "", KeyCode.ESCAPE, false, false, false, false));
        assertFalse(fixture.popup().isShowing());
        return null;
      });
      open(fixture);
      fx(() -> {
        fire(fixture.stage().getScene().getRoot(), MouseEvent.MOUSE_PRESSED);
        assertFalse(fixture.popup().isShowing());
        return null;
      });
      open(fixture);
      fx(() -> { fixture.stage().close(); return null; });
      assertFalse(fx(fixture.popup()::isShowing));
    } finally { close(fixture); }
  }

  private static Fixture fixture() throws Exception {
    return fx(() -> {
      var anchor = new Label("Concept");
      var stage = new Stage();
      stage.setScene(new Scene(new StackPane(anchor), 600, 450));
      stage.show();
      var popup = new HoverCard(Duration.millis(30), Duration.millis(150));
      popup.install(anchor, () -> {
        var concept = new ConceptImpl();
        concept.setUrn("test:Documented");
        concept.getType().add(SemanticType.SUBJECT);
        concept.getMetadata().put(Metadata.DC_COMMENT,
            "[Documentation](https://example.com/)\n\n" + "A long **description**. ".repeat(200));
        return new ObservableCard(concept, List.of()).tooltipContent(360, 240);
      });
      return new Fixture(stage, anchor, popup);
    });
  }

  private static void open(Fixture fixture) throws Exception {
    fx(() -> { fire(fixture.anchor(), MouseEvent.MOUSE_ENTERED); return null; });
    pause(200);
    assertTrue(fx(fixture.popup()::isShowing));
  }

  private static void close(Fixture fixture) throws Exception {
    fx(() -> { fixture.popup().hide(); fixture.stage().close(); return null; });
  }

  private static void fire(Node node, EventType<MouseEvent> type) {
    node.fireEvent(new MouseEvent(type, 0, 0, 0, 0, MouseButton.PRIMARY, 1,
        false, false, false, false, false, false, false, false, false, true, null));
  }

  private static void pause(double millis) throws Exception {
    var done = new CompletableFuture<Void>();
    fx(() -> {
      var delay = new PauseTransition(Duration.millis(millis));
      delay.setOnFinished(event -> done.complete(null)); delay.play(); return null;
    });
    done.get(15, TimeUnit.SECONDS);
  }

  private static <T> T fx(Supplier<T> task) throws Exception {
    var result = new CompletableFuture<T>();
    Platform.runLater(() -> {
      try { result.complete(task.get()); } catch (Throwable error) { result.completeExceptionally(error); }
    });
    return result.get(15, TimeUnit.SECONDS);
  }
}
