package org.integratedmodelling.klab.ide.components;

import atlantafx.base.theme.Styles;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import org.apache.commons.io.FileUtils;
import org.integratedmodelling.common.distribution.Downloader;
import org.integratedmodelling.common.utils.Utils;
import org.integratedmodelling.klab.api.engine.distribution.Distribution;
import org.integratedmodelling.klab.ide.Theme;

/** Two-level progress display and filesystem actuator for distribution operations. */
public class DownloadMonitor extends VBox {

  private final Label operationLabel = new Label();
  private final Label overallLabel = new Label();
  private final ProgressBar overallProgress = new ProgressBar(0);
  private final Label currentFileLabel = new Label();
  private final ProgressBar currentFileProgress = new ProgressBar(0);
  private final Label currentFileDetail = new Label();
  private final AtomicInteger completedDownloads = new AtomicInteger();
  private volatile int totalDownloads;

  public DownloadMonitor() {
    super(5);
    getStyleClass().add("distribution-progress");
    setPadding(new Insets(12));
    setMaxWidth(Double.MAX_VALUE);
    setStyle(
        "-fx-background-color: -color-bg-subtle; -fx-background-radius: 8;"
            + " -fx-border-color: -color-border-default; -fx-border-radius: 8;");

    operationLabel.getStyleClass().addAll(Styles.TEXT_BOLD);
    overallLabel.getStyleClass().addAll(Styles.TEXT_SMALL, Styles.TEXT_MUTED);
    currentFileLabel.getStyleClass().addAll(Styles.TEXT_SMALL);
    currentFileDetail.getStyleClass().addAll(Styles.TEXT_SMALL, Styles.TEXT_MUTED);
    currentFileLabel.setWrapText(false);
    currentFileLabel.setTextOverrun(javafx.scene.control.OverrunStyle.CENTER_ELLIPSIS);
    overallProgress.setMaxWidth(Double.MAX_VALUE);
    currentFileProgress.setMaxWidth(Double.MAX_VALUE);
    VBox.setVgrow(overallProgress, Priority.NEVER);
    getChildren()
        .addAll(
            operationLabel,
            overallLabel,
            overallProgress,
            currentFileLabel,
            currentFileProgress,
            currentFileDetail);
  }

  public void prepare(String operation) {
    completedDownloads.set(0);
    totalDownloads = 0;
    update(
        () -> {
          operationLabel.setText(operation);
          operationLabel.setTextFill(Theme.CURRENT_THEME.getDefaultTextColor());
          overallLabel.setText("Preparing...");
          overallProgress.setProgress(ProgressBar.INDETERMINATE_PROGRESS);
          currentFileLabel.setText("Inspecting distribution metadata");
          currentFileProgress.setProgress(0);
          currentFileDetail.setText("");
        });
  }

  public Distribution.Synchronization synchronization() {
    return new Distribution.Synchronization() {
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
        totalDownloads = downloadList.size();
        completedDownloads.set(0);
        update(
            () -> {
              overallLabel.setText(
                  totalDownloads + " files to synchronize / " + fullList.size() + " total files");
              overallProgress.setProgress(totalDownloads == 0 ? 1 : 0);
              currentFileLabel.setText(
                  totalDownloads == 0 ? "Distribution is already synchronized" : "Ready");
              currentFileProgress.setProgress(0);
            });
        return true;
      }

      @Override
      public boolean download(URL url, File file, Distribution.FileData fileData) {
        var ordinal = completedDownloads.get() + 1;
        update(
            () -> {
              currentFileLabel.setText(fileData.name());
              currentFileDetail.setText("File " + ordinal + " of " + totalDownloads);
              currentFileProgress.setProgress(0);
            });
        try {
          var downloaded =
              new Downloader(
                      url,
                      file,
                      (bytes, total) ->
                          update(
                              () -> {
                                currentFileProgress.setProgress(
                                    total <= 0
                                        ? ProgressBar.INDETERMINATE_PROGRESS
                                        : (double) bytes / total);
                                currentFileDetail.setText(
                                    formatBytes(bytes)
                                        + (total > 0 ? " / " + formatBytes(total) : ""));
                              }),
                      fileData.hash())
                  .download();
          if (downloaded) {
            var complete = completedDownloads.incrementAndGet();
            update(
                () -> {
                  currentFileProgress.setProgress(1);
                  overallProgress.setProgress(
                      totalDownloads == 0 ? 1 : (double) complete / totalDownloads);
                  overallLabel.setText(complete + " of " + totalDownloads + " files synchronized");
                });
          }
          return downloaded;
        } catch (RuntimeException e) {
          return false;
        }
      }

      @Override
      public boolean link(File file, File destination) {
        var parent = destination.getParentFile();
        if (parent != null) {
          parent.mkdirs();
        }
        return Utils.Files.symlink(file, destination);
      }

      @Override
      public void delete(File file) {
        FileUtils.deleteQuietly(file);
      }

      @Override
      public boolean copy(File source, File destination) {
        try {
          var parent = destination.getParentFile();
          if (parent != null) {
            parent.mkdirs();
          }
          Files.copy(source.toPath(), destination.toPath(), StandardCopyOption.REPLACE_EXISTING);
          return true;
        } catch (IOException e) {
          return false;
        }
      }

      @Override
      public void notifyProductSynchronizing(Distribution.Product product) {
        update(() -> operationLabel.setText("Synchronizing " + product.getType().getName()));
      }

      @Override
      public void notifyProductSynchronized(Distribution.Product product) {}
    };
  }

  public Distribution.Verification verification() {
    return new Distribution.Verification() {
      private volatile int totalFiles;

      @Override
      public void notifyVerification(int totalFiles) {
        this.totalFiles = totalFiles;
        update(
            () -> {
              overallLabel.setText("0 of " + totalFiles + " files verified");
              overallProgress.setProgress(totalFiles == 0 ? 1 : 0);
            });
      }

      @Override
      public void notifyFileVerifying(
          File file, Distribution.FileData fileData, int index) {
        update(
            () -> {
              currentFileLabel.setText(fileData.name());
              currentFileDetail.setText("Checking file " + index + " of " + totalFiles);
              currentFileProgress.setProgress(ProgressBar.INDETERMINATE_PROGRESS);
            });
      }

      @Override
      public void notifyFileVerified(
          File file, Distribution.FileData fileData, int index, boolean valid) {
        update(
            () -> {
              currentFileProgress.setProgress(valid ? 1 : 0);
              overallProgress.setProgress(totalFiles == 0 ? 1 : (double) index / totalFiles);
              overallLabel.setText(index + " of " + totalFiles + " files verified");
              if (!valid) {
                currentFileDetail.setText("Integrity check failed");
              }
            });
      }
    };
  }

  public void complete(String message) {
    update(
        () -> {
          operationLabel.setText(message);
          operationLabel.setTextFill(Color.DARKGREEN);
          overallProgress.setProgress(1);
          currentFileProgress.setProgress(1);
        });
  }

  public void fail(String message) {
    update(
        () -> {
          operationLabel.setText(message);
          operationLabel.setTextFill(Color.DARKRED);
          currentFileProgress.setProgress(0);
        });
  }

  private static String formatBytes(long bytes) {
    if (bytes < 1024) {
      return bytes + " B";
    }
    if (bytes < 1024 * 1024) {
      return String.format("%.1f kB", bytes / 1024.0);
    }
    return String.format("%.1f MB", bytes / (1024.0 * 1024.0));
  }

  private static void update(Runnable runnable) {
    if (Platform.isFxApplicationThread()) {
      runnable.run();
    } else {
      Platform.runLater(runnable);
    }
  }
}
