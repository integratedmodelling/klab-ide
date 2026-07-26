package org.integratedmodelling.klab.ide.components;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.prefs.Preferences;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ChoiceDialog;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import org.integratedmodelling.klab.api.actors.Agent;
import org.integratedmodelling.klab.api.services.ResourcesService;
import org.integratedmodelling.klab.api.services.runtime.Notification;
import org.integratedmodelling.klab.ide.KlabIDEController;
import org.integratedmodelling.klab.ide.Theme;
import org.integratedmodelling.klab.ide.components.cards.BehaviorFileCard;
import org.integratedmodelling.klab.ide.pages.BrowsablePage;
import org.integratedmodelling.klab.modeler.model.NavigableKActorsBehavior;
import org.kordamp.ikonli.Ikon;
import org.kordamp.ikonli.javafx.FontIcon;

/** Browser and editor host for standalone k.Actors behaviors. */
public class AgentView extends BrowsablePage<BehaviorEditor, NavigableKActorsBehavior> {

  private static final int MAX_RECENT_FILES = 12;
  private static final String RECENTS_KEY = "recentBehaviorFiles";
  private static final String DIRECTORY_KEY = "lastBehaviorDirectory";
  private static final String TEMPLATE_RESOURCE =
      "/org/integratedmodelling/klab/ide/templates/behavior.kactor";

  private final Preferences preferences = Preferences.userNodeForPackage(AgentView.class);
  private final Map<Path, BehaviorEditor> openEditors = new LinkedHashMap<>();
  private final Set<Agent> debugAgents = new LinkedHashSet<>();
  private final List<Path> recentFiles = new ArrayList<>();
  private final List<Node> components = new ArrayList<>();
  private Agent currentDebugTarget;

  public AgentView() {
    super(
        "Choose or create a behavior using the top-left menu",
        "Behaviors, scripts, applications and test cases are created locally; they can be imported into projects once tested");

    loadRecents();
  }

  @Override
  public String getName() {
    return "Sessions";
  }

  @Override
  public Parent getView() {
    return this;
  }

  @Override
  public void reset() {
    updateBrowser();
  }

  @Override
  protected void assetEditorSelected(BehaviorEditor assetEditor) {}

  @Override
  protected void assetEditorClosed(BehaviorEditor editor) {
    openEditors.remove(editor.getFile());
    var removedDebugAgents = editor.getDebugAgents();
    debugAgents.removeAll(removedDebugAgents);
    if (removedDebugAgents.contains(currentDebugTarget)) {
      setDebugTarget(debugAgents.stream().findFirst().orElse(null));
    }
    editor.close();
  }

  @Override
  protected void defineBrowser(VBox browser) {
    browser.getChildren().removeAll(components);
    components.clear();
    Node header = makeHeader("Recent behaviors", this::chooseOpenOrCreate);
    Tooltip.install(header, new Tooltip("Open or create a .kactor behavior"));
    components.add(header);
    recentFiles.removeIf(path -> !Files.isRegularFile(path));
    for (Path path : recentFiles) {
      components.add(new BehaviorFileCard(path, this::openFile, this::forget));
    }
    browser.getChildren().addAll(components);
  }

  private void chooseOpenOrCreate() {
    var dialog = new ChoiceDialog<>("Open existing file", "Open existing file", "Create new file");
    dialog.setTitle("Behavior file");
    dialog.setHeaderText("Open an existing behavior or create one from the template");
    dialog.setContentText("Action:");
    dialog.initOwner(getScene() == null ? null : getScene().getWindow());
    dialog
        .showAndWait()
        .ifPresent(
            choice -> {
              if (choice.startsWith("Create")) createFile();
              else chooseFile();
            });
  }

  private FileChooser chooser(String title) {
    var chooser = new FileChooser();
    chooser.setTitle(title);
    chooser
        .getExtensionFilters()
        .addAll(
            new FileChooser.ExtensionFilter("k.Actors behaviors (*.kactor)", "*.kactor"),
            new FileChooser.ExtensionFilter("Legacy k.Actors behaviors (*.kactors)", "*.kactors"));
    String directory = preferences.get(DIRECTORY_KEY, "");
    if (!directory.isBlank() && Files.isDirectory(Path.of(directory))) {
      chooser.setInitialDirectory(Path.of(directory).toFile());
    }
    return chooser;
  }

  private void chooseFile() {
    File selected = chooser("Open k.Actors behavior").showOpenDialog(getScene().getWindow());
    if (selected != null) openFile(selected.toPath());
  }

