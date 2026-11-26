package org.integratedmodelling.klab.ide.components;

import java.util.*;
import java.util.function.BooleanSupplier;
import org.integratedmodelling.klab.api.knowledge.organization.Workspace;
import org.integratedmodelling.klab.api.scope.UserScope;
import org.integratedmodelling.klab.api.services.ResourcesService;
import org.integratedmodelling.klab.api.services.ResourcesService.ProjectEditingLock;
import org.integratedmodelling.klab.api.services.ResourcesService.ProjectLockAction;
import org.integratedmodelling.klab.api.services.ResourcesService.ProjectLockRequest;
import org.integratedmodelling.klab.modeler.model.NavigableWorkspace;

/** Service calls and navigable-model preparation only; no JavaFX nodes are created here. */
final class WorkspaceOpening {
  private WorkspaceOpening() {}

  static NavigableWorkspace retrieve(ResourcesService service, String urn, UserScope user) {
    var workspace = service.retrieve(urn, Workspace.class, user);
    if (workspace == null || !Objects.equals(urn, workspace.getUrn()))
      throw new IllegalStateException("Resources did not return the requested workspace: " + urn);
    return new NavigableWorkspace(workspace);
  }

  record Prepared(NavigableWorkspace workspace, Access access) {}

  static Prepared prepare(ResourcesService service, String urn, UserScope user,
      BooleanSupplier cancelled) {
    var workspace = retrieve(service, urn, user);
    var access = access(service, urn, workspace.getProjects().stream().map(project -> project.getUrn()).toList(),
        user, cancelled);
    return new Prepared(workspace, access);
  }

  record Access(Set<String> flows, Set<String> lockedProjects, List<String> failures,
                Map<String, ProjectEditingLock> projectLocks) {
    Access(Set<String> flows, Set<String> lockedProjects, List<String> failures) {
      this(flows, lockedProjects, failures, Map.of());
    }
  }

  static Access access(ResourcesService service, String workspace, List<String> projects, UserScope user,
      BooleanSupplier cancelled) {
    var flows = new HashSet<String>();
    var locked = new HashSet<String>();
    var failures = new ArrayList<String>();
    if (cancelled.getAsBoolean()) return new Access(Set.of(), Set.of(), List.of());
    try {
      service.getFlows(true, user).stream().map(flow -> flow.getAssetUrn())
          .filter(Objects::nonNull).forEach(flows::add);
    } catch (RuntimeException ignored) {
      // Workflow discovery is optional, as in the existing constructor.
    }
    var projectLocks = new HashMap<String, ProjectEditingLock>();
    var action = service.isLocal() ? ProjectLockAction.ACQUIRE : ProjectLockAction.STATUS;
    for (var project : projects) {
      if (cancelled.getAsBoolean()) break;
      try {
        var lock = service.projectEditingLock(new ProjectLockRequest(workspace, project, action), user);
        if (lock != null) {
          projectLocks.put(project, lock);
          if (lock.editable()) locked.add(project);
        }
      } catch (RuntimeException error) {
        failures.add("Unable to prepare project " + project + ": " + error.getMessage());
      }
    }
    // Existing project locks are scope-owned and may predate this editor. Do not unlock them
    // merely because a late response is discarded; that would change another editor's access.
    return new Access(Set.copyOf(flows), Set.copyOf(locked), List.copyOf(failures), Map.copyOf(projectLocks));
  }
}
