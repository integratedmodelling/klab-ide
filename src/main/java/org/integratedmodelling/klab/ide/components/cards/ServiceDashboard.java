package org.integratedmodelling.klab.ide.components.cards;

import atlantafx.base.controls.Card;
import atlantafx.base.theme.Styles;
import java.io.File;
import java.time.Instant;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.StackedAreaChart;
import javafx.scene.chart.XYChart;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.util.Duration;
import javafx.util.StringConverter;
import org.integratedmodelling.common.utils.Utils;
import org.integratedmodelling.klab.api.authentication.CRUDOperation;
import org.integratedmodelling.klab.api.configuration.Configuration;
import org.integratedmodelling.klab.api.configuration.Setting;
import org.integratedmodelling.klab.api.knowledge.Urn;
import org.integratedmodelling.klab.api.services.KlabService;
import org.integratedmodelling.klab.api.services.resources.ResourceTransport;
import org.integratedmodelling.klab.api.services.runtime.Notification;
import org.integratedmodelling.klab.api.services.runtime.extension.Extensions;
import org.integratedmodelling.klab.ide.KlabIDEApplication;
import org.integratedmodelling.klab.ide.KlabIDEController;
import org.integratedmodelling.klab.ide.Theme;
import org.integratedmodelling.klab.ide.components.generic.CarouselBox;
import org.integratedmodelling.klab.ide.components.generic.IconButton;
import org.integratedmodelling.klab.ide.components.generic.IconLabel;
import org.integratedmodelling.klab.ide.components.generic.LogViewer;
import org.integratedmodelling.klab.ide.components.generic.UploadBox;
import org.integratedmodelling.klab.ide.components.generic.WaitButton;
import org.kordamp.ikonli.Ikon;
import org.kordamp.ikonli.evaicons.Evaicons;
import org.kordamp.ikonli.javafx.FontIcon;
import org.kordamp.ikonli.materialdesign.MaterialDesign;

/** Operational overview for a service selected in {@link ServiceViewComponent}. */
public class ServiceDashboard extends BaseAssetViewComponent {

  private static final int SAMPLE_LIMIT = 80;
  private static final Duration SAMPLE_INTERVAL = Duration.seconds(1);

  /** The horizontal carousel and its cards share this cross-axis size. */
  private static final double COMPONENT_CARD_HEIGHT = 250;

  private final KlabService service;
  private final CarouselBox components = new CarouselBox(Orientation.HORIZONTAL);
  private final XYChart.Series<Number, Number> loadSeries = new XYChart.Series<>();
  private final XYChart.Series<Number, Number> memoryUsedSeries = new XYChart.Series<>();
  private final XYChart.Series<Number, Number> memoryAvailableSeries = new XYChart.Series<>();
  private final NumberAxis loadSampleAxis = sampleAxis();
  private final NumberAxis memorySampleAxis = sampleAxis();
  private final Label statusLabel = new Label("Waiting for service status");
  private final StatusUpdateQueue statusUpdates =
      new StatusUpdateQueue(ServiceDashboard::dispatchOnFxThread, this::updateCharts);
  private final Consumer<KlabService.ServiceStatus> statusListener = statusUpdates::accept;
  private final Timeline statusSampler = new Timeline();
  private int sample;
  private boolean monitoring;
  private StackPane display;

  public ServiceDashboard(KlabService service, String title, boolean initialize) {
    super(Type.Object, title, false);
    this.service = service;
    if (initialize) createContent();
  }

