package org.integratedmodelling.klab.ide.components.cards;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import javafx.geometry.Bounds;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.OverrunStyle;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.Tooltip;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Popup;
import org.integratedmodelling.klab.api.collections.Parameters;
import org.integratedmodelling.klab.api.data.Metadata;
import org.integratedmodelling.klab.ide.components.generic.IconLabel;
import org.kordamp.ikonli.carbonicons.CarbonIcons;
import org.kordamp.ikonli.material2.Material2AL;
import org.kordamp.ikonli.material2.Material2MZ;

/**
 * Compact card visualization and optional editor for k.LAB {@link Parameters}.
 *
 * <p>The card is intended for small inspector panels where metadata must remain readable without
 * letting long values resize the component. It renders a scrollable list of key/value rows and
 * styles plain data values differently for strings, numbers, booleans, and {@code null}.
 *
 * <p>Usage:
 *
 * <pre>{@code
 * MetadataCard card =
 *     new MetadataCard(
 *         metadata,
 *         new MetadataCard.Options()
 *             .pathTree(true)
 *             .inlineStringLimit(80)
 *             .unsupportedValuePolicy(MetadataCard.UnsupportedValuePolicy.SHOW_AS_STRING)
 *             .complexValueRenderer((key, value) -> customNodeOrNull)
 *             .editHandler((key, oldValue, editedValue) -> saveEditedValue(key, editedValue)));
 * }</pre>
 *
 * <p>Options:
 *
 * <ul>
 *   <li>{@link Options#pathTree(boolean)} switches between flat key display and a tree view built
 *       from dot-separated key path segments.
 *   <li>{@link Options#unsupportedValuePolicy(UnsupportedValuePolicy)} chooses whether complex
 *       values without a custom renderer are skipped or shown as red string values.
 *   <li>{@link Options#complexValueRenderer(ComplexValueRenderer)} lets callers supply a JavaFX
 *       node for complex values. Returning {@code null} delegates to the unsupported-value policy.
 *   <li>{@link Options#editHandler(EditHandler)} enables inline editing of plain data values in
 *       either flat or tree mode. The card never mutates the backing {@link Parameters}; it reports
 *       confirmed edits through the callback and updates its displayed value only when the callback
 *       returns {@code true}.
 *   <li>{@link Options#inlineStringLimit(int)} controls how many characters are shown inline before
 *       long strings are clipped and made available through a popup text viewer.
 *   <li>{@link Options#stringPopupSize(double, double)} controls the size of the long-string popup
 *       viewer.
 * </ul>
 *
 * <p>Editable values preserve the original value type when possible: numeric entries are parsed
 * back to their original wrapper type, boolean entries are edited with a switch, enum entries are
 * edited with a {@link ComboBox}, and strings are passed through as entered. Accepted edits are
 * retained for display even if the caller does not update the backing {@link Parameters}.
 */
public class MetadataCard extends BaseCard<Parameters<?>> {

  private static final double DEFAULT_WIDTH = 320;
  private static final double DEFAULT_HEIGHT = 220;
  private static final double TREE_KEY_COLUMN_WIDTH = 150;
  private static final double TREE_INDENT_WIDTH = 12;
  private static final double TREE_TOGGLE_WIDTH = 12;

  private final Options options;
  private final Set<String> collapsedPaths = new TreeSet<>();
  private final Map<String, Object> acceptedEdits = new LinkedHashMap<>();
  private VBox rows;
  private Button treeExpansionButton;
  private Button viewToggleButton;

  public MetadataCard(Parameters<?> metadata) {
    this(metadata, new Options());
  }

  public MetadataCard(Parameters<?> metadata, Options options) {
    this(metadata, options, false);
  }

  public MetadataCard(Parameters<?> metadata, Options options, boolean extended) {
    super(metadata == null ? Metadata.create() : metadata, extended, false);
    this.options = new Options(options);
    drawContent();
  }

  public Options getOptions() {
    return new Options(options);
  }

  public void setPathTree(boolean pathTree) {
    options.pathTree(pathTree);
    collapsedPaths.clear();
    renderRows();
    updateHeader();
  }

  public void setUnsupportedValuePolicy(UnsupportedValuePolicy policy) {
    options.unsupportedValuePolicy(policy);
    renderRows();
    updateHeader();
  }

