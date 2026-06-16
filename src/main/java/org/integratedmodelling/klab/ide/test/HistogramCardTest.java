package org.integratedmodelling.klab.ide.test;

import atlantafx.base.theme.PrimerLight;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import org.integratedmodelling.klab.api.data.Histogram;
import org.integratedmodelling.klab.api.data.impl.HistogramImpl;
import org.integratedmodelling.klab.ide.components.cards.HistogramCard;

/** Standalone showcase for the {@link HistogramCard} component. */
public class HistogramCardTest extends Application {

  @Override
  public void start(Stage stage) {
    Application.setUserAgentStylesheet(new PrimerLight().getUserAgentStylesheet());

    HistogramCard numeric = new HistogramCard(numericHistogram(32), new HistogramCard.Options());
    numeric.setPrefSize(230, 150);

    HistogramCard categorical =
        new HistogramCard(
            categoricalHistogram(),
            new HistogramCard.Options()
                .orientation(Orientation.HORIZONTAL)
                .showRanges(false)
                .categoryColor("Forest", 46, 125, 82)
                .categoryColor("Water", 55, 126, 184)
                .categoryColor("Urban", 201, 85, 79)
                .categoryColor("Cropland", 205, 154, 64)
                .categoryColor("Grassland", 124, 160, 80));
    categorical.setPrefSize(270, 172);

    HistogramCard large =
        new HistogramCard(
            numericHistogram(240),
            new HistogramCard.Options().showRanges(false).useRangeGradient(true));
    large.setPrefSize(360, 150);

    HistogramCard mini =
        new HistogramCard(
            numericHistogram(18),
            new HistogramCard.Options().showCategories(false).showRanges(false));
    mini.setPrefSize(190, 116);

    List<HistogramCard> cards = List.of(numeric, categorical, large, mini);

    CheckBox categories = new CheckBox("Categories");
    categories.setSelected(true);
    categories.setOnAction(e -> cards.forEach(card -> card.setShowCategories(categories.isSelected())));

    CheckBox ranges = new CheckBox("Ranges");
    ranges.setSelected(true);
    ranges.setOnAction(e -> cards.forEach(card -> card.setShowRanges(ranges.isSelected())));

    HBox controls = new HBox(12, categories, ranges);
    controls.setAlignment(Pos.CENTER_LEFT);
    controls.setPadding(new Insets(0, 0, 4, 0));

    GridPane grid = new GridPane();
    grid.setHgap(16);
    grid.setVgap(16);
    grid.add(numeric, 0, 0);
    grid.add(categorical, 1, 0);
    grid.add(large, 0, 1, 2, 1);
    grid.add(mini, 0, 2);

    VBox content = new VBox(12, controls, grid);
    content.setStyle("-fx-padding: 16; -fx-background-color: -color-bg-subtle;");

    ScrollPane scrollPane = new ScrollPane(content);
    scrollPane.setFitToWidth(false);
    scrollPane.setFitToHeight(false);
    scrollPane.setStyle("-fx-background-color: -color-bg-subtle;");

    Scene scene = new Scene(scrollPane, 760, 540);
    scene
        .getStylesheets()
        .add(
            HistogramCardTest.class
                .getResource("/org/integratedmodelling/klab/ide/custom.css")
                .toExternalForm());

    stage.setTitle("HistogramCard Showcase");
    stage.setScene(scene);
    stage.show();
  }

  private static Histogram numericHistogram(int bins) {
    HistogramImpl histogram = new HistogramImpl();
    histogram.setMin(0);
    histogram.setMax(100);

    Random random = new Random(42 + bins);
    List<Histogram.Bin> data = new ArrayList<>();
    for (int i = 0; i < bins; i++) {
      double min = i * (100.0 / bins);
      double max = (i + 1) * (100.0 / bins);
      double center = (min + max) / 2.0;
      double signal =
          55 * Math.exp(-Math.pow((center - 42) / 18.0, 2))
              + 24 * Math.exp(-Math.pow((center - 78) / 9.0, 2))
              + 2
              + random.nextDouble() * 5;
      data.add(bin(min, max, null, signal));
    }
    histogram.setBins(data);
    return histogram;
  }

  private static Histogram categoricalHistogram() {
    HistogramImpl histogram = new HistogramImpl();
    histogram.setMin(0);
    histogram.setMax(5);

    List<Histogram.Bin> data = new ArrayList<>();
    data.add(bin(0, 1, "Forest", 114));
    data.add(bin(1, 2, "Water", 48));
    data.add(bin(2, 3, "Urban", 66));
    data.add(bin(3, 4, "Cropland", 92));
    data.add(bin(4, 5, "Grassland", 73));
    histogram.setBins(data);
    return histogram;
  }

  private static Histogram.Bin bin(double min, double max, String category, double count) {
    HistogramImpl.BinImpl bin = new HistogramImpl.BinImpl();
    bin.setMin(min);
    bin.setMax(max);
    bin.setMean((min + max) / 2.0);
    bin.setCategory(category);
    bin.setCount(count);
    return bin;
  }

  public static void main(String[] args) {
    launch(args);
  }
}
