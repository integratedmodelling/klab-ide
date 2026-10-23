package org.integratedmodelling.klab.ide.components;

import atlantafx.base.theme.Styles;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import javafx.application.Platform;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import org.integratedmodelling.klab.api.knowledge.Urn;
import org.integratedmodelling.klab.api.services.resources.ResourceInfo;
import org.integratedmodelling.klab.ide.KlabIDEController;

/** A persistent results tab that receives one update before and after each import. */
final class LegacyResourceImportView extends VBox {
  private final Label summary = new Label("Scanning legacy resource folders…");
  private final TableView<LegacyResourceImport.Outcome> table = new TableView<>();

  LegacyResourceImportView(Consumer<ResourceInfo> openResource, String serviceId) {
    super(12);
    setPadding(new Insets(24));
    summary.getStyleClass().add(Styles.TITLE_3);
    var source = column("Legacy definition", row -> row.manifest().getAbsolutePath());
    source.setPrefWidth(300);
    var status =
        column(
            "Outcome",
            row ->
                switch (row.status()) {
                  case QUEUED -> "Queued";
                  case IMPORTING -> "Importing…";
                  case SUCCESS -> "Imported";
                  case ERROR -> "Error";
                });
    status.setCellFactory(
        ignored ->
            new TableCell<>() {
              @Override
              protected void updateItem(String value, boolean empty) {
                super.updateItem(value, empty);
                setText(empty ? null : value);
                setStyle(
                    empty
                        ? ""
                        : "-fx-text-fill: "
                            + ("Imported".equals(value)
                                ? "-color-success-fg"
                                : "Error".equals(value) ? "-color-danger-fg" : "-color-fg-muted")
                            + ";");
              }
            });
    var urn = column("New URN", LegacyResourceImport.Outcome::urn);
    urn.setPrefWidth(280);
    var details = column("Explanation", LegacyResourceImport.Outcome::explanation);
    details.setPrefWidth(350);
    var open = new TableColumn<LegacyResourceImport.Outcome, String>("Resource");
    open.setCellValueFactory(cell -> new ReadOnlyStringWrapper(cell.getValue().urn()));
    open.setCellFactory(
        ignored ->
            new TableCell<>() {
              private final Button button = new Button("Open");

              {
                button.setOnAction(
                    event -> {
                      var row = getTableRow().getItem();
                      if (row == null || row.status() != LegacyResourceImport.Status.SUCCESS)
                        return;
                      var info = new ResourceInfo();
                      info.setServiceId(serviceId);
                      info.setUrn(row.urn());
                      openResource.accept(info);
                    });
              }

              @Override
              protected void updateItem(String value, boolean empty) {
                super.updateItem(value, empty);
                setGraphic(!empty && value != null && !value.isBlank() ? button : null);
              }
            });
    table.getColumns().addAll(List.of(source, status, urn, details, open));
    table.setPlaceholder(new Label("No legacy definitions found (resource.json)"));
    VBox.setVgrow(table, Priority.ALWAYS);
    getChildren().addAll(summary, table);
  }

  CompletableFuture<Boolean> start(BatchResourceInput.Input input, Runnable finished) {
    var completion = new CompletableFuture<Boolean>();
    var user = KlabIDEController.instance().user();
    var worker =
        new Thread(
            () -> {
              try {
                if (!input.service().isLocal())
                  throw new IllegalArgumentException(
                      "Legacy import requires a local Resources service");
                var manifests = LegacyResourceImport.discover(input.files());
                Platform.runLater(
                    () -> {
                      table
                          .getItems()
                          .setAll(
                              manifests.stream()
                                  .map(
                                      file ->
                                          new LegacyResourceImport.Outcome(
                                              file, LegacyResourceImport.Status.QUEUED, "", ""))
                                  .toList());
                      updateSummary();
                    });
                var schema =
                    input.service().capabilities(user).getImportSchemata().values().stream()
                        .flatMap(List::stream)
                        .filter(
                            candidate -> "resources.legacy.files".equals(candidate.getSchemaId()))
                        .findFirst()
                        .orElseThrow(
                            () ->
                                new IllegalStateException(
                                    "The service does not provide the resources.legacy.files importer"));
                LegacyResourceImport.run(
                    manifests,
                    file ->
                        input
                            .service()
                            .importAsset(schema, schema.asset(file), Urn.UNDEFINED_URN, user)
                            .join(),
                    outcome ->
                        Platform.runLater(
                            () -> {
                              for (int i = 0; i < table.getItems().size(); i++) {
                                if (table.getItems().get(i).manifest().equals(outcome.manifest())) {
                                  table.getItems().set(i, outcome);
                                  break;
                                }
                              }
                              updateSummary();
                            }));
                Platform.runLater(finished);
                completion.complete(true);
              } catch (Exception failure) {
                Platform.runLater(
                    () -> {
                      summary.setText("Legacy import failed: " + failure.getMessage());
                      table
                          .getItems()
                          .replaceAll(
                              row ->
                                  new LegacyResourceImport.Outcome(
                                      row.manifest(),
                                      LegacyResourceImport.Status.ERROR,
                                      "",
                                      failure.getMessage()));
                    });
                completion.complete(false);
              }
            },
            "legacy-resource-import");
    worker.setDaemon(true);
    worker.start();
    return completion;
  }

  private void updateSummary() {
    long success =
        table.getItems().stream()
            .filter(row -> row.status() == LegacyResourceImport.Status.SUCCESS)
            .count();
    long errors =
        table.getItems().stream()
            .filter(row -> row.status() == LegacyResourceImport.Status.ERROR)
            .count();
    summary.setText(
        (success + errors)
            + " / "
            + table.getItems().size()
            + " processed — "
            + success
            + " imported, "
            + errors
            + " failed");
  }

  private static TableColumn<LegacyResourceImport.Outcome, String> column(
      String title, java.util.function.Function<LegacyResourceImport.Outcome, String> value) {
    var column = new TableColumn<LegacyResourceImport.Outcome, String>(title);
    column.setSortable(false);
    column.setCellValueFactory(cell -> new ReadOnlyStringWrapper(value.apply(cell.getValue())));
    column.setCellFactory(
        ignored ->
            new TableCell<>() {
              @Override
              protected void updateItem(String text, boolean empty) {
                super.updateItem(text, empty);
                setText(empty ? null : text);
                setTooltip(empty || text == null || text.isBlank() ? null : new Tooltip(text));
              }
            });
    return column;
  }
}
