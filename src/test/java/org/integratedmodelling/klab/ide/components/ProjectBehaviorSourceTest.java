package org.integratedmodelling.klab.ide.components;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.lang.reflect.Proxy;
import java.util.List;
import org.integratedmodelling.klab.api.knowledge.organization.Project;
import org.integratedmodelling.klab.api.knowledge.organization.impl.ProjectImpl;
import org.integratedmodelling.klab.api.lang.kactors.KActorsBehavior;
import org.integratedmodelling.klab.api.lang.kactors.impl.KActorsBehaviorImpl;
import org.integratedmodelling.klab.api.services.ResourcesService;
import org.integratedmodelling.klab.api.scope.UserScope;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ProjectBehaviorSourceTest {
  @TempDir Path temporaryDirectory;

  @Test void openingLegacyTestMirrorLearnsItsTypeAndServiceRevision() throws Exception {
    var mirrors = new ManagedBehaviorMirrors(temporaryDirectory);
    var local = document(KActorsBehavior.Type.UNITTEST, "original");
    var remote = document(KActorsBehavior.Type.UNITTEST, "saved test");
    remote.setLastUpdateTimestamp(200);
    var project = new ProjectImpl(); project.setUrn(local.getProjectName());
    project.setTestCases(List.of(remote));
    var service = service(project);
    var user = mock(UserScope.class);
    when(user.getServices(ResourcesService.class)).thenReturn(List.of(service));
    var checkout = mirrors.checkout("resources", project.getUrn(), local.getUrn(), "original");
    var parser = mock(ResourcesService.class);
    when(parser.parseAsset(checkout.file().toUri().toURL(), KActorsBehavior.class, user)).thenReturn(local);
    assertSame(remote, ProjectBehaviorSource.openMirror(mirrors, checkout.file(), parser, user));
    var origin = mirrors.origin(checkout.file()).orElseThrow();
    assertEquals(KActorsBehavior.Type.UNITTEST, origin.behaviorType());
    assertEquals(200, origin.synchronizedTimestamp());
    assertEquals("saved test", Files.readString(checkout.file()));
  }

  @Test void reopeningEveryFamilyFetchesProjectRevisionAndPreservesLocalEdits() throws Exception {
    var project = new ProjectImpl(); project.setUrn("klab.staging.vxii");
    var documents = new java.util.ArrayList<KActorsBehavior>();
    for (var type : KActorsBehavior.Type.values()) documents.add(document(type, "original " + type));
    project.setApps(documents.stream().filter(d -> d.getBehaviorType() == KActorsBehavior.Type.APP).toList());
    project.setScripts(documents.stream().filter(d -> d.getBehaviorType() == KActorsBehavior.Type.SCRIPT).toList());
    project.setTestCases(documents.stream().filter(d -> d.getBehaviorType() == KActorsBehavior.Type.UNITTEST).toList());
    project.setBehaviors(documents.stream().filter(d -> d.getBehaviorType() != KActorsBehavior.Type.APP
        && d.getBehaviorType() != KActorsBehavior.Type.SCRIPT && d.getBehaviorType() != KActorsBehavior.Type.UNITTEST).toList());
    var service = service(project);
    var user = mock(UserScope.class);
    when(user.getServices(ResourcesService.class)).thenReturn(List.of(service));
    var parser = mock(ResourcesService.class);
    var mirrors = new ManagedBehaviorMirrors(temporaryDirectory);
    for (var document : documents) {
      var checkout = mirrors.checkout(service.serviceId(), project.getUrn(), document.getUrn(),
          document.getBehaviorType(), document.getSourceCode(), 100);
      var remote = (KActorsBehaviorImpl) document;
      remote.setLastUpdateTimestamp(200); remote.setSourceCode("updated " + remote.getBehaviorType());
      assertSame(remote, ProjectBehaviorSource.openMirror(mirrors, checkout.file(), parser, user));
      assertEquals(remote.getSourceCode(), Files.readString(checkout.file()));
      Files.writeString(checkout.file(), "local draft");
      remote.setLastUpdateTimestamp(300); remote.setSourceCode("next revision");
      var local = document(remote.getBehaviorType(), "local draft");
      when(parser.parseAsset(checkout.file().toUri().toURL(), KActorsBehavior.class, user)).thenReturn(local);
      assertSame(local, ProjectBehaviorSource.openMirror(mirrors, checkout.file(), parser, user));
      assertEquals("local draft", Files.readString(checkout.file()));
      // The user can explicitly accept the project copy without deleting any local work.
      assertSame(remote, ProjectBehaviorSource.openMirror(mirrors, checkout.file(), parser, user,
          candidate -> { assertSame(remote, candidate); return true; }));
      assertEquals("next revision", Files.readString(checkout.file()));
      assertEquals(ManagedBehaviorMirrors.LocalState.SYNCHRONIZED, mirrors.localState(checkout.file()));
      assertEquals(300, mirrors.origin(checkout.file()).orElseThrow().synchronizedTimestamp());
      try (var siblings = Files.list(checkout.file().getParent())) {
        var backups = siblings.filter(path -> path.toString().endsWith(".bak")).toList();
        assertEquals(1, backups.size());
        assertEquals("local draft", Files.readString(backups.getFirst()));
      }
      // Once resolved, the next reopen must not ask again.
      assertSame(remote, ProjectBehaviorSource.openMirror(mirrors, checkout.file(), parser, user,
          candidate -> { fail("The mirror is already synchronized"); return false; }));
    }
  }
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
          if (method.getName().equals("serviceId")) return "resources";
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
