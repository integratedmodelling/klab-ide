package org.integratedmodelling.klab.ide.components.cards;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import javafx.beans.value.ChangeListener;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Label;
import javafx.scene.control.OverrunStyle;
import javafx.scene.control.Tooltip;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.util.Duration;
import org.integratedmodelling.klab.api.data.Histogram;

/** Compact card visualization for a k.LAB {@link Histogram}. */
public class HistogramCard extends BaseCard<Histogram> {

  private static final double DEFAULT_WIDTH = 230;
  private static final double DEFAULT_HEIGHT = 150;
  private static final DecimalFormat NUMBER_FORMAT =
      new DecimalFormat("0.###", DecimalFormatSymbols.getInstance(Locale.US));
  private static final Color[] PALETTE = {
    Color.web("#2f7f6f"),
    Color.web("#5b7fbd"),
    Color.web("#c27d38"),
    Color.web("#8a6bb8"),
    Color.web("#5f8f55"),
    Color.web("#b05d68"),
    Color.web("#4b91a8"),
    Color.web("#8f7c4d")
  };

  private final Options options;
  private Canvas canvas;
  private StackPane chartFrame;
  private Label totalLabel;
  private Label binsLabel;
  private Label missingLabel;
  private final Tooltip binTooltip = new Tooltip();
  private RenderState renderState = RenderState.empty();

  public HistogramCard(Histogram histogram) {
    this(histogram, new Options());
  }

  public HistogramCard(Histogram histogram, Orientation orientation) {
    this(histogram, new Options().orientation(orientation));
  }

  public HistogramCard(Histogram histogram, Options options) {
    this(histogram, options, false);
  }

  public HistogramCard(Histogram histogram, Options options, boolean extended) {
    super(histogram == null ? Histogram.empty() : histogram, null, extended, false);
    this.options = new Options(options);
    drawContent();
  }

  public Options getOptions() {
    return new Options(options);
  }

  public void setOrientation(Orientation orientation) {
    options.orientation(orientation);
    applyPreferredSize();
    redraw();
  }

  public void setShowCategories(boolean showCategories) {
    options.showCategories(showCategories);
    redraw();
  }

  public void setShowRanges(boolean showRanges) {
    options.showRanges(showRanges);
    redraw();
  }

  public void setCategoryColor(String category, Color color) {
    options.categoryColor(category, color);
    redraw();
  }

  public void setCategoryColor(String category, int red, int green, int blue) {
    options.categoryColor(category, red, green, blue);
    redraw();
  }

  @Override
  protected void drawContent() {
    getStyleClass().add("histogram-card");
    setPadding(new Insets(8));
    setMinSize(160, 110);
    setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
    applyPreferredSize();

    this.binTooltip.setShowDelay(Duration.millis(150));

    VBox content = new VBox(6);
    content.getStyleClass().add("histogram-card-content");
    content.setFillWidth(true);

    chartFrame = new StackPane();
    chartFrame.getStyleClass().add("histogram-card-plot");
    chartFrame.setMinHeight(70);
    chartFrame.setPrefHeight(options.orientation() == Orientation.VERTICAL ? 96 : 108);
    chartFrame.setMaxHeight(Double.MAX_VALUE);

    canvas = new Canvas();
    canvas.setManaged(false);
    canvas.setMouseTransparent(false);
    canvas.setOnMouseMoved(this::updateTooltip);
    canvas.setOnMouseExited(event -> binTooltip.setText(summaryText()));
    ChangeListener<Number> redraw =
        (obs, oldValue, newValue) -> {
          canvas.setWidth(Math.max(0, chartFrame.getWidth()));
          canvas.setHeight(Math.max(0, chartFrame.getHeight()));
          redraw();
        };
    chartFrame.widthProperty().addListener(redraw);
    chartFrame.heightProperty().addListener(redraw);
    chartFrame.getChildren().add(canvas);
    binTooltip.setText(summaryText());
    Tooltip.install(canvas, binTooltip);

    content.getChildren().addAll(createHeader(), chartFrame);
    VBox.setVgrow(chartFrame, Priority.ALWAYS);
    setCenter(content);
  }

  private void applyPreferredSize() {
    if (extended) {
      setPrefSize(Region.USE_COMPUTED_SIZE, Region.USE_COMPUTED_SIZE);
      return;
    }
    if (options.orientation() == Orientation.HORIZONTAL) {
      setPrefSize(DEFAULT_WIDTH + 40, DEFAULT_HEIGHT + 22);
    } else {
      setPrefSize(DEFAULT_WIDTH, DEFAULT_HEIGHT);
    }
  }

