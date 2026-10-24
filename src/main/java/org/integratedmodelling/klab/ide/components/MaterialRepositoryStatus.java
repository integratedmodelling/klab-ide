package org.integratedmodelling.klab.ide.components;

import java.util.Set;
import org.integratedmodelling.klab.api.data.RepositoryState;

/** Git paths remain relative to the project, including for nested material folders. */
final class MaterialRepositoryStatus {
  private MaterialRepositoryStatus() {}

  static RepositoryState.Status status(RepositoryState state, String path, boolean directory) {
    if (state == null) return null;
    if (path.isEmpty()) return state.getOverallStatus();
    if (contains(state.getConflictingPaths(), path, directory)) return RepositoryState.Status.CONFLICTED;
    if (contains(state.getUntrackedPaths(), path, directory)
        || state.getUntrackedFolders().stream().anyMatch(folder -> path.equals(folder) || path.startsWith(folder + "/")
            || directory && folder.startsWith(path + "/")))
      return RepositoryState.Status.UNTRACKED;
    if (contains(state.getAddedPaths(), path, directory)) return RepositoryState.Status.ADDED;
    if (contains(state.getModifiedPaths(), path, directory)) return RepositoryState.Status.MODIFIED;
    if (contains(state.getRemovedPaths(), path, directory)) return RepositoryState.Status.REMOVED;
    if (contains(state.getUncommittedPaths(), path, directory)) return RepositoryState.Status.MODIFIED;
    return RepositoryState.Status.CLEAN;
  }

  private static boolean contains(Set<String> paths, String path, boolean directory) {
    return paths.contains(path) || directory && paths.stream().anyMatch(candidate -> candidate.startsWith(path + "/"));
  }
}
