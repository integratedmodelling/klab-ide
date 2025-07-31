package org.integratedmodelling.klab.ide.components;

import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;
import org.integratedmodelling.klab.api.knowledge.KlabAsset;
import org.integratedmodelling.klab.api.knowledge.Resource;
import org.integratedmodelling.klab.api.services.ResourcesService;
import org.integratedmodelling.klab.api.services.resources.ResourceInfo;
import org.integratedmodelling.klab.ide.KlabIDEController;
import org.integratedmodelling.klab.ide.pages.BrowsablePage;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

public class ResourcesView extends BrowsablePage<ResourceEditor, Resource> {

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
  protected void assetEditorSelected(Resource asset) {}

  @Override
  protected void assetEditorClosed(Resource asset) {}

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
                          KlabIDEController.modeler().user().getServices(ResourcesService.class);

                      // Query each service for resources matching the search term
                      for (ResourcesService service : services) {
                        if (isCancelled()) {
                          break;
                        }
                        results.addAll(
                            service.queryResources(
                                newValue,
                                KlabIDEController.modeler().user(),
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

                          if (results.isEmpty()) {
                            Label noResults = new Label("No resources found");
                            noResults.setStyle("-fx-text-fill: -color-fg-subtle;");
                            resultsBox.getChildren().add(noResults);
                          } else {
                            // Add resource cards to the results box
                            for (ResourceInfo resource : results) {
                              resultsBox
                                  .getChildren()
                                  .add(new Components.Resource(resource, this::viewResource));
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

  private void viewResource(ResourceInfo resource) {
    // Handle resource selection
    System.out.println("Selected resource: " + resource.getUrn());
  }
}