  @Override
  protected void drawContent() {
    getStyleClass().add("metadata-card");
    setPadding(new Insets(8));
    setMinSize(220, 150);
    setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
    if (extended) {
      setPrefSize(Region.USE_COMPUTED_SIZE, Region.USE_COMPUTED_SIZE);
    } else {
      setPrefSize(DEFAULT_WIDTH, DEFAULT_HEIGHT);
    }

    VBox content = new VBox(6);
    content.getStyleClass().add("metadata-card-content");
    content.setFillWidth(true);

    rows = new VBox(2);
    rows.getStyleClass().add("metadata-card-rows");

    ScrollPane scrollPane = new ScrollPane(rows);
    scrollPane.getStyleClass().add("metadata-card-scroll");
    scrollPane.setFitToWidth(true);
    scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
    scrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
    scrollPane.setMinHeight(80);

    content.getChildren().addAll(createHeader(), scrollPane);
    VBox.setVgrow(scrollPane, Priority.ALWAYS);
    setCenter(content);

    renderRows();
    updateHeader();
  }

  private Node createHeader() {
    Label title = new Label(options.title);
    title.getStyleClass().add("metadata-card-title");

    HBox spacer = new HBox();
    HBox.setHgrow(spacer, Priority.ALWAYS);
    treeExpansionButton = createTreeExpansionButton();
    viewToggleButton = createViewToggleButton();

    HBox header = new HBox(5, title, spacer, treeExpansionButton, viewToggleButton);
    header.setAlignment(Pos.CENTER_LEFT);
    header.setMinHeight(22);
    return header;
  }

  private Button createTreeExpansionButton() {
    Button button = createHeaderIconButton();
    button.setOnAction(event -> toggleTreeExpansion());
    return button;
  }

  private Button createViewToggleButton() {
    Button button = createHeaderIconButton();
    button.setOnAction(event -> setPathTree(!options.pathTree()));
    return button;
  }

  private Button createHeaderIconButton() {
    Button button = new Button();
    button.getStyleClass().add("metadata-card-header-button");
    button.setCursor(Cursor.HAND);
    button.setFocusTraversable(false);
    button.setMinSize(22, 22);
    button.setPrefSize(22, 22);
    button.setMaxSize(22, 22);
    return button;
  }

  private void updateHeader() {
    if (viewToggleButton == null) {
      return;
    }
    boolean tree = options.pathTree();
    updateTreeExpansionButton(tree);
    viewToggleButton.setGraphic(
        new IconLabel(
            tree ? Material2MZ.VIEW_LIST : Material2AL.ACCOUNT_TREE, 13, "-color-fg-muted"));
    String action = tree ? "Switch to flat view" : "Switch to tree view";
    viewToggleButton.setTooltip(new Tooltip(action));
    viewToggleButton.setAccessibleText(action);
  }

  private void updateTreeExpansionButton(boolean tree) {
    if (treeExpansionButton == null) {
      return;
    }
    treeExpansionButton.setVisible(tree);
    treeExpansionButton.setManaged(tree);
    if (!tree) {
      return;
    }
    Set<String> collapsiblePaths = collapsibleTreePaths();
    boolean allCollapsed =
        !collapsiblePaths.isEmpty() && collapsedPaths.containsAll(collapsiblePaths);
    String action = allCollapsed ? "Expand all paths" : "Collapse all paths";
    treeExpansionButton.setGraphic(
        new IconLabel(
            allCollapsed ? CarbonIcons.EXPAND_ALL : CarbonIcons.COLLAPSE_ALL,
            13,
            "-color-fg-muted"));
    treeExpansionButton.setTooltip(new Tooltip(action));
    treeExpansionButton.setAccessibleText(action);
    treeExpansionButton.setDisable(collapsiblePaths.isEmpty());
  }

  private void toggleTreeExpansion() {
    if (!options.pathTree()) {
      return;
    }
    Set<String> collapsiblePaths = collapsibleTreePaths();
    if (collapsiblePaths.isEmpty()) {
      return;
    }
    if (collapsedPaths.containsAll(collapsiblePaths)) {
      collapsedPaths.clear();
    } else {
      collapsedPaths.clear();
      collapsedPaths.addAll(collapsiblePaths);
    }
    renderRows();
    updateHeader();
  }

