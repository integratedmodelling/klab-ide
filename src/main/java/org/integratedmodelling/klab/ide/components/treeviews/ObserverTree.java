package org.integratedmodelling.klab.ide.components.treeviews;

import atlantafx.base.theme.Styles;
import atlantafx.base.theme.Tweaks;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import javafx.application.Platform;
import javafx.beans.property.SimpleObjectProperty;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.input.MouseButton;
import org.kordamp.ikonli.evaicons.Evaicons;
import org.integratedmodelling.klab.api.data.RuntimeAsset;
import org.integratedmodelling.klab.api.digitaltwin.GraphModel;
import org.integratedmodelling.klab.api.knowledge.Cohort;
import org.integratedmodelling.klab.api.knowledge.SemanticType;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.ide.IDEContextScope;
import org.integratedmodelling.klab.ide.Theme;
import org.integratedmodelling.klab.ide.components.generic.IconLabel;

/** The twin's agent catalog, independent of the observation tree's focus and depth. */
public class ObserverTree extends KlabTreeTableView<RuntimeAsset> {
  private IDEContextScope scope;
  private long generation;
  private java.util.function.Consumer<Observation> observerEditor;
  public void setObserverEditor(java.util.function.Consumer<Observation> editor) { observerEditor = editor; }

  public ObserverTree() {
    setColumnResizePolicy(TreeTableView.UNCONSTRAINED_RESIZE_POLICY);
    getStyleClass().addAll(Styles.DENSE, Tweaks.EDGE_TO_EDGE, Tweaks.NO_HEADER);
    setShowRoot(false);
    setRowFactory(table -> {
      var row = new TreeTableRow<RuntimeAsset>();
      row.setOnContextMenuRequested(event -> {
        if (observerEditor != null && row.getItem() instanceof Observation observer
            && observer.getObservable().is(SemanticType.AGENT)) {
          var item = new MenuItem("Audit / edit observer geometry");
          item.setOnAction(action -> observerEditor.accept(observer));
          new ContextMenu(item).show(row, event.getScreenX(), event.getScreenY());
          event.consume();
        }
      });
      return row;
    });
    setPlaceholder(new Label("No agents available"));
    TreeTableColumn<RuntimeAsset, HBox> description = new TreeTableColumn<>("Observer");
    description.setCellValueFactory(p -> new SimpleObjectProperty<>(describe(p.getValue().getValue())));
    description.prefWidthProperty().bind(widthProperty().subtract(10));
    getColumns().setAll(description);
    setRoot(new TreeItem<>());
  }

  public boolean matches(String text, RuntimeAsset asset) {
    return Theme.getLabel(asset).toLowerCase(Locale.ROOT).contains(text.toLowerCase(Locale.ROOT));
  }

  private HBox describe(RuntimeAsset asset) {
    if (asset == null) return new HBox();
    var selected = scope == null ? null : scope.getObserver();
    boolean agent = asset instanceof Observation observation
        && observation.getObservable().is(SemanticType.AGENT);
    boolean current = agent && selected != null && selected.getId() == asset.getId();
    var icon = agent
        ? new IconLabel(current ? Evaicons.PERSON : Evaicons.PERSON_OUTLINE, 16,
            current ? "-color-accent-fg" : "-color-fg-default")
        : Theme.getGraphics(asset);
    icon.setMinWidth(24); icon.setPrefWidth(24); icon.setMaxWidth(24);
    if (agent) {
      var observation = (Observation) asset;
      String hint = current ? "Current observer" : "Use as current observer";
      Tooltip.install(icon, new Tooltip(hint));
      icon.setAccessibleText(hint + ": " + Theme.getLabel(asset));
      icon.setOnMouseClicked(event -> {
        if (event.getButton() != MouseButton.PRIMARY) return;
        if (scope != null) {
          var previous = scope.getObserver();
          var chosen = ObserverSelection.choose(previous, observation);
          if (chosen != previous) scope.withObserver(chosen);
          refresh();
        }
        event.consume();
      });
    }
    var label = new Label(Theme.getLabel(asset));
    label.setTextOverrun(OverrunStyle.ELLIPSIS);
    label.setMinWidth(0); label.setMaxWidth(Double.MAX_VALUE); HBox.setHgrow(label, Priority.ALWAYS);
    Tooltip.install(label, new Tooltip(Theme.getLabel(asset)));
    return new HBox(4, icon, label);
  }

  private record AgentCohort(Cohort cohort, List<Observation> agents) {}

  public void update(IDEContextScope nextScope) {
    scope = nextScope;
    long request = ++generation;
    if (nextScope == null) { setRoot(new TreeItem<>()); return; }
    Set<Long> expanded = new HashSet<>();
    getRoot().getChildren().stream().filter(TreeItem::isExpanded)
        .forEach(item -> expanded.add(item.getValue().getId()));
    CompletableFuture.supplyAsync(() -> {
      var result = new ArrayList<AgentCohort>();
      var graph = nextScope.getDigitalTwin().getKnowledgeGraph();
      for (var link : graph.getLinks(RuntimeAsset.CONTEXT_ASSET,
          GraphModel.Relationship.Direction.OUTGOING, nextScope, GraphModel.Relationship.HAS_CHILD)) {
        if (link.target() instanceof Cohort cohort) {
          var agents = graph.getLinks(cohort, GraphModel.Relationship.Direction.OUTGOING,
              nextScope, GraphModel.Relationship.HAS_MEMBER).stream()
              .map(l -> l.target()).filter(Observation.class::isInstance).map(Observation.class::cast)
              .filter(o -> o.getObservable().is(SemanticType.AGENT))
              .sorted(Comparator.comparing(Theme::getLabel)).toList();
          if (!agents.isEmpty()) result.add(new AgentCohort(cohort, agents));
        }
      }
      result.sort(Comparator.comparing(c -> Theme.getLabel(c.cohort())));
      return result;
    }).whenComplete((cohorts, error) -> Platform.runLater(() -> {
      if (scope != nextScope || generation != request) return;
      if (error != null) { nextScope.warn("Cannot refresh observers: " + error.getMessage()); return; }
      var root = new TreeItem<RuntimeAsset>();
      var previous = nextScope.getObserver();
      var selected = ObserverSelection.currentOrSole(previous,
          cohorts.stream().flatMap(cohort -> cohort.agents().stream()).toList());
      if (selected != previous) nextScope.withObserver(selected);
      TreeItem<RuntimeAsset> focal = null;
      for (var cohort : cohorts) {
        var group = new TreeItem<RuntimeAsset>(cohort.cohort());
        group.setExpanded(expanded.contains(cohort.cohort().getId()));
        for (var agent : cohort.agents()) {
          var item = new TreeItem<RuntimeAsset>(agent);
          group.getChildren().add(item);
          if (selected != null && selected.getId() == agent.getId()) {
            focal = item;
            group.setExpanded(true);
          }
        }
        root.getChildren().add(group);
      }
      // Keep the selection visible even while catalog links are still arriving.
      if (selected != null && focal == null) {
        focal = new TreeItem<>(selected);
        root.getChildren().add(focal);
      }
      root.setExpanded(true);
      setRoot(root);
      if (focal != null) {
        getSelectionModel().select(focal);
        scrollTo(getRow(focal));
      }
    }));
  }

  public void reset() { generation++; scope = null; setRoot(new TreeItem<>()); }
}