package org.integratedmodelling.klab.ide.components;

import atlantafx.base.theme.Styles;
import java.io.File;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Future;
import java.util.function.Consumer;
import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.control.cell.CheckBoxTableCell;
import javafx.scene.control.cell.ComboBoxTableCell;
import javafx.scene.control.cell.TextFieldTableCell;
import javafx.scene.layout.*;
import org.integratedmodelling.klab.api.authentication.CRUDOperation;
import org.integratedmodelling.klab.api.authentication.ResourcePrivileges;
import org.integratedmodelling.klab.api.collections.Parameters;
import org.integratedmodelling.klab.api.configuration.Configuration;
import org.integratedmodelling.klab.api.data.Metadata;
import org.integratedmodelling.klab.api.data.Version;
import org.integratedmodelling.klab.api.geometry.Geometry;
import org.integratedmodelling.klab.api.geometry.Geometry.Dimension;
import org.integratedmodelling.klab.api.geometry.impl.GeometryImpl;
import org.integratedmodelling.klab.api.knowledge.Artifact;
import org.integratedmodelling.klab.api.knowledge.KlabAsset;
import org.integratedmodelling.klab.api.knowledge.Resource;
import org.integratedmodelling.klab.api.scope.UserScope;
import org.integratedmodelling.klab.api.services.ResourcesService;
import org.integratedmodelling.klab.api.services.resources.ResourceInfo;
import org.integratedmodelling.klab.api.services.resources.adapters.Adapter;
import org.integratedmodelling.klab.api.services.resources.impl.AttributeImpl;
import org.integratedmodelling.klab.api.services.resources.impl.ResourceImpl;
import org.integratedmodelling.klab.api.services.resources.workflow.Flow;
import org.integratedmodelling.klab.api.services.resources.workflow.Workflow;
import org.integratedmodelling.klab.api.services.resources.workflow.WorkflowParticipant;
import org.integratedmodelling.klab.api.services.resources.workflow.WorkflowRole;
import org.integratedmodelling.klab.api.services.runtime.Notification;
import org.integratedmodelling.klab.api.services.runtime.extension.AdapterDescriptor;
import org.integratedmodelling.klab.ide.IDEContextScope;
import org.integratedmodelling.klab.ide.KlabIDEController;
import org.integratedmodelling.klab.ide.components.ResourceEditorValidator.Result;
import org.integratedmodelling.klab.ide.components.ResourceEditorValidator.Section;
import org.integratedmodelling.klab.ide.components.cards.GeometryCard;
import org.integratedmodelling.klab.ide.components.cards.MetadataCard;
import org.integratedmodelling.klab.ide.components.cards.PermissionEditor;
import org.integratedmodelling.klab.ide.components.generic.IconLabel;
import org.integratedmodelling.klab.ide.components.generic.UploadBox;
import org.integratedmodelling.klab.ide.pages.EditorPage;
import org.kordamp.ikonli.material2.Material2AL;

/** CRUD editor and inspection view for one first-class k.LAB resource. */
public class ResourceEditor extends EditorPage<Resource, Resource> {

  private final ResourcesService service;
  private final ResourceInfo resourceInfo;
  private boolean draft;
  private final ResourceImpl resource;
  private final Map<Resource, Section> markerSections = new IdentityHashMap<>();
  private final Map<Section, Resource> sectionMarkers = new EnumMap<>(Section.class);
  private final Map<Section, String> sectionLabels = new EnumMap<>(Section.class);
  private final List<AdapterDescriptor> adapters;
  private WorkflowUIProvider workflowUIProvider;
  private Consumer<Resource> onSaved = ignored -> {};
  private Runnable onDeleted = () -> {};
  private TreeView<Resource> index;
  private Button saveButton;
  private Button saveTemporaryButton;
  private Label validationLabel;
  private Result validation = new Result(List.of());
  private boolean busy;

  public ResourceEditor(Object asset) {
    this(
        null,
        asset instanceof Resource r ? r : emptyResource(),
        null,
        !(asset instanceof Resource),
        WorkflowUIProvider.NONE);
  }

  public ResourceEditor(
      ResourcesService service,
      Resource asset,
      ResourceInfo resourceInfo,
      boolean draft,
      WorkflowUIProvider workflowUIProvider) {
    super(asset == null ? emptyResource() : asset);
    this.service = service;
    this.resourceInfo = resourceInfo == null ? initialInfo(asset, service) : resourceInfo;
    this.draft = draft;
    this.workflowUIProvider =
        workflowUIProvider == null ? WorkflowUIProvider.NONE : workflowUIProvider;
    this.resource = copy(asset == null ? getEditedAsset() : asset);
    if (this.resourceInfo.getRights() != null
        && !this.resource.getMetadata().containsKey(ResourceEditorValidator.PERMISSIONS)) {
      this.resource
          .getMetadata()
          .put(ResourceEditorValidator.PERMISSIONS, this.resourceInfo.getRights().toString());
    }
    setEditedAsset(this.resource);
    this.adapters = discoverAdapters(service);
    initializeSections();
    validateResource();
  }

  public void open() {
    edit(resource);
  }

  public Resource getResource() {
    return resource;
  }

  public ResourceInfo getResourceInfo() {
    return resourceInfo;
  }

  public void setWorkflowUIProvider(WorkflowUIProvider provider) {
    workflowUIProvider = provider == null ? WorkflowUIProvider.NONE : provider;
  }

  public void setOnSaved(Consumer<Resource> callback) {
    onSaved = callback == null ? ignored -> {} : callback;
  }

  public void setOnDeleted(Runnable callback) {
    onDeleted = callback == null ? () -> {} : callback;
  }

  private static ResourceImpl emptyResource() {
    ResourceImpl ret = new ResourceImpl();
    ret.setGeometry(Geometry.UNIVERSAL);
    ret.setVersion(Version.EMPTY_VERSION);
    ret.setTimestamp(System.currentTimeMillis());
    ret.setType(Artifact.Type.NUMBER);
    return ret;
  }

  private static ResourceInfo initialInfo(Resource resource, ResourcesService service) {
    ResourceInfo info = new ResourceInfo();
    info.setKnowledgeClass(KlabAsset.KnowledgeClass.RESOURCE);
    info.setUrn(resource == null ? null : resource.getUrn());
    info.setServiceId(service == null ? null : service.serviceId());
    try {
      info.setRights(ResourcePrivileges.create(KlabIDEController.instance().user()));
    } catch (Throwable ignored) {
      info.setRights(ResourcePrivileges.empty());
    }
    return info;
  }

  private static ResourceImpl copy(Resource source) {
    ResourceImpl ret = emptyResource();
    if (source == null) return ret;
    ret.setUrn(source.getUrn());
    ret.setServiceId(source.getServiceId());
    ret.setAdapterType(source.getAdapterType());
    ret.setVersion(source.getVersion());
    ret.setTimestamp(source.getTimestamp());
    ret.setType(source.getType());
    ret.setGeometry(
        source.getGeometry() == null
            ? Geometry.UNIVERSAL
            : Geometry.create(source.getGeometry().encode()));
    ret.setLocalName(source.getLocalName());
    ret.setLocalProjectName(source.getLocalProjectName());
    ret.setMetadata(Metadata.create(source.getMetadata()));
    ret.setParameters(Parameters.create(source.getParameters()));
    ret.setLocalFiles(new ArrayList<>(safe(source.getLocalFiles())));
    ret.setHistory(new ArrayList<>(safe(source.getHistory())));
    ret.setNotifications(new ArrayList<>(safe(source.getNotifications())));
    ret.setAttributes(copyAttributes(source.getAttributes()));
    ret.setInputs(copyAttributes(source.getInputs()));
    ret.setOutputs(copyAttributes(source.getOutputs()));
    ret.setCategorizables(new ArrayList<>(safe(source.getCategorizables())));
    ret.setCodelists(new ArrayList<>(safe(source.getCodelists())));
    ret.setAnnotations(new ArrayList<>(safe(source.getAnnotations())));
    return ret;
  }

