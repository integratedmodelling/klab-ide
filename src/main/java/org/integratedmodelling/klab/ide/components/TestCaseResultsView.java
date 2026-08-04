package org.integratedmodelling.klab.ide.components;

import atlantafx.base.theme.Styles;
import java.util.Set;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.TitledPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import org.integratedmodelling.klab.api.actors.RuntimeAgent;
import org.integratedmodelling.klab.api.collections.DomainObject;
import org.integratedmodelling.klab.ide.Theme;
import org.integratedmodelling.klab.ide.components.generic.DomainObjectView;
import org.integratedmodelling.klab.ide.components.generic.IconLabel;
import org.kordamp.ikonli.material2.Material2AL;

/** Persistent, live presentation of one test-case execution. */
public final class TestCaseResultsView extends DomainObjectView {

  private static final Set<String> REPORT_PROPERTIES =
      Set.of(
          DomainObject.TYPE,
          DomainObject.NAME,
          DomainObject.DESCRIPTION,
          DomainObject.URN,
          "testcase",
          "start",
          "end",
          "tests",
          "testsFinished",
          "testsPassed",
          "testsFailed",
          "assertions",
          "assertionsPassed",
          "assertionsFailed");

  private static final Set<String> TEST_PROPERTIES =
      Set.of(
          DomainObject.TYPE,
          DomainObject.NAME,
          DomainObject.DESCRIPTION,
          DomainObject.URN,
          "start",
          "end",
          "outcome",
          "assertions",
          "assertionsPassed",
          "assertionsFailed",
          "stacktrace");

  private final TestCaseResultModel model;

  public TestCaseResultsView() {
    this(new TestCaseResultModel());
  }

  TestCaseResultsView(TestCaseResultModel model) {
    this.model = model;
  }

  public void accept(RuntimeAgent.TestMessageType type, DomainObject payload) {
    model.accept(type, payload);
    if (Platform.isFxApplicationThread()) {
      setDomainObject(model.report());
    } else {
      Platform.runLater(() -> setDomainObject(model.report()));
    }
  }

  public TestCaseResultModel.Statistics statistics() {
    return model.statistics();
  }

  public void fail(String testCaseName, String detail) {
    long now = System.currentTimeMillis();
    accept(
        RuntimeAgent.TestMessageType.TESTCASE_FINISHED,
        DomainObject.create(
            DomainObject.TYPE,
            "testcase",
            DomainObject.NAME,
            testCaseName,
            DomainObject.DESCRIPTION,
            detail,
            "testcase",
            testCaseName,
            "start",
            now,
            "end",
            now,
            "outcome",
            false));
  }

  @Override
  protected Node createObjectNode(DomainObject object, int depth) {
    return switch (String.valueOf(object.type())) {
      case "testcase" -> createTestCase(object, depth);
      case "test" -> createTest(object, depth);
      case "assertion" -> createAssertion(object);
      default -> super.createObjectNode(object, depth);
    };
  }

  private Node createTestCase(DomainObject report, int depth) {
    var box = new VBox(10);
    var title = new Label(String.valueOf(report.get("testcase")));
    title.getStyleClass().add(Styles.TITLE_3);
    var stats = statistics();
    boolean failed = stats.finished() && !report.get("outcome", stats.testsFailed() == 0);
    var state = new Label(stats.finished() ? (failed ? "Failed" : "Finished") : "Running");
    state.setTextFill(
        stats.finished() ? (failed ? Color.FIREBRICK : Color.FORESTGREEN) : Color.DODGERBLUE);
    var spacer = new Region();
    HBox.setHgrow(spacer, Priority.ALWAYS);
    var heading =
        new HBox(
            8,
            new IconLabel(Theme.TESTCASE_ICON, 20, Theme.FOREGROUND_COLOR),
            title,
            spacer,
            state);
    heading.setAlignment(Pos.CENTER_LEFT);
    box.getChildren().add(heading);
    if (report.description() != null && !report.description().isBlank()) {
      var description = new Label(report.description());
      description.setWrapText(true);
      box.getChildren().add(description);
    }

    var summary =
        new Label(
            stats.testsFinished()
                + "/"
                + stats.tests()
                + " tests  •  "
                + stats.testsPassed()
                + " passed  •  "
                + stats.testsFailed()
                + " failed  •  "
                + stats.assertionsPassed()
                + "/"
                + stats.assertions()
                + " assertions  •  "
                + formatElapsed(stats.elapsedMilliseconds()));
    summary.setWrapText(true);
    box.getChildren().add(summary);
    var properties = createProperties(report, REPORT_PROPERTIES);
    if (!properties.getChildren().isEmpty()) {
      box.getChildren().add(properties);
    }
    for (var test : report.getChildren()) {
      box.getChildren().add(createObjectNode(test, depth + 1));
    }
    return box;
  }

