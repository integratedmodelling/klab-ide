package org.integratedmodelling.klab.ide.components;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.TreeSet;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.integratedmodelling.klab.api.knowledge.KlabAsset;
import org.integratedmodelling.klab.api.services.resources.ResourceSet;
import org.integratedmodelling.klab.api.services.runtime.Notification;

/** Filesystem discovery and sequential submission, independent of the JavaFX thread. */
final class LegacyResourceImport {
  enum Status {
    QUEUED,
    IMPORTING,
    SUCCESS,
    ERROR
  }

  record Outcome(File manifest, Status status, String urn, String explanation) {}

  static List<File> discover(List<File> folders) throws IOException {
    var manifests = new TreeSet<Path>(Comparator.comparing(Path::toString));
    for (var folder : folders) {
      if (!folder.isDirectory()) throw new IOException("Folder is unavailable: " + folder);
      try (var paths = Files.walk(folder.toPath())) {
        paths
            .filter(Files::isRegularFile)
            .filter(path -> path.getFileName().toString().equalsIgnoreCase("resource.json"))
            .map(path -> path.toAbsolutePath().normalize())
            .forEach(manifests::add);
      }
    }
    return manifests.stream().map(Path::toFile).toList();
  }

  static void run(
      List<File> manifests, Function<File, ResourceSet> submit, Consumer<Outcome> progress) {
    for (var manifest : manifests) {
      progress.accept(new Outcome(manifest, Status.IMPORTING, "", ""));
      Outcome outcome;
      try {
        outcome = outcome(manifest, submit.apply(manifest));
      } catch (Exception failure) {
        Throwable cause = failure;
        while (cause.getCause() != null && cause.getCause() != cause) cause = cause.getCause();
        outcome =
            new Outcome(
                manifest,
                Status.ERROR,
                "",
                cause.getMessage() == null ? cause.toString() : cause.getMessage());
      }
      progress.accept(outcome);
    }
  }

  static Outcome outcome(File manifest, ResourceSet result) {
    if (result == null) {
      return new Outcome(manifest, Status.ERROR, "", "The service returned no result");
    }
    var resources =
        Stream.concat(result.getResources().stream(), result.getResults().stream())
            .filter(resource -> resource.getKnowledgeClass() == KlabAsset.KnowledgeClass.RESOURCE)
            .toList();
    var notifications = new ArrayList<>(result.getNotifications());
    resources.forEach(resource -> notifications.addAll(resource.getNotifications()));
    var explanation =
        notifications.stream()
            .map(Notification::getMessage)
            .filter(message -> message != null && !message.isBlank())
            .distinct()
            .collect(Collectors.joining("; "));
    if (notifications.stream()
        .anyMatch(n -> n.getLevel().severity >= Notification.Level.Error.severity)) {
      return new Outcome(manifest, Status.ERROR, "", explanation);
    }
    var urn =
        resources.stream()
            .map(ResourceSet.Resource::getResourceUrn)
            .filter(value -> value != null && !value.isBlank())
            .findFirst()
            .orElse(null);
    if (urn == null) {
      return new Outcome(
          manifest,
          Status.ERROR,
          "",
          explanation.isBlank() ? "The service returned no imported resource URN" : explanation);
    }
    return new Outcome(manifest, Status.SUCCESS, urn, explanation);
  }
}
