package org.integratedmodelling.klab.ide.components;

import atlantafx.base.theme.Styles;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.geometry.HPos;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.util.Duration;
import org.integratedmodelling.klab.api.authentication.CRUDOperation;
import org.integratedmodelling.klab.api.authentication.ResourcePrivileges;
import org.integratedmodelling.klab.api.collections.Parameters;
import org.integratedmodelling.klab.api.configuration.Configuration;
import org.integratedmodelling.klab.api.data.Version;
import org.integratedmodelling.klab.api.geometry.Geometry;
import org.integratedmodelling.klab.api.knowledge.Artifact;
import org.integratedmodelling.klab.api.knowledge.KlabAsset;
import org.integratedmodelling.klab.api.knowledge.Resource;
import org.integratedmodelling.klab.api.services.Resolver;
import org.integratedmodelling.klab.api.services.ResourcesService;
import org.integratedmodelling.klab.api.services.resources.ResourceInfo;
import org.integratedmodelling.klab.api.services.resources.impl.ResourceImpl;
import org.integratedmodelling.klab.api.services.runtime.Notification;
import org.integratedmodelling.klab.api.services.runtime.extension.AdapterDescriptor;
import org.integratedmodelling.klab.ide.KlabIDEController;
import org.integratedmodelling.klab.ide.Theme;
import org.integratedmodelling.klab.ide.components.cards.ResourceSmallViewComponent;
import org.integratedmodelling.klab.ide.components.generic.UploadBox;
import org.integratedmodelling.klab.ide.pages.BrowsablePage;
import org.kordamp.ikonli.javafx.FontIcon;

public class ResourcesView extends BrowsablePage<ResourceEditor, Resource> {

  private static final int SEARCH_RESULT_LIMIT = 30;
  private static final Duration SEARCH_DEBOUNCE = Duration.millis(250);

  private final Map<String, ResourceEditor> openEditors = new HashMap<>();
  private Node resourceDialog;
  private WorkflowUIProvider workflowUIProvider = WorkflowUIProvider.NONE;

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
  protected void assetEditorClosed(ResourceEditor asset) {
    openEditors.values().removeIf(editor -> editor == asset);
    asset.close();
  }

  public void setWorkflowUIProvider(WorkflowUIProvider provider) {
    workflowUIProvider = provider == null ? WorkflowUIProvider.NONE : provider;
    openEditors.values().forEach(editor -> editor.setWorkflowUIProvider(workflowUIProvider));
  }

  @Override
  protected void defineBrowser(VBox vBox) {
    // Create a search box

    TextField searchBox = new TextField();
    searchBox.setPromptText("Search resources...");
    searchBox.setPrefWidth(BrowsablePage.BROWSER_WIDTH - 20);
    CheckBox includePublished = new CheckBox("Show published local resources");
    includePublished.setWrapText(true);

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
        event ->
            startResourceSearch(
                searchBox.getText(),
                includePublished.isSelected(),
                resultsBox,
                resolverResources,
                currentTask));

