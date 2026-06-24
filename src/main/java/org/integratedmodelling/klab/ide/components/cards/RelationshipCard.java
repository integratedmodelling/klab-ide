package org.integratedmodelling.klab.ide.components.cards;

import atlantafx.base.theme.Styles;
import atlantafx.base.theme.Tweaks;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.OverrunStyle;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.Text;
import org.integratedmodelling.common.services.client.digitaltwin.ClientKnowledgeGraph;
import org.integratedmodelling.common.utils.Utils;
import org.integratedmodelling.klab.api.data.KnowledgeGraph;
import org.integratedmodelling.klab.api.data.RuntimeAsset;
import org.integratedmodelling.klab.api.digitaltwin.GraphModel;
import org.integratedmodelling.klab.ide.IDEContextScope;
import org.integratedmodelling.klab.ide.KlabIDEController;
import org.integratedmodelling.klab.ide.Theme;
import org.integratedmodelling.klab.ide.components.generic.IconLabel;

/**
 * TODO shows the related assets from the knowledge graph, optionally filtered by direction, along
 * with the relationship type.
 */
public class RelationshipCard extends BaseCard<RuntimeAsset> {

  private static final double DEFAULT_WIDTH = 320;
  private static final double DEFAULT_HEIGHT = 220;
  private static final double DEFAULT_ARROW_WIDTH = 154;
  private static final double ARROW_HEIGHT = 30;
  private static final double TARGET_LABEL_WIDTH = 142;
  private static final Color[] RELATIONSHIP_COLORS = {
    Color.web("#2f7f6f"),
    Color.web("#0550ae"),
    Color.web("#9a3412"),
    Color.web("#8250df"),
    Color.web("#1f6feb"),
    Color.web("#bf3989"),
    Color.web("#57606a"),
    Color.web("#116329")
  };

  private final IDEContextScope scope;
  private final EnumSet<GraphModel.Relationship.Direction> directions;

  public RelationshipCard(
      RuntimeAsset asset, IDEContextScope scope, GraphModel.Relationship.Direction... directions) {
    super(asset, scope, true, false);
    this.scope = scope;
    this.directions = normalizeDirections(directions);
    drawContent();
  }

  @Override
  protected void drawContent() {
    getStyleClass().add("relationship-card");
    setPadding(new Insets(8));
    setMinSize(220, 150);
    setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
    if (extended) {
      setPrefSize(Region.USE_COMPUTED_SIZE, Region.USE_COMPUTED_SIZE);
    } else {
      setPrefSize(DEFAULT_WIDTH, DEFAULT_HEIGHT);
    }

    var rows = relationships();

    VBox content = new VBox(6);
    content.getStyleClass().add("relationship-card-content");
    content.setFillWidth(true);

    var table = createTable(rows);
    content.getChildren().addAll(createHeader(rows.size()), table);
    VBox.setVgrow(table, Priority.ALWAYS);
    setCenter(content);
  }

  private Node createHeader(int relationshipCount) {
    Label title = new Label("Relationships");
    title.getStyleClass().add("relationship-card-title");

    Label count = new Label(relationshipCount == 1 ? "1 link" : relationshipCount + " links");
    count.getStyleClass().add("relationship-card-chip");

    HBox spacer = new HBox();
    HBox.setHgrow(spacer, Priority.ALWAYS);

    HBox header = new HBox(6, title, spacer, count);
    header.setAlignment(Pos.CENTER_LEFT);
    header.setMinHeight(22);
    return header;
  }

  private TableView<RelationshipRow> createTable(List<RelationshipRow> rows) {
    TableView<RelationshipRow> table = new TableView<>(FXCollections.observableArrayList(rows));
    table
        .getStyleClass()
        .addAll("relationship-card-table", Styles.DENSE, Tweaks.EDGE_TO_EDGE, Tweaks.NO_HEADER);
    table.setColumnResizePolicy(TableView.UNCONSTRAINED_RESIZE_POLICY);
    table.setFocusTraversable(false);
    table.setFixedCellSize(38);
    table.setPlaceholder(createEmptyState());

    TableColumn<RelationshipRow, RelationshipRow> relationshipColumn =
        new TableColumn<>("Relationship");
    relationshipColumn.setCellValueFactory(param -> new ReadOnlyObjectWrapper<>(param.getValue()));
    relationshipColumn.setCellFactory(column -> new RelationshipCell());
    relationshipColumn.prefWidthProperty().bind(table.widthProperty().subtract(2));
    table.getColumns().setAll(relationshipColumn);
    return table;
  }

