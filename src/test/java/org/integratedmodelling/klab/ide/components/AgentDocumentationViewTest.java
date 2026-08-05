package org.integratedmodelling.klab.ide.components;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class AgentDocumentationViewTest {

  @Test
  void mockDocumentsAgentsAndTheirVerbsUsingTheServiceBeanShape() {
    var documentation = AgentDocumentationModel.mockDocumentation();

    assertEquals(AgentDocumentationModel.AGENT_DOCUMENTATION, documentation.type());
    assertEquals(2, documentation.getChildren().size());
    assertTrue(
        documentation.getChildren().stream()
            .allMatch(agent -> AgentDocumentationModel.AGENT.equals(agent.type())));
    assertTrue(
        documentation.getChildren().stream()
            .flatMap(agent -> agent.getChildren().stream())
            .allMatch(
                verb ->
                    AgentDocumentationModel.VERB.equals(verb.type())
                        && verb.get(AgentDocumentationModel.MARKDOWN) != null
                        && verb.get(AgentDocumentationModel.SYNTAX) != null));
  }

  @Test
  void searchMatchesAgentAndVerbMetadataCaseInsensitively() {
    var documentation = AgentDocumentationModel.mockDocumentation();
    var runtime = documentation.getChildren().getFirst();
    var observe = runtime.getChildren().getFirst();

    assertTrue(AgentDocumentationModel.matches("RUNTIME", runtime));
    assertTrue(AgentDocumentationModel.matches("contextualize", observe));
    assertTrue(AgentDocumentationModel.matches("within geometry", observe));
    assertFalse(AgentDocumentationModel.matches("unrelated", observe));
  }
}
