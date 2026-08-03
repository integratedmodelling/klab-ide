package org.integratedmodelling.klab.ide.components;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.integratedmodelling.klab.api.actors.RuntimeAgent;
import org.integratedmodelling.klab.api.collections.DomainObject;
import org.junit.jupiter.api.Test;

class TestCaseResultModelTest {

  @Test
  void accumulatesStartedAndFinishedTestsWithoutDuplicatingThem() {
    var model = new TestCaseResultModel();
    var report = DomainObject.create(DomainObject.TYPE, "testcase", "start", 100L);
    model.accept(RuntimeAgent.TestMessageType.TESTCASE_STARTED, report);

    var running = DomainObject.create(DomainObject.TYPE, "test", DomainObject.URN, "checks_data");
    running.put("start", 110L);
    model.accept(RuntimeAgent.TestMessageType.TEST_STARTED, running);

    var assertion =
        DomainObject.create(
            DomainObject.TYPE, "assertion", DomainObject.URN, "actual == expected", "outcome", true);
    var finished =
        DomainObject.create(
            DomainObject.TYPE,
            "test",
            DomainObject.URN,
            "checks_data",
            "start",
            110L,
            "end",
            140L,
            "outcome",
            true,
            assertion);
    model.accept(RuntimeAgent.TestMessageType.TEST_FINISHED, finished);

    var statistics = model.statistics();
    assertSame(finished, report.getChildren().getFirst());
    assertEquals(1, report.getChildren().size());
    assertEquals(1, statistics.tests());
    assertEquals(1, statistics.testsFinished());
    assertEquals(1, statistics.testsPassed());
    assertEquals(1, statistics.assertionsPassed());
    assertFalse(statistics.finished());
  }

  @Test
  void finalReportDrivesFailureAndElapsedStatistics() {
    var model = new TestCaseResultModel();
    var failedAssertion =
        DomainObject.create(
            DomainObject.TYPE, "assertion", DomainObject.URN, "value is valid", "outcome", false);
    var failedTest =
        DomainObject.create(
            DomainObject.TYPE,
            "test",
            DomainObject.URN,
            "rejects_invalid",
            "end",
            250L,
            failedAssertion);
    var report =
        DomainObject.create(
            DomainObject.TYPE, "testcase", "start", 100L, "end", 300L, failedTest);

    model.accept(RuntimeAgent.TestMessageType.TESTCASE_FINISHED, report);

    var statistics = model.statistics();
    assertTrue(statistics.finished());
    assertEquals(1, statistics.testsFailed());
    assertEquals(1, statistics.assertionsFailed());
    assertEquals(200L, statistics.elapsedMilliseconds());
  }

  @Test
  void identifiesLifecycleMessagesByTheirConstantRatherThanRequestCorrelation() {
    assertEquals(
        RuntimeAgent.TestMessageType.TEST_FINISHED,
        BehaviorEditor.testMessageType(
            RuntimeAgent.TestMessageType.TEST_FINISHED.constant().getValue()));
  }

  @Test
  void failedTestActionDoesNotPassWhenItHasNoAssertions() {
    var model = new TestCaseResultModel();
    var failedTest =
        DomainObject.create(
            DomainObject.TYPE,
            "test",
            DomainObject.URN,
            "throws_error",
            "end",
            200L,
            "outcome",
            false,
            "stacktrace",
            "broken test");
    var report = DomainObject.create(DomainObject.TYPE, "testcase", failedTest);

    model.accept(RuntimeAgent.TestMessageType.TESTCASE_FINISHED, report);

    assertEquals(1, model.statistics().testsFailed());
    assertEquals(0, model.statistics().testsPassed());
  }
}
