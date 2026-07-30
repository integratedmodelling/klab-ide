package org.integratedmodelling.klab.ide.components.cards;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import org.integratedmodelling.klab.api.digitaltwin.GraphModel;
import org.integratedmodelling.klab.api.knowledge.SemanticType;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.ide.IDEContextScope;

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

    Node value = createObservationContent(geom);
    if (value instanceof Region region) {
      region.setMinSize(180, 180);
      region.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
    }
    HBox.setHgrow(value, Priority.ALWAYS);

    var metadata =
        new MetadataCard(
            asset.getMetadata(),
            new MetadataCard.Options().title("Metadata").emptyTitle("Empty metadata"));
    metadata.setMinWidth(180);
    metadata.setPrefWidth(240);
    metadata.setMaxWidth(320);

    ret.getChildren().add(leftBox);
    ret.getChildren().add(value);
    ret.getChildren().add(metadata);
    return ret;
  }

  private Node createObservationContent(GeometryCard geometryCard) {
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
          });
      return valueCard;
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
