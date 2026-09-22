package org.integratedmodelling.klab.ide.components;

import atlantafx.base.theme.Styles;
import com.brunomnsilva.smartgraph.containers.ContentZoomScrollPane;
import com.brunomnsilva.smartgraph.graph.DigraphEdgeList;
import com.brunomnsilva.smartgraph.graphview.*;
import java.util.*;
import java.util.concurrent.*;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.*;
import org.integratedmodelling.klab.api.knowledge.*;
import org.integratedmodelling.klab.api.knowledge.Observable;
import org.integratedmodelling.klab.api.lang.kim.*;
import org.integratedmodelling.klab.api.services.Reasoner;
import org.integratedmodelling.klab.ide.*;
import org.integratedmodelling.klab.ide.components.cards.ObservableCard;
import org.integratedmodelling.klab.ide.components.generic.TreeSearchField;
import org.integratedmodelling.klab.ide.pages.EditorPage;
import org.kordamp.ikonli.Ikon;
import org.kordamp.ikonli.javafx.FontIcon;
import org.kordamp.ikonli.material2.Material2AL;
import org.kordamp.ikonli.material2.Material2MZ;

/** Read-only worldview browser. Only the tree uses syntax; all graph edges come from the reasoner. */
public class WorldviewEditor extends EditorPage<Worldview, Object> {
  private Worldview worldview;
  private final WorldviewGraphModel model;
  private final Reasoner reasoner;
  private final TextField conceptInput = new TextField();
  private final ExecutorService worker = Executors.newSingleThreadExecutor(Thread.ofVirtual().factory());
  private final ExecutorService inputWorker = Executors.newVirtualThreadPerTaskExecutor();
  private final Map<String, TreeItem<Object>> items = new HashMap<>();
  private Map<KimConceptStatement, String> semanticUrns;
  private final Set<WorldviewGraphModel.Relation> relations = EnumSet.noneOf(WorldviewGraphModel.Relation.class);
  private final BorderPane graphArea = new BorderPane();
  private final Label status = new Label();
  private final javafx.beans.property.BooleanProperty graphBusy = new javafx.beans.property.SimpleBooleanProperty();
  private final javafx.beans.property.BooleanProperty inputBusy = new javafx.beans.property.SimpleBooleanProperty();
  private final ProgressIndicator progress = new ProgressIndicator();
  private CompletableFuture<?> inputRequest;
  private boolean composing;
  private final Spinner<Integer> depth = new Spinner<>(1, 10, 2);
  private final Deque<String> history = new ArrayDeque<>();
  private TreeView<Object> tree;
  private TreeSearchField<Object> treeSearch;
  private ObservableCard currentCard;
  private String cardUrn;
  private WorldviewGraphModel.Snapshot cardSnapshot;
  private SmartGraphPanel<Concept, WorldviewGraphModel.Link> graph;
  private WorldviewGraphModel.Snapshot snapshot;
  private Future<?> pending;
  private long generation;
  private boolean closed;
  private String focus;
  private boolean placeNeighborhood = true;
  private long selectionGeneration;
  private long inputGeneration;
  private String selectedUrn;
  private boolean sourceUnavailable;

