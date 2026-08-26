package org.integratedmodelling.klab.ide.components.generic;

import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.input.DragEvent;
import javafx.scene.input.Dragboard;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import org.kordamp.ikonli.material2.Material2AL;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * A JavaFX component that provides a drag-and-drop upload box for files and URLs. Features: - Gray
 * background with dashed, curved borders - Configurable prompt text - Progress bar during upload -
 * Error handling and display - Callback on successful upload - Multiple file upload support with
 * file listing - File/URL icons for each uploaded file - Delete functionality for individual files
 * - Ability to retrieve all files as a single zip archive - Support for directory uploads (all
 * files in the directory are processed) - Exclusive mode where only one file can be uploaded before
 * resetting
 */
public class UploadBox extends StackPane {

  private final String targetDirectory;
  private final String promptText;
  private final Consumer<File> onUploadComplete;
  private final BiConsumer<String, Throwable> onError;

  private Label promptLabel;
  private Label statusLabel;
  private ProgressBar progressBar;
  private VBox contentBox;
  private VBox filesBox;
  private ExecutorService executorService;

  // Map to store uploaded files/URLs (path -> File object)
  private Map<String, File> uploadedFiles;

  // Consumer for exclusive mode - when set, only one file can be uploaded
  private Consumer<File> exclusiveConsumer;

  // CSS styles
  private static final String DEFAULT_STYLE =
      "-fx-background-color: -color-bg-subtle;"
          + "-fx-border-color: -color-border-muted;"
          + "-fx-border-width: 3;"
          + "-fx-border-style: dashed;"
          + "-fx-border-radius: 10;"
          + "-fx-background-radius: 10;"
          + "-fx-min-height: 150;"
          + "-fx-min-width: 200;";

  private static final String DRAG_OVER_STYLE =
      "-fx-background-color: -color-accent-subtle;"
          + "-fx-border-color: -color-accent-fg;"
          + "-fx-border-width: 2;"
          + "-fx-border-style: dashed;"
          + "-fx-border-radius: 10;"
          + "-fx-background-radius: 10;"
          + "-fx-min-height: 150;"
          + "-fx-min-width: 200;";

  private static final String ERROR_STYLE =
      "-fx-background-color: -color-danger-subtle;"
          + "-fx-border-color: -color-danger-fg;"
          + "-fx-border-width: 2;"
          + "-fx-border-style: dashed;"
          + "-fx-border-radius: 10;"
          + "-fx-background-radius: 10;"
          + "-fx-min-height: 150;"
          + "-fx-min-width: 200;";

  /**
   * Creates a new UploadBox component.
   *
   * @param targetDirectory The directory where uploaded files will be copied
   * @param promptText The text to display in the center of the box
   * @param onUploadComplete Callback invoked when upload completes successfully
   * @param onError Callback invoked when an error occurs (message, exception)
   */
  public UploadBox(
      String targetDirectory,
      String promptText,
      Consumer<File> onUploadComplete,
      BiConsumer<String, Throwable> onError) {
    this.targetDirectory = targetDirectory;
    this.promptText = promptText;
    this.onUploadComplete = onUploadComplete;
    this.onError = onError;
    this.executorService =
        Executors.newSingleThreadExecutor(
            r -> {
              Thread t = new Thread(r, "UploadBox-Worker");
              t.setDaemon(true);
              return t;
            });
    this.uploadedFiles = new HashMap<>();

    initializeComponent();
    setupDragAndDrop();
  }

  private void initializeComponent() {
    // Create content elements
    promptLabel = new Label(promptText);
    promptLabel.setStyle("-fx-font-size: 14px; -fx-text-fill: -color-fg-muted;");
    promptLabel.setWrapText(true);
    promptLabel.setMaxWidth(250);
    promptLabel.setAlignment(Pos.CENTER);

    statusLabel = new Label();
    statusLabel.setStyle("-fx-font-size: 12px;");
    statusLabel.setVisible(false);

    progressBar = new ProgressBar();
    progressBar.setVisible(false);
    progressBar.setPrefWidth(200);

    // Create files container
    filesBox = new VBox(5);
    filesBox.setAlignment(Pos.CENTER_LEFT);
    filesBox.setPadding(new Insets(5));
    filesBox.setVisible(false);

    // Create main content container
    contentBox = new VBox(10);
    contentBox.setAlignment(Pos.CENTER);
    contentBox.setPadding(new Insets(20));
    contentBox.getChildren().addAll(promptLabel, statusLabel, progressBar, filesBox);

    getChildren().add(contentBox);
    setStyle(DEFAULT_STYLE);

    // Ensure target directory exists
    try {
      Files.createDirectories(Paths.get(targetDirectory));
    } catch (IOException e) {
      showError("Failed to create target directory", e);
    }
  }

