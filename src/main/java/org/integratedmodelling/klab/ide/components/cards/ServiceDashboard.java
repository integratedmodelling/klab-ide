package org.integratedmodelling.klab.ide.components.cards;

import atlantafx.base.controls.Card;
import atlantafx.base.theme.Styles;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
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
import javafx.scene.control.Button;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.util.Duration;
import javafx.util.StringConverter;
import org.integratedmodelling.klab.api.services.KlabService;
import org.integratedmodelling.klab.api.services.runtime.extension.Extensions;
import org.integratedmodelling.klab.ide.KlabIDEApplication;
import org.integratedmodelling.klab.ide.KlabIDEController;
import org.integratedmodelling.klab.ide.components.generic.CarouselBox;
import org.integratedmodelling.klab.ide.components.generic.IconLabel;
import org.kordamp.ikonli.evaicons.Evaicons;
import org.kordamp.ikonli.javafx.FontIcon;
import org.kordamp.ikonli.materialdesign.MaterialDesign;

/** Operational overview for a service selected in {@link ServiceViewComponent}. */
public class ServiceDashboard extends BaseAssetViewComponent {

  private static final int SAMPLE_LIMIT = 80;
  private static final Duration SAMPLE_INTERVAL = Duration.seconds(1);
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
            link("Capabilities", MaterialDesign.MDI_FILE_TREE, "/public/capabilities"),
            link("Status", Evaicons.ACTIVITY, "/public/status"),
            link("API documentation", MaterialDesign.MDI_BOOK_OPEN_PAGE_VARIANT, "/api.html"));
    links.setAlignment(Pos.CENTER_LEFT);

    var status = new HBox(8, new FontIcon(Evaicons.ACTIVITY), statusLabel);
    status.setAlignment(Pos.CENTER_LEFT);
    statusLabel.getStyleClass().addAll(Styles.TEXT_SMALL, Styles.TEXT_MUTED);

    var memoryChart = memoryChart();
    var charts = new HBox(10, chart("Load (%)", loadSeries, loadSampleAxis, 100), memoryChart);
    HBox.setHgrow(charts.getChildren().get(0), Priority.ALWAYS);
    HBox.setHgrow(charts.getChildren().get(1), Priority.ALWAYS);

    var componentHeading = new Label("Components");
    componentHeading.getStyleClass().add(Styles.TITLE_3);
    // CarouselBox uses its own height when it lays out item wrappers. Keep enough
    // room for the card body and navigation strips so the card is never clipped.
    components.setMinHeight(220);
    components.setPrefHeight(230);
    components.setMaxWidth(Double.MAX_VALUE);
    getChildren().addAll(links, status, charts, componentHeading, components);
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
    return this;
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
    chart.setCreateSymbols(true);
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
          load < 0 ? -1 : load / 10.0,
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
        descriptors.forEach(descriptor -> components.addItem(componentCard(descriptor)));
      }
    } catch (RuntimeException e) {
      components.addItem(new Label("Component metadata is currently unavailable."));
    }
  }

  // TODO link click to component card in inspector for more information
  private Card componentCard(Extensions.ComponentDescriptor descriptor) {
    var title = new Label(descriptor.id());
    title.getStyleClass().add(Styles.TEXT_BOLD);
    var version = new Label("Version " + descriptor.version());
    version.getStyleClass().addAll(Styles.TEXT_SMALL, Styles.TEXT_MUTED);
    var description =
        new Label(
            descriptor.description() == null
                ? "No description available."
                : descriptor.description());
    description.setWrapText(true);
    description.setPrefWidth(250);
    var updated =
        new Label(
            descriptor.timestamp() <= 0
                ? "Update time unknown"
                : "Updated " + Instant.ofEpochMilli(descriptor.timestamp()));
    updated.getStyleClass().addAll(Styles.TEXT_SMALL, Styles.TEXT_MUTED);
    var update = new Button("Update");
    update.setTooltip(new Tooltip("Request an on-demand component update"));
    update.setOnAction(e -> updateComponent(descriptor));
    var body = new VBox(7, version, description, updated, update);
    body.setMinHeight(145);
    var card = new Card();
    card.setHeader(new HBox(8, new FontIcon(MaterialDesign.MDI_PACKAGE_VARIANT), title));
    card.setBody(body);
    card.setMinWidth(330);
    card.setPrefWidth(340);
    return card;
  }

  /** API hook pending the finalized component synchronization contract. */
  protected void updateComponent(Extensions.ComponentDescriptor descriptor) {
    // TODO invoke the service component update API when it is finalized.
  }
}