  @Override
  protected Node createContent() {
    getChildren().clear();
    setPadding(new Insets(8));
    setSpacing(12);

    var links = new HBox(10);
    links
        .getChildren()
        .addAll(
            link("Public Capabilities", MaterialDesign.MDI_FILE_TREE, "/public/capabilities"),
            link("Status", Evaicons.ACTIVITY, "/public/status"),
            link("API documentation", MaterialDesign.MDI_BOOK_OPEN_PAGE_VARIANT, "/api.html"));
    links.setAlignment(Pos.CENTER_LEFT);

    var navigation = new HBox(0);
    navigation.setAlignment(Pos.CENTER_RIGHT);
    var viewGroup = new ToggleGroup();
    var dashboardButton = viewButton(MaterialDesign.MDI_VIEW_DASHBOARD, "Dashboard");
    var importButton = viewButton(MaterialDesign.MDI_IMPORT, "Import components");
    var capabilities = service.capabilities(KlabIDEController.instance().user());
    var settingsButton =
        hasAdministerPermission(capabilities)
            ? viewButton(MaterialDesign.MDI_SETTINGS, "Service settings")
            : null;
    dashboardButton.setToggleGroup(viewGroup);
    importButton.setToggleGroup(viewGroup);
    if (settingsButton != null) settingsButton.setToggleGroup(viewGroup);
    dashboardButton.setSelected(true);
    navigation.getChildren().addAll(dashboardButton, importButton);
    if (settingsButton != null) navigation.getChildren().add(settingsButton);
    ToggleButton logsButton = null;
    if (isLocalService()) {
      logsButton = viewButton(MaterialDesign.MDI_FILE_DOCUMENT, "Service logs");
      logsButton.setToggleGroup(viewGroup);
      navigation.getChildren().add(logsButton);
    }
    HBox.setHgrow(links, Priority.ALWAYS);
    var linkBar = new HBox(10, links, navigation);
    linkBar.setAlignment(Pos.CENTER_LEFT);

    var status = new HBox(8, new FontIcon(Evaicons.ACTIVITY), statusLabel);
    status.setAlignment(Pos.CENTER_LEFT);
    statusLabel.getStyleClass().addAll(Styles.TEXT_SMALL, Styles.TEXT_MUTED);

    var memoryChart = memoryChart();
    var charts = new HBox(10, chart("Load (%)", loadSeries, loadSampleAxis, 100), memoryChart);
    HBox.setHgrow(charts.getChildren().get(0), Priority.ALWAYS);
    HBox.setHgrow(charts.getChildren().get(1), Priority.ALWAYS);

    var componentHeading = new Label("Components");
    componentHeading.getStyleClass().add(Styles.TITLE_3);
    // Keep the carousel at the same fixed height as its cards. CarouselBox resizes its item
    // wrappers to its own height, so allowing either side to shrink would clip the card.
    components.setMinHeight(COMPONENT_CARD_HEIGHT + 40);
    components.setPrefHeight(COMPONENT_CARD_HEIGHT + 40);
    components.setMaxHeight(COMPONENT_CARD_HEIGHT + 40);
    components.setMaxWidth(Double.MAX_VALUE);
    var dashboard = new VBox(10, status, charts, componentHeading, components);
    dashboard.setPadding(new Insets(4, 0, 0, 0));
    VBox.setVgrow(dashboard, Priority.ALWAYS);
    display = new StackPane(dashboard);
    display.setMaxHeight(Double.MAX_VALUE);
    VBox.setVgrow(display, Priority.ALWAYS);
    getChildren().addAll(linkBar, display);
    VBox.setVgrow(charts, Priority.NEVER);

    statusSampler
        .getKeyFrames()
        .setAll(new KeyFrame(SAMPLE_INTERVAL, event -> sampleCurrentStatus()));
    statusSampler.setCycleCount(Timeline.INDEFINITE);
    sceneProperty()
        .addListener(
            (observable, oldScene, newScene) -> {
              if (newScene == null) stopMonitoring();
              else startMonitoring();
            });
    if (getScene() != null) startMonitoring();
    populateComponents();

    dashboardButton.setOnAction(e -> display.getChildren().setAll(dashboard));
    importButton.setOnAction(e -> display.getChildren().setAll(importView()));
    if (settingsButton != null) {
      settingsButton.setOnAction(e -> display.getChildren().setAll(settingsView()));
    }
    if (logsButton != null) logsButton.setOnAction(e -> display.getChildren().setAll(logView()));
    return this;
  }

  private ToggleButton viewButton(org.kordamp.ikonli.Ikon icon, String label) {
    var button = new ToggleButton("", new FontIcon(icon));
    button.setTooltip(new Tooltip(label));
    button.getStyleClass().add(Styles.FLAT);
    button.setFocusTraversable(false);
    return button;
  }

