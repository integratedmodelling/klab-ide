package org.integratedmodelling.klab.ide.components.cards;

import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import org.integratedmodelling.klab.api.knowledge.Observable;
import org.integratedmodelling.klab.api.knowledge.SemanticType;

/** A compact, reusable description of the currently validated observable. */
public class ObservableCard extends BaseCard<Observable> {
  public ObservableCard(Observable asset, boolean extended) { super(asset, null, extended); }
  @Override protected void drawContent() {
    var title = new Label(asset.getUrn()); title.setWrapText(true); title.setStyle("-fx-font-weight: bold;");
    var type = SemanticType.fundamentalType(asset.getSemantics().getType());
    var detail = new Label((type == null ? "Observable" : type.name().toLowerCase().replace('_', ' '))
        + (asset.getSemantics().isCollective() ? " / collective" : "") + (asset.isAbstract() ? " / abstract" : ""));
    var content = new VBox(6, title, detail); content.setPadding(new Insets(10)); setCenter(content);
    setStyle("-fx-background-color: -color-bg-subtle; -fx-border-color: -color-border-default; -fx-border-radius: 4;");
  }
}
