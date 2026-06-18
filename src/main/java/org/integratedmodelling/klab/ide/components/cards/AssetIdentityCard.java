package org.integratedmodelling.klab.ide.components.cards;

import java.util.List;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.image.Image;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import org.integratedmodelling.klab.api.data.RuntimeAsset;
import org.integratedmodelling.klab.api.knowledge.Cohort;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.provenance.Activity;
import org.integratedmodelling.klab.ide.Theme;

public class AssetIdentityCard extends BaseCard<RuntimeAsset> {

  private static final double DEFAULT_WIDTH = 320;
  private static final double DEFAULT_HEIGHT = 220;

  protected AssetIdentityCard(RuntimeAsset asset, boolean extended) {
    super(asset, extended);
  }

  @Override
  protected void drawContent() {

    BackgroundImage bgImage =
        new BackgroundImage(
            new Image("/org/integratedmodelling/klab/ide/icons/klab-im.png"),
            BackgroundRepeat.REPEAT,
            BackgroundRepeat.REPEAT,
            BackgroundPosition.CENTER,
            BackgroundSize.DEFAULT);

    // Create a semi-transparent black fill for fading
    BackgroundFill fill =
        new BackgroundFill(
            new Color(0, 0, 0, 0.5), // Black with 50% opacity
            CornerRadii.EMPTY,
            Insets.EMPTY);

    // Combine fill and image
    Background bg = new Background(List.of(fill), List.of(bgImage));
    setBackground(bg);

    getStyleClass().add("asset-id-card");
    setPadding(new Insets(8));
    setMinSize(220, 150);
    setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
    if (extended) {
      setPrefSize(Region.USE_COMPUTED_SIZE, Region.USE_COMPUTED_SIZE);
    } else {
      setPrefSize(DEFAULT_WIDTH, DEFAULT_HEIGHT);
    }

    var content = new VBox();
    content.setSpacing(8);
    content.setAlignment(Pos.CENTER);
    switch (asset) {
      case Observation observation -> setObservation(observation, content);
      case Activity activity -> setActivity(activity, content);
      case Cohort cohort -> setCohort(cohort, content);
      default -> throw new IllegalStateException("Unexpected value: " + asset);
    }
    setCenter(content);
  }

  private void setObservation(Observation observation, VBox content) {
    content
        .getChildren()
        .add((Node) Theme.getDisplayObject(observation.getObservable(), Theme.Detail.ONE_LINER));
  }

  private void setActivity(Activity observation, VBox content) {}

  private void setCohort(Cohort observation, VBox content) {}
}
