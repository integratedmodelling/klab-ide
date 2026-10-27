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
import org.integratedmodelling.klab.api.lang.kactors.KActorsBehavior;

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
      Path file,
      KActorsBehavior.Type behaviorType,
      long synchronizedTimestamp) {}

  public enum Synchronization {
    NO_MIRROR,
    UNCHANGED,
    UPDATED,
    STALE,
    DIRTY
  }

  public enum LocalState {
    NOT_MANAGED,
    SYNCHRONIZED,
    MODIFIED,
    UNAVAILABLE
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
    return checkout(serviceId, projectUrn, behaviorUrn, null, source);
  }

  public synchronized Checkout checkout(String serviceId, String projectUrn, String behaviorUrn,
      KActorsBehavior.Type type, String source) throws IOException {
    return checkout(serviceId, projectUrn, behaviorUrn, type, source, 0);
  }

  public synchronized Checkout checkout(String serviceId, String projectUrn, String behaviorUrn,
      KActorsBehavior.Type type, String source, long timestamp) throws IOException {
    requireIdentity(serviceId, projectUrn, behaviorUrn);
    var existingOrigin = findOrigin(serviceId, projectUrn, behaviorUrn, type);
    if (existingOrigin.isPresent() && Files.isRegularFile(existingOrigin.get().file())) {
      // A checkout is also an authoritative project read. Reconcile missed change events,
      // while synchronize() continues to protect unsubmitted local edits.
      synchronize(serviceId, projectUrn, behaviorUrn, type, source, timestamp);
      var file = existingOrigin.get().file();
      var current = origin(file).orElseThrow();
      if (current.behaviorType() == null && type != null) {
        current = new Origin(current.serviceId(), current.projectUrn(), current.behaviorUrn(),
            current.synchronizedSourceHash(), file, type, current.synchronizedTimestamp());
        writeOrigin(current);
      }
      return new Checkout(file, current, false);
    }
    var directory = directoryFor(serviceId, projectUrn, typedIdentity(behaviorUrn, type));
    var file = sourceFile(directory, behaviorUrn);
    Files.createDirectories(directory);
    var created = !Files.exists(file);
    if (created) {
      writeAtomically(file, Objects.requireNonNullElse(source, ""));
    }
    var existing = origin(file);
    if (existing.isPresent() && !created) {
      return new Checkout(file, existing.get(), created);
    }
    var origin =
        new Origin(
            serviceId,
            projectUrn,
            behaviorUrn,
            hash(created ? Objects.requireNonNullElse(source, "") : Files.readString(file)),
            file, type, timestamp);
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
          new Origin(serviceId, projectUrn, behaviorUrn, synchronizedHash, normalized,
              properties.getProperty("behaviorType") == null ? null
                  : KActorsBehavior.Type.valueOf(properties.getProperty("behaviorType")),
              Long.parseLong(properties.getProperty("synchronizedTimestamp", "0"))));
    } catch (IOException | IllegalArgumentException e) {
      return Optional.empty();
    }
  }

  /** Compare a local mirror with the last source synchronized with its managed project. */
  public synchronized LocalState localState(Path file) {
    var origin = origin(file);
    if (origin.isEmpty()) return LocalState.NOT_MANAGED;
    if (!Files.isRegularFile(origin.get().file())) return LocalState.UNAVAILABLE;
    try {
      var localHash = hash(Files.readString(origin.get().file(), StandardCharsets.UTF_8));
      return localHash.equals(origin.get().synchronizedSourceHash())
          ? LocalState.SYNCHRONIZED
          : LocalState.MODIFIED;
    } catch (IOException e) {
      return LocalState.UNAVAILABLE;
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
    return markSynchronized(file, behaviorUrn, source,
        origin(file).map(Origin::synchronizedTimestamp).orElse(0L));
  }

  private Origin markSynchronized(Path file, String behaviorUrn, String source, long timestamp)
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
            origin.file(), origin.behaviorType(), timestamp);
    writeOrigin(updated);
    return updated;
  }

  public synchronized Synchronization synchronize(
      String serviceId, String projectUrn, String behaviorUrn, String remoteSource)
      throws IOException {
    return synchronize(serviceId, projectUrn, behaviorUrn, null, remoteSource);
  }

  public synchronized Synchronization synchronize(String serviceId, String projectUrn,
      String behaviorUrn, KActorsBehavior.Type type, String remoteSource) throws IOException {
    return synchronize(serviceId, projectUrn, behaviorUrn, type, remoteSource, 0);
  }

  public synchronized Synchronization synchronize(String serviceId, String projectUrn,
      String behaviorUrn, KActorsBehavior.Type type, String remoteSource, long timestamp)
      throws IOException {
    var origin = findOrigin(serviceId, projectUrn, behaviorUrn, type);
    var file = origin.map(Origin::file).orElse(pathFor(serviceId, projectUrn, behaviorUrn));
    if (origin.isEmpty() || !Files.isRegularFile(file)) return Synchronization.NO_MIRROR;
    // Service revisions, unlike local file mtimes, survive reopening and queued notifications.
    if (timestamp > 0 && timestamp < origin.get().synchronizedTimestamp())
      return Synchronization.STALE;
    long revision = Math.max(timestamp, origin.get().synchronizedTimestamp());

    var localSource = Files.readString(file, StandardCharsets.UTF_8);
    var localHash = hash(localSource);
    var remote = Objects.requireNonNullElse(remoteSource, "");
    var remoteHash = hash(remote);
    if (localHash.equals(remoteHash)) {
      markSynchronized(file, behaviorUrn, remote, revision);
      return Synchronization.UNCHANGED;
    }
    if (!localHash.equals(origin.get().synchronizedSourceHash())) {
      return Synchronization.DIRTY;
    }
    writeAtomically(file, remote);
    markSynchronized(file, behaviorUrn, remote, revision);
    return Synchronization.UPDATED;
  }

  public Path pathFor(String serviceId, String projectUrn, String behaviorUrn) {
    return pathFor(serviceId, projectUrn, behaviorUrn, null);
  }

  /** Explicit conflict resolution: retain the local bytes before accepting the project source. */
  synchronized Path useProjectSource(Path file, KActorsBehavior remote) throws IOException {
    var current = origin(file).orElseThrow(() -> new IOException("Not a managed behavior: " + file));
    if (!Objects.equals(current.behaviorUrn(), remote.getUrn())
        || !Objects.equals(current.projectUrn(), remote.getProjectName())
        || (current.behaviorType() != null && current.behaviorType() != remote.getBehaviorType()))
      throw new IOException("Project source does not match the local behavior");
    if (remote.getLastUpdateTimestamp() > 0
        && remote.getLastUpdateTimestamp() < current.synchronizedTimestamp())
      throw new IOException("Project source is older than the last synchronized revision");
    var backup = Files.createTempFile(file.getParent(), file.getFileName() + ".local-", ".bak");
    Files.copy(file, backup, StandardCopyOption.REPLACE_EXISTING);
    var source = Objects.requireNonNullElse(remote.getSourceCode(), "");
    writeAtomically(file, source);
    writeOrigin(new Origin(current.serviceId(), current.projectUrn(), current.behaviorUrn(),
        hash(source), current.file(), remote.getBehaviorType(),
        Math.max(current.synchronizedTimestamp(), remote.getLastUpdateTimestamp())));
    return backup;
  }

  public Path pathFor(String serviceId, String projectUrn, String behaviorUrn, KActorsBehavior.Type type) {
    return findOrigin(serviceId, projectUrn, behaviorUrn, type)
        .map(Origin::file)
        .orElseGet(() -> sourceFile(directoryFor(serviceId, projectUrn, typedIdentity(behaviorUrn, type)), behaviorUrn));
  }

  private Optional<Origin> findOrigin(String serviceId, String projectUrn, String behaviorUrn, KActorsBehavior.Type type) {
    requireIdentity(serviceId, projectUrn, behaviorUrn);
    var projectDirectory =
        root.resolve(identifiedSegment(serviceId)).resolve(identifiedSegment(projectUrn));
    if (!Files.isDirectory(projectDirectory)) return Optional.empty();
    Optional<Origin> legacy = Optional.empty();
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
              if (Objects.equals(type, candidate.get().behaviorType())) return candidate;
              if (candidate.get().behaviorType() == null) legacy = candidate;
            }
          }
        }
      }
    } catch (IOException e) {
      return Optional.empty();
    }
    return legacy;
  }

  private static String typedIdentity(String urn, KActorsBehavior.Type type) {
    return type == null ? urn : urn + "-" + type.name().toLowerCase(java.util.Locale.ROOT);
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
    if (origin.behaviorType() != null) properties.setProperty("behaviorType", origin.behaviorType().name());
    properties.setProperty("synchronizedSourceHash", origin.synchronizedSourceHash());
    properties.setProperty("synchronizedTimestamp", Long.toString(origin.synchronizedTimestamp()));
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