  private static <T> Collection<T> safe(Collection<T> values) {
    return values == null ? List.of() : values;
  }

  private static List<Resource.Attribute> copyAttributes(Collection<Resource.Attribute> source) {
    List<Resource.Attribute> ret = new ArrayList<>();
    for (Resource.Attribute value : safe(source)) {
      AttributeImpl copy = new AttributeImpl();
      copy.setName(value.getName());
      copy.setType(value.getType());
      copy.setKey(value.isKey());
      copy.setOptional(value.isOptional());
      copy.setIndex(value.getIndex());
      ret.add(copy);
    }
    return ret;
  }

  private static List<AdapterDescriptor> discoverAdapters(ResourcesService service) {
    if (service == null) return List.of();
    try {
      return service.capabilities(KlabIDEController.instance().user()).getComponents().stream()
          .filter(Objects::nonNull)
          .flatMap(component -> component.adapters().stream())
          .filter(Objects::nonNull)
          .filter(
              adapter ->
                  adapter.getServiceId() == null
                      || service.serviceId().equals(adapter.getServiceId()))
          .sorted(Comparator.comparing(AdapterDescriptor::getName))
          .toList();
    } catch (Throwable error) {
      return List.of();
    }
  }

  private void initializeSections() {
    sectionLabels.put(Section.OVERVIEW, "Resource");
    sectionLabels.put(Section.GEOMETRY, "Geometry and time");
    sectionLabels.put(Section.INTERFACE, "Data interface");
    sectionLabels.put(Section.PARAMETERS, "Adapter parameters");
    sectionLabels.put(Section.METADATA, "Metadata");
    sectionLabels.put(Section.LICENSE, "License and usage");
    sectionLabels.put(Section.PERMISSIONS, "Permissions");
    sectionLabels.put(Section.FILES, "Files and integrity");
    sectionLabels.put(Section.WORKFLOWS, "Workflows");
    sectionLabels.put(Section.HISTORY, "Version history");
    for (Section section : Section.values()) {
      if (section == Section.OVERVIEW) continue;
      ResourceImpl marker = new ResourceImpl();
      marker.setUrn(sectionLabels.get(section));
      markerSections.put(marker, section);
      sectionMarkers.put(section, marker);
    }
  }

  @Override
  protected void onVisualize(boolean visibleAfterCall) {
    KlabIDEController.instance().setFocalEditor(this, visibleAfterCall);
  }

  @Override
  protected TreeView<Resource> createContentTree() {
    TreeItem<Resource> root = new TreeItem<>(resource);
    root.setExpanded(true);
    root.getChildren().add(new TreeItem<>(sectionMarkers.get(Section.GEOMETRY)));
    TreeItem<Resource> contract = branch("Adapter contract");
    contract
        .getChildren()
        .addAll(
            new TreeItem<>(sectionMarkers.get(Section.INTERFACE)),
            new TreeItem<>(sectionMarkers.get(Section.PARAMETERS)));
    TreeItem<Resource> publication = branch("Publication");
    publication
        .getChildren()
        .addAll(
            new TreeItem<>(sectionMarkers.get(Section.METADATA)),
            new TreeItem<>(sectionMarkers.get(Section.LICENSE)),
            new TreeItem<>(sectionMarkers.get(Section.PERMISSIONS)));
    root.getChildren()
        .addAll(
            contract,
            publication,
            new TreeItem<>(sectionMarkers.get(Section.FILES)),
            new TreeItem<>(sectionMarkers.get(Section.WORKFLOWS)),
            new TreeItem<>(sectionMarkers.get(Section.HISTORY)));
    index = new TreeView<>(root);
    index.setShowRoot(true);
    index.setCellFactory(ignored -> new IndexCell());
    index.setContextMenu(createIndexMenu());
    return index;
  }

  private TreeItem<Resource> branch(String label) {
    ResourceImpl marker = new ResourceImpl();
    marker.setUrn(label);
    markerSections.put(marker, null);
    TreeItem<Resource> ret = new TreeItem<>(marker);
    ret.setExpanded(true);
    return ret;
  }

  private ContextMenu createIndexMenu() {
    ContextMenu menu = new ContextMenu();
    MenuItem addCodelist = new MenuItem("Add codelist");
    addCodelist.setDisable(!canEdit());
    addCodelist.setOnAction(event -> addCodelist());
    menu.getItems().add(addCodelist);
    Menu workflows = workflowMenu();
    if (!workflows.getItems().isEmpty()) menu.getItems().addAll(new SeparatorMenuItem(), workflows);
    return menu;
  }

  private void addCodelist() {
    TextField name = new TextField();
    name.setPromptText("codelist identifier");
    Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
    alert.setTitle("Add codelist");
    alert.setHeaderText("Add optional categorical information");
    alert.getDialogPane().setContent(name);
    alert
        .showAndWait()
        .filter(button -> button == ButtonType.OK)
        .ifPresent(
            button -> {
              String value = name.getText() == null ? "" : name.getText().strip();
              if (!value.isEmpty() && !resource.getCodelists().contains(value)) {
                resource.getCodelists().add(value);
                validateResource();
              }
            });
  }

  @Override
  protected void onSingleClickItemSelection(Resource value) {
    if (value == resource || markerSections.containsKey(value) && markerSections.get(value) != null)
      edit(value);
  }

  @Override
  protected void onDoubleClickItemSelection(Resource value) {
    onSingleClickItemSelection(value);
  }

  @Override
  protected Node createEditor(Resource value) {
    Section section = value == resource ? Section.OVERVIEW : markerSections.get(value);
    if (section == null) return new VBox();
    return switch (section) {
      case OVERVIEW -> overviewPage();
      case GEOMETRY -> geometryPage();
      case INTERFACE -> interfacePage();
      case PARAMETERS -> parametersPage();
      case METADATA -> metadataPage();
      case LICENSE -> licensePage();
      case PERMISSIONS -> permissionsPage();
      case FILES -> filesPage();
      case WORKFLOWS -> workflowsPage();
      case HISTORY -> historyPage();
    };
  }

