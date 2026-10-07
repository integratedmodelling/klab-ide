package org.integratedmodelling.klab.ide.components;

import atlantafx.base.util.BBCodeParser;
import java.net.URI;
import java.net.URL;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.*;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.util.Duration;
import org.integratedmodelling.klab.api.ServicesAPI;
import org.integratedmodelling.klab.api.configuration.Configuration;
import org.integratedmodelling.klab.api.knowledge.Worldview;
import org.integratedmodelling.klab.api.scope.UserScope;
import org.integratedmodelling.klab.api.services.*;
import org.integratedmodelling.klab.api.services.reasoner.objects.*;
import org.integratedmodelling.klab.api.services.resources.objects.AuthorityIdentity;
import org.integratedmodelling.klab.ide.utils.BBCodeNodeRenderer;

/** Read-only authority browsing is independent of the composer's serialized semantic edits. */
final class AuthorityBrowser implements AutoCloseable {
  record Source(UserScope scope, List<Worldview.AuthorityBinding> bindings, List<Reasoner> hosts) {
    Source { bindings = List.copyOf(bindings); hosts = List.copyOf(hosts); }
    static Source empty() { return new Source(null, List.of(), List.of()); }
    static Source load(UserScope scope) {
      if (scope == null) return empty();
      var worldview = scope.getWorldview();
      if (worldview instanceof org.integratedmodelling.klab.api.knowledge.impl.WorldviewImpl implementation)
        implementation.refreshAuthorityHosts(scope);
      return new Source(scope, worldview == null ? List.of() : worldview.getAuthorityBindings(),
          new ArrayList<>(scope.getServices(Reasoner.class)));
    }
    Reasoner host(Worldview.AuthorityBinding binding) {
      return hosts.stream().filter(host -> host.getUrl() != null && binding.reasonerUrl() != null
          && host.getUrl().toExternalForm().equals(binding.reasonerUrl().toExternalForm()))
          .findFirst().orElseThrow(() -> new IllegalStateException("No available Reasoner hosts " + binding.localId()));
    }
  }

  final ComboBox<String> chooser = new ComboBox<>();
  final ComboBox<String> codelistFilter = new ComboBox<>();
  private Future<?> listDiscovery;
  private long listRevision;
  private static final String ALL_CODELISTS = "All identities";
  final TableView<AuthorityIdentity> results = new TableView<>();
  final SplitPane view = new SplitPane();
  private final VBox documentation = new VBox(8);
  private final Consumer<String> status;
  private final Runnable changed;
  private final PauseTransition deadline = new PauseTransition(Duration.seconds(30));
  private final PauseTransition documentDeadline = new PauseTransition(Duration.seconds(30));
  private Source source = Source.empty();
  private Future<?> discovery, search, document;
  private long searchRevision, documentRevision;
  private boolean closed, busy, current;

  AuthorityBrowser(Supplier<Source> discoverySource, Consumer<String> status, Runnable changed,
      Runnable choose, Runnable providerChanged) {
    this.status = status;
    this.changed = changed;
    chooser.setId("semantic-authority");
    chooser.setPromptText("Authority");
    chooser.setPrefWidth(160);
    codelistFilter.setId("authority-codelist-filter");
    codelistFilter.getItems().setAll(ALL_CODELISTS);
    codelistFilter.getSelectionModel().selectFirst();

    codelistFilter.visibleProperty().bind(javafx.beans.binding.Bindings.size(codelistFilter.getItems())
        .greaterThan(1).and(chooser.visibleProperty()));
    codelistFilter.managedProperty().bind(codelistFilter.visibleProperty());
    codelistFilter.valueProperty().addListener((o, a, b) -> providerChanged.run());
    chooser.valueProperty().addListener((o, a, b) -> { loadCodelists(); providerChanged.run(); });
    results.setId("authority-results");
    results.getColumns().add(column("Identity", AuthorityIdentity::getLabel));
    results.getColumns().add(column("Code", AuthorityIdentity::getId));
    results.getColumns().add(column("Aliases", identity -> String.join(", ", identity.getAliases())));
    results.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
    results.setPlaceholder(new Label("Type at least two characters to search"));
    results.getSelectionModel().selectedItemProperty().addListener((o, a, b) -> loadDocumentation(b));
    results.setRowFactory(table -> {
      var row = new TableRow<AuthorityIdentity>();
      row.setOnMouseClicked(e -> {
        if (e.getButton() == javafx.scene.input.MouseButton.PRIMARY && e.getClickCount() == 2 && !row.isEmpty()) {
          table.getSelectionModel().select(row.getItem()); choose.run(); e.consume();
        }
      });
      return row;
    });
    documentation.setId("authority-documentation");
    documentation.setStyle("-fx-padding: 10;");
    var scroll = new ScrollPane(documentation);
    scroll.setFitToWidth(true);
    view.getItems().addAll(results, scroll);
    view.setDividerPositions(.5);
    view.setPrefHeight(240);
    view.setMinHeight(100);
    deadline.setOnFinished(e -> {
      invalidate(); status.accept("Authority search timed out. Edit the query to retry."); changed.run();
    });
    documentDeadline.setOnFinished(e -> {
      documentRevision++;
      if (document != null) document.cancel(true);
      documentation.getChildren().removeIf(node -> "authority-doc-loading".equals(node.getId()));
      documentation.getChildren().add(new Label("Documentation timed out"));
    });
    discovery = task(() -> {
      try {
        var loaded = discoverySource.get();
        Platform.runLater(() -> {
          if (closed) return;
          source = loaded == null ? Source.empty() : loaded;
          chooser.getItems().setAll(source.bindings().stream()
              .filter(b -> b.provider() != null)
              .map(Worldview.AuthorityBinding::localId).distinct().sorted().toList());
          chooser.getSelectionModel().selectFirst();
          if (chooser.getItems().isEmpty()) results.setPlaceholder(new Label("No searchable authorities configured"));
          changed.run();
        });
      } catch (Exception failure) {
        Platform.runLater(() -> { if (!closed) {
          results.setPlaceholder(new Label("Authority discovery failed: " + message(failure))); changed.run();
        }});
      }
    });
  }

