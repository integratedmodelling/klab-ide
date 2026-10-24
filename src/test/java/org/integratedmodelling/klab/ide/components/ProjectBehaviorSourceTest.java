package org.integratedmodelling.klab.ide.components;

import static org.junit.jupiter.api.Assertions.*;
import java.lang.reflect.Proxy;
import java.util.List;
import org.integratedmodelling.klab.api.knowledge.organization.Project;
import org.integratedmodelling.klab.api.knowledge.organization.impl.ProjectImpl;
import org.integratedmodelling.klab.api.lang.kactors.KActorsBehavior;
import org.integratedmodelling.klab.api.lang.kactors.impl.KActorsBehaviorImpl;
import org.integratedmodelling.klab.api.services.ResourcesService;
import org.junit.jupiter.api.Test;

class ProjectBehaviorSourceTest {
  @Test void selectsProjectApplicationRatherThanSameUrnBehaviorFromGlobalIndex() {
    var original = document(KActorsBehavior.Type.BEHAVIOR, "behavior staging.vxii.demoapp");
    var application = document(KActorsBehavior.Type.APP, "web app staging.vxii.demoapp action main: ui.text(\"Ready\")");
    var project = new ProjectImpl(); project.setUrn("klab.staging.vxii");
    project.setBehaviors(List.of(original)); project.setApps(List.of(application));
    var service = service(project);
    for (String source : List.of(application.getSourceCode(), "web app staging.vxii.demoapp action main: ui.text(\"Updated\")")) {
      application.setSourceCode(source);
      assertSame(application, ProjectBehaviorSource.retrieve(service, null, project.getUrn(), application.getUrn(), KActorsBehavior.Type.APP));
      assertEquals(source, ProjectBehaviorSource.retrieve(service, null, project.getUrn(), application.getUrn(), KActorsBehavior.Type.APP).getSourceCode());
      assertSame(original, ProjectBehaviorSource.retrieve(service, null, project.getUrn(), original.getUrn(), KActorsBehavior.Type.BEHAVIOR));
    }
  }

  @Test void doesNotFallBackToAnotherDocumentFamilyWhenSelectedApplicationIsMissing() {
    var original = document(KActorsBehavior.Type.BEHAVIOR, "behavior staging.vxii.demoapp");
    var project = new ProjectImpl(); project.setUrn("klab.staging.vxii"); project.setBehaviors(List.of(original));
    assertNull(ProjectBehaviorSource.retrieve(service(project), null, project.getUrn(), original.getUrn(), KActorsBehavior.Type.APP));
  }

  private static ResourcesService service(Project project) {
    return (ResourcesService) Proxy.newProxyInstance(ResourcesService.class.getClassLoader(), new Class<?>[]{ResourcesService.class},
        (self, method, args) -> {
          if (method.getName().equals("retrieve")) {
            if (args[1] == Project.class) { assertEquals(project.getUrn(), args[0]); return project; }
            fail("The selected document must not be retrieved through the global URN index");
          }
          return null;
        });
  }

  private static KActorsBehaviorImpl document(KActorsBehavior.Type type, String source) {
    var document = new KActorsBehaviorImpl(); document.setUrn("staging.vxii.demoapp");
    document.setProjectName("klab.staging.vxii"); document.setBehaviorType(type); document.setSourceCode(source); return document;
  }
}
