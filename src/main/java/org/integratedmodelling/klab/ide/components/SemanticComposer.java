package org.integratedmodelling.klab.ide.components;

import java.util.concurrent.*;
import java.util.function.*;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import org.integratedmodelling.klab.ide.Theme;
import javafx.scene.layout.*;
import javafx.util.Duration;
import org.integratedmodelling.klab.api.knowledge.Observable;
import org.integratedmodelling.klab.api.services.Reasoner;
import org.integratedmodelling.klab.api.services.reasoner.objects.*;
import org.integratedmodelling.klab.ide.components.cards.ObservableCard;

/** Reusable Reasoner-driven composer. Its host decides what Continue does with the observable. */
public final class SemanticComposer extends VBox implements AutoCloseable {
  private final TextField query = new TextField();
  private final TableView<SemanticMatch> results = new TableView<>();
  private final VBox expression = new VBox(new Label("Start by choosing a concept or operator"));
  private final Label status = new Label();
  private final VBox card = new VBox();
  private final Button undo = new Button("Undo"), open = new Button("("), closeGroup = new Button(")"),
      choose = new Button("Add selected"), addValue = new Button("Add value"),
      proceed = new Button("Continue"), reset = new Button("Start over"), cancel = new Button("Cancel");
  private final ProgressIndicator progress = new ProgressIndicator();
  private final PauseTransition debounce = new PauseTransition(Duration.millis(250));
  private final ExecutorService worker = Executors.newSingleThreadExecutor(r -> {
    var thread = new Thread(r, "semantic-composer"); thread.setDaemon(true); return thread;
  });
  private final Supplier<Reasoner> reasonerSupplier;
  private final Function<Observable, CompletableFuture<?>> action;
  private final Runnable dismiss;
  private Reasoner reasoner;
  private int searchId;
  private volatile int generation;
  private int requestId;
  private boolean closed, updating, busy, submitting;
  private boolean parenthesisKeyHeld, backspaceKeyHeld;
  private SemanticSearchResponse response;

