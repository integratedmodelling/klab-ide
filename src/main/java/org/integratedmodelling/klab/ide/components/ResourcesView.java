package org.integratedmodelling.klab.ide.components;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.scene.Parent;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;
import javafx.util.Duration;
import org.integratedmodelling.klab.api.collections.Parameters;
import org.integratedmodelling.klab.api.knowledge.KlabAsset;
import org.integratedmodelling.klab.api.knowledge.Resource;
import org.integratedmodelling.klab.api.services.Resolver;
import org.integratedmodelling.klab.api.services.ResourcesService;
import org.integratedmodelling.klab.api.services.resources.ResourceInfo;
import org.integratedmodelling.klab.api.services.runtime.Notification;
import org.integratedmodelling.klab.ide.KlabIDEController;
import org.integratedmodelling.klab.ide.Theme;
import org.integratedmodelling.klab.ide.components.cards.ResourceSmallViewComponent;
import org.integratedmodelling.klab.ide.pages.BrowsablePage;
import org.kordamp.ikonli.javafx.FontIcon;

public class ResourcesView extends BrowsablePage<ResourceEditor, Resource> {

  private static final int SEARCH_RESULT_LIMIT = 30;
  private static final Duration SEARCH_DEBOUNCE = Duration.millis(250);

  private final Map<String, ResourceEditor> openEditors = new HashMap<>();

  public ResourcesView() {
    super(
        "Choose or create a k.LAB resource using the top-left menu",
        "Resources wrap external or internal datasets and models. They are hosted by the connected Resources services");
  }

  @Override
  public String getName() {
    return "Resources";
  }

  @Override
  public Parent getView() {
    return this;
  }

  @Override
  public void reset() {}

  @Override
  protected void assetEditorSelected(ResourceEditor asset) {}

  @Override
  protected void assetEditorClosed(ResourceEditor asset) {}

  @Override
  protected void defineBrowser(VBox vBox) {
    // Create a search box
    TextField searchBox = new TextField();
    searchBox.setPromptText("Search resources...");
    searchBox.setPrefWidth(BrowsablePage.BROWSER_WIDTH - 20);

    // Create a VBox to hold search results
    VBox resultsBox = new VBox(10);
    resultsBox.setPadding(new Insets(10, 0, 0, 0));

    // Debounce keyboard input and keep only the most recent in-flight request.
    AtomicReference<Task<List<ResourceInfo>>> currentTask = new AtomicReference<>();
    PauseTransition debounce = new PauseTransition(SEARCH_DEBOUNCE);

    List<Resource> resolverResources = new ArrayList<>();
    if (KlabIDEController.instance().getFocalScope() != null) {
      resolverResources.addAll(
          KlabIDEController.instance()
              .getFocalScope()
              .getService(Resolver.class)
              .getSubmittedResources(KlabIDEController.instance().getFocalScope()));
    }

    showResults(resultsBox, resolverResources, List.of(), "");

    debounce.setOnFinished(
        event -> startResourceSearch(searchBox.getText(), resultsBox, resolverResources, currentTask));

    searchBox
        .textProperty()
        .addListener(
            (observable, oldValue, newValue) -> {
              var runningTask = currentTask.getAndSet(null);
              if (runningTask != null) {
                runningTask.cancel();
              }
              debounce.stop();
              if (newValue == null || newValue.isBlank()) {
                showResults(resultsBox, resolverResources, List.of(), "");
              } else {
                debounce.playFromStart();
              }
            });

    // Clear existing components and add new ones
    Platform.runLater(
        () -> {
          vBox.getChildren().clear();
          vBox.getChildren().addAll(searchBox, resultsBox);
        });
  }

  private void startResourceSearch(
      String text,
      VBox resultsBox,
      List<Resource> resolverResources,
      AtomicReference<Task<List<ResourceInfo>>> currentTask) {
    String queryText = text == null ? "" : text.strip();
    if (queryText.isEmpty()) {
      showResults(resultsBox, resolverResources, List.of(), "");
      return;
    }

    Task<List<ResourceInfo>> searchTask =
        new Task<>() {
          @Override
          protected List<ResourceInfo> call() {
            var results = new LinkedHashMap<String, ResourceInfo>();
            var scope = KlabIDEController.instance().user();
            int successfulServices = 0;
            RuntimeException lastFailure = null;
            for (ResourcesService service : scope.getServices(ResourcesService.class)) {
              if (isCancelled()) {
                return List.of();
              }
              try {
                var serviceResults =
                    service.query(
                        Parameters.create("query", queryText, "limit", SEARCH_RESULT_LIMIT),
                        KlabAsset.KnowledgeClass.RESOURCE,
                        ResourceInfo.class,
                        scope);
                successfulServices++;
                for (var resource : serviceResults) {
                  if (resource != null && resource.getUrn() != null) {
                    // A resource can be advertised by more than one service; retain each host.
                    results.put(resource.getServiceId() + "\n" + resource.getUrn(), resource);
                  }
                }
              } catch (RuntimeException failure) {
                lastFailure = failure;
              }
            }
            if (successfulServices == 0 && lastFailure != null) {
              throw lastFailure;
            }
            return results.values().stream()
                .sorted(
                    (first, second) ->
                        Float.compare(searchScore(second), searchScore(first)))
                .toList();
          }
        };

    searchTask.setOnSucceeded(
        event -> {
          if (currentTask.compareAndSet(searchTask, null) && !searchTask.isCancelled()) {
            showResults(resultsBox, resolverResources, searchTask.getValue(), queryText);
          }
        });
    searchTask.setOnFailed(
        event -> {
          if (currentTask.compareAndSet(searchTask, null)) {
            showSearchError(resultsBox, resolverResources, queryText);
          }
        });

    currentTask.set(searchTask);
    Thread thread = new Thread(searchTask, "resource-catalog-search");
    thread.setDaemon(true);
    thread.start();
  }

