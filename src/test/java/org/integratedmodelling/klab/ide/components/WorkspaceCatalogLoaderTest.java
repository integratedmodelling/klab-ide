package org.integratedmodelling.klab.ide.components;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Proxy;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import org.integratedmodelling.klab.api.knowledge.KlabAsset;
import org.integratedmodelling.klab.api.services.ResourcesService;
import org.integratedmodelling.klab.api.services.resources.ResourceInfo;
import org.junit.jupiter.api.Test;

class WorkspaceCatalogLoaderTest {

  @Test
  void loadsWorkspaceInfoWithOneQueryAndDiscardsMalformedOrDuplicateEntries() throws Exception {
    var calls = new AtomicInteger();
    var valid = workspace("one", null);
    var duplicate = workspace("one", KlabAsset.KnowledgeClass.WORKSPACE);
    var wrongClass = workspace("project", KlabAsset.KnowledgeClass.PROJECT);
    var service =
        service(
            () -> {
              calls.incrementAndGet();
              return List.of(valid, duplicate, wrongClass);
            });
    var scheduler = Executors.newSingleThreadScheduledExecutor();
    try {
      var loader = new WorkspaceCatalogLoader(Runnable::run, scheduler);
      var result = new CopyOnWriteArrayList<WorkspaceCatalogLoader.WorkspaceResult>();
      loader.requestWorkspaces(service, null, Duration.ofSeconds(1), result::add);

      assertEquals(1, calls.get());
      assertEquals(1, result.size());
      assertTrue(result.getFirst().succeeded());
      assertEquals(List.of(valid), result.getFirst().workspaces());
      assertEquals(2, result.getFirst().discardedEntries());
      assertEquals(KlabAsset.KnowledgeClass.WORKSPACE, valid.getKnowledgeClass());
      assertEquals("service-1", valid.getServiceId());
    } finally {
      scheduler.shutdownNow();
    }
  }

  @Test
  void timesOutWithoutDuplicatingTheBlockedCallAndPublishesLateRecovery() throws Exception {
    var entered = new CountDownLatch(1);
    var release = new CountDownLatch(1);
    var calls = new AtomicInteger();
    var service =
        service(
            () -> {
              calls.incrementAndGet();
              entered.countDown();
              try {
                release.await();
              } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
              }
              return List.of(workspace("late", KlabAsset.KnowledgeClass.WORKSPACE));
            });
    var requests = Executors.newSingleThreadExecutor();
    var scheduler = Executors.newSingleThreadScheduledExecutor();
    try {
      var loader = new WorkspaceCatalogLoader(requests, scheduler);
      var first = new CopyOnWriteArrayList<WorkspaceCatalogLoader.WorkspaceResult>();
      var second = new CopyOnWriteArrayList<WorkspaceCatalogLoader.WorkspaceResult>();

      loader.requestWorkspaces(service, null, Duration.ofMillis(40), first::add);
      assertTrue(entered.await(1, TimeUnit.SECONDS));
      loader.requestWorkspaces(service, null, Duration.ofMillis(40), second::add);
      awaitSize(first, 1);
      awaitSize(second, 1);

      assertTrue(first.getFirst().timedOut());
      assertTrue(second.getFirst().timedOut());
      assertEquals(1, calls.get(), "a timed-out in-flight request must remain single-flight");

      release.countDown();
      awaitSize(first, 2);
      awaitSize(second, 2);
      assertTrue(first.get(1).succeeded());
      assertTrue(first.get(1).late());
      assertFalse(first.get(1).workspaces().isEmpty());
    } finally {
      release.countDown();
      requests.shutdownNow();
      scheduler.shutdownNow();
    }
  }

  private static void awaitSize(List<?> results, int size) throws InterruptedException {
    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
    while (results.size() < size && System.nanoTime() < deadline) {
      Thread.sleep(5);
    }
    assertEquals(size, results.size());
  }

  private static ResourceInfo workspace(String urn, KlabAsset.KnowledgeClass knowledgeClass) {
    var info = new ResourceInfo();
    info.setUrn(urn);
    info.setKnowledgeClass(knowledgeClass);
    return info;
  }

  @SuppressWarnings("unchecked")
  private static ResourcesService service(Supplier<List<ResourceInfo>> query) {
    return (ResourcesService)
        Proxy.newProxyInstance(
            WorkspaceCatalogLoaderTest.class.getClassLoader(),
            new Class<?>[] {ResourcesService.class},
            (proxy, method, args) ->
                switch (method.getName()) {
                  case "query" -> query.get();
                  case "serviceId" -> "service-1";
                  case "hashCode" -> System.identityHashCode(proxy);
                  case "equals" -> proxy == args[0];
                  case "toString" -> "test resources service";
                  default -> defaultValue(method.getReturnType());
                });
  }

  private static Object defaultValue(Class<?> type) {
    if (!type.isPrimitive()) {
      return null;
    }
    if (type == boolean.class) {
      return false;
    }
    if (type == char.class) {
      return '\0';
    }
    return 0;
  }
}
