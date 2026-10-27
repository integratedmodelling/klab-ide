package org.integratedmodelling.klab.ide.components;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import org.integratedmodelling.klab.api.knowledge.organization.Project;
import org.integratedmodelling.klab.api.lang.kactors.KActorsBehavior;
import org.integratedmodelling.klab.api.scope.UserScope;
import org.integratedmodelling.klab.api.services.ResourcesService;

/** Resolve the selected document family within its project, rather than the global behavior index. */
final class ProjectBehaviorSource {
  /** Reopen from the owning service, including when the local copy is opened from recents. */
  static KActorsBehavior openMirror(ManagedBehaviorMirrors mirrors, Path file,
      ResourcesService parser, UserScope user) throws IOException {
    return openMirror(mirrors, file, parser, user, remote -> false);
  }

  static KActorsBehavior openMirror(ManagedBehaviorMirrors mirrors, Path file,
      ResourcesService parser, UserScope user,
      java.util.function.Predicate<KActorsBehavior> useProjectSource) throws IOException {
    var origin = mirrors.origin(file).orElse(null);
    KActorsBehavior local = null;
    if (origin != null) {
      var type = origin.behaviorType();
      if (type == null) {
        local = parser.parseAsset(file.toUri().toURL(), KActorsBehavior.class, user);
        type = local == null ? null : local.getBehaviorType();
      }
      var owner = user.getServices(ResourcesService.class).stream()
          .filter(service -> origin.serviceId().equals(service.serviceId())).findFirst();
      if (owner.isPresent() && type != null) {
        var remote = retrieve(owner.get(), user, origin.projectUrn(), origin.behaviorUrn(),
            type);
        if (remote != null) {
          var result = mirrors.synchronize(origin.serviceId(), origin.projectUrn(),
              origin.behaviorUrn(), type, remote.getSourceCode(),
              remote.getLastUpdateTimestamp());
          if (result == ManagedBehaviorMirrors.Synchronization.DIRTY && useProjectSource.test(remote)) {
            mirrors.useProjectSource(file, remote);
            return remote;
          }
          if (result == ManagedBehaviorMirrors.Synchronization.UPDATED
              || result == ManagedBehaviorMirrors.Synchronization.UNCHANGED) {
            if (origin.behaviorType() == null) mirrors.checkout(origin.serviceId(),
                origin.projectUrn(), origin.behaviorUrn(), type, remote.getSourceCode(),
                remote.getLastUpdateTimestamp());
            return remote;
          }
        }
      }
    }
    return local != null ? local : parser.parseAsset(file.toUri().toURL(), KActorsBehavior.class, user);
  }

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