  private void renderRows() {
    if (rows == null) {
      return;
    }
    rows.getChildren().clear();
    List<Entry> entries = visibleEntries();
    if (entries.isEmpty()) {
      Label empty = new Label(options.emptyTitle);
      empty.getStyleClass().addAll("metadata-card-empty", "metadata-value-null");
      rows.getChildren().add(empty);
      return;
    }

    if (options.pathTree()) {
      renderTree(entries);
    } else {
      for (Entry entry : entries) {
        rows.getChildren().add(createFlatRow(entry));
      }
    }
  }

  private List<Entry> visibleEntries() {
    List<Entry> entries = new ArrayList<>();
    for (Map.Entry<?, ?> entry : asset.entrySet()) {
      String key = entry.getKey().toString();
      Object value =
          acceptedEdits.containsKey(key) ? acceptedEdits.get(key) : asset.get(entry.getKey());
      Entry metadataEntry = Entry.of(key, value);
      if (metadataEntry.kind() == ValueKind.COMPLEX
          && options.complexValueRenderer().render(key, value) == null
          && options.unsupportedValuePolicy() == UnsupportedValuePolicy.SKIP) {
        continue;
      }
      entries.add(metadataEntry);
    }
    return entries;
  }

  private Node createFlatRow(Entry entry) {
    HBox row = new HBox(7);
    row.getStyleClass().add("metadata-card-row");
    row.setAlignment(Pos.CENTER_LEFT);

    Label key = keyLabel(entry.key());
    key.setMinWidth(118);
    key.setPrefWidth(118);
    key.setMaxWidth(118);

    Node value = valueNode(entry, true);
    constrainValueColumn(value);
    HBox.setHgrow(value, Priority.ALWAYS);
    row.getChildren().addAll(key, value);
    return row;
  }

  private void renderTree(List<Entry> entries) {
    TreeItem root = treeRoot(entries);
    List<TreeItem> children = root.children();
    children.sort(Comparator.comparing(TreeItem::name));
    for (TreeItem child : children) {
      renderTreeItem(child, 0);
    }
  }

  private TreeItem treeRoot(List<Entry> entries) {
    TreeItem root = new TreeItem("", "");
    entries.forEach(root::add);
    return root;
  }

  private Set<String> collapsibleTreePaths() {
    Set<String> paths = new TreeSet<>();
    collectCollapsibleTreePaths(treeRoot(visibleEntries()), paths);
    return paths;
  }

  private void collectCollapsibleTreePaths(TreeItem item, Set<String> paths) {
    if (!item.path().isBlank() && !item.children().isEmpty()) {
      paths.add(item.path());
    }
    for (TreeItem child : item.children()) {
      collectCollapsibleTreePaths(child, paths);
    }
  }

  private void renderTreeItem(TreeItem item, int depth) {
    if (!item.path().isBlank()) {
      rows.getChildren().add(createTreeRow(item, depth));
    }
    if (!item.children().isEmpty() && !collapsedPaths.contains(item.path())) {
      List<TreeItem> children = item.children();
      children.sort(Comparator.comparing(TreeItem::name));
      for (TreeItem child : children) {
        renderTreeItem(child, depth + 1);
      }
    }
  }

  private Node createTreeRow(TreeItem item, int depth) {
    HBox row = new HBox(5);
    row.getStyleClass().add("metadata-card-row");
    row.setAlignment(Pos.CENTER_LEFT);
    row.setPadding(new Insets(2, 4, 2, 4));

    row.getChildren().add(createTreeKeyCell(item, depth));
    if (item.entry() != null) {
      Node value = valueNode(item.entry(), true);
      constrainValueColumn(value);
      HBox.setHgrow(value, Priority.ALWAYS);
      row.getChildren().add(value);
    } else {
      Label group = new Label(item.children().size() + " nested");
      group.getStyleClass().add("metadata-card-group");
      row.getChildren().add(group);
    }
    return row;
  }

  private void constrainValueColumn(Node value) {
    if (value instanceof Region region) {
      region.setMinWidth(0);
      region.setPrefWidth(0);
      region.setMaxWidth(Double.MAX_VALUE);
    }
  }

