package org.integratedmodelling.klab.ide.components;

import java.nio.charset.StandardCharsets;
import java.util.*;
import org.integratedmodelling.klab.api.lang.kim.KimConceptStatement;
import org.integratedmodelling.klab.api.lang.kim.KlabDocument;
import org.integratedmodelling.klab.api.services.resources.workflow.ProposalReview;
import org.integratedmodelling.klabeditor.MonacoEditorView.ReviewMarker;

/** Exact parsed declaration bindings for the paired document; never guesses locations by text. */
public final class ProposalLexicalMarkers {
  private ProposalLexicalMarkers() {}
  public static Map<String, Integer> declarationOffsets(KlabDocument<?> document) {
    var result = new LinkedHashMap<String, Integer>();
    for (var statement : document.getStatements())
      if (statement instanceof KimConceptStatement concept) collect(concept, result);
    return result;
  }
  private static void collect(KimConceptStatement concept, Map<String, Integer> offsets) {
    // Duplicate identities are ambiguous even if the parser retained both declarations.
    if (concept.getUrn() != null && !concept.getUrn().isBlank())
      offsets.merge(concept.getUrn(), concept.getOffsetInDocument(), (a,b) -> -1);
    for (var child : concept.getChildren()) collect(child, offsets);
  }
  public static List<ReviewMarker> markers(String stageId, ProposalReview.StageData review,
      String parsedSource, String editorSource, Map<String, Integer> offsets) {
    if (review == null || review.version() != ProposalReview.VERSION || review.candidate() == null
        || review.dossier() == null || parsedSource == null || !parsedSource.equals(editorSource)) return List.of();
    var ontology = review.candidate().ontology();
    if (ontology != null && !Objects.equals(ontology.checksum(), ProposalCandidateReader.digest(parsedSource.getBytes(StandardCharsets.UTF_8))))
      return List.of();
    var result = new ArrayList<ReviewMarker>();
    for (var concept : review.dossier().concepts()) {
      if (concept.expression() == null || concept.expression().isBlank()) continue;
      var offset = offsets.get(concept.expression());
      if (offset == null || offset < 0 || offset >= parsedSource.length()) continue;
      int line = 1;
      for (int i = 0; i < offset; i++) if (parsedSource.charAt(i) == '\n') line++;
      result.add(new ReviewMarker(stageId + ":proposal:" + concept.id(), line, "?", "#3979cf", 16,
          "Proposal " + concept.id() + " — revision " + review.candidate().revisionId()
              + "\n" + review.status() + "; lexical reference, not scientific approval"
              + (ontology == null ? "\nNo reviewed ontology artifact is bound" : ""),
          concept.id(), review.actor()));
    }
    return List.copyOf(result);
  }
}
