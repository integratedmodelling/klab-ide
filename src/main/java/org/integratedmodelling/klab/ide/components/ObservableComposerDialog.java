package org.integratedmodelling.klab.ide.components;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.stage.Modality;
import javafx.stage.Stage;
import org.integratedmodelling.klab.api.knowledge.Observable;
import org.integratedmodelling.klab.api.services.Reasoner;
import org.integratedmodelling.klab.ide.KlabIDEController;

/** Reusable selection-only host: returns an Observable, or null when dismissed. */
public final class ObservableComposerDialog {
  private ObservableComposerDialog() {}

  public static CompletableFuture<Observable> show(Node owner) {
    return show(owner, () -> KlabIDEController.scope().getService(Reasoner.class));
  }

  static CompletableFuture<Observable> show(
      Node owner, java.util.function.Supplier<Reasoner> reasoner) {
    var result = new CompletableFuture<Observable>();
    Platform.runLater(
        () -> {
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
                    stage::close);
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
        });
    return result;
  }
}