  private boolean isLocalService() {
    return service.isLocal();
  }

  private Node settingsView() {
    var page =
        switch (service.status().getServiceType()) {
          case REASONER -> Setting.Page.REASONER;
          case RESOURCES -> Setting.Page.RESOURCES;
          case RESOLVER -> Setting.Page.RESOLVER;
          case RUNTIME -> Setting.Page.RUNTIME;
          default -> null;
        };
    var settings =
        new SettingsPageViewComponent(page, service.settings()) {
          @Override
          protected void onChangedSetting(Setting setting, Object newValue) {
            service.settings().set(setting, newValue);
          }

          @Override
          protected void onOperationResult(
              Setting setting, Map<String, Object> request, Map<String, Object> result) {
            KlabIDEController.instance()
                .handleNotification(
                    Notification.create(
                        "Service operation " + setting + " completed: " + result,
                        Notification.Level.Info));
          }
        };
    var scroll = new ScrollPane(settings);
    scroll.setFitToWidth(true);
    scroll.setMaxHeight(Double.MAX_VALUE);
    return scroll;
  }

  static boolean hasAdministerPermission(KlabService.ServiceCapabilities capabilities) {
    return capabilities != null
        && capabilities.getPermissions() != null
        && capabilities.getPermissions().contains(CRUDOperation.ADMINISTER);
  }

  private Node logView() {
    // getInstance() only returns running services. The logs view must remain
    // available for a selected local service even while its process is stopped.
    var instance =
        KlabIDEController.instance().engine().getServiceInstance(service.status().getServiceType());
    if (instance == null) return new Label("No local service instance is available.");
    var logFile =
        instance
            .getConfigurationPath()
            .resolve(
                "logs"
                    + File.separator
                    + instance.getProduct().getType().relativeConfigurationPath()
                    + ".log")
            .toFile();
    var viewer =
        new LogViewer(
            logFile.toPath(),
            EnumSet.of(LogViewer.Column.TIME, LogViewer.Column.LEVEL, LogViewer.Column.MESSAGE));
    return viewer;
  }

  private Node importView() {
    var parameterForm = new VBox(2);
    var importPane = new VBox(10);
    importPane.setPadding(new Insets(10));
    var schemaSelector = new ComboBox<String>();
    schemaSelector.setPromptText("Select Import Schema");
    var schemaKey = new HashMap<String, ResourceTransport.Schema>();
    var schemata = service.capabilities(KlabIDEController.instance().user()).getImportSchemata();
    for (var schemaName : schemata.keySet()) {
      for (var schema : schemata.get(schemaName)) {
        var description =
            schema.getProperties().isEmpty()
                ? " (" + Utils.Strings.join(schema.getMediaTypes(), ", ") + ")"
                : " (parameters)";
        var name = schemaName + description;
        schemaSelector.getItems().add(name);
        schemaKey.put(name, schema);
      }
    }
    schemaSelector.setOnAction(
        e -> updateImportForm(schemaKey.get(schemaSelector.getValue()), parameterForm));
    var formScroll = new ScrollPane(parameterForm);
    formScroll.setFitToWidth(true);
    VBox.setVgrow(formScroll, Priority.ALWAYS);
    importPane.getChildren().addAll(schemaSelector, formScroll);
    return importPane;
  }

