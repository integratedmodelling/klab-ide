package org.integratedmodelling.klab.ide.components.generic;

import atlantafx.base.theme.Styles;
import atlantafx.base.theme.Tweaks;
import java.util.HashMap;
import java.util.Map;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.StackPane;
import javafx.scene.text.Text;
import org.integratedmodelling.klab.api.collections.Pair;
import org.kordamp.ikonli.material2.Material2AL;

/**
 * A generic property editor component for handling and editing properties in a user interface. The
 * editor will be responsible for editing the given properties and validating them against known
 * target types if the mapping is provided. Properties that are described in #knownTargetTypes will
 * be proposed in a drop-down box, while new properties can always be added directly by editing the
 * key field.
 *
 * <p>Displays a two-column table with no headers. The first column shows the property key (using
 * the human-readable label for known properties), and the second column shows an editable text
 * field whose value is committed to the underlying map when the user presses Enter.
 *
 * <p>Known properties (those present in {@code knownTargetTypes}) are displayed with a drop-down
 * ComboBox in the key column so the user can switch to any known property not already in the map.
 * When {@code allowAddition} is {@code true}, the key field becomes editable and an "add" icon
 * appears in the top-right corner of the table to append new rows.
 *
 * TODO validation is missing or not working correctly
 * TODO column sizing is not correct
 * TODO consider using a TreeTableView for hierarchical properties
 */
public class PropertyEditor extends StackPane {

  // ---------------------------------------------------------------------------
  // Table row model
  // ---------------------------------------------------------------------------

  private static class PropertyEntry {
    private final StringProperty key = new SimpleStringProperty();
    private final StringProperty value = new SimpleStringProperty();

    PropertyEntry(String key, String value) {
      this.key.set(key);
      this.value.set(value != null ? value : "");
    }

    StringProperty keyProperty() {
      return key;
    }

    StringProperty valueProperty() {
      return value;
    }

    String getKey() {
      return key.get();
    }

    String getValue() {
      return value.get();
    }

    void setKey(String k) {
      key.set(k);
    }

    void setValue(String v) {
      value.set(v);
    }
  }

  // ---------------------------------------------------------------------------
  // Fields
  // ---------------------------------------------------------------------------

  private final Map<String, Object> targetProperties;
  private final Map<Pair<String, String>, Class<?>> knownTargetTypes;
  private final boolean allowAddition;
  private final ObservableList<PropertyEntry> entries = FXCollections.observableArrayList();
  private final TableView<PropertyEntry> tableView;

  // ---------------------------------------------------------------------------
  // Constructors
  // ---------------------------------------------------------------------------

  /**
   * Creates a new property editor for the provided target properties with no free-form key
   * addition.
   *
   * @param targetProperties The properties to be edited. All values should be immutable POJOs
   *     unless custom logic is in place in a derived class.
   * @param knownTargetTypes A map of known target identifier and types for validation and
   *     type-specific behaviour. The map is keyed by a {@link Pair} of {@code (label, actualKey)}:
   *     {@code label} is displayed in the key column and in the drop-down; {@code actualKey} is the
   *     real key stored in {@code targetProperties}. If a property is not present in the map it is
   *     treated as an opaque string.
   */
  public PropertyEditor(
      Map<String, Object> targetProperties, Map<Pair<String, String>, Class<?>> knownTargetTypes) {
    this(targetProperties, knownTargetTypes, false);
  }

  /**
   * Creates a new property editor for the provided target properties.
   *
   * @param targetProperties The properties to be edited. All values should be immutable POJOs
   *     unless custom logic is in place in a derived class.
   * @param knownTargetTypes A map of known target identifier and types for validation and
   *     type-specific behaviour. The map is keyed by a {@link Pair} of {@code (label, actualKey)}.
   * @param allowAddition When {@code true} the key column becomes editable and an "add" icon is
   *     displayed in the top-right corner to append new rows.
   */
  public PropertyEditor(
      Map<String, Object> targetProperties,
      Map<Pair<String, String>, Class<?>> knownTargetTypes,
      boolean allowAddition) {
    this.targetProperties = new HashMap<>(targetProperties);
    this.knownTargetTypes = knownTargetTypes;
    this.allowAddition = allowAddition;

    // Populate observable list from the initial property map
    for (Map.Entry<String, Object> e : this.targetProperties.entrySet()) {
      entries.add(new PropertyEntry(findLabel(e.getKey()), valueToString(e.getValue())));
    }

    tableView = buildTable();
    getChildren().add(tableView);

    if (allowAddition) {
      IconLabel addButton = new IconLabel(Material2AL.ADD_CIRCLE, 16, "-color-accent-fg");
      addButton.setCursor(Cursor.HAND);
      addButton.setOnMouseClicked(e -> addNewEntry());
      StackPane.setAlignment(addButton, Pos.TOP_RIGHT);
      StackPane.setMargin(addButton, new Insets(4));
      getChildren().add(addButton);
    }
  }

