package org.integratedmodelling.klab.ide.components;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javafx.geometry.Insets;
import javafx.scene.Parent;
import javafx.scene.layout.BorderPane;
import org.integratedmodelling.common.logging.Logging;
import org.integratedmodelling.common.utils.Utils;
import org.integratedmodelling.klab.api.exceptions.KlabInternalErrorException;
import org.integratedmodelling.klab.ide.KlabIDEController;
import org.integratedmodelling.klab.ide.Theme;
import org.integratedmodelling.klab.ide.cli.DashboardLineReader;
import org.integratedmodelling.klab.ide.cli.DashboardTerminal;
import org.integratedmodelling.klab.ide.components.generic.AutoCompleteTextField;
import org.integratedmodelling.klab.ide.components.generic.Notebook;
import org.integratedmodelling.klab.ide.pages.Page;

public class NotebookViewer extends BorderPane implements Page {

  private final InputBox inputBox;
  private final Notebook notebook;
  private DashboardTerminal terminal;
  private DashboardLineReader lineReader;
  private final Map<Components.Type, Components.Component> componentMap = new LinkedHashMap<>();

  public NotebookViewer() {

    this.notebook = new Notebook();
    this.setCenter(this.notebook);
    this.inputBox =
        new InputBox(text -> List.of("Dingo", "Discromia", "Diesel", "Di Bue", "Di Vacca"));
    this.setBottom(inputBox);
    this.setCenter(this.notebook);

    this.lineReader =
        new DashboardLineReader(
            this.inputBox,
            new DashboardLineReader.PrintCallback() {
              @Override
              public void onPrint(String text) {
                Logging.INSTANCE.info(text);
              }

              @Override
              public void onPrintAbove(String text) {
                Logging.INSTANCE.info(text);
              }
            });

    addComponent(new Components.About());
    //    addComponent(new Components.TimelineComponent());
  }

  public void addComponent(Components.BaseComponent component) {
    notebook.addCard(
        Components.Type.About.name(),
        Theme.DIGITAL_TWINS_ICON,
        component.getTitle(),
        "Subtitle TODO",
        component);
  }

  public void toggle(Components.Type type, Object... arguments) {

    if (notebook.hasCard(type.name())) {
      notebook.focusCard(type.name());
    } else {
      var card =
          switch (type) {
            case Distribution -> new Components.DistributionComponent();
            case UserInfo -> new Components.User(KlabIDEController.instance().user());
            case ServiceInfo -> new Components.Services();
            case About -> new Components.About();
            case Settings -> new Components.Settings();
            default -> throw new KlabInternalErrorException("unexpected component " + type);
          };
      notebook.addCard(
          type.name(), Theme.LOCAL_SERVICE_ICON, card.getTitle(), "Subtitle TODO", card);
      notebook.focusCard(type.name());
    }
  }

  public static class InputBox extends AutoCompleteTextField {
    InputBox(EntryProvider entryProvider) {
      super(entryProvider);
      setMargin(this, new Insets(24, 20, 20, 10));
      setPromptText("Enter a command; 'help' for assistance");
    }
  }

  @Override
  public String getName() {
    return "Notebook";
  }

  @Override
  public Parent getView() {
    return this;
  }

  @Override
  public void reset() {}
}
