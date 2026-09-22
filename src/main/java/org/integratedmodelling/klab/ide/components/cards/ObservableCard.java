package org.integratedmodelling.klab.ide.components.cards;

import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.control.Tooltip;
import java.util.*;
import org.integratedmodelling.klab.api.knowledge.Concept;
import org.integratedmodelling.klab.api.lang.SemanticLexicalElement;
import org.integratedmodelling.klab.api.services.reasoner.objects.*;
import org.integratedmodelling.klab.ide.Theme;
import org.kordamp.ikonli.javafx.FontIcon;
import org.kordamp.ikonli.material2.Material2AL;
import org.integratedmodelling.klab.api.knowledge.Observable;
import org.integratedmodelling.klab.api.knowledge.SemanticType;

/** A compact, reusable description of the currently validated observable. */
public class ObservableCard extends BaseCard<Observable> {
  public ObservableCard(Observable asset, boolean extended) { super(asset, null, extended); }
  /** Uses the server snapshot; drawing a card must never perform remote reasoning on the FX thread. */
  public ObservableCard(Concept concept, List<SemanticClauseRestriction> clauses) {
    this(preview(concept), true);
    var content = (VBox) getCenter();
    if (clauses == null) return;
    for (var clause : clauses) {
      var code = new ArrayList<>(clause.getCode());
      if (code.isEmpty() && clause.getFiller() != null) {
        Arrays.stream(SemanticLexicalElement.values()).filter(m -> m.role == clause.getRole())
            .findFirst().ifPresent(m -> code.add(StyledKimToken.create(m)));
        code.add(StyledKimToken.create(clause.getFiller()));
      }
      var icon = new FontIcon(clause.isInherited() ? Material2AL.CALL_MERGE : Material2AL.LABEL);
      icon.setIconSize(14);
      var origin = new Label(null, icon);
      String explanation = clause.isInherited() ? "Inherited restriction" : "Direct restriction";
      origin.setTooltip(new Tooltip(explanation)); origin.setAccessibleText(explanation);
      var text = Theme.semanticExpression(code);
      HBox.setHgrow(text, Priority.ALWAYS);
      content.getChildren().add(new HBox(6, origin, text));
    }
  }
  private static Observable preview(Concept concept) {
    var observable = new org.integratedmodelling.common.knowledge.ObservableImpl();
    observable.setSemantics(concept); observable.setUrn(concept.getUrn()); return observable;
  }

  /** Add already-loaded graph evidence without remote calls while rendering an inspector. */
  public void addRelationships(Map<String, List<Concept>> relationships) {
    if (relationships.isEmpty()) return;
    var content = (VBox) getCenter();
    content.getChildren().add(new Label("Relationships in the current graph"));
    relationships.forEach((label, concepts) -> {
      for (var concept : concepts) {
        var row = new VBox(2, new Label(label), Theme.semanticExpression(List.of(StyledKimToken.create(concept))));
        content.getChildren().add(row);
      }
    });
  }
  @Override protected void drawContent() {
    var title = Theme.semanticExpression(List.of(StyledKimToken.create(asset.getSemantics())));
    var type = SemanticType.fundamentalType(asset.getSemantics().getType());
    var detail = new Label((type == null ? "Observable" : type.name().toLowerCase().replace('_', ' '))
        + (asset.getSemantics().isCollective() ? " / collective" : "") + (asset.isAbstract() ? " / abstract" : ""));
    detail.setWrapText(true);
    var urn = new Label(asset.getSemantics().getUrn());
    urn.setWrapText(true);
    urn.setTooltip(new Tooltip("Concept URN"));
    var content = new VBox(6, title, detail, urn);
    var metadata = asset.getSemantics().getMetadata();
    for (var key : List.of(org.integratedmodelling.klab.api.data.Metadata.DC_COMMENT,
        org.integratedmodelling.klab.api.data.Metadata.RDFS_COMMENT)) {
      if (metadata.get(key) instanceof String description && !description.isBlank()) {
        var text = new Label(description); text.setWrapText(true); content.getChildren().add(text);
        break;
      }
    }
    for (var notification : asset.getSemantics().getNotifications()) {
      var text = new Label(notification.getMessage()); text.setWrapText(true); content.getChildren().add(text);
    }
    content.setPadding(new Insets(10)); setCenter(content);
    setStyle("-fx-background-color: -color-bg-subtle; -fx-border-color: -color-border-default; -fx-border-radius: 4;");
  }
}
