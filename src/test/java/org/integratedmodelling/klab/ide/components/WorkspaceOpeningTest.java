package org.integratedmodelling.klab.ide.components;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.integratedmodelling.klab.api.knowledge.organization.Workspace;
import org.integratedmodelling.klab.api.knowledge.organization.impl.WorkspaceImpl;
import org.integratedmodelling.klab.api.scope.UserScope;
import org.integratedmodelling.klab.api.services.ResourcesService;
import org.integratedmodelling.klab.ide.utils.AsyncOpen;
import org.integratedmodelling.klab.modeler.model.NavigableWorkspace;
import org.junit.jupiter.api.Test;

class WorkspaceOpeningTest {
  @Test void slowWorkspaceServiceAndModelPreparationRunOffCallerThread() throws Exception {
    var entered = new CountDownLatch(1); var release = new CountDownLatch(1);
    var service = mock(ResourcesService.class); var user = mock(UserScope.class);
    var workspace = new WorkspaceImpl(); workspace.setUrn("local");
    var caller = Thread.currentThread(); var retrieval = new AtomicReference<Thread>();
    when(service.retrieve("local", Workspace.class, user)).thenAnswer(call -> {
      retrieval.set(Thread.currentThread()); entered.countDown();
      assertTrue(release.await(5, TimeUnit.SECONDS)); return workspace;
    });
    var ui = new LinkedBlockingQueue<Runnable>(); var results = new ArrayList<NavigableWorkspace>();
    var open = new AsyncOpen<String, NavigableWorkspace>(task -> Thread.startVirtualThread(task), ui::add);
    try {
      open.open("local", () -> WorkspaceOpening.retrieve(service, "local", user), () -> true,
          results::add, error -> fail(error), () -> {});
      assertTrue(entered.await(5, TimeUnit.SECONDS)); assertNotSame(caller, retrieval.get());
      assertTrue(results.isEmpty()); release.countDown();
      var deliver = ui.poll(5, TimeUnit.SECONDS); assertNotNull(deliver); deliver.run();
      assertEquals("local", results.getFirst().getUrn());
      verify(service, never()).getFlows(anyBoolean(), any());
      verify(service, never()).lockProject(anyString(), any());
    } finally { release.countDown(); }
  }

  @Test void projectAccessFinishesOnWorkerBeforeWorkspaceCanBeDelivered() throws Exception {
    var service = mock(ResourcesService.class); var user = mock(UserScope.class);
    var project = new org.integratedmodelling.klab.api.knowledge.organization.impl.ProjectImpl();
    project.setUrn("imod");
    var workspace = new WorkspaceImpl(); workspace.setUrn("local"); workspace.setProjects(List.of(project));
    when(service.retrieve("local", Workspace.class, user)).thenReturn(workspace);
    when(service.getFlows(true, user)).thenReturn(List.of());
    var entered = new CountDownLatch(1); var release = new CountDownLatch(1);
    var caller = Thread.currentThread();
    when(service.lockProject("imod", user)).thenAnswer(call -> {
      assertNotSame(caller, Thread.currentThread()); entered.countDown();
      assertTrue(release.await(5, TimeUnit.SECONDS)); return true;
    });
    var ui = new LinkedBlockingQueue<Runnable>(); var results = new ArrayList<WorkspaceOpening.Prepared>();
    var open = new AsyncOpen<String, WorkspaceOpening.Prepared>(task -> Thread.startVirtualThread(task), ui::add);
    try {
      open.open("local", () -> WorkspaceOpening.prepare(service, "local", user, () -> false),
          () -> true, results::add, error -> fail(error), () -> {});
      assertTrue(entered.await(5, TimeUnit.SECONDS)); assertTrue(ui.isEmpty()); assertTrue(results.isEmpty());
      release.countDown(); var deliver = ui.poll(5, TimeUnit.SECONDS); assertNotNull(deliver); deliver.run();
      assertEquals(Set.of("imod"), results.getFirst().access().lockedProjects());
      assertEquals("local", results.getFirst().workspace().getUrn());
    } finally { release.countDown(); }
  }

  @Test void missingOrWrongWorkspaceFailsInsteadOfOpeningAnUnrelatedSource() {
    var service = mock(ResourcesService.class); var user = mock(UserScope.class);
    assertThrows(IllegalStateException.class, () -> WorkspaceOpening.retrieve(service, "local", user));
    var other = new WorkspaceImpl(); other.setUrn("other");
    when(service.retrieve("local", Workspace.class, user)).thenReturn(other);
    assertThrows(IllegalStateException.class, () -> WorkspaceOpening.retrieve(service, "local", user));
  }

  @Test void cancelledWorkspaceAccessDoesNotStartMoreProjectLocksOrUnlockExistingScopeLocks() {
    var service = mock(ResourcesService.class); var user = mock(UserScope.class); var closed = new AtomicBoolean();
    when(service.getFlows(true, user)).thenReturn(List.of());
    when(service.lockProject("a", user)).thenAnswer(call -> { closed.set(true); return true; });
    var access = WorkspaceOpening.access(service, List.of("a", "b"), user, closed::get);
    assertEquals(Set.of("a"), access.lockedProjects());
    verify(service, never()).lockProject("b", user);
    verify(service, never()).unlockProject(anyString(), any());
    clearInvocations(service);
    WorkspaceOpening.access(service, List.of("a", "b"), user, () -> true);
    verifyNoInteractions(service);
  }

  @Test void optionalFlowFailureAndOneProjectFailureDoNotPreventOtherProjectsOpening() {
    var service = mock(ResourcesService.class); var user = mock(UserScope.class);
    when(service.getFlows(true, user)).thenThrow(new IllegalStateException("Offline workflow provider"));
    when(service.lockProject("a", user)).thenThrow(new IllegalStateException("Locked elsewhere"));
    when(service.lockProject("b", user)).thenReturn(true);
    var access = WorkspaceOpening.access(service, List.of("a", "b"), user, () -> false);
    assertEquals(Set.of("b"), access.lockedProjects()); assertTrue(access.flows().isEmpty());
    assertEquals(1, access.failures().size()); assertTrue(access.failures().getFirst().contains("Locked elsewhere"));
  }
}
