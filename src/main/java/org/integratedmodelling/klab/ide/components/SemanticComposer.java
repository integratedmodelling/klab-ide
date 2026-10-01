package org.integratedmodelling.klab.ide.components;

import java.util.concurrent.*;
import java.util.function.*;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.beans.value.ChangeListener;
import javafx.event.EventHandler;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.*;
import javafx.util.Duration;
import org.integratedmodelling.klab.api.knowledge.Observable;
import org.integratedmodelling.klab.api.services.Reasoner;
import org.integratedmodelling.klab.api.services.reasoner.objects.*;
import org.integratedmodelling.klab.ide.Theme;
import org.integratedmodelling.klab.ide.components.cards.ObservableCard;
import org.kordamp.ikonli.javafx.FontIcon;

/** Reusable Reasoner-driven composer. Its host decides what Continue does with the observable. */
public final class SemanticComposer extends VBox implements AutoCloseable {
  private static final java.util.logging.Logger LOG =
      java.util.logging.Logger.getLogger(SemanticComposer.class.getName());

  public record InitialState(String query, Observable observable) {}

  private final TextField query = new TextField();
  private final TableView<SemanticMatch> results = new TableView<>();
  private final HBox inputLine = new HBox();
  private final javafx.scene.text.TextFlow confirmedTokens = new javafx.scene.text.TextFlow();
  private final javafx.scene.text.Text tokenGap = new javafx.scene.text.Text();
  private final ScrollPane inputScroll = new ScrollPane(inputLine);
  private final ComboBox<String> source = new ComboBox<>();
  private final Label status = new Label();
  private final VBox card = new VBox();
  private final Button proceed = new Button();
  private final Button copy = new Button();
  private final ProgressIndicator progress = new ProgressIndicator();
  private final PauseTransition debounce = new PauseTransition(Duration.millis(350));
  private final PauseTransition requestTimeout;
  private ExecutorService worker = newWorker();
  private Future<?> activeTask;
  private SemanticSearchRequest activeRequest;
  private long requestStarted;
  private final Supplier<Reasoner> reasonerSupplier;
  private final Function<Observable, CompletableFuture<?>> action;
  private final Runnable dismiss;
  private Reasoner reasoner;
  private int searchId;
  private int queryRevision;
  private int requestId;
  private boolean closed, updating, busy, submitting;
  private boolean queryQueued, matchesCurrent, stateUncertain, sessionLost;
  private int pendingUndo;
  private boolean parenthesisKeyHeld, backspaceKeyHeld, enterKeyHeld;
  private SemanticSearchResponse response;
  private Observable initialObservable;
  private final AuthorityBrowser authorities;
  private final EventHandler<KeyEvent> releaseKeys =
      e -> {
        if (e.getCode() == KeyCode.ENTER) enterKeyHeld = false;
        if (e.getCode() == KeyCode.BACK_SPACE) backspaceKeyHeld = false;
        if (!e.getCode().isModifierKey()) parenthesisKeyHeld = false;
      };
  private final ChangeListener<Boolean> windowFocus =
      (o, old, focused) -> {
        if (!focused) {
          enterKeyHeld = false;
          backspaceKeyHeld = false;
          parenthesisKeyHeld = false;
        }
      };
  private final ChangeListener<javafx.stage.Window> windowChanged =
      (o, old, window) -> {
        if (old != null) old.focusedProperty().removeListener(windowFocus);
        if (window != null) window.focusedProperty().addListener(windowFocus);
      };

  public SemanticComposer(
      Supplier<Reasoner> reasonerSupplier,
      Function<Observable, CompletableFuture<?>> action,
      Runnable dismiss) {
    this(reasonerSupplier, action, dismiss, null);
  }

  public SemanticComposer(
      Supplier<Reasoner> reasonerSupplier,
      Function<Observable, CompletableFuture<?>> action,
      Runnable dismiss,
      InitialState initialState) {
    this(reasonerSupplier, action, dismiss, initialState, Duration.seconds(30));
  }

  SemanticComposer(
      Supplier<Reasoner> reasonerSupplier,
      Function<Observable, CompletableFuture<?>> action,
      Runnable dismiss,
      InitialState initialState,
      Duration timeout) {
    this(reasonerSupplier, action, dismiss, initialState, timeout, AuthorityBrowser.Source::empty);
  }