  private Node createTest(DomainObject test, int depth) {
    boolean complete = test.get("end") != null;
    boolean passed = complete && test.get("outcome", false);
    var content = new VBox(6);
    content.setPadding(new Insets(6));
    if (test.description() != null && !test.description().isBlank()) {
      var description = new Label(test.description());
      description.setWrapText(true);
      content.getChildren().add(description);
    }
    var properties = createProperties(test, TEST_PROPERTIES);
    if (!properties.getChildren().isEmpty()) {
      content.getChildren().add(properties);
    }
    var stacktrace = test.get("stacktrace", String.class);
    if (stacktrace != null && !stacktrace.isBlank()) {
      var trace = new Label(stacktrace);
      trace.setWrapText(true);
      trace.setStyle("-fx-font-family: monospace; -fx-font-size: 10px;");
      var details = new TitledPane("Test failure", trace);
      details.setExpanded(false);
      content.getChildren().add(details);
    }
    for (var assertion : test.getChildren()) {
      content.getChildren().add(createObjectNode(assertion, depth + 1));
    }

    Color color = complete ? (passed ? Color.FORESTGREEN : Color.FIREBRICK) : Color.DODGERBLUE;
    var icon =
        new IconLabel(
            complete
                ? (passed ? Material2AL.CHECK_CIRCLE : Material2AL.ERROR)
                : Theme.TESTCASE_ICON,
            16,
            color);
    long start = number(test.get("start"));
    long end = number(test.get("end"));
    var title =
        objectTitle(test)
            + " — "
            + (complete ? (passed ? "passed" : "failed") : "running")
            + (start > 0 && end >= start ? " • " + formatElapsed(end - start) : "");
    var pane = new TitledPane(title, content);
    pane.setGraphic(icon);
    pane.setExpanded(!complete || !passed);
    return pane;
  }

  private Node createAssertion(DomainObject assertion) {
    boolean passed = assertion.get("outcome", false);
    var box = new VBox(4);
    box.setPadding(new Insets(5, 8, 5, 8));
    var source = new Label(assertion.urn() == null ? "Assertion" : assertion.urn());
    source.setWrapText(true);
    var row =
        new HBox(
            7,
            new IconLabel(
                passed ? Material2AL.CHECK_CIRCLE : Material2AL.ERROR,
                14,
                passed ? Color.FORESTGREEN : Color.FIREBRICK),
            source);
    row.setAlignment(Pos.TOP_LEFT);
    box.getChildren().add(row);
    var stacktrace = assertion.get("stacktrace", String.class);
    if (stacktrace != null && !stacktrace.isBlank()) {
      var trace = new Label(stacktrace);
      trace.setWrapText(true);
      trace.setStyle("-fx-font-family: monospace; -fx-font-size: 10px;");
      var details = new TitledPane("Stack trace", trace);
      details.setExpanded(false);
      box.getChildren().add(details);
    }
    return box;
  }

  private static String formatElapsed(long milliseconds) {
    long seconds = Math.max(0, milliseconds / 1000);
    long minutes = seconds / 60;
    return minutes > 0
        ? String.format("%dm %02ds", minutes, seconds % 60)
        : String.format("%.1fs", milliseconds / 1000.0);
  }

  private static long number(Object value) {
    return value instanceof Number number ? number.longValue() : 0;
  }
}
