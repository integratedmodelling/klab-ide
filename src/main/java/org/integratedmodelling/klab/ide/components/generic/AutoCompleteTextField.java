package org.integratedmodelling.klab.ide.components.generic;

import java.util.LinkedList;
import java.util.List;
import java.util.function.Consumer;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.geometry.Side;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.CustomMenuItem;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;

/**
 * This class is a TextField which implements an "autocomplete" functionality, based on a supplied
 * list of entries.
 *
 * <p>If the entered text matches a part of any of the supplied entries these are going to be
 * displayed in a popup. Further the matching part of the entry is going to be displayed in a
 * special style, defined by {@link #textOccurenceStyle textOccurenceStyle}. The maximum number of
 * displayed entries in the popup is defined by {@link #maxEntries maxEntries}.<br>
 * By default the pattern matching is not case-sensitive. This behaviour is defined by the {@link
 * #caseSensitive caseSensitive} .
 *
 * <p>The AutoCompleteTextField also has a List of {@link #filteredEntries filteredEntries} that is
 * equal to the search results if search results are not empty, or {@link #filteredEntries
 * filteredEntries} is equal to entries otherwise. If {@link #popupHidden popupHidden} is set to
 * true no popup is going to be shown. This list can be used to bind all entries to another node (a
 * ListView for example) in the following way:
 *
 * <pre>
 * <code>
 * AutoCompleteTextField auto = new AutoCompleteTextField(entries);
 * auto.setPopupHidden(true);
 * SimpleListProperty filteredEntries = new SimpleListProperty(auto.getFilteredEntries());
 * listView.itemsProperty().bind(filteredEntries);
 * </code>
 * </pre>
 *
 * @author Caleb Brinkman
 * @author Fabian Ochmann
 */
public class AutoCompleteTextField extends TextField {

  /** Interface for providing autocomplete entries */
  @FunctionalInterface
  public interface EntryProvider {
    List<String> getSuggestions(String text);
  }

  private Consumer<AutoCompleteTextField> ctrlSpaceHandler = null;

  /** The callback for getting autocomplete entries. */
  private final EntryProvider entryProvider;

  /**
   * The set of filtered entries:<br>
   * Equal to the search results if search results are not empty, equal to entries otherwise.
   */
  private ObservableList<String> filteredEntries = FXCollections.observableArrayList();

  /** The popup used to select an entry. */
  protected ContextMenu entriesPopup;

  private int selectedSuggestionIndex = -1;

  private boolean isCycling = false;

  /**
   * Returns the string to be inserted into the text field when a suggestion is selected. Default
   * implementation returns the suggestion as-is.
   *
   * @param suggestion the suggestion string from the provider.
   * @return the string to insert.
   */
  protected String getReplacement(String suggestion) {
    return suggestion;
  }

  /**
   * Indicates whether the search is case sensitive or not. <br>
   * Default: false
   */
  private boolean caseSensitive = false;

  /**
   * Indicates whether the Popup should be hidden or displayed. Use this if you want to filter an
   * existing list/set (for example values of a {@link javafx.scene.control.ListView ListView}). Do
   * this by binding {@link #getFilteredEntries() getFilteredEntries()} to the list/set.
   */
  private boolean popupHidden = false;

  /**
   * The CSS style that should be applied on the parts in the popup that match the entered text.
   * <br>
   * Default: "-fx-font-weight: bold; -fx-fill: red;"
   *
   * <p>Note: This style is going to be applied on an {@link javafx.scene.text.Text Text} instance.
   * See the <i>JavaFX CSS Reference Guide</i> for available CSS Propeties.
   */
  private String textOccurenceStyle = "-fx-font-weight: bold; " + "-fx-fill: red;";

  /**
   * The maximum Number of entries displayed in the popup.<br>
   * Default: 10
   */
  private int maxEntries = 10;

