package org.integratedmodelling.klab.ide.components;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Set;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.stage.Stage;
import org.integratedmodelling.klab.api.scope.UserScope;
import org.integratedmodelling.klab.api.services.ResourcesService;
import org.integratedmodelling.klab.api.services.resources.workflow.*;
import org.integratedmodelling.klab.ide.KlabIDEController;

/** Opt-in slow-response native QA. No network calls and no production state. */
public final class WorkflowProgressFixture {
  public static void main(String[] args) {
    Platform.startup(() -> {
      javafx.application.Application.setUserAgentStylesheet(new atlantafx.base.theme.PrimerLight().getUserAgentStylesheet());
      var fixture = new ReviewEditorJourneyFixture();
      var service = (ResourcesService) Proxy.newProxyInstance(ResourcesService.class.getClassLoader(),
          new Class<?>[] {ResourcesService.class}, (proxy, method, values) -> {
            if (Set.of("getWorkflow", "getFlow", "getFlows", "updateFlowStateDetails", "getFlowActions", "executeFlowAction").contains(method.getName())) {
              if (Platform.isFxApplicationThread()) throw new AssertionError("Service call blocked JavaFX: " + method.getName());
              Thread.sleep(3500);
            }
            if (method.getName().equals("getWorkflow")) return fixture.workflow;
            try { return method.invoke(fixture.service, values); }
            catch (InvocationTargetException error) { throw error.getCause(); }
          });
      new KlabIDEController() {
        @Override public UserScope user() { return fixture.scope; }
        @Override public boolean handleNotification(org.integratedmodelling.klab.api.services.runtime.Notification notice) {
          System.out.println(notice); return true;
        }
      };
      WorkflowNativeControlsTest.savedEditor(fixture, () -> {}, ignored -> {}).close();
      var workspace = new org.integratedmodelling.klab.api.knowledge.organization.impl.WorkspaceImpl();
      workspace.setUrn("synthetic-progress-workspace"); workspace.setProjects(List.of());
      var prepared = new WorkspaceOpening.Prepared(new org.integratedmodelling.klab.modeler.model.NavigableWorkspace(workspace),
          new WorkspaceOpening.Access(Set.of(), Set.of(), List.of()));
      WorkflowUIProvider provider = new WorkflowUIProvider() {
        public List<Workflow> availableWorkflows(org.integratedmodelling.klab.api.knowledge.KlabAsset asset, UserScope scope) { return List.of(fixture.workflow); }
        public WorkflowEditor.StageEditor stageEditor(Workflow w, Flow f, Flow.State state, Workflow.StateSchema schema,
            boolean readOnly, Runnable changed) { return ReviewStageEditor.create(w, f, state, service.getUrl()); }
      };
      var host = new WorkspaceEditor(service, prepared, null, provider);
      var open = new Button("Open synthetic saved workflow");
      open.setOnAction(event -> {
        try {
          var method = WorkspaceEditor.class.getDeclaredMethod("openWorkflow", Flow.class, Workflow.class);
          method.setAccessible(true); method.invoke(host, fixture.copy(), null);
        } catch (ReflectiveOperationException error) { throw new RuntimeException(error); }
      });
      var root = new BorderPane(host);
      root.setTop(new HBox(12, open, new Label("Isolated fixture: 3.5-second responses, no network or user data")));
      var stage = new Stage(); stage.setTitle("Workflow progress - isolated fixture");
      stage.setScene(new Scene(root, 1180, 900));
      stage.setOnCloseRequest(event -> { host.close(); Platform.exit(); });
      stage.show();
    });
  }
}
