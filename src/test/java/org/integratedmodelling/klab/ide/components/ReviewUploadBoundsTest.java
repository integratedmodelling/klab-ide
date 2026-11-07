package org.integratedmodelling.klab.ide.components;

import static org.junit.jupiter.api.Assertions.*;
import org.integratedmodelling.common.utils.Utils;
import org.junit.jupiter.api.Test;

class ReviewUploadBoundsTest {
  private static final long MIB=1024L*1024;
  @Test void enforcesProposalSourceAndReferenceBounds() {
    assertDoesNotThrow(() -> WorkflowEditor.validateReviewUpload("application/vnd.klab.proposal+yaml",4*MIB,0,0));
    assertThrows(IllegalArgumentException.class,() -> WorkflowEditor.validateReviewUpload("application/vnd.klab.proposal+yaml",4*MIB+1,0,0));
    assertThrows(IllegalArgumentException.class,() -> WorkflowEditor.validateReviewUpload("application/vnd.klab.ontology",2*MIB+1,0,0));
    assertThrows(IllegalArgumentException.class,() -> WorkflowEditor.validateReviewUpload("text/plain",16*MIB+1,0,0));
  }
  @Test void checksAggregateBytesAndCountBeforeStaging() {
    assertDoesNotThrow(() -> WorkflowEditor.validateReviewUpload("text/plain",MIB,31*MIB,255));
    assertThrows(IllegalArgumentException.class,() -> WorkflowEditor.validateReviewUpload("text/plain",MIB,32*MIB,255));
    assertThrows(IllegalArgumentException.class,() -> WorkflowEditor.validateReviewUpload("text/plain",1,0,256));
  }
  @Test void neverTreatsUnknownOrServerFailureAsSafeToRetry() {
    assertTrue(WorkflowEditor.definiteCreationRejection(new Utils.Http.Client.RequestFailure(400,"rejected",null)));
    assertTrue(WorkflowEditor.definiteCreationRejection(new Utils.Http.Client.RequestFailure(422,"invalid",null)));
    assertFalse(WorkflowEditor.definiteCreationRejection(new Utils.Http.Client.RequestFailure(0,"network",null)));
    assertFalse(WorkflowEditor.definiteCreationRejection(new Utils.Http.Client.RequestFailure(500,"server",null)));
    assertFalse(WorkflowEditor.definiteCreationRejection(new IllegalStateException("HTTP 400")));
  }
}
