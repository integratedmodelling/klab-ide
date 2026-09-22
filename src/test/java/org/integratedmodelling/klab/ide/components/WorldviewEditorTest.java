package org.integratedmodelling.klab.ide.components;

import static org.junit.jupiter.api.Assertions.*;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.concurrent.*;
import java.util.function.Supplier;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.TextField;
import javafx.stage.Stage;
import org.integratedmodelling.common.knowledge.ConceptImpl;
import org.integratedmodelling.common.knowledge.ObservableImpl;
import org.integratedmodelling.klab.api.knowledge.SemanticType;
import org.integratedmodelling.klab.api.knowledge.impl.WorldviewImpl;
import org.integratedmodelling.klab.api.services.Reasoner;
import org.integratedmodelling.klab.ide.KlabIDEController;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class WorldviewEditorTest {
  @Test void relationshipMenusUseCompactAtlantaSwitchesAndStayOpen() throws Exception {
    fx(() -> {
      var item = new GraphLayerMenuItem("Subclass");
      var toggle = assertInstanceOf(atlantafx.base.controls.ToggleSwitch.class, item.getContent());
      assertTrue(toggle.getStyleClass().contains(atlantafx.base.theme.Styles.SMALL));
      assertFalse(item.isHideOnClick());
      assertEquals(javafx.geometry.HorizontalDirection.RIGHT, toggle.getLabelPosition());
      toggle.setSelected(true); assertTrue(item.selectedProperty().get());
      return null;
    });
  }

  @Test void treeDoubleClickUsesQualifiedConceptUrnAndRootIsHidden() throws Exception {
    var concept = new ConceptImpl(); concept.setUrn("biology:Tree"); concept.getType().add(SemanticType.SUBJECT);
    var resolved = new java.util.concurrent.CopyOnWriteArrayList<String>();
    var reasoner = (Reasoner) Proxy.newProxyInstance(Reasoner.class.getClassLoader(), new Class<?>[]{Reasoner.class},
        (proxy, method, args) -> switch (method.getName()) {
          case "resolveConcept" -> { resolved.add((String) args[0]); assertEquals("biology:Tree", args[0]); yield concept; }
          case "parents", "children", "influences", "relationshipSources", "relationshipTargets" -> List.of();
          case "describedType" -> null;
          default -> throw new AssertionError(method.getName());
        });
    var world = new WorldviewImpl();
    var ontology = new org.integratedmodelling.klab.api.lang.kim.impl.KimOntologyImpl(); ontology.setUrn("biology");
    var declaration = new org.integratedmodelling.klab.api.lang.kim.impl.KimConceptStatementImpl(); declaration.setUrn("Tree");
    ontology.getStatements().add(declaration); world.getOntologies().add(ontology);
    var previousController = KlabIDEController.instance();
    var editor = fx(() -> {
      new KlabIDEController();
      var result = new WorldviewEditor(world, reasoner) { @Override protected void onVisualize(boolean visible) {} };
      assertFalse(result.createContentTree().isShowRoot());
      return result;
    });
    try {
      await(() -> read(editor, "snapshot") != null);
      fx(() -> { editor.onDoubleClickItemSelection(declaration); return null; });
      await(() -> "biology:Tree".equals(read(editor, "focus"))
          && ((javafx.scene.control.Label) read(editor, "status")).getText().contains("biology:Tree"));
      assertFalse(resolved.isEmpty());
      fx(() -> {
        var snapshot = (WorldviewGraphModel.Snapshot) read(editor, "snapshot");
        assertTrue(snapshot.concepts().containsKey("biology:Tree"));
        return null;
      });
    } finally {
      fx(() -> {
        editor.close();
        try {
          var singleton = KlabIDEController.class.getDeclaredField("_this"); singleton.setAccessible(true);
          singleton.set(null, previousController);
        } catch (ReflectiveOperationException error) { throw new AssertionError(error); }
        return null;
      });
    }
  }

  @BeforeAll static void startFx() throws Exception {
    var ready = new CompletableFuture<Void>();
    try { Platform.startup(() -> { Platform.setImplicitExit(false); ready.complete(null); }); }
    catch (IllegalStateException started) { Platform.runLater(() -> ready.complete(null)); }
    ready.get(15, TimeUnit.SECONDS);
  }

  @Test void urnConfirmationUsesReturnedConceptAndRetainsGraphPanelAndVertices() throws Exception {
    var concept = new ConceptImpl();
    concept.setUrn("earth:Terrestrial earth:Region");
    concept.getType().add(SemanticType.SUBJECT);
    var observable = new ObservableImpl();
    observable.setSemantics(concept); // Exercise a response with no observable URN.
    var reasoner = (Reasoner) Proxy.newProxyInstance(Reasoner.class.getClassLoader(), new Class<?>[]{Reasoner.class},
        (proxy, method, args) -> switch (method.getName()) {
          case "resolveObservable" -> observable;
          case "resolveConcept" -> throw new AssertionError("Must use the concept returned by composition");
          case "parents", "children", "influences", "relationshipSources", "relationshipTargets" -> List.of();
          case "describedType" -> null;
          default -> throw new AssertionError(method.getName());
        });
    var previousController = KlabIDEController.instance();
    var stage = fx(() -> {
      new KlabIDEController();
      var editor = new WorldviewEditor(new WorldviewImpl(), reasoner) {
        @Override protected void onVisualize(boolean visible) {}
      };
      editor.createContentTree();
      var window = new Stage(); window.setScene(new Scene(editor, 900, 600)); window.show(); return window;
    });
    var editor = (WorldviewEditor) stage.getScene().getRoot();
    try {
      await(() -> read(editor, "graph") != null);
      var graph = fx(() -> read(editor, "graph"));
      fx(() -> {
        var input = (TextField) read(editor, "conceptInput");
        input.setText("test:Input"); input.fireEvent(new javafx.event.ActionEvent()); return null;
      });
      await(() -> {
        var snapshot = (WorldviewGraphModel.Snapshot) read(editor, "snapshot");
        return snapshot != null && snapshot.concepts().containsKey(concept.getUrn());
      });
      fx(() -> {
        assertSame(graph, read(editor, "graph"));
        var tree = (javafx.scene.control.TreeView<?>) read(editor, "tree");
        assertSame(concept, tree.getSelectionModel().getSelectedItem().getValue());
        assertEquals(concept.getUrn(), read(editor, "selectedUrn"));
        assertEquals("", ((TextField) read(editor, "conceptInput")).getText());
        return null;
      });
      var panel = (com.brunomnsilva.smartgraph.graphview.SmartGraphPanel<?, ?>) graph;
      fx(() -> {
        assertEquals(2, ((javafx.scene.control.Spinner<?>) read(editor, "depth")).getValue());
        assertFalse(panel.automaticLayoutProperty().get(), "Layout must not keep drifting after placement");
        return null;
      });
      var vertex = fx(() -> panel.getModel().vertices().iterator().next());
      fx(() -> {
        var input = (TextField) read(editor, "conceptInput");
        input.setText(concept.getUrn());
        input.fireEvent(new javafx.event.ActionEvent());
        assertFalse(input.isDisabled());
        return null;
      });
      await(() -> !((javafx.beans.property.BooleanProperty) read(editor, "graphBusy")).get()
          && !((javafx.beans.property.BooleanProperty) read(editor, "inputBusy")).get()
          && concept.getUrn().equals(read(editor, "focus")));
      fx(() -> { assertSame(vertex, panel.getModel().vertices().iterator().next()); return null; });
      await(() -> panel.getSmartVertices().size() == 1);
      fx(() -> {
        try {
          var apply = WorldviewEditor.class.getDeclaredMethod("applyInput",
              org.integratedmodelling.klab.api.knowledge.Observable.class, boolean.class);
          apply.setAccessible(true); apply.invoke(editor, observable, false);
          assertEquals(concept.getUrn(), ((TextField) read(editor, "conceptInput")).getText());
        } catch (ReflectiveOperationException error) { throw new AssertionError(error); }
        return null;
      });
      fx(() -> {
        var typed = (com.brunomnsilva.smartgraph.graphview.SmartGraphPanel<org.integratedmodelling.klab.api.knowledge.Concept, WorldviewGraphModel.Link>) panel;
        var modelVertex = typed.getModel().vertices().iterator().next();
        for (var visual : List.of(typed.getStylableVertex(modelVertex), typed.getStylableLabel(modelVertex))) {
          long before = (long) read(editor, "generation");
          ((javafx.scene.Node) visual).fireEvent(new javafx.scene.input.MouseEvent(
              javafx.scene.input.MouseEvent.MOUSE_CLICKED, 0, 0, 0, 0,
              javafx.scene.input.MouseButton.PRIMARY, 2, false, false, false, false,
              false, false, false, false, false, true, null));
          assertEquals(before + 1, read(editor, "generation"), "Double-click must reload exactly once");
          assertEquals(concept.getUrn(), read(editor, "focus"));
        }
        return null;
      });
    } finally {
      fx(() -> {
        editor.close(); stage.close();
        try {
          var singleton = KlabIDEController.class.getDeclaredField("_this");
          singleton.setAccessible(true); singleton.set(null, previousController);
        } catch (ReflectiveOperationException error) { throw new AssertionError(error); }
        return null;
      });
    }
  }

  @Test void slowInputCanBeEditedAndReplacedWithoutLateReplyTakingOver() throws Exception {
    var release = new CountDownLatch(1);
    var started = new CountDownLatch(1);
    var concept = new ConceptImpl(); concept.setUrn("earth:Terrestrial earth:Region");
    concept.getType().add(SemanticType.SUBJECT);
    var observable = new ObservableImpl(); observable.setSemantics(concept);
    var reasoner = (Reasoner) Proxy.newProxyInstance(Reasoner.class.getClassLoader(), new Class<?>[]{Reasoner.class},
        (proxy, method, args) -> switch (method.getName()) {
          case "resolveObservable" -> {
            if ("slow".equals(args[0])) { started.countDown(); release.await(10, TimeUnit.SECONDS); yield null; }
            yield observable;
          }
          case "parents", "children", "influences", "relationshipSources", "relationshipTargets" -> List.of();
          case "describedType" -> null;
          default -> throw new AssertionError(method.getName());
        });
    var previousController = KlabIDEController.instance();
    var editor = fx(() -> {
      new KlabIDEController();
      return new WorldviewEditor(new WorldviewImpl(), reasoner) {
        @Override protected void onVisualize(boolean visible) {}
      };
    });
    try {
      fx(() -> {
        var input = (TextField) read(editor, "conceptInput");
        input.setText("slow"); input.fireEvent(new javafx.event.ActionEvent());
        assertTrue(((javafx.scene.control.ProgressIndicator) read(editor, "progress")).isVisible());
        return null;
      });
      assertTrue(started.await(5, TimeUnit.SECONDS));
      fx(() -> {
        var input = (TextField) read(editor, "conceptInput");
        assertFalse(input.isDisabled()); assertTrue(input.isEditable());
        input.setText(concept.getUrn()); input.fireEvent(new javafx.event.ActionEvent());
        return null;
      });
      await(() -> concept.getUrn().equals(read(editor, "focus"))
          && !((javafx.beans.property.BooleanProperty) read(editor, "graphBusy")).get());
      release.countDown();
      fx(() -> {
        assertEquals("", ((TextField) read(editor, "conceptInput")).getText());
        assertFalse(((javafx.scene.control.ProgressIndicator) read(editor, "progress")).isVisible());
        return null;
      });
    } finally {
      release.countDown();
      fx(() -> {
        editor.close();
        try {
          var singleton = KlabIDEController.class.getDeclaredField("_this"); singleton.setAccessible(true);
          singleton.set(null, previousController);
        } catch (ReflectiveOperationException error) { throw new AssertionError(error); }
        return null;
      });
    }
  }

  @Test void initialNeighborhoodFollowsFinalViewportSizeWithoutAnotherSelection() throws Exception {
    var root = new ConceptImpl(); root.setUrn("test:Subject"); root.getType().add(SemanticType.SUBJECT);
    var children = java.util.stream.IntStream.range(0, 8).mapToObj(i -> {
      var child = new ConceptImpl(); child.setUrn("test:Child" + i);
      child.getType().add(SemanticType.SUBJECT); return child;
    }).toList();
    var reasoner = (Reasoner) Proxy.newProxyInstance(Reasoner.class.getClassLoader(), new Class<?>[]{Reasoner.class},
        (proxy, method, args) -> switch (method.getName()) {
          case "resolveConcept" -> root;
          case "children" -> args[0] == root ? children : List.of();
          case "parents", "influences", "relationshipSources", "relationshipTargets" -> List.of();
          case "describedType" -> null;
          default -> throw new AssertionError(method.getName());
        });
    var world = new WorldviewImpl();
    var ontology = new org.integratedmodelling.klab.api.lang.kim.impl.KimOntologyImpl(); ontology.setUrn("test");
    var declaration = new org.integratedmodelling.klab.api.lang.kim.impl.KimConceptStatementImpl();
    declaration.setUrn("Subject"); declaration.setUpperConceptDefined("odo:Subject");
    ontology.getStatements().add(declaration); world.getOntologies().add(ontology);
    var previousController = KlabIDEController.instance();
    var stage = fx(() -> {
      new KlabIDEController();
      var editor = new WorldviewEditor(world, reasoner) {
        @Override protected void onVisualize(boolean visible) {}
      };
      var window = new Stage(); window.setScene(new Scene(editor, 500, 350)); window.show(); return window;
    });
    var editor = (WorldviewEditor) stage.getScene().getRoot();
    try {
      await(() -> read(editor, "graph") != null && !(boolean) read(editor, "placeNeighborhood"));
      var panel = fx(() -> (com.brunomnsilva.smartgraph.graphview.SmartGraphPanel<org.integratedmodelling.klab.api.knowledge.Concept, WorldviewGraphModel.Link>) read(editor, "graph"));
      double originalWidth = fx(panel::getWidth);
      fx(() -> { stage.setWidth(1100); stage.setHeight(750); return null; });
      await(() -> panel.getWidth() > originalWidth + 100 && !(boolean) read(editor, "placeNeighborhood"));
      await(() -> {
        var focal = panel.getModel().vertices().stream().filter(v -> v.element() == root).findFirst().orElseThrow();
        return Math.abs(panel.getVertexPositionX(focal) - panel.getWidth() / 2) < 1
            && Math.abs(panel.getVertexPositionY(focal) - panel.getHeight() / 2) < 1;
      });
      fx(() -> {
        var xs = panel.getModel().vertices().stream().mapToDouble(panel::getVertexPositionX).summaryStatistics();
        assertEquals(9, xs.getCount());
        assertTrue(xs.getMax() - xs.getMin() > 150, "Initial neighborhood must be spread across the viewport");
        return null;
      });
    } finally {
      fx(() -> {
        editor.close(); stage.close();
        try {
          var singleton = KlabIDEController.class.getDeclaredField("_this"); singleton.setAccessible(true);
          singleton.set(null, previousController);
        } catch (ReflectiveOperationException error) { throw new AssertionError(error); }
        return null;
      });
    }
  }

  private static Object read(Object object, String name) {
    try {
      var field = WorldviewEditor.class.getDeclaredField(name); field.setAccessible(true); return field.get(object);
    } catch (ReflectiveOperationException error) { throw new AssertionError(error); }
  }
  private static void await(Supplier<Boolean> predicate) throws Exception {
    long end = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
    while (System.nanoTime() < end) { if (fx(predicate)) return; Thread.sleep(20); }
    fail("Timed out waiting for worldview update");
  }
  private static <T> T fx(Supplier<T> task) throws Exception {
    var result = new CompletableFuture<T>();
    Platform.runLater(() -> { try { result.complete(task.get()); } catch (Throwable error) { result.completeExceptionally(error); } });
    return result.get(15, TimeUnit.SECONDS);
  }
}
