package org.integratedmodelling.klab.ide.components.generic;

import atlantafx.base.theme.Styles;
import java.util.LinkedHashSet;
import java.util.Set;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import org.integratedmodelling.klab.api.collections.DomainObject;

/** Generic, replaceable JavaFX presentation for a {@link DomainObject} property tree. */
public class DomainObjectView extends ScrollPane {

  private static final Set<String> HEADER_PROPERTIES =
      Set.of(
          DomainObject.TYPE,
          DomainObject.NAME,
          DomainObject.LABEL,
          DomainObject.DESCRIPTION,
          DomainObject.URN);

  private final VBox content = new VBox(8);
  private DomainObject domainObject;

  public DomainObjectView() {
    setFitToWidth(true);
    setPannable(true);
    content.setPadding(new Insets(12));
    setContent(content);
  }

  public DomainObjectView(DomainObject domainObject) {
    this();
    setDomainObject(domainObject);
  }

  public final DomainObject getDomainObject() {
    return domainObject;
  }

  public final void setDomainObject(DomainObject domainObject) {
    this.domainObject = domainObject;
    refresh();
  }

  /** Rebuild the presentation after the supplied domain model has changed. */
  public void refresh() {
    content.getChildren().clear();
    if (domainObject == null) {
      var empty = new Label("No data");
      empty.getStyleClass().add(Styles.TEXT_MUTED);
      content.getChildren().add(empty);
    } else {
      content.getChildren().add(createObjectNode(domainObject, 0));
    }
  }

  /** Override to specialize selected domain types while retaining the generic property renderer. */
  protected Node createObjectNode(DomainObject object, int depth) {
    var card = new VBox(6);
    card.setPadding(new Insets(10));
    card.setMaxWidth(Double.MAX_VALUE);
    card.setStyle(
        "-fx-background-color: -color-bg-subtle; -fx-background-radius: 4;"
            + " -fx-border-color: -color-border-default; -fx-border-radius: 4;");

    var title = new Label(objectTitle(object));
    title.getStyleClass().add(Styles.TEXT_BOLD);
    title.setWrapText(true);
    card.getChildren().add(title);

    if (object.description() != null && !object.description().isBlank()) {
      var description = new Label(object.description());
      description.setWrapText(true);
      card.getChildren().add(description);
    }

    var properties = createProperties(object, HEADER_PROPERTIES);
    if (!properties.getChildren().isEmpty()) {
      card.getChildren().add(properties);
    }

    if (object.getChildren() != null && !object.getChildren().isEmpty()) {
      var children = new VBox(6);
      children.setPadding(new Insets(4, 0, 0, Math.min(24, (depth + 1) * 4)));
      for (var child : object.getChildren()) {
        children.getChildren().add(createObjectNode(child, depth + 1));
      }
      card.getChildren().add(children);
      VBox.setVgrow(children, Priority.ALWAYS);
    }
    return card;
  }

  protected GridPane createProperties(DomainObject object, Set<String> excludedProperties) {
    var properties = new GridPane();
    properties.setHgap(10);
    properties.setVgap(3);
    int row = 0;
    for (var key : new LinkedHashSet<>(object.keySet())) {
      if (excludedProperties.contains(key) || object.get(key) == null) {
        continue;
      }
      var property = new Label(humanize(key));
      property.getStyleClass().add(Styles.TEXT_MUTED);
      var value = createValueNode(object.get(key));
      properties.add(property, 0, row);
      properties.add(value, 1, row++);
      if (value instanceof Region region) {
        region.setMaxWidth(Double.MAX_VALUE);
      }
    }
    return properties;
  }

  protected Node createValueNode(Object value) {
    var label = new Label(String.valueOf(value));
    label.setWrapText(true);
    return label;
  }

  protected String objectTitle(DomainObject object) {
    if (object.name() != null && !object.name().isBlank()) {
      return object.name();
    }
    if (object.label() != null && !object.label().isBlank()) {
      return object.label();
    }
    if (object.urn() != null && !object.urn().isBlank()) {
      return object.urn();
    }
    return object.type() == null || object.type().isBlank() ? "Object" : humanize(object.type());
  }

  protected static String humanize(String key) {
    if (key == null || key.isBlank()) {
      return "";
    }
    var text = key.replace('-', ' ').replace('_', ' ');
    var ret = new StringBuilder();
    for (int i = 0; i < text.length(); i++) {
      char c = text.charAt(i);
      if (i > 0 && Character.isUpperCase(c) && Character.isLowerCase(text.charAt(i - 1))) {
        ret.append(' ');
      }
      ret.append(c);
    }
    ret.setCharAt(0, Character.toUpperCase(ret.charAt(0)));
    return ret.toString();
  }
}