  private Node createTreeKeyCell(TreeItem item, int depth) {
    HBox keyCell = new HBox(5);
    keyCell.getStyleClass().add("metadata-tree-key-cell");
    keyCell.setAlignment(Pos.CENTER_LEFT);
    keyCell.setMinWidth(TREE_KEY_COLUMN_WIDTH);
    keyCell.setPrefWidth(TREE_KEY_COLUMN_WIDTH);
    keyCell.setMaxWidth(TREE_KEY_COLUMN_WIDTH);

    Label toggle =
        new Label(
            item.children().isEmpty() ? "" : collapsedPaths.contains(item.path()) ? "+" : "-");
    toggle.getStyleClass().add("metadata-tree-toggle");
    toggle.setMinWidth(TREE_TOGGLE_WIDTH);
    toggle.setPrefWidth(TREE_TOGGLE_WIDTH);
    toggle.setMaxWidth(TREE_TOGGLE_WIDTH);
    toggle.setAlignment(Pos.CENTER);
    if (!item.children().isEmpty()) {
      toggle.setCursor(Cursor.HAND);
      toggle.setOnMouseClicked(
          event -> {
            if (collapsedPaths.contains(item.path())) {
              collapsedPaths.remove(item.path());
            } else {
              collapsedPaths.add(item.path());
            }
            renderRows();
            updateHeader();
          });
    }

    Label key = keyLabel(item.name());
    key.setMinWidth(0);
    key.setMaxWidth(Double.MAX_VALUE);
    HBox.setHgrow(key, Priority.ALWAYS);

    keyCell.getChildren().addAll(createTreeIndent(depth), toggle, key);
    return keyCell;
  }

  private Node createTreeIndent(int depth) {
    HBox indent = new HBox(0);
    indent.getStyleClass().add("metadata-tree-indent");
    double width = treeIndentWidth(depth);
    indent.setMinWidth(width);
    indent.setPrefWidth(width);
    indent.setMaxWidth(width);
    int guides = (int) Math.floor(width / TREE_INDENT_WIDTH);
    for (int i = 0; i < guides; i++) {
      Region guide = new Region();
      guide.getStyleClass().add("metadata-tree-guide");
      guide.setMinWidth(TREE_INDENT_WIDTH);
      guide.setPrefWidth(TREE_INDENT_WIDTH);
      guide.setMaxWidth(TREE_INDENT_WIDTH);
      indent.getChildren().add(guide);
    }
    return indent;
  }

  private double treeIndentWidth(int depth) {
    return Math.min(depth * TREE_INDENT_WIDTH, TREE_KEY_COLUMN_WIDTH - TREE_TOGGLE_WIDTH - 34);
  }

  private Label keyLabel(String text) {
    Label label = new Label(text);
    label.getStyleClass().add("metadata-card-key");
    label.setTextOverrun(OverrunStyle.CENTER_ELLIPSIS);
    label.setTooltip(new Tooltip(text));
    label.setMinWidth(0);
    return label;
  }

  private Node valueNode(Entry entry, boolean editable) {
    if (entry.kind() == ValueKind.COMPLEX) {
      Node rendered = options.complexValueRenderer().render(entry.key(), entry.value());
      if (rendered != null) {
        StackPane wrapper = new StackPane(rendered);
        wrapper.getStyleClass().add("metadata-card-custom-value");
        wrapper.setAlignment(Pos.CENTER_LEFT);
        wrapper.setMinWidth(0);
        wrapper.setPrefWidth(0);
        wrapper.setMaxWidth(Double.MAX_VALUE);
        return wrapper;
      }
      return unsupportedNode(entry);
    }

    Label value = valueLabel(entry);
    value.setMinWidth(0);
    value.setPrefWidth(0);
    value.setMaxWidth(Double.MAX_VALUE);
    HBox.setHgrow(value, Priority.ALWAYS);
    boolean longString =
        entry.kind() == ValueKind.STRING
            && entry.displayValue().length() > options.inlineStringLimit();
    if (longString) {
      value.setTooltip(
          new Tooltip(
              (editable && options.editHandler() != null
                      ? "Click to edit; right-click to view full string"
                      : "Click to view full string")
                  + "\n"
                  + clipped(entry.displayValue(), 240)));
    }

    StackPane wrapper = new StackPane(value);
    wrapper.setAlignment(Pos.CENTER_LEFT);
    wrapper.setMinWidth(0);
    wrapper.setPrefWidth(0);
    wrapper.setMaxWidth(Double.MAX_VALUE);
    if (longString) {
      wrapper.setCursor(Cursor.HAND);
      wrapper.setOnMouseClicked(
          event -> {
            if (editable
                && options.editHandler() != null
                && event.getButton() == MouseButton.PRIMARY) {
              beginEdit(wrapper, entry);
            } else if (event.getButton() == MouseButton.PRIMARY
                || event.getButton() == MouseButton.SECONDARY) {
              showStringPopup(wrapper, entry);
            }
          });
    } else if (editable && entry.isPod() && options.editHandler() != null) {
      wrapper.setCursor(entry.kind() == ValueKind.BOOLEAN ? Cursor.HAND : Cursor.TEXT);
      wrapper.setOnMouseClicked(
          event -> {
            if (event.getButton() == MouseButton.PRIMARY) {
              beginEdit(wrapper, entry);
            }
          });
    }
    return wrapper;
  }

