package org.integratedmodelling.klab.ide.components;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.paint.Paint;
import javafx.scene.text.Font;
import org.integratedmodelling.common.distribution.Downloader;
import org.integratedmodelling.klab.api.engine.distribution.Distribution;
import org.integratedmodelling.klab.api.engine.distribution.Stack;

import java.io.File;
import java.net.URL;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class DownloadMonitor extends Region {

  private final ProgressBar engineProgressBarOverall;
  private final Label engineProgressLabelTotal;
  private final ProgressBar engineProgressBarDetail;
  private final Label engineProgressLabelDetail;
  private final Label engineCurrentFileLabel;
  private final Paint originalLabelColor;
  private final Stack.Tag tag;

  public DownloadMonitor(Stack.Tag tag) {

    this.tag = tag;

    // Create the main VBox
    VBox engineDownloadMonitor = new VBox();
    VBox.setMargin(engineDownloadMonitor, new Insets(6.0, 0, 0, 0));

    // First HBox - Overall progress
    HBox hbox1 = new HBox();
    hbox1.setAlignment(Pos.CENTER_LEFT);

    engineProgressBarOverall = new ProgressBar(0.0);
    engineProgressBarOverall.setMaxHeight(12.0);
    engineProgressBarOverall.setMinHeight(10.0);
    engineProgressBarOverall.setPrefHeight(10.0);
    engineProgressBarOverall.setPrefWidth(172.0);
    engineProgressBarOverall.setStyle("-fx-text-box-border: #28c41d; -fx-background-insets: 0;");

    engineProgressLabelTotal = new Label("Copying local files");
    engineProgressLabelTotal.setTextFill(Color.web("#777777"));
    engineProgressLabelTotal.setFont(Font.font("Open Sans Regular", 10.0));
    HBox.setMargin(engineProgressLabelTotal, new Insets(0, 0, 0, 3.0));

    hbox1.getChildren().addAll(engineProgressBarOverall, engineProgressLabelTotal);

    // Second HBox - Detail progress
    HBox hbox2 = new HBox();
    hbox2.setAlignment(Pos.CENTER_LEFT);

    engineProgressBarDetail = new ProgressBar(0.0);
    engineProgressBarDetail.setMaxHeight(8.0);
    engineProgressBarDetail.setMinHeight(6.0);
    engineProgressBarDetail.setPrefHeight(6.0);
    engineProgressBarDetail.setPrefWidth(172.0);
    engineProgressBarDetail.setStyle("-fx-text-box-border: #28c41d; -fx-background-insets: 0;");

    engineProgressLabelDetail = new Label("please wait...");
    engineProgressLabelDetail.setTextFill(Color.web("#777777"));
    engineProgressLabelDetail.setFont(Font.font("Open Sans Regular", 10.0));
    HBox.setMargin(engineProgressLabelDetail, new Insets(0, 0, 0, 3.0));

    hbox2.getChildren().addAll(engineProgressBarDetail, engineProgressLabelDetail);

    // Current file label
    engineCurrentFileLabel = new Label("Preparing incremental download..");
    engineCurrentFileLabel.setTextFill(Color.web("#777777"));
    engineCurrentFileLabel.setFont(Font.font("Open Sans Regular", 10.0));

    originalLabelColor = engineCurrentFileLabel.getTextFill();

    // Add all to main VBox
    engineDownloadMonitor.getChildren().addAll(hbox1, hbox2, engineCurrentFileLabel);

    // Add the VBox to this Region
    getChildren().add(engineDownloadMonitor);
  }

  void startDownload(String file) {

    ExecutorService executor = Executors.newFixedThreadPool(2);
    Future<Boolean> etask =
        executor.submit(
            new Callable<>() {
              @Override
              public Boolean call() throws Exception {

                var synchronizer =
                    new Distribution.Synchronization() {

                      @Override
                      public boolean isSynchronizing() {
                        return true;
                      }

                      @Override
                      public boolean notifyDownload(
                          long totalSize,
                          long downloadSize,
                          Map<Distribution.FileData, Distribution.FileTarget> fullList,
                          Map<Distribution.FileData, Distribution.FileTarget> downloadList) {
                        return false;
                      }

                      @Override
                      public boolean download(URL url, File file, Distribution.FileData fileData) {
                        var downloader =
                            new Downloader(
                                url,
                                file,
                                (bytesSoFar, totalBytes) -> {
                                  engineProgressBarDetail.setProgress(
                                      (double) bytesSoFar / (double) totalBytes);
                                  engineProgressLabelDetail.setText(
                                      (bytesSoFar / 1024) + "/" + (totalBytes / 1024) + " kB");
                                });
                        // TODO initialize progress
                        var ret = downloader.download();
                        // TODO finish up
                        return ret;
                      }

                      @Override
                      public boolean link(File file, File destination) {
                        return false;
                      }

                      @Override
                      public void delete(File file) {}

                      @Override
                      public boolean copy(File source, File destination) {
                        return false;
                      }

                      @Override
                      public void notifyProductSynchronizing(Distribution.Product product) {}

                      @Override
                      public void notifyProductSynchronized(Distribution.Product product) {}
                    };

                //                return releaseService
                //                    .getRelease(rs.chosen)
                //                    .getDistribution(
                //                        IProduct.ProductType.CLI,
                //                        releaseService.getLastLocalRelease(),
                //                        new SyncListener() {
                //
                //                          int total;
                //                          int sofar = 0;
                //
                //                          @Override
                //                          public void transferFinished(Exception e) {
                //                            Platform.runLater(
                //                                () -> {
                //                                  if (e != null) {
                //                                    engineCurrentFileLabel.setTextFill(Color.RED);
                //                                    engineCurrentFileLabel.setText("Error
                // downloading k.Engine");
                //                                  } else {
                //
                // engineCurrentFileLabel.setTextFill(originalLabelColor);
                //                                    engineCurrentFileLabel.setText("k.Engine
                // download complete");
                //                                    // engine.cleanOldBuilds();
                //                                  }
                //                                });
                //                          }
                //
                //                          @Override
                //                          public void notifyFileProgress(
                //                              String file, long bytesSoFar, long totalBytes) {
                //                            Platform.runLater(
                //                                () -> {
                //
                // engineProgressLabelDetail.setTextFill(originalLabelColor);
                //                                  engineProgressBarDetail.setProgress(
                //                                      (double) bytesSoFar / (double) totalBytes);
                //                                  engineProgressLabelDetail.setText(
                //                                      (bytesSoFar / 1024) + "/" + (totalBytes /
                // 1024) + " kB");
                //                                });
                //                          }
                //
                //                          @Override
                //                          public void notifyDownloadCount(
                //                              int downloadFilecount, int deleteFileCount) {
                //                            this.total = downloadFilecount;
                //                          }
                //
                //                          @Override
                //                          public void beforeDownload(String file) {
                //
                // engineCurrentFileLabel.setTextFill(originalLabelColor);
                //
                // engineProgressLabelDetail.setTextFill(originalLabelColor);
                //                            sofar++;
                //                            Platform.runLater(
                //                                () -> {
                //                                  engineProgressBarOverall.setProgress(
                //                                      (double) sofar / (double) total);
                //                                  engineProgressLabelTotal.setText("#" + sofar + "
                // of " + total);
                //                                  engineCurrentFileLabel.setText(file);
                //                                });
                //                          }
                //
                //                          @Override
                //                          public void beforeDelete(File localFile) {}
                //
                //                          @Override
                //                          public void notifyDownloadPreparationStart() {
                //                            // TODO Auto-generated method stub
                //
                //                          }
                //
                //                          @Override
                //                          public void notifyDownloadPreparationEnd() {
                //                            // TODO Auto-generated method stub
                //
                //                          }
                //
                //                          @Override
                //                          public void notifyError(Exception e) {
                //                            Platform.runLater(
                //                                () -> {
                //                                  engineCurrentFileLabel.setTextFill(Color.RED);
                //                                  engineCurrentFileLabel.setText(e.getMessage());
                //                                });
                //                          }
                //                        })
                //                    .isComplete();
                return true;
              }
            });
  }
}