  private Node createEmptyState() {
    String text =
        asset == null
            ? "No asset selected"
            : scope == null ? "No context scope selected" : "No relationships";
    Label empty = new Label(text);
    empty.getStyleClass().add("relationship-card-empty");
    return empty;
  }

  private List<RelationshipRow> relationships() {
    if (asset == null || scope == null || scope.getDigitalTwin() == null) {
      return List.of();
    }

    var graph = scope.getDigitalTwin().getKnowledgeGraph();
    if (graph == null) {
      return List.of();
    }

    Map<RowKey, RelationshipRow> rows = new LinkedHashMap<>();
    if (graph instanceof ClientKnowledgeGraph clientGraph) {
      collectClientGraphRelationships(clientGraph, rows);
    } else {
      collectKnowledgeGraphRelationships(graph, rows);
    }

    return rows.values().stream()
        .sorted(
            Comparator.comparing(RelationshipRow::direction)
                .thenComparing(row -> row.type().name())
                .thenComparing(row -> labelFor(row.connectedAsset())))
        .toList();
  }

  private void collectClientGraphRelationships(
      ClientKnowledgeGraph graph, Map<RowKey, RelationshipRow> rows) {
    for (GraphModel.Relationship relationship : GraphModel.Relationship.values()) {
      if (directions.contains(GraphModel.Relationship.Direction.OUTGOING)) {
        for (RuntimeAsset connected : graph.outgoing(asset, relationship)) {
          addRow(rows, relationship, GraphModel.Relationship.Direction.OUTGOING, connected);
        }
      }
      if (directions.contains(GraphModel.Relationship.Direction.INCOMING)) {
        for (RuntimeAsset connected : graph.incoming(asset, relationship)) {
          addRow(rows, relationship, GraphModel.Relationship.Direction.INCOMING, connected);
        }
      }
    }
  }

  private void collectKnowledgeGraphRelationships(
      KnowledgeGraph graph, Map<RowKey, RelationshipRow> rows) {
    GraphModel.Relationship[] relationshipTypes = GraphModel.Relationship.values();
    for (GraphModel.Relationship.Direction direction : directions) {
      Collection<KnowledgeGraph.Link> links;
      try {
        links = graph.getLinks(asset, direction, scope, relationshipTypes);
      } catch (RuntimeException e) {
        scope.warn("Unable to retrieve knowledge graph relationships", e);
        continue;
      }
      for (KnowledgeGraph.Link link : links) {
        if (link == null || link.type() == null) {
          continue;
        }
        RuntimeAsset connected = oppositeEndpoint(link, asset);
        if (connected != null) {
          addRow(rows, link.type(), direction, connected);
        }
      }
    }
  }

  private void addRow(
      Map<RowKey, RelationshipRow> rows,
      GraphModel.Relationship type,
      GraphModel.Relationship.Direction direction,
      RuntimeAsset connectedAsset) {
    if (type == null || direction == null || connectedAsset == null) {
      return;
    }
    RowKey key = new RowKey(type, direction, stableId(connectedAsset));
    rows.putIfAbsent(key, new RelationshipRow(type, direction, connectedAsset));
  }

  private RuntimeAsset oppositeEndpoint(KnowledgeGraph.Link link, RuntimeAsset focus) {
    if (sameAsset(link.source(), focus)) {
      return link.target();
    }
    if (sameAsset(link.target(), focus)) {
      return link.source();
    }
    return link.target() == null ? link.source() : link.target();
  }

  private static boolean sameAsset(RuntimeAsset left, RuntimeAsset right) {
    return left != null && right != null && stableId(left) == stableId(right);
  }

  private static long stableId(RuntimeAsset asset) {
    if (asset == null) {
      return Long.MIN_VALUE;
    }
    return asset.getId() == -1 ? asset.getTransientId() : asset.getId();
  }