  /**
   * Adds a file item to the UI with appropriate icons and a delete button.
   *
   * @param file The file to add
   * @param isUrl Whether the file was uploaded from a URL
   */
  private void addFileItem(File file, boolean isUrl) {
    if (file == null || !file.exists()) {
      return;
    }

    // Create file item container
    HBox fileItem = new HBox(5);
    fileItem.setAlignment(Pos.CENTER_LEFT);
    fileItem.setPadding(new Insets(3, 5, 3, 5));
    fileItem.setStyle(
        "-fx-background-color: -color-bg-default; -fx-background-radius: 4;");

    // Create appropriate icon based on file type
    IconLabel fileIcon =
        new IconLabel(
            isUrl ? Material2AL.LINK : Material2AL.DESCRIPTION, 16, "-color-fg-muted");

    // Create file name label
    Label fileNameLabel = new Label(file.getName());
    fileNameLabel.setStyle("-fx-font-size: 12px;");
    HBox.setHgrow(fileNameLabel, Priority.ALWAYS);

    // Create delete button
    IconLabel deleteIcon =
        new IconLabel(Material2AL.DELETE, 16, "-color-danger-fg");
    deleteIcon.setStyle("-fx-cursor: hand; -fx-text-fill: -color-danger-fg;");

    // Add delete action
    deleteIcon.setOnMouseClicked(
        event -> {
          uploadedFiles.remove(file.getPath());
          filesBox.getChildren().remove(fileItem);

          // Hide filesBox if empty
          if (filesBox.getChildren().isEmpty()) {
            filesBox.setVisible(false);
            promptLabel.setVisible(true);
          }
        });

    // Add components to file item
    fileItem.getChildren().addAll(fileIcon, fileNameLabel, deleteIcon);

    // Add to files box
    filesBox.getChildren().add(fileItem);
    filesBox.setVisible(true);

    // Store file in map
    uploadedFiles.put(file.getPath(), file);
  }

  private void setupDragAndDrop() {
    setOnDragOver(this::handleDragOver);
    setOnDragDropped(this::handleDragDropped);
    setOnDragEntered(e -> setStyle(DRAG_OVER_STYLE));
    setOnDragExited(e -> resetStyle());
  }

  private void handleDragOver(DragEvent event) {
    Dragboard dragboard = event.getDragboard();
    if (dragboard.hasFiles() || dragboard.hasUrl() || dragboard.hasString()) {
      event.acceptTransferModes(TransferMode.COPY);
    }
    event.consume();
  }

  private void handleDragDropped(DragEvent event) {
    Dragboard dragboard = event.getDragboard();
    boolean success = false;

    try {
      if (dragboard.hasFiles()) {
        List<File> files = dragboard.getFiles();
        if (!files.isEmpty()) {
          // Process all files and directories
          for (File file : files) {
            if (file.isDirectory()) {
              // Process directory
              processDirectory(file);
            } else {
              // Process single file
              uploadFile(file);
            }
          }
          success = true;
        }
      } else if (dragboard.hasUrl()) {
        String urlString = dragboard.getUrl();
        uploadFromUrl(urlString);
        success = true;
      } else if (dragboard.hasString()) {
        String content = dragboard.getString();
        if (isValidUrl(content)) {
          uploadFromUrl(content);
          success = true;
        }
      }
    } catch (Exception e) {
      showError("Failed to process dropped content", e);
    }

    event.setDropCompleted(success);
    event.consume();
    resetStyle();
  }