  public WorldviewEditor(Worldview worldview, Reasoner reasoner) {
    super(worldview);
    this.worldview = worldview;
    semanticUrns = WorldviewConcepts.index(worldview);
    focus = WorldviewConcepts.initialFocus(worldview);
    this.reasoner = reasoner;
    model = new WorldviewGraphModel(reasoner);
    var layers = new MenuButton("Relations", new FontIcon(Material2AL.FILTER_LIST));
    layers.getStyleClass().addAll(Styles.FLAT, Styles.SMALL);
    for (var relation : WorldviewGraphModel.Relation.values()) {
      var item = new GraphLayerMenuItem(relation.label);
      item.setSelected(relation.selected);
      if (relation.selected) relations.add(relation);
      item.selectedProperty().addListener((p, old, selected) -> {
        if (selected) relations.add(relation); else relations.remove(relation);
        reload();
      });
      layers.getItems().add(item);
    }
    layers.getItems().add(new SeparatorMenuItem());
    for (var clause : List.of("Requires identity / realm / extent / attribute / authority",
        "Emerges from", "Implies observables", "Equals / disjoint children", "Deniable as")) {
      var item = new MenuItem(clause + " — unavailable from reasoner API");
      item.setDisable(true);
      layers.getItems().add(item);
    }
    depth.setPrefWidth(90);
    depth.setTooltip(new Tooltip("Neighborhood depth from the focused concept"));
    depth.getStyleClass().addAll(Styles.SMALL, Spinner.STYLE_CLASS_SPLIT_ARROWS_HORIZONTAL);
    depth.valueProperty().addListener((p, old, value) -> reload());
    var back = button(Material2MZ.NAVIGATE_BEFORE, "Previous concept", () -> {
      if (!history.isEmpty()) { focus = history.pop(); if (focus.isEmpty()) focus = null; reload(); }
    });
    var home = button(Material2AL.HOME, "Worldview subject", () -> center(WorldviewConcepts.initialFocus(this.worldview)));
    var refresh = button(Material2AL.AUTORENEW, "Reload graph", () -> {
      worker.submit(model::invalidate);
      reload();
    });
    var toolbar = new HBox(4, back, home, depth, refresh, layers);
    toolbar.getStyleClass().add("knowledge-graph-controls");
    graphArea.setTop(toolbar);
    status.setWrapText(true);
    conceptInput.setPromptText("Concept URN or observable — Enter to locate; Shift+Space to compose");
    conceptInput.setAccessibleText("Concept URN or observable");
    conceptInput.textProperty().addListener((p, old, value) -> {
      if (!composing) cancelInput();
    });
    conceptInput.setOnAction(event -> confirmInput());
    conceptInput.addEventFilter(javafx.scene.input.KeyEvent.KEY_PRESSED, event -> {
      if (event.getCode() == javafx.scene.input.KeyCode.SPACE && event.isShiftDown()) {
        event.consume();
        composeInput(event.isControlDown());
      }
    });
    conceptInput.addEventFilter(javafx.scene.input.KeyEvent.KEY_TYPED, event -> {
      if (event.isShiftDown() && " ".equals(event.getCharacter())) event.consume();
    });
    progress.setPrefSize(28, 28);
    progress.setMaxSize(28, 28);
    progress.visibleProperty().bind(graphBusy.or(inputBusy));
    progress.managedProperty().bind(progress.visibleProperty());
    progress.setAccessibleText("Loading worldview concepts");
    var cancel = new Button("Cancel");
    cancel.visibleProperty().bind(progress.visibleProperty());
    cancel.managedProperty().bind(cancel.visibleProperty());
    cancel.setOnAction(event -> {
      cancelInput();
      generation++;
      if (pending != null) pending.cancel(true);
      graphBusy.set(false);
      status.setText("Loading cancelled");
    });
    var activity = new HBox(8, progress, status, cancel);
    activity.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
    graphArea.setBottom(new VBox(4, activity, conceptInput));
    showAuxiliaryEditor("worldview-graph", "Concept graph", graphArea).setClosable(false);
    reload();
  }

  private void composeInput(boolean useWholeExpression) {
    cancelInput();
    composing = true;
    inputBusy.set(true);
    long ticket = ++inputGeneration;
    String text = conceptInput.getSelectedText().isBlank() ? conceptInput.getText() : conceptInput.getSelectedText();
    var context = new org.integratedmodelling.klabeditor.MonacoEditorView.ObservableCompositionContext(
        useWholeExpression ? text : null, text);
    ObservableComposerDialog.show(conceptInput, () -> reasoner, context).whenComplete((observable, error) ->
        Platform.runLater(() -> {
          if (closed || ticket != inputGeneration) return;
          composing = false;
          inputBusy.set(false);
          if (error != null) { status.setText("Could not compose concept: " + error.getMessage()); return; }
          if (observable != null) applyInput(observable, false);
          conceptInput.requestFocus();
        }));
  }

