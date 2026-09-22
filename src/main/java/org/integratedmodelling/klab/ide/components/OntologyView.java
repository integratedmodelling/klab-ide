package org.integratedmodelling.klab.ide.components;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import javafx.scene.control.Label;
import javafx.application.Platform;
import javafx.scene.Parent;
import javafx.scene.layout.VBox;
import org.integratedmodelling.klab.api.knowledge.Worldview;
import org.integratedmodelling.klab.api.services.Reasoner;
import org.integratedmodelling.klab.ide.KlabIDEController;
import org.integratedmodelling.klab.ide.Theme;
import org.integratedmodelling.klab.ide.components.generic.IconLabel;
import org.integratedmodelling.klab.ide.pages.BrowsablePage;

/** One persistent main tab for the worldview currently loaded by the connected reasoner. */
public class OntologyView extends BrowsablePage<WorldviewEditor, Worldview> {
  private final AtomicBoolean checking = new AtomicBoolean();
  private final AtomicBoolean recheck = new AtomicBoolean();
  private final ScheduledExecutorService monitor = Executors.newSingleThreadScheduledExecutor(
      Thread.ofPlatform().daemon().name("worldview-monitor").factory());
  private volatile boolean closed;
  private WorldviewEditor editor;
  private Reasoner currentReasoner;
  private String worldviewId;
  private long revision = -1;
  private String resourcesId;
  private String content;
  private boolean local;
  private volatile long generation;
  private final Supplier<WorldviewSource.Snapshot> source;

  public OntologyView() {
    this(() -> WorldviewSource.load(KlabIDEController.instance().user()), true);
  }

  OntologyView(Supplier<WorldviewSource.Snapshot> source, boolean monitorUpdates) {
    super("The Worldview Explorer is where you can browse the shared k.LAB knowledge",
        "Connect a reasoner with a loaded worldview to browse its concepts.");
    this.source = source;
    if (monitorUpdates) monitor.scheduleWithFixedDelay(() -> {
      if (!checking.get()) refresh();
    }, 3, 3, TimeUnit.SECONDS);
  }

  /** Called on engine updates; remote capabilities and worldview retrieval never block JavaFX. */
  public void refresh() {
    if (closed) return;
    if (!checking.compareAndSet(false, true)) { recheck.set(true); return; }
    long ticket = generation;
    Thread.ofVirtual().start(() -> {
      try {
        // Local preference is a user-level choice, independent of a remote focal digital twin.
        var selected = source.get();
        Platform.runLater(() -> {
          try {
            // A queued refresh must not starve a completed load during a stream of status events.
            if (closed || ticket != generation) return;
            if (selected == null || selected.worldview() == null) {
              reset();
              setDescription("The certificate worldview will open automatically when it becomes available.");
              return;
            }
            var reasoner = selected.reasoner();
            var worldview = selected.worldview();
            if (editor != null && currentReasoner == reasoner
                && Objects.equals(resourcesId, selected.resourcesId()) && local == selected.local()
                && Objects.equals(worldviewId, worldview.getUrn())) {
              editor.sourceAvailable();
              if (revision != selected.revision() || !Objects.equals(content, selected.content())) {
                revision = selected.revision();
                content = selected.content();
                editor.refreshWorldview(worldview);
                updateBrowser();
              }
              return;
            }
            reset();
            currentReasoner = reasoner;
            worldviewId = worldview.getUrn();
            revision = selected.revision();
            resourcesId = selected.resourcesId();
            content = selected.content();
            local = selected.local();
            editor = createWorldviewEditor(worldview, reasoner);
            addEditor(editor, Objects.toString(worldviewId, "Worldview") + (local ? " (local)" : ""), new IconLabel(Theme.WORLDVIEW_ICON, 16, "-color-fg-default"), false);
            hideBrowser();
            updateBrowser();
          } finally { checked(); }
        });
      } catch (Exception failure) {
        Platform.runLater(() -> {
          try {
            if (!closed && ticket == generation) {
              if (editor != null) editor.sourceUnavailable(failure.getMessage());
              else setDescription("Worldview unavailable: " + failure.getMessage());
            }
          } finally { checked(); }
        });
      }
    });
  }

  protected WorldviewEditor createWorldviewEditor(Worldview worldview, Reasoner reasoner) {
    return new WorldviewEditor(worldview, reasoner);
  }

  private void checked() {
    checking.set(false);
    if (recheck.getAndSet(false)) refresh();
  }

  public void close() {
    closed = true;
    monitor.shutdownNow();
    reset();
  }

  @Override public String getName() { return "Worldview"; }
  @Override public Parent getView() { return this; }
  @Override public void reset() {
    if (!Platform.isFxApplicationThread()) { Platform.runLater(this::reset); return; }
    generation++;
    if (editor != null) { editor.close(); removeEditor(editor); editor = null; }
    currentReasoner = null; worldviewId = null; revision = -1;
    resourcesId = null; content = null; local = false;
  }
  @Override protected void assetEditorSelected(WorldviewEditor editor) {}
  @Override protected void assetEditorClosed(WorldviewEditor editor) {}
  @Override protected void defineBrowser(VBox box) {
    box.getChildren().setAll(makeHeader("Worldview", new HeaderAction(Theme.WORLDVIEW_ICON,
        "Refresh worldview", this::refresh)));
    var info = new Label(editor == null
        ? "The certificate worldview opens automatically when available."
        : Objects.toString(editor.getEditedAsset().getUrn(), "Worldview")
            + (local ? " · Local services" : "") + "\n"
            + editor.getEditedAsset().getOntologies().size() + " ontologies\nRead-only worldview viewer");
    info.setWrapText(true);
    info.setStyle("-fx-padding: 12; -fx-background-color: -color-bg-subtle;");
    box.getChildren().add(info);
  }
  @Override public void show() {
    if (!Platform.isFxApplicationThread()) { Platform.runLater(this::show); return; }
    if (closed) return;
    hideBrowser();
    if (editor != null) selectEditor(editor);
    refresh();
  }
}