  /**
   * Recursively processes a directory and uploads all files within it.
   *
   * @param directory The directory to process
   */
  private void processDirectory(File directory) {
    if (directory == null || !directory.exists() || !directory.isDirectory()) {
      return;
    }

    showProgress("Scanning directory: " + directory.getName() + "...");

    // Create a task to process the directory in the background
    Task<List<File>> directoryTask =
        new Task<List<File>>() {
          // Maximum number of files to process in a single directory upload
          private static final int MAX_FILES = 1000;

          @Override
          protected List<File> call() throws Exception {
            List<File> processedFiles = new ArrayList<>();

            // First scan the directory to count files (with a separate task)
            updateMessage("Scanning directory...");

            // Get all files in the directory and subdirectories
            List<File> allFiles = getAllFilesInDirectory(directory);

            // Check if we have too many files
            if (allFiles.size() > MAX_FILES) {
              updateMessage("Too many files: " + allFiles.size() + " (limit: " + MAX_FILES + ")");
              throw new Exception(
                  "Directory contains too many files: "
                      + allFiles.size()
                      + ". Maximum allowed: "
                      + MAX_FILES);
            }

            // Update total progress
            updateProgress(0, allFiles.size());
            AtomicInteger filesDone = new AtomicInteger(0);

            // Process each file
            for (File file : allFiles) {
              if (Thread.currentThread().isInterrupted()) {
                throw new InterruptedException("Directory processing was cancelled");
              }

              if (file.isFile()) {
                // Skip files that are already uploaded
                if (uploadedFiles.containsKey(file.getPath())) {
                  filesDone.incrementAndGet();
                  updateProgress(filesDone.get(), allFiles.size());
                  continue;
                }

                // Update message with current file
                updateMessage("Processing: " + file.getName());

                // Copy file to target directory
                Path sourcePath = file.toPath();

                // Create a filename that preserves some directory structure
                String relativePath = getRelativePath(directory, file);
                String targetFileName = relativePath.replace(File.separator, "_");

                Path targetPath = Paths.get(targetDirectory, targetFileName);

                try {
                  Files.copy(sourcePath, targetPath, StandardCopyOption.REPLACE_EXISTING);
                  processedFiles.add(targetPath.toFile());
                } catch (IOException e) {
                  // Log error but continue with other files
                  System.err.println(
                      "Failed to copy file: " + file.getPath() + " - " + e.getMessage());
                }
              }

              // Update progress
              filesDone.incrementAndGet();
              updateProgress(filesDone.get(), allFiles.size());
            }

            updateMessage("Completed processing " + processedFiles.size() + " files");
            return processedFiles;
          }

          @Override
          protected void succeeded() {
            Platform.runLater(
                () -> {
                  hideProgress();
                  List<File> processedFiles = getValue();

                  if (processedFiles.isEmpty()) {
                    showSuccess("No new files found in directory: " + directory.getName());
                    return;
                  }

                  // Check if we're in exclusive mode
                  if (exclusiveConsumer != null && !processedFiles.isEmpty()) {
                    // In exclusive mode, only process the first file
                    File firstFile = processedFiles.get(0);

                    // Call the exclusive consumer with the first file
                    exclusiveConsumer.accept(firstFile);

                    // Reset the component to pristine state
                    dispose();

                    showSuccess("Processed file from directory: " + directory.getName());
                  } else {
                    // Normal mode - add all processed files to UI
                    for (File file : processedFiles) {
                      addFileItem(file, false);

                      // Notify callback for each file
                      if (onUploadComplete != null) {
                        onUploadComplete.accept(file);
                      }
                    }

                    showSuccess(
                        "Processed "
                            + processedFiles.size()
                            + " files from directory: "
                            + directory.getName());

                    // Hide prompt when files are shown
                    if (!filesBox.getChildren().isEmpty()) {
                      promptLabel.setVisible(false);
                    }
                  }
                });
          }

          @Override
          protected void failed() {
            Platform.runLater(
                () -> {
                  hideProgress();
                  Throwable exception = getException();
                  showError("Failed to process directory: " + directory.getName(), exception);
                });
          }

          @Override
          protected void running() {
            Platform.runLater(
                () -> {
                  // Update status label with the current message
                  messageProperty()
                      .addListener(
                          (obs, oldMsg, newMsg) -> {
                            statusLabel.setText(newMsg);
                          });
                });
          }
        };

    progressBar.progressProperty().bind(directoryTask.progressProperty());
    executorService.submit(directoryTask);
  }

