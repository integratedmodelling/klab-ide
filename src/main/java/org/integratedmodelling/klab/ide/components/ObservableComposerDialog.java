package org.integratedmodelling.klab.ide.components;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;
import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.stage.Modality;
import javafx.stage.Stage;
import org.integratedmodelling.klab.api.knowledge.Observable;
import org.integratedmodelling.klab.api.knowledge.SemanticType;
import org.integratedmodelling.klab.api.services.Reasoner;
import org.integratedmodelling.klab.ide.KlabIDEController;
import org.integratedmodelling.klabeditor.MonacoEditorView.ObservableCompositionContext;

/** Reusable selection-only host: returns an Observable, or null when dismissed. */
public final class ObservableComposerDialog {
  private static final Pattern CONCEPT =
      Pattern.compile("[a-z]+(?:\\.[a-z]+)*:[A-Z][A-Za-z0-9]*");

  private ObservableComposerDialog() {}

  public static CompletableFuture<Observable> show(Node owner) {
    return show(owner, () -> KlabIDEController.scope().getService(Reasoner.class), null);
  }

  public static CompletableFuture<Observable> show(
      Node owner, ObservableCompositionContext context) {
    return show(owner, () -> KlabIDEController.scope().getService(Reasoner.class), context);
  }

  static CompletableFuture<Observable> show(
      Node owner, java.util.function.Supplier<Reasoner> reasoner) {
    return show(owner, reasoner, null);
  }

  static CompletableFuture<Observable> show(
      Node owner,
      java.util.function.Supplier<Reasoner> reasoner,
      ObservableCompositionContext context) {
    var result = new CompletableFuture<Observable>();
    String selection = context == null ? null : context.selectedText();
    if (selection != null && !selection.isBlank()) {
      CompletableFuture.supplyAsync(() -> initialState(reasoner, context))
          .whenComplete((initial, failure) -> Platform.runLater(() -> open(owner, reasoner,
              failure == null ? initial : cursorInitialState(context), result)));
    } else {
      Platform.runLater(() -> open(owner, reasoner, cursorInitialState(context), result));
    }
    return result;
  }

  private static SemanticComposer.InitialState initialState(
      java.util.function.Supplier<Reasoner> reasonerSupplier,
      ObservableCompositionContext context) {
    try {
      Reasoner reasoner = reasonerSupplier.get();
      Observable observable = reasoner == null ? null : reasoner.resolveObservable(context.selectedText().trim());
      if (observable != null && !observable.is(SemanticType.NOTHING)) {
        var matcher = CONCEPT.matcher(context.selectedText());
        String firstConcept = matcher.find() ? matcher.group() : observable.getSemantics().getUrn();
        return new SemanticComposer.InitialState(firstConcept, observable);
      }
    } catch (RuntimeException ignored) {
      // An invalid or temporarily unresolvable selection leaves the composer usable.
    }
    return cursorInitialState(context);
  }

  private static SemanticComposer.InitialState cursorInitialState(
      ObservableCompositionContext context) {
    String concept = context == null ? null : context.conceptAtCursor();
    return concept == null || concept.isBlank()
        ? null
        : new SemanticComposer.InitialState(concept, null);
  }

  private static void open(
      Node owner,
      java.util.function.Supplier<Reasoner> reasoner,
      SemanticComposer.InitialState initialState,
      CompletableFuture<Observable> result) {
    if (result.isDone()) return;
    if (owner.getScene() == null || owner.getScene().getWindow() == null) {
      result.complete(null);
      return;
    }
    try {
      var selected = new AtomicReference<Observable>();
      var stage = new Stage();
      stage.initOwner(owner.getScene().getWindow());
      stage.initModality(Modality.WINDOW_MODAL);
      stage.setTitle("Compose observable");
      var composer =
          new SemanticComposer(
              reasoner,
              observable -> {
                selected.set(observable);
                return CompletableFuture.completedFuture(null);
              },
              stage::close,
              initialState);
      var scene = new Scene(composer);
      scene.getStylesheets().setAll(owner.getScene().getStylesheets());
      stage.setScene(scene);
      ChangeListener<Scene> detached =
          (property, before, after) -> {
            if (after != before) {
              selected.set(null);
              stage.close();
            }
          };
      owner.sceneProperty().addListener(detached);
      stage.setOnHidden(
          event -> {
            owner.sceneProperty().removeListener(detached);
            composer.close();
            result.complete(selected.get());
          });
      stage.show();
    } catch (RuntimeException failure) {
      result.completeExceptionally(failure);
    }
  }
}
