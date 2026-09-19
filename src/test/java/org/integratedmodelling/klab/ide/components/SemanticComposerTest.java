package org.integratedmodelling.klab.ide.components;

import static org.junit.jupiter.api.Assertions.*;
import java.lang.reflect.Proxy;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Supplier;
import javafx.application.Platform;
import javafx.event.Event;
import javafx.scene.control.*;
import javafx.scene.input.*;
import javafx.scene.layout.HBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Text;
import org.integratedmodelling.klab.api.lang.kim.style.KimStyle;
import org.integratedmodelling.klab.api.services.Reasoner;
import org.integratedmodelling.klab.api.services.reasoner.objects.*;
import org.integratedmodelling.klab.ide.Theme;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class SemanticComposerTest {
  @BeforeAll static void startFx() throws Exception {
    var ready = new CompletableFuture<Void>();
    try { Platform.startup(() -> { Platform.setImplicitExit(false); ready.complete(null); }); }
    catch (IllegalStateException started) { Platform.runLater(() -> ready.complete(null)); }
    ready.get(15, TimeUnit.SECONDS);
  }

  @Test void selectionDialogReturnsObservableAndNullOnCancelOrWindowClose() throws Exception {
    var owner = fx(() -> {
      var stage = new javafx.stage.Stage();
      stage.setScene(new javafx.scene.Scene(new javafx.scene.layout.VBox(), 200, 100)); stage.show(); return stage;
    });
    var concept = new org.integratedmodelling.common.knowledge.ConceptImpl();
    concept.setUrn("test:Tree");
    concept.getType().add(org.integratedmodelling.klab.api.knowledge.SemanticType.SUBJECT);
    var observable = new org.integratedmodelling.common.knowledge.ObservableImpl();
    observable.setSemantics(concept); observable.setUrn(concept.getUrn());
    var reasoner = (Reasoner) Proxy.newProxyInstance(Reasoner.class.getClassLoader(), new Class<?>[]{Reasoner.class}, (p, method, args) -> {
      if (!method.getName().equals("semanticSearch")) return null;
      var request = (SemanticSearchRequest) args[0];
      var response = new SemanticSearchResponse(42, request.getRequestId());
      response.setObservable(observable); return response;
    });
    try {
      for (String action : List.of("Continue", "Cancel", "window-close")) {
        var result = fx(() -> ObservableComposerDialog.show(owner.getScene().getRoot(), () -> reasoner));
        var stage = fx(() -> (javafx.stage.Stage) javafx.stage.Window.getWindows().stream()
            .filter(w -> w != owner && w instanceof javafx.stage.Stage st && st.getTitle().equals("Compose observable"))
            .findFirst().orElseThrow());
        var composer = fx(() -> (SemanticComposer) stage.getScene().getRoot());
        ready(composer);
        fx(() -> { if (action.equals("window-close")) stage.close(); else button(composer, action).fire(); return null; });
        assertSame(action.equals("Continue") ? observable : null, result.get(10, TimeUnit.SECONDS));
      }
    } finally { fx(() -> { owner.close(); return null; }); }
  }

  @Test void observableCardShowsColoredClausesWithDistinctOriginIcons() throws Exception {
    fx(() -> {
      var concept = new org.integratedmodelling.common.knowledge.ConceptImpl();
      concept.setUrn("test:Tree");
      concept.getType().addAll(List.of(org.integratedmodelling.klab.api.knowledge.SemanticType.SUBJECT,
          org.integratedmodelling.klab.api.knowledge.SemanticType.OBSERVABLE));
      var direct = new SemanticClauseRestriction(org.integratedmodelling.klab.api.knowledge.SemanticRole.INHERENT, concept, false);
      var inherited = new SemanticClauseRestriction(org.integratedmodelling.klab.api.knowledge.SemanticRole.GOAL, concept, true);
      var card = new org.integratedmodelling.klab.ide.components.cards.ObservableCard(concept, List.of(direct, inherited));
      var content = (javafx.scene.layout.VBox) card.getCenter();
      assertInstanceOf(javafx.scene.text.TextFlow.class, content.getChildren().getFirst());
      var directRow = (HBox) content.getChildren().get(2);
      var inheritedRow = (HBox) content.getChildren().get(3);
      assertEquals("Direct restriction", ((Label) directRow.getChildren().getFirst()).getTooltip().getText());
      assertEquals("Inherited restriction", ((Label) inheritedRow.getChildren().getFirst()).getTooltip().getText());
      var flow = (javafx.scene.text.TextFlow) directRow.getChildren().get(1);
      assertTrue(flow.getChildren().stream().anyMatch(n -> n instanceof Text t && t.getText().equals("test:Tree")));
      return null;
    });
  }

  @Test void selectedObservableSeedsItsCardAndFirstConcept() throws Exception {
    var owner = fx(() -> {
      var stage = new javafx.stage.Stage();
      stage.setScene(new javafx.scene.Scene(new javafx.scene.layout.VBox(), 200, 100));
      stage.show();
      return stage;
    });
    var concept = new org.integratedmodelling.common.knowledge.ConceptImpl();
    concept.setUrn("test:Tree");
    concept.getType().add(org.integratedmodelling.klab.api.knowledge.SemanticType.SUBJECT);
    var observable = new org.integratedmodelling.common.knowledge.ObservableImpl();
    observable.setSemantics(concept);
    observable.setUrn("each test:Tree within test:Forest");
    var reasoner = (Reasoner) Proxy.newProxyInstance(
        Reasoner.class.getClassLoader(), new Class<?>[] {Reasoner.class}, (p, method, args) -> {
          if (method.getName().equals("resolveObservable")) return observable;
          if (!method.getName().equals("semanticSearch")) return null;
          var request = (SemanticSearchRequest) args[0];
          return new SemanticSearchResponse(42, request.getRequestId());
        });
    try {
      var context = new org.integratedmodelling.klabeditor.MonacoEditorView
          .ObservableCompositionContext(observable.getUrn(), "test:Forest");
      var result = fx(() -> ObservableComposerDialog.show(
          owner.getScene().getRoot(), () -> reasoner, context));
      javafx.stage.Stage stage = null;
      long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
      while (stage == null) {
        stage = fx(() -> (javafx.stage.Stage) javafx.stage.Window.getWindows().stream()
            .filter(w -> w != owner && w instanceof javafx.stage.Stage st
                && st.getTitle().equals("Compose observable"))
            .findFirst().orElse(null));
        if (System.nanoTime() > deadline) fail("Composer dialog did not open");
        if (stage == null) Thread.sleep(10);
      }
      var composerStage = stage;
      var composer = fx(() -> (SemanticComposer) composerStage.getScene().getRoot());
      ready(composer);
      fx(() -> {
        assertEquals("test:Tree", query(composer).getText());
        assertTrue(composer.getChildren().stream()
            .filter(javafx.scene.layout.VBox.class::isInstance)
            .map(javafx.scene.layout.VBox.class::cast)
            .flatMap(box -> box.getChildren().stream())
            .anyMatch(org.integratedmodelling.klab.ide.components.cards.ObservableCard.class::isInstance));
        composerStage.close();
        return null;
      });
      assertNull(result.get(10, TimeUnit.SECONDS));
    } finally {
      fx(() -> { owner.close(); return null; });
    }
  }

  @Test void typingAParenthesisDoesNotEnterSearchTextOrRepeatAndBackspaceUndoes() throws Exception {
    var fake = new FakeSearch();
    var composer = fx(() -> new SemanticComposer(() -> fake.reasoner(), o -> CompletableFuture.completedFuture(null), () -> {}));
    try {
      ready(composer);
      fx(() -> { typed(query(composer), "("); typed(query(composer), "("); return null; });
      ready(composer);
      fx(() -> {
        assertEquals("", query(composer).getText());
        assertTrue(button(composer, "(").isDisabled());
        release(query(composer), KeyCode.DIGIT9);
        typed(query(composer), "(");
        return null;
      });
      assertEquals(1, fake.count(SemanticSearchRequest.Mode.OPEN_SCOPE));
      fx(() -> { press(query(composer), KeyCode.BACK_SPACE); return null; });
      ready(composer);
      fx(() -> {
        press(query(composer), KeyCode.BACK_SPACE);
        release(query(composer), KeyCode.BACK_SPACE);
        assertFalse(button(composer, "(").isDisabled());
        return null;
      });
      assertEquals(1, fake.count(SemanticSearchRequest.Mode.UNDO));
    } finally { fx(() -> { composer.close(); return null; }); }
  }

  @Test void heldClosingKeyDoesNotDuplicateButSeparatePressesCanCloseNestedGroups() throws Exception {
    var fake = new FakeSearch();
    fake.code.addAll(List.of(StyledKimToken.create("("), token("test:Tree"), token("of"),
        StyledKimToken.create("("), token("test:Tree")));
    var composer = fx(() -> new SemanticComposer(() -> fake.reasoner(), o -> CompletableFuture.completedFuture(null), () -> {}));
    try {
      ready(composer);
      fx(() -> { typed(query(composer), ")"); return null; });
      ready(composer);
      fx(() -> { typed(query(composer), ")"); return null; });
      assertEquals(1, fake.count(SemanticSearchRequest.Mode.CLOSE_SCOPE));
      fx(() -> { release(query(composer), KeyCode.DIGIT0); typed(query(composer), ")"); return null; });
      ready(composer);
      assertEquals(2, fake.count(SemanticSearchRequest.Mode.CLOSE_SCOPE));
      fx(() -> { assertTrue(button(composer, ")").isDisabled()); press(query(composer), KeyCode.BACK_SPACE); return null; });
      ready(composer);
      fx(() -> { press(query(composer), KeyCode.BACK_SPACE); return null; });
      assertEquals(1, fake.count(SemanticSearchRequest.Mode.UNDO));
      fx(() -> { release(query(composer), KeyCode.BACK_SPACE); press(query(composer), KeyCode.BACK_SPACE); return null; });
      ready(composer);
      assertEquals(2, fake.count(SemanticSearchRequest.Mode.UNDO));
      fx(() -> {
        release(query(composer), KeyCode.BACK_SPACE);
        query(composer).setText("tree");
        press(query(composer), KeyCode.BACK_SPACE);
        assertEquals(2, fake.count(SemanticSearchRequest.Mode.UNDO));
        return null;
      });
    } finally { fx(() -> { composer.close(); return null; }); }
  }

  @Test void parenthesesInLiteralInputRemainTextAndThemePreservesSemanticStyles() throws Exception {
    var fake = new FakeSearch(); fake.value = true;
    var composer = fx(() -> new SemanticComposer(() -> fake.reasoner(), o -> CompletableFuture.completedFuture(null), () -> {}));
    try {
      ready(composer);
      fx(() -> {
        typed(query(composer), "(");
        assertEquals(0, fake.count(SemanticSearchRequest.Mode.OPEN_SCOPE));
        var concept = token("test:Tree");
        concept.setColor(KimStyle.Color.SUBJECT); concept.setFont(KimStyle.FontStyle.ITALIC);
        var literal = token("[b]literal[/b]");
        var flow = Theme.semanticExpression(List.of(StyledKimToken.create("("), concept,
            StyledKimToken.create(")"), literal));
        var text = (Text) flow.getChildren().get(1);
        assertEquals(Color.rgb(153, 76, 0), text.getFill());
        assertTrue(text.getStyle().contains("italic"));
        assertEquals("(test:Tree) [b]literal[/b]", flow.getChildren().stream()
            .map(node -> ((Text) node).getText()).reduce("", String::concat));
        return null;
      });
    } finally { fx(() -> { composer.close(); return null; }); }
  }

  private static StyledKimToken token(String value) {
    var token = new StyledKimToken(); token.setValue(value);
    token.setNeedsWhitespaceBefore(true); token.setNeedsWhitespaceAfter(true); return token;
  }
  private static TextField query(SemanticComposer composer) {
    return (TextField) composer.getChildren().stream().filter(TextField.class::isInstance).findFirst().orElseThrow();
  }
  private static HBox actions(SemanticComposer composer) { return (HBox) composer.getChildren().getLast(); }
  private static Button button(SemanticComposer composer, String label) {
    return (Button) actions(composer).getChildren().stream()
        .filter(n -> n instanceof Button b && b.getText().equals(label)).findFirst().orElseThrow();
  }
  private static void ready(SemanticComposer composer) throws Exception {
    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
    while (fx(() -> actions(composer).getChildren().stream()
        .anyMatch(n -> n instanceof ProgressIndicator && n.isVisible()))) {
      if (System.nanoTime() > deadline) fail("Composer did not finish its request");
      Thread.sleep(10);
    }
  }
  private static void typed(TextField target, String value) {
    Event.fireEvent(target, new KeyEvent(KeyEvent.KEY_TYPED, value, "", KeyCode.UNDEFINED, false, false, false, false));
  }
  private static void press(TextField target, KeyCode code) {
    Event.fireEvent(target, new KeyEvent(KeyEvent.KEY_PRESSED, "", "", code, false, false, false, false));
  }
  private static void release(TextField target, KeyCode code) {
    Event.fireEvent(target, new KeyEvent(KeyEvent.KEY_RELEASED, "", "", code, false, false, false, false));
  }
  private static <T> T fx(Supplier<T> action) throws Exception {
    var result = new CompletableFuture<T>();
    Platform.runLater(() -> { try { result.complete(action.get()); } catch (Throwable t) { result.completeExceptionally(t); } });
    return result.get(15, TimeUnit.SECONDS);
  }

  private static class FakeSearch {
    final List<StyledKimToken> code = new ArrayList<>();
    final List<SemanticSearchRequest.Mode> calls = new CopyOnWriteArrayList<>();
    boolean value;
    long count(SemanticSearchRequest.Mode mode) { return calls.stream().filter(m -> m == mode).count(); }
    Reasoner reasoner() {
      return (Reasoner) Proxy.newProxyInstance(Reasoner.class.getClassLoader(), new Class<?>[]{Reasoner.class}, (p, method, args) -> {
        if (!method.getName().equals("semanticSearch")) return null;
        var request = (SemanticSearchRequest) args[0];
        var reply = new SemanticSearchResponse(42, request.getRequestId());
        if (request.isCancelSearch()) return reply;
        calls.add(request.getSearchMode());
        switch (request.getSearchMode()) {
          case OPEN_SCOPE -> code.add(StyledKimToken.create("("));
          case CLOSE_SCOPE -> code.add(StyledKimToken.create(")"));
          case UNDO -> { if (!code.isEmpty()) code.removeLast(); }
          default -> { }
        }
        reply.setCode(List.copyOf(code));
        reply.setCanOpenScope(true); // Deliberately permissive to exercise the client guard.
        reply.setCanUndo(!code.isEmpty()); reply.setAcceptsValue(value);
        long depth = code.stream().filter(t -> t.getValue().equals("(")).count()
            - code.stream().filter(t -> t.getValue().equals(")")).count();
        reply.setCanCloseScope(depth > 0 && !code.getLast().getValue().equals("("));
        return reply;
      });
    }
  }
}