  /**
   * Gets all files in a directory and its subdirectories.
   *
   * @param directory The directory to scan
   * @return List of all files found
   */
  private List<File> getAllFilesInDirectory(File directory) {
    List<File> files = new ArrayList<>();
    File[] directoryContents = directory.listFiles();

    if (directoryContents != null) {
      for (File file : directoryContents) {
        // Skip hidden files and directories
        if (file.isHidden()) {
          continue;
        }

        if (file.isFile()) {
          files.add(file);
        } else if (file.isDirectory()) {
          // Recursively process subdirectories
          files.addAll(getAllFilesInDirectory(file));
        }
      }
    }

    return files;
  }

  /**
   * Gets the relative path of a file within a base directory.
   *
   * @param baseDir The base directory
   * @param file The file to get the relative path for
   * @return The relative path as a string
   */
  private String getRelativePath(File baseDir, File file) {
    String basePath = baseDir.getAbsolutePath();
    String filePath = file.getAbsolutePath();

    if (filePath.startsWith(basePath)) {
      return filePath.substring(basePath.length() + 1);
    }

    return file.getName();
  }

  private void uploadFile(File sourceFile) {
    if (!sourceFile.exists() || !sourceFile.isFile()) {
      showError("Invalid file: " + sourceFile.getName(), null);
      return;
    }

    // Check if file is already uploaded
    if (uploadedFiles.containsKey(sourceFile.getPath())) {
      showError("File already uploaded: " + sourceFile.getName(), null);
      return;
    }

    showProgress("Uploading " + sourceFile.getName() + "...");

    Task<File> uploadTask =
        new Task<File>() {
          @Override
          protected File call() throws Exception {
            Path sourcePath = sourceFile.toPath();
            Path targetPath = Paths.get(targetDirectory, sourceFile.getName());

            // Update progress
            updateProgress(0, 1);

            // Copy file with progress tracking
            long fileSize = Files.size(sourcePath);

            // For demonstration, we'll simulate progress updates
            // In a real implementation, you might want to implement custom copy with progress
            Files.copy(sourcePath, targetPath, StandardCopyOption.REPLACE_EXISTING);

            updateProgress(1, 1);
            return targetPath.toFile();
          }

          @Override
          protected void succeeded() {
            Platform.runLater(
                () -> {
                  hideProgress();
                  File uploadedFile = getValue();

                  // Check if we're in exclusive mode
                  if (exclusiveConsumer != null) {
                    // Call the exclusive consumer
                    exclusiveConsumer.accept(uploadedFile);

                    // Reset the component to pristine state
                    dispose();

                    showSuccess("Upload completed: " + uploadedFile.getName());
                  } else {
                    // Normal mode - add file to UI
                    addFileItem(uploadedFile, false);

                    showSuccess("Upload completed: " + uploadedFile.getName());

                    // Hide prompt when files are shown
                    if (!filesBox.getChildren().isEmpty()) {
                      promptLabel.setVisible(false);
                    }

                    if (onUploadComplete != null) {
                      onUploadComplete.accept(uploadedFile);
                    }
                  }
                });
          }

          @Override
          protected void failed() {
            Platform.runLater(
                () -> {
                  hideProgress();
                  Throwable exception = getException();
                  showError("Upload failed", exception);
                });
          }
        };

    progressBar.progressProperty().bind(uploadTask.progressProperty());
    executorService.submit(uploadTask);
  }

