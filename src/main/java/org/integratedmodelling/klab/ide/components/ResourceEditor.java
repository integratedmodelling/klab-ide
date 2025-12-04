package org.integratedmodelling.klab.ide.components;

import atlantafx.base.theme.Styles;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontPosture;
import javafx.scene.text.FontWeight;
import org.integratedmodelling.common.logging.Logging;
import org.integratedmodelling.klab.api.knowledge.Resource;
import org.integratedmodelling.klab.ide.IDEContextScope;
import org.integratedmodelling.klab.ide.KlabIDEController;
import org.integratedmodelling.klab.ide.pages.EditorPage;
import org.kordamp.ikonli.javafx.FontIcon;
import org.kordamp.ikonli.material2.Material2AL;
import org.kordamp.ikonli.material2.Material2MZ;

/**
 * JavaFX implementation of a Resource editor, inspired by the original SWT ResourceEditor. This
 * editor provides a UI for viewing and editing Resource objects.
 */
public class ResourceEditor extends EditorPage<Object, Resource> {

  private TabPane mainTabPane;
  private Label urnLabel;
  private Label localNameLabel;
  private TextArea descriptionField;
  private CheckBox isPublishableCheckbox;
  private TextField unpublishableReasonField;
  private WorldMapView worldMapView;
  private TimeEditorView timeEditorView;
  private TableView<AttributeItem> attributesTable;
  private TableView<AttributeItem> inputsTable;
  private TableView<AttributeItem> outputsTable;
  private TreeView<ParameterItem> adapterParametersTree;
  private ComboBox<String> operationsComboBox;
  private Button executeOperationButton;
  private Label geometryDefinitionLabel;

  @Override
  protected void onVisualize(boolean visibleAfterCall) {
    KlabIDEController.instance().setFocalEditor(this, visibleAfterCall);
  }

  /**
   * Constructor for the ResourceEditor
   *
   * @param asset The asset to edit
   */
  public ResourceEditor(Object asset) {
    super(asset);
  }

  @Override
  protected TreeView<Resource> createContentTree() {
    var ret = new TreeView<Resource>();
    return ret;
  }

  @Override
  protected void onSingleClickItemSelection(Resource value) {
    // Handle single click selection
  }

  @Override
  protected void onDoubleClickItemSelection(Resource value) {
    // Handle double click selection
  }

  @Override
  public boolean isAffectedBy(IDEContextScope scope) {
    return this.digitalTwinControlPanel != null && this.digitalTwinControlPanel.isAffectedBy(scope);
  }

  @Override
  public void closeDigitalTwin(IDEContextScope ideContextScope) {
    digitalTwinControlPanel.closeDigitalTwin(ideContextScope);
  }

  @Override
  protected Node createEditor(Resource resource) {
    VBox mainContainer = new VBox(10);
    mainContainer.setPadding(new Insets(10));

    // Header section
    HBox headerBox = createHeaderSection();
    mainContainer.getChildren().add(headerBox);

    // Main tab pane
    mainTabPane = new TabPane();
    mainTabPane.getStyleClass().add(Styles.TABS_CLASSIC);
    mainTabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);

    // Create tabs
    Tab resourceDataTab = createResourceDataTab(resource);
    Tab metadataTab = createMetadataTab(resource);
    Tab provenanceTab = createProvenanceTab(resource);
    Tab operationsTab = createOperationsTab(resource);

    mainTabPane.getTabs().addAll(resourceDataTab, metadataTab, provenanceTab, operationsTab);

    VBox.setVgrow(mainTabPane, Priority.ALWAYS);
    mainContainer.getChildren().add(mainTabPane);