  private Node createHeader() {
    Label title = new Label("Histogram");
    title.getStyleClass().add("histogram-card-title");

    binsLabel = chip("");
    totalLabel = chip("");
    missingLabel = chip("");

    HBox spacer = new HBox();
    HBox.setHgrow(spacer, Priority.ALWAYS);

    HBox header = new HBox(5, title, spacer, binsLabel, totalLabel, missingLabel);
    header.setAlignment(Pos.CENTER_LEFT);
    header.setMinHeight(22);
    updateHeader(BinEntry.from(asset));
    return header;
  }

  private Label chip(String text) {
    Label label = new Label(text);
    label.getStyleClass().add("histogram-card-chip");
    label.setTextOverrun(OverrunStyle.ELLIPSIS);
    label.setMinWidth(Region.USE_PREF_SIZE);
    return label;
  }

  private void updateHeader(List<BinEntry> entries) {
    if (binsLabel == null) {
      return;
    }
    double total = entries.stream().mapToDouble(BinEntry::value).sum();
    binsLabel.setText(entries.size() + " bins");
    totalLabel.setText("n " + formatNumber(total));
    missingLabel.setVisible(asset.getMissingCount() > 0);
    missingLabel.setManaged(asset.getMissingCount() > 0);
    missingLabel.setText("missing " + formatNumber(asset.getMissingCount()));
  }

  private void redraw() {
    if (canvas == null) {
      return;
    }
    List<BinEntry> entries = BinEntry.from(asset);
    updateHeader(entries);

    GraphicsContext gc = canvas.getGraphicsContext2D();
    double width = canvas.getWidth();
    double height = canvas.getHeight();
    gc.clearRect(0, 0, width, height);
    renderState = RenderState.empty();
    if (width <= 2 || height <= 2) {
      return;
    }

    drawBackground(gc, width, height);
    if (asset.isEmpty() || entries.isEmpty()) {
      drawEmpty(gc, width, height);
      return;
    }

    if (options.orientation() == Orientation.HORIZONTAL) {
      drawHorizontal(gc, width, height, entries);
    } else {
      drawVertical(gc, width, height, entries);
    }
  }

  private void drawBackground(GraphicsContext gc, double width, double height) {
    gc.setFill(Color.web("#fbfcfd"));
    gc.fillRoundRect(0.5, 0.5, width - 1, height - 1, 6, 6);
    gc.setStroke(Color.web("#e6ebf0"));
    gc.setLineWidth(1);
    gc.strokeLine(0, height * 0.25, width, height * 0.25);
    gc.strokeLine(0, height * 0.5, width, height * 0.5);
    gc.strokeLine(0, height * 0.75, width, height * 0.75);
  }

  private void drawEmpty(GraphicsContext gc, double width, double height) {
    gc.setStroke(Color.web("#9aa4ad"));
    gc.setLineWidth(1.2);
    gc.setLineDashes(4, 4);
    gc.strokeLine(10, height / 2.0, width - 10, height / 2.0);
    gc.setLineDashes();
    gc.setFill(Color.web("#57606a"));
    gc.setFont(Font.font("System", 10));
    gc.fillText("empty histogram", 10, Math.max(18, height - 12));
  }

  private void drawVertical(
      GraphicsContext gc, double width, double height, List<BinEntry> source) {
    boolean drawLabels = shouldDrawLabels(source);
    double left = 6;
    double right = 6;
    double top = 8;
    double bottom = drawLabels ? 20 : 7;
    Rect plot =
        new Rect(left, top, Math.max(1, width - left - right), Math.max(1, height - top - bottom));
    List<BinEntry> entries = aggregate(source, Math.max(1, (int) Math.floor(plot.width() / 2.0)));
    renderState = new RenderState(Orientation.VERTICAL, plot, entries);

    double max = maxValue(entries);
    double gap = entries.size() <= 70 ? 1.0 : 0.0;
    double slot = plot.width() / entries.size();
    double barWidth = Math.max(1, slot - gap);

    for (int i = 0; i < entries.size(); i++) {
      BinEntry entry = entries.get(i);
      double ratio = entry.value() / max;
      double barHeight = Math.max(entry.value() > 0 ? 1 : 0, plot.height() * ratio);
      double x = plot.x() + i * slot + gap / 2.0;
      double y = plot.y() + plot.height() - barHeight;
      gc.setFill(colorFor(entry, i, entries.size()));
      gc.fillRect(x, y, barWidth, barHeight);
    }

    drawVerticalLabels(gc, entries, plot, height, drawLabels);
  }

