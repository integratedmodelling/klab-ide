package org.integratedmodelling.klab.ide.components;

import java.lang.reflect.Proxy;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;
import org.integratedmodelling.common.utils.Utils;
import org.integratedmodelling.klab.api.identities.UserIdentity;
import org.integratedmodelling.klab.api.knowledge.KlabAsset;
import org.integratedmodelling.klab.api.scope.*;
import org.integratedmodelling.klab.api.services.ResourcesService;
import org.integratedmodelling.klab.api.services.resources.workflow.*;
import org.integratedmodelling.klab.api.services.resources.workflow.impl.*;

/** Isolated native UI fixture. All service calls are synthetic and never contact a real service. */
public final class ReviewEditorJourneyFixture {
  final WorkflowImpl workflow = definition("0.3");
  final UserScope scope;
  final ResourcesService service;
  final Map<String, byte[]> bytes = new HashMap<>();
  volatile Flow saved;
  volatile Flow.InitializationRequest initialization;
  volatile int creations, saves, uploads, preparations;
  volatile int diagrams, deletions;
  boolean failCreation, rejectCreation, conflictSave, failDiagram, failDeletion, rejectDeletion;
  Runnable beforeDelete;
  java.nio.file.Path diagramPath;
  Runnable beforeRead;

  ReviewEditorJourneyFixture() {
    var user = (UserIdentity) Proxy.newProxyInstance(UserIdentity.class.getClassLoader(), new Class<?>[]{UserIdentity.class},
        (p,m,a) -> switch (m.getName()) { case "getUsername" -> "fixture-editor"; case "getEmailAddress" -> "editor@example.invalid";
          case "isAuthenticated" -> true; case "isAnonymous" -> false; case "getGroups" -> Set.of(); default -> null; });
    scope = (UserScope) Proxy.newProxyInstance(UserScope.class.getClassLoader(), new Class<?>[]{UserScope.class, ServiceSideScope.class},
        (p,m,a) -> switch (m.getName()) { case "getUser" -> user; case "isAuthorized" -> true; default -> null; });
    service = (ResourcesService) Proxy.newProxyInstance(ResourcesService.class.getClassLoader(), new Class<?>[]{ResourcesService.class},
        (p,m,a) -> {
          switch (m.getName()) {
            case "initializeFlow": {
              creations++; initialization = (Flow.InitializationRequest)a[1];
              if (initialization.getTransition() != null || initialization.isPublicRead()) throw new AssertionError("Expected private bootstrap only");
              if (!workflow.getUrn().equals(a[0])) throw new AssertionError("Expected pinned current workflow");
              if (failCreation) return null;
              if (rejectCreation) throw new Utils.Http.Client.RequestFailure(400,"Rejected invalid proposal",null);
              var initial = initialization.getInitialState();
              saved = WorkflowUIProvider.NONE.draftFlow(workflow, initial, scope);
              saved.setId("fixture-flow-" + creations); saved.setRevision(1);
              initial.setFlowId(saved.getId()); initial.setOwner("fixture-editor");
              for (var upload : initialization.getAttachments()) add(upload, initial);
              report("CREATED"); return copy();
            }
            case "createFlow", "transitionFlow": throw new AssertionError("Review preparation must not use implicit transitions");
            case "getFlow": { if (beforeRead != null) { var hook = beforeRead; beforeRead = null; hook.run(); } return copy(); }
            case "getFlows": return saved == null ? List.of() : List.of(copy());
            case "updateFlowStateDetails": {
              var update = (Flow.StateDetailsUpdate)a[2];
              if (conflictSave || update.expectedRevision() != saved.getRevision()) throw new IllegalStateException("Revision conflict; refresh before retrying");
              var state = saved.getStates().get((String)a[1]); state.setTitle(update.title()); state.setDescription(update.description());
              saved.setRevision(saved.getRevision()+1); saves++; report("SAVED"); return copy();
            }
            case "updateFlowState": throw new AssertionError("Review details require revision-safe update");
            case "addFlowAttachment": {
              var upload = (Flow.AttachmentUpload)a[2];
              if (upload.getExpectedRevision() != saved.getRevision()) throw new AssertionError("Upload must bind current revision");
              var attachment = add(upload, saved.getStates().get((String)a[1]));
              saved.setRevision(saved.getRevision()+1); uploads++; report("UPLOADED"); return attachment;
            }
            case "getFlowAttachment": return bytes.get((String)a[1]);
            case "getFlowActions": return List.of(new WorkflowBehavior.AvailableAction("prepare-review-draft", "Prepare review draft", "prepare",
                List.of(new WorkflowBehavior.Parameter("proposalId", "String", null), new WorkflowBehavior.Parameter("purpose", "String", null)), saved.getRevision()));
            case "executeFlowAction": {
              var request = (WorkflowBehavior.ActionRequest)a[3];
              if (request.expectedRevision() != saved.getRevision() || request.parameters().get("proposalId").toString().isBlank()
                  || request.parameters().get("purpose").toString().isBlank()) throw new AssertionError("Preparation must use entered parameters and current revision");
              var upload = Flow.AttachmentUpload.create(); upload.setType("bootstrap-proposal"); upload.setFileName("prepared-proposal.yaml");
              upload.setMediaType("application/vnd.klab.proposal+yaml"); upload.setContent("proposal: synthetic-fixture".getBytes(StandardCharsets.UTF_8));
              add(upload, saved.getStates().get((String)a[1])); saved.setRevision(saved.getRevision()+1); preparations++; report("PREPARED"); return copy();
            }
            case "info": {
              diagrams++;
              if (saved != null && (!saved.getUrn().equals(a[0]) || a[1] != KlabAsset.KnowledgeClass.FLOW))
                throw new AssertionError("Persisted review must render its saved FLOW, not an INIT-only schema");
              if (saved == null && (!workflow.getUrn().equals(a[0]) || a[1] != KlabAsset.KnowledgeClass.WORKFLOW))
                throw new AssertionError("Unsaved review must render its pinned WORKFLOW definition");
              if (failDiagram) throw new IllegalStateException("Synthetic graph service failure");
              if (diagramPath != null) return javax.imageio.ImageIO.read(diagramPath.toFile());
              var image = new java.awt.image.BufferedImage(900, 240, java.awt.image.BufferedImage.TYPE_INT_ARGB);
              var graphics = image.createGraphics();
              try {
                graphics.setColor(java.awt.Color.WHITE); graphics.fillRect(0,0,900,240);
                graphics.setColor(new java.awt.Color(29,63,73));
                graphics.drawRoundRect(30,80,240,90,12,12); graphics.drawRoundRect(560,80,280,90,12,12);
                graphics.drawLine(270,125,560,125); graphics.drawLine(550,115,560,125); graphics.drawLine(550,135,560,125);
                graphics.setFont(new java.awt.Font("SansSerif", java.awt.Font.PLAIN,16));
                graphics.drawString("Synthetic bootstrap",55,130); graphics.drawString("Review cycle (fixture only)",580,130);
                graphics.drawString("Isolated PNG transport fixture: no production data",30,35);
              } finally { graphics.dispose(); }
              return image;
            }
            case "deleteFlow": {
              deletions++; if (beforeDelete != null) beforeDelete.run();
              if (failDeletion) throw new IllegalStateException("Synthetic service deletion failure");
              if (rejectDeletion) return false;
              saved = null; bytes.clear(); System.out.println("DELETED synthetic fixture only"); return true;
            }
            case "getUrl": return new URL("http://127.0.0.1:1");
            default: return null;
          }
        });
  }