    includePublished
        .selectedProperty()
        .addListener(
            (observable, oldValue, newValue) -> {
              if (searchBox.getText() != null && !searchBox.getText().isBlank()) {
                debounce.playFromStart();
              }
            });

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
          vBox.getChildren().add(makeHeader("Resources", this::addResource));
          if (resourceDialog != null) {
            vBox.getChildren().add(resourceDialog);
          }
          vBox.getChildren().addAll(searchBox, includePublished, resultsBox);
        });
  }

  private void addResource() {
    resourceDialog = createResourceDialog();
    updateBrowser();
  }

  private Node createResourceDialog() {
    var availableServices = KlabIDEController.instance().user().getServices(ResourcesService.class);
    var dialog = new VBox(8);
    dialog.setPadding(new Insets(6));
    dialog.setStyle("-fx-background-color: -color-neutral-muted;");

    var serviceSelector = new ComboBox<ResourcesService>();
    serviceSelector.getItems().addAll(availableServices);
    serviceSelector.setMaxWidth(Double.MAX_VALUE);
    configureServiceCells(serviceSelector);

    var adapterSelector = new ComboBox<AdapterDescriptor>();
    adapterSelector.setMaxWidth(Double.MAX_VALUE);
    adapterSelector.setCellFactory(ignored -> adapterCell());
    adapterSelector.setButtonCell(adapterCell());

    var originator = creationField("originator", "Originator");
    var namespace = creationField("catalog", "Namespace");
    var resourceId = creationField("resource-id", "Resource ID");
    var urnPreview = new Label();
    urnPreview.setWrapText(true);
    urnPreview.getStyleClass().add(Styles.TEXT_SMALL);

    AtomicReference<Runnable> dialogUpdater = new AtomicReference<>(() -> {});
    var uploadBox =
        new UploadBox(
            Configuration.INSTANCE.getTemporaryDataPath().toString(),
            "Drop the primary dataset/file or a URL",
            file -> Platform.runLater(dialogUpdater.get()),
            (message, throwable) ->
                KlabIDEController.instance()
                    .handleNotification(Notification.error("Resource upload failed: " + message)));
    uploadBox.setPrefWidth(270);
    uploadBox.setMaxWidth(270);
    uploadBox.setMinWidth(0);

    var selection = new GridPane();
    selection.setHgap(6);
    selection.setVgap(6);
    selection.add(new Label("Service *"), 0, 0);
    selection.add(serviceSelector, 1, 0);
    selection.add(new Label("Adapter *"), 0, 1);
    selection.add(adapterSelector, 1, 1);
    selection.add(new Label("Originator *"), 0, 2);
    selection.add(originator, 1, 2);
    selection.add(new Label("Namespace *"), 0, 3);
    selection.add(namespace, 1, 3);
    selection.add(new Label("Resource ID *"), 0, 4);
    selection.add(resourceId, 1, 4);
    selection.add(new Label("URN"), 0, 5);
    selection.add(urnPreview, 1, 5);
    selection.getColumnConstraints().add(new ColumnConstraints());
    selection
        .getColumnConstraints()
        .add(new ColumnConstraints(170, 200, Double.MAX_VALUE, Priority.ALWAYS, HPos.LEFT, true));

    var uploadHint = new Label();
    uploadHint.setWrapText(true);
    uploadHint.getStyleClass().add(Styles.TEXT_MUTED);

    var accept = new Button("Continue to editor");
    var cancel = new Button("Cancel");
    accept.getStyleClass().addAll(Styles.BUTTON_OUTLINED, Styles.SUCCESS, Styles.SMALL);
    cancel.getStyleClass().addAll(Styles.BUTTON_OUTLINED, Styles.DANGER, Styles.SMALL);
    var buttons = new HBox(accept, cancel);
    buttons.setAlignment(Pos.CENTER_RIGHT);
    buttons.setSpacing(4);

    Runnable update =
        () -> {
          var service = serviceSelector.getValue();
          var adapter = adapterSelector.getValue();
          String urn =
              service == null
                  ? ""
                  : service.serviceId()
                      + ":"
                      + originator.getText().strip().toLowerCase(java.util.Locale.ROOT)
                      + ":"
                      + namespace.getText().strip().toLowerCase(java.util.Locale.ROOT)
                      + ":"
                      + resourceId.getText().strip().toLowerCase(java.util.Locale.ROOT);
          urnPreview.setText(urn);
          boolean requiresUpload = adapterRequiresUpload(adapter);
          uploadHint.setText(
              requiresUpload
                  ? "This adapter imports data: add its primary file before continuing."
                  : "This adapter can be parameterized without a file. Upload is optional and can also be used for documentation or ancillary data.");
          accept.setDisable(
              service == null
                  || adapter == null
                  || !validUrnPart(originator.getText())
                  || !validUrnPart(namespace.getText())
                  || !validUrnPart(resourceId.getText())
                  || (requiresUpload && uploadBox.getUploadedFiles().isEmpty()));
        };
    dialogUpdater.set(update);

    serviceSelector
        .valueProperty()
        .addListener(
            (observable, oldService, newService) -> {
              adapterSelector.getItems().setAll(adapters(newService));
              adapterSelector.getSelectionModel().selectFirst();
              update.run();
            });
    adapterSelector.valueProperty().addListener((observable, oldValue, newValue) -> update.run());
    originator.textProperty().addListener((observable, oldValue, newValue) -> update.run());
    namespace.textProperty().addListener((observable, oldValue, newValue) -> update.run());
    resourceId.textProperty().addListener((observable, oldValue, newValue) -> update.run());

    accept.setOnAction(
        event -> {
          var service = serviceSelector.getValue();
          var adapter = adapterSelector.getValue();
          var resource = new ResourceImpl();
          resource.setUrn(urnPreview.getText());
          resource.setServiceId(service.serviceId());
          resource.setAdapterType(adapter.getName());
          resource.setVersion(Version.EMPTY_VERSION);
          resource.setTimestamp(System.currentTimeMillis());
          resource.setType(Artifact.Type.NUMBER);
          resource.setGeometry(Geometry.UNIVERSAL);
          resource.setLocalFiles(new ArrayList<>(uploadBox.getUploadedFiles()));
          var info = new ResourceInfo();
          info.setUrn(resource.getUrn());
          info.setServiceId(service.serviceId());
          info.setKnowledgeClass(KlabAsset.KnowledgeClass.RESOURCE);
          info.setRights(ResourcePrivileges.create(KlabIDEController.instance().user()));
          info.getPermissions()
              .addAll(
                  List.of(
                      CRUDOperation.READ,
                      CRUDOperation.CREATE,
                      CRUDOperation.UPDATE,
                      CRUDOperation.UPDATE_METADATA,
                      CRUDOperation.DELETE));
          uploadBox.dispose();
          resourceDialog = null;
          updateBrowser();
          hideBrowser();
          openResourceEditor(service, resource, info, true);
        });
    cancel.setOnAction(
        event -> {
          uploadBox.dispose();
          resourceDialog = null;
          updateBrowser();
        });

    if (availableServices.isEmpty()) {
      serviceSelector.setDisable(true);
      accept.setDisable(true);
    } else {
      serviceSelector.getSelectionModel().selectFirst();
    }
    update.run();
    dialog.getChildren().addAll(selection, uploadHint, uploadBox, buttons);
    return dialog;
  }

  private static TextField creationField(String prompt, String accessibleText) {
    var ret = new TextField();
    ret.setPromptText(prompt);
    ret.setAccessibleText(accessibleText);
    ret.setMaxWidth(Double.MAX_VALUE);
    return ret;
  }

  private static boolean validUrnPart(String value) {
    return value != null
        && value.strip().toLowerCase(java.util.Locale.ROOT).matches("[a-z0-9][a-z0-9._-]*");
  }

  private static boolean adapterRequiresUpload(AdapterDescriptor adapter) {
    return adapter != null
        && adapter.getImportSchemata() != null
        && !adapter.getImportSchemata().isEmpty();
  }

  private static List<AdapterDescriptor> adapters(ResourcesService service) {
    if (service == null) return List.of();
    try {
      return service.capabilities(KlabIDEController.instance().user()).getComponents().stream()
          .flatMap(component -> component.adapters().stream())
          .filter(
              adapter ->
                  adapter.getServiceId() == null
                      || service.serviceId().equals(adapter.getServiceId()))
          .sorted(java.util.Comparator.comparing(AdapterDescriptor::getName))
          .toList();
    } catch (Throwable error) {
      return List.of();
    }
  }

  private static javafx.scene.control.ListCell<AdapterDescriptor> adapterCell() {
    return new javafx.scene.control.ListCell<>() {
      @Override
      protected void updateItem(AdapterDescriptor adapter, boolean empty) {
        super.updateItem(adapter, empty);
        setText(empty || adapter == null ? null : adapter.getName());
      }
    };
  }

  private static void configureServiceCells(ComboBox<ResourcesService> selector) {
    selector.setCellFactory(
        ignored ->
            new javafx.scene.control.ListCell<>() {
              @Override
              protected void updateItem(ResourcesService service, boolean empty) {
                super.updateItem(service, empty);
                setText(empty || service == null ? null : service.serviceName());
              }
            });
    selector.setButtonCell(
        new javafx.scene.control.ListCell<>() {
          @Override
          protected void updateItem(ResourcesService service, boolean empty) {
            super.updateItem(service, empty);
            setText(empty || service == null ? null : service.serviceName());
          }
        });
  }

  private void startResourceSearch(
      String text,
      boolean includePublishedLocal,
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
                        Parameters.create(
                            "query",
                            queryText,
                            "limit",
                            SEARCH_RESULT_LIMIT,
                            "includePublishedLocal",
                            includePublishedLocal),
                        KlabAsset.KnowledgeClass.RESOURCE,
                        ResourceInfo.class,
                        scope);
                successfulServices++;
                for (var resource : serviceResults) {
                  if (resource != null && resource.getUrn() != null) {
                    mergeResourceInfo(results, resource);
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
                .sorted((first, second) -> Float.compare(searchScore(second), searchScore(first)))
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
        mergeResourceInfo(displayed, info);
      }
    }
    for (var info : serviceResults) {
      mergeResourceInfo(displayed, info);
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

  static void mergeResourceInfo(Map<String, ResourceInfo> resources, ResourceInfo candidate) {
    if (candidate == null || candidate.getUrn() == null) return;
    resources.merge(
        candidate.getUrn(),
        candidate,
        (current, replacement) ->
            resourceVersion(replacement).compareTo(resourceVersion(current)) >= 0
                ? replacement
                : current);
  }

  private static Version resourceVersion(ResourceInfo info) {
    Object version = info.getMetadata() == null ? null : info.getMetadata().get("im:version");
    try {
      return version instanceof Version value
          ? value
          : version == null ? Version.EMPTY_VERSION : Version.create(version.toString());
    } catch (RuntimeException ignored) {
      return Version.EMPTY_VERSION;
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
    ResourceInfo requestedInfo = resourceInfo;
    String editorKey = resourceKey(resourceInfo.getServiceId(), resourceInfo.getUrn());
    if (openEditors.containsKey(editorKey)) {
      selectEditor(openEditors.get(editorKey));
    } else {
      Resource resource = null;
      var service =
          KlabIDEController.instance()
              .user()
              .findService(
                  ResourcesService.class, s -> requestedInfo.getServiceId().equals(s.serviceId()))
              .orElse(null);

      if (service != null) {
        try {
          var detailedInfo =
              service.info(
                  resourceInfo.getUrn(),
                  KlabAsset.KnowledgeClass.RESOURCE,
                  ResourceInfo.class,
                  KlabIDEController.instance().user());
          if (detailedInfo != null) {
            resourceInfo = detailedInfo;
          }
        } catch (Throwable ignored) {
          // Search results already contain enough information for a read-only inspector.
        }
        resource =
            service.retrieve(
                resourceInfo.getUrn(), Resource.class, KlabIDEController.instance().user());
      } else if (KlabIDEController.instance().getFocalScope() != null) {

        var resolver =
            KlabIDEController.instance()
                .user()
                .findService(
                    Resolver.class, s -> requestedInfo.getServiceId().equals(s.serviceId()))
                .orElse(null);

        if (resolver != null) {
          resource =
              resolver.getSubmittedResources(KlabIDEController.instance().getFocalScope()).stream()
                  .filter(r -> r.getUrn().equals(requestedInfo.getUrn()))
                  .findFirst()
                  .orElse(null);
        }
      }

      if (resource == null) {
        KlabIDEController.instance()
            .handleNotification(Notification.error("Could not find resource"));
        return;
      }

      openResourceEditor(service, resource, resourceInfo, false);
    }
  }

  private void openResourceEditor(
      ResourcesService service, Resource resource, ResourceInfo resourceInfo, boolean draft) {
    String key = resourceKey(resourceInfo.getServiceId(), resource.getUrn());
    if (openEditors.containsKey(key)) {
      selectEditor(openEditors.get(key));
      return;
    }
    var editor = new ResourceEditor(service, resource, resourceInfo, draft, workflowUIProvider);
    openEditors.put(key, editor);
    editor.setOnSaved(
        stored -> {
          openEditors.remove(key, editor);
          openEditors.put(resourceKey(stored.getServiceId(), stored.getUrn()), editor);
          updateBrowser();
        });
    editor.setOnDeleted(
        () -> {
          openEditors.remove(key, editor);
          removeEditor(editor);
          updateBrowser();
        });
    addEditor(
        editor,
        resource.getUrn() == null ? "New resource" : resource.toString(),
        new FontIcon(Theme.RESOURCE_ICON));
    Platform.runLater(editor::open);
  }

  private static String resourceKey(String serviceId, String urn) {
    return String.valueOf(serviceId) + "\n" + String.valueOf(urn);
  }
}