  private void createFile() {
    var chooser = chooser("Create k.Actors behavior");
    chooser.setInitialFileName("behavior.kactor");
    File selected = chooser.showSaveDialog(getScene().getWindow());
    if (selected == null) return;
    Path path = withKActorExtension(selected.toPath()).toAbsolutePath().normalize();
    if (Files.exists(path)) {
      var confirmation =
          new Alert(
              Alert.AlertType.CONFIRMATION,
              "Replace existing file " + path.getFileName() + "?",
              ButtonType.YES,
              ButtonType.CANCEL);
      confirmation.initOwner(getScene().getWindow());
      if (confirmation.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.YES) return;
    }
    try {
      String name =
          LocalBehavior.stripExtension(path.getFileName().toString())
              .replaceAll("[^A-Za-z0-9_.]", "_");
      Files.writeString(path, templateFor(name), StandardCharsets.UTF_8);
      openFile(path);
    } catch (IOException e) {
      KlabIDEController.instance().handleNotification(Notification.error(e));
    }
  }

  private Path withKActorExtension(Path path) {
    String name = path.getFileName().toString().toLowerCase();
    return name.endsWith(".kactor") || name.endsWith(".kactors")
        ? path
        : path.resolveSibling(path.getFileName() + ".kactor");
  }

  private String templateFor(String behaviorName) throws IOException {
    try (var input = AgentView.class.getResourceAsStream(TEMPLATE_RESOURCE)) {
      if (input == null) throw new IOException("Missing behavior template " + TEMPLATE_RESOURCE);
      return new String(input.readAllBytes(), StandardCharsets.UTF_8)
          .replace("${name}", behaviorName);
    }
  }

  /** Opens a behavior from the SessionView UI or any external IDE action. */
  public void openFile(File file) {
    if (file != null) openFile(file.toPath());
  }

  /** Opens a behavior from the SessionView UI or any external IDE action. */
  public void openFile(Path requestedPath) {
    if (requestedPath == null) return;
    Path path = requestedPath.toAbsolutePath().normalize();
    String lower = path.getFileName().toString().toLowerCase();
    if (!Files.isRegularFile(path) || !(lower.endsWith(".kactor") || lower.endsWith(".kactors"))) {
      KlabIDEController.instance()
          .alert(Notification.error("Not a readable .kactor file: " + path));
      return;
    }
    remember(path);
    hideBrowser();
    var existing = openEditors.get(path);
    if (existing != null) {
      selectEditor(existing);
      return;
    }
    try {
      //      String source = Files.readString(path, StandardCharsets.UTF_8);
      var behavior =
          KlabIDEController.instance()
              .user()
              .getService(ResourcesService.class)
              .readBehavior(path.toUri().toURL(), KlabIDEController.instance().user());
      var editor =
          new BehaviorEditor(
              path,
              behavior,
              this::remember,
              icon -> updateEditorIcon(path, icon),
              this::registerDebugAgent,
              this::setDebugTarget,
              this::unregisterDebugAgent);
      openEditors.put(path, editor);
      addEditor(editor, path.getFileName().toString(), new FontIcon(Theme.getIcon(behavior)));
    } catch (IOException e) {
      KlabIDEController.instance().handleNotification(Notification.error(e));
    }
  }

  private void updateEditorIcon(Path path, Ikon icon) {
    var editor = openEditors.get(path);
    if (editor != null) {
      setEditorGraphic(editor, new FontIcon(icon));
    }
  }

  private void registerDebugAgent(Agent agent) {
    if (agent != null && debugAgents.add(agent)) {
      setDebugTarget(agent);
    }
  }

  private void unregisterDebugAgent(Agent agent) {
    if (agent == null || !debugAgents.remove(agent)) {
      return;
    }
    if (currentDebugTarget == agent) {
      setDebugTarget(debugAgents.stream().findFirst().orElse(null));
    }
  }

  private void setDebugTarget(Agent agent) {
    if (agent != null && !debugAgents.contains(agent)) {
      return;
    }
    currentDebugTarget = agent;
    openEditors.values().forEach(editor -> editor.setCurrentDebugTarget(agent));
  }

  /** The agent selected as the target for future debugger components, or {@code null}. */
  public Agent getCurrentDebugTarget() {
    return currentDebugTarget;
  }

  private void remember(Path path) {
    path = path.toAbsolutePath().normalize();
    recentFiles.remove(path);
    recentFiles.add(0, path);
    if (recentFiles.size() > MAX_RECENT_FILES) {
      recentFiles.subList(MAX_RECENT_FILES, recentFiles.size()).clear();
    }
    if (path.getParent() != null) preferences.put(DIRECTORY_KEY, path.getParent().toString());
    saveRecents();
    updateBrowser();
  }

  private void forget(Path path) {
    recentFiles.remove(path.toAbsolutePath().normalize());
    saveRecents();
    updateBrowser();
  }

  private void loadRecents() {
    String stored = preferences.get(RECENTS_KEY, "");
    if (!stored.isBlank()) {
      Arrays.stream(stored.split("\\n"))
          .filter(value -> !value.isBlank())
          .map(Path::of)
          .map(path -> path.toAbsolutePath().normalize())
          .distinct()
          .limit(MAX_RECENT_FILES)
          .forEach(recentFiles::add);
    }
  }

  private void saveRecents() {
    preferences.put(
        RECENTS_KEY, String.join("\n", recentFiles.stream().map(Path::toString).toList()));
  }
}
