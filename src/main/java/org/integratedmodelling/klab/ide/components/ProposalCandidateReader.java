package org.integratedmodelling.klab.ide.components;

import org.integratedmodelling.common.review.ProposalCandidateBinding;
import org.integratedmodelling.klab.api.services.resources.workflow.ProposalReview.*;

/** Client upload adapter; canonical identity parsing is shared with the service. */
public final class ProposalCandidateReader {
  private ProposalCandidateReader() {}
  public static Candidate read(byte[] bytes, String attachmentId, Artifact ontology) {
    try {
      return ProposalCandidateBinding.inspect(new Artifact(attachmentId, digest(bytes)), ontology, bytes);
    } catch (RuntimeException e) {
      throw new IllegalArgumentException("Cannot bind proposal bytes: " + e.getMessage(), e);
    }
  }
  public static String digest(byte[] bytes) { return ProposalCandidateBinding.digest(bytes); }
}
