package org.integratedmodelling.klab.ide.components;

import java.util.*;
import java.util.function.Consumer;
import javafx.application.Platform;
import org.integratedmodelling.klab.api.lang.kim.KlabDocument;
import org.integratedmodelling.klab.api.services.*;
import org.integratedmodelling.klab.api.services.reasoner.objects.*;
import org.integratedmodelling.klab.api.services.runtime.Notification;
import org.integratedmodelling.klab.ide.KlabIDEController;

/** Source-bound proposal feedback for a single open editor. Service listeners are removed on close. */
final class AuthorityProposalFeedback implements AutoCloseable {
  private final Consumer<List<Notification>> display;
  private final Map<Reasoner, Consumer<KlabService.ServiceStatus>> listeners = new IdentityHashMap<>();
  private final Map<Reasoner, Object> revisions = new IdentityHashMap<>();
  private KlabDocument<?> document;
  private long generation;
  private boolean closed;
  AuthorityProposalFeedback(Consumer<List<Notification>> display) { this.display = display; }
  void update(KlabDocument<?> document, boolean submit) {
    this.document = document;
    long current = ++generation;
    var extraction = AuthorityProposals.extract(document);
    if (extraction.candidates().isEmpty()) { display.accept(extraction.notifications()); return; }
    Thread.ofVirtual().start(() -> {
      var notifications = new ArrayList<>(extraction.notifications());
      try {
        var source = AuthorityBrowser.Source.load(KlabIDEController.instance().user());
        for (var candidate : extraction.candidates()) {
          try {
            var binding = source.bindings().stream().filter(b -> b.localId().equals(candidate.authority()))
                .findFirst().orElseThrow(() -> new IllegalArgumentException("Proposal authority is not configured"));
            var host = source.host(binding);
            var request = new AuthorityCodelistRequest(submit ? AuthorityCodelistRequest.Operation.SUBMIT
                : AuthorityCodelistRequest.Operation.LIST, candidate.authority(), candidate.namespace(),
                candidate.alias(), candidate.identity(), null, 0, null, null, null, candidate.source());
            var reply = host.authorityCodelists(request, source.scope());
            var proposal = reply.proposals().stream().filter(p -> p.namespace().equals(candidate.namespace())
                && p.alias().equals(candidate.alias()) && p.identity().equals(candidate.identity()) && p.sources().contains(candidate.source())).findFirst().orElse(null);
            String text = proposal == null ? "Not submitted; save to propose this authority alias"
                : proposal.status() + (proposal.approvedAlias() == null ? "" : " as " + proposal.namespace() + ":" + proposal.approvedAlias())
                    + (proposal.message() == null ? "" : ": " + proposal.message());
            notifications.add(Notification.info(candidate.namespace() + ":" + candidate.alias() + " — " + text,
                Notification.LexicalContext.of(candidate.statement(), document)));
            Platform.runLater(() -> { if (!closed && current == generation) watch(host); });
          } catch (Exception failure) {
            notifications.add(Notification.warning("Authority proposal: " + failure.getMessage(),
                Notification.LexicalContext.of(candidate.statement(), document)));
          }
        }
      } catch (Exception failure) { notifications.add(Notification.warning("Authority proposals unavailable: " + failure.getMessage())); }
      Platform.runLater(() -> { if (!closed && current == generation) display.accept(List.copyOf(notifications)); });
    });
  }
  private void watch(Reasoner host) {
    if (listeners.containsKey(host)) return;
    Consumer<KlabService.ServiceStatus> listener = status -> Platform.runLater(() -> {
      if (closed) return;
      Object revision = status.getMetadata().get("authority.codelist.revisions");
      if (revision != null && !Objects.equals(revisions.put(host, revision), revision)) update(document, false);
    });
    listeners.put(host, listener);
    KlabIDEController.instance().addServiceStatusListener(host, listener);
  }
  @Override public void close() {
    closed = true; generation++;
    listeners.forEach((host, listener) -> KlabIDEController.instance().removeServiceStatusListener(host, listener));
    listeners.clear();
  }
}
