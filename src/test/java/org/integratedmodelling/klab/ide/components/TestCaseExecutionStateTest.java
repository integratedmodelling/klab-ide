package org.integratedmodelling.klab.ide.components;

import static org.junit.jupiter.api.Assertions.*;

import org.integratedmodelling.klab.api.actors.RuntimeAgent.TestMessageType;
import org.integratedmodelling.klab.api.collections.DomainObject;
import org.integratedmodelling.klab.api.lang.kactors.impl.KActorsBehaviorImpl;
import org.junit.jupiter.api.Test;

class TestCaseExecutionStateTest {
  @Test void locksUntilCompletionAndRetainsResultsUntilNextRun() {
    var state = new TestCaseExecutionState();
    state.begin("first");
    assertTrue(state.isRunning());
    assertThrows(IllegalStateException.class, () -> state.begin("second"));
    state.accept("first", TestMessageType.TEST_STARTED, test("one", false, false));
    assertEquals(TestCaseExecutionState.Status.RUNNING, state.status("one"));
    state.accept("first", TestMessageType.TEST_FINISHED, test("one", true, true));
    assertTrue(state.isRunning());
    assertEquals(TestCaseExecutionState.Status.PASSED, state.status("one"));
    state.accept("first", TestMessageType.TESTCASE_FINISHED,
        DomainObject.create(DomainObject.TYPE, "testcase", test("one", true, true), test("two", true, false)));
    assertFalse(state.isRunning());
    assertEquals(TestCaseExecutionState.Status.FAILED, state.status("two"));
    state.begin("second");
    assertNull(state.status("one"));
    state.accept("first", TestMessageType.TESTCASE_FINISHED, DomainObject.create());
    assertTrue(state.isRunning());
  }

  @Test void stopFailsUnfinishedActionsAndKeepsCompletedResults() {
    var state = new TestCaseExecutionState();
    state.begin("run");
    state.accept("run", TestMessageType.TEST_STARTED, test("pending", false, false));
    state.accept("run", TestMessageType.TEST_FINISHED, test("done", true, true));
    state.stopped("stale");
    assertTrue(state.isRunning());
    state.stopped("run");
    assertFalse(state.isRunning());
    assertEquals(TestCaseExecutionState.Status.FAILED, state.status("pending"));
    assertEquals(TestCaseExecutionState.Status.PASSED, state.status("done"));
  }

  @Test void singleActionRequestPreservesSourceSnapshotAndBehaviorIdentity() {
    var behavior = new KActorsBehaviorImpl();
    behavior.setUrn("tests.example");
    behavior.setSourceCode("testcase tests.example");
    var request = BehaviorEditor.executionBehavior(behavior, "one");
    assertEquals("tests.example", request.getUrn());
    assertEquals(behavior.getSourceCode(), request.getSourceCode());
    assertEquals("one", request.getProperties().get("testAction"));
    assertFalse(behavior.getProperties().containsKey("testAction"));
    assertSame(behavior, BehaviorEditor.executionBehavior(behavior, null));
  }

  private static DomainObject test(String name, boolean finished, boolean passed) {
    var test = DomainObject.create(DomainObject.TYPE, "test", DomainObject.URN, name, "outcome", passed);
    if (finished) test.put("end", 1L);
    return test;
  }
}
