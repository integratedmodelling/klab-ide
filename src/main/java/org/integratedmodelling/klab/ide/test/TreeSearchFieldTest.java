package org.integratedmodelling.klab.ide.test;

import atlantafx.base.theme.PrimerLight;
import javafx.application.Application;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.Stage;
import org.integratedmodelling.klab.ide.components.generic.TreeSearchField;

/**
 * Showcase / manual-test application for {@link TreeSearchField}.
 *
 * <p>The left pane shows the search field wired to a tree of technologies. The right pane provides
 * search-term suggestions and a checklist of behaviours to verify.
 *
 * <p>Run via {@code main()} – no extra arguments required.
 */
public class TreeSearchFieldTest extends Application {

  @Override
  public void start(Stage stage) {
    Application.setUserAgentStylesheet(new PrimerLight().getUserAgentStylesheet());

    // ── Tree ──────────────────────────────────────────────────────────────
    TreeTableView<String> treeView = buildTree();
    treeView.setShowRoot(false);
    VBox.setVgrow(treeView, Priority.ALWAYS);

    // ── Search field: case-insensitive substring match ────────────────────
    var searchField =
        new TreeSearchField<>(
            treeView, (text, item) -> item.toLowerCase().contains(text.toLowerCase()));
    searchField.setMaxWidth(Double.MAX_VALUE);

    // ── Left pane ─────────────────────────────────────────────────────────
    var leftPane = new VBox(6, searchField, treeView);
    leftPane.setPadding(new Insets(10));
    VBox.setVgrow(leftPane, Priority.ALWAYS);

    // ── Right pane (hints & checklist) ────────────────────────────────────
    var rightPane = buildRightPane();
    rightPane.setPrefWidth(235);
    rightPane.setPadding(new Insets(10, 12, 10, 8));

    // ── Root layout ───────────────────────────────────────────────────────
    var center = new HBox(leftPane, new Separator(Orientation.VERTICAL), rightPane);
    HBox.setHgrow(leftPane, Priority.ALWAYS);

    var statusBar =
        new Label("Click the search field or the search icon on its right to activate.");
    statusBar.setPadding(new Insets(4, 8, 4, 8));
    statusBar.setStyle("-fx-background-color: -color-bg-subtle;");
    statusBar.setMaxWidth(Double.MAX_VALUE);

    var root = new BorderPane();
    root.setCenter(center);
    root.setBottom(statusBar);

    var scene = new Scene(root, 700, 580);
    stage.setTitle("TreeSearchField — Demo & Test");
    stage.setScene(scene);
    stage.show();
  }

  // ── Tree construction ─────────────────────────────────────────────────────

  private static TreeTableView<String> buildTree() {
    var root = new TreeItem<>("Root");
    root.setExpanded(true);

    root.getChildren()
        .addAll(
            category(
                "Programming Languages",
                category("JVM", leaf("Java"), leaf("Kotlin"), leaf("Scala"), leaf("Groovy")),
                category(
                    "Web",
                    leaf("JavaScript"),
                    leaf("TypeScript"),
                    leaf("Dart"),
                    leaf("CoffeeScript")),
                category("Systems", leaf("C"), leaf("C++"), leaf("Rust"), leaf("Go"), leaf("Zig"))),
            category(
                "Frameworks",
                category(
                    "Backend",
                    leaf("Spring Boot"),
                    leaf("Quarkus"),
                    leaf("Micronaut"),
                    leaf("Vert.x")),
                category("Frontend", leaf("React"), leaf("Angular"), leaf("Vue"), leaf("Svelte")),
                category(
                    "Mobile",
                    leaf("Flutter"),
                    leaf("React Native"),
                    leaf("Ionic"),
                    leaf("Capacitor"))),
            category(
                "Tools",
                category("Build", leaf("Maven"), leaf("Gradle"), leaf("Ant"), leaf("Bazel")),
                category(
                    "IDE",
                    leaf("IntelliJ IDEA"),
                    leaf("Eclipse"),
                    leaf("NetBeans"),
                    leaf("VS Code")),
                category(
                    "Version Control",
                    leaf("Git"),
                    leaf("Mercurial"),
                    leaf("SVN"),
                    leaf("Perforce"))));

    var column = new TreeTableColumn<String, String>("Name");
    column.setCellValueFactory(param -> new ReadOnlyStringWrapper(param.getValue().getValue()));
    column.setPrefWidth(500);
    var ret = new TreeTableView<>(root);
    ret.getColumns().add(column);
    return ret;
  }

  @SafeVarargs
  private static TreeItem<String> category(String name, TreeItem<String>... children) {
    var item = new TreeItem<>(name);
    item.setExpanded(false);
    item.getChildren().addAll(children);
    return item;
  }

  private static TreeItem<String> leaf(String name) {
    return new TreeItem<>(name);
  }

  // ── Right pane ────────────────────────────────────────────────────────────

  private static VBox buildRightPane() {
    var pane = new VBox(10);

    pane.getChildren().add(sectionLabel("Usage"));
    pane.getChildren().add(step("1.", "Click the field or the search icon to activate."));
    pane.getChildren()
        .add(step("2.", "Type to filter. Parent nodes of matching items are always kept."));
    pane.getChildren().add(step("3.", "Backspace to empty → full tree is restored."));
    pane.getChildren().add(step("4.", "Esc → deactivates and hands focus to the tree."));

    pane.getChildren().add(new Separator());
    pane.getChildren().add(sectionLabel("Suggested search terms"));
    for (String term :
        new String[] {
          "\"java\"",
          "\"script\"",
          "\"react\"",
          "\"maven\"",
          "\"ion\"",
          "\"c\"",
          "\"go\"",
          "\"vert\""
        }) {
      pane.getChildren().add(hint(term));
    }

    pane.getChildren().add(new Separator());
    pane.getChildren().add(sectionLabel("Behaviours to verify"));
    pane.getChildren().add(step("✓", "Field starts grayed-out and non-editable."));
    pane.getChildren().add(step("✓", "Only icon or field click activates editing."));
    pane.getChildren().add(step("✓", "Category parents of matches are preserved."));
    pane.getChildren().add(step("✓", "Empty field restores the original tree."));
    pane.getChildren().add(step("✓", "Esc returns focus to the tree view."));

    return pane;
  }

  private static Label sectionLabel(String text) {
    var l = new Label(text);
    l.setFont(Font.font(null, FontWeight.BOLD, 12));
    return l;
  }

  private static HBox step(String bullet, String text) {
    var b = new Label(bullet);
    b.setMinWidth(22);
    b.setAlignment(Pos.TOP_LEFT);
    var t = new Label(text);
    t.setWrapText(true);
    t.setMaxWidth(190);
    t.setStyle("-fx-font-size: 11;");
    var row = new HBox(4, b, t);
    row.setAlignment(Pos.TOP_LEFT);
    return row;
  }

  private static Label hint(String term) {
    var l = new Label("  " + term);
    l.setStyle("-fx-font-family: monospace; -fx-font-size: 11; -fx-text-fill: -color-accent-fg;");
    return l;
  }

  public static void main(String[] args) {
    launch(args);
  }
}
