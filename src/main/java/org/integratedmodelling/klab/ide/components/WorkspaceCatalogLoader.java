package org.integratedmodelling.klab.ide.components;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Supplier;
import org.integratedmodelling.klab.api.authentication.CRUDOperation;
import org.integratedmodelling.klab.api.knowledge.KlabAsset;
import org.integratedmodelling.klab.api.scope.UserScope;
import org.integratedmodelling.klab.api.services.ResourcesService;
import org.integratedmodelling.klab.api.services.resources.ResourceInfo;

/**
 * Loads the workspace catalogue without involving the JavaFX application thread.
 *
 * <p>Calls are single-flight per service. A timed-out call is therefore not submitted repeatedly
 * every time the browser is opened; if it eventually returns, its valid result is still delivered.
 */
final class WorkspaceCatalogLoader {

  record WorkspaceResult(
      ResourcesService service,
      List<ResourceInfo> workspaces,
      Throwable failure,
      boolean timedOut,
      int discardedEntries,
      boolean late) {

    boolean succeeded() {
      return failure == null && !timedOut;
    }

    WorkspaceResult asLate() {
      return new WorkspaceResult(
          service, workspaces, failure, timedOut, discardedEntries, true);
    }
  }

  record CapabilityResult(
      ResourcesService service,
      boolean canCreate,
      String serviceName,
      Throwable failure,
      boolean timedOut,
      boolean late) {

    boolean succeeded() {
      return failure == null && !timedOut;
    }

    CapabilityResult asLate() {
      return new CapabilityResult(service, canCreate, serviceName, failure, timedOut, true);
    }
  }

  private static final Executor REQUEST_EXECUTOR =
      Executors.newThreadPerTaskExecutor(
          Thread.ofVirtual().name("workspace-catalog-request-", 0).factory());
  private static final ScheduledExecutorService TIMEOUT_EXECUTOR =
      Executors.newSingleThreadScheduledExecutor(
          Thread.ofPlatform().daemon().name("workspace-catalog-timeout").factory());

  private final Executor requestExecutor;
  private final ScheduledExecutorService timeoutExecutor;
  private final ConcurrentHashMap<ResourcesService, CompletableFuture<WorkspaceResult>>
      workspaceRequests = new ConcurrentHashMap<>();
  private final ConcurrentHashMap<ResourcesService, CompletableFuture<CapabilityResult>>
      capabilityRequests = new ConcurrentHashMap<>();

  WorkspaceCatalogLoader() {
    this(REQUEST_EXECUTOR, TIMEOUT_EXECUTOR);
  }

  WorkspaceCatalogLoader(Executor requestExecutor, ScheduledExecutorService timeoutExecutor) {
    this.requestExecutor = requestExecutor;
    this.timeoutExecutor = timeoutExecutor;
  }

  void requestWorkspaces(
      ResourcesService service,
      UserScope scope,
      Duration timeout,
      Consumer<WorkspaceResult> consumer) {
    var request =
        workspaceRequests.computeIfAbsent(
            service,
            ignored ->
                CompletableFuture.supplyAsync(
                    () -> loadWorkspaces(service, scope), requestExecutor));
    request.whenComplete((result, error) -> workspaceRequests.remove(service, request));
    deliverWithTimeout(
        request,
        timeout,
        () ->
            new WorkspaceResult(
                service,
                List.of(),
                new java.util.concurrent.TimeoutException("Workspace catalogue request timed out"),
                true,
                0,
                false),
        consumer,
        WorkspaceResult::succeeded,
        WorkspaceResult::asLate);
  }

  void requestCapabilities(
      ResourcesService service,
      UserScope scope,
      Duration timeout,
      Consumer<CapabilityResult> consumer) {
    var request =
        capabilityRequests.computeIfAbsent(
            service,
            ignored ->
                CompletableFuture.supplyAsync(
                    () -> loadCapabilities(service, scope), requestExecutor));
    request.whenComplete((result, error) -> capabilityRequests.remove(service, request));
    deliverWithTimeout(
        request,
        timeout,
        () ->
            new CapabilityResult(
                service,
                false,
                null,
                new java.util.concurrent.TimeoutException("Capabilities request timed out"),
                true,
                false),
        consumer,
        CapabilityResult::succeeded,
        CapabilityResult::asLate);
  }

  private WorkspaceResult loadWorkspaces(ResourcesService service, UserScope scope) {
    try {
      // The generic query endpoint is the collection form of info(): one remote call returns all
      // visible workspace ResourceInfo projections and avoids capabilities + N info() calls.
      var response =
          service.query(null, KlabAsset.KnowledgeClass.WORKSPACE, ResourceInfo.class, scope);
      if (response == null) {
        throw new IllegalStateException("Service returned a null workspace catalogue");
      }
      var valid = new LinkedHashMap<String, ResourceInfo>();
      int discarded = 0;
      for (var info : response) {
        if (info == null
            || info.getUrn() == null
            || info.getUrn().isBlank()
            || (info.getKnowledgeClass() != null
                && info.getKnowledgeClass() != KlabAsset.KnowledgeClass.WORKSPACE)) {
          discarded++;
          continue;
        }
        if (info.getKnowledgeClass() == null) {
          info.setKnowledgeClass(KlabAsset.KnowledgeClass.WORKSPACE);
        }
        if (info.getServiceId() == null || info.getServiceId().isBlank()) {
          info.setServiceId(service.serviceId());
        }
        if (valid.putIfAbsent(info.getUrn(), info) != null) {
          discarded++;
        }
      }
      return new WorkspaceResult(
          service, List.copyOf(valid.values()), null, false, discarded, false);
    } catch (Throwable failure) {
      return new WorkspaceResult(service, List.of(), failure, false, 0, false);
    }
  }

  private CapabilityResult loadCapabilities(ResourcesService service, UserScope scope) {
    try {
      var capabilities = service.capabilities(scope);
      if (capabilities == null) {
        throw new IllegalStateException("Service returned null capabilities");
      }
      Set<CRUDOperation> permissions = capabilities.getPermissions();
      return new CapabilityResult(
          service,
          permissions != null && permissions.contains(CRUDOperation.CREATE),
          capabilities.getServiceName(),
          null,
          false,
          false);
    } catch (Throwable failure) {
      return new CapabilityResult(service, false, null, failure, false, false);
    }
  }

  private <T> void deliverWithTimeout(
      CompletableFuture<T> request,
      Duration timeout,
      Supplier<T> timeoutResult,
      Consumer<T> consumer,
      java.util.function.Predicate<T> successful,
      java.util.function.UnaryOperator<T> markLate) {
    var delivered = new AtomicBoolean(false);
    var timer =
        timeoutExecutor.schedule(
            () -> {
              if (delivered.compareAndSet(false, true)) {
                consumer.accept(timeoutResult.get());
              }
            },
            timeout.toMillis(),
            TimeUnit.MILLISECONDS);
    request.whenComplete(
        (result, error) -> {
          if (delivered.compareAndSet(false, true)) {
            timer.cancel(false);
            consumer.accept(result);
          } else if (result != null && successful.test(result)) {
            // A slow but valid response is useful. Publish it without counting it as another
            // completion of the refresh cycle.
            consumer.accept(markLate.apply(result));
          }
        });
  }
}
