package org.integratedmodelling.klab.ide.components.generic;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import java.util.concurrent.*;
import org.integratedmodelling.klab.api.services.logging.*;
import org.junit.jupiter.api.Test;

class RemoteLogSourceTest {
  LogPage page(String cursor,boolean caughtUp) {
    return new LogPage("instance","UTC",List.of(),cursor,caughtUp,0,0,"vetted-diagnostics-v1");
  }
  @Test void pagesBeforeFollowingAndRequiresExplicitResetAfterFailure() {
    var requests=new ArrayList<LogQuery>();
    var source=new RemoteLogSource(q->{requests.add(q);return page("cursor"+requests.size(),requests.size()>1);});
    source.poll();source.poll();source.poll();
    assertNull(requests.get(0).cursor());assertFalse(requests.get(1).follow());
    assertEquals("cursor1",requests.get(1).cursor());assertTrue(requests.get(2).follow());
    source.close();assertThrows(IllegalStateException.class,source::poll);
    source.reset();source.poll();assertNull(requests.getLast().cursor());
  }
  @Test void closeDoesNotWaitForNetworkAndLateReplyCannotAdvanceResetCursor() throws Exception {
    var started=new CountDownLatch(1);var release=new CountDownLatch(1);
    var queries=Collections.synchronizedList(new ArrayList<LogQuery>());
    var source=new RemoteLogSource(q->{queries.add(q);if(queries.size()==1) {
      started.countDown();try { if(!release.await(5,TimeUnit.SECONDS)) throw new IllegalStateException(); }
      catch(InterruptedException e) { throw new IllegalStateException(e); }
    } return page("old-response",true);});
    try(var executor=Executors.newSingleThreadExecutor()) {
      var pending=executor.submit(source::poll);assertTrue(started.await(2,TimeUnit.SECONDS));
      assertTimeoutPreemptively(java.time.Duration.ofMillis(200),source::close);
      source.reset();release.countDown();pending.get(2,TimeUnit.SECONDS);source.poll();
      assertNull(queries.getLast().cursor());
    } finally { release.countDown();source.close(); }
  }
}
