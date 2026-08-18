package org.integratedmodelling.klab.ide.components;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;

/** Persistent local checkouts of behaviors whose authoritative copy belongs to a project. */
public final class ManagedBehaviorMirrors {

  private static final String METADATA_FILE = "origin.properties";
  private static final ManagedBehaviorMirrors DEFAULT =
      new ManagedBehaviorMirrors(
          Path.of(System.getProperty("user.home"), ".klab", "ide", "behavior-mirrors"));

  private final Path root;

  public record Origin(
      String serviceId,
      String projectUrn,
      String behaviorUrn,
      String synchronizedSourceHash,
      Path file) {}

  public enum Synchronization {
    NO_MIRROR,
    UNCHANGED,
    UPDATED,
    DIRTY
  }

  public record Checkout(Path file, Origin origin, boolean created) {}

  public ManagedBehaviorMirrors(Path root) {
    this.root = root.toAbsolutePath().normalize();
  }

  public static ManagedBehaviorMirrors getDefault() {
    return DEFAULT;
  }

  public synchronized Checkout checkout(
      String serviceId, String projectUrn, String behaviorUrn, String source) throws IOException {
    requireIdentity(serviceId, projectUrn, behaviorUrn);
    var existingOrigin = findOrigin(serviceId, projectUrn, behaviorUrn);
    if (existingOrigin.isPresent() && Files.isRegularFile(existingOrigin.get().file())) {
      return new Checkout(existingOrigin.get().file(), existingOrigin.get(), false);
    }
    var directory = directoryFor(serviceId, projectUrn, behaviorUrn);
    var file = sourceFile(directory, behaviorUrn);
    Files.createDirectories(directory);
    var created = !Files.exists(file);
    if (created) {
      writeAtomically(file, Objects.requireNonNullElse(source, ""));
    }
    var existing = origin(file);
    if (existing.isPresent()) {
      return new Checkout(file, existing.get(), created);
    }
    var origin =
        new Origin(
            serviceId,
            projectUrn,
            behaviorUrn,
            hash(created ? Objects.requireNonNullElse(source, "") : Files.readString(file)),
            file);
    writeOrigin(origin);
    return new Checkout(file, origin, created);
  }

  public synchronized Optional<Origin> origin(Path file) {
    if (file == null) return Optional.empty();
    var normalized = file.toAbsolutePath().normalize();
    var metadata =
        normalized.getParent() == null ? null : normalized.getParent().resolve(METADATA_FILE);
    if (metadata == null || !Files.isRegularFile(metadata)) return Optional.empty();
    var properties = new Properties();
    try (InputStream input = Files.newInputStream(metadata)) {
      properties.load(input);
      var serviceId = properties.getProperty("serviceId");
      var projectUrn = properties.getProperty("projectUrn");
      var behaviorUrn = properties.getProperty("behaviorUrn");
      var synchronizedHash = properties.getProperty("synchronizedSourceHash");
      if (isBlank(serviceId)
          || isBlank(projectUrn)
          || isBlank(behaviorUrn)
          || isBlank(synchronizedHash)) {
        return Optional.empty();
      }
      return Optional.of(
          new Origin(serviceId, projectUrn, behaviorUrn, synchronizedHash, normalized));
    } catch (IOException e) {
      return Optional.empty();
    }
  }

  public synchronized Origin markSynchronized(Path file, String source) throws IOException {
    var current =
        origin(file)
            .orElseThrow(() -> new IOException("Behavior is not a managed mirror: " + file));
    return markSynchronized(file, current.behaviorUrn(), source);
  }

  public synchronized Origin markSynchronized(Path file, String behaviorUrn, String source)
      throws IOException {
    var origin =
        origin(file)
            .orElseThrow(() -> new IOException("Behavior is not a managed mirror: " + file));
    requireIdentity(origin.serviceId(), origin.projectUrn(), behaviorUrn);
    var updated =
        new Origin(
            origin.serviceId(),
            origin.projectUrn(),
            behaviorUrn,
            hash(Objects.requireNonNullElse(source, "")),
            origin.file());
    writeOrigin(updated);
    return updated;
  }

  public synchronized Synchronization synchronize(
      String serviceId, String projectUrn, String behaviorUrn, String remoteSource)
      throws IOException {
    var origin = findOrigin(serviceId, projectUrn, behaviorUrn);
    var file = origin.map(Origin::file).orElse(pathFor(serviceId, projectUrn, behaviorUrn));
    if (origin.isEmpty() || !Files.isRegularFile(file)) return Synchronization.NO_MIRROR;

    var localSource = Files.readString(file, StandardCharsets.UTF_8);
    var localHash = hash(localSource);
    var remote = Objects.requireNonNullElse(remoteSource, "");
    var remoteHash = hash(remote);
    if (localHash.equals(remoteHash)) {
      markSynchronized(file, remote);
      return Synchronization.UNCHANGED;
    }
    if (!localHash.equals(origin.get().synchronizedSourceHash())) {
      return Synchronization.DIRTY;
    }
    writeAtomically(file, remote);
    markSynchronized(file, remote);
    return Synchronization.UPDATED;
  }