  private void updateImportForm(ResourceTransport.Schema schema, VBox parameterForm) {
    if (schema == null) return;
    parameterForm.getChildren().clear();
    Map<String, Object> userInput = new HashMap<>();
    AtomicReference<File> file = new AtomicReference<>();
    if (schema.getType() == ResourceTransport.Schema.Type.PROPERTIES) {
      for (var parameter : schema.getProperties().entrySet()) {
        var label = new Label(parameter.getKey());
        label.setStyle(
            parameter.getValue().optional()
                ? "-fx-font-weight: bold;"
                : "-fx-text-fill: #dd0000; -fx-font-weight: bold;");
        var input = new javafx.scene.control.TextField();
        input.setPromptText(parameter.getValue().defaultValue());
        input
            .textProperty()
            .addListener((obs, oldValue, newValue) -> userInput.put(parameter.getKey(), newValue));
        parameterForm.getChildren().addAll(label, input);
      }
    } else {
      var upload =
          new UploadBox(
              Configuration.INSTANCE.getTemporaryDataPath().toString(),
              "Drop file or URL to upload",
              file::set,
              (message, throwable) ->
                  KlabIDEController.instance()
                      .handleNotifications(
                          List.of(Notification.error("Upload error: " + message))));
      parameterForm.getChildren().add(upload);
    }
    var submit = new WaitButton("Submit");
    submit.setOnActionAsync(
        () -> {
          var asset = file.get() == null ? schema.asset(userInput) : schema.asset(file.get());
          if (asset.isEmpty()) {
            KlabIDEController.instance()
                .handleNotifications(
                    List.of(Notification.error("Import failed: specifications are incomplete")));
            return CompletableFuture.completedFuture(false);
          }
          return service
              .importAsset(schema, asset, Urn.UNDEFINED_URN, KlabIDEController.instance().user())
              .thenApply(
                  resourceSet -> {
                    KlabIDEController.instance()
                        .handleNotifications(resourceSet.getNotifications());
                    return true;
                  })
              .exceptionally(
                  throwable -> {
                    KlabIDEController.instance().handleNotification(Notification.error(throwable));
                    return false;
                  });
        });
    var buttons = new HBox(10, submit);
    buttons.setAlignment(Pos.CENTER_RIGHT);
    parameterForm.getChildren().add(buttons);
  }

  private void startMonitoring() {
    if (monitoring) return;
    monitoring = true;
    KlabIDEController.instance().addServiceStatusListener(service, statusListener);
    statusSampler.play();
    sampleCurrentStatus();
  }

  private void stopMonitoring() {
    if (!monitoring) return;
    monitoring = false;
    statusSampler.stop();
    KlabIDEController.instance().removeServiceStatusListener(service, statusListener);
  }

  private void sampleCurrentStatus() {
    try {
      // status() is contractually cheap; service clients return the monitor's
      // latest cached snapshot, so this does not add another network poller.
      statusUpdates.accept(service.status());
    } catch (RuntimeException ignored) {
      // Retain the most recent sample while the service is temporarily unavailable.
    }
  }

  private Hyperlink link(String text, org.kordamp.ikonli.Ikon icon, String path) {
    var link = new Hyperlink(text, new IconLabel(icon, 15, Color.GRAY));
    link.setTooltip(new Tooltip("Open " + text));
    link.setOnAction(
        e -> KlabIDEApplication.instance().getHostServices().showDocument(service.getUrl() + path));
    return link;
  }

  private LineChart<Number, Number> chart(
      String title, XYChart.Series<Number, Number> series, NumberAxis sampleAxis, double upper) {
    var y = new NumberAxis(0, upper, upper / 2);
    y.setLabel(title);
    var chart = new LineChart<Number, Number>(sampleAxis, y);
    chart.setTitle(title);
    chart.setAnimated(false);
    chart.setLegendVisible(false);
    chart.setCreateSymbols(true);
    chart.setPrefHeight(180);
    chart.getData().add(series);
    return chart;
  }

  private static NumberAxis sampleAxis() {
    var axis = new NumberAxis(0, SAMPLE_LIMIT - 1, 5);
    axis.setAutoRanging(false);
    axis.setForceZeroInRange(false);
    axis.setLabel("sample");
    return axis;
  }

  private StackedAreaChart<Number, Number> memoryChart() {
    var y = new NumberAxis();
    y.setLabel("memory");
    y.setTickLabelFormatter(
        new StringConverter<>() {
          @Override
          public String toString(Number value) {
            return String.format("%.0f MB", value.doubleValue());
          }

          @Override
          public Number fromString(String value) {
            return Double.parseDouble(value.replace(" MB", ""));
          }
        });
    var chart = new StackedAreaChart<>(memorySampleAxis, y);
    chart.setTitle("Memory");
    chart.setAnimated(false);
    chart.setCreateSymbols(false);
    chart.setLegendVisible(true);
    memoryUsedSeries.setName("Used");
    memoryAvailableSeries.setName("Available");
    // Series order is significant in a StackedAreaChart: used is the bottom
    // area and available is stacked above it.
    chart.getData().addAll(memoryUsedSeries, memoryAvailableSeries);
    chart.setPrefHeight(180);
    return chart;
  }