  private Label valueLabel(Entry entry) {
    String text =
        switch (entry.kind()) {
          case STRING -> clipped(entry.displayValue(), options.inlineStringLimit());
          case NUMBER, BOOLEAN, NULL -> entry.displayValue();
          case COMPLEX -> entry.displayValue();
        };
    Label label = new Label(text);
    label.getStyleClass().addAll("metadata-card-value", entry.cssClass());
    label.setTextOverrun(OverrunStyle.ELLIPSIS);
    label.setTooltip(new Tooltip(tooltipText(entry)));
    return label;
  }

  private Node unsupportedNode(Entry entry) {
    Label label = new Label(Objects.toString(entry.value(), "null"));
    label.getStyleClass().addAll("metadata-card-value", "metadata-value-unsupported");
    label.setTextOverrun(OverrunStyle.ELLIPSIS);
    label.setTooltip(new Tooltip("Unsupported value\n" + entry.value().getClass().getName()));
    label.setMinWidth(0);
    label.setPrefWidth(0);
    label.setMaxWidth(Double.MAX_VALUE);
    return label;
  }

  private void beginEdit(StackPane wrapper, Entry entry) {
    if (entry.value() instanceof Enum<?>) {
      beginEnumEdit(wrapper, entry);
      return;
    }
    if (entry.value() instanceof Boolean) {
      beginBooleanEdit(wrapper, entry);
      return;
    }

    TextField editor = new TextField(entry.editValue());
    editor.getStyleClass().add("metadata-card-editor");
    editor.setMinWidth(0);
    editor.setMaxWidth(Double.MAX_VALUE);
    wrapper.getChildren().setAll(editor);
    editor.requestFocus();
    editor.selectAll();
    editor.setOnKeyPressed(
        event -> {
          if (event.getCode() == KeyCode.ENTER || event.getCode() == KeyCode.TAB) {
            commitEdit(editor, entry);
            event.consume();
          } else if (event.getCode() == KeyCode.ESCAPE) {
            renderRows();
            event.consume();
          }
        });
  }

  private void beginBooleanEdit(StackPane wrapper, Entry entry) {
    ToggleButton editor = new ToggleButton();
    editor.getStyleClass().addAll("metadata-card-editor", "metadata-card-boolean-switch");
    editor.setMinSize(48, 20);
    editor.setPrefSize(54, 20);
    editor.setMaxSize(54, 20);
    editor.setCursor(Cursor.HAND);
    editor.setFocusTraversable(true);
    editor.setSelected(Boolean.TRUE.equals(entry.value()));
    updateBooleanSwitchText(editor);
    boolean[] completed = {false};

    editor
        .selectedProperty()
        .addListener((observable, oldValue, newValue) -> updateBooleanSwitchText(editor));
    editor.setOnAction(event -> finishBooleanEdit(editor, entry, completed, true));
    editor.setOnKeyPressed(
        event -> {
          if (event.getCode() == KeyCode.ENTER || event.getCode() == KeyCode.TAB) {
            finishBooleanEdit(editor, entry, completed, true);
            event.consume();
          } else if (event.getCode() == KeyCode.ESCAPE) {
            finishBooleanEdit(editor, entry, completed, false);
            event.consume();
          }
        });
    editor
        .focusedProperty()
        .addListener(
            (observable, wasFocused, isFocused) -> {
              if (wasFocused && !isFocused) {
                finishBooleanEdit(editor, entry, completed, true);
              }
            });

    wrapper.getChildren().setAll(editor);
    editor.requestFocus();
  }