  SemanticComposer(Supplier<Reasoner> reasonerSupplier,
      Function<Observable, CompletableFuture<?>> action, Runnable dismiss, InitialState initialState,
      Duration timeout, Supplier<AuthorityBrowser.Source> authoritySource) {
    this.reasonerSupplier = reasonerSupplier;
    this.action = action;
    this.dismiss = dismiss;
    this.requestTimeout = new PauseTransition(timeout);
    requestTimeout.setOnFinished(e -> timedOut());
    this.initialObservable = initialState == null ? null : initialState.observable();
    setId("semantic-composer");
    setSpacing(8);
    setPadding(new Insets(12));
    setPrefWidth(780);
    setPrefHeight(Region.USE_COMPUTED_SIZE);
    setMaxSize(900, Region.USE_PREF_SIZE);
    setStyle(
        "-fx-background-color: -color-bg-default; -fx-background-radius: 8; -fx-font-size: 12px;");
    source.getItems().setAll("Concepts / operators", "Authorities");
    source.getSelectionModel().selectFirst();
    source.setId("semantic-source");
    authorities = new AuthorityBrowser(authoritySource,
        text -> { if (authorityMode() && activeRequest == null) status.setText(text); },
        this::updateControls, this::accept, () -> { if (authorityMode()) sourceChanged(); });
    source.valueProperty().addListener((o, old, value) -> sourceChanged());
    authorities.chooser.visibleProperty().bind(source.valueProperty().isEqualTo("Authorities"));
    authorities.chooser.managedProperty().bind(authorities.chooser.visibleProperty());
    authorities.view.visibleProperty().bind(authorities.chooser.visibleProperty());
    authorities.view.managedProperty().bind(authorities.view.visibleProperty());
    results.setId("semantic-matches");
    results.visibleProperty().bind(authorities.chooser.visibleProperty().not());
    results.managedProperty().bind(results.visibleProperty());
    query.setId("semantic-query");
    query.setAccessibleText("Next semantic token");
    query.setStyle(
        "-fx-background-color: transparent; -fx-border-width: 0; -fx-padding: 0; -fx-font-family: monospace;");
    query.setMinWidth(120);
    HBox.setHgrow(query, Priority.ALWAYS);
    inputLine.setAlignment(Pos.CENTER_LEFT);
    inputLine.setFillHeight(false);
    inputLine.setStyle("-fx-font-family: monospace;");
    confirmedTokens.setMinWidth(Region.USE_PREF_SIZE);
    confirmedTokens.setMaxHeight(Region.USE_PREF_SIZE);
    query.setMaxHeight(Region.USE_PREF_SIZE);
    inputLine.getChildren().addAll(confirmedTokens, tokenGap, query);
    inputScroll.setFitToHeight(true);
    inputScroll.setMinHeight(0);
    inputScroll.setPrefHeight(20);
    inputScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
    inputScroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
    inputScroll.setStyle(
        "-fx-background: transparent; -fx-background-color: transparent; -fx-padding: 0;");
    inputScroll
        .viewportBoundsProperty()
        .addListener((o, old, bounds) -> inputLine.setMinWidth(bounds.getWidth()));
    var input = new StackPane(inputScroll, copy, proceed, progress);
    input.setId("semantic-input");
    input.getStyleClass().add("text-input");
    input.setStyle("-fx-padding: 8 6 8 10;");
    input.setMaxHeight(Region.USE_PREF_SIZE);
    query
        .focusedProperty()
        .addListener(
            (o, old, focused) ->
                input.pseudoClassStateChanged(
                    javafx.css.PseudoClass.getPseudoClass("focused"), focused));
    StackPane.setMargin(inputScroll, new Insets(0, 64, 0, 0));
    StackPane.setAlignment(copy, Pos.CENTER_RIGHT);
    StackPane.setMargin(copy, new Insets(0, 30, 0, 0));
    StackPane.setAlignment(proceed, Pos.CENTER_RIGHT);
    StackPane.setAlignment(progress, Pos.CENTER_RIGHT);
    StackPane.setMargin(progress, new Insets(0, 4, 0, 0));
    proceed.setId("semantic-continue");
    proceed.setGraphic(new FontIcon("mdi-arrow-right"));
    proceed.setAccessibleText("Continue");
    proceed.setTooltip(new Tooltip("Continue (Ctrl+Enter)"));
    proceed.setStyle("-fx-padding: 4; -fx-background-color: transparent;");
    copy.setId("semantic-copy");
    copy.setGraphic(new FontIcon("mdi-content-copy"));
    copy.setAccessibleText("Copy expression");
    copy.setTooltip(new Tooltip("Copy expression"));
    copy.setStyle("-fx-padding: 4; -fx-background-color: transparent;");
    copy.setOnAction(e -> {
      if (!canExport()) return;
      var content = new javafx.scene.input.ClipboardContent();
      content.putString(confirmedDeclaration());
      javafx.scene.input.Clipboard.getSystemClipboard().setContent(content);
    });
    input.setOnMouseClicked(
        e -> {
          if (e.getTarget() != proceed) query.requestFocus();
        });
    status.setId("semantic-status");
    status.setWrapText(true);
    status.managedProperty().bind(status.visibleProperty());
    card.setId("semantic-card");
    card.managedProperty().bind(card.visibleProperty());
    results.getColumns().add(column("Name", SemanticMatch::getName, 190));
    results.getColumns().add(column("Identifier / operator", SemanticMatch::getId, 210));
    results.getColumns().add(column("Description", SemanticMatch::getDescription, 330));
    results.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
    results.setPlaceholder(new Label("No matching components"));
    results.setMinHeight(100);
    results.setPrefHeight(240);
    card.setMinHeight(Region.USE_PREF_SIZE);
    progress.setId("semantic-progress");
    progress.setPrefSize(18, 18);
    progress.setMaxSize(18, 18);
    var spacer = new Region();
    HBox.setHgrow(spacer, Priority.ALWAYS);
    var header = new HBox(8, source, authorities.chooser, spacer);
    header.setAlignment(Pos.CENTER_LEFT);
    progress.managedProperty().bind(progress.visibleProperty());
    getChildren().addAll(header, results, authorities.view, input, card, status);
    query
        .textProperty()
        .addListener(
            (o, old, value) -> {
              if (updating || closed || submitting) return;
              queryRevision++;
              authorities.invalidate();
              if (activeRequest != null && activeRequest.getSearchMode() == SemanticSearchRequest.Mode.TOKEN)
                supersedeQuery();
              queryQueued = true;
              matchesCurrent = false;
              results.getItems().clear();
              if (!sessionLost) {
                if (activeRequest == null) {
                  if (authorityMode()) {
                    busy = true;
                    status.setText("Searching…");
                    debounce.playFromStart();
                  } else if (queryNeedsMoreCharacters()) pauseShortQuery();
                  else {
                    busy = true;
                    status.setText("Searching…");
                    debounce.playFromStart();
                  }
                }
              }
              updateControls();
            });
    debounce.setOnFinished(e -> search());
    query.setOnAction(
        e -> {
          if (!authorityMode() && idle() && response.isAcceptsValue()) send(SemanticSearchRequest.Mode.VALUE, null);
        });
    // Filters run before TextField's own editing behavior; structural keys never enter the query
    // or invalidate an in-flight edit through the text-change listener.
    query.addEventFilter(
        KeyEvent.KEY_TYPED,
        e -> {
          String character = e.getCharacter();
          if ((!"(".equals(character) && !")".equals(character))
              || authorityMode() || (response != null && response.isAcceptsValue())) return;
          e.consume();
          if (parenthesisKeyHeld) return;
          parenthesisKeyHeld = true;
          if ("(".equals(character) && canOpen()) send(SemanticSearchRequest.Mode.OPEN_SCOPE, null);
          else if (")".equals(character) && idle() && response.isCanCloseScope())
            send(SemanticSearchRequest.Mode.CLOSE_SCOPE, null);
        });
    query.addEventFilter(
        KeyEvent.KEY_PRESSED,
        e -> {
          if (e.getCode() == KeyCode.BACK_SPACE) {
            boolean repeated = backspaceKeyHeld;
            backspaceKeyHeld = true;
            if (query.getCaretPosition() == 0 && query.getSelection().getLength() == 0) {
              e.consume();
              if (!repeated && !closed && !submitting) {
                if (sessionLost) restart();
                else if (canUndo()) send(SemanticSearchRequest.Mode.UNDO, null);
                else if (stateUncertain) send(SemanticSearchRequest.Mode.TOKEN, null);
              }
            }
          } else if ((e.getCode() == KeyCode.UP || e.getCode() == KeyCode.DOWN)
              && !e.isControlDown()
              && !e.isAltDown()
              && !e.isMetaDown()) {
            TableView<?> table = authorityMode() ? authorities.results : results;
            if (idle() && !table.getItems().isEmpty()) {
              int selected = table.getSelectionModel().getSelectedIndex();
              int next =
                  selected < 0
                      ? 0
                      : Math.max(
                          0,
                          Math.min(
                              table.getItems().size() - 1,
                              selected + (e.getCode() == KeyCode.DOWN ? 1 : -1)));
              table.getSelectionModel().select(next);
              table.scrollTo(next);
            }
            e.consume();
          }
        });
    query.addEventFilter(
        KeyEvent.KEY_RELEASED,
        e -> {
          if (e.getCode() == KeyCode.BACK_SPACE) backspaceKeyHeld = false;
          if (!e.getCode().isModifierKey()) parenthesisKeyHeld = false;
        });
    query
        .focusedProperty()
        .addListener(
            (o, old, focused) -> {
              if (!focused) {
                parenthesisKeyHeld = false;
                backspaceKeyHeld = false;
              }
            });
    results.setRowFactory(
        table -> {
          var row = new TableRow<SemanticMatch>();
          row.setOnMouseClicked(
              e -> {
                if (e.getButton() == javafx.scene.input.MouseButton.PRIMARY
                    && e.getClickCount() == 2
                    && !row.isEmpty()) {
                  table.getSelectionModel().select(row.getItem());
                  accept();
                  e.consume();
                }
              });
          return row;
        });
    results.getSelectionModel().selectedItemProperty().addListener((o, a, b) -> updateControls());
    proceed.setOnAction(
        e -> {
          if (sessionLost) restart();
          else if (needsRecovery() && !canExport()) send(SemanticSearchRequest.Mode.TOKEN, null);
          else finish();
        });
    addEventFilter(
        KeyEvent.KEY_PRESSED,
        e -> {
          if (e.getCode() == KeyCode.ESCAPE && !submitting) {
            dismiss();
            e.consume();
          } else if (e.getCode() == KeyCode.ENTER) {
            boolean repeated = enterKeyHeld;
            enterKeyHeld = true;
            if (repeated) {
              e.consume();
              return;
            }
            if (e.isControlDown()) {
              if (e.getTarget() == query) finish();
              e.consume();
            } else if ((e.getTarget() == results || e.getTarget() == authorities.results)
                && !e.isAltDown()
                && !e.isMetaDown()
                && !e.isShiftDown()) {
              accept();
              e.consume();
            } else if (e.getTarget() == query
                && !e.isAltDown()
                && !e.isMetaDown()
                && !e.isShiftDown()) {
              if (idle()) {
                if (!authorityMode() && response.isAcceptsValue()) send(SemanticSearchRequest.Mode.VALUE, null);
                else if (!query.getText().isBlank()
                    && query.getCaretPosition() == query.getLength()
                    && query.getSelection().getLength() == 0) accept();
              }
              e.consume();
            }
          }
        });
    sceneProperty().addListener((o, old, scene) -> attachScene(old, scene));
    parentProperty()
        .addListener(
            (o, old, parent) -> {
              if (old != null && parent == null) close();
            });
    if (initialState != null && initialState.query() != null && !initialState.query().isBlank()) {
      updating = true;
      query.setText(initialState.query());
      updating = false;
    }
    if (initialObservable != null)
      card.getChildren().add(new ObservableCard(initialObservable, true));
    send(SemanticSearchRequest.Mode.TOKEN, null);
    Platform.runLater(query::requestFocus);
  }