  private static EnumSet<GraphModel.Relationship.Direction> normalizeDirections(
      GraphModel.Relationship.Direction... directions) {
    if (directions == null || directions.length == 0) {
      return EnumSet.allOf(GraphModel.Relationship.Direction.class);
    }
    EnumSet<GraphModel.Relationship.Direction> ret =
        EnumSet.noneOf(GraphModel.Relationship.Direction.class);
    for (GraphModel.Relationship.Direction direction : directions) {
      if (direction != null) {
        ret.add(direction);
      }
    }
    return ret.isEmpty() ? EnumSet.allOf(GraphModel.Relationship.Direction.class) : ret;
  }

  private Node relationshipGraphic(RelationshipRow row) {
    HBox graphic = new HBox(6);
    graphic.getStyleClass().add("relationship-card-row-content");
    graphic.setAlignment(Pos.CENTER_LEFT);

    Node arrow = arrowView(row);
    Button icon = targetButton(row.connectedAsset());
    Label target = connectedAssetLabel(row.connectedAsset());

    graphic.getChildren().addAll(arrow, icon, target);
    HBox.setHgrow(arrow, Priority.ALWAYS);
    return graphic;
  }

  private Button targetButton(RuntimeAsset target) {
    IconLabel graphic = Theme.getGraphics(target);
    Button button = new Button(null, graphic);
    button.getStyleClass().add("relationship-card-target-button");
    button.setCursor(Cursor.HAND);
    button.setFocusTraversable(false);
    button.setTooltip(new Tooltip("Inspect " + labelFor(target)));
    button.setAccessibleText("Inspect " + labelFor(target));
    button.setMinSize(28, 28);
    button.setPrefSize(28, 28);
    button.setMaxSize(28, 28);
    button.setOnAction(event -> inspect(target));
    return button;
  }

  private Label connectedAssetLabel(RuntimeAsset target) {
    String label = labelFor(target);
    Label ret = new Label(label);
    ret.getStyleClass().add("relationship-card-target");
    ret.setTextOverrun(OverrunStyle.ELLIPSIS);
    ret.setMinWidth(74);
    ret.setPrefWidth(TARGET_LABEL_WIDTH);
    ret.setMaxWidth(TARGET_LABEL_WIDTH);
    ret.setTooltip(new Tooltip(label));
    return ret;
  }

  private void inspect(RuntimeAsset target) {
    if (target == null || KlabIDEController.instance() == null) {
      return;
    }
    KlabIDEController.instance().showInspector().inspect(target);
  }

  private Node arrowView(RelationshipRow row) {

    Canvas canvas = new Canvas(DEFAULT_ARROW_WIDTH, ARROW_HEIGHT);
    canvas.setMouseTransparent(true);

    StackPane track = new StackPane(canvas);
    track.getStyleClass().add("relationship-card-arrow-track");
    track.setMinWidth(74);
    track.setPrefWidth(DEFAULT_ARROW_WIDTH);
    track.setMaxWidth(Double.MAX_VALUE);
    track.setMinHeight(ARROW_HEIGHT);
    track.setPrefHeight(ARROW_HEIGHT);
    track.setMaxHeight(ARROW_HEIGHT);

    canvas.widthProperty().bind(track.widthProperty());
    canvas.heightProperty().bind(track.heightProperty());
    canvas
        .widthProperty()
        .addListener(
            (observable, oldValue, newValue) -> drawArrow(canvas, row.type(), row.direction()));
    canvas
        .heightProperty()
        .addListener(
            (observable, oldValue, newValue) -> drawArrow(canvas, row.type(), row.direction()));

    drawArrow(canvas, row.type(), row.direction());
    Tooltip.install(track, new Tooltip(row.type().name()));
    return track;
  }

