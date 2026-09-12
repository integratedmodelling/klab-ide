package org.integratedmodelling.klab.ide.components;

import static org.junit.jupiter.api.Assertions.*;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import javafx.scene.layout.Pane;
import org.integratedmodelling.klabeditor.MonacoEditorView.ReviewMarkerClick;
import org.junit.jupiter.api.Test;

class WorkflowReviewCallbacksTest {
  @Test
  void existingStageProvidersNeedNoReviewCallbacks() {
    var stage = new WorkflowEditor.StageEditor(new Pane(), null, null, null);
    assertTrue(stage.valid().getAsBoolean());
    assertDoesNotThrow(() -> stage.focusReview().accept(new ReviewMarkerClick("comment", 3, null, null)));
    assertDoesNotThrow(() -> stage.createComment().accept(3));
  }

  @Test
  void specializedStagesReceiveTheMarkerAndDocumentLine() {
    var clicked = new AtomicReference<ReviewMarkerClick>();
    var line = new AtomicInteger();
    var stage = new WorkflowEditor.StageEditor(new Pane(), null, null, null, clicked::set, line::set);
    var marker = new ReviewMarkerClick("attachment-comment", 17, "focus", "reviewer");
    stage.focusReview().accept(marker);
    stage.createComment().accept(23);
    assertSame(marker, clicked.get());
    assertEquals(23, line.get());
  }
}