  private TableColumn<SemanticMatch, String> column(
      String title, Function<SemanticMatch, String> text, int width) {
    var column = new TableColumn<SemanticMatch, String>(title);
    column.setCellValueFactory(value -> new ReadOnlyStringWrapper(text.apply(value.getValue())));
    column.setPrefWidth(width);
    return column;
  }

  private void accept() {
    if (authorityMode()) {
      if (idle() && authorities.selected() != null) send(SemanticSearchRequest.Mode.IDENTITY, null);
      return;
    }
    if (idle() && matchesCurrent && results.getSelectionModel().getSelectedItem() != null)
      send(SemanticSearchRequest.Mode.SELECT, results.getSelectionModel().getSelectedItem());
  }

  private void send(SemanticSearchRequest.Mode mode, SemanticMatch match) {
    if (closed || submitting) return;
    if (sessionLost) return;
    if (activeRequest != null && activeRequest.getSearchMode() == SemanticSearchRequest.Mode.TOKEN
        && mode == SemanticSearchRequest.Mode.UNDO) supersedeQuery();
    if (activeRequest != null) {
      if (mode == SemanticSearchRequest.Mode.UNDO) pendingUndo = Math.min(128, pendingUndo + 1);
      else if (mode == SemanticSearchRequest.Mode.TOKEN) queryQueued = true;
      if (pendingUndo > 0) status.setText("Waiting to undo…");
      updateControls();
      return;
    }
    if (mode == SemanticSearchRequest.Mode.TOKEN && queryNeedsMoreCharacters()) {
      pauseShortQuery();
      updateControls();
      return;
    }
    debounce.stop();
    queryQueued = false;
    int revision = queryRevision;
    var request = new SemanticSearchRequest();
    request.setRequestId(++requestId);
    request.setSearchId(searchId);
    request.setSearchMode(mode);
    request.setQueryString(query.getText());
    request.setMaxResults(30);
    if (mode == SemanticSearchRequest.Mode.IDENTITY) {
      request.setAuthority(authorities.authority());
      request.setIdentityCode(authorities.selected().getId());
      request.setMatchesRequestId(response.getRequestId());
      authorities.invalidate();
    }
    if (authorityMode() && mode == SemanticSearchRequest.Mode.TOKEN) request.setQueryString("");
    if (match != null) {
      request.setSelectedMatchId(match.getId());
      request.setMatchesRequestId(response.getRequestId());
    }
    activeRequest = request;
    matchesCurrent = false;
    requestStarted = System.nanoTime();
    LOG.fine(() -> "Semantic search start: " + requestSummary(request));
    busy = true;
    status.setText(
        switch (mode) {
          case TOKEN -> stateUncertain ? "Recovering expression…" : "Searching…";
          case SELECT ->
              "Adding " + (match.getName() == null ? match.getId() : match.getName()) + "…";
          case UNDO -> "Undoing…";
          case VALUE -> "Adding value…";
          case IDENTITY -> "Adding identity…";
          case OPEN_SCOPE, CLOSE_SCOPE -> "Updating group…";
        });
    updateControls();
    requestTimeout.playFromStart();
    var service = reasoner;
    activeTask =
        worker.submit(
            () -> {
              try {
                var selectedService = service == null ? reasonerSupplier.get() : service;
                if (selectedService == null)
                  throw new IllegalStateException("No Reasoner is available.");
                var reply = selectedService.semanticSearch(request);
                if (reply == null)
                  throw new IllegalStateException("The Reasoner returned no search response.");
                Platform.runLater(
                    () -> {
                      if (closed || activeRequest != request) {
                        if (reply.getSearchId() != 0 && reply.getSearchId() != searchId)
                          cancelSession(selectedService, reply.getSearchId());
                        return;
                      }
                      reasoner = selectedService;
                      try {
                        received(request, revision, reply);
                      } catch (Exception ex) {
                        failed(request, ex);
                      }
                    });
              } catch (Exception ex) {
                Platform.runLater(() -> failed(request, ex));
              }
            });
  }