  private static void dispatchOnFxThread(Runnable runnable) {
    if (Platform.isFxApplicationThread()) runnable.run();
    else Platform.runLater(runnable);
  }

  private void updateCharts(StatusSample status) {
    var currentSample = sample++;
    addSample(loadSeries, currentSample, status.loadPercentage());
    addSample(memoryUsedSeries, currentSample, status.memoryUsedMb());
    addSample(memoryAvailableSeries, currentSample, status.memoryAvailableMb());
    var window = SampleWindow.endingAt(currentSample, SAMPLE_LIMIT);
    setWindow(loadSampleAxis, window);
    setWindow(memorySampleAxis, window);

    statusLabel.setText(
        (status.operational() ? "Operational" : "Unavailable")
            + " · health "
            + percentage(status.healthPercentage())
            + " · load "
            + percentage(status.loadPercentage()));
  }

  private void addSample(XYChart.Series<Number, Number> series, int x, double value) {
    series.getData().add(new XYChart.Data<>(x, value));
    while (series.getData().size() > SAMPLE_LIMIT) series.getData().remove(0);
  }

  private static void setWindow(NumberAxis axis, SampleWindow window) {
    axis.setLowerBound(window.lowerBound());
    axis.setUpperBound(window.upperBound());
  }

  private static double bytesToMb(long bytes) {
    return bytes < 0 ? 0 : bytes / (1024.0 * 1024.0);
  }

  private static String percentage(double value) {
    return value < 0 ? "unknown" : String.format("%.1f%%", value);
  }

  /** A fixed-width sample range that advances only after the initial window is full. */
  record SampleWindow(int lowerBound, int upperBound) {

    static SampleWindow endingAt(int currentSample, int size) {
      if (currentSample < 0)
        throw new IllegalArgumentException("Sample index must be non-negative");
      if (size < 2) throw new IllegalArgumentException("Window size must be at least two");
      var lowerBound = Math.max(0, currentSample - size + 1);
      return new SampleWindow(lowerBound, lowerBound + size - 1);
    }
  }

  /** Immutable notification snapshot; safe to transfer from service threads to JavaFX. */
  record StatusSample(
      boolean operational,
      double healthPercentage,
      double loadPercentage,
      double memoryUsedMb,
      double memoryAvailableMb) {

    static StatusSample from(KlabService.ServiceStatus status) {
      var load = status.getLoadMilliPercentage();
      return new StatusSample(
          status.isOperational(),
          status.getHealthPercentage(),
          Math.max(load / 10.0, 0),
          bytesToMb(status.getMemoryUsedBytes()),
          bytesToMb(status.getMemoryAvailableBytes()));
    }
  }

  /**
   * Lossless bridge from service notification threads to one UI dispatcher. The dispatcher is
   * injected so the concurrency contract can be tested without initializing JavaFX.
   */
  static final class StatusUpdateQueue {

    private final ConcurrentLinkedQueue<StatusSample> pending = new ConcurrentLinkedQueue<>();
    private final AtomicBoolean drainQueued = new AtomicBoolean();
    private final Consumer<Runnable> dispatcher;
    private final Consumer<StatusSample> sampleConsumer;

    StatusUpdateQueue(Consumer<Runnable> dispatcher, Consumer<StatusSample> sampleConsumer) {
      this.dispatcher = dispatcher;
      this.sampleConsumer = sampleConsumer;
    }

    void accept(KlabService.ServiceStatus status) {
      if (status == null) return;
      // Status implementations may be mutable. Capture values on the notifying
      // thread instead of retaining an object that can change before the drain.
      pending.add(StatusSample.from(status));
      scheduleDrain();
    }

    private void scheduleDrain() {
      if (drainQueued.compareAndSet(false, true)) dispatcher.accept(this::drain);
    }

