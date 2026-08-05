package org.integratedmodelling.klab.ide;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

class SubmissionCompletionTrackerTest {

  @Test
  void graphBackedDuplicateStillRefreshesAfterEarlyModelerCallback() {
    var now = new AtomicLong(10);
    var tracker = new SubmissionCompletionTracker(now::get, 100);

    assertEquals(
        SubmissionCompletionTracker.Decision.COMPLETE_AND_REFRESH,
        tracker.record(42, false));
    now.incrementAndGet();
    assertEquals(
        SubmissionCompletionTracker.Decision.REFRESH_ONLY,
        tracker.record(42, true));
  }

  @Test
  void duplicateModelerCallbacksRemainSuppressedAndWindowExpires() {
    var now = new AtomicLong(10);
    var tracker = new SubmissionCompletionTracker(now::get, 100);

    assertEquals(
        SubmissionCompletionTracker.Decision.COMPLETE_AND_REFRESH,
        tracker.record(42, false));
    now.incrementAndGet();
    assertEquals(SubmissionCompletionTracker.Decision.IGNORE, tracker.record(42, false));
    now.addAndGet(101);
    assertEquals(
        SubmissionCompletionTracker.Decision.COMPLETE_AND_REFRESH,
        tracker.record(42, false));
  }
}