  public Path pathFor(String serviceId, String projectUrn, String behaviorUrn) {
    return findOrigin(serviceId, projectUrn, behaviorUrn)
        .map(Origin::file)
        .orElseGet(() -> sourceFile(directoryFor(serviceId, projectUrn, behaviorUrn), behaviorUrn));
  }

  private Optional<Origin> findOrigin(String serviceId, String projectUrn, String behaviorUrn) {
    requireIdentity(serviceId, projectUrn, behaviorUrn);
    var projectDirectory =
        root.resolve(identifiedSegment(serviceId)).resolve(identifiedSegment(projectUrn));
    if (!Files.isDirectory(projectDirectory)) return Optional.empty();
    try (var directories = Files.list(projectDirectory)) {
      for (var directory : directories.filter(Files::isDirectory).toList()) {
        try (var files = Files.list(directory)) {
          for (var file : files.filter(Files::isRegularFile).toList()) {
            if (!file.getFileName().toString().endsWith(".kactor")) continue;
            var candidate = origin(file);
            if (candidate.isPresent()
                && serviceId.equals(candidate.get().serviceId())
                && projectUrn.equals(candidate.get().projectUrn())
                && behaviorUrn.equals(candidate.get().behaviorUrn())) {
              return candidate;
            }
          }
        }
      }
    } catch (IOException e) {
      return Optional.empty();
    }
    return Optional.empty();
  }

  private static Path sourceFile(Path directory, String behaviorUrn) {
    var separator = Math.max(behaviorUrn.lastIndexOf('.'), behaviorUrn.lastIndexOf(':'));
    var localName = separator < 0 ? behaviorUrn : behaviorUrn.substring(separator + 1);
    return directory.resolve(safeSegment(localName) + ".kactor");
  }

  private Path directoryFor(String serviceId, String projectUrn, String behaviorUrn) {
    requireIdentity(serviceId, projectUrn, behaviorUrn);
    return root.resolve(identifiedSegment(serviceId))
        .resolve(identifiedSegment(projectUrn))
        .resolve(safeSegment(behaviorUrn) + "-" + hash(behaviorUrn).substring(0, 12));
  }

  private static String identifiedSegment(String value) {
    return safeSegment(value) + "-" + hash(value).substring(0, 12);
  }

  private void writeOrigin(Origin origin) throws IOException {
    var properties = new Properties();
    properties.setProperty("serviceId", origin.serviceId());
    properties.setProperty("projectUrn", origin.projectUrn());
    properties.setProperty("behaviorUrn", origin.behaviorUrn());
    properties.setProperty("synchronizedSourceHash", origin.synchronizedSourceHash());
    var metadata = origin.file().getParent().resolve(METADATA_FILE);
    var temporary = Files.createTempFile(metadata.getParent(), "origin-", ".tmp");
    try {
      try (OutputStream output = Files.newOutputStream(temporary)) {
        properties.store(output, "Managed k.Actors behavior origin");
      }
      moveAtomically(temporary, metadata);
    } finally {
      Files.deleteIfExists(temporary);
    }
  }

  private static void writeAtomically(Path file, String source) throws IOException {
    var temporary = Files.createTempFile(file.getParent(), "behavior-", ".tmp");
    try {
      Files.writeString(temporary, source, StandardCharsets.UTF_8);
      moveAtomically(temporary, file);
    } finally {
      Files.deleteIfExists(temporary);
    }
  }

  private static void moveAtomically(Path source, Path target) throws IOException {
    try {
      Files.move(
          source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
    } catch (AtomicMoveNotSupportedException e) {
      Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
    }
  }

  private static String safeSegment(String value) {
    var safe = value.replaceAll("[^A-Za-z0-9._-]", "_");
    return safe.length() <= 80 ? safe : safe.substring(0, 80);
  }

  private static String hash(String value) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 is unavailable", e);
    }
  }

  private static void requireIdentity(String serviceId, String projectUrn, String behaviorUrn) {
    if (isBlank(serviceId) || isBlank(projectUrn) || isBlank(behaviorUrn)) {
      throw new IllegalArgumentException("Managed behavior origin must be fully identified");
    }
  }

  private static boolean isBlank(String value) {
    return value == null || value.isBlank();
  }
}
