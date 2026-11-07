package org.integratedmodelling.klab.ide.components;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import javafx.scene.control.*;
import javafx.scene.Scene;
import javafx.stage.Stage;
import org.integratedmodelling.klab.api.services.resources.workflow.*;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ReviewEditorJourneyTest {
  @BeforeAll static void start() { WorkflowProposalTransitionTest.start(); }
  @TempDir Path directory;

  @Test void actualControlsCreateWithFilesThenSaveAndUploadWithoutLosingEdits() throws Exception {
    var fixture = new ReviewEditorJourneyFixture();
    var reference = directory.resolve("reference.txt"); Files.writeString(reference,"Synthetic reference material");
    var proposal = directory.resolve("proposal.yaml"); Files.writeString(proposal,"proposal: fixture-only");
    var completed = new CountDownLatch(1); var editorRef = new AtomicReference<WorkflowEditor>();
    WorkflowProposalTransitionTest.fx(() -> {
      var editor = fixture.editor(completed::countDown); editorRef.set(editor);
      var stage = new Stage(); stage.setScene(new Scene(editor, 1150,900)); stage.show();
      assertNotNull(editor.lookup("#workflow-diagram-button"));
      title(editor).setText("Water review"); description(editor).setText("Check scope and references");
      upload(editor, proposal, fixture.workflow.getStates().get("bootstrap").getAttachments().getFirst());
      upload(editor, reference, fixture.workflow.getStates().get("bootstrap").getAttachments().getLast());
      var create = (Button)editor.lookup("#workflow-create"); create.fire(); create.fire();
    });
    assertTrue(completed.await(15, TimeUnit.SECONDS));
    WorkflowProposalTransitionTest.fx(() -> {
      var editor = editorRef.get();
      assertEquals(1,fixture.creations); assertEquals(2,fixture.initialization.getAttachments().size());
      assertEquals("Water review",fixture.saved.getStates().values().iterator().next().getTitle());
      assertNull(editor.lookup("#workflow-create"));
      assertNull(editor.lookup("#workflow-action-prepare-review-draft"),"Do not regenerate over an uploaded proposal");
      assertNotNull(editor.lookup("#workflow-choose-attachment"));
      assertNotNull(editor.lookup("#review-save-details"));
      assertNotNull(editor.lookup("#review-open-ui"));
      title(editor).setText("Revised title"); description(editor).setText("Unsaved brief remains across uploads");
      upload(editor, reference,fixture.workflow.getStates().get("bootstrap").getAttachments().getLast());
      assertEquals("Revised title",title(editor).getText());
      assertEquals("Unsaved brief remains across uploads",description(editor).getText());
      assertEquals(1,fixture.uploads);
      ((Button)editor.lookup("#review-save-details")).fire();
      assertEquals(1,fixture.saves); assertEquals("Revised title",fixture.saved.getStates().values().iterator().next().getTitle());
      ((Button)editor.lookup("#workflow-diagram-button")).fire();
      assertTrue(javafx.stage.Window.getWindows().stream().anyMatch(w -> w.isShowing() && w.getScene().getRoot() instanceof DialogPane));
      editor.close(); ((Stage)editor.getScene().getWindow()).close();
    });
  }

  @Test void uncertainCreationRetainsFilesAndPreventsDuplicateRequest() throws Exception {
    var fixture = new ReviewEditorJourneyFixture(); fixture.failCreation=true;
    var reference = directory.resolve("reference.txt"); Files.writeString(reference,"Synthetic evidence");
    var editorRef = new AtomicReference<WorkflowEditor>();
    WorkflowProposalTransitionTest.fx(() -> {
      var editor = fixture.editor(() -> fail("Unexpected creation")); editorRef.set(editor);
      var stage = new Stage(); stage.setScene(new Scene(editor,1150,900)); stage.show();
      title(editor).setText("Retained title");
      upload(editor,reference,fixture.workflow.getStates().get("bootstrap").getAttachments().getLast());
      ((Button)editor.lookup("#workflow-create")).fire();
    });
    for(int i=0;i<100 && fixture.creations==0;i++) Thread.sleep(20);
    Thread.sleep(150);
    WorkflowProposalTransitionTest.fx(() -> {
      var editor=editorRef.get();
      assertTrue(editor.getFlow().getId().startsWith("draft-"));
      assertEquals("Retained title",title(editor).getText());
      var create=(Button)editor.lookup("#workflow-create"); assertTrue(create.isDisabled()); create.fire();
      assertEquals(1,fixture.creations); assertEquals(1,fixture.initialization.getAttachments().size());
      editor.close(); ((Stage)editor.getScene().getWindow()).close();
    });
  }

  @Test void conflictingSaveKeepsDraftAndPreparationInputsUsePublicServiceBoundary() throws Exception {
    var fixture=new ReviewEditorJourneyFixture(); var created=new CountDownLatch(1); var editorRef=new AtomicReference<WorkflowEditor>();
    WorkflowProposalTransitionTest.fx(() -> {
      var editor=fixture.editor(created::countDown); editorRef.set(editor);
      var stage=new Stage(); stage.setScene(new Scene(editor,1150,900)); stage.show();
      ((Button)editor.lookup("#workflow-create")).fire();
    });
    assertTrue(created.await(15,TimeUnit.SECONDS));
    WorkflowProposalTransitionTest.fx(() -> {
      var editor=editorRef.get();
      assertNotNull(editor.lookup("#workflow-action-prepare-review-draft"));
      fixture.conflictSave=true; title(editor).setText("Keep on conflict");
      ((Button)editor.lookup("#review-save-details")).fire();
      assertEquals("Keep on conflict",title(editor).getText()); assertEquals(0,fixture.saves);
      fixture.conflictSave=false; ((Button)editor.lookup("#review-save-details")).fire(); assertEquals(1,fixture.saves);
      ((Button)editor.lookup("#workflow-action-prepare-review-draft")).fire();
    });
    var entered = new java.util.concurrent.atomic.AtomicBoolean();
    for (int i=0; i<100 && !entered.get(); i++) {
      WorkflowProposalTransitionTest.fx(() -> javafx.stage.Window.getWindows().stream().filter(javafx.stage.Window::isShowing)
          .map(window -> window.getScene().getRoot()).filter(DialogPane.class::isInstance).map(DialogPane.class::cast)
          .filter(pane -> pane.getButtonTypes().stream().anyMatch(type -> "Run".equals(type.getText()))).findFirst()
          .ifPresent(pane -> {
            var content = (javafx.scene.layout.VBox)pane.getContent();
            var fields=content.getChildren().stream().filter(TextField.class::isInstance).map(TextField.class::cast).toList();
            assertEquals(2,fields.size()); fields.get(0).setText("HydrologyReview"); fields.get(1).setText("Check water scope");
            entered.set(true);
            var run=pane.getButtonTypes().stream().filter(type -> "Run".equals(type.getText())).findFirst().orElseThrow();
            ((Button)pane.lookupButton(run)).fire();
          }));
      if (!entered.get()) Thread.sleep(20);
    }
    assertTrue(entered.get(), "Preparation parameters must be rendered in a dialog");
    for (int i=0;i<100 && fixture.preparations==0;i++) Thread.sleep(20);
    assertEquals(1,fixture.preparations);
    WorkflowProposalTransitionTest.fx(() -> {
      assertTrue(fixture.saved.getStates().values().iterator().next().getAttachments().stream()
          .anyMatch(attachment -> "prepared-proposal.yaml".equals(attachment.getFileName())));
      editorRef.get().close(); ((Stage)editorRef.get().getScene().getWindow()).close();
    });
  }

  @Test void definiteRejectionRetainsDraftAndAllowsAnExplicitCorrectedRetry() throws Exception {
    var fixture=new ReviewEditorJourneyFixture(); fixture.rejectCreation=true;
    var created=new CountDownLatch(1); var editorRef=new AtomicReference<WorkflowEditor>();
    WorkflowProposalTransitionTest.fx(() -> {
      var editor=fixture.editor(created::countDown); editorRef.set(editor);
      var stage=new Stage(); stage.setScene(new Scene(editor,1150,900)); stage.show();
      title(editor).setText("Draft retained after rejection");
      ((Button)editor.lookup("#workflow-create")).fire();
    });
    var ready=new java.util.concurrent.atomic.AtomicBoolean();
    for(int i=0;i<100 && !ready.get();i++) {
      WorkflowProposalTransitionTest.fx(() -> ready.set(fixture.creations==1 && !editorRef.get().isDisabled()));
      if(!ready.get()) Thread.sleep(20);
    }
    assertTrue(ready.get());
    WorkflowProposalTransitionTest.fx(() -> {
      var editor=editorRef.get();
      assertEquals("Draft retained after rejection",title(editor).getText());
      var create=(Button)editor.lookup("#workflow-create"); assertFalse(create.isDisabled());
      fixture.rejectCreation=false; title(editor).setText("Corrected draft"); create.fire();
    });
    assertTrue(created.await(15,TimeUnit.SECONDS));
    WorkflowProposalTransitionTest.fx(() -> {
      assertEquals(2,fixture.creations);
      assertEquals("Corrected draft",fixture.saved.getStates().values().iterator().next().getTitle());
      editorRef.get().close(); ((Stage)editorRef.get().getScene().getWindow()).close();
    });
  }

  @Test void uploadReloadAdoptsConcurrentDetailsWhenLocalFieldsAreClean() throws Exception {
    uploadReloadWithConcurrentDetails(false);
  }

  @Test void uploadReloadRetainsDirtyFieldsButBlocksSavingAcrossConcurrentDetails() throws Exception {
    uploadReloadWithConcurrentDetails(true);
  }

  private void uploadReloadWithConcurrentDetails(boolean dirty) throws Exception {
    var fixture = new ReviewEditorJourneyFixture(); var created = new CountDownLatch(1);
    var editorRef = new AtomicReference<WorkflowEditor>();
    var reference = directory.resolve("concurrent-reference.txt"); Files.writeString(reference, "Synthetic reference");
    WorkflowProposalTransitionTest.fx(() -> {
      var editor = fixture.editor(created::countDown); editorRef.set(editor);
      var stage = new Stage(); stage.setScene(new Scene(editor, 1150, 900)); stage.show();
      title(editor).setText("Original title"); description(editor).setText("Original brief");
      ((Button) editor.lookup("#workflow-create")).fire();
    });
    assertTrue(created.await(15, TimeUnit.SECONDS));
    WorkflowProposalTransitionTest.fx(() -> {
      var editor = editorRef.get();
      if (dirty) title(editor).setText("Unsaved local title");
      fixture.beforeRead = () -> {
        var state = fixture.saved.getStates().values().iterator().next();
        state.setTitle("Concurrent server title"); state.setDescription("Concurrent server brief");
        fixture.saved.setRevision(fixture.saved.getRevision() + 1);
      };
      upload(editor, reference, fixture.workflow.getStates().get("bootstrap").getAttachments().getLast());
      assertEquals(1, fixture.uploads); assertEquals(3, fixture.saved.getRevision());
      assertEquals(dirty ? "Unsaved local title" : "Concurrent server title", title(editor).getText());
      assertEquals("Concurrent server brief", description(editor).getText(), "Clean fields must not be restored over server edits");
      ((Button) editor.lookup("#review-save-details")).fire();
      assertEquals(dirty ? 0 : 1, fixture.saves, "A conflicted draft must not adopt the newer save revision");
      assertEquals("Concurrent server title", fixture.saved.getStates().values().iterator().next().getTitle());
      if (dirty) {
        upload(editor, reference, fixture.workflow.getStates().get("bootstrap").getAttachments().getLast());
        assertEquals(1, fixture.uploads, "A second upload must not silently clear the conflict");
      }
      editor.close(); ((Stage) editor.getScene().getWindow()).close();
    });
  }
  static TextField title(WorkflowEditor editor) { return (TextField)editor.lookup("#review-stage-title"); }
  static TextArea description(WorkflowEditor editor) { return (TextArea)editor.lookup("#review-stage-description"); }
  static void upload(WorkflowEditor editor,Path file,Workflow.AttachmentRule rule) {
    try { var method=WorkflowEditor.class.getDeclaredMethod("upload",java.io.File.class,Workflow.AttachmentRule.class);
      method.setAccessible(true); method.invoke(editor,file.toFile(),rule); }
    catch(Exception error) { throw new RuntimeException(error); }
  }
}