  private Node overviewPage() {
    VBox content = page("Resource", "Identity, host, adapter, geometry and publication readiness");
    GridPane identity = form();
    TextField urn = field(resource.getUrn(), "service:originator:namespace:resource-id");
    TextField localName = field(resource.getLocalName(), "Optional short name");
    TextField version =
        field(resource.getVersion() == null ? "" : resource.getVersion().toString(), "1.0.0");
    ComboBox<Artifact.Type> type =
        new ComboBox<>(FXCollections.observableArrayList(Artifact.Type.values()));
    type.setValue(resource.getType());
    type.setMaxWidth(Double.MAX_VALUE);
    ComboBox<AdapterDescriptor> adapter = adapterSelector();
    addRow(identity, 0, "URN *", urn);
    addRow(identity, 1, "Local name", localName);
    addRow(identity, 2, "Version *", version);
    addRow(identity, 3, "Produces *", type);
    addRow(identity, 4, "Adapter *", adapter);
    urn.textProperty()
        .addListener(
            (o, a, b) -> {
              resource.setUrn(text(b));
              validateResource();
            });
    localName.textProperty().addListener((o, a, b) -> resource.setLocalName(text(b)));
    version
        .textProperty()
        .addListener(
            (o, a, b) -> {
              try {
                resource.setVersion(Version.create(b));
              } catch (RuntimeException ignored) {
                resource.setVersion(null);
              }
              validateResource();
            });
    type.valueProperty()
        .addListener(
            (o, a, b) -> {
              resource.setType(b);
              validateResource();
            });
    adapter
        .valueProperty()
        .addListener(
            (o, a, b) -> {
              resource.setAdapterType(b == null ? null : b.getName());
              validateResource();
            });
    setEditable(identity, canEdit());
    HBox cards = new HBox(10);
    GeometryCard geometry = new GeometryCard(resource.getGeometry(), true);
    geometry.setMinWidth(260);
    geometry.setMaxWidth(Double.MAX_VALUE);
    MetadataCard metadata =
        new MetadataCard(
            resource.getMetadata(),
            new MetadataCard.Options()
                .title("Metadata")
                .emptyTitle("Metadata have not been entered"),
            true);
    metadata.setMinWidth(260);
    metadata.setMaxWidth(Double.MAX_VALUE);
    HBox.setHgrow(geometry, Priority.ALWAYS);
    HBox.setHgrow(metadata, Priority.ALWAYS);
    cards.getChildren().addAll(geometry, metadata);
    validationLabel = new Label();
    validationLabel.setWrapText(true);
    validationLabel.setMaxWidth(Double.MAX_VALUE);
    saveTemporaryButton = new Button("Update temporary data");
    saveTemporaryButton.setTooltip(
        new Tooltip(
            "Store the current information while keeping the resource in staging, even when publication metadata are incomplete."));
    saveTemporaryButton.setOnAction(event -> submit(true));
    saveButton = new Button(draft ? "Create resource" : "Save new version");
    saveButton.getStyleClass().add(Styles.ACCENT);
    saveButton.setOnAction(event -> submit(false));
    Button delete = new Button("Delete");
    delete.getStyleClass().addAll(Styles.DANGER, Styles.BUTTON_OUTLINED);
    delete.setDisable(!canDelete() || draft);
    delete.setOnAction(event -> deleteResource());
    HBox actions = new HBox(8, validationLabel, spacer(), delete, saveTemporaryButton, saveButton);
    actions.setAlignment(Pos.CENTER_LEFT);
    HBox.setHgrow(validationLabel, Priority.ALWAYS);
    content.getChildren().addAll(identity, cards, new Separator(), actions);
    VBox.setVgrow(cards, Priority.ALWAYS);
    refreshValidationControls();
    return scroll(content);
  }

  private Node geometryPage() {
    VBox content =
        page(
            "Geometry and time",
            "Edit the encoded geometry or the commonly changed temporal extent");
    StackPane preview = new StackPane();
    Consumer<Geometry> refreshPreview =
        geometry -> {
          GeometryCard card = new GeometryCard(geometry, true);
          card.setMinHeight(280);
          card.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
          preview.getChildren().setAll(card);
        };
    refreshPreview.accept(resource.getGeometry());
    TextField encoded =
        field(
            resource.getGeometry() == null ? "" : resource.getGeometry().encode(),
            "Geometry encoding");
    Button apply = new Button("Apply geometry");
    Label error = new Label();
    error.setStyle("-fx-text-fill: -color-danger-fg;");
    apply.setOnAction(
        event -> {
          try {
            Geometry geometry = Geometry.create(encoded.getText().strip());
            resource.setGeometry(geometry);
            error.setText("");
            refreshPreview.accept(geometry);
          } catch (RuntimeException failure) {
            error.setText("Invalid geometry: " + failure.getMessage());
          }
          validateResource();
        });
    DatePicker start = new DatePicker(temporalDate(resource.getGeometry(), true));
    DatePicker end = new DatePicker(temporalDate(resource.getGeometry(), false));
    Button applyTime = new Button("Apply time extent");
    applyTime.setOnAction(
        event -> {
          if (start.getValue() == null
              || end.getValue() == null
              || !start.getValue().isBefore(end.getValue())) {
            error.setText("Choose an end date after the start date");
            return;
          }
          GeometryImpl geometry = (GeometryImpl) Geometry.create(encoded.getText().strip());
          geometry
              .withTemporalStart(epoch(start.getValue()))
              .withTemporalEnd(epoch(end.getValue()));
          resource.setGeometry(geometry);
          encoded.setText(geometry.encode());
          error.setText("");
          refreshPreview.accept(geometry);
          validateResource();
        });
    HBox encoding = new HBox(8, encoded, apply);
    HBox.setHgrow(encoded, Priority.ALWAYS);
    HBox time = new HBox(8, new Label("Start"), start, new Label("End"), end, applyTime);
    time.setAlignment(Pos.CENTER_LEFT);
    content
        .getChildren()
        .addAll(preview, new Label("Encoded geometry"), encoding, error, new Separator(), time);
    VBox.setVgrow(preview, Priority.ALWAYS);
    setEditable(content, canEdit());
    return content;
  }

  private Node interfacePage() {
    VBox content =
        page("Data interface", "Named, non-semantic data produced or consumed by this resource");
    content
        .getChildren()
        .addAll(
            attributePane("Attributes", resource.getAttributes(), AttributeKind.ATTRIBUTE),
            attributePane("Inputs", resource.getInputs(), AttributeKind.INPUT),
            attributePane("Additional outputs", resource.getOutputs(), AttributeKind.OUTPUT));
    return scroll(content);
  }

  private Node attributePane(
      String title, Collection<Resource.Attribute> source, AttributeKind kind) {
    ObservableList<AttributeRow> rows = FXCollections.observableArrayList();
    safe(source).forEach(value -> rows.add(new AttributeRow(value)));
    TableView<AttributeRow> table = new TableView<>(rows);
    table.setEditable(canEdit());
    table.setMinHeight(150);
    table.setPrefHeight(190);
    TableColumn<AttributeRow, String> name = new TableColumn<>("Name");
    name.setCellValueFactory(value -> value.getValue().name);
    name.setCellFactory(TextFieldTableCell.forTableColumn());
    TableColumn<AttributeRow, Artifact.Type> type = new TableColumn<>("Data type");
    type.setCellValueFactory(value -> value.getValue().type);
    type.setCellFactory(ComboBoxTableCell.forTableColumn(Artifact.Type.values()));
    TableColumn<AttributeRow, Boolean> key = new TableColumn<>("Key");
    key.setCellValueFactory(value -> value.getValue().key);
    key.setCellFactory(CheckBoxTableCell.forTableColumn(key));
    TableColumn<AttributeRow, Boolean> optional = new TableColumn<>("Optional");
    optional.setCellValueFactory(value -> value.getValue().optional);
    optional.setCellFactory(CheckBoxTableCell.forTableColumn(optional));
    if (kind == AttributeKind.OUTPUT) {
      key.setVisible(false);
      optional.setVisible(false);
    }
    table.getColumns().addAll(name, type, key, optional);
    name.setPrefWidth(230);
    type.setPrefWidth(180);
    key.setPrefWidth(80);
    optional.setPrefWidth(90);
    rows.forEach(row -> watch(row, rows, kind));
    Button add = new Button("Add");
    Button remove = new Button("Remove");
    add.setDisable(!canEdit());
    remove
        .disableProperty()
        .bind(
            table
                .getSelectionModel()
                .selectedItemProperty()
                .isNull()
                .or(new SimpleBooleanProperty(!canEdit())));
    add.setOnAction(
        event -> {
          AttributeRow row = new AttributeRow("", Artifact.Type.NUMBER, false, false);
          rows.add(row);
          watch(row, rows, kind);
          table.getSelectionModel().select(row);
          syncAttributes(rows, kind);
        });
    remove.setOnAction(
        event -> {
          rows.remove(table.getSelectionModel().getSelectedItem());
          syncAttributes(rows, kind);
        });
    VBox box = new VBox(6, new HBox(6, new Label(title), spacer(), add, remove), table);
    TitledPane pane = new TitledPane(title, box);
    pane.setCollapsible(false);
    return pane;
  }

