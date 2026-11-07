package org.integratedmodelling.klab.ide.components;

import java.util.function.Consumer;
import java.util.function.Supplier;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.layout.VBox;
import org.integratedmodelling.klab.api.services.resources.workflow.Workflow;
import org.integratedmodelling.klab.ide.utils.AsyncLoad;

/** Visible, cancellable opening state. Closing never permits a late result to reopen a tab. */
final class WorkflowOpening extends VBox implements AutoCloseable {
  private final AsyncLoad<Workflow> load = new AsyncLoad<>();
  private final Label message = new Label();
  private final ProgressIndicator progress = new ProgressIndicator();
  private final Button retry = new Button("Retry opening");
  private boolean loading;
  private boolean closed;

  WorkflowOpening(Runnable cancel) {
    super(12);
    setId("workflow-opening");
    setAlignment(Pos.CENTER);
    setPadding(new Insets(24));
    message.setWrapText(true);
    message.setId("workflow-opening-status");
    progress.setMaxSize(32, 32);
    var close = new Button("Cancel opening");
    close.setId("workflow-cancel-opening");
    close.setOnAction(event -> { close(); cancel.run(); });
    retry.setId("workflow-retry-opening");
    getChildren().addAll(progress, message, retry, close);
  }

  void start(Supplier<Workflow> request, Consumer<Workflow> opened) {
    if (loading || closed) return;
    loading = true;
    message.setText("Opening workflow: loading its saved definition...");
    progress.setVisible(true);
    retry.setVisible(false);
    retry.setManaged(false);
    retry.setDisable(true);
    retry.setOnAction(event -> start(request, opened));
    load.load(() -> {
      var result = request.get();
      if (result == null) throw new IllegalStateException("The pinned workflow definition is unavailable.");
      return result;
    }, result -> {
      loading = false;
      try { opened.accept(result); }
      catch (Throwable error) { failed(error); }
    }, this::failed);
  }

  private void failed(Throwable error) {
    loading = false;
    progress.setVisible(false);
    message.setText("Could not open the workflow. " + error.getMessage());
    retry.setVisible(true);
    retry.setManaged(true);
    retry.setDisable(false);
  }

  @Override public void close() { closed = true; load.invalidate(); }
}