  // ---------------------------------------------------------------------------
  // Public API
  // ---------------------------------------------------------------------------

  /**
   * Retrieve the edited and, if necessary, validated target properties as a copy of the originally
   * submitted map.
   *
   * @return the current properties
   */
  public Map<String, Object> getTargetProperties() {
    return validate(targetProperties);
  }

  /**
   * Override this method to perform any validation on the target properties and remove/add what is
   * needed.
   *
   * @param targetProperties the current property map
   * @return the validated map
   */
  protected Map<String, Object> validate(Map<String, Object> targetProperties) {
    return targetProperties;
  }

  // ---------------------------------------------------------------------------
  // Key / label helpers
  // ---------------------------------------------------------------------------

  /**
   * Returns the human-readable label for an actual map key, or the key itself when it is not a
   * known property.
   */
  private String findLabel(String actualKey) {
    for (Pair<String, String> pair : knownTargetTypes.keySet()) {
      if (pair.getSecond().equals(actualKey)) {
        return pair.getFirst();
      }
    }
    return actualKey;
  }

  /**
   * Returns the actual map key for a display label, or the label itself when it does not match any
   * known property.
   */
  private String findActualKey(String label) {
    for (Pair<String, String> pair : knownTargetTypes.keySet()) {
      if (pair.getFirst().equals(label)) {
        return pair.getSecond();
      }
    }
    return label;
  }

  /** Returns {@code true} if the given display label corresponds to a known target type. */
  private boolean isKnownLabel(String label) {
    if (label == null || label.isEmpty()) {
      return false;
    }
    for (Pair<String, String> pair : knownTargetTypes.keySet()) {
      if (pair.getFirst().equals(label)) {
        return true;
      }
    }
    return false;
  }

  private static String valueToString(Object value) {
    return value != null ? value.toString() : "";
  }

  // ---------------------------------------------------------------------------
  // Table construction
  // ---------------------------------------------------------------------------

  private TableView<PropertyEntry> buildTable() {
    TableView<PropertyEntry> table = new TableView<>(entries);
    table.setEditable(false); // editing is handled by custom cell graphics
    table.getStyleClass().addAll(Tweaks.NO_HEADER, Tweaks.EDGE_TO_EDGE, Styles.DENSE);

    TableColumn<PropertyEntry, String> keyColumn = new TableColumn<>("Key");
    keyColumn.setCellValueFactory(data -> data.getValue().keyProperty());
    keyColumn.setCellFactory(col -> createKeyCell());
    keyColumn.setResizable(false);
    keyColumn.setSortable(false);

    TableColumn<PropertyEntry, String> valueColumn = new TableColumn<>("Value");
    valueColumn.setCellValueFactory(data -> data.getValue().valueProperty());
    valueColumn.setCellFactory(col -> createValueCell());
    valueColumn.setSortable(false);

    // Fixed key column width; value column fills the remainder via CONSTRAINED policy
    double keyColWidth = computeKeyColumnWidth();
    keyColumn.setPrefWidth(keyColWidth);
    keyColumn.setMinWidth(keyColWidth);
    keyColumn.setMaxWidth(keyColWidth);

    table.getColumns().addAll(keyColumn, valueColumn);
    table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

    return table;
  }

  /**
   * Measures all current and known key labels and returns the width needed to display the longest
   * one (plus horizontal cell padding).
   */
  private double computeKeyColumnWidth() {
    Text measurer = new Text();
    double maxWidth = 60; // minimum
    for (PropertyEntry e : entries) {
      measurer.setText(e.getKey());
      double w = measurer.getLayoutBounds().getWidth() + 24;
      if (w > maxWidth) maxWidth = w;
    }
    for (Pair<String, String> pair : knownTargetTypes.keySet()) {
      measurer.setText(pair.getFirst());
      double w = measurer.getLayoutBounds().getWidth() + 24;
      if (w > maxWidth) maxWidth = w;
    }
    return maxWidth;
  }

  // ---------------------------------------------------------------------------
  // Cell factories
  // ---------------------------------------------------------------------------