  private void drawHorizontal(
      GraphicsContext gc, double width, double height, List<BinEntry> source) {
    boolean drawLabels = shouldDrawLabels(source);
    double left = drawLabels ? 56 : 7;
    double right = 8;
    double top = 6;
    double bottom = 6;
    Rect plot =
        new Rect(left, top, Math.max(1, width - left - right), Math.max(1, height - top - bottom));
    List<BinEntry> entries = aggregate(source, Math.max(1, (int) Math.floor(plot.height() / 2.0)));
    renderState = new RenderState(Orientation.HORIZONTAL, plot, entries);

    double max = maxValue(entries);
    double gap = entries.size() <= 50 ? 1.0 : 0.0;
    double slot = plot.height() / entries.size();
    double barHeight = Math.max(1, slot - gap);

    for (int i = 0; i < entries.size(); i++) {
      BinEntry entry = entries.get(i);
      double ratio = entry.value() / max;
      double barWidth = Math.max(entry.value() > 0 ? 1 : 0, plot.width() * ratio);
      double y = plot.y() + i * slot + gap / 2.0;
      gc.setFill(colorFor(entry, i, entries.size()));
      gc.fillRect(plot.x(), y, barWidth, barHeight);
    }

    drawHorizontalLabels(gc, entries, plot, drawLabels);
  }

  private void drawVerticalLabels(
      GraphicsContext gc, List<BinEntry> entries, Rect plot, double height, boolean drawLabels) {
    gc.setStroke(Color.web("#d0d7de"));
    gc.setLineWidth(1);
    gc.strokeLine(
        plot.x(),
        plot.y() + plot.height() + 0.5,
        plot.x() + plot.width(),
        plot.y() + plot.height() + 0.5);
    if (!drawLabels) {
      return;
    }

    gc.setFill(Color.web("#57606a"));
    gc.setFont(Font.font("System", 8.5));
    int step = labelStep(entries.size(), (int) Math.max(1, plot.width() / 42.0));
    double slot = plot.width() / entries.size();
    for (int i = 0; i < entries.size(); i += step) {
      String label = clipped(entries.get(i).label(options), 9);
      double x = plot.x() + i * slot + 1;
      gc.fillText(label, x, height - 6, Math.max(28, slot * step - 2));
    }
  }

  private void drawHorizontalLabels(
      GraphicsContext gc, List<BinEntry> entries, Rect plot, boolean drawLabels) {
    gc.setStroke(Color.web("#d0d7de"));
    gc.setLineWidth(1);
    gc.strokeLine(plot.x() - 0.5, plot.y(), plot.x() - 0.5, plot.y() + plot.height());
    if (!drawLabels) {
      return;
    }

    gc.setFill(Color.web("#57606a"));
    gc.setFont(Font.font("System", 8.5));
    int step = labelStep(entries.size(), (int) Math.max(1, plot.height() / 14.0));
    double slot = plot.height() / entries.size();
    for (int i = 0; i < entries.size(); i += step) {
      String label = clipped(entries.get(i).label(options), 12);
      double y = plot.y() + i * slot + Math.min(10, slot);
      gc.fillText(label, 5, y, Math.max(20, plot.x() - 9));
    }
  }

  private boolean shouldDrawLabels(List<BinEntry> entries) {
    return entries.stream().anyMatch(entry -> !entry.label(options).isBlank());
  }

  private void updateTooltip(MouseEvent event) {
    BinEntry entry = binAt(event.getX(), event.getY());
    binTooltip.setText(entry == null ? summaryText() : entry.tooltipText(options));
  }

  private BinEntry binAt(double x, double y) {
    if (renderState.entries().isEmpty() || !renderState.plot().contains(x, y)) {
      return null;
    }
    Rect plot = renderState.plot();
    int index;
    if (renderState.orientation() == Orientation.HORIZONTAL) {
      double slot = plot.height() / renderState.entries().size();
      index = (int) Math.floor((y - plot.y()) / slot);
    } else {
      double slot = plot.width() / renderState.entries().size();
      index = (int) Math.floor((x - plot.x()) / slot);
    }
    if (index < 0 || index >= renderState.entries().size()) {
      return null;
    }
    return renderState.entries().get(index);
  }

  private List<BinEntry> aggregate(List<BinEntry> source, int targetCount) {
    if (source.size() <= targetCount) {
      return source;
    }
    List<BinEntry> ret = new ArrayList<>(targetCount);
    for (int i = 0; i < targetCount; i++) {
      int start = (int) Math.floor((double) i * source.size() / targetCount);
      int end = (int) Math.floor((double) (i + 1) * source.size() / targetCount);
      if (end <= start) {
        end = start + 1;
      }
      ret.add(BinEntry.merge(source.subList(start, Math.min(end, source.size()))));
    }
    return ret;
  }