  private void confirmInput() {
    String text = conceptInput.getText().strip();
    if (text.isEmpty()) return;
    cancelInput();
    long ticket = ++inputGeneration;
    inputBusy.set(true);
    status.setText("Resolving concept...");
    inputRequest = CompletableFuture.supplyAsync(() -> reasoner.resolveObservable(text), inputWorker)
        .orTimeout(45, TimeUnit.SECONDS)
        .whenComplete((observable, error) -> Platform.runLater(() -> {
          if (closed || ticket != inputGeneration) return;
          inputBusy.set(false);
          if (error != null) {
            status.setText("Could not resolve concept: " + (error instanceof TimeoutException
                ? "The reasoner timed out; edit or retry the expression" : error.getMessage()));
            return;
          }
          applyInput(observable, true);
        }));
  }

  private void cancelInput() {
    composing = false;
    inputGeneration++;
    if (inputRequest != null) inputRequest.cancel(true);
    inputBusy.set(false);
  }

  private void applyInput(Observable observable, boolean submitted) {
    try {
      if (observable == null || observable.getSemantics() == null || observable.is(SemanticType.NOTHING))
        throw new IllegalArgumentException("The reasoner could not validate this concept");
      // Some reasoners return the expression only on the semantic concept.
      String urn = observable.getUrn();
      acceptConcept(observable.getSemantics());
      if (submitted) conceptInput.clear();
      else conceptInput.setText(urn == null || urn.isBlank() ? observable.getSemantics().getUrn() : urn);
    } catch (Exception error) {
      status.setText("Could not display concept: " + error.getMessage());
    }
  }

  private void acceptConcept(Concept concept) {
    if (concept == null || concept.is(SemanticType.NOTHING)) return;
    worker.submit(() -> model.accept(concept));
    select(concept);
    center(concept.getUrn());
  }

  void refreshWorldview(Worldview updated) {
    worldview = updated;
    semanticUrns = WorldviewConcepts.index(updated);
    if (focus == null) focus = WorldviewConcepts.initialFocus(updated);
    worker.submit(model::refreshKnowledge);
    if (tree != null) {
      if (treeSearch != null) treeSearch.clearSearch();
      boolean declaredSelection = items.containsKey(selectedUrn)
          && items.get(selectedUrn).getValue() instanceof KimConceptStatement;
      boolean declaredFocus = items.containsKey(focus)
          && items.get(focus).getValue() instanceof KimConceptStatement;
      var expanded = new HashSet<String>();
      items.forEach((urn, item) -> { if (item.isExpanded()) expanded.add(urn); });
      items.clear();
      var root = new TreeItem<Object>(worldview);
      for (var ontology : worldview.getOntologies()) {
        var item = new TreeItem<Object>(ontology);
        root.getChildren().add(item);
        for (var statement : ontology.getStatements()) addStatement(item, statement);
      }
      root.setExpanded(true);
      expanded.forEach(urn -> { if (items.containsKey(urn)) items.get(urn).setExpanded(true); });
      tree.setRoot(root);
      if (declaredSelection && !items.containsKey(selectedUrn)) selectedUrn = null;
      if (declaredFocus && !items.containsKey(focus)) {
        focus = WorldviewConcepts.initialFocus(updated);
        history.clear();
      }
      if (snapshot != null && selectedUrn != null && snapshot.concepts().containsKey(selectedUrn))
        select(snapshot.concepts().get(selectedUrn));
    }
    reload();
  }

  void sourceUnavailable(String message) {
    sourceUnavailable = true;
    status.setText("Worldview refresh unavailable; displaying the last snapshot: " + message);
  }

  void sourceAvailable() {
    if (sourceUnavailable) {
      sourceUnavailable = false;
      status.setText(snapshot == null ? "Loading concepts from the reasoner…"
          : snapshot.concepts().size() + " concepts · " + snapshot.links().size() + " relationships");
    }
  }

  @Override public Worldview getEditedAsset() { return worldview; }

  private static Button button(Ikon icon, String tooltip, Runnable action) {
    var button = new Button("", new FontIcon(icon));
    button.getStyleClass().addAll(Styles.BUTTON_CIRCLE, Styles.FLAT, Styles.SMALL);
    button.setTooltip(new Tooltip(tooltip));
    button.setAccessibleText(tooltip);
    button.setOnAction(event -> action.run());
    return button;
  }

