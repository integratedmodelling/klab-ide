package org.integratedmodelling.klab.ide.utils;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;

/** Persistent diagnostic output for GUI distributions that have no attached console. */
public final class ApplicationLog {

  private static final long MAX_LOG_SIZE = 5L * 1024L * 1024L;
  private static final int RETAINED_LOGS = 5;
  private static final Path LOG_DIRECTORY =
      Path.of(System.getProperty("user.home"), ".klab", "logs");
  private static final Path LOG_FILE = LOG_DIRECTORY.resolve("modeler.log");

  private static boolean initialized;

  private ApplicationLog() {}

  /**
   * Starts persistent capture of standard output, standard error, and uncaught exceptions.
   * Initialization deliberately never prevents the application from starting.
   */
  public static synchronized void initialize() {
    if (initialized) {
      return;
    }
    initialized = true;

    try {
      Files.createDirectories(LOG_DIRECTORY);
      rotateIfNeeded();

      var fileOutput = new FileOutputStream(LOG_FILE.toFile(), true);
      var originalOut = System.out;
      var originalErr = System.err;
      System.setOut(
          new PrintStream(
              new TeeOutputStream(originalOut, fileOutput), true, StandardCharsets.UTF_8));
      System.setErr(
          new PrintStream(
              new TeeOutputStream(originalErr, fileOutput), true, StandardCharsets.UTF_8));

      Thread.setDefaultUncaughtExceptionHandler(
          (thread, failure) -> report("Uncaught exception on thread " + thread.getName(), failure));
      System.out.printf(
          "%n[%s] k.LAB Modeler started (Java %s, %s)%n",
          Instant.now(), System.getProperty("java.version"), System.getProperty("os.name"));
    } catch (Throwable failure) {
      // Diagnostics must remain best-effort, especially in constrained package sandboxes.
      System.err.println("Unable to initialize persistent application log at " + LOG_FILE);
      failure.printStackTrace(System.err);
    }
  }

  public static void report(String message, Throwable failure) {
    System.err.printf("[%s] %s%n", Instant.now(), message);
    if (failure != null) {
      failure.printStackTrace(System.err);
    }
  }

  public static Path file() {
    return LOG_FILE;
  }

  private static void rotateIfNeeded() throws IOException {
    if (!Files.exists(LOG_FILE) || Files.size(LOG_FILE) < MAX_LOG_SIZE) {
      return;
    }
    for (int index = RETAINED_LOGS - 1; index >= 1; index--) {
      var source = LOG_DIRECTORY.resolve("modeler.log." + index);
      if (Files.exists(source)) {
        Files.move(
            source,
            LOG_DIRECTORY.resolve("modeler.log." + (index + 1)),
            StandardCopyOption.REPLACE_EXISTING);
      }
    }
    Files.move(
        LOG_FILE,
        LOG_DIRECTORY.resolve("modeler.log.1"),
        StandardCopyOption.REPLACE_EXISTING);
  }

  private static final class TeeOutputStream extends OutputStream {

    private final OutputStream console;
    private final OutputStream file;

    private TeeOutputStream(OutputStream console, OutputStream file) {
      this.console = console;
      this.file = file;
    }

    @Override
    public synchronized void write(int value) throws IOException {
      console.write(value);
      file.write(value);
    }

    @Override
    public synchronized void write(byte[] bytes, int offset, int length) throws IOException {
      console.write(bytes, offset, length);
      file.write(bytes, offset, length);
    }

    @Override
    public synchronized void flush() throws IOException {
      console.flush();
      file.flush();
    }
  }
}