  private void watch(AttributeRow row, ObservableList<AttributeRow> rows, AttributeKind kind) {
    row.name.addListener((o, a, b) -> syncAttributes(rows, kind));
    row.type.addListener((o, a, b) -> syncAttributes(rows, kind));
    row.key.addListener(
        (o, a, b) -> {
          if (b) row.optional.set(false);
          syncAttributes(rows, kind);
        });
    row.optional.addListener((o, a, b) -> syncAttributes(rows, kind));
  }

  private void syncAttributes(List<AttributeRow> rows, AttributeKind kind) {
    List<Resource.Attribute> values =
        rows.stream().map(AttributeRow::toAttribute).map(Resource.Attribute.class::cast).toList();
    switch (kind) {
      case ATTRIBUTE -> resource.setAttributes(new ArrayList<>(values));
      case INPUT -> resource.setInputs(new ArrayList<>(values));
      case OUTPUT -> resource.setOutputs(new ArrayList<>(values));
    }
    validateResource();
  }

  private Node parametersPage() {
    VBox content =
        page("Adapter parameters", "The selected adapter defines this parameterization contract");
    AdapterDescriptor descriptor = selectedAdapter();
    if (descriptor == null) {
      content.getChildren().add(new Label("Choose an adapter on the Resource page first."));
      return content;
    }
    GridPane grid = form();
    int row = 0;
    for (Adapter.Parameter parameter :
        Optional.ofNullable(descriptor.getParameters()).orElseGet(List::of)) {
      Node editor;
      if (parameter.getEnumValues() != null && !parameter.getEnumValues().isEmpty()) {
        ComboBox<String> combo =
            new ComboBox<>(FXCollections.observableArrayList(parameter.getEnumValues()));
        Object current = resource.getParameters().get(parameter.getName());
        combo.setValue(current == null ? null : current.toString());
        combo.valueProperty().addListener((o, a, b) -> updateParameter(parameter.getName(), b));
        combo.setMaxWidth(Double.MAX_VALUE);
        editor = combo;
      } else {
        Object current = resource.getParameters().get(parameter.getName());
        TextField field =
            field(current == null ? "" : current.toString(), parameter.getDescription());
        field.textProperty().addListener((o, a, b) -> updateParameter(parameter.getName(), b));
        editor = field;
      }
      addRow(grid, row++, parameter.getName() + (parameter.isOptional() ? "" : " *"), editor);
      if (parameter.getDescription() != null)
        Tooltip.install(editor, new Tooltip(parameter.getDescription()));
    }
    MetadataCard all =
        new MetadataCard(
            resource.getParameters(),
            new MetadataCard.Options()
                .title("All stored parameters")
                .pathTree(true)
                .editHandler(
                    (key, old, value) -> {
                      resource.getParameters().put(key, value);
                      validateResource();
                      return true;
                    }),
            true);
    content.getChildren().addAll(grid, all);
    VBox.setVgrow(all, Priority.ALWAYS);
    setEditable(content, canEdit());
    return content;
  }

  private void updateParameter(String name, String value) {
    if (value == null || value.isBlank()) resource.getParameters().remove(name);
    else resource.getParameters().put(name, value);
    validateResource();
  }

  private Node metadataPage() {
    VBox content = page("Metadata", "Dublin Core-inspired discovery and review information");
    GridPane mandatory = form();
    TextField label = metadataField(Metadata.DC_LABEL, "Display label");
    TextField originator =
        metadataField(Metadata.DC_ORIGINATOR, "Person or organization that originated the data");
    TextField contact =
        metadataField(ResourceEditorValidator.CONTACT, "Reference contact or email");
    TextArea description =
        metadataArea(Metadata.DC_DESCRIPTION, "What the resource contains and how it was produced");
    TextField creator = metadataField(Metadata.DC_CREATOR, "Resource descriptor author");
    TextField publisher = metadataField(Metadata.DC_PUBLISHER, "Publisher");
    TextField source = metadataField(Metadata.DC_SOURCE, "Source URL, DOI or citation");
    TextField keywords = metadataField(Metadata.IM_KEYWORDS, "Comma-separated discovery terms");
    addRow(mandatory, 0, "Label *", label);
    addRow(mandatory, 1, "Originator *", originator);
    addRow(mandatory, 2, "Reference contact *", contact);
    addRow(mandatory, 3, "Description *", description);
    addRow(mandatory, 4, "Creator", creator);
    addRow(mandatory, 5, "Publisher", publisher);
    addRow(mandatory, 6, "Source", source);
    addRow(mandatory, 7, "Keywords", keywords);
    StackPane extra = new StackPane();
    Runnable refresh = () -> extra.getChildren().setAll(additionalMetadataCard());
    refresh.run();
    TextField key = field("", "namespace:key");
    TextField value = field("", "Value");
    Button add = new Button("Add metadata");
    add.setDisable(!canEditMetadata());
    add.setOnAction(
        event -> {
          if (!key.getText().isBlank()) {
            resource.getMetadata().put(key.getText().strip(), value.getText());
            key.clear();
            value.clear();
            refresh.run();
            validateResource();
          }
        });
    HBox addition = new HBox(8, key, value, add);
    HBox.setHgrow(key, Priority.ALWAYS);
    HBox.setHgrow(value, Priority.ALWAYS);
    content.getChildren().addAll(mandatory, new Separator(), addition, extra);
    VBox.setVgrow(extra, Priority.ALWAYS);
    setEditable(mandatory, canEditMetadata());
    return scroll(content);
  }

  private MetadataCard additionalMetadataCard() {
    Metadata additional = Metadata.create(resource.getMetadata());
    for (String key :
        List.of(
            Metadata.DC_LABEL,
            Metadata.DC_ORIGINATOR,
            ResourceEditorValidator.CONTACT,
            Metadata.DC_DESCRIPTION,
            Metadata.DC_CREATOR,
            Metadata.DC_PUBLISHER,
            Metadata.DC_SOURCE,
            Metadata.IM_KEYWORDS,
            ResourceEditorValidator.LICENSE_ID,
            ResourceEditorValidator.LICENSE_TEXT,
            ResourceEditorValidator.LICENSE_URL,
            ResourceEditorValidator.USAGE)) additional.remove(key);
    return new MetadataCard(
        additional,
        new MetadataCard.Options()
            .title("Additional metadata")
            .pathTree(true)
            .emptyTitle("No additional metadata")
            .editHandler(
                canEditMetadata()
                    ? (key, old, value) -> {
                      resource.getMetadata().put(key, value);
                      validateResource();
                      return true;
                    }
                    : null),
        true);
  }

