package org.integratedmodelling.klab.ide.components;

import static org.junit.jupiter.api.Assertions.*;
import org.integratedmodelling.klab.api.data.RepositoryState;
import org.junit.jupiter.api.Test;

class MaterialRepositoryStatusTest {
  @Test void specificStatusWinsOverGitUnionAndFoldersIncludeDescendants() {
    var state = new RepositoryState();
    state.getAddedPaths().add("assets/style.css"); state.getUncommittedPaths().add("assets/style.css");
    assertEquals(RepositoryState.Status.ADDED, MaterialRepositoryStatus.status(state, "assets/style.css", false));
    assertEquals(RepositoryState.Status.ADDED, MaterialRepositoryStatus.status(state, "assets", true));
    state.getAddedPaths().clear(); state.getModifiedPaths().add("assets/style.css");
    assertEquals(RepositoryState.Status.MODIFIED, MaterialRepositoryStatus.status(state, "assets", true));
    state.getConflictingPaths().add("assets/style.css");
    assertEquals(RepositoryState.Status.CONFLICTED, MaterialRepositoryStatus.status(state, "assets", true));
    assertEquals(RepositoryState.Status.CLEAN, MaterialRepositoryStatus.status(state, "asset", true));
  }

  @Test void newFoldersApplyToChildrenAndNullStateHasNoMarks() {
    var state = new RepositoryState(); state.getUntrackedFolders().add("assets/new");
    assertEquals(RepositoryState.Status.UNTRACKED, MaterialRepositoryStatus.status(state, "assets/new/notes.txt", false));
    assertEquals(RepositoryState.Status.UNTRACKED, MaterialRepositoryStatus.status(state, "assets", true));
    assertNull(MaterialRepositoryStatus.status(null, "assets/new", true));
  }
}
