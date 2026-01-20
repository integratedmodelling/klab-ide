package org.integratedmodelling.klab.ide.components;

import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.layout.VBox;
import org.integratedmodelling.klab.ide.pages.BrowsablePage;

public class OntologyView extends BrowsablePage {

  @Override
  public String getName() {
    return "Digital Twins";
  }

  @Override
  public Parent getView() {
    return this;
  }

  @Override
  public void reset() {}

  @Override
  protected void assetEditorSelected(Node assetEditor) {}

  @Override
  protected void assetEditorClosed(Node assetEditor) {}

  @Override
  protected void defineBrowser(VBox vBox) {}
}