  private Node licensePage() {
    VBox content =
        page("License and usage", "License terms are independent from k.LAB access permissions");
    ComboBox<LicenseChoice> choices =
        new ComboBox<>(FXCollections.observableArrayList(LicenseChoice.COMMON));
    choices.setMaxWidth(Double.MAX_VALUE);
    choices.setCellFactory(ignored -> licenseCell());
    choices.setButtonCell(licenseCell());
    String current = string(resource.getMetadata().get(ResourceEditorValidator.LICENSE_ID));
    choices
        .getSelectionModel()
        .select(
            LicenseChoice.COMMON.stream()
                .filter(choice -> choice.id().equals(current))
                .findFirst()
                .orElse(LicenseChoice.CUSTOM));
    TextArea licenseText = new TextArea();
    licenseText.setWrapText(true);
    licenseText.setPrefRowCount(10);
    TextField url =
        field(
            string(resource.getMetadata().get(ResourceEditorValidator.LICENSE_URL)), "License URL");
    ComboBox<String> usage =
        new ComboBox<>(
            FXCollections.observableArrayList("Open", "Restricted", "Conditional", "Unknown"));
    usage.setValue(string(resource.getMetadata().get(ResourceEditorValidator.USAGE)));
    usage.setMaxWidth(Double.MAX_VALUE);
    Label signal = new Label();
    signal.setWrapText(true);
    Runnable refresh =
        () -> {
          LicenseChoice choice = choices.getValue();
          boolean custom = choice == LicenseChoice.CUSTOM;
          licenseText.setEditable(canEditMetadata() && custom);
          licenseText.setText(
              custom
                  ? string(resource.getMetadata().get(ResourceEditorValidator.LICENSE_TEXT))
                  : choice.text());
          if (!custom) url.setText(choice.url());
          signal.setText(
              "Open".equals(usage.getValue())
                  ? "Open for use, subject to the license terms shown below."
                  : "Usage is not automatically open; inspect the terms and contact the originator.");
        };
    choices
        .valueProperty()
        .addListener(
            (o, a, choice) -> {
              if (choice != null) {
                resource.getMetadata().put(ResourceEditorValidator.LICENSE_ID, choice.id());
                resource.getMetadata().put(Metadata.DC_RIGHTS, choice.name());
                resource.getMetadata().put(ResourceEditorValidator.LICENSE_URL, choice.url());
                if (choice != LicenseChoice.CUSTOM)
                  resource.getMetadata().put(ResourceEditorValidator.LICENSE_TEXT, choice.text());
                refresh.run();
                validateResource();
              }
            });
    licenseText
        .textProperty()
        .addListener(
            (o, a, b) -> {
              if (choices.getValue() == LicenseChoice.CUSTOM) {
                resource.getMetadata().put(ResourceEditorValidator.LICENSE_TEXT, b);
                validateResource();
              }
            });
    url.textProperty()
        .addListener(
            (o, a, b) -> resource.getMetadata().put(ResourceEditorValidator.LICENSE_URL, b));
    usage
        .valueProperty()
        .addListener(
            (o, a, b) -> {
              resource.getMetadata().put(ResourceEditorValidator.USAGE, b);
              refresh.run();
              validateResource();
            });
    GridPane grid = form();
    addRow(grid, 0, "License *", choices);
    addRow(grid, 1, "Usage at a glance *", usage);
    addRow(grid, 2, "License URL", url);
    content.getChildren().addAll(grid, signal, new Label("License text"), licenseText);
    VBox.setVgrow(licenseText, Priority.ALWAYS);
    refresh.run();
    setEditable(grid, canEditMetadata());
    return content;
  }

  private ListCell<LicenseChoice> licenseCell() {
    return new ListCell<>() {
      @Override
      protected void updateItem(LicenseChoice item, boolean empty) {
        super.updateItem(item, empty);
        setText(empty || item == null ? null : item.name());
      }
    };
  }

  private Node permissionsPage() {
    VBox content = page("Permissions", "Access rights are evaluated separately from license terms");
    ResourcePrivileges rights =
        resourceInfo.getRights() == null ? ResourcePrivileges.empty() : resourceInfo.getRights();
    PermissionEditor editor = new PermissionEditor(rights.toString());
    resource.getMetadata().put(ResourceEditorValidator.PERMISSIONS, rights.toString());
    editor
        .permissionsProperty()
        .addListener(
            (o, a, b) -> {
              resourceInfo.setRights(ResourcePrivileges.create(b));
              resource.getMetadata().put(ResourceEditorValidator.PERMISSIONS, b);
              validateResource();
            });
    Label note =
        new Label(
            "An empty permission set means current-owner scope only. Public access is represented by '*'.");
    note.setWrapText(true);
    content.getChildren().addAll(note, editor);
    setEditable(editor, canEdit());
    return content;
  }

  private Node filesPage() {
    VBox content =
        page(
            "Files and integrity",
            "Ancillary data, source datasets and documentation stored with the resource");
    ListView<File> files =
        new ListView<>(FXCollections.observableArrayList(resource.getLocalFiles()));
    files.setCellFactory(
        ignored ->
            new ListCell<>() {
              @Override
              protected void updateItem(File file, boolean empty) {
                super.updateItem(file, empty);
                setText(
                    empty || file == null
                        ? null
                        : (file.isFile() && file.canRead() ? "✓ " : "! ") + file.getAbsolutePath());
              }
            });
    UploadBox upload =
        new UploadBox(
            Configuration.INSTANCE.getTemporaryDataPath().toString(),
            "Drop ancillary files, source data or documentation",
            file -> {
              if (!resource.getLocalFiles().contains(file)) resource.getLocalFiles().add(file);
              Platform.runLater(() -> files.getItems().setAll(resource.getLocalFiles()));
              validateResource();
            },
            (message, throwable) -> notifyError("Resource upload failed: " + message));
    Button remove = new Button("Remove selected");
    remove
        .disableProperty()
        .bind(
            files
                .getSelectionModel()
                .selectedItemProperty()
                .isNull()
                .or(new SimpleBooleanProperty(!canEdit())));
    remove.setOnAction(
        event -> {
          resource.getLocalFiles().remove(files.getSelectionModel().getSelectedItem());
          files.getItems().setAll(resource.getLocalFiles());
          validateResource();
        });
    content.getChildren().addAll(upload, remove, files);
    VBox.setVgrow(files, Priority.ALWAYS);
    if (!canEdit()) upload.setDisable(true);
    return content;
  }

  private Node historyPage() {
    VBox content =
        page(
            "Version history",
            "Previous immutable versions are maintained by the hosting Resources service");
    ListView<Resource> history =
        new ListView<>(FXCollections.observableArrayList(resource.getHistory()));
    history.setCellFactory(
        ignored ->
            new ListCell<>() {
              @Override
              protected void updateItem(Resource value, boolean empty) {
                super.updateItem(value, empty);
                setText(
                    empty || value == null
                        ? null
                        : value.getVersion() + " — " + Instant.ofEpochMilli(value.getTimestamp()));
              }
            });
    if (resource.getHistory().isEmpty())
      history.setPlaceholder(new Label("No previous version is available"));
    content.getChildren().add(history);
    VBox.setVgrow(history, Priority.ALWAYS);
    return content;
  }