  private void uploadFromUrl(String urlString) {
    showProgress("Downloading from URL...");

    Task<File> downloadTask =
        new Task<File>() {
          @Override
          protected File call() throws Exception {
            URL url = new URL(urlString);
            String fileName = extractFileNameFromUrl(urlString);
            Path targetPath = Paths.get(targetDirectory, fileName);

            // Check if a file with this name already exists in the target directory
            File targetFile = targetPath.toFile();
            if (uploadedFiles.containsKey(targetFile.getPath())) {
              // Generate a unique filename by adding a timestamp
              String baseName = fileName;
              String extension = "";
              int dotIndex = fileName.lastIndexOf('.');
              if (dotIndex > 0) {
                baseName = fileName.substring(0, dotIndex);
                extension = fileName.substring(dotIndex);
              }
              fileName = baseName + "_" + System.currentTimeMillis() + extension;
              targetPath = Paths.get(targetDirectory, fileName);
            }

            updateProgress(0, 1);

            // Download file from URL
            try (var inputStream = url.openStream()) {
              Files.copy(inputStream, targetPath, StandardCopyOption.REPLACE_EXISTING);
            }

            updateProgress(1, 1);
            return targetPath.toFile();
          }

          @Override
          protected void succeeded() {
            Platform.runLater(
                () -> {
                  hideProgress();
                  File downloadedFile = getValue();

                  // Check if we're in exclusive mode
                  if (exclusiveConsumer != null) {
                    // Call the exclusive consumer
                    exclusiveConsumer.accept(downloadedFile);

                    // Reset the component to pristine state
                    dispose();

                    showSuccess("Download completed: " + downloadedFile.getName());
                  } else {
                    // Normal mode - add file to UI with URL icon
                    addFileItem(downloadedFile, true);

                    showSuccess("Download completed: " + downloadedFile.getName());

                    // Hide prompt when files are shown
                    if (!filesBox.getChildren().isEmpty()) {
                      promptLabel.setVisible(false);
                    }

                    if (onUploadComplete != null) {
                      onUploadComplete.accept(downloadedFile);
                    }
                  }
                });
          }

          @Override
          protected void failed() {
            Platform.runLater(
                () -> {
                  hideProgress();
                  Throwable exception = getException();
                  showError("Download failed", exception);
                });
          }
        };

    progressBar.progressProperty().bind(downloadTask.progressProperty());
    executorService.submit(downloadTask);
  }

  private void showProgress(String message) {
    statusLabel.setText(message);
    statusLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: -color-accent-fg;");
    statusLabel.setVisible(true);
    progressBar.setVisible(true);

    // Only hide prompt if no files are displayed
    if (filesBox.getChildren().isEmpty()) {
      promptLabel.setVisible(false);
    }
  }

  private void hideProgress() {
    progressBar.setVisible(false);
    progressBar.progressProperty().unbind();
    progressBar.setProgress(0);
  }

