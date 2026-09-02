package org.integratedmodelling.klab.ide.components.cards;

import atlantafx.base.controls.Tile;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import org.integratedmodelling.klab.api.digitaltwin.GraphModel;
import org.integratedmodelling.klab.api.knowledge.Cohort;
import org.integratedmodelling.klab.ide.IDEContextScope;

public class CohortCard extends BaseCard<Cohort> {

  public CohortCard(Cohort asset, IDEContextScope scope, boolean extended) {
    super(asset, scope, extended);
  }

  @Override
  protected void drawContent() {
    var tile = new Tile();

    //    tile.setTitle("Observation " + Theme.getLabel(asset));
    //    tile.setDescription(Theme.getDescription(asset));
    //    tile.setGraphic(Theme.getGraphics(asset));
    //    setTop(tile);
    setCenter(createBody());
    //    setBottom(createFooter());
    //    if (extended) {
    //      getStyleClass().add(Styles.ELEVATED_2);
    //      setPadding(new Insets(2, 10, 4, 10));
    //    }
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

    var leftBox = new VBox(10);
    var geom = new GeometryCard(asset.getGeometry(), true);
    geom.setMinWidth(220);
    geom.setPrefWidth(240);
    geom.setMaxWidth(280);
    leftBox.getChildren().add(geom);

    var relationships =
        new RelationshipCard(
            asset,
            scope,
            GraphModel.Relationship.Direction.OUTGOING);
    relationships.setMinWidth(220);
    relationships.setPrefWidth(240);
    relationships.setMaxWidth(280);
    VBox.setVgrow(relationships, Priority.ALWAYS);
    leftBox.getChildren().add(relationships);

    Node value =
        extended && scope != null && GeoJsonCard.supportsCohort(asset)
            ? new GeoJsonCard(asset, scope, true)
            : createMapStub();
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

    ret.getChildren().addAll(leftBox, value, metadata);
    return ret;
  }

  private Node createMapStub() {
    var title = new javafx.scene.control.Label("Spatial features");
    title.getStyleClass().add("observation-content-stub-title");
    var detail =
        new javafx.scene.control.Label(
            scope == null
                ? "Select or open the cohort's digital twin to load the interactive map."
                : "This cohort has no two-dimensional spatial geometry.");
    detail.getStyleClass().add("observation-content-stub-detail");
    detail.setWrapText(true);
    var box = new VBox(6, title, detail);
    box.getStyleClass().add("observation-content-stub");
    box.setAlignment(javafx.geometry.Pos.CENTER);
    box.setPadding(new Insets(16));
    return box;
  }
}
