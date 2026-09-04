package org.integratedmodelling.klab.ide.components;

import java.util.List;
import java.time.Instant;
import java.util.UUID;
import org.integratedmodelling.klab.api.scope.UserScope;
import org.integratedmodelling.klab.api.knowledge.KlabAsset;
import org.integratedmodelling.klab.api.services.ResourcesService;
import org.integratedmodelling.klab.api.services.resources.workflow.Flow;
import org.integratedmodelling.klab.api.services.resources.workflow.Workflow;

/** Application extension point for workflow discovery and stage-specific JavaFX editors. */
public interface WorkflowUIProvider {

  WorkflowUIProvider NONE = (asset, scope) -> List.of();

  /**
   * Return candidate workflow schemas for the selected asset. The generic IDE shell additionally
   * enforces each schema's workflow-level asset types and the caller's workflow permissions.
   */
  List<Workflow> availableWorkflows(KlabAsset asset, UserScope scope);

  /**
   * Select a specialized stage editor. Returning {@code null} uses the generic metadata browser.
   */
  default WorkflowEditor.StageEditor stageEditor(
      Workflow workflow,
      Flow flow,
      Flow.State state,
      Workflow.StateSchema schema,
      boolean readOnly,
      Runnable validationChanged) {
    return null;
  }

  /** Start a flow after the generic shell has prepared and validated its initial stage. */
  default Flow startFlow(
      ResourcesService service,
      Workflow workflow,
      Flow.State initialState,
      UserScope scope) {
    return service.createFlow(workflow.getId(), initialState, scope);
  }

  /**
   * Build the non-persistent client model shown while the first stage is being completed. No
   * service call is made until the editor submits a successful transition.
   */
  default Flow draftFlow(Workflow workflow, Flow.State initialState, UserScope scope) {
    var now = Instant.now();
    var participant = org.integratedmodelling.klab.api.services.resources.workflow.WorkflowParticipant.from(scope);
    var flow = Flow.create();
    flow.setId("draft-" + UUID.randomUUID());
    flow.setWorkflowId(workflow.getId());
    flow.setWorkflowVersion(workflow.getVersion());
    flow.setAssetUrn(initialState.getAssetUrn());
    flow.setAssetType(initialState.getAssetType());
    flow.setOwner(participant.getIdentity());
    flow.setCreatedAt(now);
    flow.setUpdatedAt(now);
    initialState.setId(UUID.randomUUID().toString());
    initialState.setFlowId(flow.getId());
    initialState.setStatus(Flow.StateStatus.OPEN);
    initialState.setCreatedAt(now);
    initialState.setUpdatedAt(now);
    flow.getStates().put(initialState.getId(), initialState);
    flow.getCurrentStateIds().add(initialState.getId());
    return flow;
  }
}
