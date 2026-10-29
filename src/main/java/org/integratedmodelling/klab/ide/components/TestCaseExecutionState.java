package org.integratedmodelling.klab.ide.components;

import java.util.HashMap;
import java.util.Map;
import org.integratedmodelling.klab.api.actors.RuntimeAgent;
import org.integratedmodelling.klab.api.collections.DomainObject;

/** Execution ownership and persistent action decorations, independent of results tabs. */
final class TestCaseExecutionState {
  enum Status { RUNNING, PASSED, FAILED }

  private String agentUrn;
  private final Map<String, Status> actions = new HashMap<>();

  boolean isRunning() { return agentUrn != null; }
  boolean owns(String urn) { return urn != null && urn.equals(agentUrn); }
  Status status(String action) { return actions.get(action); }

  void begin(String urn) {
    if (isRunning()) throw new IllegalStateException("A test case is already running");
    actions.clear();
    agentUrn = urn;
  }

  void accept(String urn, RuntimeAgent.TestMessageType type, DomainObject payload) {
    if (!owns(urn)) return;
    switch (type) {
      case TEST_STARTED -> actions.put(payload.urn(), Status.RUNNING);
      case TEST_FINISHED -> record(payload);
      case TESTCASE_FINISHED -> {
        for (var test : payload.getChildren()) {
          if ("test".equals(test.type())) record(test);
        }
        stopped(urn);
      }
      default -> { }
    }
  }

  void stopped(String urn) {
    if (!owns(urn)) return;
    actions.replaceAll((action, status) -> status == Status.RUNNING ? Status.FAILED : status);
    agentUrn = null;
  }

  private void record(DomainObject test) {
    actions.put(test.urn(), test.get("end") != null && test.get("outcome", false)
        ? Status.PASSED : Status.FAILED);
  }
}
