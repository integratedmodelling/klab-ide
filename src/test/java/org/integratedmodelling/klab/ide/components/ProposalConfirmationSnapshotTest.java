package org.integratedmodelling.klab.ide.components;

import static org.junit.jupiter.api.Assertions.*;
import java.lang.reflect.Proxy;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.*;
import javafx.application.Platform;
import javafx.scene.control.*;
import javafx.stage.Window;
import org.integratedmodelling.klab.api.identities.UserIdentity;
import org.integratedmodelling.klab.api.scope.*;
import org.integratedmodelling.klab.api.services.ResourcesService;
import org.integratedmodelling.klab.api.services.resources.workflow.*;
import org.integratedmodelling.klab.api.services.resources.workflow.impl.WorkflowImpl;
import org.junit.jupiter.api.*;

class ProposalConfirmationSnapshotTest {
  @BeforeAll static void start() { WorkflowProposalTransitionTest.start(); }
  static void upload(WorkflowEditor editor, Path file, Workflow.AttachmentRule rule) {
    try {
      var method = WorkflowEditor.class.getDeclaredMethod("upload", java.io.File.class, Workflow.AttachmentRule.class);
      method.setAccessible(true); method.invoke(editor, file.toFile(), rule);
    } catch (Exception e) { throw new RuntimeException(e); }
  }
  @Test void lateUploadAndRepeatedActionCannotChangeConfirmedBytesAndFailuresKeepDraft() throws Exception {
    var directory = Files.createTempDirectory("proposal-confirmation");
    var proposalFile = directory.resolve("proposal.yaml");
    var proposalText = "proposal_schema: classpath:/schemas/llm/domain-context-proposal.schema.json\ncontext_pack_version: '1.3'\nproposal:\n  id: p\n  revision_id: r1\n  existing_ontologies: []\n  actions: [{action_id: a}]\n";
    Files.writeString(proposalFile, proposalText);
    var ontologyFile = Files.writeString(directory.resolve("late.kwv"), "ontology late in domain root version 1.0.0; thing Entity;");
    WorkflowProposalTransitionTest.fx(() -> {
      var user = (UserIdentity) Proxy.newProxyInstance(UserIdentity.class.getClassLoader(), new Class<?>[]{UserIdentity.class},
          (p,m,a) -> switch(m.getName()) { case "getUsername" -> "editor"; case "getGroups" -> Set.of(); case "isAuthenticated", "isAnonymous" -> false; default -> null; });
      var scope = (UserScope) Proxy.newProxyInstance(UserScope.class.getClassLoader(), new Class<?>[]{UserScope.class, ServiceSideScope.class},
          (p,m,a) -> switch(m.getName()) { case "getUser" -> user; case "isAuthorized" -> true; default -> null; });
      var captured = new AtomicReference<Flow.InitializationRequest>();
      var calls = new AtomicInteger();
      var service = (ResourcesService) Proxy.newProxyInstance(ResourcesService.class.getClassLoader(), new Class<?>[]{ResourcesService.class}, (p,m,a) -> {
        if (m.getName().equals("initializeFlow")) {
          calls.incrementAndGet(); captured.set((Flow.InitializationRequest)a[1]);
          throw new IllegalStateException("Service failure: keep draft");
        }
        return null;
      });
      var workflow = new WorkflowImpl(); workflow.setId("review"); workflow.setVersion("1");
      var schema = new WorkflowImpl.StateSchemaImpl(); schema.setId("editing"); schema.setOpen(true); workflow.getStates().put("editing",schema);
      var transition = new WorkflowImpl.TransitionSchemaImpl(); transition.setId("submit"); transition.setSourceStates(Set.of("editing")); transition.setTargetState("editing");
      transition.getMetadata().put("proposalReviewOperation","SUBMIT"); workflow.getTransitions().put("submit",transition);
      var proposalRule = new WorkflowImpl.AttachmentRuleImpl(); proposalRule.setType("bootstrap-proposal"); proposalRule.setMediaType("application/vnd.klab.proposal+yaml"); proposalRule.setArity(1); schema.getAttachments().add(proposalRule);
      var ontologyRule = new WorkflowImpl.AttachmentRuleImpl(); ontologyRule.setType("candidate-ontology"); ontologyRule.setMediaType("application/vnd.klab.ontology"); ontologyRule.setArity(1); schema.getAttachments().add(ontologyRule);
      var flow = Flow.create(); flow.setId("draft"); flow.setWorkflowId("review"); flow.setOwner("editor");
      var state = Flow.State.create(); state.setId("editing"); state.setSchemaId("editing"); state.setOwner("editor"); state.setStatus(Flow.StateStatus.OPEN);
      flow.getStates().put("editing",state); flow.getCurrentStateIds().add("editing");
      var editor = new WorkflowEditor(service,scope,workflow,flow,ProposalStageEditor::create);
      upload(editor,proposalFile,proposalRule);
      var model = WorkflowProposalTransitionTest.view(editor).model(); model.rationale("Approve these exact bytes");
      var candidate = model.candidate();
      for (var answer : List.of(ButtonType.CANCEL, ButtonType.OK)) {
        var observed = new AtomicBoolean();
        var originalBytes = new AtomicReference<byte[]>();
        Platform.runLater(() -> {
          for (var window : Window.getWindows().stream().filter(Window::isShowing).toList()) {
            if (window.getScene().getRoot() instanceof DialogPane pane && pane.getContent() instanceof TextArea text) {
              observed.set(text.getText().contains("Ontology: not supplied"));
              // Reproduces a queued UploadBox completion inside the modal nested event loop.
              upload(editor,ontologyFile,ontologyRule);
              if (answer == ButtonType.OK) {
                try {
                  var field = WorkflowEditor.class.getDeclaredField("pendingAttachments"); field.setAccessible(true);
                  @SuppressWarnings("unchecked") var pending = (Map<String,List<Flow.AttachmentUpload>>)field.get(editor);
                  var bytes = pending.get("editing").getFirst().getContent(); originalBytes.set(bytes);
                  bytes[0] = '!'; // A retained mutable byte array must not change the confirmed snapshot.
                } catch (Exception e) { throw new RuntimeException(e); }
              }
              WorkflowProposalTransitionTest.call(editor,"confirm",Workflow.TransitionSchema.class,transition);
              ((Button)pane.lookupButton(answer)).fire();
            }
          }
        });
        WorkflowProposalTransitionTest.call(editor,"confirm",Workflow.TransitionSchema.class,transition);
        if (originalBytes.get() != null) originalBytes.get()[0] = 'p';
        assertTrue(observed.get());
        assertEquals(candidate,model.candidate());
        assertTrue(model.dirty());
        assertEquals(0,flow.getRevision());
        assertEquals(answer == ButtonType.CANCEL ? 0 : 1,calls.get());
      }
      var sent = captured.get();
      assertEquals(1,sent.getAttachments().size());
      assertArrayEquals(proposalText.getBytes(java.nio.charset.StandardCharsets.UTF_8),sent.getAttachments().getFirst().getContent());
      assertNull(sent.getTransition().getProposalReview().candidate()); // Server derives from frozen bytes.
      assertEquals("editing",sent.getTransition().getSourceStateId());
      assertEquals(-1,sent.getTransition().getExpectedRevision());
      assertEquals("Approve these exact bytes",sent.getTransition().getProposalReview().rationale());
      // Cancellation/failure release the guard; the same draft can now explicitly include the upload.
      upload(editor,ontologyFile,ontologyRule);
      assertNotNull(model.candidate().ontology());
      editor.close();
    });
  }
}