  private void loadCodelists() {
    long revision = ++listRevision;
    if (listDiscovery != null) listDiscovery.cancel(true);
    codelistFilter.getItems().setAll(ALL_CODELISTS);
    codelistFilter.getSelectionModel().selectFirst();

    var binding = source.bindings().stream().filter(b -> b.localId().equals(authority())).findFirst().orElse(null);
    if (binding == null) return;
    listDiscovery = task(() -> {
      try {
        var reply = source.host(binding).authorityCodelists(new AuthorityCodelistRequest(
            AuthorityCodelistRequest.Operation.LIST, binding.localId(), null, null, null,
            null, 0, null, null, null, null), source.scope());
        var names = reply.codelists().entrySet().stream().filter(e -> e.getValue().size() > 0)
            .map(java.util.Map.Entry::getKey).sorted().toList();
        Platform.runLater(() -> {
          if (closed || revision != listRevision) return;
          codelistFilter.getItems().addAll(names);

        });
      } catch (Exception failure) {
        Platform.runLater(() -> { if (!closed && revision == listRevision)
          status.accept("Codelist filters unavailable: " + message(failure)); });
      }
    });
  }

  private TableColumn<AuthorityIdentity, String> column(String title, Function<AuthorityIdentity, String> value) {
    var column = new TableColumn<AuthorityIdentity, String>(title);
    column.setCellValueFactory(cell -> new ReadOnlyStringWrapper(value.apply(cell.getValue())));
    return column;
  }

  boolean busy() { return busy; }
  AuthorityIdentity selected() { return current ? results.getSelectionModel().getSelectedItem() : null; }
  String authority() { return chooser.getValue(); }
  void invalidate() {
    searchRevision++; documentRevision++; current = false; busy = false;
    deadline.stop(); documentDeadline.stop();
    if (search != null) search.cancel(true);
    if (document != null) document.cancel(true);
    results.getItems().clear(); documentation.getChildren().clear();
  }

  void search(String text) {
    invalidate();
    if (closed || authority() == null) { status.accept(""); changed.run(); return; }
    if (text.strip().codePointCount(0, text.strip().length()) < 2) {
      results.setPlaceholder(new Label("Type at least two characters to search")); status.accept(""); changed.run(); return;
    }
    String filter = codelistFilter.getValue();
    final String selectedFilter = ALL_CODELISTS.equals(filter) ? null : filter;
    long revision = searchRevision;
    var binding = source.bindings().stream().filter(b -> b.localId().equals(authority())).findFirst().orElseThrow();
    busy = true; status.accept("Searching " + authority() + "…"); changed.run(); deadline.playFromStart();
    search = task(() -> {
      try {
        var reply = source.host(binding).searchAuthority(new AuthoritySearchRequest(binding.localId(), text.strip(), selectedFilter, 0, 100), source.scope());
        if (reply == null) throw new IllegalStateException("The Reasoner returned no authority response");
        Platform.runLater(() -> {
          if (closed || revision != searchRevision) return;
          busy = false; deadline.stop();
          current = reply.status() == AuthoritySearchResponse.Status.OK;
          results.getItems().setAll(current ? reply.matches() : List.of());
          results.setPlaceholder(new Label(current ? "No matching identities" : "Authority search " + reply.status().name().toLowerCase()));
          status.accept(reply.notifications().isEmpty() ? (reply.nextOffset() >= 0 ? "Showing the first 100 identities. Refine the search for more." : "")
              : String.join("\n", reply.notifications().stream().map(n -> n.getMessage()).toList()));
          results.getSelectionModel().selectFirst(); changed.run();
        });
      } catch (Exception failure) {
        Platform.runLater(() -> { if (!closed && revision == searchRevision) {
          busy = false; deadline.stop(); status.accept("Authority search failed: " + message(failure)); changed.run();
        }});
      }
    });
  }

