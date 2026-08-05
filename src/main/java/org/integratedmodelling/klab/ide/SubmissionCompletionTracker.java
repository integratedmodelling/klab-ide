package org.integratedmodelling.klab.ide;

import java.util.HashMap;
import java.util.Map;
import java.util.function.LongSupplier;

/**
 * Distinguishes duplicate submission callbacks from the graph-backed event that makes a commit
 * safe to render.
 */
final class SubmissionCompletionTracker {

  enum Decision {
    COMPLETE_AND_REFRESH,
    REFRESH_ONLY,
    IGNORE
  }

  private static final long DEFAULT_WINDOW_NANOS = 1_000_000_000L;

  private final Map<Long, Long> recentlyFinished = new HashMap<>();
  private final LongSupplier clock;
  private final long duplicateWindowNanos;

  SubmissionCompletionTracker() {
    this(System::nanoTime, DEFAULT_WINDOW_NANOS);
  }

  SubmissionCompletionTracker(LongSupplier clock, long duplicateWindowNanos) {
    this.clock = clock;
    this.duplicateWindowNanos = duplicateWindowNanos;
  }

  Decision record(long observationId, boolean knowledgeGraphCurrent) {
    if (observationId < 0) {
      return Decision.COMPLETE_AND_REFRESH;
    }

    var now = clock.getAsLong();
    var previous = recentlyFinished.put(observationId, now);
    recentlyFinished.entrySet().removeIf(entry -> now - entry.getValue() > duplicateWindowNanos);
    if (previous == null || now - previous > duplicateWindowNanos) {
      return Decision.COMPLETE_AND_REFRESH;
    }
    return knowledgeGraphCurrent ? Decision.REFRESH_ONLY : Decision.IGNORE;
  }
}