  private void updateBooleanSwitchText(ToggleButton editor) {
    editor.setText(editor.isSelected() ? "true" : "false");
  }

  private void finishBooleanEdit(
      ToggleButton editor, Entry entry, boolean[] completed, boolean commit) {
    if (completed[0]) {
      return;
    }
    completed[0] = true;
    Boolean editedValue = editor.isSelected();
    if (commit && !Objects.equals(editedValue, entry.value())) {
      acceptEdit(entry, editedValue);
    }
    renderRows();
  }

  private void beginEnumEdit(StackPane wrapper, Entry entry) {
    ComboBox<Object> editor = new ComboBox<>();
    editor.getStyleClass().add("metadata-card-editor");
    editor.setMinWidth(0);
    editor.setMaxWidth(Double.MAX_VALUE);
    editor.setFocusTraversable(true);
    boolean[] completed = {false};

    Object[] constants = entry.value().getClass().getEnumConstants();
    if (constants != null) {
      editor.getItems().addAll(constants);
    }
    editor.setValue(entry.value());

    editor.setOnKeyPressed(
        event -> {
          if (event.getCode() == KeyCode.ENTER || event.getCode() == KeyCode.TAB) {
            finishEnumEdit(editor, entry, completed, true);
            event.consume();
          } else if (event.getCode() == KeyCode.ESCAPE) {
            finishEnumEdit(editor, entry, completed, false);
            event.consume();
          }
        });
    editor.setOnHidden(event -> finishEnumEdit(editor, entry, completed, true));
    editor
        .focusedProperty()
        .addListener(
            (observable, wasFocused, isFocused) -> {
              if (wasFocused && !isFocused && !editor.isShowing()) {
                finishEnumEdit(editor, entry, completed, true);
              }
            });

    wrapper.getChildren().setAll(editor);
    editor.requestFocus();
    editor.show();
  }

  private void finishEnumEdit(
      ComboBox<Object> editor, Entry entry, boolean[] completed, boolean commit) {
    if (completed[0]) {
      return;
    }
    completed[0] = true;
    if (commit) {
      Object editedValue = editor.getValue();
      if (editedValue != null && !Objects.equals(editedValue, entry.value())) {
        acceptEdit(entry, editedValue);
      }
    }
    renderRows();
  }

  private void commitEdit(TextField editor, Entry entry) {
    try {
      Object editedValue = parseEditedValue(entry.value(), editor.getText());
      acceptEdit(entry, editedValue);
      renderRows();
    } catch (RuntimeException e) {
      editor.getStyleClass().add("metadata-card-editor-error");
      editor.setTooltip(new Tooltip(e.getMessage()));
    }
  }

  private boolean acceptEdit(Entry entry, Object editedValue) {
    if (options.editHandler() == null) {
      return false;
    }
    boolean accepted = options.editHandler().edited(entry.key(), entry.value(), editedValue);
    if (accepted) {
      acceptedEdits.put(entry.key(), editedValue);
    }
    return accepted;
  }

  private Object parseEditedValue(Object original, String text) {
    if (original == null || original instanceof String) {
      return text;
    }
    if (original instanceof Boolean) {
      if ("true".equalsIgnoreCase(text) || "false".equalsIgnoreCase(text)) {
        return Boolean.parseBoolean(text);
      }
      throw new IllegalArgumentException("Use true or false");
    }
    if (original instanceof Integer) {
      return Integer.parseInt(text);
    }
    if (original instanceof Long) {
      return Long.parseLong(text);
    }
    if (original instanceof Short) {
      return Short.parseShort(text);
    }
    if (original instanceof Byte) {
      return Byte.parseByte(text);
    }
    if (original instanceof Float) {
      return Float.parseFloat(text);
    }
    if (original instanceof Double) {
      return Double.parseDouble(text);
    }
    if (original instanceof BigInteger) {
      return new BigInteger(text);
    }
    if (original instanceof BigDecimal) {
      return new BigDecimal(text);
    }
    if (original instanceof Character) {
      if (text.length() == 1) {
        return text.charAt(0);
      }
      throw new IllegalArgumentException("Use one character");
    }
    return text;
  }

