package org.integratedmodelling.klab.ide.components.cards;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import org.integratedmodelling.common.services.client.digitaltwin.ClientKnowledgeGraph;
import org.integratedmodelling.klab.api.data.Histogram;
import org.integratedmodelling.klab.api.data.KnowledgeGraph;
import org.integratedmodelling.klab.api.data.RuntimeAsset;
import org.integratedmodelling.klab.api.digitaltwin.GraphModel;
import org.integratedmodelling.klab.api.knowledge.SemanticType;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.ide.IDEContextScope;
import org.integratedmodelling.klab.ide.Theme;

public class ObservationCard extends BaseCard<Observation> {

  private final ValueCard.Options valueOptions;

  public ObservationCard(Observation asset, IDEContextScope scope, boolean extended) {
    this(asset, scope, extended, null);
  }

  public ObservationCard(
      Observation asset,
      IDEContextScope scope,
      boolean extended,
      ValueCard.Options valueOptions) {
    super(asset, scope, extended, false);
    this.valueOptions = valueOptions;
    drawContent();
  }

  @Override
  protected void drawContent() {
    getStyleClass().add("observation-card");
    setCenter(createBody());
  }

  private Node createFooter() {
    var ret = new HBox();
    ret.setSpacing(10);
    ret.setPadding(new Insets(10));
    return ret;
  }

  private Node createBody() {
    var ret = new HBox();
    ret.setSpacing(10);
    ret.setPadding(new Insets(10));

    var leftBox = new VBox();
    var geom = new GeometryCard(asset.getGeometry(), true);
    geom.setMinWidth(220);
    geom.setPrefWidth(240);
    geom.setMaxWidth(280);
    leftBox.getChildren().add(geom);
    var relationshipCard =
        new RelationshipCard(
            asset,
            scope,
            GraphModel.Relationship.Direction.INCOMING,
            GraphModel.Relationship.Direction.OUTGOING);
    relationshipCard.setMinWidth(220);
    relationshipCard.setPrefWidth(240);
    relationshipCard.setMaxWidth(280);
    VBox.setVgrow(relationshipCard, Priority.ALWAYS);
    leftBox.getChildren().add(relationshipCard);

    StackPane histogramSlot = hasHistogram() ? new StackPane() : null;
    Node value = createObservationContent(geom, histogramSlot);
    if (value instanceof Region region) {
      region.setMinSize(180, 180);
      region.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
    }
    VBox.setVgrow(value, Priority.ALWAYS);

    var valueColumn = new VBox(8, createTitleBar(), value);
    valueColumn.setMinWidth(180);
    valueColumn.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
    HBox.setHgrow(valueColumn, Priority.ALWAYS);

    var metadata =
        new MetadataCard(
            asset.getMetadata(),
            new MetadataCard.Options().title("Metadata").emptyTitle("Empty metadata"));
    metadata.setMinWidth(180);
    metadata.setPrefWidth(240);
    metadata.setMaxWidth(320);

    var rightBox = new VBox(10);
    rightBox.setMinWidth(180);
    rightBox.setPrefWidth(240);
    rightBox.setMaxWidth(320);
    if (histogramSlot != null) {
      histogramSlot.setMinHeight(130);
      histogramSlot.setPrefHeight(170);
      histogramSlot.setMaxHeight(220);
      rightBox.getChildren().add(histogramSlot);
    }
    VBox.setVgrow(metadata, Priority.ALWAYS);
    rightBox.getChildren().add(metadata);

    ret.getChildren().add(leftBox);
    ret.getChildren().add(valueColumn);
    ret.getChildren().add(rightBox);
    return ret;
  }

  private Node createTitleBar() {
    Node icon = Theme.getGraphics(asset);
    if (icon instanceof Region region) {
      region.setMinWidth(24);
      region.setMaxWidth(24);
    }

    String observationLabel = observationLabel(asset);
    Observation inherentContext = inherentContext();
    if (inherentContext != null) {
      observationLabel += " of " + observationLabel(inherentContext);
    }
    var label = new Label(observationLabel);
    label.getStyleClass().add("observation-card-title");
    label.setMinWidth(0);
    label.setMaxWidth(240);
    label.setTooltip(new Tooltip(observationLabel));

    Object semanticDisplay =
        asset.getObservable() == null
            ? null
            : Theme.getDisplayObject(
                asset.getObservable().getSemantics(), Theme.Detail.ONE_LINER);
    Node semantics;
    if (semanticDisplay instanceof Node node) {
      semantics = node;
    } else {
      var fallback = new Label(semanticDisplay == null ? "" : semanticDisplay.toString());
      semantics = fallback;
    }
    semantics.getStyleClass().add("observation-card-semantics");
    if (semantics instanceof Region region) {
      region.setMinWidth(0);
      region.setPrefHeight(Region.USE_COMPUTED_SIZE);
      region.setMaxWidth(Double.MAX_VALUE);
    }
    HBox.setHgrow(semantics, Priority.ALWAYS);

    var id = new Label("ID " + asset.getId());
    id.getStyleClass().add("observation-card-id-chip");
    id.setMinWidth(Region.USE_PREF_SIZE);
    id.setTooltip(new Tooltip(asset.getUrn()));

    var ret = new HBox(10, icon, label, semantics, id);
    ret.getStyleClass().add("observation-card-title-bar");
    ret.setAlignment(Pos.CENTER_LEFT);
    ret.setPadding(new Insets(5, 8, 5, 8));
    return ret;
  }

  private String observationLabel(Observation observation) {
    String label = observation.getName();
    if ((label == null || label.isBlank()) && observation.getObservable() != null) {
      label = observation.getObservable().getName();
    }
    return label == null || label.isBlank() ? Theme.getLabel(observation) : label;
  }

