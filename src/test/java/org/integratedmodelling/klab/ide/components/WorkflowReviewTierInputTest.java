package org.integratedmodelling.klab.ide.components;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import javafx.scene.control.*;
import javafx.stage.Window;
import org.integratedmodelling.klab.api.services.resources.workflow.WorkflowBehavior;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** Real preparation controls must reject invalid scope without changing the saved bootstrap. */
class WorkflowReviewTierInputTest {
  private static final WorkflowBehavior.Parameter TIER =
      new WorkflowBehavior.Parameter("requested_tier", "Integer", null);

  @BeforeAll static void start() { WorkflowProposalTransitionTest.start(); }
  @AfterEach void closeWindows() throws Exception {
    WorkflowProposalTransitionTest.fx(() -> Window.getWindows().stream().filter(Window::isShowing)
        .toList().forEach(Window::hide));
  }

  @Test void tierRequiresPositiveJavaIntegerWithoutChangingOtherActionSemantics() {
    for (String invalid : List.of("", " ", "0", "-1", "1.5", "abc", "NaN", "Infinity", "2147483648")) {
      var error = assertThrows(IllegalArgumentException.class,
          () -> WorkflowEditor.behaviorActionInput("prepare-review-draft", TIER, invalid), invalid);
      assertTrue(error.getMessage().startsWith("Requested tier must be an integer of 1 or greater"));
    }
    assertEquals(1, WorkflowEditor.behaviorActionInput("prepare-review-draft", TIER, "1"));
    assertEquals(2, WorkflowEditor.behaviorActionInput("prepare-review-draft", TIER, " 2 "));
    assertEquals(Integer.MAX_VALUE,
        WorkflowEditor.behaviorActionInput("prepare-review-draft", TIER, "2147483647"));
    assertEquals(-1, WorkflowEditor.behaviorActionInput("other-action", TIER, "-1"));
  }

  @Test void invalidAttemptsRetainDialogAndScopeThenCorrectedTierPreparesExactlyOnce() throws Exception {
    var fixture = new ReviewEditorJourneyFixture();
    fixture.preparationParameters = List.of(fixture.preparationParameters.getFirst(),
        fixture.preparationParameters.getLast(), TIER);
    var ref = new AtomicReference<WorkflowEditor>();
    WorkflowProposalTransitionTest.fx(() -> {
      var editor = WorkflowNativeControlsTest.savedEditor(fixture, () -> {}, ignored -> {});
      ref.set(editor); WorkflowNativeControlsTest.show(editor);
      ((Button) editor.lookup("#workflow-action-prepare-review-draft")).fire();
    });
    WorkflowNativeControlsTest.await(() -> dialog() != null);
    WorkflowProposalTransitionTest.fx(() -> {
      var pane = dialog();
      field(pane, "proposalId").setText("HydrologyReview");
      field(pane, "purpose").setText("Retain this scope");
      var tier = field(pane, "requested_tier");
      var run = (Button) pane.lookupButton(pane.getButtonTypes().stream()
          .filter(type -> "Run".equals(type.getText())).findFirst().orElseThrow());
      for (String invalid : List.of("0", "-1", "1.5", "", "abc", "2147483648")) {
        tier.setText(invalid); run.fire(); run.fire();
        assertSame(pane, dialog(), "Invalid input must not close the preparation dialog");
        assertTrue(((Label) pane.lookup("#workflow-action-validation")).getText()
            .startsWith("Requested tier must be an integer of 1 or greater"));
        assertEquals(invalid, tier.getText());
        assertEquals("Retain this scope", field(pane, "purpose").getText());
        assertEquals(0, fixture.preparations);
        assertEquals(3, fixture.saved.getRevision());
        assertTrue(fixture.saved.getStates().values().stream().allMatch(s -> s.getAttachments().isEmpty()));
      }
      tier.setText("2"); run.fire();
    });
    WorkflowNativeControlsTest.await(() -> fixture.preparations == 1 && !ref.get().isDisabled());
    WorkflowProposalTransitionTest.fx(() -> {
      assertNull(dialog());
      assertEquals(2, fixture.preparation.parameters().get("requested_tier"));
      assertEquals("HydrologyReview", fixture.preparation.parameters().get("proposalId"));
      assertEquals("Retain this scope", fixture.preparation.parameters().get("purpose"));
      assertEquals(3, fixture.preparation.expectedRevision());
      assertEquals(4, fixture.saved.getRevision());
      assertEquals(1, fixture.saved.getStates().values().iterator().next().getAttachments().size());
      assertNull(ref.get().lookup("#workflow-action-prepare-review-draft"));
      ref.get().close();
    });
  }

  @Test void cancelAfterInvalidTierDoesNotPrepareOrMutate() throws Exception {
    var fixture = new ReviewEditorJourneyFixture();
    fixture.preparationParameters = List.of(TIER);
    var ref = new AtomicReference<WorkflowEditor>();
    WorkflowProposalTransitionTest.fx(() -> {
      var editor = WorkflowNativeControlsTest.savedEditor(fixture, () -> {}, ignored -> {});
      ref.set(editor); WorkflowNativeControlsTest.show(editor);
      ((Button) editor.lookup("#workflow-action-prepare-review-draft")).fire();
    });
    WorkflowNativeControlsTest.await(() -> dialog() != null);
    WorkflowProposalTransitionTest.fx(() -> {
      var pane = dialog(); field(pane, "requested_tier").setText("0");
      ((Button) pane.lookupButton(pane.getButtonTypes().stream()
          .filter(type -> "Run".equals(type.getText())).findFirst().orElseThrow())).fire();
      ((Button) pane.lookupButton(ButtonType.CANCEL)).fire();
    });
    WorkflowNativeControlsTest.await(() -> !ref.get().isDisabled());
    WorkflowProposalTransitionTest.fx(() -> {
      assertEquals(0, fixture.preparations); assertEquals(3, fixture.saved.getRevision());
      assertFalse(ref.get().lookup("#workflow-action-prepare-review-draft").isDisabled());
      ref.get().close();
    });
  }

  private static TextField field(DialogPane pane, String name) {
    return (TextField) pane.lookup("#workflow-action-input-" + name);
  }
  private static DialogPane dialog() {
    return Window.getWindows().stream().filter(Window::isShowing).map(w -> w.getScene().getRoot())
        .filter(DialogPane.class::isInstance).map(DialogPane.class::cast)
        .filter(pane -> pane.lookup("#workflow-action-validation") != null).findFirst().orElse(null);
  }
}
