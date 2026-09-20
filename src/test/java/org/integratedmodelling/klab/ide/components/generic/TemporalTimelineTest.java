package org.integratedmodelling.klab.ide.components.generic;
import static org.junit.jupiter.api.Assertions.*;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
class TemporalTimelineTest {
  @Test void fullYearMinuteBackgroundIsBoundedByPixels() {
    assertEquals(50,Timeline.intervalCount(TimeUnit.DAYS.toMillis(365),TimeUnit.MINUTES,1,600));
    assertEquals(1,Timeline.intervalCount(0,TimeUnit.MINUTES,1,600));
  }
}