  @Override protected TreeView<Object> createContentTree() {
    var root = new TreeItem<Object>(worldview);
    for (var ontology : worldview.getOntologies()) {
      var item = new TreeItem<Object>(ontology);
      root.getChildren().add(item);
      for (var statement : ontology.getStatements()) addStatement(item, statement);
    }
    root.setExpanded(true);
    tree = new TreeView<>(root);
    tree.setShowRoot(false);
    tree.getStyleClass().add(Styles.DENSE);
    tree.setPrefWidth(340);
    tree.setCellFactory(view -> new TreeCell<>() {
      @Override protected void updateItem(Object value, boolean empty) {
        super.updateItem(value, empty);
        setText(null); setGraphic(null);
        if (empty || value == null) return;
        setText(label(value));
        if (value instanceof KimConceptStatement statement) {
          var type = SemanticType.fundamentalType(statement.getType());
          if (type != null) setGraphic(new org.integratedmodelling.klab.ide.components.generic.IconLabel(
              Theme.WORLDVIEW_ICON, 14, Theme.getColorForType(type)));
        }
      }
    });
    tree.getSelectionModel().selectedItemProperty().addListener((p, old, selected) -> {
      if (selected != null) {
        var value = selected.getValue();
        if (value instanceof Concept || value instanceof KimConceptStatement) {
          selectedUrn = semanticUrn(value);
          if (graph != null && snapshot != null) styleGraph(graph, snapshot);
        }
        inspect(value);
      }
    });
    return tree;
  }

  private void addStatement(TreeItem<Object> parent, KimConceptStatement statement) {
    var item = new TreeItem<Object>(statement);
    parent.getChildren().add(item);
    items.put(semanticUrn(statement), item);
    for (var child : statement.getChildren()) addStatement(item, child);
  }

  private static String label(Object value) {
    if (value instanceof Worldview w) return w.getWorldviewId();
    if (value instanceof KimOntology o) return o.getUrn();
    if (value instanceof KimConceptStatement s) return s.getUrn();
    if (value instanceof Concept c) return c.getUrn();
    return String.valueOf(value);
  }

  private String semanticUrn(Object value) {
    return value instanceof KimConceptStatement statement ? semanticUrns.get(statement) : label(value);
  }

  @Override protected Node createTopMenu() {
    var search = treeSearch = new TreeSearchField<>(tree, (query, value) -> label(value).toLowerCase(Locale.ROOT).contains(query.toLowerCase(Locale.ROOT)));
    HBox.setHgrow(search, Priority.ALWAYS);
    var settings = button(Theme.WORKSPACE_SETTINGS_ICON, "Worldview information and viewer settings", () -> {
      var text = new Label("Worldview: " + worldview.getWorldviewId() + "\nOntologies: "
          + worldview.getOntologies().size() + "\nConcept declarations: " + worldview.allConceptStatements().size()
          + "\n\nThis worldview is read-only. Edit its sources in the Workspace Editor."
          + "\nUse Relations and depth in the graph toolbar to configure this viewer.");
      text.setWrapText(true);
      var dialog = new Alert(Alert.AlertType.INFORMATION);
      dialog.setTitle("Worldview settings"); dialog.setHeaderText(worldview.getWorldviewId());
      dialog.getDialogPane().setContent(text);
      if (getScene() != null) dialog.initOwner(getScene().getWindow());
      dialog.show();
    });
    return new HBox(2, settings, search,
        button(Material2MZ.UNFOLD_MORE, "Expand tree", () -> expand(tree.getRoot(), true)),
        button(Material2MZ.UNFOLD_LESS, "Collapse tree", () -> {
          expand(tree.getRoot(), false); tree.getRoot().setExpanded(true);
        }));
  }

  private void expand(TreeItem<?> item, boolean expanded) {
    item.setExpanded(expanded);
    item.getChildren().forEach(child -> expand(child, expanded));
  }

  private void center(String urn) {
    if (!Objects.equals(focus, urn)) history.push(focus == null ? "" : focus);
    focus = urn;
    placeNeighborhood = true;
    reload();
  }

