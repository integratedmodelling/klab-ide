package org.integratedmodelling.klab.ide.components;

import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;
import org.integratedmodelling.klab.api.Klab;
import org.integratedmodelling.klab.api.knowledge.KlabAsset;
import org.integratedmodelling.klab.api.knowledge.Resource;
import org.integratedmodelling.klab.api.services.Resolver;
import org.integratedmodelling.klab.api.services.ResourcesService;
import org.integratedmodelling.klab.api.services.resources.ResourceInfo;
import org.integratedmodelling.klab.ide.KlabIDEController;
import org.integratedmodelling.klab.ide.Theme;
import org.integratedmodelling.klab.ide.pages.BrowsablePage;
import org.kordamp.ikonli.javafx.FontIcon;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

public class ResourcesView extends BrowsablePage<ResourceEditor, Resource> {

  private final Map<String, ResourceEditor> openEditors = new HashMap<>();

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

  // List to store components
  private final List<Node> components = new ArrayList<>();

  @Override
  protected void defineBrowser(VBox vBox) {
    // Create a search box
    TextField searchBox = new TextField();
    searchBox.setPromptText("Search resources...");
    searchBox.setPrefWidth(BrowsablePage.BROWSER_WIDTH - 20);

    // Create a VBox to hold search results
    VBox resultsBox = new VBox(10);
    resultsBox.setPadding(new Insets(10, 0, 0, 0));

    // Set up search functionality with debouncing
    AtomicReference<Task<List<ResourceInfo>>> currentTask = new AtomicReference<>();

    List<Resource> resolverResources = new ArrayList<>();
    if (KlabIDEController.instance().getFocalScope() != null) {
      resolverResources.addAll(
          KlabIDEController.instance()
              .getFocalScope()
              .getService(Resolver.class)
              .getSubmittedResources(KlabIDEController.instance().getFocalScope()));
    }

    for (var resource : resolverResources) {
      resultsBox
          .getChildren()
          .add(
              new Components.Resource(
                  makeResourceInfo(resource), this::viewResource, null /* TODO */));
    }

    searchBox
        .textProperty()
        .addListener(
            (observable, oldValue, newValue) -> {
              // Cancel previous task if it's still running
              if (currentTask.get() != null && currentTask.get().isRunning()) {
                currentTask.get().cancel();
              }

              // Create a new task for the search
              Task<List<ResourceInfo>> searchTask =
                  new Task<>() {
                    @Override
                    protected List<ResourceInfo> call() throws Exception {
                      List<ResourceInfo> results = new ArrayList<>();

                      // Get all ResourceServices,
                      var services =
                          KlabIDEController.instance().user().getServices(ResourcesService.class);

                      // Query each service for resources matching the search term
                      for (ResourcesService service : services) {
                        if (isCancelled()) {
                          break;
                        }
                        results.addAll(
                            service.queryResources(
                                newValue,
                                KlabIDEController.instance().user(),
                                KlabAsset.KnowledgeClass.RESOURCE));
                      }

                      return results;
                    }
                  };

              // Update the UI when the search is complete
              searchTask.setOnSucceeded(
                  event -> {
                    List<ResourceInfo> results = searchTask.getValue();
                    Platform.runLater(
                        () -> {
                          resultsBox.getChildren().clear();
                          for (var resource : resolverResources) {
                            resultsBox
                                .getChildren()
                                .add(
                                    new Components.Resource(
                                        makeResourceInfo(resource),
                                        this::viewResource,
                                        null /* TODO */));
                          }
                          if (results.isEmpty()) {
                            Label noResults = new Label("No resources found");
                            noResults.setStyle("-fx-text-fill: -color-fg-subtle;");
                            resultsBox.getChildren().add(noResults);
                          } else {
                            // Add resource cards to the results box
                            for (ResourceInfo resource : results) {
                              resultsBox
                                  .getChildren()
                                  .add(
                                      new Components.Resource(
                                          resource, this::viewResource, null /* TODO */));
                            }
                          }
                        });
                  });

              // Handle errors
              searchTask.setOnFailed(
                  event -> {
                    Platform.runLater(
                        () -> {
                          resultsBox.getChildren().clear();
                          Label error = new Label("Error searching resources");
                          error.setStyle("-fx-text-fill: -color-danger;");
                          resultsBox.getChildren().add(error);
                        });
                  });

              // Store the current task and start it
              currentTask.set(searchTask);
              new Thread(searchTask).start();
            });

    // Clear existing components and add new ones
    Platform.runLater(
        () -> {
          vBox.getChildren().clear();
          vBox.getChildren().addAll(searchBox, resultsBox);
        });
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
    return ret;
  }

  private void viewResource(ResourceInfo resourceInfo) {

    hideBrowser();
    if (openEditors.containsKey(resourceInfo.getUrn())) {
      openEditors
          .get(resourceInfo.getUrn())
          .requestFocus(); // FIXME must remember the tabs and select(tab) - in both cases
    } else {
      var service =
          KlabIDEController.instance()
              .user()
              .findService(
                  ResourcesService.class, s -> resourceInfo.getServiceId().equals(s.serviceId()))
              .orElse(null);

      // TODO handle the unlikely case that the service is unavailable. That will throw an exception
      //  from getService

      var resource =
          service.retrieveResource(
              List.of(resourceInfo.getUrn()), KlabIDEController.instance().user());

      var newEditor = new ResourceEditor(resource /*, resourceInfo, this*/);
      openEditors.put(resourceInfo.getUrn(), newEditor);
      addEditor(newEditor, resourceInfo.getUrn(), new FontIcon(Theme.WORKSPACE_ICON));
      newEditor.edit(resource);
    }
  }
}