  private double maxValue(List<BinEntry> entries) {
    double max = entries.stream().mapToDouble(BinEntry::value).max().orElse(1);
    return max <= 0 ? 1 : max;
  }

  private int labelStep(int entries, int availableLabels) {
    if (entries <= 0 || availableLabels <= 0) {
      return 1;
    }
    return Math.max(1, (int) Math.ceil((double) entries / availableLabels));
  }

  private Color colorFor(BinEntry entry, int index, int total) {
    if (entry.category() != null) {
      Color color = options.categoryColors().get(entry.category());
      if (color != null) {
        return color;
      }
      return PALETTE[Math.floorMod(entry.category().hashCode(), PALETTE.length)];
    }

    if (options.useRangeGradient()) {
      double ratio = total <= 1 ? 0 : (double) index / (total - 1);
      return Color.web("#2f7f6f").interpolate(Color.web("#7ba7cf"), ratio);
    }
    return options.defaultColor();
  }

  private String summaryText() {
    List<BinEntry> entries = BinEntry.from(asset);
    double total = entries.stream().mapToDouble(BinEntry::value).sum();
    return "Histogram\n"
        + entries.size()
        + " bins\nn "
        + formatNumber(total)
        + (asset.getMissingCount() > 0 ? "\nmissing " + formatNumber(asset.getMissingCount()) : "");
  }

  private static String clipped(String value, int maxLength) {
    if (value == null || value.length() <= maxLength) {
      return Objects.toString(value, "");
    }
    return value.substring(0, Math.max(1, maxLength - 1)) + "...";
  }

  private static String formatNumber(double value) {
    if (!Double.isFinite(value)) {
      return "?";
    }
    if (Math.abs(value) >= 100_000 || Math.abs(value) < 0.001 && value != 0) {
      return String.format(Locale.US, "%.2e", value);
    }
    return NUMBER_FORMAT.format(value);
  }

  public static class Options {
    private Orientation orientation = Orientation.VERTICAL;
    private boolean showCategories = true;
    private boolean showRanges = true;
    private boolean useRangeGradient = true;
    private Color defaultColor = Color.web("#2f7f6f");
    private final Map<String, Color> categoryColors = new LinkedHashMap<>();

    public Options() {}

    public Options(Options other) {
      if (other != null) {
        this.orientation = other.orientation;
        this.showCategories = other.showCategories;
        this.showRanges = other.showRanges;
        this.useRangeGradient = other.useRangeGradient;
        this.defaultColor = other.defaultColor;
        this.categoryColors.putAll(other.categoryColors);
      }
    }

    public Options orientation(Orientation orientation) {
      this.orientation = orientation == null ? Orientation.VERTICAL : orientation;
      return this;
    }

    public Options showCategories(boolean showCategories) {
      this.showCategories = showCategories;
      return this;
    }

    public Options showRanges(boolean showRanges) {
      this.showRanges = showRanges;
      return this;
    }

    public Options useRangeGradient(boolean useRangeGradient) {
      this.useRangeGradient = useRangeGradient;
      return this;
    }

    public Options defaultColor(Color defaultColor) {
      this.defaultColor = defaultColor == null ? Color.web("#2f7f6f") : defaultColor;
      return this;
    }

    public Options categoryColor(String category, int red, int green, int blue) {
      return categoryColor(category, Color.rgb(red, green, blue));
    }

    public Options categoryColor(String category, Color color) {
      if (category != null && !category.isBlank()) {
        if (color == null) {
          categoryColors.remove(category);
        } else {
          categoryColors.put(category, color);
        }
      }
      return this;
    }

    public Options categoryColors(Map<String, Color> colors) {
      categoryColors.clear();
      if (colors != null) {
        colors.forEach(this::categoryColor);
      }
      return this;
    }

    public Orientation orientation() {
      return orientation;
    }

    public boolean showCategories() {
      return showCategories;
    }

    public boolean showRanges() {
      return showRanges;
    }

    public boolean useRangeGradient() {
      return useRangeGradient;
    }

    public Color defaultColor() {
      return defaultColor;
    }

    public Map<String, Color> categoryColors() {
      return categoryColors;
    }
  }