  private void drawArrow(
      Canvas canvas,
      GraphModel.Relationship relationship,
      GraphModel.Relationship.Direction direction) {

    GraphicsContext gc = canvas.getGraphicsContext2D();
    double width = Math.max(44, canvas.getWidth());
    double height = Math.max(18, canvas.getHeight());
    gc.clearRect(0, 0, width, height);

    double mid = height / 2.0;
    double y1 = 5;
    double y2 = height - 5;
    double notch = Math.clamp(width * 0.16, 7, 13);
    double bodyInset = Math.clamp(width * 0.22, 14, 23);
    double head = Math.clamp(width * 0.2, 13, 19);
    double headBase = direction == GraphModel.Relationship.Direction.OUTGOING ? width - head : head;
    double headTip = direction == GraphModel.Relationship.Direction.OUTGOING ? width - 4 : 4;
    Color fill = relationshipColor(relationship);

    gc.setFill(fill);
    if (direction == GraphModel.Relationship.Direction.OUTGOING) {
      gc.fillPolygon(
          new double[] {3, bodyInset, headBase, headTip, headBase, bodyInset},
          new double[] {mid, y1, y1, mid, y2, y2},
          6);
      gc.setStroke(fill.darker());
      gc.strokePolyline(
          new double[] {3, bodyInset, headBase, headTip, headBase, bodyInset, 3},
          new double[] {mid, y1, y1, mid, y2, y2, mid},
          7);
    } else {
      gc.fillPolygon(
          new double[] {
            headTip, headBase, width - bodyInset, width - 3, width - bodyInset, headBase
          },
          new double[] {mid, y1, y1, mid, y2, y2},
          6);
      gc.setStroke(fill.darker());
      gc.strokePolyline(
          new double[] {
            headTip, headBase, width - bodyInset, width - 3, width - bodyInset, headBase, headTip
          },
          new double[] {mid, y1, y1, mid, y2, y2, mid},
          7);
    }

    String label = relationship.name();
    Font font = fittingFont(label, width - notch - 36);
    gc.setFont(font);
    gc.setFill(Color.rgb(255, 255, 255, 0.96));
    double textWidth = textWidth(label, font);
    double x = Math.max(10, (width - textWidth) / 2.0);
    double y = mid + 3.2;
    gc.fillText(label, x, y);
  }

  private static Font fittingFont(String label, double maxWidth) {
    double size = 8.5;
    Font font = Font.font("System", size);
    while (size > 6.2 && textWidth(label, font) > maxWidth) {
      size -= 0.35;
      font = Font.font("System", size);
    }
    return font;
  }

  private static double textWidth(String text, Font font) {
    Text helper = new Text(text);
    helper.setFont(font);
    return helper.getLayoutBounds().getWidth();
  }

  private static Color relationshipColor(GraphModel.Relationship relationship) {
    if (relationship == null) {
      return RELATIONSHIP_COLORS[0];
    }
    return switch (relationship.name()) {
      case "HAS_CHILD", "HAS_MEMBER", "HAS_CONTEXT", "CONTEXTUALIZED", "CONTEXTUALIZED_BY" ->
          Color.web("#2f7f6f");
      case "AFFECTS", "TRIGGERED", "CONTRIBUTED_TO" -> Color.web("#9a3412");
      case "HAS_DATA", "HAS_GEOMETRY", "HAS_DATAFLOW" -> Color.web("#0550ae");
      case "HAS_PROVENANCE", "HAS_ACTIVITY", "BY_AGENT", "CREATED", "EMERGED_FROM", "RESOLVED" ->
          Color.web("#8250df");
      case "HAS_RELATIONSHIP_SOURCE", "HAS_RELATIONSHIP_TARGET" -> Color.web("#bf3989");
      default -> RELATIONSHIP_COLORS[relationship.ordinal() % RELATIONSHIP_COLORS.length];
    };
  }

  private static String labelFor(RuntimeAsset asset) {
    if (asset == null) {
      return "Unresolved asset";
    }
    try {
      return Utils.Strings.abbreviate(Theme.getLabel(asset), 64);
    } catch (RuntimeException e) {
      return asset.toString();
    }
  }

  private record RelationshipRow(
      GraphModel.Relationship type,
      GraphModel.Relationship.Direction direction,
      RuntimeAsset connectedAsset) {}

  private record RowKey(
      GraphModel.Relationship type, GraphModel.Relationship.Direction direction, long targetId) {}

  private class RelationshipCell extends TableCell<RelationshipRow, RelationshipRow> {
    @Override
    protected void updateItem(RelationshipRow row, boolean empty) {
      super.updateItem(row, empty);
      setText(null);
      Node graphic = empty || row == null ? null : relationshipGraphic(row);
      if (graphic instanceof Region region) {
        region.setMaxWidth(Double.MAX_VALUE);
        region.prefWidthProperty().bind(widthProperty().subtract(8));
      }
      setGraphic(graphic);
    }
  }
}
