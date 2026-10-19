package org.integratedmodelling.klab.ide.components;
import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import javafx.scene.control.TreeItem;
import org.integratedmodelling.klab.api.authentication.CRUDOperation;
import org.integratedmodelling.klab.api.knowledge.KlabAsset.KnowledgeClass;
import org.integratedmodelling.klab.api.knowledge.organization.impl.*;
import org.integratedmodelling.klab.api.lang.kim.impl.KimOntologyImpl;
import org.integratedmodelling.klab.api.services.resources.ResourceSet;
import org.integratedmodelling.klab.api.view.modeler.navigation.NavigableAsset;
import org.integratedmodelling.klab.modeler.model.*;
import org.junit.jupiter.api.Test;
class WorkspaceOntologyProjectScopeTest {
 private static ProjectImpl project(String name){var p=new ProjectImpl();p.setUrn(name);var d=new KimOntologyImpl();d.setUrn("imod");d.setProjectName(name);d.setSourceCode(name);p.setOntologies(List.of(d));return p;}
 private static TreeItem<NavigableAsset> tree(NavigableAsset asset){var result=new TreeItem<NavigableAsset>(asset);for(var child:asset.children())result.getChildren().add(tree(child));return result;}
 @Test void changeLookupAndTreeSelectionStayInOwningProjectWithCollidingUrns(){
  var w=new WorkspaceImpl();w.setUrn("workspace");w.setProjects(List.of(project("canonical"),project("demo")));var workspace=new NavigableWorkspace(w);var root=tree(workspace);
  var change=new ResourceSet.Resource();change.setProjectUrn("demo");change.setResourceUrn("imod");change.setKnowledgeClass(KnowledgeClass.ONTOLOGY);change.setOperation(CRUDOperation.UPDATE);
  assertEquals("demo",((org.integratedmodelling.klab.api.lang.kim.KimOntology)WorkspaceEditor.findChangedAsset(workspace,change)).getProjectName());
  assertEquals("demo",((org.integratedmodelling.klab.api.lang.kim.KimOntology)WorkspaceEditor.findChangedTreeNode(root,change).getValue()).getProjectName());
  change.setProjectUrn("missing");assertNull(WorkspaceEditor.findChangedAsset(workspace,change));assertNull(WorkspaceEditor.findChangedTreeNode(root,change));
 }
}