  private void reload() {
    if (closed) return;
    long ticket = ++generation;
    if (pending != null) pending.cancel(true);
    var selected = EnumSet.copyOf(relations);
    var roots = focus == null ? List.<String>of() : List.of(focus);
    int distance = depth.getValue();
    graphBusy.set(true);
    status.setText("Loading concepts from the reasoner…");
    pending = worker.submit(() -> {
      try {
        var result = model.load(roots, distance, selected, 150, partial -> Platform.runLater(() -> {
          if (closed || ticket != generation) return;
          status.setText("Loading neighborhood... " + partial.concepts().size() + " concepts");
        }));
        Platform.runLater(() -> {
          if (closed || ticket != generation) return;
          graphBusy.set(false);
          placeNeighborhood = placeNeighborhood || snapshot == null
              || !snapshot.concepts().keySet().equals(result.concepts().keySet());
          snapshot = result;
          drawGraph(result);
          var controller = KlabIDEController.instance();
          if (currentCard != null && controller.isInspectorShown()
              && controller.getInspector().getCurrentObject() == currentCard
              && result.concepts().containsKey(selectedUrn))
            showCard(result.concepts().get(selectedUrn));
          status.setText(result.concepts().size() + " concepts · " + result.links().size() + " relationships"
              + (focus == null ? " · Entire worldview" : " · " + focus)
              + (result.unresolved().isEmpty() ? "" : " · Could not resolve " + result.unresolved().size()
                  + " concepts (" + result.unresolved().getFirst() + ")"));
        });
      } catch (CancellationException ignored) {
      } catch (Exception error) {
        Platform.runLater(() -> {
          if (!closed && ticket == generation) {
            graphBusy.set(false);
            status.setText("Could not load graph: " + error.getMessage());
          }
        });
      }
    });
  }

  private void drawGraph(WorldviewGraphModel.Snapshot result) {
    if (graph != null) {
      var data = graph.getModel();
      for (var edge : new ArrayList<>(data.edges()))
        if (!result.links().contains(edge.element())) data.removeEdge(edge);
      for (var vertex : new ArrayList<>(data.vertices()))
        if (!result.concepts().containsKey(vertex.element().getUrn())) data.removeVertex(vertex);
      var existing = new HashMap<String, Concept>();
      data.vertices().forEach(vertex -> existing.put(vertex.element().getUrn(), vertex.element()));
      result.concepts().forEach((urn, concept) -> {
        if (!existing.containsKey(urn)) { data.insertVertex(concept); existing.put(urn, concept); }
      });
      var edges = new HashSet<WorldviewGraphModel.Link>();
      data.edges().forEach(edge -> edges.add(edge.element()));
      result.links().forEach(link -> {
        if (!edges.contains(link)) data.insertEdge(existing.get(link.source()), existing.get(link.target()), link);
      });
      if (Boolean.TRUE.equals(graph.getProperties().get("worldview-initialized"))) {
        graph.update();
        Platform.runLater(() -> {
          if (!closed && snapshot == result) { placeNeighborhood(); styleGraph(graph, result); }
        });
      }
      return;
    }
    var data = new DigraphEdgeList<Concept, WorldviewGraphModel.Link>();
    result.concepts().values().forEach(data::insertVertex);
    result.links().forEach(link -> data.insertEdge(result.concepts().get(link.source()), result.concepts().get(link.target()), link));
    SmartGraphProperties properties;
    try (var stream = java.nio.file.Files.newInputStream(GraphResources.applicationFile("smartgraph.properties"))) {
      properties = new SmartGraphProperties(stream);
    } catch (java.io.IOException error) {
      properties = new SmartGraphProperties();
    }
    var panel = new SmartGraphPanel<>(data, properties,
        new SmartCircularSortedPlacementStrategy(), GraphResources.applicationFile("smartgraph.css").toUri());
    graph = panel;
    panel.setDarkModeStylesheet(GraphResources.applicationFile("smartgraph-dark.css").toUri());
    panel.setDarkMode(Theme.CURRENT_THEME.isDark());
    panel.setVertexLabelProvider(Concept::getUrn);
    panel.setEdgeLabelProvider(WorldviewGraphModel.Link::label);
    var scroll = new ContentZoomScrollPane(panel);
    scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
    scroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
    graphArea.setCenter(scroll);
    // Initialize on a real layout pass, including when this tab was initially hidden.
    panel.layoutBoundsProperty().addListener((p, old, bounds) -> {
      if (old.getWidth() == bounds.getWidth() && old.getHeight() == bounds.getHeight()) return;
      // The tab may first receive a small provisional size. A later real viewport size
      // must reposition the neighborhood even when SmartGraph has already initialized.
      placeNeighborhood = true;
      Platform.runLater(() -> {
        initialize(panel, result);
        if (!closed && graph == panel && Boolean.TRUE.equals(panel.getProperties().get("worldview-initialized")))
          placeNeighborhood();
      });
    });
    panel.sceneProperty().addListener((p, old, scene) -> Platform.runLater(() -> initialize(panel, result)));
    Platform.runLater(() -> initialize(panel, result));
  }