  private void loadDocumentation(AuthorityIdentity identity) {
    long revision = ++documentRevision;
    documentDeadline.stop();
    if (document != null) document.cancel(true);
    documentation.getChildren().clear();
    if (identity == null) return;
    render(identity.getDescription());
    var loading = new Label("Loading documentation…"); loading.setId("authority-doc-loading"); documentation.getChildren().add(loading);
    var binding = source.bindings().stream().filter(b -> b.localId().equals(authority())).findFirst().orElseThrow();
    documentDeadline.playFromStart();
    document = task(() -> {
      try {
        var host = source.host(binding);
        var media = host.getAuthorityDocumentation(binding.localId(), identity.getId(), source.scope());
        String markdown = null;
        var markdownUrl = media == null ? null : media.get("text/markdown");
        if (markdownUrl != null) markdown = readMarkdown(markdownUrl, host, source.scope());
        String content = markdown;
        Platform.runLater(() -> {
          if (closed || revision != documentRevision) return;
          documentDeadline.stop();
          documentation.getChildren().remove(loading);
          if (content != null) { documentation.getChildren().clear(); render(content); }
          if (media != null) media.forEach((type, url) -> {
            if (!List.of("http", "https").contains(url.getProtocol())) return;
            var link = new Hyperlink(type);
            link.setOnAction(e -> {
              var hostServices = org.integratedmodelling.klab.ide.utils.AppContext.getHostServices();
              if (hostServices != null) hostServices.showDocument(url.toExternalForm());
            });
            documentation.getChildren().add(link);
          });
        });
      } catch (Exception failure) {
        Platform.runLater(() -> { if (!closed && revision == documentRevision) {
          documentDeadline.stop();
          documentation.getChildren().remove(loading);
          var warning = new Label("Documentation unavailable: " + message(failure)); warning.setWrapText(true);
          documentation.getChildren().add(warning);
        }});
      }
    });
  }

  private void render(String markdown) {
    String text = markdown == null || markdown.isBlank() ? "No documentation available" : markdown;
    try { documentation.getChildren().add(BBCodeParser.createLayout(BBCodeNodeRenderer.fromMarkdown(text))); }
    catch (RuntimeException malformed) { var fallback = new Label(text); fallback.setWrapText(true); documentation.getChildren().add(fallback); }
  }

  /** Credentials go only to the Reasoner's content endpoint and never follow redirects. */
  static String readMarkdown(URL url, Reasoner host, UserScope scope) throws Exception {
    if (!List.of("http", "https").contains(url.getProtocol())) throw new IllegalArgumentException("Unsupported documentation URL");
    URI uri = url.toURI(), origin = host.getUrl().toURI();
    var request = HttpRequest.newBuilder(uri).timeout(java.time.Duration.ofSeconds(15)).GET();
    boolean content = Objects.equals(uri.getScheme(), origin.getScheme()) && Objects.equals(uri.getAuthority(), origin.getAuthority())
        && uri.getPath().equals(origin.getPath().replaceAll("/$", "") + ServicesAPI.REASONER.AUTHORITY_DOCUMENTATION_CONTENT);
    if (content && scope != null) {
      var identity = scope.getIdentity();
      request.header("Authorization", identity instanceof org.integratedmodelling.klab.api.identities.ServiceIdentity service ? service.getToken() : identity.getId());
      if (org.integratedmodelling.common.utils.Utils.URLs.isLocalHost(host.getUrl())) {
        var secret = Configuration.INSTANCE.getServiceSecret(KlabService.Type.REASONER);
        if (secret != null) request.header(ServicesAPI.SERVER_KEY_HEADER, secret);
      }
    }
    try (var http = HttpClient.newBuilder().connectTimeout(java.time.Duration.ofSeconds(10)).build()) {
      var reply = http.send(request.build(), HttpResponse.BodyHandlers.ofInputStream());
      try (var input = reply.body()) {
        if (reply.statusCode() != 200) throw new IllegalStateException("Documentation HTTP " + reply.statusCode());
        byte[] bytes = input.readNBytes(1_048_577);
        if (bytes.length > 1_048_576) throw new IllegalStateException("Documentation exceeds 1 MiB");
        return new String(bytes, StandardCharsets.UTF_8);
      }
    }
  }

  private static Future<?> task(Runnable action) { var task = new FutureTask<Void>(action, null); Thread.ofVirtual().start(task); return task; }
  private static String message(Throwable failure) { return Objects.toString(failure.getMessage(), failure.getClass().getSimpleName()); }
  @Override public void close() { closed = true; listRevision++; if (listDiscovery != null) listDiscovery.cancel(true); invalidate(); if (discovery != null) discovery.cancel(true); }
}