    private void drain() {
      StatusSample status;
      while ((status = pending.poll()) != null) sampleConsumer.accept(status);
      drainQueued.set(false);
      if (!pending.isEmpty()) scheduleDrain();
    }
  }

  private void populateComponents() {
    try {
      var capabilities = service.capabilities(KlabIDEController.instance().user());
      var descriptors =
          capabilities == null
              ? List.<Extensions.ComponentDescriptor>of()
              : capabilities.getComponents(); // TODO remove the internal component
      if (descriptors == null || descriptors.isEmpty()) {
        components.addItem(new Label("No components advertised by this service."));
      } else {
        descriptors.stream()
            .filter(descriptor -> !Extensions.LOCAL_SERVICE_COMPONENT.equals(descriptor.id()))
            .forEach(descriptor -> components.addItem(componentCard(descriptor)));
      }
    } catch (RuntimeException e) {
      components.addItem(new Label("Component metadata is currently unavailable."));
    }
  }

  // TODO link click to component card in inspector for more information
  private Card componentCard(Extensions.ComponentDescriptor descriptor) {
    var state = componentCardState(descriptor, service.serviceId());
    var title = new Label(descriptor.id());
    title.getStyleClass().add(Styles.TEXT_BOLD);
    var version = new Label("Version " + descriptor.version());
    version.getStyleClass().addAll(Styles.TEXT_SMALL, Styles.TEXT_MUTED);
    var titleBox = new VBox(1, title, version);
    HBox.setHgrow(titleBox, Priority.ALWAYS);

    var status = new Label(state.updateStatusText());
    status.getStyleClass().addAll(Styles.TEXT_SMALL, Styles.TEXT_BOLD);
    status
        .getStyleClass()
        .add(
            switch (state.updateStatus()) {
              case UPDATE_AVAILABLE -> Styles.WARNING;
              case UP_TO_DATE -> Styles.SUCCESS;
              case UNKNOWN, NOT_UPDATEABLE -> Styles.TEXT_MUTED;
            });
    var statusIcon =
        switch (state.updateStatus()) {
          case UPDATE_AVAILABLE -> new IconLabel(Evaicons.DOWNLOAD, 14, "-color-warning-fg");
          case UP_TO_DATE -> new IconLabel(Evaicons.CHECKMARK_CIRCLE_2, 14, "-color-success-fg");
          case UNKNOWN -> new IconLabel(Evaicons.FLAG, 14, "-color-fg-muted");
          case NOT_UPDATEABLE -> new IconLabel(MaterialDesign.MDI_LOCK, 14, "-color-fg-muted");
        };
    var statusBox = new HBox(5, statusIcon, status);
    statusBox.setAlignment(Pos.CENTER_LEFT);

    var header =
        new HBox(
            8, new IconLabel(Theme.COMPONENT_ICON, 16, "-color-fg-default"), titleBox, statusBox);
    header.setAlignment(Pos.CENTER_LEFT);
    header.setPadding(new Insets(10, 12, 6, 12));

    var description =
        new Label(
            descriptor.description() == null
                ? "No description available."
                : descriptor.description());
    description.setWrapText(true);
    description.setPrefWidth(250);

    var provenance = new Label(state.provenanceText());
    provenance.getStyleClass().addAll(Styles.TEXT_SMALL, Styles.TEXT_MUTED);
    provenance.setWrapText(true);
    var source = new Label(state.sourceText());
    source.getStyleClass().addAll(Styles.TEXT_SMALL, Styles.TEXT_MUTED);
    source.setWrapText(true);
    source.setVisible(!state.sourceText().isBlank());
    source.setManaged(!state.sourceText().isBlank());

    var updated = new Label(state.latestVersionText());
    updated.getStyleClass().addAll(Styles.TEXT_SMALL, Styles.TEXT_MUTED);
    updated.setWrapText(true);
    var body = new VBox(7, description, provenance, source, updated);
    // Keep the body content clear of the card's lower border and the footer slot.
    body.setPadding(new Insets(4, 12, 12, 12));
    // Leave room for the header and footer within COMPONENT_CARD_HEIGHT. The description remains
    // wrapped, but the body no longer forces the card taller than the carousel viewport.
    body.setMinHeight(110);

    var update =
        actionButton(
            Evaicons.DOWNLOAD,
            "-color-success-fg",
            "Update component (action pending API support)",
            state.updateEnabled(),
            () -> updateComponent(descriptor));
    var remove =
        actionButton(
            Evaicons.TRASH,
            "-color-danger-fg",
            "Remove component (action pending API support)",
            state.removalEnabled(),
            () -> removeComponent(descriptor));
    var actions = new HBox(6, update, remove);
    actions.setAlignment(Pos.CENTER_LEFT);
    actions.setPadding(new Insets(6, 12, 9, 12));

    var card = new Card();
    card.setHeader(header);
    card.setBody(body);
    card.setFooter(actions);
    card.setMinWidth(330);
    card.setPrefWidth(360);
    card.setMinHeight(COMPONENT_CARD_HEIGHT);
    card.setPrefHeight(COMPONENT_CARD_HEIGHT);
    card.setMaxHeight(COMPONENT_CARD_HEIGHT);
    return card;
  }

