package org.integratedmodelling.klab.ide.components;

import static org.junit.jupiter.api.Assertions.*;
import java.lang.reflect.Proxy;
import java.net.URI;
import java.util.*;
import java.util.concurrent.atomic.*;
import java.util.function.Function;
import org.integratedmodelling.klab.api.knowledge.Worldview;
import org.integratedmodelling.klab.api.knowledge.impl.WorldviewImpl;
import org.integratedmodelling.klab.api.lang.kim.impl.KimOntologyImpl;
import org.integratedmodelling.klab.api.scope.UserScope;
import org.integratedmodelling.klab.api.services.*;
import org.junit.jupiter.api.Test;

class WorldviewSourceTest {
  private static <T> T proxy(Class<T> type, Function<String, Object> values) {
    return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, (self, method, args) -> {
      return switch (method.getName()) {
        case "hashCode" -> System.identityHashCode(self);
        case "equals" -> self == args[0];
        case "toString" -> type.getSimpleName();
        default -> values.apply(method.getName());
      };
    }));
  }

  private static WorldviewImpl worldview(String id, String source) {
    var world = new WorldviewImpl(); world.setWorldviewId(id); world.setUrn("test");
    var ontology = new KimOntologyImpl(); ontology.setUrn("test"); ontology.setSourceCode(source);
    world.getOntologies().add(ontology); return world;
  }

  private static Reasoner reasoner(boolean local, AtomicReference<String> id, AtomicLong revision, AtomicBoolean online) {
    var status = proxy(KlabService.ServiceStatus.class, method -> method.equals("isOperational") ? online.get() : null);
    var caps = proxy(Reasoner.Capabilities.class, method -> switch (method) {
      case "getWorldviewId" -> id.get(); case "getKnowledgeRevision" -> revision.get(); default -> null;
    });
    return proxy(Reasoner.class, method -> switch (method) {
      case "getUrl" -> { try { yield URI.create(local ? "http://127.0.0.1:8091" : "https://remote.example").toURL(); }
        catch (Exception error) { throw new AssertionError(error); } }
      case "capabilities" -> caps; case "status" -> status; default -> null;
    });
  }

  private static ResourcesService resources(AtomicReference<Worldview> world, AtomicBoolean online) {
    var status = proxy(KlabService.ServiceStatus.class, method -> method.equals("isOperational") ? online.get() : null);
    return proxy(ResourcesService.class, method -> switch (method) {
      case "getUrl" -> { try { yield URI.create("http://localhost:8092").toURL(); }
        catch (Exception error) { throw new AssertionError(error); } }
      case "serviceId" -> "local-resources";
      case "status" -> status;
      case "list" -> world.get() == null ? List.of() : List.of(world.get());
      default -> null;
    });
  }

  private static class Fixture {
    final AtomicReference<String> localId = new AtomicReference<>("local");
    final AtomicLong revision = new AtomicLong(1);
    final AtomicBoolean online = new AtomicBoolean(true);
    final AtomicReference<Worldview> world = new AtomicReference<>(worldview("local", "local content"));
    final Reasoner local = reasoner(true, localId, revision, online);
    final Reasoner remote = reasoner(false, new AtomicReference<>("remote"), new AtomicLong(), new AtomicBoolean(true));
    final ResourcesService resources = resources(world, online);
    final AtomicInteger remoteReads = new AtomicInteger();
    final UserScope scope = (UserScope) Proxy.newProxyInstance(UserScope.class.getClassLoader(), new Class<?>[]{UserScope.class},
        (self, method, args) -> switch (method.getName()) {
          case "getServices" -> args[0] == Reasoner.class ? List.of(remote, local) : List.of(resources);
          case "getService" -> remote;
          case "getWorldview" -> { remoteReads.incrementAndGet(); yield worldview("remote", "remote content"); }
          default -> null;
        });
  }

  @Test void readyLocalPairOverridesRemoteDefaultsWithoutReadingCachedWorldview() {
    var fixture = new Fixture();
    var selected = WorldviewSource.load(fixture.scope);
    assertTrue(selected.local()); assertSame(fixture.local, selected.reasoner());
    assertSame(fixture.world.get(), selected.worldview()); assertEquals(0, fixture.remoteReads.get());
  }

  @Test void switchesWhenLocalReasonerFinishesLoadingWithoutChangingScopeDefaults() {
    var fixture = new Fixture(); fixture.localId.set(null);
    assertFalse(WorldviewSource.load(fixture.scope).local());
    fixture.localId.set("local");
    assertTrue(WorldviewSource.load(fixture.scope).local());
  }

  @Test void detectsResourceEditsAndDeletionWithoutAnIdOrReasonerRevisionChange() {
    var fixture = new Fixture(); var first = WorldviewSource.load(fixture.scope);
    fixture.world.set(worldview("local", "edited content"));
    var edited = WorldviewSource.load(fixture.scope);
    assertEquals(first.revision(), edited.revision()); assertNotEquals(first.content(), edited.content());
    fixture.world.get().getOntologies().clear();
    assertNotEquals(edited.content(), WorldviewSource.load(fixture.scope).content());
  }

  @Test void detectsReasonerOnlyUpdates() {
    var fixture = new Fixture(); var first = WorldviewSource.load(fixture.scope);
    fixture.revision.incrementAndGet(); var updated = WorldviewSource.load(fixture.scope);
    assertEquals(first.content(), updated.content()); assertNotEquals(first.revision(), updated.revision());
  }

  @Test void independentLocalSnapshotIdsDoNotHideTheCertificateWorldview() {
    var fixture = new Fixture(); fixture.localId.set("updating");
    var selected = WorldviewSource.load(fixture.scope);
    assertTrue(selected.local()); assertSame(fixture.world.get(), selected.worldview());
    assertSame(fixture.local, selected.reasoner()); assertEquals(0, fixture.remoteReads.get());
  }

  @Test void independentClientSnapshotIdDoesNotHideTheRemoteCertificateWorldview() {
    var fixture = new Fixture(); fixture.online.set(false);
    var clientWorldview = worldview("client-generated-id", "remote content");
    var scope = (UserScope) Proxy.newProxyInstance(UserScope.class.getClassLoader(), new Class<?>[]{UserScope.class},
        (self, method, args) -> switch (method.getName()) {
          case "getWorldview" -> clientWorldview;
          default -> method.invoke(fixture.scope, args);
        });
    assertSame(clientWorldview, WorldviewSource.load(scope).worldview());
    var refreshed = worldview("another-client-id", "remote content");
    assertEquals(WorldviewSource.fingerprint(clientWorldview), WorldviewSource.fingerprint(refreshed));
  }

  @Test void fallbackRemainsAvailableWhenLocalServicesGoOffline() {
    var fixture = new Fixture(); assertTrue(WorldviewSource.load(fixture.scope).local());
    fixture.online.set(false); assertFalse(WorldviewSource.load(fixture.scope).local());
  }
}