  /** Construct a new AutoCompleteTextField. */
  public AutoCompleteTextField(EntryProvider provider) {
    super();
    this.entryProvider = provider;
    this.filteredEntries = FXCollections.observableArrayList();

    entriesPopup = new ContextMenu();
    entriesPopup
        .showingProperty()
        .addListener(
            (obs, oldVal, newVal) -> {
              if (!newVal) {
                selectedSuggestionIndex = -1;
              }
            });

    this.addEventFilter(
        KeyEvent.KEY_PRESSED,
        event -> {
          if (event.getCode() == KeyCode.SPACE
              && event.isControlDown()
              && ctrlSpaceHandler != null) {
            ctrlSpaceHandler.accept(this);
            event.consume();
            return;
          }
          if (event.getCode() == KeyCode.TAB) {
            if (entriesPopup.isShowing() && !filteredEntries.isEmpty()) {
              if (event.isShiftDown()) {
                selectedSuggestionIndex =
                    (selectedSuggestionIndex <= 0)
                        ? filteredEntries.size() - 1
                        : selectedSuggestionIndex - 1;
              } else {
                selectedSuggestionIndex = (selectedSuggestionIndex + 1) % filteredEntries.size();
              }
              String suggestion = filteredEntries.get(selectedSuggestionIndex);
              String replacement = getReplacement(suggestion);

              isCycling = true;
              setText(replacement);
              positionCaret(replacement.length());
              isCycling = false;
              event.consume();
            }
          } else if (event.getCode() != KeyCode.SHIFT
              && event.getCode() != KeyCode.CONTROL
              && event.getCode() != KeyCode.ALT
              && event.getCode() != KeyCode.META) {
            if (selectedSuggestionIndex != -1) {
              entriesPopup.hide();
            }
          }
        });

    textProperty()
        .addListener(
            new ChangeListener<String>() {
              @Override
              public void changed(
                  ObservableValue<? extends String> observableValue, String s, String s2) {
                if (isCycling) {
                  return;
                }
                if (getText().isEmpty()) {
                  filteredEntries.clear();
                  entriesPopup.hide();
                } else {
                  String text = getText();
                  List<String> searchResult = entryProvider.getSuggestions(text);

                  if (!searchResult.isEmpty()) {
                    selectedSuggestionIndex = -1;
                    filteredEntries.clear();
                    filteredEntries.addAll(searchResult);

                    // Only show popup if not in filter mode
                    if (!isPopupHidden()) {
                      populatePopup(searchResult, text);
                      if (!entriesPopup.isShowing()) {
                        double x = localToScreen(getBoundsInLocal()).getMinX();
                        double y = localToScreen(getBoundsInLocal()).getMinY();

                        // Get screen bounds
                        double screenHeight = getScene().getWindow().getHeight();
                        double popupHeight = entriesPopup.getHeight();
                        double fieldBottom = y + getHeight();

                        // Show above if displaying below would exceed screen bounds
                        if (fieldBottom + popupHeight > screenHeight) {
                          entriesPopup.show(AutoCompleteTextField.this, Side.TOP, 0, -fieldBottom);
                        } else {
                          entriesPopup.show(AutoCompleteTextField.this, Side.BOTTOM, 0, y);
                        }
                      }
                    }
                  } else {
                    entriesPopup.hide();
                  }
                }
              }
            });

    focusedProperty()
        .addListener(
            new ChangeListener<Boolean>() {
              @Override
              public void changed(
                  ObservableValue<? extends Boolean> observableValue,
                  Boolean aBoolean,
                  Boolean aBoolean2) {
                entriesPopup.hide();
              }
            });
  }

  public void setCtrlSpaceHandler(Consumer<AutoCompleteTextField> ctrlSpaceHandler) {
    this.ctrlSpaceHandler = ctrlSpaceHandler;
  }

  /**
   * Populate the entry set with the given search results. Display is limited to 10 entries, for
   * performance.
   *
   * @param searchResult The set of matching strings.
   */
  protected void populatePopup(List<String> searchResult, String text) {
    List<CustomMenuItem> menuItems = new LinkedList<>();
    int count = Math.min(searchResult.size(), getMaxEntries());
    for (int i = 0; i < count; i++) {
      final String result = searchResult.get(i);
      int occurence;

      if (isCaseSensitive()) {
        occurence = result.indexOf(text);
      } else {
        occurence = result.toLowerCase().indexOf(text.toLowerCase());
      }

      if (occurence < 0) {
        continue;
      }

      // Part before occurence (might be empty)
      Text pre = new Text(result.substring(0, occurence));
      // Part of (first) occurence
      Text in = new Text(result.substring(occurence, occurence + text.length()));
      in.setStyle(getTextOccurenceStyle());
      // Part after occurence
      Text post = new Text(result.substring(occurence + text.length(), result.length()));

      TextFlow entryFlow = new TextFlow(pre, in, post);

      CustomMenuItem item = new CustomMenuItem(entryFlow, true);
      item.setOnAction(
          new EventHandler<ActionEvent>() {
            @Override
            public void handle(ActionEvent actionEvent) {
              setText(getReplacement(result));
              entriesPopup.hide();
            }
          });
      menuItems.add(item);
    }
    entriesPopup.getItems().clear();
    entriesPopup.getItems().addAll(menuItems);
  }

  public boolean isCaseSensitive() {
    return caseSensitive;
  }

  public String getTextOccurenceStyle() {
    return textOccurenceStyle;
  }

  public void setCaseSensitive(boolean caseSensitive) {
    this.caseSensitive = caseSensitive;
  }

  public void setTextOccurenceStyle(String textOccurenceStyle) {
    this.textOccurenceStyle = textOccurenceStyle;
  }

  public boolean isPopupHidden() {
    return popupHidden;
  }

  public void setPopupHidden(boolean popupHidden) {
    this.popupHidden = popupHidden;
  }

  public ObservableList<String> getFilteredEntries() {
    return filteredEntries;
  }

  public int getMaxEntries() {
    return maxEntries;
  }

  public void setMaxEntries(int maxEntries) {
    this.maxEntries = maxEntries;
  }
}