  private void showStringPopup(Node owner, Entry entry) {
    Popup popup = new Popup();
    popup.setAutoHide(true);

    Label title = new Label(entry.key());
    title.setStyle("-fx-font-size: 11px; -fx-font-weight: bold;");

    TextArea textArea = new TextArea(entry.displayValue());
    textArea.setEditable(false);
    textArea.setWrapText(true);
    textArea.setPrefSize(options.stringPopupWidth(), options.stringPopupHeight());

    VBox box = new VBox(6, title, textArea);
    box.setPadding(new Insets(8));
    box.setStyle(
        "-fx-background-color: white; -fx-background-radius: 7; -fx-border-color: #d0d7de; "
            + "-fx-border-radius: 7; -fx-effect: dropshadow(gaussian, rgba(27, 31, 36, 0.16), 12, 0.2, 0, 3);");

    popup.getContent().add(box);
    Bounds bounds = owner.localToScreen(owner.getBoundsInLocal());
    popup.show(owner, bounds.getMinX(), bounds.getMaxY() + 4);
  }

  private String tooltipText(Entry entry) {
    if (entry.kind() == ValueKind.STRING
        && entry.displayValue().length() > options.inlineStringLimit()) {
      return "Click to view full string\n" + clipped(entry.displayValue(), 240);
    }
    return entry.kind().label() + "\n" + entry.displayValue();
  }

  private static String clipped(String text, int maxLength) {
    if (text == null) {
      return "";
    }
    if (text.length() <= maxLength) {
      return text;
    }
    return text.substring(0, Math.max(1, maxLength - 1)) + "...";
  }

  /** How to handle metadata values that cannot be rendered as plain data or with a custom node. */
  public enum UnsupportedValuePolicy {
    /** Omit unsupported complex values from the card. */
    SKIP,
    /** Render unsupported complex values with their string representation and error styling. */
    SHOW_AS_STRING
  }

  /** Callback used to render complex metadata values as custom JavaFX content. */
  @FunctionalInterface
  public interface ComplexValueRenderer {
    /**
     * Return a node for {@code value}, or {@code null} to let the card apply the configured
     * unsupported-value policy.
     */
    Node render(String key, Object value);
  }

  /** Callback invoked when an inline edit is confirmed. */
  @FunctionalInterface
  public interface EditHandler {
    /**
     * Receives the edited value without requiring changes to the backing {@link Metadata}.
     *
     * @param key metadata key
     * @param oldValue value currently stored in the metadata
     * @param editedValue parsed value confirmed by the user
     * @return {@code true} when the edit is accepted and should replace the displayed value
     */
    boolean edited(String key, Object oldValue, Object editedValue);
  }

  /** Builder-style options for configuring display, unsupported values, and editing behavior. */
  public static class Options {
    private boolean pathTree;
    private UnsupportedValuePolicy unsupportedValuePolicy = UnsupportedValuePolicy.SKIP;
    private ComplexValueRenderer complexValueRenderer = (key, value) -> null;
    private EditHandler editHandler;
    private int inlineStringLimit = 64;
    private double stringPopupWidth = 360;
    private double stringPopupHeight = 220;
    private String title = "Parameters";
    private String emptyTitle = "Nothing to show";

    public Options() {}

    /** Copy constructor used so cards are insulated from later option-object mutations. */
    public Options(Options other) {
      if (other != null) {
        this.pathTree = other.pathTree;
        this.unsupportedValuePolicy = other.unsupportedValuePolicy;
        this.complexValueRenderer = other.complexValueRenderer;
        this.editHandler = other.editHandler;
        this.inlineStringLimit = other.inlineStringLimit;
        this.stringPopupWidth = other.stringPopupWidth;
        this.stringPopupHeight = other.stringPopupHeight;
        this.title = other.title;
        this.emptyTitle = other.emptyTitle;
      }
    }

    /** Show dot-separated keys as a collapsible hierarchy instead of a flat list. */
    public Options pathTree(boolean pathTree) {
      this.pathTree = pathTree;
      return this;
    }

    /** Configure how complex values are handled when no custom renderer is available. */
    public Options unsupportedValuePolicy(UnsupportedValuePolicy unsupportedValuePolicy) {
      this.unsupportedValuePolicy =
          unsupportedValuePolicy == null ? UnsupportedValuePolicy.SKIP : unsupportedValuePolicy;
      return this;
    }