  public SemanticComposer(Supplier<Reasoner> reasonerSupplier,
      Function<Observable, CompletableFuture<?>> action, Runnable dismiss) {
    this.reasonerSupplier = reasonerSupplier; this.action = action; this.dismiss = dismiss;
    setSpacing(10); setPadding(new Insets(18)); setPrefSize(780, 560); setMaxSize(900, 680);
    setStyle("-fx-background-color: -color-bg-default; -fx-background-radius: 8;");
    var title = new Label("Compose an observable");
    title.setStyle("-fx-font-size: 18; -fx-font-weight: bold;");
    query.setPromptText("Find the next concept, operator, or clause");
    expression.setStyle("-fx-font-family: monospace; -fx-padding: 10; -fx-background-color: -color-bg-subtle;");
    expression.setMaxWidth(Double.MAX_VALUE); status.setWrapText(true);
    results.getColumns().add(column("Name", SemanticMatch::getName, 190));
    results.getColumns().add(column("Identifier / operator", SemanticMatch::getId, 210));
    results.getColumns().add(column("Description", SemanticMatch::getDescription, 330));
    results.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
    results.setPlaceholder(new Label("No matching components"));
    VBox.setVgrow(results, Priority.ALWAYS);
    progress.setPrefSize(18, 18); progress.setMaxSize(18, 18);
    var spacer = new Region(); HBox.setHgrow(spacer, Priority.ALWAYS);
    var actions = new HBox(8, undo, open, closeGroup, choose, addValue, spacer, progress, reset, cancel, proceed);
    getChildren().addAll(title, expression, query, results, card, status, actions);
    query.textProperty().addListener((o, old, value) -> {
      if (updating || closed || submitting) return;
      generation++; busy = true; results.getItems().clear(); updateControls(); debounce.playFromStart();
    });
    debounce.setOnFinished(e -> send(SemanticSearchRequest.Mode.TOKEN, null));
    query.setOnAction(e -> {
      if (!addValue.isDisabled()) send(SemanticSearchRequest.Mode.VALUE, null); else accept();
    });
    // Filters run before TextField's own editing behavior; structural keys never enter the query
    // or invalidate an in-flight edit through the text-change listener.
    query.addEventFilter(KeyEvent.KEY_TYPED, e -> {
      String character = e.getCharacter();
      if ((!"(".equals(character) && !")".equals(character))
          || (response != null && response.isAcceptsValue())) return;
      e.consume();
      if (parenthesisKeyHeld) return;
      parenthesisKeyHeld = true;
      if ("(".equals(character) && !open.isDisabled()) open.fire();
      else if (")".equals(character) && !closeGroup.isDisabled()) closeGroup.fire();
    });
    query.addEventFilter(KeyEvent.KEY_PRESSED, e -> {
      if (e.getCode() == KeyCode.BACK_SPACE) {
        boolean repeated = backspaceKeyHeld;
        backspaceKeyHeld = true;
        if (query.getText().isEmpty()) {
          e.consume();
          if (!repeated && !undo.isDisabled()) undo.fire();
        }
      } else if (e.getCode() == KeyCode.DOWN && !results.getItems().isEmpty()) {
        results.requestFocus(); results.getSelectionModel().selectFirst(); e.consume();
      }
    });
    query.addEventFilter(KeyEvent.KEY_RELEASED, e -> {
      if (e.getCode() == KeyCode.BACK_SPACE) backspaceKeyHeld = false;
      if (!e.getCode().isModifierKey()) parenthesisKeyHeld = false;
    });
    query.focusedProperty().addListener((o, old, focused) -> {
      if (!focused) { parenthesisKeyHeld = false; backspaceKeyHeld = false; }
    });
    results.setOnMouseClicked(e -> { if (e.getClickCount() == 2) accept(); });
    results.setOnKeyPressed(e -> { if (e.getCode() == KeyCode.ENTER) { accept(); e.consume(); } });
    results.getSelectionModel().selectedItemProperty().addListener((o, a, b) -> updateControls());
    undo.setOnAction(e -> send(SemanticSearchRequest.Mode.UNDO, null));
    open.setOnAction(e -> send(SemanticSearchRequest.Mode.OPEN_SCOPE, null));
    closeGroup.setOnAction(e -> send(SemanticSearchRequest.Mode.CLOSE_SCOPE, null));
    choose.setOnAction(e -> accept()); addValue.setOnAction(e -> send(SemanticSearchRequest.Mode.VALUE, null));
    reset.setOnAction(e -> restart()); cancel.setOnAction(e -> dismiss()); proceed.setOnAction(e -> finish());
    setOnKeyPressed(e -> {
      if (e.getCode() == KeyCode.ESCAPE && !submitting) { dismiss(); e.consume(); }
      else if (e.getCode() == KeyCode.ENTER && e.isControlDown() && !proceed.isDisabled()) { finish(); e.consume(); }
    });
    parentProperty().addListener((o, old, parent) -> { if (old != null && parent == null) close(); });
    send(SemanticSearchRequest.Mode.TOKEN, null);
  }