  private Flow.Attachment add(Flow.AttachmentUpload upload, Flow.State state) {
    var attachment = new FlowImpl.AttachmentImpl(); attachment.setId("attachment-" + UUID.randomUUID());
    attachment.setFlowId(saved.getId()); attachment.setStateId(state.getId()); attachment.setType(upload.getType());
    attachment.setFileName(upload.getFileName()); attachment.setMediaType(upload.getMediaType()); attachment.setAssetType(upload.getAssetType());
    attachment.setChecksum(ProposalCandidateReader.digest(upload.getContent())); attachment.setSize(upload.getContent().length);
    attachment.setCreatedAt(Instant.now()); attachment.setCreatedBy("fixture-editor");
    state.getAttachments().add(attachment); bytes.put(attachment.getId(), upload.getContent().clone()); return attachment;
  }
  Flow copy() { return Utils.Json.parseObject(Utils.Json.asString(saved), Flow.class); }
  void report(String event) {
    System.out.println(event + " creations=" + creations + " saves=" + saves + " uploads=" + uploads + " preparations=" + preparations
        + " revision=" + saved.getRevision() + " state=" + Utils.Json.asString(saved.getStates().values().iterator().next()));
  }
  WorkflowEditor editor(Runnable created) {
    var initial = Flow.State.create(); initial.setSchemaId("bootstrap"); initial.setOwner("fixture-editor");
    initial.getAssignees().add("fixture-editor"); initial.setAssetUrn("fixture.hydrology"); initial.setAssetType(KlabAsset.KnowledgeClass.ONTOLOGY);
    var draft = WorkflowUIProvider.NONE.draftFlow(workflow, initial, scope);
    return new WorkflowEditor(service, scope, workflow, draft,
        (w,f,state,schema,readOnly,changed) -> ReviewStageEditor.create(w,f,state,service.getUrl()), null, flow -> created.run());
  }
  static WorkflowImpl definition(String version) {
    var workflow = new WorkflowImpl(); workflow.setId("ontology-review-pilot"); workflow.setVersion(version);
    workflow.setName("Ontology community review pilot"); workflow.setBehavior("klab.workflow.ontology_review"); workflow.getMetadata().put("reviewStages",true);
    var state = new WorkflowImpl.StateSchemaImpl(); state.setId("bootstrap"); state.setOpen(true);
    state.setDescription("Prepare and edit the initial proposal"); state.setInstructions("Prepare an ontology review without starting a review cycle.");
    state.setActions(List.of(new WorkflowBehavior.Action("prepare-review-draft","Prepare review draft","prepare",Map.of())));
    for (var pair : List.of(new String[]{"bootstrap-proposal","application/vnd.klab.proposal+yaml"},
        new String[]{"supporting-material","*/*"})) {
      var rule = new WorkflowImpl.AttachmentRuleImpl(); rule.setType(pair[0]); rule.setMediaType(pair[1]); rule.setArity(-1);
      state.getAttachments().add(rule);
    }
    workflow.getStates().put(state.getId(),state); return workflow;
  }
  public static void main(String[] args) {
    Platform.startup(() -> {
      javafx.application.Application.setUserAgentStylesheet(new atlantafx.base.theme.PrimerLight().getUserAgentStylesheet());
      var fixture = new ReviewEditorJourneyFixture();
      var root = new BorderPane();
      var menu = new MenuButton("Start workflow");
      for (var definition : WorkflowCatalog.latest(List.of(definition("0.1"), definition("0.2"), fixture.workflow))) {
        var item = new MenuItem(WorkflowCatalog.label(definition)); item.setOnAction(event -> root.setCenter(fixture.editor(() -> {})));
        menu.getItems().add(item);
      }
      var note = new Label("Isolated UI fixture: no production data, service or network calls");
      root.setTop(new HBox(12, menu, note));
      var stage = new Stage(); stage.setTitle("Ontology review IDE journey - isolated fixture");
      stage.setScene(new Scene(root, 1160, 900)); stage.setOnCloseRequest(event -> Platform.exit()); stage.show();
    });
  }
}