  private Node workflowsPage() {
    VBox content =
        page("Workflows", "Review and publication workflows associated with this resource");
    ListView<Flow> flows = new ListView<>();
    Runnable refresh =
        () -> {
          if (service == null) return;
          try {
            flows
                .getItems()
                .setAll(
                    service.getFlows(true, KlabIDEController.instance().user()).stream()
                        .filter(flow -> Objects.equals(resource.getUrn(), flow.getAssetUrn()))
                        .toList());
          } catch (Throwable error) {
            notifyError(error.getMessage());
          }
        };
    flows.setCellFactory(
        ignored ->
            new ListCell<>() {
              @Override
              protected void updateItem(Flow flow, boolean empty) {
                super.updateItem(flow, empty);
                setText(
                    empty || flow == null ? null : flow.getWorkflowId() + " — " + flow.getStatus());
              }
            });
    flows.setOnMouseClicked(
        event -> {
          if (event.getClickCount() == 2 && flows.getSelectionModel().getSelectedItem() != null)
            openWorkflow(flows.getSelectionModel().getSelectedItem(), null);
        });
    Button open = new Button("Open");
    open.disableProperty().bind(flows.getSelectionModel().selectedItemProperty().isNull());
    open.setOnAction(event -> openWorkflow(flows.getSelectionModel().getSelectedItem(), null));
    Button reload = new Button("Refresh");
    reload.setOnAction(event -> refresh.run());
    Menu start = workflowMenu();
    Button startButton = new Button("Start workflow");
    ContextMenu startMenu = new ContextMenu();
    startMenu.getItems().addAll(start.getItems());
    startButton.setOnAction(
        event -> startMenu.show(startButton, javafx.geometry.Side.BOTTOM, 0, 0));
    startButton.setDisable(startMenu.getItems().isEmpty());
    content.getChildren().addAll(new HBox(8, startButton, open, reload), flows);
    VBox.setVgrow(flows, Priority.ALWAYS);
    refresh.run();
    return content;
  }

  private Menu workflowMenu() {
    Menu workflows = new Menu("Workflows");
    if (service == null || resource.getUrn() == null) return workflows;
    try {
      UserScope scope = KlabIDEController.instance().user();
      WorkflowParticipant participant = WorkflowParticipant.from(scope);
      if (participant.getRoles().contains(WorkflowRole.EDITOR)
          || participant.getRoles().contains(WorkflowRole.ADMIN))
        for (Workflow workflow :
            Optional.ofNullable(workflowUIProvider.availableWorkflows(resource, scope))
                .orElseGet(List::of)) {
          MenuItem item = new MenuItem(workflowName(workflow));
          item.setOnAction(event -> startWorkflow(workflow));
          workflows.getItems().add(item);
        }
    } catch (Throwable error) {
      notifyError(error.getMessage());
    }
    return workflows;
  }

  private void startWorkflow(Workflow workflow) {
    try {
      UserScope scope = KlabIDEController.instance().user();
      WorkflowParticipant participant = WorkflowParticipant.from(scope);
      Workflow.TransitionSchema initialTransition =
          workflow.getTransitions().values().stream()
              .filter(transition -> transition.getSourceStates().contains(Workflow.INIT))
              .filter(transition -> participant.hasAnyRole(transition.getRoles()))
              .findFirst()
              .orElseThrow(() -> new IllegalStateException("No permitted initial stage"));
      Flow.State initial = new Flow.State();
      initial.setSchemaId(initialTransition.getTargetState());
      initial.setAssetUrn(resource.getUrn());
      initial.setAssetType(KlabAsset.KnowledgeClass.RESOURCE);
      initial.setOwner(participant.getIdentity());
      initial.getAssignees().add(participant.getIdentity());
      Flow flow = workflowUIProvider.startFlow(service, workflow, initial, scope);
      openWorkflow(flow, workflow);
    } catch (Throwable error) {
      notifyError(error.getMessage());
    }
  }

  private void openWorkflow(Flow flow, Workflow known) {
    if (flow == null) return;
    try {
      Workflow workflow =
          known == null
              ? service.getWorkflow(flow.getWorkflowId(), KlabIDEController.instance().user())
              : known;
      WorkflowEditor editor =
          new WorkflowEditor(
              service,
              KlabIDEController.instance().user(),
              workflow,
              flow,
              workflowUIProvider::stageEditor);
      showAuxiliaryEditor(
          "workflow:" + flow.getId(), workflowName(workflow) + " — workflow", editor);
    } catch (Throwable error) {
      notifyError(error.getMessage());
    }
  }

  private static String workflowName(Workflow workflow) {
    return workflow.getName() == null || workflow.getName().isBlank()
        ? workflow.getId()
        : workflow.getName();
  }

  private AdapterDescriptor selectedAdapter() {
    return adapters.stream()
        .filter(adapter -> Objects.equals(adapter.getName(), resource.getAdapterType()))
        .findFirst()
        .orElse(null);
  }

  private ComboBox<AdapterDescriptor> adapterSelector() {
    ComboBox<AdapterDescriptor> ret = new ComboBox<>(FXCollections.observableArrayList(adapters));
    ret.setCellFactory(ignored -> adapterCell());
    ret.setButtonCell(adapterCell());
    ret.setMaxWidth(Double.MAX_VALUE);
    ret.setValue(selectedAdapter());
    if (ret.getValue() == null && resource.getAdapterType() != null) {
      AdapterDescriptor unavailable = new AdapterDescriptor();
      unavailable.setName(resource.getAdapterType());
      ret.getItems().addFirst(unavailable);
      ret.setValue(unavailable);
    }
    return ret;
  }

  private ListCell<AdapterDescriptor> adapterCell() {
    return new ListCell<>() {
      @Override
      protected void updateItem(AdapterDescriptor item, boolean empty) {
        super.updateItem(item, empty);
        setText(
            empty || item == null
                ? null
                : item.getName() + (item.getVersion() == null ? "" : "  " + item.getVersion()));
      }
    };
  }

  private void validateResource() {
    AdapterDescriptor adapter = selectedAdapter();
    Adapter.Parameter[] parameters =
        adapter == null || adapter.getParameters() == null
            ? new Adapter.Parameter[0]
            : adapter.getParameters().toArray(Adapter.Parameter[]::new);
    validation = ResourceEditorValidator.validate(resource, expectedServiceId(), parameters);
    refreshValidationControls();
    if (index != null) index.refresh();
  }

  private void refreshValidationControls() {
    if (saveButton != null)
      saveButton.setDisable(!validation.valid() || !canEditMetadata() || busy || service == null);
    if (saveTemporaryButton != null)
      saveTemporaryButton.setDisable(
          !validation.valid(Section.OVERVIEW) || !canEditMetadata() || busy || service == null);
    if (validationLabel != null) {
      if (validation.valid()) {
        validationLabel.setText("Ready to " + (draft ? "create" : "save"));
        validationLabel.setTooltip(null);
        validationLabel.setStyle("-fx-text-fill: -color-success-fg;");
      } else {
        String details =
            validation.issues().stream()
                .limit(2)
                .map(ResourceEditorValidator.Issue::message)
                .distinct()
                .reduce((a, b) -> a + "; " + b)
                .orElse("Incomplete resource");
        if (validation.issues().size() > 2) details += "; …";
        validationLabel.setText(
            validation.issues().size() + " required or inconsistent field(s): " + details);
        validationLabel.setTooltip(
            new Tooltip(
                validation.issues().stream()
                    .map(issue -> sectionLabels.get(issue.section()) + ": " + issue.message())
                    .distinct()
                    .reduce((a, b) -> a + "\n" + b)
                    .orElse("")));
        validationLabel.setStyle("-fx-text-fill: -color-danger-fg;");
      }
    }
  }