  private void showResults(
      VBox resultsBox,
      List<Resource> resolverResources,
      List<ResourceInfo> serviceResults,
      String queryText) {
    resultsBox.getChildren().clear();
    var displayed = new LinkedHashMap<String, ResourceInfo>();
    for (var resource : resolverResources) {
      if (matches(resource, queryText)) {
        var info = makeResourceInfo(resource);
        displayed.put(info.getServiceId() + "\n" + info.getUrn(), info);
      }
    }
    for (var info : serviceResults) {
      displayed.put(info.getServiceId() + "\n" + info.getUrn(), info);
    }
    if (displayed.isEmpty() && !queryText.isBlank()) {
      Label noResults = new Label("No resources found");
      noResults.setStyle("-fx-text-fill: -color-fg-subtle;");
      resultsBox.getChildren().add(noResults);
      return;
    }
    for (var resource : displayed.values()) {
      resultsBox
          .getChildren()
          .add(new ResourceSmallViewComponent(resource, this::viewResource, null /* TODO */));
    }
  }

  private static boolean matches(Resource resource, String queryText) {
    if (queryText == null || queryText.isBlank()) {
      return true;
    }
    String query = queryText.toLowerCase(java.util.Locale.ROOT);
    if (contains(resource.getUrn(), query)
        || contains(resource.getLocalName(), query)
        || contains(resource.getAdapterType(), query)) {
      return true;
    }
    return resource.getMetadata() != null
        && resource.getMetadata().values().stream().anyMatch(value -> contains(value, query));
  }

  private static boolean contains(Object value, String lowerCaseQuery) {
    return value != null
        && value.toString().toLowerCase(java.util.Locale.ROOT).contains(lowerCaseQuery);
  }

  private static float searchScore(ResourceInfo info) {
    if (info == null || info.getMetadata() == null) {
      return 0;
    }
    Object score =
        info.getMetadata().get(org.integratedmodelling.klab.api.data.Metadata.IM_SEARCH_SCORE);
    if (score instanceof Number number) {
      return number.floatValue();
    }
    try {
      return score == null ? 0 : Float.parseFloat(score.toString());
    } catch (NumberFormatException ignored) {
      return 0;
    }
  }

  private void showSearchError(
      VBox resultsBox, List<Resource> resolverResources, String queryText) {
    showResults(resultsBox, resolverResources, List.of(), queryText);
    resultsBox
        .getChildren()
        .removeIf(
            node -> node instanceof Label label && "No resources found".equals(label.getText()));
    Label error = new Label("Resource services could not be searched");
    error.setStyle("-fx-text-fill: -color-danger;");
    resultsBox.getChildren().addFirst(error);
  }

  /**
   * This is only used for the resolver-hosted, scope-specific resources submitted.
   *
   * @param resource
   * @return
   */
  private ResourceInfo makeResourceInfo(Resource resource) {
    var ret = new ResourceInfo();
    ret.setUrn(resource.getUrn());
    ret.setServiceId(resource.getServiceId());
    ret.setKnowledgeClass(KlabAsset.KnowledgeClass.RESOURCE);
    if (resource.getMetadata() != null) {
      ret.getMetadata().putAll(resource.getMetadata());
    }
    ret.getMetadata().put("im:adapter", resource.getAdapterType());
    ret.getMetadata().put("im:version", resource.getVersion());
    return ret;
  }

  private void viewResource(ResourceInfo resourceInfo) {

    hideBrowser();
    if (openEditors.containsKey(resourceInfo.getUrn())) {
      openEditors
          .get(resourceInfo.getUrn())
          .requestFocus(); // FIXME must remember the tabs and select(tab) - in both cases
    } else {
      Resource resource = null;
      var service =
          KlabIDEController.instance()
              .user()
              .findService(
                  ResourcesService.class, s -> resourceInfo.getServiceId().equals(s.serviceId()))
              .orElse(null);

      if (service != null) {
        resource =
            service.retrieve(
                resourceInfo.getUrn(), Resource.class, KlabIDEController.instance().user());
      } else if (KlabIDEController.instance().getFocalScope() != null) {

        var resolver =
            KlabIDEController.instance()
                .user()
                .findService(Resolver.class, s -> resourceInfo.getServiceId().equals(s.serviceId()))
                .orElse(null);

        if (resolver != null) {
          resource =
              resolver.getSubmittedResources(KlabIDEController.instance().getFocalScope()).stream()
                  .filter(r -> r.getUrn().equals(resourceInfo.getUrn()))
                  .findFirst()
                  .orElse(null);
        }
      }

      if (resource == null) {
        KlabIDEController.instance()
            .handleNotification(Notification.error("Could not find resource"));
        return;
      }

      var newEditor = new ResourceEditor(resource /*, resourceInfo, this*/);
      openEditors.put(resourceInfo.getUrn(), newEditor);
      addEditor(newEditor, resourceInfo.getUrn(), new FontIcon(Theme.WORKSPACE_ICON));
      newEditor.edit(resource);
    }
  }
}
