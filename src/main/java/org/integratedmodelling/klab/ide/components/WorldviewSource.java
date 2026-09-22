package org.integratedmodelling.klab.ide.components;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import org.integratedmodelling.common.utils.Utils;
import org.integratedmodelling.klab.api.knowledge.Worldview;
import org.integratedmodelling.klab.api.lang.kim.KlabDocument;
import org.integratedmodelling.klab.api.scope.UserScope;
import org.integratedmodelling.klab.api.services.*;

/** Selects an authoritative pair without changing defaults used by other IDE views. */
final class WorldviewSource {
  record Snapshot(Reasoner reasoner, String resourcesId, Worldview worldview,
                  long revision, String content, boolean local) {}

  static Snapshot load(UserScope scope) {
    if (scope == null) return null;
    var reasoners = new LinkedHashMap<Reasoner, Reasoner.Capabilities>();
    for (var reasoner : scope.getServices(Reasoner.class)) {
      if (!local(reasoner)) continue;
      var status = reasoner.status();
      if (status == null || !status.isOperational()) continue;
      var capabilities = reasoner.capabilities(scope);
      if (capabilities != null && capabilities.getWorldviewId() != null)
        reasoners.put(reasoner, capabilities);
    }
    if (!reasoners.isEmpty()) {
      for (var resources : scope.getServices(ResourcesService.class)) {
        if (!local(resources)) continue;
        var status = resources.status();
        if (status == null || !status.isOperational()) continue;
        // Retrieve from this service directly: the scope's worldview cache may still be remote.
        for (var worldview : resources.list(Worldview.class, scope)) {
          if (worldview == null || worldview.isEmpty()) continue;
          // There is one certificate worldview. Snapshot IDs are assigned independently by
          // Resources, Reasoner and collectWorldview(); they are not an identity handshake.
          var entry = reasoners.entrySet().stream()
              .filter(e -> Objects.equals(worldview.getWorldviewId(), e.getValue().getWorldviewId()))
              .findFirst().orElse(reasoners.entrySet().iterator().next());
          return snapshot(entry.getKey(), resources.serviceId(), worldview,
              entry.getValue().getKnowledgeRevision(), true);
        }
      }
    }
    var reasoner = scope.getService(Reasoner.class);
    if (reasoner == null || !reasoner.status().isOperational()) return null;
    var capabilities = reasoner.capabilities(scope);
    if (capabilities == null || capabilities.getWorldviewId() == null) return null;
    var worldview = scope.getWorldview();
    if (worldview == null || worldview.isEmpty()) return null;
    return snapshot(reasoner, null, worldview, capabilities.getKnowledgeRevision(), false);
  }

  private static boolean local(KlabService service) {
    return service.getUrl() != null && Utils.URLs.isLocalHost(service.getUrl());
  }

  private static Snapshot snapshot(Reasoner reasoner, String resourcesId, Worldview worldview,
      long revision, boolean local) {
    return new Snapshot(reasoner, resourcesId, worldview, revision, fingerprint(worldview), local);
  }

  /** Worldview IDs are stable across edits; include document contents, additions and removals. */
  static String fingerprint(Worldview worldview) {
    try {
      var digest = MessageDigest.getInstance("SHA-256");
      update(digest, worldview.getUrn());
      update(digest, new TreeMap<>(worldview.getMetadata()).toString());
      for (var ontology : worldview.getOntologies()) document(digest, ontology);
      for (var strategy : worldview.getObservationStrategies()) document(digest, strategy);
      return HexFormat.of().formatHex(digest.digest());
    } catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
  }

  private static void document(MessageDigest digest, KlabDocument<?> document) {
    update(digest, document.getUrn());
    update(digest, String.valueOf(document.getVersion()));
    update(digest, String.valueOf(document.getLastUpdateTimestamp()));
    update(digest, document.getSourceCode());
  }

  private static void update(MessageDigest digest, String text) {
    byte[] bytes = Objects.toString(text, "").getBytes(StandardCharsets.UTF_8);
    digest.update(java.nio.ByteBuffer.allocate(4).putInt(bytes.length).array());
    digest.update(bytes);
  }
}
