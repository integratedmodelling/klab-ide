package org.integratedmodelling.klab.ide.components;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.layout.BorderPane;
import org.integratedmodelling.common.logging.Logging;
import org.integratedmodelling.klab.api.exceptions.KlabInternalErrorException;
import org.integratedmodelling.klab.ide.KlabIDEController;
import org.integratedmodelling.klab.ide.cli.DashboardLineReader;
import org.integratedmodelling.klab.ide.cli.DashboardTerminal;
import org.integratedmodelling.klab.ide.contrib.AutoCompleteTextField;
import org.integratedmodelling.klab.ide.pages.OutlinePage;
import org.integratedmodelling.klab.ide.pages.Page;

public class NotebookView extends BorderPane implements Page {

  private final InputBox inputBox;
  private final Notebook notebook;
  private DashboardTerminal terminal;
  private DashboardLineReader lineReader;
  private final Map<Components.Type, Components.Component> componentMap = new LinkedHashMap<>();

  public NotebookView() {

    this.notebook = new Notebook();
    this.setCenter(this.notebook);
    this.inputBox =
        new InputBox(
            text -> List.of("Dio", "Dingo", "Discromia", "Dicomarca", "Di Bue e di Vacca"));
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

  public static class InputBox extends AutoCompleteTextField {
    InputBox(AutoCompleteTextField.EntryProvider entryProvider) {
      super(entryProvider);
      setMargin(this, new Insets(24, 20, 20, 10));
      setPromptText("Enter a command; 'help' for assistance");
    }
  }

  public static class Notebook extends OutlinePage {

    @Override
    public String getName() {
      return "Notebook";
    }
  }

  @Override
  public String getName() {
    return "Dashboard";
  }

  @Override
  public Parent getView() {
    return this;
  }

  public void focus(Components.Type componentType, Object... arguments) {
    if (this.componentMap.containsKey(componentType)) {
      this.componentMap.remove(componentType);
    } else {
      componentMap.put(
          componentType,
          switch (componentType) {
            case Distribution ->
                new Components.DistributionComponent(KlabIDEController.modeler().getDistribution());
            case UserInfo -> new Components.User(KlabIDEController.modeler().user());
            case ServiceInfo -> new Components.Services();
            case About -> new Components.About();
            case Settings -> new Components.Settings();
            //            case AutoScroll -> new Components.AutoScrollDemo();
            default ->
                throw new KlabInternalErrorException("unexpected component " + componentType);
          });
      addComponent(componentMap.get(componentType));
    }
  }

  public void toggle(Components.Type componentType, Object... arguments) {
    if (this.componentMap.containsKey(componentType)) {
      componentMap.remove(componentType);
      redraw();
    } else {
      componentMap.put(
          componentType,
          switch (componentType) {
            case Distribution ->
                new Components.DistributionComponent(KlabIDEController.modeler().getDistribution());
            case UserInfo -> new Components.User(KlabIDEController.modeler().user());
            case ServiceInfo -> new Components.Services();
            case About -> new Components.About();
            case Settings -> new Components.Settings();
            //            case AutoScroll -> new Components.AutoScrollDemo();
            default ->
                throw new KlabInternalErrorException("unexpected component " + componentType);
          });
      addComponent(componentMap.get(componentType));
    }
  }

  public void addComponent(Components.Component component) {
    if (component instanceof Node node) {
      Platform.runLater(
          () -> {
            notebook.addSection(component.getTitle(), node);
          });
    }
  }

  public void redraw() {
    notebook.removeAll();
    for (var type : componentMap.keySet()) {
      addComponent(componentMap.get(type));
    }
  }

  @Override
  public void reset() {
    notebook.reset();
  }
}