  private record BinEntry(
      String category,
      double min,
      double max,
      double mean,
      double value,
      double sum,
      double sumSquared,
      double weight,
      double missingCount,
      boolean aggregate,
      int sourceBins) {

    static List<BinEntry> from(Histogram histogram) {
      if (histogram == null || histogram.getBins() == null) {
        return List.of();
      }
      List<BinEntry> ret = new ArrayList<>();
      for (Histogram.Bin bin : histogram.getBins()) {
        double count = bin.getCount();
        if (count <= 0 && bin.getWeight() > 0) {
          count = bin.getWeight();
        }
        if (count < 0 || !Double.isFinite(count)) {
          count = 0;
        }
        ret.add(
            new BinEntry(
                blankToNull(bin.getCategory()),
                bin.getMin(),
                bin.getMax(),
                bin.getMean(),
                count,
                finite(bin.getSum()),
                finite(bin.getSumSquared()),
                finite(bin.getWeight()),
                finite(bin.getMissingCount()),
                false,
                1));
      }
      return ret;
    }

    static BinEntry merge(List<BinEntry> entries) {
      if (entries.isEmpty()) {
        return new BinEntry(null, 0, 0, 0, 0, 0, 0, 0, 0, true, 0);
      }
      double min =
          entries.stream().mapToDouble(BinEntry::min).filter(Double::isFinite).min().orElse(0);
      double max =
          entries.stream().mapToDouble(BinEntry::max).filter(Double::isFinite).max().orElse(0);
      double value = entries.stream().mapToDouble(BinEntry::value).sum();
      double sum = entries.stream().mapToDouble(BinEntry::sum).sum();
      double sumSquared = entries.stream().mapToDouble(BinEntry::sumSquared).sum();
      double weight = entries.stream().mapToDouble(BinEntry::weight).sum();
      double missingCount = entries.stream().mapToDouble(BinEntry::missingCount).sum();
      int sourceBins = entries.stream().mapToInt(BinEntry::sourceBins).sum();
      double weightedMean =
          entries.stream().mapToDouble(entry -> entry.mean() * entry.value()).sum()
              / Math.max(1, value);
      String category = sharedCategory(entries);
      return new BinEntry(
          category,
          min,
          max,
          weightedMean,
          value,
          sum,
          sumSquared,
          weight,
          missingCount,
          true,
          sourceBins);
    }

    String label(Options options) {
      if (category != null && options.showCategories()) {
        return aggregate ? category + "+" : category;
      }
      if (options.showRanges() && Double.isFinite(min) && Double.isFinite(max) && max > min) {
        return formatNumber(min) + "-" + formatNumber(max);
      }
      if (options.showRanges() && Double.isFinite(mean)) {
        return formatNumber(mean);
      }
      return "";
    }

    String tooltipText(Options options) {
      List<String> lines = new ArrayList<>();
      String label = label(options);
      lines.add(label.isBlank() ? "Bin" : "Bin: " + label);
      if (aggregate && sourceBins > 1) {
        lines.add("Aggregated bins: " + sourceBins);
      }
      if (category != null) {
        lines.add("Category: " + category);
      }
      if (Double.isFinite(min) && Double.isFinite(max) && max > min) {
        lines.add("Range: " + formatNumber(min) + " - " + formatNumber(max));
      }
      if (Double.isFinite(mean)) {
        lines.add("Mean: " + formatNumber(mean));
      }
      lines.add("Count: " + formatNumber(value));
      if (sum != 0) {
        lines.add("Sum: " + formatNumber(sum));
      }
      if (sumSquared != 0) {
        lines.add("Sum squared: " + formatNumber(sumSquared));
      }
      if (weight > 0 && Math.abs(weight - value) > 1e-9) {
        lines.add("Weight: " + formatNumber(weight));
      }
      if (missingCount > 0) {
        lines.add("Missing: " + formatNumber(missingCount));
      }
      return String.join("\n", lines);
    }

    private static String blankToNull(String value) {
      return value == null || value.isBlank() ? null : value;
    }

    private static String sharedCategory(List<BinEntry> entries) {
      String first = entries.getFirst().category();
      if (first == null) {
        return null;
      }
      for (BinEntry entry : entries) {
        if (!first.equals(entry.category())) {
          return null;
        }
      }
      return first;
    }

    private static double finite(double value) {
      return Double.isFinite(value) ? value : 0;
    }
  }

  private record RenderState(Orientation orientation, Rect plot, List<BinEntry> entries) {
    static RenderState empty() {
      return new RenderState(Orientation.VERTICAL, new Rect(0, 0, 0, 0), List.of());
    }
  }

  private record Rect(double x, double y, double width, double height) {
    boolean contains(double px, double py) {
      return px >= x && px <= x + width && py >= y && py <= y + height;
    }
  }
}
