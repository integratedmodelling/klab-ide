package org.integratedmodelling.klab.ide.components;

import static org.junit.jupiter.api.Assertions.*;
import org.integratedmodelling.klab.api.knowledge.impl.WorldviewImpl;
import org.integratedmodelling.klab.api.lang.kim.impl.*;
import org.junit.jupiter.api.Test;

class WorldviewConceptsTest {
  @Test void startsAtSubjectCoreBindingInsteadOfEnumeratingWorldview() {
    var world = new WorldviewImpl();
    assertNull(WorldviewConcepts.initialFocus(world));
    var ontology = new KimOntologyImpl(); ontology.setUrn("test");
    var other = new KimConceptStatementImpl(); other.setUrn("Aardvark");
    var subject = new KimConceptStatementImpl(); subject.setUrn("Subject");
    subject.setUpperConceptDefined("odo:Subject");
    ontology.getStatements().add(other); ontology.getStatements().add(subject);
    world.getOntologies().add(ontology);
    assertEquals("test:Subject", WorldviewConcepts.initialFocus(world));
  }

  @Test void qualifiesLocalDeclarationsAndChildrenWithoutDoubleQualifyingSemanticUrns() {
    var world = new WorldviewImpl();
    var ontology = new KimOntologyImpl(); ontology.setUrn("biology");
    var tree = new KimConceptStatementImpl(); tree.setUrn("Tree"); tree.setNamespace("biology");
    var oak = new KimConceptStatementImpl(); oak.setUrn("Oak"); tree.getChildren().add(oak);
    var qualified = new KimConceptStatementImpl(); qualified.setUrn("biology:Plant");
    ontology.getStatements().add(tree); ontology.getStatements().add(qualified); world.getOntologies().add(ontology);
    var index = WorldviewConcepts.index(world);
    assertEquals("biology:Tree", index.get(tree));
    assertEquals("biology:Oak", index.get(oak));
    assertEquals("biology:Plant", index.get(qualified));
  }

  @Test void identicalLocalNamesInDifferentOntologiesRemainDistinct() {
    var world = new WorldviewImpl();
    for (var namespace : java.util.List.of("first", "second")) {
      var ontology = new KimOntologyImpl(); ontology.setUrn(namespace);
      var statement = new KimConceptStatementImpl(); statement.setUrn("Thing");
      ontology.getStatements().add(statement); world.getOntologies().add(ontology);
    }
    assertEquals(java.util.Set.of("first:Thing", "second:Thing"),
        new java.util.HashSet<>(WorldviewConcepts.index(world).values()));
  }
}
