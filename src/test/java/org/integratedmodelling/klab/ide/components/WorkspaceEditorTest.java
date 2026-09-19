package org.integratedmodelling.klab.ide.components;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class WorkspaceEditorTest {

  @Test
  void validationOnlyTargetsDocumentAndAncestorsWithoutMutatingTree() {
    var namespace = new org.integratedmodelling.klab.api.lang.kim.impl.KimNamespaceImpl();
    namespace.setUrn("demo");
    namespace.setProjectName("project");
    var document = new org.integratedmodelling.klab.modeler.model.NavigableKimNamespace(namespace, null);
    var root = new javafx.scene.control.TreeItem<org.integratedmodelling.klab.api.view.modeler.navigation.NavigableAsset>();
    var item = new javafx.scene.control.TreeItem<org.integratedmodelling.klab.api.view.modeler.navigation.NavigableAsset>(document);
    var declaration = new javafx.scene.control.TreeItem<org.integratedmodelling.klab.api.view.modeler.navigation.NavigableAsset>();
    var sibling = new javafx.scene.control.TreeItem<org.integratedmodelling.klab.api.view.modeler.navigation.NavigableAsset>();
    root.getChildren().addAll(java.util.List.of(item, sibling));
    item.getChildren().add(declaration);
    item.setExpanded(true);
    var events = new java.util.concurrent.atomic.AtomicInteger();
    root.addEventHandler(javafx.scene.control.TreeItem.treeNotificationEvent(), event -> events.incrementAndGet());
    var affected = new java.util.HashSet<javafx.scene.control.TreeItem<org.integratedmodelling.klab.api.view.modeler.navigation.NavigableAsset>>();

    assertTrue(WorkspaceEditor.collectSemanticPath(root, WorkspaceSemanticValidation.key(document), affected));
    org.junit.jupiter.api.Assertions.assertEquals(java.util.Set.of(root, item), affected);
    org.junit.jupiter.api.Assertions.assertEquals(0, events.get());
    assertTrue(item.isExpanded());
  }

  @Test
  void matchesEachParsedResultToTheSourceThatWasActuallySaved() {
    Map<String, Deque<String>> pending = new HashMap<>();
    pending.put("demo", new ArrayDeque<>());
    pending.get("demo").add("first save");
    pending.get("demo").add("second save");

    assertTrue(WorkspaceEditor.consumePendingSave(pending, "demo", "first save"));
    assertTrue(pending.containsKey("demo"));
    assertFalse(WorkspaceEditor.consumePendingSave(pending, "demo", "external change"));
    assertTrue(WorkspaceEditor.consumePendingSave(pending, "demo", "second save"));
    assertFalse(pending.containsKey("demo"));
  }
}

