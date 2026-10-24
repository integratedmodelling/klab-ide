package org.integratedmodelling.klab.ide.components;

import java.util.List;
import java.util.function.Supplier;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.control.*;
import javafx.stage.FileChooser;
import javafx.stage.Window;
import org.integratedmodelling.klab.ide.components.generic.IconLabel;
import org.kordamp.ikonli.codicons.Codicons;

/** Shared per-editor launch destination; the running view can also switch the attachment. */
final class ApplicationBrowserOptions {
  static final ApplicationBrowser INTERNAL = new ApplicationBrowser("Internal WebView", List.of());
  private static final ApplicationBrowser CUSTOM = new ApplicationBrowser("Choose browser…", List.of());
  private final ObservableList<ApplicationBrowser> browsers = FXCollections.observableArrayList();
  private final ObjectProperty<ApplicationBrowser> selected = new SimpleObjectProperty<>(INTERNAL);

  ApplicationBrowserOptions() {
    browsers.add(INTERNAL); browsers.addAll(ApplicationBrowser.installed()); browsers.add(CUSTOM);
  }

  ObjectProperty<ApplicationBrowser> selectedProperty() { return selected; }
  ApplicationBrowser selected() { return selected.get(); }

  MenuButton createMenu(Supplier<Window> owner) {
    var menu = new MenuButton("", new IconLabel(Codicons.BROWSER, 18, "-color-fg-default"));
    menu.setAccessibleText("Application browser");
    Runnable rebuild = () -> {
      menu.getItems().clear(); var group = new ToggleGroup();
      for (var browser : browsers) {
        if (browser == CUSTOM) {
          var custom = new MenuItem(browser.name()); custom.setOnAction(event -> choose(owner));
          menu.getItems().add(new SeparatorMenuItem()); menu.getItems().add(custom);
        } else {
          var item = new RadioMenuItem(browser.name()); item.setToggleGroup(group);
          item.setSelected(browser.equals(selected()));
          item.setOnAction(event -> selected.set(browser)); menu.getItems().add(item);
        }
      }
      menu.setTooltip(new Tooltip("Application browser: " + selected().name()));
    };
    menu.setOnShowing(event -> rebuild.run());
    javafx.beans.value.ChangeListener<ApplicationBrowser> listener = (obs, before, after) -> rebuild.run();
    menu.getProperties().put("browser-choice-listener", listener);
    selected.addListener(new javafx.beans.value.WeakChangeListener<>(listener));
    rebuild.run(); return menu;
  }

  ChoiceBox<ApplicationBrowser> createChoice(Supplier<Window> owner) {
    var choice = new ChoiceBox<>(browsers); choice.setValue(selected());
    choice.setAccessibleText("Application browser");
    choice.setTooltip(new Tooltip("Choose a browser; an active run reconnects with its state retained"));
    choice.valueProperty().addListener((obs, before, after) -> {
      if (after == CUSTOM) { choose(owner); choice.setValue(selected()); }
      else if (after != null) selected.set(after);
    });
    javafx.beans.value.ChangeListener<ApplicationBrowser> listener = (obs, before, after) -> choice.setValue(after);
    selected.addListener(listener);
    choice.getProperties().put("dispose-browser-options", (Runnable) () -> selected.removeListener(listener));
    return choice;
  }

  private void choose(Supplier<Window> owner) {
    var chooser = new FileChooser(); chooser.setTitle("Choose browser executable");
    var file = chooser.showOpenDialog(owner.get());
    if (file != null) {
      var browser = new ApplicationBrowser(file.getName(), List.of(file.getAbsolutePath()));
      browsers.add(browsers.size() - 1, browser); selected.set(browser);
    }
  }
}
