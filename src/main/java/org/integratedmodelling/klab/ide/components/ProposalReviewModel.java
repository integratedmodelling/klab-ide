package org.integratedmodelling.klab.ide.components;

import java.util.List;
import java.util.Objects;
import org.integratedmodelling.klab.api.services.resources.workflow.ProposalReview;
import org.integratedmodelling.klab.api.services.resources.workflow.BootstrapDossierValidator;
import org.integratedmodelling.klab.api.services.resources.workflow.ProposalReview.*;

/** UI-independent draft and command policy. Uses the shared API records, not a second wire format. */
public final class ProposalReviewModel {
  private final StageData original;
  private final boolean authoring;
  private Candidate candidate;
  private BootstrapDossier dossier;
  private String rationale = "";
  private boolean readOnly;

  public ProposalReviewModel(StageData data, boolean authoring, boolean readOnly) {
    this.original = data;
    this.authoring = authoring;
    this.readOnly = readOnly;
    candidate = data == null ? null : data.candidate();
    dossier = data == null ? null : data.dossier();
  }

  public boolean supported() { return original == null || original.version() == ProposalReview.VERSION; }
  public boolean editable() { return supported() && !readOnly; }
  public boolean authoring() { return authoring; }
  public Candidate candidate() { return candidate; }
  public BootstrapDossier dossier() { return dossier; }
  public StageData original() { return original; }
  public String rationale() { return rationale; }
  public void readOnly(boolean value) { readOnly = value; }
  public void candidate(Candidate value) { requireAuthoring(); candidate = value; }
  public void dossier(BootstrapDossier value) { requireAuthoring(); dossier = value; }
  public void rationale(String value) { requireEditable(); rationale = Objects.requireNonNullElse(value, ""); }
  public boolean dirty() {
    return !rationale.isBlank()
        || !Objects.equals(candidate, original == null ? null : original.candidate())
        || !Objects.equals(dossier, original == null ? null : original.dossier());
  }

  public String problem(Operation operation) {
    if (!supported()) return "Unsupported proposal review version. Update the IDE before submitting.";
    if (readOnly) return "This stage is read-only. Ownership and permissions are enforced by the service.";
    if (operation == null) return "The proposal operation is missing or unsupported.";
    if ((operation == Operation.SUBMIT) != authoring) return "This operation does not match the stage.";
    if (!authoring && (original == null || original.status() != Status.IN_REVIEW))
      return "No candidate is currently in review at this stage.";
    if (candidate == null) return "No candidate is bound to this stage.";
    if (blank(candidate.proposalId()) || blank(candidate.revisionId()) || blank(candidate.contextDigest()) || !bound(candidate.proposal()))
      return "Bind a proposal ID, revision and exact proposal attachment/checksum.";
    if (candidate.actionIds().stream().anyMatch(ProposalReviewModel::blank)
        || candidate.actionIds().stream().distinct().count() != candidate.actionIds().size())
      return "The ordered action IDs must be nonblank and unique.";
    if (authoring && original != null && original.candidate() != null) {
      var previous = original.candidate();
      if (!Objects.equals(previous.proposalId(), candidate.proposalId())
          || Objects.equals(previous.revisionId(), candidate.revisionId())
          || !Objects.equals(previous.revisionId(), candidate.supersedesRevision()))
        return "Resubmission needs the same proposal ID, a new revision, and the previous revision as supersedes.";
    }
    if (operation == Operation.ACCEPT && !bound(candidate.ontology()))
      return "Acceptance requires an exact ontology artifact. Request changes to submit a new candidate.";
    if (operation == Operation.ACCEPT) {
      for (var kind : List.of(CheckKind.IMPORT_CONTEXT, CheckKind.DOCUMENT_SCHEMA, CheckKind.PARSER, CheckKind.ADAPTATION, CheckKind.REASONER)) {
        var matching = checks().stream().filter(check -> check.kind() == kind).toList();
        if (matching.size() != 1 || matching.getFirst().status() != CheckStatus.PASS)
          return "Acceptance blocked: " + kind + " has not passed for this candidate. The service must validate current imports and exact bytes.";
      }
      if (dossier != null && !dossier.unresolvedSemantics().isEmpty()) return "Acceptance blocked by unresolved semantics. Request changes.";
    }
    if (operation == Operation.SUBMIT) {
      var errors = BootstrapDossierValidator.errors(dossier);
      if (!errors.isEmpty()) return "Dossier structure: " + String.join("; ", errors);
    }
    if (rationale.isBlank()) return "Record a scientific rationale or revision note before submitting.";
    return null;
  }

  public Command command(Operation operation) {
    var problem = problem(operation);
    if (problem != null) throw new IllegalStateException(problem);
    return new Command(ProposalReview.VERSION, candidate, rationale.trim(), dossier);
  }

  public String confirmation(Operation operation) {
    var command = command(operation);
    var c = command.candidate();
    return operation + "\nProposal: " + c.proposalId() + "\nRevision: " + c.revisionId()
        + "\nSupersedes: " + Objects.toString(c.supersedesRevision(), "none")
        + "\nProposal artifact: " + c.proposal().attachmentId() + "\nChecksum: " + c.proposal().checksum()
        + "\nOntology: " + Objects.toString(c.ontology(), "not supplied")
        + "\nExact ordered actions: " + c.actionIds() + "\n\nRationale: " + command.rationale()
        + "\nImport context digest: " + c.contextDigest()
        + "\n\nThis records a review decision; it does not apply changes or publish a PR.";
  }

  public List<Check> checks() { return original == null ? List.of() : original.validation(); }
  private void requireEditable() { if (!editable()) throw new IllegalStateException("Stage is read-only or unsupported"); }
  private void requireAuthoring() { requireEditable(); if (!authoring) throw new IllegalStateException("Reviewed evidence is immutable; request a new revision"); }
  private static boolean blank(String text) { return text == null || text.isBlank(); }
  private static boolean bound(Artifact artifact) { return artifact != null && !blank(artifact.attachmentId()) && !blank(artifact.checksum()); }
}
