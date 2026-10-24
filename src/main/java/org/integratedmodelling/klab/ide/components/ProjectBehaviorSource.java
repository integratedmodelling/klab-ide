package org.integratedmodelling.klab.ide.components;

import java.util.List;
import java.util.Objects;
import org.integratedmodelling.klab.api.knowledge.organization.Project;
import org.integratedmodelling.klab.api.lang.kactors.KActorsBehavior;
import org.integratedmodelling.klab.api.scope.UserScope;
import org.integratedmodelling.klab.api.services.ResourcesService;

/** Resolve the selected document family within its project, rather than the global behavior index. */
final class ProjectBehaviorSource {
  static KActorsBehavior retrieve(ResourcesService service, UserScope user, String projectUrn,
      String behaviorUrn, KActorsBehavior.Type type) {
    var project = service.retrieve(projectUrn, Project.class, user);
    if (project == null) return null;
    List<KActorsBehavior> documents = switch (type) {
      case APP -> project.getApps();
      case UNITTEST -> project.getTestCases();
      case SCRIPT -> project.getScripts();
      default -> project.getBehaviors();
    };
    var matches = documents.stream().filter(document -> Objects.equals(behaviorUrn, document.getUrn())
        && document.getBehaviorType() == type).toList();
    if (matches.size() > 1) throw new IllegalArgumentException("Multiple " + type + " documents declare " + behaviorUrn + " in " + projectUrn);
    return matches.isEmpty() ? null : matches.getFirst();
  }
}