    /** Supply custom JavaFX content for complex metadata values. */
    public Options complexValueRenderer(ComplexValueRenderer complexValueRenderer) {
      this.complexValueRenderer =
          complexValueRenderer == null ? (key, value) -> null : complexValueRenderer;
      return this;
    }

    /**
     * Enable inline editing in flat and tree mode and receive confirmed edits through the callback.
     */
    public Options editHandler(EditHandler editHandler) {
      this.editHandler = editHandler;
      return this;
    }

    public Options title(String title) {
      this.title = title;
      return this;
    }

    public Options emptyTitle(String emptyTitle) {
      this.emptyTitle = emptyTitle;
      return this;
    }

    /** Set the maximum inline string length before values are clipped and shown through a popup. */
    public Options inlineStringLimit(int inlineStringLimit) {
      this.inlineStringLimit = Math.max(16, inlineStringLimit);
      return this;
    }

    /** Set the preferred size for the popup used to browse long string values. */
    public Options stringPopupSize(double width, double height) {
      this.stringPopupWidth = Math.max(220, width);
      this.stringPopupHeight = Math.max(120, height);
      return this;
    }

    public boolean pathTree() {
      return pathTree;
    }

    public UnsupportedValuePolicy unsupportedValuePolicy() {
      return unsupportedValuePolicy;
    }

    public ComplexValueRenderer complexValueRenderer() {
      return complexValueRenderer;
    }

    public EditHandler editHandler() {
      return editHandler;
    }

    public int inlineStringLimit() {
      return inlineStringLimit;
    }

    public double stringPopupWidth() {
      return stringPopupWidth;
    }

    public double stringPopupHeight() {
      return stringPopupHeight;
    }
  }

  private enum ValueKind {
    STRING("String"),
    NUMBER("Number"),
    BOOLEAN("Boolean"),
    NULL("Null"),
    COMPLEX("Object");

    private final String label;

    ValueKind(String label) {
      this.label = label;
    }

    String label() {
      return label;
    }
  }

  private record Entry(String key, Object value, ValueKind kind) {

    static Entry of(String key, Object value) {
      return new Entry(key, value, kindOf(value));
    }

    boolean isPod() {
      return kind != ValueKind.COMPLEX;
    }

    String cssClass() {
      return switch (kind) {
        case STRING -> "metadata-value-string";
        case NUMBER -> "metadata-value-number";
        case BOOLEAN -> "metadata-value-boolean";
        case NULL -> "metadata-value-null";
        case COMPLEX -> "metadata-value-complex";
      };
    }

    String displayValue() {
      if (value == null) {
        return "null";
      }
      if (value instanceof Collection<?> collection) {
        return collection.toString();
      }
      return value.toString();
    }

    String editValue() {
      return value == null ? "" : value.toString();
    }

    private static ValueKind kindOf(Object value) {
      if (value == null) {
        return ValueKind.NULL;
      }
      if (value instanceof Boolean) {
        return ValueKind.BOOLEAN;
      }
      if (value instanceof Number) {
        return ValueKind.NUMBER;
      }
      if (value instanceof CharSequence || value instanceof Character || value instanceof Enum<?>) {
        return ValueKind.STRING;
      }
      return ValueKind.COMPLEX;
    }
  }

  private static class TreeItem {
    private final String name;
    private final String path;
    private Entry entry;
    private final Map<String, TreeItem> children = new LinkedHashMap<>();

    TreeItem(String name, String path) {
      this.name = name;
      this.path = path;
    }

    void add(Entry entry) {
      String[] parts = entry.key().split("\\.");
      TreeItem current = this;
      StringBuilder pathBuilder = new StringBuilder();
      for (String rawPart : parts) {
        String part = rawPart.isBlank() ? "(empty)" : rawPart;
        if (!pathBuilder.isEmpty()) {
          pathBuilder.append(".");
        }
        pathBuilder.append(part);
        current =
            current.children.computeIfAbsent(
                part, name -> new TreeItem(name, pathBuilder.toString()));
      }
      current.entry = entry;
    }

    String name() {
      return name;
    }

    String path() {
      return path;
    }

    Entry entry() {
      return entry;
    }

    List<TreeItem> children() {
      return new ArrayList<>(children.values());
    }
  }
}