    return mainContainer;
  }

  /** Creates the header section of the editor */
  private HBox createHeaderSection() {
    HBox headerBox = new HBox(10);
    headerBox.setPadding(new Insets(10));
    headerBox.setAlignment(Pos.CENTER_LEFT);

    // Logo/icon
    FontIcon icon = new FontIcon(Material2AL.DESCRIPTION);
    icon.setIconSize(32);
    icon.setIconColor(Color.DARKBLUE);

    // Title and description
    VBox titleBox = new VBox(5);
    Label titleLabel = new Label("k.LAB Resource Editor");
    titleLabel.setFont(Font.font("System", FontWeight.BOLD, 16));

    Label subtitleLabel =
        new Label(
            "Define all the properties of a resource, its geometry and its provenance information");
    subtitleLabel.setFont(Font.font("System", 12));

    Label statusLabel = new Label("LOCAL, UNPUBLISHED");
    statusLabel.setFont(Font.font("System", FontWeight.BOLD, 10));
    statusLabel.setTextFill(Color.DARKGREEN);

    titleBox.getChildren().addAll(titleLabel, subtitleLabel, statusLabel);

    headerBox.getChildren().addAll(icon, titleBox);

    return headerBox;
  }

  /** Creates the Resource Data tab */
  private Tab createResourceDataTab(Resource resource) {
    Tab tab = new Tab("Resource Data");

    VBox container = new VBox(10);
    container.setPadding(new Insets(10));

    // Resource data section
    GridPane resourceDataGrid = new GridPane();
    resourceDataGrid.setHgap(10);
    resourceDataGrid.setVgap(10);
    resourceDataGrid.setPadding(new Insets(10));

    // URN
    Label urnTitleLabel = new Label("URN:");
    urnTitleLabel.setFont(Font.font("System", FontWeight.BOLD, 12));
    urnLabel = new Label(resource != null ? resource.toString() : "");
    urnLabel.setTextFill(Color.DARKGREEN);
    urnLabel.setFont(Font.font("System", FontWeight.BOLD, 12));

    // Local name
    Label localNameTitleLabel = new Label("Local name:");
    localNameTitleLabel.setFont(Font.font("System", FontWeight.BOLD, 12));
    localNameLabel = new Label(resource != null ? resource.toString() : "");
    localNameLabel.setTextFill(Color.DARKCYAN);
    localNameLabel.setFont(Font.font("System", FontWeight.BOLD, 12));

    resourceDataGrid.add(urnTitleLabel, 0, 0);
    resourceDataGrid.add(urnLabel, 1, 0);
    resourceDataGrid.add(localNameTitleLabel, 2, 0);
    resourceDataGrid.add(localNameLabel, 3, 0);

    // Wrap in a titled pane
    TitledPane resourceDataPane = new TitledPane("Resource Data", resourceDataGrid);
    resourceDataPane.setCollapsible(false);

    // Geometry section with split pane
    SplitPane geometrySplitPane = new SplitPane();

    // Left side - Geometry tabs
    TabPane geometryTabPane = new TabPane();
    geometryTabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);

    // Space tab
    Tab spaceTab = new Tab("Space");
    worldMapView = new WorldMapView();
    spaceTab.setContent(worldMapView);

    // Time tab
    Tab timeTab = new Tab("Time");
    timeEditorView = new TimeEditorView();
    timeTab.setContent(timeEditorView);

    geometryTabPane.getTabs().addAll(spaceTab, timeTab);

    // Right side - Attributes tabs
    VBox attributesContainer = new VBox(10);
    attributesContainer.setPadding(new Insets(10));

    // Publishable checkbox
    HBox publishableBox = new HBox(10);
    publishableBox.setAlignment(Pos.CENTER_LEFT);

    isPublishableCheckbox = new CheckBox("Publishable");
    isPublishableCheckbox.setSelected(true);

    Label whyNotLabel = new Label("Why not:");
    whyNotLabel.setDisable(true);

    unpublishableReasonField = new TextField();
    unpublishableReasonField.setDisable(true);
    HBox.setHgrow(unpublishableReasonField, Priority.ALWAYS);

    isPublishableCheckbox
        .selectedProperty()
        .addListener(
            (obs, oldVal, newVal) -> {
              whyNotLabel.setDisable(newVal);
              unpublishableReasonField.setDisable(newVal);
            });

    publishableBox
        .getChildren()
        .addAll(isPublishableCheckbox, whyNotLabel, unpublishableReasonField);

    // Attributes TabPane
    TabPane attributesTabPane = new TabPane();
    attributesTabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);

    // Attributes tab
    Tab attributesTab = new Tab("Attributes");
    attributesTable = createAttributesTable();
    attributesTab.setContent(attributesTable);

    // Inputs tab
    Tab inputsTab = new Tab("Inputs");
    inputsTable = createAttributesTable();
    inputsTab.setContent(inputsTable);

    // Outputs tab
    Tab outputsTab = new Tab("Outputs");
    outputsTable = createAttributesTable();
    outputsTab.setContent(outputsTable);

    attributesTabPane.getTabs().addAll(attributesTab, inputsTab, outputsTab);
    VBox.setVgrow(attributesTabPane, Priority.ALWAYS);

    // Operations section
    HBox operationsBox = new HBox(10);
    operationsBox.setAlignment(Pos.CENTER_LEFT);

    Label operationsLabel = new Label("Operations:");

    operationsComboBox = new ComboBox<>();
    operationsComboBox.getItems().addAll("VALIDATE", "PUBLISH", "EXPORT", "IMPORT");
    HBox.setHgrow(operationsComboBox, Priority.ALWAYS);

    executeOperationButton = new Button("Execute");
    executeOperationButton.setDisable(true);

    operationsComboBox
        .valueProperty()
        .addListener(
            (obs, oldVal, newVal) -> {
              executeOperationButton.setDisable(newVal == null);
            });

    operationsBox.getChildren().addAll(operationsLabel, operationsComboBox, executeOperationButton);

    attributesContainer.getChildren().addAll(publishableBox, attributesTabPane, operationsBox);

    geometrySplitPane.getItems().addAll(geometryTabPane, attributesContainer);
    geometrySplitPane.setDividerPositions(0.4);

    // Geometry definition
    HBox geometryDefinitionBox = new HBox(10);
    geometryDefinitionBox.setAlignment(Pos.CENTER_LEFT);

    Button copyGeometryButton = new Button();
    copyGeometryButton.setGraphic(new FontIcon(Material2AL.CONTENT_COPY));
    copyGeometryButton.setTooltip(new Tooltip("Copy geometry definition to clipboard"));

    geometryDefinitionLabel = new Label("No geometry defined");
    geometryDefinitionLabel.setTextFill(Color.DARKGRAY);
    geometryDefinitionLabel.setFont(Font.font("System", FontPosture.ITALIC, 10));
    HBox.setHgrow(geometryDefinitionLabel, Priority.ALWAYS);

    geometryDefinitionBox.getChildren().addAll(copyGeometryButton, geometryDefinitionLabel);

    // Adapter parameters section
    TitledPane adapterParametersPane =
        new TitledPane("Adapter Parameters", createAdapterParametersTree());
    VBox.setVgrow(adapterParametersPane, Priority.ALWAYS);

    // Add all components to the container
    container
        .getChildren()
        .addAll(resourceDataPane, geometrySplitPane, geometryDefinitionBox, adapterParametersPane);

    tab.setContent(container);
    return tab;
  }

  /** Creates the Metadata tab */
  private Tab createMetadataTab(Resource resource) {
    Tab tab = new Tab("Metadata");

    GridPane grid = new GridPane();
    grid.setHgap(10);
    grid.setVgap(10);
    grid.setPadding(new Insets(20));

    // Title
    Label titleLabel = new Label("Title:");
    TextField titleField = new TextField();
    titleField.setPromptText("Resource title");
    GridPane.setHgrow(titleField, Priority.ALWAYS);

    // Description
    Label descriptionLabel = new Label("Description:");
    descriptionField = new TextArea();
    descriptionField.setPromptText("Resource description");
    descriptionField.setPrefRowCount(3);
    GridPane.setHgrow(descriptionField, Priority.ALWAYS);

    // Keywords
    Label keywordsLabel = new Label("Keywords:");
    TextField keywordsField = new TextField();
    keywordsField.setPromptText("Comma-separated keywords");
    GridPane.setHgrow(keywordsField, Priority.ALWAYS);

    // Version
    Label versionLabel = new Label("Version:");
    TextField versionField = new TextField();
    versionField.setPromptText("1.0.0");

    // License
    Label licenseLabel = new Label("License:");
    ComboBox<String> licenseComboBox = new ComboBox<>();
    licenseComboBox
        .getItems()
        .addAll("CC0", "CC BY", "CC BY-SA", "CC BY-NC", "CC BY-NC-SA", "CC BY-ND", "CC BY-NC-ND");
    licenseComboBox.setPromptText("Select a license");
    GridPane.setHgrow(licenseComboBox, Priority.ALWAYS);

    // Add to grid
    grid.add(titleLabel, 0, 0);
    grid.add(titleField, 1, 0);

    grid.add(descriptionLabel, 0, 1);
    grid.add(descriptionField, 1, 1);

    grid.add(keywordsLabel, 0, 2);
    grid.add(keywordsField, 1, 2);

    grid.add(versionLabel, 0, 3);
    grid.add(versionField, 1, 3);

    grid.add(licenseLabel, 0, 4);
    grid.add(licenseComboBox, 1, 4);

    ScrollPane scrollPane = new ScrollPane(grid);
    scrollPane.setFitToWidth(true);

    tab.setContent(scrollPane);
    return tab;
  }

  /** Creates the Provenance tab */
  private Tab createProvenanceTab(Resource resource) {
    Tab tab = new Tab("Provenance");

    GridPane grid = new GridPane();
    grid.setHgap(10);
    grid.setVgap(10);
    grid.setPadding(new Insets(20));

    // Creator
    Label creatorLabel = new Label("Creator:");
    TextField creatorField = new TextField();
    creatorField.setPromptText("Creator name");
    GridPane.setHgrow(creatorField, Priority.ALWAYS);

    // Organization
    Label organizationLabel = new Label("Organization:");
    TextField organizationField = new TextField();
    organizationField.setPromptText("Organization name");
    GridPane.setHgrow(organizationField, Priority.ALWAYS);

    // URL
    Label urlLabel = new Label("URL:");
    TextField urlField = new TextField();
    urlField.setPromptText("https://example.com");
    GridPane.setHgrow(urlField, Priority.ALWAYS);

    // Creation date
    Label creationDateLabel = new Label("Creation date:");
    DatePicker creationDatePicker = new DatePicker();
    GridPane.setHgrow(creationDatePicker, Priority.ALWAYS);

    // Add to grid
    grid.add(creatorLabel, 0, 0);
    grid.add(creatorField, 1, 0);

    grid.add(organizationLabel, 0, 1);
    grid.add(organizationField, 1, 1);

    grid.add(urlLabel, 0, 2);
    grid.add(urlField, 1, 2);

    grid.add(creationDateLabel, 0, 3);
    grid.add(creationDatePicker, 1, 3);

    ScrollPane scrollPane = new ScrollPane(grid);
    scrollPane.setFitToWidth(true);

    tab.setContent(scrollPane);
    return tab;
  }

  /** Creates the Operations tab */
  private Tab createOperationsTab(Resource resource) {
    Tab tab = new Tab("Operations");

    VBox container = new VBox(20);
    container.setPadding(new Insets(20));

    // Operations list
    TitledPane operationsPane = new TitledPane("Available Operations", createOperationsList());
    VBox.setVgrow(operationsPane, Priority.ALWAYS);

    // Operation parameters
    TitledPane parametersPane =
        new TitledPane("Operation Parameters", createOperationParametersPane());
    VBox.setVgrow(parametersPane, Priority.ALWAYS);

    // Execution controls
    HBox executionBox = new HBox(10);
    executionBox.setAlignment(Pos.CENTER_RIGHT);

    Button executeButton = new Button("Execute Operation");
    executeButton.setGraphic(new FontIcon(Material2MZ.PLAY_ARROW));

    executionBox.getChildren().add(executeButton);

    container.getChildren().addAll(operationsPane, parametersPane, executionBox);

    tab.setContent(container);
    return tab;
  }

  /** Creates a table for attributes */
  private TableView<AttributeItem> createAttributesTable() {
    TableView<AttributeItem> table = new TableView<>();

    TableColumn<AttributeItem, String> nameColumn = new TableColumn<>("Name");
    nameColumn.setCellValueFactory(cellData -> cellData.getValue().nameProperty());
    nameColumn.setPrefWidth(150);

    TableColumn<AttributeItem, String> typeColumn = new TableColumn<>("Type");
    typeColumn.setCellValueFactory(cellData -> cellData.getValue().typeProperty());
    typeColumn.setPrefWidth(150);

    TableColumn<AttributeItem, String> valueColumn = new TableColumn<>("Value/Required");
    valueColumn.setCellValueFactory(cellData -> cellData.getValue().valueProperty());
    valueColumn.setPrefWidth(150);

    table.getColumns().addAll(nameColumn, typeColumn, valueColumn);

    return table;
  }

  /** Creates the adapter parameters tree */
  private TreeView<ParameterItem> createAdapterParametersTree() {
    adapterParametersTree = new TreeView<>();

    TreeItem<ParameterItem> root = new TreeItem<>(new ParameterItem("Parameters", "Category", ""));
    root.setExpanded(true);

    // Example parameters
    TreeItem<ParameterItem> dataItem = new TreeItem<>(new ParameterItem("dataUrl", "URL", ""));
    TreeItem<ParameterItem> formatItem = new TreeItem<>(new ParameterItem("format", "String", ""));
    TreeItem<ParameterItem> noDataItem = new TreeItem<>(new ParameterItem("nodata", "Number", ""));

    root.getChildren().addAll(dataItem, formatItem, noDataItem);

    adapterParametersTree.setRoot(root);
    adapterParametersTree.setShowRoot(false);

    // Set up columns
    TreeTableColumn<ParameterItem, String> nameColumn = new TreeTableColumn<>("Parameter");
    nameColumn.setPrefWidth(180);

    TreeTableColumn<ParameterItem, String> typeColumn = new TreeTableColumn<>("Type");
    typeColumn.setPrefWidth(100);

    TreeTableColumn<ParameterItem, String> valueColumn = new TreeTableColumn<>("Value");
    valueColumn.setPrefWidth(200);

    return adapterParametersTree;
  }

  /** Creates the operations list */
  private ListView<String> createOperationsList() {
    ListView<String> operationsList = new ListView<>();
    operationsList.getItems().addAll("VALIDATE", "PUBLISH", "EXPORT", "IMPORT");

    return operationsList;
  }

  /** Creates the operation parameters pane */
  private GridPane createOperationParametersPane() {
    GridPane grid = new GridPane();
    grid.setHgap(10);
    grid.setVgap(10);
    grid.setPadding(new Insets(10));

    // Example parameters
    Label param1Label = new Label("Parameter 1:");
    TextField param1Field = new TextField();
    GridPane.setHgrow(param1Field, Priority.ALWAYS);

    Label param2Label = new Label("Parameter 2:");
    ComboBox<String> param2ComboBox = new ComboBox<>();
    param2ComboBox.getItems().addAll("Option 1", "Option 2", "Option 3");
    GridPane.setHgrow(param2ComboBox, Priority.ALWAYS);

    grid.add(param1Label, 0, 0);
    grid.add(param1Field, 1, 0);
    grid.add(param2Label, 0, 1);
    grid.add(param2ComboBox, 1, 1);

    return grid;
  }

  /** Model class for attribute items */
  public static class AttributeItem {
    private final javafx.beans.property.StringProperty name;
    private final javafx.beans.property.StringProperty type;
    private final javafx.beans.property.StringProperty value;

    public AttributeItem(String name, String type, String value) {
      this.name = new javafx.beans.property.SimpleStringProperty(name);
      this.type = new javafx.beans.property.SimpleStringProperty(type);
      this.value = new javafx.beans.property.SimpleStringProperty(value);
    }

    public javafx.beans.property.StringProperty nameProperty() {
      return name;
    }

    public javafx.beans.property.StringProperty typeProperty() {
      return type;
    }

    public javafx.beans.property.StringProperty valueProperty() {
      return value;
    }
  }

  /** Model class for parameter items */
  public static class ParameterItem {
    private final String name;
    private final String type;
    private final String value;

    public ParameterItem(String name, String type, String value) {
      this.name = name;
      this.type = type;
      this.value = value;
    }

    public String getName() {
      return name;
    }

    public String getType() {
      return type;
    }

    public String getValue() {
      return value;
    }

    @Override
    public String toString() {
      return name;
    }
  }

  /** Simple world map view placeholder */
  private class WorldMapView extends Pane {
    public WorldMapView() {
      setPrefSize(400, 300);
      setStyle("-fx-background-color: lightblue;");

      Label placeholder = new Label("World Map View");
      placeholder.setLayoutX(150);
      placeholder.setLayoutY(150);

      getChildren().add(placeholder);
    }
  }

  /** Simple time editor view placeholder */
  private class TimeEditorView extends VBox {
    public TimeEditorView() {
      setPadding(new Insets(10));
      setSpacing(10);

      Label titleLabel = new Label("Time Configuration");
      titleLabel.setFont(Font.font("System", FontWeight.BOLD, 14));

      ComboBox<String> timeTypeCombo = new ComboBox<>();
      timeTypeCombo.getItems().addAll("CONTINUOUS", "DISCRETE", "REGULAR", "CUSTOM");
      timeTypeCombo.setValue("CONTINUOUS");

      Label startLabel = new Label("Start time:");
      DatePicker startDatePicker = new DatePicker();

      Label endLabel = new Label("End time:");
      DatePicker endDatePicker = new DatePicker();

      Label stepLabel = new Label("Time step:");
      HBox stepBox = new HBox(5);
      TextField stepValueField = new TextField("1");
      stepValueField.setPrefWidth(50);
      ComboBox<String> stepUnitCombo = new ComboBox<>();
      stepUnitCombo.getItems().addAll("SECONDS", "MINUTES", "HOURS", "DAYS", "MONTHS", "YEARS");
      stepUnitCombo.setValue("DAYS");
      stepBox.getChildren().addAll(stepValueField, stepUnitCombo);

      getChildren()
          .addAll(
              titleLabel,
              timeTypeCombo,
              startLabel,
              startDatePicker,
              endLabel,
              endDatePicker,
              stepLabel,
              stepBox);
    }
  }
}
