package org.integratedmodelling.klab.ide;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.integratedmodelling.klab.api.knowledge.KlabAsset;
import org.integratedmodelling.klab.api.lang.kim.impl.KimNamespaceImpl;
import org.integratedmodelling.klab.api.lang.kim.impl.KimOntologyImpl;
import org.integratedmodelling.klab.api.scope.UserScope;
import org.integratedmodelling.klab.api.services.ResourcesService;
import org.integratedmodelling.klab.api.services.resources.ResourceSet;
import org.integratedmodelling.klab.api.services.runtime.Notification;
import org.integratedmodelling.klab.modeler.model.NavigableKimNamespace;
import org.integratedmodelling.klab.modeler.model.NavigableKimOntology;
import org.junit.jupiter.api.Test;

class DocumentDeletionTest {
  @Test
  void namespaceAndOntologyDeletionsIncludeProjectAndPreserveTreeUrns() {
    var namespace = new KimNamespaceImpl();
    namespace.setUrn("demo.models");
    namespace.setProjectName("project");
    var ontology = new KimOntologyImpl();
    ontology.setUrn("demo");
    ontology.setProjectName("worldview");
    var scope = (UserScope) Proxy.newProxyInstance(
        UserScope.class.getClassLoader(), new Class<?>[] {UserScope.class},
        (proxy, method, args) -> null);
    var calls = new AtomicInteger();
    var result = List.of(new ResourceSet());
    var service = service((proxy, method, args) -> {
      assertEquals("delete", method.getName());
      int call = calls.getAndIncrement();
      assertEquals(call == 0 ? "project/demo.models" : "worldview/demo", args[0]);
      assertEquals(call == 0 ? KlabAsset.KnowledgeClass.NAMESPACE : KlabAsset.KnowledgeClass.ONTOLOGY, args[1]);
      assertSame(scope, args[2]);
      return result;
    });

    assertSame(result, DocumentDeletion.delete(service, new NavigableKimNamespace(namespace, null), scope));
    assertSame(result, DocumentDeletion.delete(service, new NavigableKimOntology(ontology, null), scope));
    assertEquals(2, calls.get());
    assertEquals("demo.models", namespace.getUrn());
    assertEquals("demo", ontology.getUrn());
  }

  @Test
  void missingProjectNeverInvokesTheApi() {
    var namespace = new KimNamespaceImpl();
    namespace.setUrn("demo");
    var service = service((proxy, method, args) -> {
      fail("Deletion must not call the API without a project");
      return null;
    });
    assertThrows(IllegalArgumentException.class,
        () -> DocumentDeletion.delete(service, new NavigableKimNamespace(namespace, null), null));
  }

  @Test
  void rejectedDeletionRetainsServiceNotificationsAndEmptyResponsesBecomeErrors() {
    var namespace = new KimNamespaceImpl();
    namespace.setUrn("demo");
    namespace.setProjectName("project");
    var document = new NavigableKimNamespace(namespace, null);
    var rejected = List.of(ResourceSet.empty(Notification.error("Document is referenced")));
    assertSame(rejected, DocumentDeletion.delete(service((proxy, method, args) -> rejected), document, null));
    for (var response : new Object[] {null, List.of()}) {
      var result = DocumentDeletion.delete(service((proxy, method, args) -> response), document, null);
      assertEquals(1, result.size());
      assertTrue(result.getFirst().isEmpty());
      assertFalse(result.getFirst().getNotifications().isEmpty());
    }
    assertThrows(IllegalStateException.class,
        () -> DocumentDeletion.delete(service((proxy, method, args) -> {
          throw new IllegalStateException("Service unavailable");
        }), document, null));
  }

  private static ResourcesService service(java.lang.reflect.InvocationHandler handler) {
    return (ResourcesService) Proxy.newProxyInstance(
        ResourcesService.class.getClassLoader(), new Class<?>[] {ResourcesService.class}, handler);
  }
}