  private Observation inherentContext() {
    if (scope == null
        || asset.getObservable() == null
        || (!asset.getObservable().is(SemanticType.QUALITY)
            && !asset.getObservable().is(SemanticType.PROCESS))
        || scope.getDigitalTwin() == null
        || scope.getDigitalTwin().getKnowledgeGraph() == null) {
      return null;
    }

    var graph = scope.getDigitalTwin().getKnowledgeGraph();
    try {
      if (graph instanceof ClientKnowledgeGraph clientGraph) {
        return clientGraph.incoming(asset, GraphModel.Relationship.HAS_CHILD).stream()
            .filter(Observation.class::isInstance)
            .map(Observation.class::cast)
            .findFirst()
            .orElse(null);
      }

      for (KnowledgeGraph.Link link :
          graph.getLinks(
              asset,
              GraphModel.Relationship.Direction.INCOMING,
              scope,
              GraphModel.Relationship.HAS_CHILD)) {
        RuntimeAsset connected =
            sameAsset(link.target(), asset) ? link.source() : link.target();
        if (connected instanceof Observation observation) {
          return observation;
        }
      }
    } catch (RuntimeException e) {
      scope.warn("Unable to retrieve the observation's inherent context", e);
    }
    return null;
  }

  private boolean sameAsset(RuntimeAsset left, RuntimeAsset right) {
    if (left == null || right == null) {
      return false;
    }
    long leftId = left.getId() == -1 ? left.getTransientId() : left.getId();
    long rightId = right.getId() == -1 ? right.getTransientId() : right.getId();
    return leftId == rightId;
  }

  private boolean hasHistogram() {
    return asset.getHistograms() != null
        && asset.getHistograms().values().stream().anyMatch(java.util.Objects::nonNull);
  }

  private Histogram latestHistogram() {
    if (asset.getHistograms() == null || asset.getHistograms().isEmpty()) {
      return null;
    }
    Long latestTimestamp = null;
    Histogram latest = null;
    for (var entry : asset.getHistograms().entrySet()) {
      Long timestamp = normalizeTimestamp(entry.getKey());
      if (entry.getValue() != null
          && timestamp != null
          && (latestTimestamp == null || timestamp > latestTimestamp)) {
        latestTimestamp = timestamp;
        latest = entry.getValue();
      }
    }
    return latest;
  }

  private Histogram histogramAt(Long timestamp) {
    if (timestamp == null || asset.getHistograms() == null) {
      return latestHistogram();
    }
    for (var entry : asset.getHistograms().entrySet()) {
      Long entryTimestamp = normalizeTimestamp(entry.getKey());
      if (timestamp.equals(entryTimestamp)) {
        return entry.getValue();
      }
    }
    return null;
  }

  private Long normalizeTimestamp(Object value) {
    return switch (value) {
      case Number number -> number.longValue();
      case String string -> {
        try {
          yield Long.parseLong(string);
        } catch (NumberFormatException ignored) {
          yield null;
        }
      }
      case null, default -> null;
    };
  }

  private void updateHistogram(StackPane histogramSlot, Long timestamp) {
    if (histogramSlot == null) {
      return;
    }
    Histogram histogram = histogramAt(timestamp);
    var histogramCard =
        new HistogramCard(
            histogram == null ? Histogram.empty() : histogram,
            new HistogramCard.Options(),
            true);
    histogramCard.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
    histogramSlot.getChildren().setAll(histogramCard);
  }

  private Node createObservationContent(
      GeometryCard geometryCard, StackPane histogramSlot) {
    boolean quality =
        asset.getObservable() != null && asset.getObservable().is(SemanticType.QUALITY);
    if (quality && extended && scope != null) {
      var valueCard =
          valueOptions == null
              ? new ValueCard(asset, scope, true)
              : new ValueCard(asset, scope, true, valueOptions);
      var states = ValueCard.temporalStates(asset);
      geometryCard.setTimelineMarks(states);
      geometryCard.setSelectedTimelineMark(valueCard.getSelectedTimestamp());
      geometryCard.setTimelineMarkClickHandler(
          timestamp -> {
            geometryCard.setSelectedTimelineMark(timestamp);
            valueCard.selectTimestamp(timestamp);
            updateHistogram(histogramSlot, timestamp);
          });
      updateHistogram(histogramSlot, valueCard.getSelectedTimestamp());
      return valueCard;
    }

    if (histogramSlot != null) {
      var states = ValueCard.temporalStates(asset);
      Long selectedTimestamp = states.isEmpty() ? null : states.getFirst();
      geometryCard.setTimelineMarks(states);
      geometryCard.setSelectedTimelineMark(selectedTimestamp);
      geometryCard.setTimelineMarkClickHandler(
          timestamp -> {
            geometryCard.setSelectedTimelineMark(timestamp);
            updateHistogram(histogramSlot, timestamp);
          });
      updateHistogram(histogramSlot, selectedTimestamp);
    }
    Label title = new Label("Observation content");
    title.getStyleClass().add("observation-content-stub-title");
    Label detail =
        new Label(
            quality
                ? (scope == null
                    ? "Select or open the observation's digital twin to load the interactive map."
                    : "Open the detailed observation view to load the interactive map.")
                : "Interactive content for "
                    + (asset.getObservable() == null
                        ? "this observation"
                        : asset.getObservable().getSemantics().toString().toLowerCase())
                    + " is not implemented yet.");
    detail.getStyleClass().add("observation-content-stub-detail");
    detail.setWrapText(true);
    VBox placeholder = new VBox(6, title, detail);
    placeholder.getStyleClass().add("observation-content-stub");
    placeholder.setAlignment(Pos.CENTER);
    placeholder.setPadding(new Insets(16));
    return placeholder;
  }
}