  private String expectedServiceId() {
    return service != null ? service.serviceName() : resource.getServiceId();
  }

  private boolean canEdit() {
    if (draft) return true;
    if (resourceInfo.getPermissions() != null && !resourceInfo.getPermissions().isEmpty())
      return resourceInfo.getPermissions().contains(CRUDOperation.UPDATE)
          || resourceInfo.getPermissions().contains(CRUDOperation.ADMINISTER);
    try {
      var permissions = service.capabilities(KlabIDEController.instance().user()).getPermissions();
      return permissions.contains(CRUDOperation.UPDATE)
          || permissions.contains(CRUDOperation.ADMINISTER);
    } catch (Throwable ignored) {
      return false;
    }
  }

  private boolean canEditMetadata() {
    return canEdit()
        || resourceInfo.getPermissions().contains(CRUDOperation.UPDATE_METADATA)
        || resourceInfo.getPermissions().contains(CRUDOperation.ADMINISTER);
  }

  private boolean canDelete() {
    return resourceInfo.getPermissions().contains(CRUDOperation.DELETE)
        || resourceInfo.getPermissions().contains(CRUDOperation.ADMINISTER);
  }

  private void submit(boolean temporary) {
    validateResource();
    if ((!temporary && !validation.valid())
        || (temporary && !validation.valid(Section.OVERVIEW))
        || service == null
        || busy) return;
    busy = true;
    refreshValidationControls();
    Task<Resource> task =
        new Task<>() {
          @Override
          protected Resource call() throws Exception {
            resource.setTimestamp(System.currentTimeMillis());
            resource.setServiceId(service.serviceId());
            if (draft) {
              Future<?> future =
                  service.importResource(resource, KlabIDEController.instance().user());
              future.get();
            } else
              service.submit(
                  resource,
                  ResourcesService.SubmissionMode.UPDATE,
                  KlabIDEController.instance().user());
            Resource stored =
                service.retrieve(
                    resource.getUrn(), Resource.class, KlabIDEController.instance().user());
            return stored == null ? resource : stored;
          }
        };
    task.setOnSucceeded(
        event -> {
          busy = false;
          Resource stored = task.getValue();
          adoptStoredResource(stored);
          if (temporary && (resourceInfo.getStage() == null || draft))
            resourceInfo.setStage(ResourceInfo.Stage.STAGING);
          draft = false;
          if (saveButton != null) saveButton.setText("Save new version");
          validateResource();
          onSaved.accept(stored);
          KlabIDEController.instance()
              .handleNotification(
                  Notification.info(
                      (temporary ? "Temporary resource data updated: " : "Resource saved: ")
                          + resource.getUrn()));
        });
    task.setOnFailed(
        event -> {
          busy = false;
          refreshValidationControls();
          notifyError("Resource could not be saved: " + task.getException().getMessage());
        });
    Thread thread = new Thread(task, "resource-submit");
    thread.setDaemon(true);
    thread.start();
  }

  private void deleteResource() {
    if (service == null || !canDelete()) return;
    Alert alert = new Alert(Alert.AlertType.WARNING);
    alert.setTitle("Delete resource");
    alert.setHeaderText("Delete " + resource.getUrn() + "?");
    alert.setContentText(
        "The service may retain a tombstone, but this resource will no longer be usable.");
    ButtonType confirm = new ButtonType("Delete", ButtonBar.ButtonData.OK_DONE);
    alert.getButtonTypes().setAll(confirm, ButtonType.CANCEL);
    if (alert.showAndWait().orElse(ButtonType.CANCEL) != confirm) return;
    try {
      service.delete(
          resource.getUrn(),
          KlabAsset.KnowledgeClass.RESOURCE,
          KlabIDEController.instance().user());
      onDeleted.run();
    } catch (Throwable error) {
      notifyError("Resource could not be deleted: " + error.getMessage());
    }
  }

  private void adoptStoredResource(Resource stored) {
    if (stored == null || stored == resource) return;
    ResourceImpl copy = copy(stored);
    resource.setUrn(copy.getUrn());
    resource.setServiceId(copy.getServiceId());
    resource.setAdapterType(copy.getAdapterType());
    resource.setVersion(copy.getVersion());
    resource.setTimestamp(copy.getTimestamp());
    resource.setType(copy.getType());
    resource.setGeometry(copy.getGeometry());
    resource.setLocalName(copy.getLocalName());
    resource.setLocalProjectName(copy.getLocalProjectName());
    resource.setMetadata(copy.getMetadata());
    resource.setParameters(copy.getParameters());
    resource.setLocalFiles(copy.getLocalFiles());
    resource.setHistory(copy.getHistory());
    resource.setNotifications(copy.getNotifications());
    resource.setAttributes(copy.getAttributes());
    resource.setInputs(copy.getInputs());
    resource.setOutputs(copy.getOutputs());
    resource.setCategorizables(copy.getCategorizables());
    resource.setCodelists(copy.getCodelists());
    resource.setAnnotations(copy.getAnnotations());
  }

  private TextField metadataField(String key, String prompt) {
    TextField ret = field(string(resource.getMetadata().get(key)), prompt);
    ret.textProperty()
        .addListener(
            (o, a, b) -> {
              putMetadata(key, b);
              validateResource();
            });
    return ret;
  }

  private TextArea metadataArea(String key, String prompt) {
    TextArea ret = new TextArea(string(resource.getMetadata().get(key)));
    ret.setPromptText(prompt);
    ret.setWrapText(true);
    ret.setPrefRowCount(4);
    ret.textProperty()
        .addListener(
            (o, a, b) -> {
              putMetadata(key, b);
              validateResource();
            });
    return ret;
  }

  private void putMetadata(String key, String value) {
    if (value == null || value.isBlank()) resource.getMetadata().remove(key);
    else resource.getMetadata().put(key, value.strip());
  }

  private static VBox page(String title, String subtitle) {
    Label heading = new Label(title);
    heading.getStyleClass().add(Styles.TITLE_3);
    Label explanation = new Label(subtitle);
    explanation.getStyleClass().add(Styles.TEXT_MUTED);
    explanation.setWrapText(true);
    VBox ret = new VBox(10, heading, explanation, new Separator());
    ret.setPadding(new Insets(14));
    ret.setFillWidth(true);
    ret.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
    return ret;
  }

  private static ScrollPane scroll(Node content) {
    ScrollPane ret = new ScrollPane(content);
    ret.setFitToWidth(true);
    ret.setFitToHeight(false);
    return ret;
  }

  private static GridPane form() {
    GridPane ret = new GridPane();
    ret.setHgap(10);
    ret.setVgap(8);
    ColumnConstraints labels = new ColumnConstraints();
    labels.setMinWidth(130);
    ColumnConstraints fields = new ColumnConstraints();
    fields.setHgrow(Priority.ALWAYS);
    ret.getColumnConstraints().addAll(labels, fields);
    return ret;
  }