  private void showSuccess(String message) {
    statusLabel.setText(message);
    statusLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: -color-success-fg;");
    statusLabel.setVisible(true);

    // Hide success message after 3 seconds
    new Thread(
            () -> {
              try {
                Thread.sleep(3000);
                Platform.runLater(
                    () -> {
                      statusLabel.setVisible(false);

                      // Only show prompt if no files are displayed
                      if (filesBox.getChildren().isEmpty()) {
                        promptLabel.setVisible(true);
                      }

                      resetStyle();
                    });
              } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
              }
            })
        .start();
  }

  private void showError(String message, Throwable throwable) {
    String errorMessage = message;
    if (throwable != null) {
      errorMessage += ": " + throwable.getMessage();
    }

    statusLabel.setText(errorMessage);
    statusLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: -color-danger-fg;");
    statusLabel.setVisible(true);

    // Only hide prompt if no files are displayed
    if (filesBox.getChildren().isEmpty()) {
      promptLabel.setVisible(false);
    }

    setStyle(ERROR_STYLE);

    if (onError != null) {
      onError.accept(message, throwable);
    }

    // Hide error message after 5 seconds
    new Thread(
            () -> {
              try {
                Thread.sleep(5000);
                Platform.runLater(
                    () -> {
                      statusLabel.setVisible(false);

                      // Only show prompt if no files are displayed
                      if (filesBox.getChildren().isEmpty()) {
                        promptLabel.setVisible(true);
                      }

                      resetStyle();
                    });
              } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
              }
            })
        .start();
  }

  private void resetStyle() {
    setStyle(DEFAULT_STYLE);
  }

  private boolean isValidUrl(String urlString) {
    try {
      new URL(urlString);
      return true;
    } catch (MalformedURLException e) {
      return false;
    }
  }

  private String extractFileNameFromUrl(String urlString) {
    try {
      URL url = new URL(urlString);
      String path = url.getPath();
      String fileName = path.substring(path.lastIndexOf('/') + 1);

      if (fileName.isEmpty() || !fileName.contains(".")) {
        fileName = "downloaded_file_" + System.currentTimeMillis();
      }

      return fileName;
    } catch (Exception e) {
      return "downloaded_file_" + System.currentTimeMillis();
    }
  }

  /** Clean up resources when the component is no longer needed. */
  public void dispose() {
    if (executorService != null && !executorService.isShutdown()) {
      executorService.shutdown();
    }

    // Clear uploaded files map
    uploadedFiles.clear();

    // Clear UI elements
    if (filesBox != null) {
      filesBox.getChildren().clear();
      filesBox.setVisible(false);
    }

    // Reset UI state
    if (promptLabel != null) {
      promptLabel.setVisible(true);
    }
  }

  /**
   * Returns a list of all uploaded files.
   *
   * @return List of uploaded files
   */
  public List<File> getUploadedFiles() {
    return new ArrayList<>(uploadedFiles.values());
  }

  /**
   * Sets the exclusive consumer for this upload box. When set, the first upload of a file or URL
   * will trigger the consumer and then immediately reset the UploadBox to pristine state.
   *
   * @param consumer The consumer to call with the uploaded file
   */
  public void setExclusive(Consumer<File> consumer) {
    this.exclusiveConsumer = consumer;
  }

  /**
   * Creates a zip file containing all uploaded files.
   *
   * @return Task that will produce the zip file when complete. The caller can use this to track
   *     progress and get the result when ready.
   */
  public Task<File> getUploadedFilesAsZip() {
    if (uploadedFiles.isEmpty()) {
      showError("No files to zip", null);
      Task<File> emptyTask =
          new Task<File>() {
            @Override
            protected File call() {
              return null;
            }
          };
      emptyTask.run();
      return emptyTask;
    }

    showProgress("Creating zip file...");

    Task<File> zipTask =
        new Task<File>() {
          @Override
          protected File call() throws Exception {
            // Create a unique zip filename
            String zipFileName = "uploaded_files_" + System.currentTimeMillis() + ".zip";
            Path zipPath = Paths.get(targetDirectory, zipFileName);
            File zipFile = zipPath.toFile();

            // Update progress
            updateProgress(0, uploadedFiles.size());
            int filesDone = 0;

            try (FileOutputStream fos = new FileOutputStream(zipFile);
                ZipOutputStream zos = new ZipOutputStream(fos)) {

              // Add each file to the zip
              for (File file : uploadedFiles.values()) {
                if (file.exists()) {
                  // Create a new entry in the zip file
                  ZipEntry zipEntry = new ZipEntry(file.getName());
                  zos.putNextEntry(zipEntry);

                  // Copy file content to the zip
                  try (FileInputStream fis = new FileInputStream(file)) {
                    byte[] buffer = new byte[1024];
                    int length;
                    while ((length = fis.read(buffer)) > 0) {
                      zos.write(buffer, 0, length);
                    }
                  }

                  // Close the entry
                  zos.closeEntry();

                  // Update progress
                  filesDone++;
                  updateProgress(filesDone, uploadedFiles.size());
                }
              }

              return zipFile;
            } catch (IOException e) {
              // Delete the zip file if an error occurs
              try {
                Files.deleteIfExists(zipPath);
              } catch (IOException ignored) {
                // Ignore deletion errors
              }
              throw e;
            }
          }

          @Override
          protected void succeeded() {
            Platform.runLater(
                () -> {
                  hideProgress();
                  File zipFile = getValue();
                  showSuccess("Zip file created: " + zipFile.getName());
                });
          }

          @Override
          protected void failed() {
            Platform.runLater(
                () -> {
                  hideProgress();
                  Throwable exception = getException();
                  showError("Failed to create zip file", exception);
                });
          }
        };

    // Bind progress bar to task progress
    progressBar.progressProperty().bind(zipTask.progressProperty());

    // Execute the task
    executorService.submit(zipTask);

    // Return the task so the caller can track progress and get the result when ready
    return zipTask;
  }

  /**
   * Convenience method that creates a zip file containing all uploaded files and waits for
   * completion. Note: This method blocks until the zip file is created. Do not call from the JavaFX
   * application thread.
   *
   * @return File object representing the created zip file, or null if an error occurred
   */
  public File getUploadedFilesAsZipBlocking() {
    Task<File> zipTask = getUploadedFilesAsZip();
    try {
      // Wait for the task to complete and return the result
      return zipTask.get();
    } catch (Exception e) {
      showError("Failed to create zip file", e);
      return null;
    }
  }
}
