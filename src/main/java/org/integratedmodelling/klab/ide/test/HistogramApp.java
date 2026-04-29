package org.integratedmodelling.klab.ide.test;

import java.util.Random;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;

/**
 * Sample histogram application for later use
 */
public class HistogramApp extends Application {

  @Override
  public void start(Stage primaryStage) {
    // Define axes
    CategoryAxis xAxis = new CategoryAxis();
    NumberAxis yAxis = new NumberAxis();
//    xAxis.setLabel("Range");
//    yAxis.setLabel("Frequency");

    // Create BarChart
    BarChart<String, Number> barChart = new BarChart<>(xAxis, yAxis);
    barChart.setCategoryGap(0);
    barChart.setBarGap(1);

    // Prepare Data
    int[] data = new int[1000];
    int[] bins = new int[10]; // 10 bins: 0-9, 10-19, etc.
    Random rand = new Random();

    // Generate random data (0-99)
    for (int i = 0; i < data.length; i++) {
      data[i] = rand.nextInt(100);
    }

    // Count frequencies in bins
    for (int val : data) {
      bins[val / 10]++;
    }

    // Add data to chart
    XYChart.Series<String, Number> series = new XYChart.Series<>();
//    series.setName("Histogram");
    for (int i = 0; i < 10; i++) {
      series.getData().add(new XYChart.Data<>(i * 10 + "-" + (i * 10 + 9), bins[i]));
    }
    barChart.getData().add(series);
    barChart.setLegendVisible(false);

    // Display
    StackPane root = new StackPane(barChart);
    primaryStage.setScene(new Scene(root, 200, 200));
    primaryStage.setTitle("JavaFX Histogram");
    primaryStage.show();
  }

  public static void main(String[] args) {
    launch(args);
  }
}