  private void initialize(SmartGraphPanel<Concept, WorldviewGraphModel.Link> panel, WorldviewGraphModel.Snapshot result) {
    if (closed || graph != panel || panel.getScene() == null || panel.getWidth() <= 0 || panel.getHeight() <= 0
        || Boolean.TRUE.equals(panel.getProperties().get("worldview-initialized"))) return;
    panel.getProperties().put("worldview-initialized", true);
    panel.init();
    panel.setAutomaticLayout(false);
    if (snapshot != null) styleGraph(panel, snapshot);
    // CSS determines label bounds; defer placement until the initialization pass completes.
    Platform.runLater(() -> {
      if (!closed && graph == panel) { panel.applyCss(); placeNeighborhood(); }
    });
  }

  private void styleGraph(SmartGraphPanel<Concept, WorldviewGraphModel.Link> panel, WorldviewGraphModel.Snapshot result) {
    result.concepts().values().forEach(concept -> {
      var vertex = panel.getStylableVertex(concept);
      if (vertex == null) return;
      var type = SemanticType.fundamentalType(concept.getType());
      if (type != null) {
        var color = Theme.getColorForType(type);
        String fill = String.format(Locale.ROOT, "#%02x%02x%02x", Math.round(color.getRed()*255), Math.round(color.getGreen()*255), Math.round(color.getBlue()*255));
        vertex.setStyleInline("-fx-fill: " + fill + "; -fx-stroke: -color-fg-default; -fx-stroke-width: "
            + (Objects.equals(selectedUrn, concept.getUrn()) ? "4;" : "1;")
            + (concept.isAbstract() ? " -fx-stroke-dash-array: 4 3;" : ""));
      }
      installConceptClick(vertex, concept);
      panel.getModel().vertices().stream()
          .filter(v -> v.element().getUrn().equals(concept.getUrn())).findFirst()
          .ifPresent(v -> installConceptClick(panel.getStylableLabel(v), concept));
    });
  }

  private void installConceptClick(Object visual, Concept concept) {
    if (visual instanceof Node node && node.getProperties().putIfAbsent("worldview-click", true) == null) {
      node.setMouseTransparent(false);
      node.addEventHandler(MouseEvent.MOUSE_CLICKED, event -> {
        if (event.getButton() != javafx.scene.input.MouseButton.PRIMARY) return;
        var current = currentConcept(concept);
        select(current);
        if (event.getClickCount() == 2) center(current.getUrn());
        event.consume();
      });
    }
  }

  private void placeNeighborhood() {
    if (graphBusy.get() || !placeNeighborhood || graph.getModel().numVertices() == 0) return;
    // Place a completed neighborhood once; continuous force layout can push long labels
    // outside the viewport, and incremental vertex spawning creates a left-biased grid.
    double labelWidth = graph.getModel().vertices().stream()
        .map(graph::getStylableLabel).filter(Node.class::isInstance).map(Node.class::cast)
        .mapToDouble(label -> label.getLayoutBounds().getWidth()).max().orElse(240);
    double marginX = Math.min(Math.max(0, graph.getWidth() / 2 - 24), labelWidth / 2 + 24);
    double marginY = Math.min(60, graph.getHeight() / 4);
    new SmartCircularSortedPlacementStrategy().place(
        graph.getWidth() - 2 * marginX, graph.getHeight() - 2 * marginY, graph);
    for (var vertex : graph.getModel().vertices()) {
      if (Objects.equals(vertex.element().getUrn(), focus))
        graph.setVertexPosition(vertex, graph.getWidth() / 2, graph.getHeight() / 2);
      else graph.setVertexPosition(vertex, graph.getVertexPositionX(vertex) + marginX,
          graph.getVertexPositionY(vertex) + marginY);
    }
    if (graphArea.getCenter() instanceof ContentZoomScrollPane scroll) {
      scroll.setHvalue(0.5); scroll.setVvalue(0.5);
    }
    placeNeighborhood = false;
  }