  private TableColumn<SemanticMatch, String> column(String title, Function<SemanticMatch, String> text, int width) {
    var column = new TableColumn<SemanticMatch, String>(title);
    column.setCellValueFactory(value -> new ReadOnlyStringWrapper(text.apply(value.getValue())));
    column.setPrefWidth(width); return column;
  }
  private void accept() {
    if (!choose.isDisabled()) send(SemanticSearchRequest.Mode.SELECT, results.getSelectionModel().getSelectedItem());
  }
  private void send(SemanticSearchRequest.Mode mode, SemanticMatch match) {
    if (closed || submitting) return;
    debounce.stop(); int revision = ++generation;
    var request = new SemanticSearchRequest(); request.setRequestId(++requestId);
    request.setSearchMode(mode); request.setQueryString(query.getText()); request.setMaxResults(30);
    if (match != null) {
      request.setSelectedMatchId(match.getId()); request.setMatchesRequestId(response.getRequestId());
    }
    busy = true; status.setText("Searching..."); updateControls();
    worker.execute(() -> {
      try {
        if (mode == SemanticSearchRequest.Mode.TOKEN && revision != generation) return;
        if (reasoner == null) reasoner = reasonerSupplier.get();
        if (reasoner == null) throw new IllegalStateException("No Reasoner is available.");
        request.setSearchId(searchId);
        var reply = reasoner.semanticSearch(request);
        if (reply == null) throw new IllegalStateException("The Reasoner returned no search response.");
        searchId = reply.getSearchId();
        Platform.runLater(() -> {
          if (closed || revision != generation) return;
          response = reply; busy = false;
          if (mode != SemanticSearchRequest.Mode.TOKEN && reply.getErrors().isEmpty()) {
            updating = true; query.clear(); updating = false;
          }
          expression.getChildren().setAll(reply.getCode().isEmpty()
              ? new Label("Start by choosing a concept or operator")
              : Theme.semanticExpression(reply.getCode()));
          results.getItems().setAll(reply.getMatches()); results.getSelectionModel().selectFirst();
          card.getChildren().clear();
          if (reply.getCurrentConcept() != null)
            card.getChildren().add(new ObservableCard(reply.getCurrentConcept(), reply.getClauses()));
          else if (reply.getObservable() != null) card.getChildren().add(new ObservableCard(reply.getObservable(), true));
          status.setText(!reply.getErrors().isEmpty() ? String.join("\n", reply.getErrors())
              : reply.isAcceptsValue() ? "Enter a number, boolean, or quoted text value, then press Enter."
              : reply.getObservable() != null ? "Observable validated. Continue when ready, or add another clause."
              : "Choose a component to continue the expression.");
          updateControls();
        });
      } catch (Exception ex) {
        Platform.runLater(() -> {
          if (closed || revision != generation) return;
          busy = false; response = null; results.getItems().clear(); card.getChildren().clear();
          status.setText("Search failed: " + message(ex) + ". Use Start over to retry."); updateControls();
        });
      }
    });
  }
  private void updateControls() {
    boolean idle = !closed && !busy && !submitting && response != null;
    undo.setDisable(!idle || !response.isCanUndo());
    open.setDisable(!idle || !response.isCanOpenScope() || endsWithOpeningParenthesis());
    closeGroup.setDisable(!idle || !response.isCanCloseScope());
    choose.setDisable(!idle || results.getSelectionModel().getSelectedItem() == null);
    addValue.setDisable(!idle || !response.isAcceptsValue());
    proceed.setDisable(!idle || response.getObservable() == null || !response.getErrors().isEmpty());
    query.setDisable(submitting || closed); results.setDisable(!idle);
    reset.setDisable(submitting || closed); cancel.setDisable(submitting || closed);
    progress.setVisible(busy || submitting);
  }
  private boolean endsWithOpeningParenthesis() {
    return response != null && !response.getCode().isEmpty()
        && "(".equals(response.getCode().getLast().getValue());
  }

  private void finish() {
    if (proceed.isDisabled()) return;
    submitting = true; debounce.stop(); updateControls(); status.setText("Continuing...");
    try {
      action.apply(response.getObservable()).whenComplete((value, failure) -> Platform.runLater(() -> {
        if (closed) return;
        submitting = false;
        if (failure == null) dismiss(); else { status.setText(message(failure)); updateControls(); }
      }));
    } catch (Exception ex) { submitting = false; status.setText(message(ex)); updateControls(); }
  }
  private void restart() {
    generation++; debounce.stop(); worker.execute(this::cancelSession); response = null; card.getChildren().clear();
    expression.getChildren().setAll(new Label("Start by choosing a concept or operator"));
    updating = true; query.clear(); updating = false; send(SemanticSearchRequest.Mode.TOKEN, null);
  }
  private void cancelSession() {
    if (reasoner != null && searchId != 0) {
      var request = new SemanticSearchRequest(); request.setSearchId(searchId); request.setCancelSearch(true);
      try { reasoner.semanticSearch(request); } catch (RuntimeException ignored) { /* Server expires idle sessions. */ }
    }
    searchId = 0;
  }
  private void dismiss() { close(); dismiss.run(); }
  @Override public void close() {
    if (closed) return;
    closed = true; generation++; debounce.stop(); worker.execute(this::cancelSession); worker.shutdown();
  }
  private static String message(Throwable failure) {
    while (failure.getCause() != null) failure = failure.getCause();
    return failure.getMessage() == null ? failure.getClass().getSimpleName() : failure.getMessage();
  }
}
