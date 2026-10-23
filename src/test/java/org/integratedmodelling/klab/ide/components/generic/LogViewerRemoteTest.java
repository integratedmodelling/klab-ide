package org.integratedmodelling.klab.ide.components.generic;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import javafx.application.Platform;
import org.integratedmodelling.common.utils.Utils;
import org.integratedmodelling.klab.api.services.logging.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

class LogViewerRemoteTest {
  @TempDir Path root;
  @BeforeAll static void startFx() throws Exception {
    var started=new CountDownLatch(1);
    try {Platform.startup(started::countDown);}
    catch (IllegalStateException alreadyStarted) {Platform.runLater(started::countDown);}
    assertTrue(started.await(5,TimeUnit.SECONDS));Platform.setImplicitExit(false);
  }
  // JavaFX is process-wide and cannot restart: leave shutdown to the test JVM.
  static <T> T fx(Callable<T> action) throws Exception {
    var future=new CompletableFuture<T>();Platform.runLater(()->{
      try {future.complete(action.call());} catch(Throwable error) {future.completeExceptionally(error);}
    });return future.get(5,TimeUnit.SECONDS);
  }
  LogRecord record(String id,boolean partial) {
    return new LogRecord(id,"2026-10-23T00:00:00Z","ERROR","safe.logger","failure",
        "Caused by: java.io.IOException: message redacted",Map.of(),partial,false);
  }
  void await(Callable<Boolean> condition) throws Exception {
    long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(8);
    while(System.nanoTime()<deadline) {if(fx(condition))return;Thread.sleep(20);}
    fail("Viewer condition did not complete within eight seconds");
  }
  @Test void partialEventsUpdateWithoutDuplicatesAndVisibleWindowIsBounded() throws Exception {
    var polls=new AtomicInteger();
    var source=new RemoteLogSource(query->{
      int count=polls.incrementAndGet();
      List<LogRecord> records=count==1?List.of(record("same",true)):
          count==2?List.of(record("same",false),record("second",false)):
          java.util.stream.IntStream.range(0,5001).mapToObj(i->record("bulk"+i,false)).toList();
      return new LogPage("instance","UTC",records,"cursor",true,0,0,"vetted-diagnostics-v1");
    });
    var viewer=fx(()->new LogViewer(source));
    try {
      await(()->viewer.getEntries().size()==2);
      assertFalse(fx(()->viewer.getEntries().getFirst().getMessage().contains("partial event")));
      await(()->viewer.getEntries().size()==5000);
      assertTrue(polls.get()>=3);
    } finally {fx(()->{viewer.shutdown();return null;});}
  }
  @Test void remoteFailureDoesNotExposeRawMessagesAndLocalFileModeRemainsUsable() throws Exception {
    var source=new RemoteLogSource(query->{throw new Utils.Http.Client.RequestFailure(410,"token secret",null);});
    var viewer=fx(()->new LogViewer(source));
    try {
      await(()->!viewer.getEntries().isEmpty());
      assertTrue(fx(()->viewer.getEntries().getFirst().getMessage().contains("410")));
      assertFalse(fx(()->viewer.getEntries().getFirst().getMessage().contains("secret")));
      var file=root.resolve("local.log");Files.writeString(file,
          "[2026-10-23 13:31:46.123] - 123 INFO [main] --- local.logger: local text\n");
      fx(()->{viewer.setFile(file);return null;});
      await(()->viewer.getEntries().size()==1 && viewer.getEntries().getFirst().getMessage().equals("local text"));
    } finally {fx(()->{viewer.shutdown();return null;});}
  }
}