  private Concept currentConcept(Concept concept) {
    return snapshot == null ? concept : snapshot.concepts().getOrDefault(concept.getUrn(), concept);
  }

  private void select(Concept concept) {
    selectedUrn = concept.getUrn();
    if (graph != null && snapshot != null) styleGraph(graph, snapshot);
    if (tree == null) return;
    if (treeSearch != null) treeSearch.clearSearch();
    var item = items.get(concept.getUrn());
    if (item == null) {
      item = new TreeItem<>(concept);
      tree.getRoot().getChildren().add(item);
      items.put(concept.getUrn(), item);
    }
    for (var parent = item.getParent(); parent != null; parent = parent.getParent()) parent.setExpanded(true);
    tree.getSelectionModel().select(item);
    tree.scrollTo(tree.getRow(item));
    inspect(concept);
  }

  private void inspect(Object value) {
    var controller = KlabIDEController.instance();
    long ticket = ++selectionGeneration;
    if (!controller.isInspectorShown() || !(value instanceof Concept || value instanceof KimConceptStatement)) return;
    String urn = semanticUrn(value);
    Concept known = value instanceof Concept concept ? concept : snapshot == null ? null : snapshot.concepts().get(urn);
    if (known != null) { showCard(known); return; }
    inputWorker.submit(() -> {
      try {
        var concept = reasoner.resolveConcept(urn);
        if (concept == null || concept.is(SemanticType.NOTHING))
          throw new IllegalArgumentException("Reasoner cannot resolve " + urn);
        Platform.runLater(() -> {
          if (!closed && concept != null && ticket == selectionGeneration && controller.isInspectorShown())
            showCard(concept);
        });
      } catch (Exception error) {
        Platform.runLater(() -> {
          if (!closed && ticket == selectionGeneration) status.setText("Could not inspect " + urn + ": " + error.getMessage());
        });
      }
    });
  }

  private void showCard(Concept concept) {
    if (currentCard != null && Objects.equals(cardUrn, concept.getUrn()) && cardSnapshot == snapshot) {
      KlabIDEController.instance().getInspector().inspect(concept.getUrn(), currentCard);
      return;
    }
    var card = new ObservableCard(concept, List.of());
    currentCard = card;
    cardUrn = concept.getUrn();
    cardSnapshot = snapshot;
    if (snapshot != null) {
      var details = new LinkedHashMap<String, List<Concept>>();
      for (var link : snapshot.links()) {
        if (link.source().equals(concept.getUrn()))
          details.computeIfAbsent(link.label(), key -> new ArrayList<>()).add(snapshot.concepts().get(link.target()));
      }
      card.addRelationships(details);
    }
    KlabIDEController.instance().getInspector().inspect(concept.getUrn(), card);
  }

  @Override protected void onSingleClickItemSelection(Object value) { inspect(value); }
  @Override protected void onDoubleClickItemSelection(Object value) {
    if (value instanceof Concept || value instanceof KimConceptStatement) { inspect(value); center(semanticUrn(value)); }
    else if (value instanceof Worldview) center(null);
  }
  @Override protected Node createEditor(Object asset) { return null; }
  @Override protected void onVisualize(boolean visible) { KlabIDEController.instance().setFocalEditor(this, visible); }
  @Override public boolean isAffectedBy(IDEContextScope scope) { return false; }
  @Override public void closeDigitalTwin(IDEContextScope scope) {}
  @Override public void close() {
    closed = true; generation++; selectionGeneration++;
    cancelInput();
    if (pending != null) pending.cancel(true);
    worker.shutdownNow();
    inputWorker.shutdownNow();
    if (graph != null) graph.setAutomaticLayout(false);
    super.close();
  }
}
