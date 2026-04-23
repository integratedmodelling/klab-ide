package org.integratedmodelling.klab.ide.components.cards;

import javafx.scene.Node;
import javafx.scene.layout.VBox;
import org.integratedmodelling.klab.ide.Theme;
import org.kordamp.ikonli.Ikon;

public abstract class BaseAssetViewComponent extends VBox implements AssetViewComponent {

  String title;
  Type type;

  public BaseAssetViewComponent(Type type, String title, boolean initialize) {
    super(10);
    this.title = title;
    this.type = type;
    if (initialize) {
      createContent();
    }
  }

  @Override
  public Type getType() {
    return type;
  }

  @Override
  public String getTitle() {
    return title;
  }

  @Override
  public String getDescription() {
    return "No description available";
  }

  @Override
  public Ikon getIcon() {
    return Theme.DIGITAL_TWINS_ICON;
  }

  protected abstract Node createContent();
}
