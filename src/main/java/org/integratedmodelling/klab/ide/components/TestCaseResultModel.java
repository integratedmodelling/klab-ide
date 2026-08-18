package org.integratedmodelling.klab.ide.components;

import java.util.Objects;
import org.integratedmodelling.klab.api.actors.RuntimeAgent;
import org.integratedmodelling.klab.api.collections.DomainObject;

/** UI-independent accumulator for the incremental domain objects emitted by a test-case agent. */
public final class TestCaseResultModel {

  public record Statistics(
      int tests,
      int testsFinished,
      int testsPassed,
      int testsFailed,
      int assertions,
      int assertionsPassed,
      int assertionsFailed,
      boolean finished,
      long elapsedMilliseconds) {}

  private DomainObject report;
  private boolean finished;

  public synchronized void accept(RuntimeAgent.TestMessageType type, DomainObject payload) {
    if (type == null || payload == null) {
      return;
    }
    switch (type) {
      case TESTCASE_STARTED -> {
        report = payload;
        finished = false;
      }
      case TEST_STARTED, TEST_FINISHED -> {
        if (report == null) {
          report = DomainObject.create(DomainObject.TYPE, "testcase");
        }
        upsertTest(payload);
      }
      case TESTCASE_FINISHED -> {
        report = payload;
        finished = true;
      }
    }
  }

  public synchronized DomainObject report() {
    return report;
  }

  public synchronized Statistics statistics() {
    if (report == null) {
      return new Statistics(0, 0, 0, 0, 0, 0, 0, finished, 0);
    }
    int tests = report.getChildren().size();
    int testsFinished = 0;
    int testsPassed = 0;
    int assertions = 0;
    int assertionsPassed = 0;
    for (var test : report.getChildren()) {
      boolean testFinished = test.get("end") != null;
      if (testFinished) {
        testsFinished++;
      }
      int failedInTest = 0;
      for (var assertion :
          test.getChildren().stream()
              .filter(child -> "assertion".equals(child.type()))
              .toList()) {
        assertions++;
        if (assertion.get("outcome", false)) {
          assertionsPassed++;
        } else {
          failedInTest++;
        }
      }
      if (testFinished && test.get("outcome", failedInTest == 0)) {
        testsPassed++;
      }
    }
    long start = longValue(report.get("start"));
    long end = longValue(report.get("end"));
    long elapsed =
        start <= 0 ? 0 : Math.max(0, (end > 0 ? end : System.currentTimeMillis()) - start);
    return new Statistics(
        tests,
        testsFinished,
        testsPassed,
        testsFinished - testsPassed,
        assertions,
        assertionsPassed,
        assertions - assertionsPassed,
        finished,
        elapsed);
  }

  private void upsertTest(DomainObject update) {
    for (int i = 0; i < report.getChildren().size(); i++) {
      var current = report.getChildren().get(i);
      if (Objects.equals(current.urn(), update.urn())) {
        report.getChildren().set(i, update);
        return;
      }
    }
    report.getChildren().add(update);
  }

  private static long longValue(Object value) {
    return value instanceof Number number ? number.longValue() : 0;
  }
}
