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

  @Test
  void repeatedValidationUpdatesPreserveTheEditorAndNewerUnsavedEdits() {
    assertTrue(WorkspaceEditor.preservesEditorSource(false, "saved", "saved", "saved"));
    assertTrue(WorkspaceEditor.preservesEditorSource(false, "saved", "new unsaved edits", "saved"));
    assertTrue(WorkspaceEditor.preservesEditorSource(true, "previous", "new unsaved edits", "saved"));
    assertTrue(WorkspaceEditor.preservesEditorSource(false, "previous", "saved", "saved"));
    assertFalse(WorkspaceEditor.preservesEditorSource(false, "saved", "saved", "external change"));
    assertFalse(WorkspaceEditor.preservesEditorSource(false, "saved", "new edits", "external change"));
  }

  @Test
  void saveAcknowledgementsTolerateNormalizedLineEndings() {
    Map<String, Deque<String>> pending = new HashMap<>();
    pending.put("demo", new ArrayDeque<>(java.util.List.of("first\r\nsecond\r\n")));
    assertTrue(WorkspaceEditor.consumePendingSave(pending, "demo", "first\nsecond\n"));
    assertFalse(pending.containsKey("demo"));
    assertTrue(WorkspaceEditor.preservesEditorSource(false, "first\r\nsecond", "new edits", "first\nsecond"));
  }

  @Test
  void unchangedChildrenEmitNoTreeEventsAndKeepTheirParentsAndExpansion() {
    var parent = new javafx.scene.control.TreeItem<String>("document");
    var first = new javafx.scene.control.TreeItem<String>("first");
    var second = new javafx.scene.control.TreeItem<String>("second");
    parent.getChildren().addAll(java.util.List.of(first, second));
    first.setExpanded(true);
    var events = new java.util.concurrent.atomic.AtomicInteger();
    parent.addEventHandler(javafx.scene.control.TreeItem.treeNotificationEvent(), event -> events.incrementAndGet());

    WorkspaceEditor.reconcileChildren(parent, java.util.List.of(first, second));

    org.junit.jupiter.api.Assertions.assertEquals(0, events.get());
    org.junit.jupiter.api.Assertions.assertSame(parent, first.getParent());
    assertTrue(first.isExpanded());
  }

  @Test
  void addingAndRemovingDeclarationsDoesNotDetachSurvivingSiblings() {
    var parent = new javafx.scene.control.TreeItem<String>("document");
    var first = new javafx.scene.control.TreeItem<String>("first");
    var removed = new javafx.scene.control.TreeItem<String>("removed");
    var last = new javafx.scene.control.TreeItem<String>("last");
    var added = new javafx.scene.control.TreeItem<String>("added");
    parent.getChildren().addAll(java.util.List.of(first, removed, last));
    var detached = new java.util.concurrent.atomic.AtomicInteger();
    first.parentProperty().addListener((observable, oldParent, newParent) -> detached.incrementAndGet());
    last.parentProperty().addListener((observable, oldParent, newParent) -> detached.incrementAndGet());

    WorkspaceEditor.reconcileChildren(parent, java.util.List.of(first, added, last));

    org.junit.jupiter.api.Assertions.assertEquals(java.util.List.of(first, added, last), parent.getChildren());
    org.junit.jupiter.api.Assertions.assertEquals(0, detached.get());
    org.junit.jupiter.api.Assertions.assertNull(removed.getParent());
    WorkspaceEditor.reconcileChildren(parent, java.util.List.of(last, first, added));
    org.junit.jupiter.api.Assertions.assertEquals(java.util.List.of(last, first, added), parent.getChildren());
    org.junit.jupiter.api.Assertions.assertSame(parent, last.getParent());
  }
}

