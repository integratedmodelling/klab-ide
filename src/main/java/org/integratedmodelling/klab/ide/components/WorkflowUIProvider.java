package org.integratedmodelling.klab.ide.components;

import java.util.List;
import org.integratedmodelling.klab.api.scope.UserScope;
import org.integratedmodelling.klab.api.knowledge.KlabAsset;
import org.integratedmodelling.klab.api.services.ResourcesService;
import org.integratedmodelling.klab.api.services.resources.workflow.Flow;
import org.integratedmodelling.klab.api.services.resources.workflow.Workflow;

/** Application extension point for workflow discovery and stage-specific JavaFX editors. */
public interface WorkflowUIProvider {

  WorkflowUIProvider NONE = (asset, scope) -> List.of();

  /** Return the workflow schemas the user may start for the selected asset. */
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
}
