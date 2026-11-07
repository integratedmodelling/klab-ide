package org.integratedmodelling.klab.ide.components;

import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;
import org.integratedmodelling.klab.api.services.resources.workflow.*;

/** Opt-in native chooser/deletion QA. Uses only resettable, in-memory synthetic records. */
public final class WorkflowNativeControlsFixture {
  public static void main(String[] args) {
    Platform.startup(() -> {
      javafx.application.Application.setUserAgentStylesheet(new atlantafx.base.theme.PrimerLight().getUserAgentStylesheet());
      var root = new BorderPane();
      var tabs = new TabPane(); root.setCenter(tabs);
      var list = new ListView<String>(); list.setPrefWidth(210); root.setLeft(list);
      var fail = new CheckBox("Simulate deletion failure");
      var note = new Label("Isolated synthetic flow. No production data or service calls.");
      var result = new Label(); root.setBottom(result);
      var current = new AtomicReference<ReviewEditorJourneyFixture>();
      fail.selectedProperty().addListener((o, old, value) -> {
        if (current.get() != null) current.get().failDeletion = value;
      });
      var open = new Button("Open synthetic flow");
      open.setOnAction(event -> {
        tabs.getTabs().forEach(tab -> { if (tab.getContent() instanceof WorkflowEditor editor) editor.close(); });
        tabs.getTabs().clear(); list.getItems().clear();
        var fixture = new ReviewEditorJourneyFixture(); current.set(fixture);
        fixture.failDeletion = fail.isSelected();
        if (args.length > 0) fixture.diagramPath = Path.of(args[0]);
        var initial = Flow.State.create(); initial.setSchemaId("bootstrap"); initial.setOwner("fixture-editor");
        initial.setTitle("Synthetic native verification"); initial.setDescription("Discardable test only");
        initial.setAssetUrn("fixture.hydrology");
        fixture.saved = WorkflowUIProvider.NONE.draftFlow(fixture.workflow, initial, fixture.scope);
        fixture.saved.setId("fixture-native-flow"); fixture.saved.setRevision(3);
        initial.setFlowId(fixture.saved.getId());
        var tab = new Tab("Synthetic flow");
        var editor = new WorkflowEditor(fixture.service, fixture.scope, fixture.workflow, fixture.copy(),
            (w,f,s,schema,ro,changed) -> ReviewStageEditor.create(w,f,s,fixture.service.getUrl()),
            () -> { tabs.getTabs().remove(tab); result.setText("Synthetic flow deleted; selected tab closed and sidebar entry removed."); },
            null, removed -> list.getItems().remove(removed.getId()));
        tab.setContent(editor); tab.setOnClosed(e -> editor.close());
        tabs.getTabs().add(tab); list.getItems().add(fixture.saved.getId()); list.getSelectionModel().selectFirst();
        result.setText("Synthetic flow loaded. Delete and Export PNG exercise the production controls.");
      });
      root.setTop(new HBox(12, open, fail, note));
      var stage = new Stage(); stage.setTitle("Workflow native controls - isolated synthetic fixture");
      stage.setScene(new Scene(root, 1370, 900)); stage.setOnCloseRequest(e -> Platform.exit()); stage.show();
      open.fire();
    });
  }
}
