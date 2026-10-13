package org.integratedmodelling.klab.ide.components;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.List;
import javafx.scene.control.TreeItem;
import org.integratedmodelling.klab.api.knowledge.KlabAsset.KnowledgeClass;
import org.integratedmodelling.klab.api.knowledge.organization.impl.ProjectImpl;
import org.integratedmodelling.klab.api.lang.kim.impl.KimNamespaceImpl;
import org.integratedmodelling.klab.api.lang.kim.impl.KimOntologyImpl;
import org.integratedmodelling.klab.api.view.modeler.navigation.NavigableAsset;
import org.integratedmodelling.klab.modeler.model.NavigableKimNamespace;
import org.integratedmodelling.klab.modeler.model.NavigableKimOntology;
import org.integratedmodelling.klab.modeler.model.NavigableProject;
import org.junit.jupiter.api.Test;

class WorkspaceEditorTest {

  @Test
  void savingDocumentsNamedAfterTheirProjectPreservesTheProjectAndOtherDocuments() {
    var root = collidingProjectTree();
    var project = (NavigableProject) root.getValue();
    var originalChildren = List.copyOf(root.getChildren());
    root.setExpanded(true);

    // Both document types share the project's URN. Repeated saves must stay on their own nodes.
    for (int save = 0; save < 2; save++) {
      for (var type : List.of(KnowledgeClass.NAMESPACE, KnowledgeClass.ONTOLOGY)) {
        var node = WorkspaceEditor.findTreeNode(root, "imod", type);
        var updated = type == KnowledgeClass.NAMESPACE
            ? new NavigableKimNamespace(namespace("imod"), project)
            : new NavigableKimOntology(ontology("imod"), project);
        WorkspaceEditor.updateTree(node, updated);

        assertSame(updated, node.getValue());
        assertSame(project, root.getValue());
        assertEquals(originalChildren, root.getChildren());
        assertSame(root, node.getParent());
        assertSame(root, WorkspaceEditor.findTreeNode(root, "imod", KnowledgeClass.PROJECT));
        assertTrue(root.isExpanded());
      }
    }
  }

  @Test
  void deletingNamespaceNamedAfterProjectLeavesOntologyAndOtherNamespaces() {
    var root = collidingProjectTree();
    var ontology = WorkspaceEditor.findTreeNode(root, "imod", KnowledgeClass.ONTOLOGY);
    var sibling = WorkspaceEditor.findTreeNode(root, "imod.other", KnowledgeClass.NAMESPACE);
    var deleted = WorkspaceEditor.findTreeNode(root, "imod", KnowledgeClass.NAMESPACE);

    deleted.getParent().getChildren().remove(deleted);

    assertEquals(List.of(ontology, sibling), root.getChildren());
    assertSame(root, WorkspaceEditor.findTreeNode(root, "imod", KnowledgeClass.PROJECT));
    assertNull(WorkspaceEditor.findTreeNode(root, "imod", KnowledgeClass.NAMESPACE));
  }

  @Test
  void treeLookupRequiresMatchingAssetType() {
    var root = collidingProjectTree();
    assertNull(WorkspaceEditor.findTreeNode(root, "imod", KnowledgeClass.WORKSPACE));
    assertNull(WorkspaceEditor.findTreeNode(root, "imod", null));
    assertNull(WorkspaceEditor.findTreeNode(root, "missing", KnowledgeClass.NAMESPACE));
  }

  private static TreeItem<NavigableAsset> collidingProjectTree() {
    var project = new ProjectImpl();
    project.setUrn("imod");
    project.setNamespaces(List.of(namespace("imod"), namespace("imod.other")));
    project.setOntologies(List.of(ontology("imod")));
    var navigable = new NavigableProject(project, null);
    var root = new TreeItem<NavigableAsset>(navigable);
    for (var child : navigable.children()) root.getChildren().add(new TreeItem<>(child));
    return root;
  }

  private static KimNamespaceImpl namespace(String urn) {
    var namespace = new KimNamespaceImpl();
    namespace.setUrn(urn);
    namespace.setProjectName("imod");
    return namespace;
  }

  private static KimOntologyImpl ontology(String urn) {
    var ontology = new KimOntologyImpl();
    ontology.setUrn(urn);
    ontology.setProjectName("imod");
    return ontology;
  }

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

