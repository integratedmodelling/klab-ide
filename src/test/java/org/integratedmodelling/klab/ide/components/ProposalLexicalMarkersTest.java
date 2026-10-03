package org.integratedmodelling.klab.ide.components;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.integratedmodelling.klab.api.services.resources.workflow.ProposalReview.*;
import org.junit.jupiter.api.Test;

class ProposalLexicalMarkersTest {
  private static final String SOURCE = "namespace hydrology\n\nsubject SurfaceCatchment;\n";
  private static StageData stage(Artifact ontology) {
    var candidate = new Candidate("proposal", "r1", null, new Artifact("proposal-bytes", "sha"), ontology, List.of(), "imports");
    return new StageData(1, candidate, Status.IN_REVIEW, "reviewer", "", List.of(), ProposalStageEditorTest.hydrology());
  }
  @Test void exactLexicalBindingProducesRevisionScopedClickableGlyph() {
    var markers = ProposalLexicalMarkers.markers("peer-stage", stage(null), SOURCE, SOURCE,
        Map.of("hydrology:SurfaceCatchment", SOURCE.indexOf("subject")));
    assertEquals(1, markers.size());
    assertEquals(3, markers.getFirst().lineNumber());
    assertEquals("peer-stage:proposal:hydrology-SurfaceCatchment", markers.getFirst().id());
    assertEquals("hydrology-SurfaceCatchment", markers.getFirst().action());
    assertTrue(markers.getFirst().tooltip().contains("not scientific approval"));
    assertTrue(markers.getFirst().tooltip().contains("revision r1"));
  }
  @Test void staleTextWrongCandidateBytesAndUnboundExpressionsNeverGetGuessedMarkers() {
    var offsets = Map.of("hydrology:SurfaceCatchment", 21);
    assertTrue(ProposalLexicalMarkers.markers("s", stage(null), SOURCE, SOURCE + "edit", offsets).isEmpty());
    assertTrue(ProposalLexicalMarkers.markers("s", stage(new Artifact("ontology", "wrong")), SOURCE, SOURCE, offsets).isEmpty());
    assertTrue(ProposalLexicalMarkers.markers("s", stage(null), SOURCE, SOURCE, Map.of("SurfaceCatchment", 21)).isEmpty());
    assertTrue(ProposalLexicalMarkers.markers("s", stage(null), SOURCE, SOURCE, Map.of("hydrology:SurfaceCatchment", -1)).isEmpty());
    assertTrue(ProposalLexicalMarkers.markers("s", null, SOURCE, SOURCE, offsets).isEmpty());
    assertEquals(1, ProposalLexicalMarkers.markers("s", stage(new Artifact("ontology", ProposalCandidateReader.digest(SOURCE.getBytes(StandardCharsets.UTF_8)))), SOURCE, SOURCE, offsets).size());
  }
}