  private TableCell<PropertyEntry, String> createKeyCell() {
    return new TableCell<>() {
      private ComboBox<String> comboBox;
      private TextField textField;
      // Guard that prevents the textProperty listener from re-entering when we call
      // entry.setKey(), which in turn triggers updateItem → setText on the same field.
      private boolean updatingKey = false;

      @Override
      protected void updateItem(String item, boolean empty) {
        super.updateItem(item, empty);
        if (empty || item == null) {
          setGraphic(null);
          setText(null);
          return;
        }

        if (isKnownLabel(item)) {
          // --- Known property: drop-down ComboBox ---
          if (comboBox == null) {
            comboBox = new ComboBox<>();
            comboBox.setEditable(allowAddition);
            comboBox.setMaxWidth(Double.MAX_VALUE);
            comboBox.setOnAction(e -> commitKeyComboSelection());
          }
          // Populate: current label first, then known labels not already in the map
          comboBox.getItems().clear();
          comboBox.getItems().add(item);
          for (Pair<String, String> pair : knownTargetTypes.keySet()) {
            String label = pair.getFirst();
            String actualKey = pair.getSecond();
            if (!label.equals(item) && !targetProperties.containsKey(actualKey)) {
              comboBox.getItems().add(label);
            }
          }
          comboBox.setValue(item);
          setGraphic(comboBox);
          setText(null);

        } else if (allowAddition) {
          // --- Unknown property (addition allowed): live-updating TextField ---
          if (textField == null) {
            textField = new TextField();
            // Commit the key on every keystroke so the map stays in sync regardless of
            // whether the user presses Enter, Tab, or clicks directly into the value field.
            textField
                .textProperty()
                .addListener(
                    (obs, oldText, newText) -> {
                      if (updatingKey) return;
                      PropertyEntry entry =
                          getTableRow() != null ? getTableRow().getItem() : null;
                      if (entry == null || newText.equals(entry.getKey())) return;
                      String oldActualKey = findActualKey(entry.getKey());
                      String newActualKey = findActualKey(newText);
                      // Move the stored value to the new key; fall back to the entry's
                      // current displayed value so the value field is never blanked.
                      Object stored = targetProperties.remove(oldActualKey);
                      String valueToKeep =
                          stored != null ? stored.toString() : entry.getValue();
                      targetProperties.put(newActualKey, valueToKeep);
                      updatingKey = true;
                      entry.setKey(newText);
                      updatingKey = false;
                    });
          }
          // Guard matches the value-cell pattern: skip setText when already correct to
          // avoid a spurious caret-reset while the user is typing.
          if (!item.equals(textField.getText())) {
            updatingKey = true;
            textField.setText(item);
            updatingKey = false;
          }
          setGraphic(textField);
          setText(null);

        } else {
          // --- Unknown property (addition not allowed): plain label ---
          setGraphic(null);
          setText(item);
        }
      }

      /** Handles a ComboBox selection or an editable ComboBox commit (Enter / focus-loss). */
      private void commitKeyComboSelection() {
        if (comboBox == null) return;
        PropertyEntry entry = getTableRow() != null ? getTableRow().getItem() : null;
        if (entry == null) return;
        String newLabel = comboBox.getValue();
        if (newLabel == null || newLabel.equals(entry.getKey())) return;
        String oldActualKey = findActualKey(entry.getKey());
        String newActualKey = findActualKey(newLabel);
        targetProperties.remove(oldActualKey);
        // If the new key already has a value in the map (e.g. editable combo typed an existing
        // key) use it; otherwise the slot is new and should start empty.
        String newValue = valueToString(targetProperties.get(newActualKey));
        targetProperties.put(newActualKey, newValue);
        entry.setKey(newLabel);
        entry.setValue(newValue);
      }
    };
  }

  private TableCell<PropertyEntry, String> createValueCell() {
    return new TableCell<>() {
      private final TextField textField = new TextField();

      {
        // Commit to the underlying map on every keystroke
        textField
            .textProperty()
            .addListener(
                (obs, oldText, newText) -> {
                  PropertyEntry entry = getTableRow() != null ? getTableRow().getItem() : null;
                  if (entry != null) {
                    String actualKey = findActualKey(entry.getKey());
                    targetProperties.put(actualKey, newText);
                    entry.setValue(newText);
                  }
                });
      }

      @Override
      protected void updateItem(String item, boolean empty) {
        super.updateItem(item, empty);
        if (empty) {
          setGraphic(null);
          setText(null);
        } else {
          // Only reset the text when the content actually differs; skipping the redundant
          // setText() call prevents the caret from jumping to position 0 while the user
          // is typing (the textProperty listener already keeps entry and map in sync).
          String target = item != null ? item : "";
          if (!target.equals(textField.getText())) {
            textField.setText(target);
          }
          setGraphic(textField);
          setText(null);
        }
      }
    };
  }

  // ---------------------------------------------------------------------------
  // Row addition
  // ---------------------------------------------------------------------------

  private void addNewEntry() {
    PropertyEntry newEntry = new PropertyEntry("", "");
    entries.add(newEntry);
    tableView.getSelectionModel().select(newEntry);
    tableView.scrollTo(newEntry);
  }
}