  private void received(SemanticSearchRequest request, int revision, SemanticSearchResponse reply) {
    LOG.fine(
        () ->
            "Semantic search complete: "
                + requestSummary(request)
                + ", clientMs="
                + TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - requestStarted)
                + ", serverMs="
                + reply.getElapsedTimeMs()
                + ", matches="
                + reply.getMatches().size()
                + ", errors="
                + reply.getErrors().size());
    requestTimeout.stop();
    activeTask = null;
    busy = false;
    if (reply.getSearchId() == 0) {
      activeRequest = null;
      searchId = 0;
      sessionLost = true;
      stateUncertain = true;
      pendingUndo = 0;
      results.getItems().clear();
      status.setText(
          (reply.getErrors().isEmpty()
                  ? "The search session is unavailable."
                  : String.join("\n", reply.getErrors()))
              + " Backspace at the input boundary or the restart icon starts a new expression.");
      updateControls();
      return;
    }
    searchId = reply.getSearchId();
    if (request.getSearchMode() == SemanticSearchRequest.Mode.IDENTITY
        && !acknowledgesIdentity(request, reply)) {
      boolean unchanged = response != null && tokenValues(response).equals(tokenValues(reply));
      if (!unchanged || reply.getErrors().isEmpty()) {
        // An empty/success-looking reply does not prove that this edit was committed.
        // Preserve the visible prefix and query; recover the actual session before accepting it.
        activeRequest = null;
        stateUncertain = true;
        matchesCurrent = false;
        authorities.invalidate();
        status.setText((reply.getErrors().isEmpty() ? "The Reasoner did not confirm the selected authority reference."
            : String.join("\n", reply.getErrors())) + " Your input is preserved. Retry with the input icon to recover the expression.");
        LOG.warning("Unconfirmed authority insertion: " + requestSummary(request)
            + ", previousTokens=" + (response == null ? 0 : response.getCode().size())
            + ", returnedTokens=" + reply.getCode().size() + ", errors=" + reply.getErrors().size());
        updateControls();
        return;
      }
      // A normal rejection returns the unchanged expression and a diagnostic.
    }
    // Edits must always be applied, even if the user typed the next query while waiting.
    // Obsolete TOKEN replies still carry session state, but their proposals cannot be selected.
    boolean edited = request.getSearchMode() != SemanticSearchRequest.Mode.TOKEN;
    boolean changed = response == null || !tokenValues(response).equals(tokenValues(reply));
    boolean accepted = edited && (reply.getErrors().isEmpty() || changed);
    if (edited) authorities.invalidate();
    response = reply;
    stateUncertain = false;
    sessionLost = false;
    if (accepted) {
      initialObservable = null;
      if (revision == queryRevision && request.getSearchMode() != SemanticSearchRequest.Mode.UNDO) {
        updating = true;
        query.clear();
        // A confirmed identity prefixes the next concept; show the proposals already returned.
        if (request.getSearchMode() == SemanticSearchRequest.Mode.IDENTITY)
          source.getSelectionModel().selectFirst();
        updating = false;
      }
    }
    renderExpression(reply);
    card.getChildren().clear();
    if (reply.getCurrentConcept() != null)
      card.getChildren().add(new ObservableCard(reply.getCurrentConcept(), reply.getClauses()));
    else if (reply.getObservable() != null)
      card.getChildren().add(new ObservableCard(reply.getObservable(), true));
    else if (initialObservable != null)
      card.getChildren().add(new ObservableCard(initialObservable, true));
    boolean current = revision == queryRevision && !queryQueued;
    // Edit responses propose the next token for an empty query, regardless of request text.
    if (edited && !query.getText().isEmpty()) current = false;
    matchesCurrent = current;
    if (current) {
      results.getItems().setAll(reply.getMatches());
      results.getSelectionModel().selectFirst();
    } else results.getItems().clear();
    status.setText(String.join("\n", reply.getErrors()));
    if (accepted && revision == queryRevision) {
      query.requestFocus();
      query.positionCaret(0);
    }
    activeRequest = null;
    // Do not erase a rejected edit's diagnostic by immediately issuing an unrelated query.
    drain(!current && (accepted || reply.getErrors().isEmpty() || revision != queryRevision)
        || authorityMode() && !edited && reply.getErrors().isEmpty() && !query.getText().isBlank());
  }

  private static java.util.List<String> tokenValues(SemanticSearchResponse reply) {
    return reply.getCode().stream().map(StyledKimToken::getValue).toList();
  }

  private boolean acknowledgesIdentity(SemanticSearchRequest request, SemanticSearchResponse reply) {
    if (response == null) return false;
    var expected = new java.util.ArrayList<>(tokenValues(response));
    expected.add(AuthorityIdentitySyntax.encode(request.getAuthority(), request.getIdentityCode()));
    return expected.equals(tokenValues(reply));
  }

  private void failed(SemanticSearchRequest request, Exception failure) {
    if (closed || activeRequest != request) return;
    LOG.log(
        java.util.logging.Level.WARNING,
        "Semantic search failed: " + requestSummary(request),
        failure);
    requestTimeout.stop();
    activeRequest = null;
    activeTask = null;
    busy = false;
    stateUncertain = true;
    matchesCurrent = false;
    results.getItems().clear();
    // Retain the confirmed expression and session: even a failed HTTP call may have committed an
    // edit.
    status.setText(
        "Search failed: "
            + message(failure)
            + ". Retry with the input icon, or use Backspace to undo.");
    drain(queryQueued);
  }

  private void drain(boolean refresh) {
    if (pendingUndo > 0 && canUndo()) {
      pendingUndo--;
      send(SemanticSearchRequest.Mode.UNDO, null);
    } else {
      pendingUndo = 0;
      if (refresh || queryQueued) search();
      else updateControls();
    }
  }

  private void timedOut() {
    if (closed || activeRequest == null) return;
    LOG.warning(
        "Semantic search timeout: "
            + requestSummary(activeRequest)
            + ", elapsedMs="
            + TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - requestStarted)
            + ", workerDone="
            + (activeTask != null && activeTask.isDone()));
    boolean queryOnly = activeRequest.getSearchMode() == SemanticSearchRequest.Mode.TOKEN && searchId != 0;
    activeRequest = null;
    if (activeTask != null) activeTask.cancel(true);
    activeTask = null;
    worker.shutdownNow();
    worker = newWorker();
    if (!queryOnly) {
      cancelSession(reasoner, searchId);
      searchId = 0;
    }
    sessionLost = !queryOnly;
    stateUncertain = true;
    busy = false;
    matchesCurrent = false;
    pendingUndo = 0;
    results.getItems().clear();
    status.setText(queryOnly
        ? "The search did not respond. Your expression is preserved. Retry with the input icon or edit the search."
        : "The Reasoner did not respond. The displayed expression is preserved, but its session cannot be trusted."
            + " Backspace at the input boundary or the restart icon starts a new expression.");
    updateControls();
  }

  private void supersedeQuery() {
    LOG.fine(() -> "Semantic search superseded: " + requestSummary(activeRequest));
    requestTimeout.stop();
    activeRequest = null;
    if (activeTask != null) activeTask.cancel(true);
    activeTask = null;
    worker.shutdownNow();
    worker = newWorker();
    busy = false;
    // TOKEN never edits the expression. Preserve its session and let the newer request
    // supersede the old scan on the server; no edit is repeated or interrupted.
  }

  private void restart() {
    cancelSession(reasoner, searchId);
    searchId = 0;
    response = null;
    initialObservable = null;
    sessionLost = false;
    stateUncertain = false;
    matchesCurrent = false;
    pendingUndo = 0;
    queryQueued = false;
    confirmedTokens.getChildren().clear();
    tokenGap.setText("");
    card.getChildren().clear();
    send(SemanticSearchRequest.Mode.TOKEN, null);
  }

  private void updateControls() {
    boolean idle = idle();
    boolean recovery = !closed && !busy && !submitting && needsRecovery() && !canExport();
    proceed.setDisable(!recovery && !canExport());
    copy.setDisable(!canExport());
    copy.setVisible(!busy && !submitting);
    ((FontIcon) proceed.getGraphic()).setIconLiteral(recovery ? "mdi-refresh" : "mdi-arrow-right");
    proceed.setTooltip(
        new Tooltip(
            recovery
                ? sessionLost ? "Restart expression" : "Retry search"
                : "Continue (Ctrl+Enter)"));
    proceed.setAccessibleText(
        recovery ? sessionLost ? "Restart expression" : "Retry search" : "Continue");
    proceed.setVisible(!busy && !submitting);
    query.setDisable(submitting || closed);
    results.setDisable(!idle);
    source.setDisable(submitting || closed);
    authorities.chooser.setDisable(submitting || closed);
    authorities.results.setDisable(!idle || authorities.busy());
    progress.setVisible(busy || submitting || authorities.busy());
    if (authorities.busy()) proceed.setVisible(false);
    status.setVisible(!status.getText().isBlank());
    card.setVisible(!card.getChildren().isEmpty());
  }

  private boolean idle() {
    return !closed && !busy && !submitting && !stateUncertain && !sessionLost && response != null;
  }

  private boolean authorityMode() { return "Authorities".equals(source.getValue()); }

  private void sourceChanged() {
    if (closed || updating) return;
    queryRevision++;
    authorities.invalidate();
    results.getItems().clear(); matchesCurrent = false;
    if (activeRequest != null && activeRequest.getSearchMode() == SemanticSearchRequest.Mode.TOKEN
        && searchId != 0) supersedeQuery();
    queryQueued = true;
    if (activeRequest == null) { busy = true; status.setText("Searching…"); debounce.playFromStart(); }
    updateControls();
  }

  private void search() {
    if (closed || submitting || sessionLost) return;
    if (activeRequest != null) { queryQueued = true; return; }
    if (authorityMode() && response != null && !stateUncertain) {
      queryQueued = false; busy = false;
      authorities.search(query.getText());
      updateControls();
    } else send(SemanticSearchRequest.Mode.TOKEN, null);
  }

  private boolean queryNeedsMoreCharacters() {
    String text = query.getText().strip();
    return text.codePointCount(0, text.length()) == 1
        && Character.isLetterOrDigit(text.codePointAt(0))
        && (response == null || !response.isAcceptsValue());
  }

  private void pauseShortQuery() {
    debounce.stop();
    queryQueued = false;
    busy = false;
    matchesCurrent = false;
    results.getItems().clear();
    status.setText("Type at least two characters to search.");
  }

  private boolean needsRecovery() {
    return stateUncertain || sessionLost || response != null && !response.getErrors().isEmpty();
  }

  private boolean canUndo() {
    return !closed
        && !submitting
        && (response != null && response.isCanUndo()
            || stateUncertain && searchId != 0
            || activeRequest != null
                && activeRequest.getSearchMode() != SemanticSearchRequest.Mode.TOKEN);
  }

  private boolean canOpen() {
    return idle() && response.isCanOpenScope() && !endsWithOpeningParenthesis();
  }

  private void renderExpression(SemanticSearchResponse reply) {
    confirmedTokens.getChildren().setAll(Theme.semanticExpression(reply.getCode()).getChildren());
    tokenGap.setText(
        !reply.getCode().isEmpty() && reply.getCode().getLast().isNeedsWhitespaceAfter()
            ? " "
            : "");
    Platform.runLater(
        () -> {
          if (!closed) {
            inputLine.layout();
            inputScroll.setHvalue(1);
          }
        });
  }

  private boolean endsWithOpeningParenthesis() {
    return response != null
        && !response.getCode().isEmpty()
        && "(".equals(response.getCode().getLast().getValue());
  }

  private void finish() {
    if (!canExport()) return;
    Observable accepted = response.getObservable();
    if (accepted == null) {
      // Editor insertion also accepts predicates and incomplete confirmed declarations.
      // The host receives their exact text; runtime hosts retain their own validation.
      var declaration = new org.integratedmodelling.common.knowledge.ObservableImpl();
      declaration.setUrn(confirmedDeclaration());
      var concept = response.getCurrentConcept();
      if (concept == null) {
        // A text-only declaration must not require initializing the global k.LAB environment.
        var unresolved = new org.integratedmodelling.common.knowledge.ConceptImpl();
        unresolved.setUrn("owl:Nothing");
        unresolved.getType().add(org.integratedmodelling.klab.api.knowledge.SemanticType.NOTHING);
        concept = unresolved;
      }
      declaration.setSemantics(concept);
      accepted = declaration;
    }
    submitting = true;
    debounce.stop();
    status.setText("Continuing…");
    updateControls();
    try {
      action
          .apply(accepted)
          .whenComplete(
              (value, failure) ->
                  Platform.runLater(
                      () -> {
                        if (closed) return;
                        submitting = false;
                        if (failure == null) dismiss();
                        else {
                          status.setText(message(failure));
                          updateControls();
                        }
                      }));
    } catch (Exception ex) {
      submitting = false;
      status.setText(message(ex));
      updateControls();
    }
  }

  private boolean canExport() {
    return !closed && !busy && !submitting && !stateUncertain && !sessionLost
        && response != null && (!response.getCode().isEmpty() || response.getObservable() != null)
        && query.getText().isBlank();
  }

  private String confirmedDeclaration() {
    if (response.getDeclaration() != null && !response.getDeclaration().isBlank()) return response.getDeclaration();
    if (response.getCode().isEmpty() && response.getObservable() != null) return response.getObservable().getUrn();
    var text = new StringBuilder();
    StyledKimToken previous = null;
    for (var token : response.getCode()) {
      if (previous != null && previous.isNeedsWhitespaceAfter() && token.isNeedsWhitespaceBefore()) text.append(' ');
      text.append(token.getValue()); previous = token;
    }
    return text.toString();
  }

  private static ExecutorService newWorker() {
    return Executors.newSingleThreadExecutor(
        r -> {
          var thread = new Thread(r, "semantic-composer");
          thread.setDaemon(true);
          return thread;
        });
  }

  private void attachScene(javafx.scene.Scene old, javafx.scene.Scene scene) {
    if (old != null) {
      old.removeEventFilter(KeyEvent.KEY_RELEASED, releaseKeys);
      old.windowProperty().removeListener(windowChanged);
      if (old.getWindow() != null) old.getWindow().focusedProperty().removeListener(windowFocus);
    }
    if (scene != null && !closed) {
      scene.addEventFilter(KeyEvent.KEY_RELEASED, releaseKeys);
      scene.windowProperty().addListener(windowChanged);
      if (scene.getWindow() != null) scene.getWindow().focusedProperty().addListener(windowFocus);
    }
    enterKeyHeld = false;
    backspaceKeyHeld = false;
    parenthesisKeyHeld = false;
  }

  private static void cancelSession(Reasoner service, int id) {
    if (service == null || id == 0) return;
    Thread.ofVirtual()
        .name("semantic-composer-cleanup")
        .start(
            () -> {
              var request = new SemanticSearchRequest();
              request.setSearchId(id);
              request.setCancelSearch(true);
              try {
                service.semanticSearch(request);
              } catch (RuntimeException ignored) {
                /* Server expires idle sessions. */
              }
            });
  }

  private void dismiss() {
    close();
    dismiss.run();
  }

  @Override
  public void close() {
    if (closed) return;
    closed = true;
    debounce.stop();
    requestTimeout.stop();
    authorities.close();
    activeRequest = null;
    attachScene(getScene(), null);
    if (activeTask != null) activeTask.cancel(true);
    worker.shutdownNow();
    cancelSession(reasoner, searchId);
  }

  private static String message(Throwable failure) {
    while (failure.getCause() != null) failure = failure.getCause();
    return failure.getMessage() == null ? failure.getClass().getSimpleName() : failure.getMessage();
  }

  private static String requestSummary(SemanticSearchRequest request) {
    return "mode="
        + request.getSearchMode()
        + ", request="
        + request.getRequestId()
        + ", session="
        + request.getSearchId()
        + ", proposals="
        + request.getMatchesRequestId()
        + ", selected="
        + request.getSelectedMatchId()
        + ", queryLength="
        + (request.getQueryString() == null ? 0 : request.getQueryString().length());
  }
}
