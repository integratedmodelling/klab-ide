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
    try { Platform.startup(() -> {
      Platform.setImplicitExit(false);
      javafx.application.Application.setUserAgentStylesheet(new atlantafx.base.theme.PrimerLight().getUserAgentStylesheet());
      ready.complete(null);
    }); }
    catch (IllegalStateException started) { Platform.runLater(() -> ready.complete(null)); }
    ready.get(15, TimeUnit.SECONDS);
  }

  @Test void selectionDialogReturnsObservableAndNullOnCancelOrWindowClose() throws Exception {
    var owner = fx(() -> {
      var stage = new javafx.stage.Stage();
      stage.setScene(new javafx.scene.Scene(new javafx.scene.layout.VBox(), 1000, 800)); stage.show(); return stage;
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
        var composer = fx(() -> (SemanticComposer) stage.getScene().lookup("#semantic-composer"));
        ready(composer);
        fx(() -> {
          if (action.equals("window-close")) stage.close();
          else if (action.equals("Cancel")) press(query(composer), KeyCode.ESCAPE);
          else ((Button) composer.lookup("#semantic-continue")).fire();
          return null;
        });
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
      var rows = content.getChildren().stream().filter(HBox.class::isInstance).map(HBox.class::cast).toList();
      assertEquals(2, rows.size());
      var directRow = rows.get(0);
      var inheritedRow = rows.get(1);
      assertTrue(content.getChildren().stream().filter(Label.class::isInstance).map(Label.class::cast)
          .anyMatch(label -> concept.getUrn().equals(label.getText())));
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
      stage.setScene(new javafx.scene.Scene(new javafx.scene.layout.VBox(), 1000, 800));
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
      long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
      while (stage == null) {
        stage = fx(() -> (javafx.stage.Stage) javafx.stage.Window.getWindows().stream()
            .filter(w -> w != owner && w instanceof javafx.stage.Stage st
                && st.getTitle().equals("Compose observable"))
            .findFirst().orElse(null));
        if (System.nanoTime() > deadline) fail("Composer dialog did not open");
        if (stage == null) Thread.sleep(10);
      }
      var composerStage = stage;
      var composer = fx(() -> (SemanticComposer) composerStage.getScene().lookup("#semantic-composer"));
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
        assertEquals("(", fake.code.getLast().getValue());
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
        assertTrue(fake.code.isEmpty());
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
      fx(() -> { press(query(composer), KeyCode.BACK_SPACE); return null; });
      ready(composer);
      fx(() -> { press(query(composer), KeyCode.BACK_SPACE); return null; });
      assertEquals(1, fake.count(SemanticSearchRequest.Mode.UNDO));
      fx(() -> { release(query(composer), KeyCode.BACK_SPACE); press(query(composer), KeyCode.BACK_SPACE); return null; });
      ready(composer);
      assertEquals(2, fake.count(SemanticSearchRequest.Mode.UNDO));
      fx(() -> {
        release(query(composer), KeyCode.BACK_SPACE);
        query(composer).setText("tree");
        query(composer).positionCaret(4);
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

  @Test void compoundInputKeepsColoredTokensAndReturnAddsSelectedMatch() throws Exception {
    var fake = new FakeSearch();
    var tree = token("test:Tree"); tree.setColor(KimStyle.Color.SUBJECT);
    fake.code.add(tree);
    var composer = fx(() -> new SemanticComposer(() -> fake.reasoner(),
        o -> CompletableFuture.completedFuture(null), () -> {}));
    try {
      ready(composer);
      fx(() -> {
        var input = (javafx.scene.layout.StackPane) composer.lookup("#semantic-input");
        var scroll = (ScrollPane) input.getChildren().getFirst();
        var line = (HBox) scroll.getContent();
        var flow = (javafx.scene.text.TextFlow) line.getChildren().getFirst();
        assertEquals("test:Tree", ((Text) flow.getChildren().getFirst()).getText());
        assertEquals(Color.rgb(153, 76, 0), ((Text) flow.getChildren().getFirst()).getFill());
        assertSame(query(composer), line.getChildren().getLast());
        assertEquals(flow.getBoundsInParent().getCenterY(), query(composer).getBoundsInParent().getCenterY(), 1.0);
        assertTrue(composer.getChildren().indexOf(composer.lookup(".table-view"))
            < composer.getChildren().indexOf(input));
        var table = (TableView<?>) composer.lookup(".table-view");
        press(table, KeyCode.ENTER);
        return null;
      });
      ready(composer);
      assertEquals(1, fake.count(SemanticSearchRequest.Mode.SELECT));
      fx(() -> {
        assertEquals("", query(composer).getText());
        assertEquals(0, query(composer).getCaretPosition());
        return null;
      });
    } finally { fx(() -> { composer.close(); return null; }); }
  }

  @Test void controlReturnOnlyFromInputSubmitsConfirmedObservableOnly() throws Exception {
    for (boolean fromTable : List.of(false, true)) {
      var fake = new FakeSearch();
      var concept = new org.integratedmodelling.common.knowledge.ConceptImpl();
      concept.setUrn("test:Tree");
      concept.getType().add(org.integratedmodelling.klab.api.knowledge.SemanticType.SUBJECT);
      fake.observable = new org.integratedmodelling.common.knowledge.ObservableImpl();
      fake.observable.setSemantics(concept); fake.observable.setUrn("test:Tree");
      var accepted = new CompletableFuture<org.integratedmodelling.klab.api.knowledge.Observable>();
      var composer = fx(() -> new SemanticComposer(() -> fake.reasoner(),
          observable -> { accepted.complete(observable); return CompletableFuture.completedFuture(null); }, () -> {}));
      try {
        ready(composer);
        fx(() -> { query(composer).setText("unconfirmed"); return null; });
        ready(composer);
        fx(() -> {
          if (fromTable) {
            Event.fireEvent(composer.lookup(".table-view"),
                new KeyEvent(KeyEvent.KEY_PRESSED, "", "", KeyCode.ENTER, false, true, false, false));
            assertFalse(accepted.isDone());
            release(query(composer), KeyCode.ENTER);
          }
          Event.fireEvent(query(composer), new KeyEvent(KeyEvent.KEY_PRESSED, "", "", KeyCode.ENTER,
              false, true, false, false));
          return null;
        });
        assertFalse(accepted.isDone());
        fx(() -> { query(composer).clear(); release(query(composer), KeyCode.ENTER); return null; }); ready(composer);
        fx(() -> { Event.fireEvent(query(composer), new KeyEvent(KeyEvent.KEY_PRESSED, "", "", KeyCode.ENTER,
            false, true, false, false)); return null; });
        assertSame(fake.observable, accepted.get(10, TimeUnit.SECONDS));
        assertEquals(0, fake.count(SemanticSearchRequest.Mode.SELECT));
      } finally { fx(() -> { composer.close(); return null; }); }
    }
  }

  @Test void heldEnterAfterTableSelectionCannotAddAgainOrSubmit() throws Exception {
    var fake = new FakeSearch();
    var composer = fx(() -> new SemanticComposer(() -> fake.reasoner(),
        o -> { fail("Table selection submitted the observable"); return CompletableFuture.completedFuture(null); }, () -> {}));
    try {
      ready(composer);
      fx(() -> { press(composer.lookup(".table-view"), KeyCode.ENTER); return null; });
      ready(composer);
      fx(() -> {
        press(query(composer), KeyCode.ENTER);
        press(composer.lookup(".table-view"), KeyCode.ENTER);
        return null;
      });
      assertEquals(1, fake.count(SemanticSearchRequest.Mode.SELECT));
      fx(() -> {
        release(query(composer), KeyCode.ENTER);
        press(query(composer), KeyCode.ENTER);
        release(query(composer), KeyCode.ENTER);
        return null;
      });
      assertEquals(1, fake.count(SemanticSearchRequest.Mode.SELECT));
      fx(() -> { press(composer.lookup(".table-view"), KeyCode.ENTER); return null; });
      ready(composer);
      assertEquals(2, fake.count(SemanticSearchRequest.Mode.SELECT));
    } finally { fx(() -> { composer.close(); return null; }); }
  }

  @Test void onlyPrimaryDoubleClickOnPopulatedRowAddsSelection() throws Exception {
    var fake = new FakeSearch();
    var composer = fx(() -> new SemanticComposer(() -> fake.reasoner(),
        o -> { fail("Double-click submitted the observable"); return CompletableFuture.completedFuture(null); }, () -> {}));
    try {
      ready(composer);
      fx(() -> {
        @SuppressWarnings("unchecked")
        var table = (TableView<SemanticMatch>) composer.lookup(".table-view");
        var row = table.getRowFactory().call(table); row.updateTableView(table); row.updateIndex(0);
        click(row, MouseButton.PRIMARY, 1);
        click(row, MouseButton.SECONDARY, 2);
        var empty = table.getRowFactory().call(table); empty.updateTableView(table); empty.updateIndex(5);
        click(empty, MouseButton.PRIMARY, 2);
        assertEquals(0, fake.count(SemanticSearchRequest.Mode.SELECT));
        click(row, MouseButton.PRIMARY, 2);
        return null;
      });
      ready(composer);
      assertEquals(1, fake.count(SemanticSearchRequest.Mode.SELECT));
    } finally { fx(() -> { composer.close(); return null; }); }
  }

  private static void click(javafx.scene.Node target, MouseButton button, int count) {
    Event.fireEvent(target, new MouseEvent(MouseEvent.MOUSE_CLICKED, 0, 0, 0, 0, button, count,
        false, false, false, false, false, false, false, false, false, true, null));
  }

  @Test void inputArrowsScanMatchesAndEnterSelectsAtEndOfSearchWithoutLeavingInput() throws Exception {
    var fake = new FakeSearch();
    fake.matches = List.of(match("test:Tree"), match("test:Forest"), match("test:Wood"));
    var composer = fx(() -> new SemanticComposer(() -> fake.reasoner(),
        o -> { fail("Enter submitted the observable"); return CompletableFuture.completedFuture(null); }, () -> {}));
    var stage = fx(() -> {
      var window = new javafx.stage.Stage(); window.setScene(new javafx.scene.Scene(composer)); window.show();
      query(composer).requestFocus(); return window;
    });
    try {
      ready(composer);
      fx(() -> { query(composer).setText("test"); query(composer).positionCaret(4); return null; });
      ready(composer);
      fx(() -> {
        var table = (TableView<?>) composer.lookup(".table-view");
        press(query(composer), KeyCode.DOWN); assertEquals(1, table.getSelectionModel().getSelectedIndex());
        press(query(composer), KeyCode.DOWN); press(query(composer), KeyCode.DOWN);
        assertEquals(2, table.getSelectionModel().getSelectedIndex());
        press(query(composer), KeyCode.UP); assertEquals(1, table.getSelectionModel().getSelectedIndex());
        assertSame(query(composer), composer.getScene().getFocusOwner());
        assertEquals(4, query(composer).getCaretPosition());
        press(query(composer), KeyCode.ENTER);
        return null;
      });
      ready(composer);
      assertEquals(List.of("test:Forest"), fake.selections);
      fx(() -> {
        assertEquals("", query(composer).getText());
        assertSame(query(composer), composer.getScene().getFocusOwner());
        return null;
      });
    } finally { fx(() -> { composer.close(); stage.close(); return null; }); }
  }

  @Test void enterInsidePendingSearchDoesNotAcceptIt() throws Exception {
    var fake = new FakeSearch();
    var composer = fx(() -> new SemanticComposer(() -> fake.reasoner(),
        o -> CompletableFuture.completedFuture(null), () -> {}));
    try {
      ready(composer);
      fx(() -> { query(composer).setText("test"); query(composer).positionCaret(2); return null; });
      ready(composer);
      fx(() -> { press(query(composer), KeyCode.ENTER); return null; });
      assertEquals(0, fake.count(SemanticSearchRequest.Mode.SELECT));
    } finally { fx(() -> { composer.close(); return null; }); }
  }

  @Test void overlayCoversOwnerSceneAndGrowsForCardWithScrollingInSmallWindows() throws Exception {
    var fake = new FakeSearch();
    var owner = fx(() -> {
      var window = new javafx.stage.Stage();
      window.setScene(new javafx.scene.Scene(new javafx.scene.layout.VBox(), 1000, 800)); window.show(); return window;
    });
    var overlay = fx(() -> SemanticComposerOverlay.show(owner.getScene().getRoot(),
        dismiss -> new SemanticComposer(() -> fake.reasoner(), o -> CompletableFuture.completedFuture(null), dismiss), () -> {}));
    var composer = fx(() -> (SemanticComposer) overlay.getScene().lookup("#semantic-composer"));
    try {
      ready(composer);
      var initialHeight = fx(() -> composer.getHeight());
      fx(() -> {
        assertEquals(javafx.stage.StageStyle.TRANSPARENT, overlay.getStyle());
        assertEquals(javafx.stage.Modality.WINDOW_MODAL, overlay.getModality());
        assertEquals(owner.getScene().getWidth(), overlay.getWidth(), 1);
        assertEquals(owner.getScene().getHeight(), overlay.getHeight(), 1);
        var concept = new org.integratedmodelling.common.knowledge.ConceptImpl(); concept.setUrn("test:Tree");
        concept.getType().add(org.integratedmodelling.klab.api.knowledge.SemanticType.SUBJECT);
        fake.observable = new org.integratedmodelling.common.knowledge.ObservableImpl();
        fake.observable.setSemantics(concept); fake.observable.setUrn("test:Tree");
        query(composer).setText("tree"); return null;
      });
      ready(composer);
      fx(() -> {
        overlay.getScene().getRoot().applyCss(); overlay.getScene().getRoot().layout();
        var panel = (ScrollPane) overlay.getScene().lookup("#semantic-composer-panel");
        var card = composer.lookup("#semantic-card");
        assertTrue(composer.getHeight() > initialHeight);
        assertTrue(card.isVisible());
        assertTrue(card.getBoundsInParent().getMaxY() <= composer.getHeight());
        assertTrue(composer.getHeight() <= panel.getViewportBounds().getHeight() + 1);
        assertEquals(composer.getHeight(), panel.getViewportBounds().getHeight(), 3);
        assertEquals(overlay.getScene().getHeight() / 2, panel.getBoundsInParent().getCenterY(), 1);
        owner.setWidth(460); owner.setHeight(280);
        return null;
      });
      // Native window resize notifications and the following layout pulse are asynchronous.
      waitUntil(() -> {
        overlay.getScene().getRoot().applyCss(); overlay.getScene().getRoot().layout();
        var panel = (ScrollPane) overlay.getScene().lookup("#semantic-composer-panel");
        return Math.abs(owner.getScene().getWidth() - overlay.getWidth()) <= 1
            && Math.abs(owner.getScene().getHeight() - overlay.getHeight()) <= 1
            && composer.getHeight() > panel.getViewportBounds().getHeight();
      });
      fx(() -> {
        overlay.getScene().getRoot().applyCss(); overlay.getScene().getRoot().layout();
        var panel = (ScrollPane) overlay.getScene().lookup("#semantic-composer-panel");
        assertEquals(owner.getScene().getWidth(), overlay.getWidth(), 1);
        assertEquals(owner.getScene().getHeight(), overlay.getHeight(), 1);
        assertTrue(composer.getHeight() > panel.getViewportBounds().getHeight());
        return null;
      });
    } finally { fx(() -> { overlay.close(); owner.close(); return null; }); }
  }

  private static SemanticMatch match(String id) {
    var match = new SemanticMatch(); match.setId(id); match.setName(id); return match;
  }

  @Test void typingDuringSlowInsertionPreservesEditAndRefreshesOnlyLatestQuery() throws Exception {
    var fake = new FakeSearch(); fake.selectAddsToken = true;
    var started = new CountDownLatch(1); var release = new CountDownLatch(1);
    fake.before = request -> {
      if (request.getSearchMode() == SemanticSearchRequest.Mode.SELECT) {
        started.countDown(); await(release);
      }
    };
    var composer = fx(() -> new SemanticComposer(() -> fake.reasoner(),
        o -> CompletableFuture.completedFuture(null), () -> {}));
    try {
      ready(composer);
      fx(() -> { press(composer.lookup(".table-view"), KeyCode.ENTER); return null; });
      assertTrue(started.await(5, TimeUnit.SECONDS));
      fx(() -> {
        assertTrue(composer.lookup("#semantic-progress").isVisible());
        assertTrue(status(composer).getText().startsWith("Adding"));
        assertFalse(composer.lookup("#semantic-continue").isVisible());
        query(composer).setText("for"); query(composer).setText("forest"); query(composer).positionCaret(6);
        return null;
      });
      release.countDown(); ready(composer);
      fx(() -> {
        assertEquals("forest", query(composer).getText());
        assertEquals(List.of("test:Tree"), displayedTokens(composer));
        return null;
      });
      assertEquals("forest", fake.requests.getLast().getQueryString());
      assertEquals(1, fake.count(SemanticSearchRequest.Mode.SELECT));
      assertEquals(2, fake.count(SemanticSearchRequest.Mode.TOKEN));
    } finally { release.countDown(); fx(() -> { composer.close(); return null; }); }
  }

  @Test void backspaceWhileAnInsertionIsPendingQueuesUndo() throws Exception {
    var fake = new FakeSearch(); fake.selectAddsToken = true;
    var started = new CountDownLatch(1); var release = new CountDownLatch(1);
    fake.before = request -> {
      if (request.getSearchMode() == SemanticSearchRequest.Mode.SELECT) { started.countDown(); await(release); }
    };
    var composer = fx(() -> new SemanticComposer(() -> fake.reasoner(),
        o -> CompletableFuture.completedFuture(null), () -> {}));
    try {
      ready(composer);
      fx(() -> { press(composer.lookup(".table-view"), KeyCode.ENTER); return null; });
      assertTrue(started.await(5, TimeUnit.SECONDS));
      fx(() -> { press(query(composer), KeyCode.BACK_SPACE); release(query(composer), KeyCode.BACK_SPACE); return null; });
      release.countDown(); ready(composer);
      assertEquals(1, fake.count(SemanticSearchRequest.Mode.UNDO));
      fx(() -> { assertTrue(displayedTokens(composer).isEmpty()); return null; });
    } finally { release.countDown(); fx(() -> { composer.close(); return null; }); }
  }

  @Test void transportFailureRetainsExpressionAndBackspaceCanRecover() throws Exception {
    var fake = new FakeSearch(); fake.code.add(token("test:Tree"));
    var fail = new java.util.concurrent.atomic.AtomicBoolean(false);
    fake.before = request -> { if (fail.getAndSet(false)) throw new IllegalStateException("Connection interrupted"); };
    var composer = fx(() -> new SemanticComposer(() -> fake.reasoner(),
        o -> CompletableFuture.completedFuture(null), () -> {}));
    try {
      ready(composer); fail.set(true);
      fx(() -> { query(composer).setText("tree"); return null; }); ready(composer);
      fx(() -> {
        assertEquals(List.of("test:Tree"), displayedTokens(composer));
        assertTrue(status(composer).getText().contains("Connection interrupted"));
        assertEquals("Retry search", ((Button) composer.lookup("#semantic-continue")).getAccessibleText());
        query(composer).positionCaret(0); press(query(composer), KeyCode.BACK_SPACE);
        return null;
      }); ready(composer);
      assertEquals(1, fake.count(SemanticSearchRequest.Mode.UNDO));
      fx(() -> { assertTrue(displayedTokens(composer).isEmpty()); return null; });
    } finally { fx(() -> { composer.close(); return null; }); }
  }

  @Test void editCommittedBeforeFailedResponseCanStillBeUndone() throws Exception {
    var fake = new FakeSearch(); fake.selectAddsToken = true;
    fake.after = (request, reply) -> {
      if (request.getSearchMode() == SemanticSearchRequest.Mode.SELECT) throw new IllegalStateException("Suggestions failed after edit");
    };
    var composer = fx(() -> new SemanticComposer(() -> fake.reasoner(),
        o -> { fail("Uncertain state was submitted"); return CompletableFuture.completedFuture(null); }, () -> {}));
    try {
      ready(composer);
      fx(() -> { press(composer.lookup(".table-view"), KeyCode.ENTER); return null; }); ready(composer);
      assertEquals(1, fake.code.size());
      fx(() -> { press(query(composer), KeyCode.BACK_SPACE); return null; }); ready(composer);
      assertEquals(1, fake.count(SemanticSearchRequest.Mode.UNDO));
      assertTrue(fake.code.isEmpty());
    } finally { fx(() -> { composer.close(); return null; }); }
  }

  @Test void expiredSessionPreservesDisplayUntilExplicitRestart() throws Exception {
    var fake = new FakeSearch(); fake.code.add(token("test:Tree"));
    var expire = new java.util.concurrent.atomic.AtomicBoolean(false);
    fake.after = (request, reply) -> {
      if (expire.getAndSet(false)) {
        reply.setSearchId(0); reply.setCode(List.of()); reply.setCanUndo(false);
        reply.getErrors().add("This search has expired."); fake.code.clear();
      }
    };
    var composer = fx(() -> new SemanticComposer(() -> fake.reasoner(),
        o -> CompletableFuture.completedFuture(null), () -> {}));
    try {
      ready(composer); expire.set(true);
      fx(() -> { query(composer).setText("tree"); return null; }); ready(composer);
      fx(() -> {
        assertEquals(List.of("test:Tree"), displayedTokens(composer));
        assertEquals("Restart expression", ((Button) composer.lookup("#semantic-continue")).getAccessibleText());
        query(composer).positionCaret(0); press(query(composer), KeyCode.BACK_SPACE);
        return null;
      }); ready(composer);
      assertEquals(0, fake.requests.getLast().getSearchId());
      assertEquals(SemanticSearchRequest.Mode.TOKEN, fake.requests.getLast().getSearchMode());
      fx(() -> { assertTrue(displayedTokens(composer).isEmpty()); return null; });
    } finally { fx(() -> { composer.close(); return null; }); }
  }

  @Test void hungRequestTimesOutAndLateReplyCannotReplaceRestartedSession() throws Exception {
    var fake = new FakeSearch(); fake.selectAddsToken = true;
    var started = new CountDownLatch(1); var release = new CountDownLatch(1); var returned = new CountDownLatch(1);
    fake.before = request -> {
      if (request.getSearchMode() == SemanticSearchRequest.Mode.SELECT) {
        started.countDown();
        boolean done = false;
        while (!done) { try { release.await(); done = true; } catch (InterruptedException ignored) { } }
      }
    };
    fake.after = (request, reply) -> {
      if (request.getSearchMode() == SemanticSearchRequest.Mode.SELECT) { reply.setSearchId(99); returned.countDown(); }
    };
    var composer = fx(() -> new SemanticComposer(() -> fake.reasoner(),
        o -> CompletableFuture.completedFuture(null), () -> {}, null, javafx.util.Duration.millis(500)));
    try {
      ready(composer);
      fx(() -> { press(composer.lookup(".table-view"), KeyCode.ENTER); return null; });
      assertTrue(started.await(5, TimeUnit.SECONDS)); ready(composer);
      fx(() -> {
        assertTrue(status(composer).getText().contains("did not respond"));
        ((Button) composer.lookup("#semantic-continue")).fire(); return null;
      }); ready(composer);
      assertEquals(0, fake.requests.getLast().getSearchId());
      release.countDown(); assertTrue(returned.await(5, TimeUnit.SECONDS));
      fx(() -> { assertTrue(displayedTokens(composer).isEmpty()); return null; });
    } finally { release.countDown(); fx(() -> { composer.close(); return null; }); }
  }

  private static Label status(SemanticComposer composer) { return (Label) composer.lookup("#semantic-status"); }

  @Test void conceptSearchWaitsForTwoCharactersAndClearsShortQueryMatches() throws Exception {
    var fake = new FakeSearch(); fake.code.add(token("data:Normalized"));
    var composer = fx(() -> new SemanticComposer(() -> fake.reasoner(),
        o -> CompletableFuture.completedFuture(null), () -> {}));
    try {
      ready(composer);
      fx(() -> { query(composer).setText("s"); return null; }); ready(composer);
      assertEquals(1, fake.count(SemanticSearchRequest.Mode.TOKEN));
      fx(() -> {
        assertTrue(status(composer).getText().contains("two characters"));
        assertTrue(((TableView<?>) composer.lookup(".table-view")).getItems().isEmpty());
        assertEquals(List.of("data:Normalized"), displayedTokens(composer));
        query(composer).setText("sl"); return null;
      }); ready(composer);
      assertEquals("sl", fake.requests.getLast().getQueryString());
      assertEquals(2, fake.count(SemanticSearchRequest.Mode.TOKEN));
      fx(() -> { query(composer).setText("s"); return null; }); ready(composer);
      assertEquals(2, fake.count(SemanticSearchRequest.Mode.TOKEN));
      fx(() -> {
        press(query(composer), KeyCode.ENTER); release(query(composer), KeyCode.ENTER);
        query(composer).clear(); return null;
      }); ready(composer);
      assertEquals(0, fake.count(SemanticSearchRequest.Mode.SELECT));
      assertEquals(3, fake.count(SemanticSearchRequest.Mode.TOKEN));
    } finally { fx(() -> { composer.close(); return null; }); }
  }

  @Test void minimumSearchLengthPreservesSymbolOperatorsAndSingleDigitValues() throws Exception {
    var fake = new FakeSearch();
    var composer = fx(() -> new SemanticComposer(() -> fake.reasoner(),
        o -> CompletableFuture.completedFuture(null), () -> {}));
    try {
      ready(composer);
      fx(() -> { query(composer).setText(">"); return null; }); ready(composer);
      assertEquals(">", fake.requests.getLast().getQueryString());
      fake.value = true;
      fx(() -> { query(composer).setText(">="); return null; }); ready(composer);
      fx(() -> { query(composer).setText("3"); query(composer).positionCaret(1); return null; }); ready(composer);
      fx(() -> { press(query(composer), KeyCode.ENTER); return null; }); ready(composer);
      assertEquals(1, fake.count(SemanticSearchRequest.Mode.VALUE));
      assertEquals("3", fake.requests.getLast().getQueryString());
    } finally { fx(() -> { composer.close(); return null; }); }
  }

  @Test void shortQueryTypedDuringSearchIsNotSentWhenTheOldReplyArrives() throws Exception {
    var fake = new FakeSearch();
    var started = new CountDownLatch(1); var release = new CountDownLatch(1);
    fake.before = request -> {
      if (request.getQueryString().equals("sl")) { started.countDown(); await(release); }
    };
    var composer = fx(() -> new SemanticComposer(() -> fake.reasoner(),
        o -> CompletableFuture.completedFuture(null), () -> {}));
    try {
      ready(composer);
      fx(() -> { query(composer).setText("sl"); return null; });
      assertTrue(started.await(5, TimeUnit.SECONDS));
      fx(() -> { query(composer).setText("s"); return null; });
      release.countDown(); ready(composer);
      assertEquals(2, fake.count(SemanticSearchRequest.Mode.TOKEN));
      fx(() -> {
        assertEquals("s", query(composer).getText());
        assertTrue(status(composer).getText().contains("two characters"));
        assertTrue(((TableView<?>) composer.lookup(".table-view")).getItems().isEmpty());
        return null;
      });
    } finally { release.countDown(); fx(() -> { composer.close(); return null; }); }
  }

  @Test void tokenTimeoutKeepsConfirmedExpressionAndRetriesTheSameSession() throws Exception {
    var fake = new FakeSearch(); fake.code.add(token("data:Normalized"));
    var started = new CountDownLatch(1); var release = new CountDownLatch(1);
    var returned = new CountDownLatch(1); var first = new java.util.concurrent.atomic.AtomicBoolean(true);
    fake.before = request -> {
      if (request.getQueryString().equals("slope") && first.getAndSet(false)) {
        started.countDown();
        boolean done = false;
        while (!done) try { done = release.await(5, TimeUnit.SECONDS); }
        catch (InterruptedException ignored) { /* Simulate a server call still running after cancellation. */ }
        returned.countDown();
      }
    };
    var composer = fx(() -> new SemanticComposer(() -> fake.reasoner(),
        o -> CompletableFuture.completedFuture(null), () -> {}, null, javafx.util.Duration.millis(500)));
    try {
      ready(composer);
      fx(() -> { query(composer).setText("slope"); return null; });
      assertTrue(started.await(5, TimeUnit.SECONDS)); ready(composer);
      fx(() -> {
        assertTrue(status(composer).getText().contains("search did not respond"));
        assertEquals(List.of("data:Normalized"), displayedTokens(composer));
        var button = (Button) composer.lookup("#semantic-continue");
        assertEquals("Retry search", button.getAccessibleText()); button.fire(); return null;
      }); ready(composer);
      assertEquals(42, fake.requests.getLast().getSearchId());
      release.countDown(); assertTrue(returned.await(5, TimeUnit.SECONDS));
      fx(() -> {
        assertEquals(List.of("data:Normalized"), displayedTokens(composer));
        assertEquals("slope", query(composer).getText()); return null;
      });
    } finally { release.countDown(); fx(() -> { composer.close(); return null; }); }
  }

  @Test void newerQueryDoesNotWaitForHungOlderQueryAndIgnoresItsLateReply() throws Exception {
    var fake = new FakeSearch(); fake.code.add(token("data:Normalized"));
    var started = new CountDownLatch(1); var release = new CountDownLatch(1);
    var returned = new CountDownLatch(1);
    fake.before = request -> {
      if (request.getQueryString().equals("old")) {
        started.countDown();
        boolean done = false;
        while (!done) try { done = release.await(5, TimeUnit.SECONDS); }
        catch (InterruptedException ignored) { }
      }
    };
    fake.after = (request, reply) -> {
      if (request.getQueryString().equals("old")) { reply.getErrors().add("Obsolete reply"); returned.countDown(); }
    };
    var composer = fx(() -> new SemanticComposer(() -> fake.reasoner(),
        o -> CompletableFuture.completedFuture(null), () -> {}));
    try {
      ready(composer);
      fx(() -> { query(composer).setText("old"); return null; });
      assertTrue(started.await(5, TimeUnit.SECONDS));
      fx(() -> { query(composer).setText("slope"); return null; }); ready(composer);
      assertEquals("slope", fake.requests.getLast().getQueryString());
      assertEquals(42, fake.requests.getLast().getSearchId());
      assertEquals(1, release.getCount(), "Newer query waited for the old call to return");
      release.countDown(); assertTrue(returned.await(5, TimeUnit.SECONDS));
      fx(() -> {
        assertEquals("", status(composer).getText());
        assertEquals("slope", query(composer).getText());
        assertEquals(List.of("data:Normalized"), displayedTokens(composer)); return null;
      });
    } finally { release.countDown(); fx(() -> { composer.close(); return null; }); }
  }

  @Test void rapidTypingSendsOnlyTheLastDebouncedQuery() throws Exception {
    var fake = new FakeSearch();
    var composer = fx(() -> new SemanticComposer(() -> fake.reasoner(),
        o -> CompletableFuture.completedFuture(null), () -> {}));
    try {
      ready(composer);
      for (String text : List.of("sl", "slo", "slop", "slope")) {
        fx(() -> { query(composer).setText(text); return null; });
        Thread.sleep(40);
      }
      ready(composer);
      assertEquals(2, fake.count(SemanticSearchRequest.Mode.TOKEN));
      assertEquals("slope", fake.requests.getLast().getQueryString());
    } finally { fx(() -> { composer.close(); return null; }); }
  }

  @Test void backspaceCanUndoWithoutWaitingForAnObsoleteTokenSearch() throws Exception {
    var fake = new FakeSearch(); fake.code.add(token("data:Normalized"));
    var started = new CountDownLatch(1); var undoStarted = new CountDownLatch(1);
    var release = new CountDownLatch(1);
    fake.before = request -> {
      if (request.getSearchMode() == SemanticSearchRequest.Mode.UNDO) undoStarted.countDown();
      if (request.getSearchMode() == SemanticSearchRequest.Mode.TOKEN
          && request.getQueryString().equals("slow") && !fake.code.isEmpty()) {
        started.countDown();
        boolean done = false;
        while (!done) try { done = release.await(5, TimeUnit.SECONDS); }
        catch (InterruptedException ignored) { }
      }
    };
    var composer = fx(() -> new SemanticComposer(() -> fake.reasoner(),
        o -> CompletableFuture.completedFuture(null), () -> {}));
    try {
      ready(composer);
      fx(() -> { query(composer).setText("slow"); query(composer).positionCaret(0); return null; });
      assertTrue(started.await(5, TimeUnit.SECONDS));
      fx(() -> { press(query(composer), KeyCode.BACK_SPACE); return null; });
      assertTrue(undoStarted.await(2, TimeUnit.SECONDS)); ready(composer);
      assertEquals(1, release.getCount());
      fx(() -> { assertTrue(displayedTokens(composer).isEmpty()); return null; });
    } finally { release.countDown(); fx(() -> { composer.close(); return null; }); }
  }

  @Test void rejectedLiteralKeepsItsDiagnosticAndCanBeCorrected() throws Exception {
    var fake = new FakeSearch(); fake.value = true; fake.code.add(token("test:Tree"));
    fake.after = (request, reply) -> {
      if (request.getSearchMode() == SemanticSearchRequest.Mode.VALUE && request.getQueryString().equals("bad"))
        reply.getErrors().add("Invalid numeric value");
    };
    var composer = fx(() -> new SemanticComposer(() -> fake.reasoner(),
        o -> CompletableFuture.completedFuture(null), () -> {}));
    try {
      ready(composer);
      fx(() -> { query(composer).setText("bad"); query(composer).positionCaret(3); return null; }); ready(composer);
      fx(() -> { press(query(composer), KeyCode.ENTER); release(query(composer), KeyCode.ENTER); return null; }); ready(composer);
      fx(() -> {
        assertEquals("Invalid numeric value", status(composer).getText());
        assertEquals("bad", query(composer).getText());
        query(composer).setText("3"); return null;
      }); ready(composer);
      fx(() -> { press(query(composer), KeyCode.ENTER); return null; }); ready(composer);
      assertEquals(2, fake.count(SemanticSearchRequest.Mode.VALUE));
      fx(() -> { assertEquals("", query(composer).getText()); assertEquals("", status(composer).getText()); return null; });
    } finally { fx(() -> { composer.close(); return null; }); }
  }

  @Test void committedExpressionWithSuggestionWarningCanStillBeSubmitted() throws Exception {
    var fake = new FakeSearch(); fake.selectAddsToken = true;
    fake.after = (request, reply) -> {
      if (request.getSearchMode() == SemanticSearchRequest.Mode.SELECT) reply.getErrors().add("Suggestions unavailable");
    };
    var accepted = new CompletableFuture<org.integratedmodelling.klab.api.knowledge.Observable>();
    var composer = fx(() -> new SemanticComposer(() -> fake.reasoner(),
        o -> { accepted.complete(o); return CompletableFuture.completedFuture(null); }, () -> {}));
    try {
      ready(composer);
      fx(() -> { press(composer.lookup(".table-view"), KeyCode.ENTER); return null; }); ready(composer);
      fx(() -> {
        assertEquals(List.of("test:Tree"), displayedTokens(composer));
        assertEquals("Suggestions unavailable", status(composer).getText());
        var icon = (Button) composer.lookup("#semantic-continue");
        assertEquals("Continue", icon.getAccessibleText()); icon.fire(); return null;
      });
      assertEquals("test:Tree", accepted.get(10, TimeUnit.SECONDS).getUrn());
      assertEquals(1, fake.count(SemanticSearchRequest.Mode.TOKEN));
    } finally { fx(() -> { composer.close(); return null; }); }
  }

  @Test void releaseOutsideComposerDoesNotLeaveEnterLatched() throws Exception {
    var fake = new FakeSearch();
    var composer = fx(() -> new SemanticComposer(() -> fake.reasoner(),
        o -> CompletableFuture.completedFuture(null), () -> {}));
    fx(() -> { new javafx.scene.Scene(new javafx.scene.layout.StackPane(composer)); return null; });
    try {
      ready(composer);
      fx(() -> { press(composer.lookup(".table-view"), KeyCode.ENTER); return null; }); ready(composer);
      fx(() -> {
        Event.fireEvent(composer.getScene().getRoot(), new KeyEvent(KeyEvent.KEY_RELEASED, "", "",
            KeyCode.ENTER, false, false, false, false));
        press(composer.lookup(".table-view"), KeyCode.ENTER); return null;
      }); ready(composer);
      assertEquals(2, fake.count(SemanticSearchRequest.Mode.SELECT));
    } finally { fx(() -> { composer.close(); return null; }); }
  }

  private static List<String> displayedTokens(SemanticComposer composer) {
    var line = (HBox) ((ScrollPane) ((javafx.scene.layout.StackPane) composer.lookup("#semantic-input")).getChildren().getFirst()).getContent();
    return ((javafx.scene.text.TextFlow) line.getChildren().getFirst()).getChildren().stream()
        .map(Text.class::cast).map(Text::getText).filter(text -> !text.isBlank()).toList();
  }
  private static void await(CountDownLatch latch) {
    try { if (!latch.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("Test request was not released"); }
    catch (InterruptedException ex) { Thread.currentThread().interrupt(); throw new IllegalStateException(ex); }
  }

  @Test void controlReturnWithIncompleteExpressionDoesNotSelect() throws Exception {
    var fake = new FakeSearch();
    var composer = fx(() -> new SemanticComposer(() -> fake.reasoner(),
        o -> { fail("Incomplete observable submitted"); return CompletableFuture.completedFuture(null); }, () -> {}));
    try {
      ready(composer);
      fx(() -> {
        Event.fireEvent(query(composer), new KeyEvent(KeyEvent.KEY_PRESSED, "", "", KeyCode.ENTER,
            false, true, false, false));
        return null;
      });
      assertEquals(0, fake.count(SemanticSearchRequest.Mode.SELECT));
    } finally { fx(() -> { composer.close(); return null; }); }
  }

  private static StyledKimToken token(String value) {
    var token = new StyledKimToken(); token.setValue(value);
    token.setNeedsWhitespaceBefore(true); token.setNeedsWhitespaceAfter(true); return token;
  }

  @Test void authoritySelectionUsesCanonicalCodeAndExistingSessionThenUndoes() throws Exception {
    var fake = new FakeSearch();
    fake.authoritySearch = request -> new AuthoritySearchResponse(AuthoritySearchResponse.Status.OK,
        List.of(identity("A 1", "First"), identity("A2", "Second")), 2, -1, List.of());
    var host = fake.reasoner();
    var composer = fx(() -> new SemanticComposer(() -> host, o -> CompletableFuture.completedFuture(null), () -> {},
        null, javafx.util.Duration.seconds(30), () -> authoritySource(host)));
    try {
      ready(composer);
      waitUntil(() -> ((ComboBox<?>) composer.lookup("#semantic-authority")).getItems().size() == 1);
      fx(() -> { ((ComboBox<String>) composer.lookup("#semantic-source")).setValue("Authorities");
        query(composer).setText("ab"); query(composer).positionCaret(2); return null; });
      waitUntil(() -> ((TableView<?>) composer.lookup("#authority-results")).getItems().size() == 2);
      fx(() -> { press(query(composer), KeyCode.DOWN); press(query(composer), KeyCode.ENTER); release(query(composer), KeyCode.ENTER); return null; });
      ready(composer);
      var insertion = fake.requests.stream().filter(r -> r.getSearchMode() == SemanticSearchRequest.Mode.IDENTITY).findFirst().orElseThrow();
      assertEquals("TAXA", insertion.getAuthority()); assertEquals("A2", insertion.getIdentityCode());
      assertEquals(42, insertion.getSearchId()); assertEquals(1, insertion.getMatchesRequestId());
      assertEquals(List.of("TAXA:A2"), fx(() -> displayedTokens(composer)));
      assertEquals("", fx(() -> query(composer).getText()));
      fx(() -> { press(query(composer), KeyCode.BACK_SPACE); release(query(composer), KeyCode.BACK_SPACE); return null; });
      ready(composer); assertTrue(fx(() -> displayedTokens(composer).isEmpty()));
      assertEquals(1, fake.count(SemanticSearchRequest.Mode.TOKEN));
    } finally { fx(() -> { composer.close(); return null; }); }
  }

  @Test void authorityQueriesDiscardLateRepliesAndDocumentationDoesNotInsert() throws Exception {
    var fake = new FakeSearch(); var oldStarted = new CountDownLatch(1); var releaseOld = new CountDownLatch(1);
    fake.authoritySearch = request -> {
      if (request.query().equals("old")) { oldStarted.countDown();
        // Simulate a provider that cannot be interrupted.
        boolean done = false; while (!done) { try { done = releaseOld.await(5, TimeUnit.SECONDS); } catch (InterruptedException ignored) {} }
      }
      return new AuthoritySearchResponse(AuthoritySearchResponse.Status.OK, List.of(identity(request.query(), request.query())), 1, -1, List.of());
    };
    var host = fake.reasoner();
    var composer = fx(() -> new SemanticComposer(() -> host, o -> CompletableFuture.completedFuture(null), () -> {},
        null, javafx.util.Duration.seconds(30), () -> authoritySource(host)));
    try {
      ready(composer); waitUntil(() -> ((ComboBox<?>) composer.lookup("#semantic-authority")).getItems().size() == 1);
      fx(() -> { ((ComboBox<String>) composer.lookup("#semantic-source")).setValue("Authorities"); query(composer).setText("old"); return null; });
      assertTrue(oldStarted.await(5, TimeUnit.SECONDS));
      fx(() -> { query(composer).setText("new"); return null; });
      waitUntil(() -> ((TableView<?>) composer.lookup("#authority-results")).getItems().size() == 1);
      releaseOld.countDown(); Thread.sleep(100);
      assertEquals("new", fx(() -> ((org.integratedmodelling.klab.api.services.resources.objects.AuthorityIdentity)
          ((TableView<?>) composer.lookup("#authority-results")).getItems().getFirst()).getId()));
      assertEquals(0, fake.count(SemanticSearchRequest.Mode.IDENTITY));
      assertTrue(fake.documentationCalls.size() > 0);
    } finally { releaseOld.countDown(); fx(() -> { composer.close(); return null; }); }
  }

  @Test void authorityUnavailableAndRejectedInsertionPreserveExpressionAndQuery() throws Exception {
    var fake = new FakeSearch(); fake.code.add(token("test:Tree"));
    fake.authoritySearch = request -> AuthoritySearchResponse.failure(AuthoritySearchResponse.Status.UNAVAILABLE, "Provider unavailable");
    var host = fake.reasoner();
    var composer = fx(() -> new SemanticComposer(() -> host, o -> CompletableFuture.completedFuture(null), () -> {},
        null, javafx.util.Duration.seconds(30), () -> authoritySource(host)));
    try {
      ready(composer); waitUntil(() -> ((ComboBox<?>) composer.lookup("#semantic-authority")).getItems().size() == 1);
      fx(() -> { ((ComboBox<String>) composer.lookup("#semantic-source")).setValue("Authorities"); query(composer).setText("ab"); return null; });
      waitUntil(() -> ((Label) composer.lookup("#semantic-status")).getText().contains("Provider unavailable"));
      assertEquals(List.of("test:Tree"), fx(() -> displayedTokens(composer)));
      fake.authoritySearch = request -> new AuthoritySearchResponse(AuthoritySearchResponse.Status.OK, List.of(identity("A1", "First")), 1, -1, List.of());
      fake.after = (request, reply) -> { if (request.getSearchMode() == SemanticSearchRequest.Mode.IDENTITY) {
        fake.code.removeLast(); reply.setCode(List.copyOf(fake.code)); reply.getErrors().add("Incompatible identity");
      }};
      fx(() -> { query(composer).setText("abc"); query(composer).positionCaret(3); return null; });
      waitUntil(() -> ((TableView<?>) composer.lookup("#authority-results")).getItems().size() == 1);
      fx(() -> { press(query(composer), KeyCode.ENTER); return null; }); ready(composer);
      assertEquals("abc", fx(() -> query(composer).getText()));
      assertEquals(List.of("test:Tree"), fx(() -> displayedTokens(composer)));
    } finally { fx(() -> { composer.close(); return null; }); }
  }

  private static void waitUntil(Supplier<Boolean> condition) throws Exception {
    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
    while (!fx(condition)) { if (System.nanoTime() > deadline) fail("Condition did not become true"); Thread.sleep(10); }
  }

  @Test void lateAuthorityDocumentationCannotOverwriteNewSelection() throws Exception {
    var started = new CountDownLatch(1); var released = new CountDownLatch(1); var latest = new CountDownLatch(1);
    var host = (Reasoner) Proxy.newProxyInstance(Reasoner.class.getClassLoader(), new Class<?>[]{Reasoner.class}, (p, method, args) -> {
      if (method.getName().equals("getUrl")) return java.net.URI.create("http://localhost:8091/reasoner").toURL();
      if (method.getName().equals("searchAuthority")) return new AuthoritySearchResponse(AuthoritySearchResponse.Status.OK,
          List.of(identity("old", "Old"), identity("new", "New")), 2, -1, List.of());
      if (method.getName().equals("getAuthorityDocumentation")) {
        if (args[1].equals("old")) { started.countDown();
          boolean done = false; while (!done) { try { done = released.await(5, TimeUnit.SECONDS); } catch (InterruptedException ignored) {} }
          throw new IllegalStateException("Old documentation failed");
        }
        latest.countDown(); return Map.of();
      }
      return null;
    });
    var browser = fx(() -> new AuthorityBrowser(() -> authoritySource(host), s -> {}, () -> {}, () -> {}, () -> {}));
    try {
      waitUntil(() -> browser.chooser.getItems().size() == 1);
      fx(() -> { browser.search("ab"); return null; });
      assertTrue(started.await(5, TimeUnit.SECONDS));
      fx(() -> { browser.results.getSelectionModel().select(1); return null; });
      assertTrue(latest.await(5, TimeUnit.SECONDS)); released.countDown(); Thread.sleep(100);
      fx(() -> {
        var content = (javafx.scene.layout.VBox) ((ScrollPane) browser.view.getItems().get(1)).getContent();
        assertTrue(content.getChildren().stream().filter(Label.class::isInstance).map(Label.class::cast)
            .noneMatch(label -> label.getText().contains("Old"))); return null;
      });
    } finally { released.countDown(); fx(() -> { browser.close(); return null; }); }
  }

  @Test void authorityChooserIncludesOnlySearchableLocalBindings() throws Exception {
    var host = new FakeSearch().reasoner(); var snapshot = authoritySource(host); var searchable = snapshot.bindings().getFirst();
    var descriptor = new org.integratedmodelling.klab.api.services.runtime.extension.Extensions.AuthorityDescriptor("hidden-provider", null, true, false, List.of("RANK"), List.of());
    var hidden = new org.integratedmodelling.klab.api.knowledge.Worldview.AuthorityBinding("HIDDEN", "test:Identity", "test", 1, 1, "hash", descriptor, "component", null, host.getUrl());
    var browser = fx(() -> new AuthorityBrowser(() -> new AuthorityBrowser.Source(null, List.of(searchable, hidden), List.of(host)),
        s -> {}, () -> {}, () -> {}, () -> {}));
    try { waitUntil(() -> !browser.chooser.getItems().isEmpty());
      assertEquals(List.of("TAXA"), fx(() -> List.copyOf(browser.chooser.getItems())));
    } finally { fx(() -> { browser.close(); return null; }); }
  }

  @Test void authorityInsertionWithoutConfirmedTokenNeverClearsQueryOrPrefix() throws Exception {
    for (boolean erasePrefix : List.of(false, true)) {
      var fake = new FakeSearch(); fake.code.add(token("test:Tree"));
      fake.authoritySearch = request -> new AuthoritySearchResponse(AuthoritySearchResponse.Status.OK,
          List.of(identity("A1", "First")), 1, -1, List.of());
      fake.after = (request, reply) -> {
        if (request.getSearchMode() == SemanticSearchRequest.Mode.IDENTITY) {
          fake.code.removeLast();
          reply.setCode(erasePrefix ? List.of() : List.copyOf(fake.code));
          if (erasePrefix) reply.getErrors().add("Illegal expression");
        }
      };
      var host = fake.reasoner();
      var composer = fx(() -> new SemanticComposer(() -> host, o -> CompletableFuture.completedFuture(null), () -> {},
          null, javafx.util.Duration.seconds(30), () -> authoritySource(host)));
      try {
        ready(composer); waitUntil(() -> ((ComboBox<?>) composer.lookup("#semantic-authority")).getItems().size() == 1);
        fx(() -> { ((ComboBox<String>) composer.lookup("#semantic-source")).setValue("Authorities");
          query(composer).setText("ab"); query(composer).positionCaret(2); return null; });
        waitUntil(() -> ((TableView<?>) composer.lookup("#authority-results")).getItems().size() == 1);
        fx(() -> { press(query(composer), KeyCode.ENTER); release(query(composer), KeyCode.ENTER); return null; }); ready(composer);
        assertEquals("ab", fx(() -> query(composer).getText()));
        assertEquals(List.of("test:Tree"), fx(() -> displayedTokens(composer)));
        assertTrue(fx(() -> ((Label) composer.lookup("#semantic-status")).getText().contains("preserved")));
        fx(() -> { ((Button) composer.lookup("#semantic-continue")).fire(); return null; }); ready(composer);
        assertEquals(List.of("test:Tree"), fx(() -> displayedTokens(composer)));
        assertEquals(2, fake.count(SemanticSearchRequest.Mode.TOKEN));
      } finally { fx(() -> { composer.close(); return null; }); }
    }
  }

  @Test void complexAuthorityCodeReplacesSearchAsOneLiteralStyledToken() throws Exception {
    var fake = new FakeSearch(); String code = "Homo sapiens] \\ specimen";
    fake.authoritySearch = request -> new AuthoritySearchResponse(AuthoritySearchResponse.Status.OK,
        List.of(identity(code, "Complex identity")), 1, -1, List.of());
    var host = fake.reasoner();
    var composer = fx(() -> new SemanticComposer(() -> host, o -> CompletableFuture.completedFuture(null), () -> {},
        null, javafx.util.Duration.seconds(30), () -> authoritySource(host)));
    try {
      ready(composer); waitUntil(() -> ((ComboBox<?>) composer.lookup("#semantic-authority")).getItems().size() == 1);
      fx(() -> { ((ComboBox<String>) composer.lookup("#semantic-source")).setValue("Authorities");
        query(composer).setText("Homo"); return null; });
      waitUntil(() -> ((TableView<?>) composer.lookup("#authority-results")).getItems().size() == 1);
      fx(() -> { press(composer.lookup("#authority-results"), KeyCode.ENTER); return null; }); ready(composer);
      assertEquals(List.of(AuthorityIdentitySyntax.encode("TAXA", code)), fx(() -> displayedTokens(composer)));
      assertEquals("", fx(() -> query(composer).getText()));
      assertEquals(code, fake.requests.stream().filter(r -> r.getSearchMode() == SemanticSearchRequest.Mode.IDENTITY).findFirst().orElseThrow().getIdentityCode());
      fx(() -> { press(query(composer), KeyCode.BACK_SPACE); return null; }); ready(composer);
      assertTrue(fx(() -> displayedTokens(composer).isEmpty()));
    } finally { fx(() -> { composer.close(); return null; }); }
  }

  @Test void liveFagusInsertionPayloadDisplaysAuthorityPrefixAndDocumentation() throws Exception {
    var payload = org.integratedmodelling.common.utils.Utils.Json.parseObject(java.nio.file.Files.readString(
        java.nio.file.Path.of("src/test/resources/semantic-composer/authority-insertion.json")), SemanticSearchResponse.class);
    assertEquals("TAXA:[3DSK5]", payload.getCode().getFirst().getValue());
    var fake = new FakeSearch();
    fake.authoritySearch = request -> new AuthoritySearchResponse(AuthoritySearchResponse.Status.OK,
        List.of(identity("3DSK5", "Fagus sylvatica L.")), 1, -1, List.of());
    fake.after = (request, reply) -> { if (request.getSearchMode() == SemanticSearchRequest.Mode.IDENTITY) {
      reply.setCode(payload.getCode()); reply.setCurrentConcept(payload.getCurrentConcept()); reply.setClauses(payload.getClauses());
      reply.setDeclaration(payload.getDeclaration()); reply.setCanUndo(payload.isCanUndo());
    }};
    var host = fake.reasoner();
    var composer = fx(() -> new SemanticComposer(() -> host, o -> CompletableFuture.completedFuture(null), () -> {},
        null, javafx.util.Duration.seconds(30), () -> authoritySource(host)));
    try {
      ready(composer); waitUntil(() -> ((ComboBox<?>) composer.lookup("#semantic-authority")).getItems().size() == 1);
      fx(() -> { ((ComboBox<String>) composer.lookup("#semantic-source")).setValue("Authorities");
        query(composer).setText("Fagus"); query(composer).positionCaret(5); return null; });
      waitUntil(() -> ((TableView<?>) composer.lookup("#authority-results")).getItems().size() == 1);
      fx(() -> { press(query(composer), KeyCode.ENTER); return null; }); ready(composer);
      assertEquals(List.of("TAXA:[3DSK5]"), fx(() -> displayedTokens(composer)));
      assertEquals("", fx(() -> query(composer).getText()));
      assertTrue(fx(() -> composer.lookup("#semantic-card").isVisible()));
      assertEquals("", fx(() -> ((Label) composer.lookup("#semantic-status")).getText()));
    } finally { fx(() -> { composer.close(); return null; }); }
  }

  @Test void identityCanBeCopiedAndSubmittedOnlyWithoutPendingQuery() throws Exception {
    var originalClipboard = fx(() -> {
      var clipboard = javafx.scene.input.Clipboard.getSystemClipboard();
      var saved = new HashMap<javafx.scene.input.DataFormat, Object>();
      clipboard.getContentTypes().forEach(type -> saved.put(type, clipboard.getContent(type)));
      return saved;
    });
    var payload = org.integratedmodelling.common.utils.Utils.Json.parseObject(java.nio.file.Files.readString(
        java.nio.file.Path.of("src/test/resources/semantic-composer/authority-insertion.json")), SemanticSearchResponse.class);
    var fake = new FakeSearch(); fake.code.addAll(payload.getCode());
    fake.after = (request, reply) -> {
      reply.setDeclaration(payload.getDeclaration()); reply.setCurrentConcept(payload.getCurrentConcept());
    };
    var accepted = new CompletableFuture<org.integratedmodelling.klab.api.knowledge.Observable>();
    var composer = fx(() -> new SemanticComposer(() -> fake.reasoner(),
        o -> { accepted.complete(o); return CompletableFuture.completedFuture(null); }, () -> {}));
    try {
      ready(composer);
      fx(() -> {
        var copy = (Button) composer.lookup("#semantic-copy"); var submit = (Button) composer.lookup("#semantic-continue");
        assertFalse(copy.isDisabled()); assertFalse(submit.isDisabled());
        copy.fire(); assertEquals("TAXA:[3DSK5]", javafx.scene.input.Clipboard.getSystemClipboard().getString());
        query(composer).setText("unresolved"); return null;
      }); ready(composer);
      fx(() -> {
        assertTrue(((Button) composer.lookup("#semantic-copy")).isDisabled());
        assertTrue(((Button) composer.lookup("#semantic-continue")).isDisabled());
        Event.fireEvent(query(composer), new KeyEvent(KeyEvent.KEY_PRESSED, "", "", KeyCode.ENTER, false, true, false, false));
        assertFalse(accepted.isDone());
        query(composer).clear(); release(query(composer), KeyCode.ENTER); return null;
      }); ready(composer);
      fx(() -> { ((Button) composer.lookup("#semantic-continue")).fire(); return null; });
      var result = accepted.get(10, TimeUnit.SECONDS);
      assertEquals("TAXA:[3DSK5]", result.getUrn());
      assertSame(payload.getCurrentConcept(), result.getSemantics());
    } finally { fx(() -> {
      composer.close(); javafx.scene.input.Clipboard.getSystemClipboard().setContent(originalClipboard); return null;
    }); }
  }

  @Test void markdownDocumentationIsBoundedAndPublicUrlsReceiveNoCredentials() throws Exception {
    var authorization = new java.util.concurrent.atomic.AtomicReference<String>();
    var server = com.sun.net.httpserver.HttpServer.create(new java.net.InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext("/public", exchange -> {
      authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
      byte[] content = "# Identity\nDocumentation".getBytes(java.nio.charset.StandardCharsets.UTF_8);
      exchange.sendResponseHeaders(200, content.length);
      try (var output = exchange.getResponseBody()) { output.write(content); }
    });
    server.createContext("/large", exchange -> {
      byte[] content = new byte[1_048_577]; exchange.sendResponseHeaders(200, content.length);
      try (var output = exchange.getResponseBody()) { output.write(content); }
    });
    server.start();
    try {
      var host = new FakeSearch().reasoner();
      var scope = (org.integratedmodelling.klab.api.scope.UserScope) Proxy.newProxyInstance(
          org.integratedmodelling.klab.api.scope.UserScope.class.getClassLoader(), new Class<?>[]{org.integratedmodelling.klab.api.scope.UserScope.class},
          (p, method, args) -> { fail("Public documentation must not request authentication"); return null; });
      String base = "http://127.0.0.1:" + server.getAddress().getPort();
      assertEquals("# Identity\nDocumentation", AuthorityBrowser.readMarkdown(java.net.URI.create(base + "/public").toURL(), host, scope));
      assertNull(authorization.get());
      assertThrows(IllegalStateException.class, () -> AuthorityBrowser.readMarkdown(java.net.URI.create(base + "/large").toURL(), host, scope));
      assertThrows(IllegalArgumentException.class, () -> AuthorityBrowser.readMarkdown(java.net.URI.create("file:///C:/private.md").toURL(), host, scope));
    } finally { server.stop(0); }
  }
  private static AuthorityBrowser.Source authoritySource(Reasoner host) {
    var descriptor = new org.integratedmodelling.klab.api.services.runtime.extension.Extensions.AuthorityDescriptor("provider", null, true, true, List.of(), List.of());
    return new AuthorityBrowser.Source(null, List.of(new org.integratedmodelling.klab.api.knowledge.Worldview.AuthorityBinding(
        "TAXA", "test:Identity", "test", 0, 1, "hash", descriptor, "component", null, host.getUrl())), List.of(host));
  }
  private static org.integratedmodelling.klab.api.services.resources.objects.AuthorityIdentity identity(String code, String label) {
    var identity = new org.integratedmodelling.klab.api.services.resources.objects.AuthorityIdentity();
    identity.setId(code); identity.setLabel(label); identity.setDescription("Documentation for " + label); return identity;
  }
  private static TextField query(SemanticComposer composer) {
    var input = (javafx.scene.layout.StackPane) composer.lookup("#semantic-input");
    var scroll = (ScrollPane) input.getChildren().getFirst();
    return (TextField) ((HBox) scroll.getContent()).getChildren().getLast();
  }
  private static void ready(SemanticComposer composer) throws Exception {
    fx(() -> {
      if (composer.getScene() == null) new javafx.scene.Scene(composer);
      composer.applyCss(); composer.layout();
      return null;
    });
    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
    while (fx(() -> composer.lookup("#semantic-progress").isVisible())) {
      if (System.nanoTime() > deadline) fail("Composer did not finish its request");
      Thread.sleep(10);
    }
  }
  private static void typed(TextField target, String value) {
    Event.fireEvent(target, new KeyEvent(KeyEvent.KEY_TYPED, value, "", KeyCode.UNDEFINED, false, false, false, false));
  }
  private static void press(javafx.scene.Node target, KeyCode code) {
    Event.fireEvent(target, new KeyEvent(KeyEvent.KEY_PRESSED, "", "", code, false, false, false, false));
  }
  private static void release(TextField target, KeyCode code) {
    Event.fireEvent(target, new KeyEvent(KeyEvent.KEY_RELEASED, "", "", code, false, false, false, false));
  }
  private static <T> T fx(Supplier<T> action) throws Exception {
    var result = new CompletableFuture<T>();
    Platform.runLater(() -> { try { result.complete(action.get()); } catch (Throwable t) { result.completeExceptionally(t); } });
    return result.get(30, TimeUnit.SECONDS);
  }

  private static class FakeSearch {
    final List<StyledKimToken> code = new ArrayList<>();
    final List<SemanticSearchRequest.Mode> calls = new CopyOnWriteArrayList<>();
    final List<String> selections = new CopyOnWriteArrayList<>();
    final List<SemanticSearchRequest> requests = new CopyOnWriteArrayList<>();
    java.util.function.Consumer<SemanticSearchRequest> before = request -> {};
    java.util.function.BiConsumer<SemanticSearchRequest, SemanticSearchResponse> after = (request, reply) -> {};
    boolean selectAddsToken;
    List<SemanticMatch> matches = List.of(match("test:Tree"));
    boolean value;
    org.integratedmodelling.common.knowledge.ObservableImpl observable;
    java.util.function.Function<AuthoritySearchRequest, AuthoritySearchResponse> authoritySearch = request -> null;
    final List<String> documentationCalls = new CopyOnWriteArrayList<>();
    long count(SemanticSearchRequest.Mode mode) { return calls.stream().filter(m -> m == mode).count(); }
    Reasoner reasoner() {
      return (Reasoner) Proxy.newProxyInstance(Reasoner.class.getClassLoader(), new Class<?>[]{Reasoner.class}, (p, method, args) -> {
        if (method.getName().equals("getUrl")) return java.net.URI.create("http://localhost:8091/reasoner").toURL();
        if (method.getName().equals("searchAuthority")) return authoritySearch.apply((AuthoritySearchRequest) args[0]);
        if (method.getName().equals("getAuthorityDocumentation")) { documentationCalls.add((String) args[1]); return Map.of(); }
        if (!method.getName().equals("semanticSearch")) return null;
        var request = (SemanticSearchRequest) args[0];
        var reply = new SemanticSearchResponse(42, request.getRequestId());
        if (request.isCancelSearch()) return reply;
        calls.add(request.getSearchMode());
        requests.add(request); before.accept(request);
        if (request.getSearchMode() == SemanticSearchRequest.Mode.SELECT) selections.add(request.getSelectedMatchId());
        switch (request.getSearchMode()) {
          case OPEN_SCOPE -> code.add(StyledKimToken.create("("));
          case CLOSE_SCOPE -> code.add(StyledKimToken.create(")"));
          case UNDO -> { if (!code.isEmpty()) code.removeLast(); }
          case SELECT -> { if (selectAddsToken) code.add(token(request.getSelectedMatchId())); }
          case IDENTITY -> code.add(token(AuthorityIdentitySyntax.encode(request.getAuthority(), request.getIdentityCode())));
          default -> { }
        }
        reply.setCode(List.copyOf(code));
        reply.setMatches(matches); reply.setObservable(observable);
        reply.setCanOpenScope(true); // Deliberately permissive to exercise the client guard.
        reply.setCanUndo(!code.isEmpty()); reply.setAcceptsValue(value);
        long depth = code.stream().filter(t -> t.getValue().equals("(")).count()
            - code.stream().filter(t -> t.getValue().equals(")")).count();
        reply.setCanCloseScope(depth > 0 && !code.getLast().getValue().equals("("));
        after.accept(request, reply);
        return reply;
      });
    }
  }
}
