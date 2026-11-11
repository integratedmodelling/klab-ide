package org.integratedmodelling.klab.ide.components.cards;

import atlantafx.base.util.BBCodeParser;
import javafx.event.ActionEvent;
import javafx.scene.control.Hyperlink;
import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.control.Tooltip;
import java.util.*;
import org.integratedmodelling.klab.api.knowledge.Concept;
import org.integratedmodelling.klab.api.lang.SemanticLexicalElement;
import org.integratedmodelling.klab.api.services.reasoner.objects.*;
import org.integratedmodelling.klab.ide.Theme;
import org.integratedmodelling.klab.ide.utils.AppContext;
import org.integratedmodelling.klab.ide.utils.BBCodeNodeRenderer;
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

  /** Size to the wrapped content, adding vertical scrolling only beyond the tooltip height limit. */
  public ScrollPane tooltipContent(double width, double maxHeight) {
    setMinHeight(Region.USE_PREF_SIZE);
    setMaxHeight(Region.USE_PREF_SIZE);
    var scroll = new ScrollPane(this) {
      @Override
      protected double computePrefHeight(double availableWidth) {
        // Reserve room for a vertical scrollbar so wrapping also fits when it becomes visible.
        double contentWidth = Math.max(1, width - 20);
        return Math.min(maxHeight,
            ObservableCard.this.prefHeight(contentWidth) + getInsets().getTop()
                + getInsets().getBottom() + 2);
      }
    };
    scroll.setFitToWidth(true);
    scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
    scroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
    scroll.setMinSize(0, 0);
    scroll.setPrefWidth(width);
    scroll.setMaxSize(width, maxHeight);
    return scroll;
  }

  /** Add already-loaded graph evidence without remote calls while rendering an inspector. */
  public void addRelationships(Map<String, List<Concept>> relationships) {
    if (relationships.isEmpty()) return;
    var content = (VBox) getCenter();
    content.getChildren().add(new Label("Relationships in the current graph"));
    relationships.forEach((label, concepts) -> {
      for (var concept : concepts) {
        var relationship = new Label(label);
        relationship.setWrapText(true);
        var row = new VBox(2, relationship, Theme.semanticExpression(List.of(StyledKimToken.create(concept))));
        row.setMinHeight(Region.USE_PREF_SIZE);
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
        var text = BBCodeParser.createLayout(BBCodeNodeRenderer.fromMarkdown(description));
        text.addEventHandler(ActionEvent.ACTION, event -> {
          if (event.getTarget() instanceof Hyperlink link && link.getUserData() instanceof String url
              && (url.startsWith("https://") || url.startsWith("http://") || url.startsWith("mailto:"))) {
            var hostServices = AppContext.getHostServices();
            if (hostServices != null) {
              hostServices.showDocument(url);
              event.consume();
            }
          }
        });
        text.setMaxWidth(Double.MAX_VALUE);
        text.getStyleClass().add("concept-documentation");
        content.getChildren().add(text);
        break;
      }
    }
    for (var notification : asset.getSemantics().getNotifications()) {
      var text = new Label(notification.getMessage()); text.setWrapText(true); content.getChildren().add(text);
    }
    // VBox must retain each wrapped paragraph's full height inside a scrolling tooltip.
    content.getChildren().forEach(child -> {
      if (child instanceof Region region) region.setMinHeight(Region.USE_PREF_SIZE);
    });
    content.setPadding(new Insets(10)); setCenter(content);
    setStyle("-fx-background-color: -color-bg-subtle; -fx-border-color: -color-border-default; -fx-border-radius: 4;");
  }
}
