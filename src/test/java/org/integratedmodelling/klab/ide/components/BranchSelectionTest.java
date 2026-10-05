package org.integratedmodelling.klab.ide.components;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import javafx.application.Platform;
import javafx.scene.control.*;
import javafx.stage.Window;
import org.integratedmodelling.klab.api.data.RepositoryState;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class BranchSelectionTest {
  @BeforeAll
  static void startFx() throws Exception {
    var ready = new CompletableFuture<Void>();
    try {
      Platform.startup(() -> { Platform.setImplicitExit(false); ready.complete(null); });
    } catch (IllegalStateException started) {
      Platform.runLater(() -> ready.complete(null));
    }
    ready.get(15, TimeUnit.SECONDS);
  }

  @Test
  void selectExistingCreateNewMergeAndCancel() throws Exception {
    var done = new CompletableFuture<Void>();
    Platform.runLater(() -> {
      try {
        var state = new RepositoryState();
        state.setCurrentBranch("main");
        state.getBranchNames().addAll(List.of("main", "feature/shared"));
        for (String action : List.of("existing", "new", "merge", "cancel")) {
          var checked = new CompletableFuture<Void>();
          Platform.runLater(() -> {
            Window window = null;
            try {
              window = Window.getWindows().stream()
                  .filter(w -> w.getScene().getRoot() instanceof DialogPane).findFirst().orElseThrow();
              var pane = (DialogPane) window.getScene().getRoot();
              var choices = (ComboBox<?>) pane.getContent();
              assertEquals(List.of("feature/shared"), choices.getItems());
              assertEquals(!action.equals("merge"), choices.isEditable());
              if (action.equals("new")) choices.getEditor().setText("feature/new");
              var type = pane.getButtonTypes().stream().filter(button ->
                  button.getButtonData() == (action.equals("cancel")
                      ? ButtonBar.ButtonData.CANCEL_CLOSE : ButtonBar.ButtonData.OK_DONE)).findFirst().orElseThrow();
              ((Button) pane.lookupButton(type)).fire();
              checked.complete(null);
            } catch (Throwable error) {
              if (window != null) window.hide();
              checked.completeExceptionally(error);
            }
          });
          var result = WorkspaceEditor.chooseBranch(state, !action.equals("merge"), null);
          checked.join();
          if (action.equals("cancel")) assertNull(result);
          else assertArrayEquals(new String[] {action.equals("new") ? "feature/new" : "feature/shared"}, result);
        }
        done.complete(null);
      } catch (Throwable error) { done.completeExceptionally(error); }
    });
    done.get(20, TimeUnit.SECONDS);
  }
}