  private static void addRow(GridPane grid, int row, String label, Node field) {
    GridPane.setHgrow(field, Priority.ALWAYS);
    if (field instanceof Region region) region.setMaxWidth(Double.MAX_VALUE);
    grid.add(new Label(label), 0, row);
    grid.add(field, 1, row);
  }

  private static TextField field(String value, String prompt) {
    TextField ret = new TextField(value == null ? "" : value);
    ret.setPromptText(prompt);
    ret.setMaxWidth(Double.MAX_VALUE);
    return ret;
  }

  private static Region spacer() {
    Region ret = new Region();
    HBox.setHgrow(ret, Priority.ALWAYS);
    return ret;
  }

  private static void setEditable(Node node, boolean editable) {
    if (node instanceof TextField field) field.setEditable(editable);
    else if (node instanceof TextArea area) area.setEditable(editable);
    else if (node instanceof ComboBox<?> combo) combo.setDisable(!editable);
    else if (node instanceof DatePicker picker) picker.setDisable(!editable);
    else if (node instanceof CheckBox check) check.setDisable(!editable);
    if (node instanceof Parent parent)
      parent.getChildrenUnmodifiable().forEach(child -> setEditable(child, editable));
  }

  private static String text(String value) {
    return value == null || value.isBlank() ? null : value.strip();
  }

  private static String string(Object value) {
    return value == null ? "" : value.toString();
  }

  private static long epoch(LocalDate date) {
    return date.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli();
  }

  private static LocalDate temporalDate(Geometry geometry, boolean start) {
    if (geometry == null) return null;
    Dimension time = geometry.dimension(Dimension.Type.TIME);
    if (time == null) return null;
    Object value =
        time.getParameters()
            .get(start ? GeometryImpl.PARAMETER_TIME_START : GeometryImpl.PARAMETER_TIME_END);
    return value instanceof Number number
        ? Instant.ofEpochMilli(number.longValue()).atZone(ZoneOffset.UTC).toLocalDate()
        : null;
  }

  private void notifyError(String message) {
    KlabIDEController.instance()
        .handleNotification(
            Notification.error(message == null ? "Resource operation failed" : message));
  }

  @Override
  public boolean isAffectedBy(IDEContextScope scope) {
    return digitalTwinControlPanel != null && digitalTwinControlPanel.isAffectedBy(scope);
  }

  @Override
  public void closeDigitalTwin(IDEContextScope scope) {
    if (digitalTwinControlPanel != null) digitalTwinControlPanel.closeDigitalTwin(scope);
  }

  @Override
  public void unsetDigitalTwin(IDEContextScope scope) {
    super.unsetDigitalTwin(scope);
  }

  private final class IndexCell extends TreeCell<Resource> {
    @Override
    protected void updateItem(Resource item, boolean empty) {
      super.updateItem(item, empty);
      if (empty || item == null) {
        setText(null);
        setGraphic(null);
        return;
      }
      Section section = item == resource ? Section.OVERVIEW : markerSections.get(item);
      setText(
          item == resource
              ? (resource.getUrn() == null ? "New resource" : resource.getUrn())
              : item.getUrn());
      if (section == null) {
        setGraphic(new IconLabel(Material2AL.FOLDER_OPEN, 14, "-color-fg-muted"));
        return;
      }
      boolean valid = validation.valid(section);
      setGraphic(
          new IconLabel(
              valid ? Material2AL.CHECK_CIRCLE : Material2AL.ERROR,
              14,
              valid ? "-color-success-fg" : "-color-danger-fg"));
      setStyle(valid ? "" : "-fx-text-fill: -color-danger-fg;");
      List<ResourceEditorValidator.Issue> issues = validation.forSection(section);
      setTooltip(
          issues.isEmpty()
              ? null
              : new Tooltip(
                  issues.stream()
                      .map(ResourceEditorValidator.Issue::message)
                      .distinct()
                      .reduce((a, b) -> a + "\n" + b)
                      .orElse("")));
    }
  }

  private enum AttributeKind {
    ATTRIBUTE,
    INPUT,
    OUTPUT
  }

  private static final class AttributeRow {
    final StringProperty name = new SimpleStringProperty();
    final ObjectProperty<Artifact.Type> type = new SimpleObjectProperty<>();
    final BooleanProperty key = new SimpleBooleanProperty();
    final BooleanProperty optional = new SimpleBooleanProperty();

    AttributeRow(Resource.Attribute value) {
      this(value.getName(), value.getType(), value.isKey(), value.isOptional());
    }

    AttributeRow(String name, Artifact.Type type, boolean key, boolean optional) {
      this.name.set(name);
      this.type.set(type);
      this.key.set(key);
      this.optional.set(optional);
    }

    AttributeImpl toAttribute() {
      AttributeImpl ret = new AttributeImpl();
      ret.setName(name.get());
      ret.setType(type.get());
      ret.setKey(key.get());
      ret.setOptional(optional.get());
      ret.setIndex(-1);
      return ret;
    }
  }

  private record LicenseChoice(String id, String name, String url, String text) {
    static final LicenseChoice CC0 =
        new LicenseChoice(
            "CC0-1.0",
            "CC0 1.0 — public domain dedication",
            "https://creativecommons.org/publicdomain/zero/1.0/",
            "The creator waives copyright and related rights to the extent permitted by law. Attribution is appreciated but not required.");
    static final LicenseChoice CC_BY =
        new LicenseChoice(
            "CC-BY-4.0",
            "Creative Commons Attribution 4.0",
            "https://creativecommons.org/licenses/by/4.0/",
            "Reuse and adaptation are permitted, including commercially, with attribution and an indication of changes.");
    static final LicenseChoice CC_BY_SA =
        new LicenseChoice(
            "CC-BY-SA-4.0",
            "Creative Commons Attribution-ShareAlike 4.0",
            "https://creativecommons.org/licenses/by-sa/4.0/",
            "Reuse and adaptation are permitted with attribution; adapted material must use the same license.");
    static final LicenseChoice CC_BY_NC =
        new LicenseChoice(
            "CC-BY-NC-4.0",
            "Creative Commons Attribution-NonCommercial 4.0",
            "https://creativecommons.org/licenses/by-nc/4.0/",
            "Non-commercial reuse and adaptation are permitted with attribution and an indication of changes.");
    static final LicenseChoice CC_BY_NC_SA =
        new LicenseChoice(
            "CC-BY-NC-SA-4.0",
            "Creative Commons Attribution-NonCommercial-ShareAlike 4.0",
            "https://creativecommons.org/licenses/by-nc-sa/4.0/",
            "Non-commercial reuse is permitted with attribution; adaptations must use the same license.");
    static final LicenseChoice ODC_BY =
        new LicenseChoice(
            "ODC-BY-1.0",
            "Open Data Commons Attribution 1.0",
            "https://opendatacommons.org/licenses/by/1-0/",
            "Use, redistribution and production of derivative databases are permitted with attribution.");
    static final LicenseChoice ODBL =
        new LicenseChoice(
            "ODbL-1.0",
            "Open Database License 1.0",
            "https://opendatacommons.org/licenses/odbl/1-0/",
            "Database use and adaptation are permitted with attribution and share-alike requirements for derivative databases.");
    static final LicenseChoice CUSTOM =
        new LicenseChoice("custom", "Custom or ad-hoc license", "", "");
    static final List<LicenseChoice> COMMON =
        List.of(CC0, CC_BY, CC_BY_SA, CC_BY_NC, CC_BY_NC_SA, ODC_BY, ODBL, CUSTOM);
  }
}
