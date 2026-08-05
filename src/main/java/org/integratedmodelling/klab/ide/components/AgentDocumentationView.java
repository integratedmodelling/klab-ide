package org.integratedmodelling.klab.ide.components;

import atlantafx.base.theme.Styles;
import atlantafx.base.theme.Tweaks;
import atlantafx.base.util.BBCodeParser;
import java.util.Objects;
import java.util.function.Function;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TreeCell;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import org.integratedmodelling.klab.api.collections.DomainObject;
import org.integratedmodelling.klab.ide.components.generic.DomainObjectView;
import org.integratedmodelling.klab.ide.components.generic.TreeSearchField;

/** Compact, searchable documentation browser for agents and their verbs. */
public final class AgentDocumentationView extends DomainObjectView {

  public static final String AGENT_DOCUMENTATION = AgentDocumentationModel.AGENT_DOCUMENTATION;
  public static final String AGENT = AgentDocumentationModel.AGENT;
  public static final String VERB = AgentDocumentationModel.VERB;
  public static final String DOCUMENTATION = AgentDocumentationModel.DOCUMENTATION;
  public static final String MARKDOWN = AgentDocumentationModel.MARKDOWN;
  public static final String SYNTAX = AgentDocumentationModel.SYNTAX;

  private static final String COMPACT_STYLE = "-fx-font-size: 10px;";

  private final Function<String, String> markdownToBBCode;
  private final TreeView<DomainObject> documentationTree = new TreeView<>();

  /** Create the view with mock data and the intentionally replaceable translation stub. */
  public AgentDocumentationView() {
    this(AgentDocumentationView::markdownToBBCodeStub);
  }

  /**
   * @param markdownToBBCode callback used only when a verb is rendered; callers may replace the
   *     stub with the service-provided Markdown translator
   */
  public AgentDocumentationView(Function<String, String> markdownToBBCode) {
    this.markdownToBBCode = Objects.requireNonNull(markdownToBBCode, "markdownToBBCode");

    documentationTree.setShowRoot(false);
    documentationTree.getStyleClass().addAll(Tweaks.EDGE_TO_EDGE, Styles.DENSE);
    documentationTree.setCellFactory(ignored -> new DocumentationTreeCell());
    documentationTree.setStyle(COMPACT_STYLE);

    var search = new TreeSearchField<>(documentationTree, AgentDocumentationModel::matches);
    search.setPadding(new Insets(4, 5, 4, 5));

    var layout = new VBox(search, documentationTree);
    VBox.setVgrow(documentationTree, Priority.ALWAYS);
    layout.setStyle(COMPACT_STYLE);
    setFitToWidth(true);
    setFitToHeight(true);
    setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
    setVbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
    setContent(layout);
    setDomainObject(AgentDocumentationModel.mockDocumentation());
  }

  @Override
  public void refresh() {
    var rootObject = getDomainObject();
    var root = new TreeItem<DomainObject>(rootObject);
    root.setExpanded(true);
    if (rootObject != null) {
      for (var agent : rootObject.getChildren()) {
        var agentItem = new TreeItem<>(agent);
        for (var verb : agent.getChildren()) {
          var verbItem = new TreeItem<>(verb);
          // A presentation-only child gives each verb a disclosure control without changing the
          // service bean shape: root -> agents -> verbs.
          verbItem.getChildren().add(
              new TreeItem<>(AgentDocumentationModel.documentationNode(verb)));
          agentItem.getChildren().add(verbItem);
        }
        root.getChildren().add(agentItem);
      }
    }
    documentationTree.setRoot(root);
  }

  private static String text(Object value) {
    return value == null ? "" : String.valueOf(value);
  }

  private Node renderDocumentation(DomainObject documentation) {
    String markdown = documentation.get(MARKDOWN, String.class);
    String bbCode = markdownToBBCode.apply(markdown == null ? "" : markdown);
    if (bbCode == null || bbCode.isBlank()) {
      var empty = new Label("No documentation available");
      empty.getStyleClass().add(Styles.TEXT_MUTED);
      return empty;
    }
    try {
      var layout = BBCodeParser.createLayout(bbCode);
      layout.setMaxWidth(Double.MAX_VALUE);
      layout.setStyle(COMPACT_STYLE);
      return layout;
    } catch (RuntimeException malformedBBCode) {
      var fallback = new Label(markdown);
      fallback.setWrapText(true);
      return fallback;
    }
  }

  /**
   * Translation seam for the future Markdown converter. It deliberately displays Markdown as
   * plain text while allowing safe BBCode emphasis around the placeholder notice.
   */
  static String markdownToBBCodeStub(String markdown) {
    return "[i]Markdown translation callback not installed[/i]\n\n"
        + (markdown == null ? "" : markdown.replace("[", "(").replace("]", ")"));
  }

  private final class DocumentationTreeCell extends TreeCell<DomainObject> {

    @Override
    protected void updateItem(DomainObject item, boolean empty) {
      super.updateItem(item, empty);
      setText(null);
      setGraphic(null);
      setStyle(COMPACT_STYLE);
      if (empty || item == null) {
        return;
      }
      switch (text(item.type())) {
        case AGENT -> {
          var label = new Label(objectTitle(item));
          label.getStyleClass().add(Styles.TEXT_BOLD);
          label.setStyle(COMPACT_STYLE);
          setGraphic(label);
        }
        case VERB -> {
          String syntax = item.get(SYNTAX, String.class);
          var label = new Label(
              syntax == null || syntax.isBlank() ? objectTitle(item) : syntax);
          label.setStyle(COMPACT_STYLE);
          setGraphic(label);
        }
        case DOCUMENTATION -> {
          var content = renderDocumentation(item);
          if (content instanceof Region region) {
            region.prefWidthProperty().bind(documentationTree.widthProperty().subtract(58));
          }
          setGraphic(content);
          setPadding(new Insets(3, 5, 8, 2));
        }
        default -> setText(objectTitle(item));
      }
    }
  }
}