  private IconButton actionButton(
      Ikon icon, String color, String tooltip, boolean enabled, Runnable action) {
    return new IconButton(icon, 20, color, color, false) {
      @Override
      protected void action() {
        action.run();
      }
    }.enabled(enabled).styleClass(Styles.ROUNDED).tooltip(tooltip);
  }

  static ComponentCardState componentCardState(
      Extensions.ComponentDescriptor descriptor, String currentServiceId) {
    var importType =
        descriptor.importType() == null
            ? Extensions.ComponentImportType.FILE
            : descriptor.importType();
    var updateStatus =
        descriptor.updateStatus() == null
            ? Extensions.ComponentUpdateStatus.UNKNOWN
            : descriptor.updateStatus();
    var hosted = Objects.equals(descriptor.sourceServiceId(), currentServiceId);
    var provenance =
        switch (importType) {
          case BUILT_IN -> "Built into this service";
          case FILE -> hosted ? "Hosted .kar import" : "Direct .kar import";
          case MAVEN -> hosted ? "Hosted Maven import" : "Maven import";
          case DEPENDENCY -> "Dependency imported from a Resources service";
        };
    var source =
        switch (importType) {
          case MAVEN -> nullToEmpty(descriptor.mavenCoordinates());
          case DEPENDENCY ->
              descriptor.sourceServiceId() == null
                  ? "Source service unknown"
                  : "Source: " + descriptor.sourceServiceId();
          default -> "";
        };
    var statusText =
        switch (updateStatus) {
          case UPDATE_AVAILABLE -> "Update available";
          case UP_TO_DATE -> "Up to date";
          case UNKNOWN -> "Status unknown";
          case NOT_UPDATEABLE -> "Not updateable";
        };
    var latestVersionText =
        descriptor.latestVersionTimestamp() > 0
            ? "Latest version: " + Instant.ofEpochMilli(descriptor.latestVersionTimestamp())
            : descriptor.timestamp() > 0
                ? "Installed: " + Instant.ofEpochMilli(descriptor.timestamp())
                : "Version timestamp unknown";
    return new ComponentCardState(
        importType,
        updateStatus,
        provenance,
        source,
        statusText,
        latestVersionText,
        updateStatus == Extensions.ComponentUpdateStatus.UPDATE_AVAILABLE,
        importType != Extensions.ComponentImportType.BUILT_IN);
  }

  private static String nullToEmpty(String value) {
    return value == null ? "" : value;
  }

  record ComponentCardState(
      Extensions.ComponentImportType importType,
      Extensions.ComponentUpdateStatus updateStatus,
      String provenanceText,
      String sourceText,
      String updateStatusText,
      String latestVersionText,
      boolean updateEnabled,
      boolean removalEnabled) {}

  /** API hook pending the finalized component synchronization contract. */
  protected void updateComponent(Extensions.ComponentDescriptor descriptor) {
    // TODO invoke the service component update API when it is finalized.
  }

  /** API hook pending the finalized component removal contract. */
  protected void removeComponent(Extensions.ComponentDescriptor descriptor) {
    // TODO invoke the service component removal API when it is finalized.
  }
}
