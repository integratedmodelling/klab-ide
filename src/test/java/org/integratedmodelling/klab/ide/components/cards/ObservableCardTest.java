package org.integratedmodelling.klab.ide.components.cards;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import javafx.application.Platform;
import javafx.application.Application;
import javafx.geometry.Orientation;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollBar;
import javafx.scene.layout.StackPane;
import org.integratedmodelling.common.knowledge.ConceptImpl;
import org.integratedmodelling.klab.api.data.Metadata;
import org.integratedmodelling.klab.api.knowledge.SemanticType;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class ObservableCardTest {
  @BeforeAll
  static void startFx() throws Exception {
    var ready = new CompletableFuture<Void>();
    try {
      Platform.startup(() -> { Platform.setImplicitExit(false); ready.complete(null); });
    } catch (IllegalStateException started) {
      Platform.runLater(() -> ready.complete(null));
    }
    ready.get(15, TimeUnit.SECONDS);
  }

  @Test
  void shortTooltipUsesContentHeightWithoutScrolling() throws Exception {
    checkLayout(false, false);
    checkLayout(false, true);
  }

  @Test
  void longTooltipCapsHeightAndKeepsCompleteWrappedContentScrollable() throws Exception {
    checkLayout(true, false);
    checkLayout(true, true);
  }

  private static void checkLayout(boolean longContent, boolean dark) throws Exception {
    var result = new CompletableFuture<Void>();
    Platform.runLater(() -> {
      try {
        Application.setUserAgentStylesheet(dark
            ? new atlantafx.base.theme.PrimerDark().getUserAgentStylesheet()
            : new atlantafx.base.theme.PrimerLight().getUserAgentStylesheet());
        var concept = new ConceptImpl();
        concept.setUrn("test:Observable");
        concept.getType().add(SemanticType.SUBJECT);
        String description = longContent ? "A long description that must remain readable. ".repeat(150)
            : "A short description.";
        concept.getMetadata().put(Metadata.DC_COMMENT, description);
        var card = new ObservableCard(concept, List.of());
        if (longContent) card.addRelationships(Map.of("Related observable", List.of(concept)));
        var scroll = card.tooltipContent(360, 320);
        var root = new StackPane(scroll);
        new Scene(root);
        root.applyCss();
        root.autosize();
        root.layout();
        scroll.layout();
        card.layout();
        assertTrue(scroll.getHeight() <= 320, "Tooltip must respect the height cap");
        assertTrue(scroll.getWidth() <= 360, "Tooltip must respect the width cap");
        var paragraph = card.lookupAll(".label").stream()
            .filter(Label.class::isInstance).map(Label.class::cast)
            .filter(label -> description.equals(label.getText())).findFirst().orElseThrow();
        assertTrue(paragraph.getHeight() >= paragraph.prefHeight(paragraph.getWidth()) - 1,
            "Wrapped description must retain its full height");
        double viewportHeight = scroll.getViewportBounds().getHeight();
        var verticalBar = scroll.lookupAll(".scroll-bar").stream()
            .filter(ScrollBar.class::isInstance).map(ScrollBar.class::cast)
            .filter(bar -> bar.getOrientation() == Orientation.VERTICAL).findFirst().orElseThrow();
        assertEquals(longContent, verticalBar.isVisible(), "Scroll only when content exceeds the cap");
        if (longContent) {
          assertTrue(card.getHeight() > viewportHeight, "Long content must scroll");
          scroll.setVvalue(1);
          scroll.layout();
          assertEquals(1, scroll.getVvalue());
        } else {
          assertTrue(scroll.getHeight() < 320, "Short content must not fill the height cap");
          assertTrue(card.getHeight() <= viewportHeight + 1, "Short content must fit without scrolling");
        }
        result.complete(null);
      } catch (Throwable error) {
        result.completeExceptionally(error);
      }
    });
    result.get(15, TimeUnit.SECONDS);
  }
}
