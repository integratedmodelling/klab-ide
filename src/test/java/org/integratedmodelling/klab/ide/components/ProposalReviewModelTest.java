package org.integratedmodelling.klab.ide.components;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import org.integratedmodelling.klab.api.services.resources.workflow.ProposalReview.*;
import org.junit.jupiter.api.Test;

class ProposalReviewModelTest {
  static Candidate candidate(String revision, String previous) {
    return new Candidate("proposal", revision, previous, new Artifact("attachment", "checksum"),
        new Artifact("ontology", "ontology-checksum"), List.of("action-2", "action-1"), "imports");
  }
  static StageData stage(int version) {
    return new StageData(version, candidate("r1", null), Status.IN_REVIEW, "author", "submitted",
        List.of(new Check(CheckKind.PARSER, CheckStatus.PASS, List.of("Syntax only"))), null);
  }
  @Test void parserPassDoesNotMakeAHumanDecision() {
    var model = new ProposalReviewModel(stage(1), false, false);
    assertNotNull(model.problem(Operation.ACCEPT));
    assertThrows(IllegalStateException.class, () -> model.command(Operation.ACCEPT));
    assertEquals(Status.IN_REVIEW, model.original().status());
    model.rationale("Scientific evidence reviewed");
    assertNotNull(model.problem(Operation.ACCEPT)); // Parser success alone cannot enable acceptance.
    var passing = new StageData(1, stage(1).candidate(), Status.IN_REVIEW, "author", "submitted",
        List.of(CheckKind.IMPORT_CONTEXT, CheckKind.DOCUMENT_SCHEMA, CheckKind.PARSER, CheckKind.REASONER).stream()
            .map(kind -> new Check(kind, CheckStatus.PASS, List.of("Synthetic test gate only"))).toList(), null);
    var accepted = new ProposalReviewModel(passing, false, false);
    assertNotNull(accepted.problem(Operation.ACCEPT)); // Even all automatic checks require a human rationale.
    accepted.rationale("Scientific evidence reviewed");
    assertEquals(stage(1).candidate(), accepted.command(Operation.ACCEPT).candidate());
    assertEquals(List.of("action-2", "action-1"), accepted.command(Operation.ACCEPT).candidate().actionIds());
  }
  @Test void missingUnknownAndReadOnlyFailClosed() {
    var missing = new ProposalReviewModel(null, false, false);
    assertNotNull(missing.problem(Operation.ACCEPT));
    var unknown = new ProposalReviewModel(stage(2), false, false);
    assertFalse(unknown.editable());
    assertThrows(IllegalStateException.class, () -> unknown.rationale("yes"));
    var readOnly = new ProposalReviewModel(stage(1), false, true);
    assertThrows(IllegalStateException.class, () -> readOnly.command(Operation.REQUEST_CHANGES));
  }
  @Test void reviewedCandidateAndDossierCannotBeReplaced() {
    var model = new ProposalReviewModel(stage(1), false, false);
    assertThrows(IllegalStateException.class, () -> model.candidate(candidate("r2", "r1")));
    assertThrows(IllegalStateException.class, () -> model.dossier(null));
  }
  @Test void resubmissionRequiresFreshCorrectlyLinkedRevision() {
    var model = new ProposalReviewModel(stage(1), true, false);
    model.rationale("Addressed requested changes");
    assertNotNull(model.problem(Operation.SUBMIT));
    model.candidate(candidate("r2", "wrong"));
    assertNotNull(model.problem(Operation.SUBMIT));
    model.candidate(candidate("r2", "r1"));
    assertNull(model.problem(Operation.SUBMIT));
    assertNotNull(model.problem(Operation.ACCEPT));
  }
  @Test void preparingOrRepeatingCommandDoesNotMutateOriginalOrClearDraft() {
    var model = new ProposalReviewModel(stage(1), false, false);
    model.rationale("Clarify imported parent");
    var first = model.command(Operation.REQUEST_CHANGES);
    assertEquals(first, model.command(Operation.REQUEST_CHANGES));
    assertTrue(model.dirty());
    assertEquals(Status.IN_REVIEW, model.original().status());
    assertTrue(model.confirmation(Operation.REQUEST_CHANGES).contains("[action-2, action-1]"));
    assertTrue(model.confirmation(Operation.REQUEST_CHANGES).contains("imports"));
    // A server conflict leaves the model intact; the caller must reopen and reconcile.
    assertEquals("r1", model.candidate().revisionId());
  }
  @Test void unknownOperationsAndMissingOntologyCannotAccept() {
    var model = new ProposalReviewModel(null, true, false);
    assertNotNull(model.problem(null));
    var c = candidate("r1", null);
    var data = new StageData(1, new Candidate(c.proposalId(), c.revisionId(), null, c.proposal(), null, c.actionIds(), c.contextDigest()), Status.IN_REVIEW, "a", "", List.of(), null);
    var review = new ProposalReviewModel(data, false, false);
    review.rationale("Review completed");
    assertNotNull(review.problem(Operation.ACCEPT));
    assertNull(review.problem(Operation.REQUEST_CHANGES));
  }
  @Test void sharedDossierValidatorRejectsDanglingQuestionMappingsWithoutPaddingCounts() {
    var model = new ProposalReviewModel(null, true, false);
    model.candidate(candidate("r1", null)); model.rationale("Initial research submission");
    model.dossier(new BootstrapDossier(List.of(), List.of(),
        List.of(new Question("q", "Where does water drain?", "Surface outlet", List.of(), List.of("missing"), List.of(), List.of(), List.of())),
        List.of(), List.of(), List.of("One question only")));
    assertTrue(model.problem(Operation.SUBMIT).contains("Unknown question concept reference"));
    model.dossier(new BootstrapDossier(List.of(), List.of(), List.of(), List.of(), List.of(), List.of("Explicitly incomplete")));
    assertNull(model.problem(Operation.SUBMIT));
  }
}
