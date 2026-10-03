package org.integratedmodelling.klab.ide.components;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

class ProposalCandidateReaderTest {
  static final String YAML = """
      proposal_schema: classpath:/schemas/llm/domain-context-proposal.schema.json
      context_pack_version: '1.3'
      proposal:
        id: proposal
        revision_id: r1
        supersedes_revision: null
        existing_ontologies: []
        actions:
          - action_id: second
          - action_id: first
      """;
  @Test void bindsExactBytesOrderedActionsAndImportContext() {
    var bytes = YAML.getBytes(StandardCharsets.UTF_8);
    var result = ProposalCandidateReader.read(bytes, "uploaded", null);
    assertEquals(List.of("second", "first"), result.actionIds());
    assertEquals(ProposalCandidateReader.digest(bytes), result.proposal().checksum());
    assertEquals(ProposalCandidateReader.digest("[]".getBytes(StandardCharsets.UTF_8)), result.contextDigest());
    assertNull(result.supersedesRevision());
  }
  @Test void malformedMissingUnknownAndDuplicateInputsDoNotProduceCandidates() {
    for (var text : List.of("not a proposal", YAML.replace("'1.3'", "'99'"),
        YAML.replace("action_id: first", "action_id: second"), YAML.replace("id: proposal", "id: proposal\n  id: duplicate"),
        YAML + "\n---\nhidden: second document\n"))
      assertThrows(IllegalArgumentException.class, () -> ProposalCandidateReader.read(text.getBytes(StandardCharsets.UTF_8), "a", null));
  }
}
