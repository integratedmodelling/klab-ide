package org.integratedmodelling.klab.ide.components.generic;

import static org.junit.jupiter.api.Assertions.*;
import java.lang.reflect.Method;
import java.nio.file.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import javafx.application.Platform;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

/** Deterministic local-reader regressions: watcher stopped, production incremental reader invoked
 * from a worker to separate parsing/UI defects from OS notification timing. */
class LocalLogViewerUpdateTest {
  @TempDir Path root;
  @BeforeAll static void startFx() throws Exception {
    var ready=new CountDownLatch(1);
    try {Platform.startup(ready::countDown);} catch(IllegalStateException running) {Platform.runLater(ready::countDown);}
    assertTrue(ready.await(5,TimeUnit.SECONDS));Platform.setImplicitExit(false);
  }
  static <T> T fx(Callable<T> work) throws Exception {
    var result=new CompletableFuture<T>();Platform.runLater(()->{
      try {result.complete(work.call());} catch(Throwable error) {result.completeExceptionally(error);}
    });return result.get(5,TimeUnit.SECONDS);
  }
  static String legacy(String message) {
    return "[2026-10-23 16:00:00.000] - 123 INFO [main] --- local.logger: "+message+"\n";
  }
  LogViewer reader(Path file) throws Exception {
    var viewer=fx(()->new LogViewer(file));
    update(viewer);
    fx(()->{var field=LogViewer.class.getDeclaredField("watcherService");field.setAccessible(true);
      ((ExecutorService)field.get(viewer)).shutdownNow();return null;});return viewer;
  }
  static void readOnWorker(LogViewer viewer) throws Exception {
    Method read=LogViewer.class.getDeclaredMethod("readNewContent");read.setAccessible(true);
    try(var worker=Executors.newSingleThreadExecutor()) {
      worker.submit(()->{try {read.invoke(viewer);}catch(ReflectiveOperationException e){throw new RuntimeException(e);}}).get(5,TimeUnit.SECONDS);
    }
  }
  static void update(LogViewer viewer) throws Exception {readOnWorker(viewer);fx(()->null);}
  @Test void completeAppendAndPartialLineCompletionDoNotDuplicateRows() throws Exception {
    var file=root.resolve("append.log");Files.writeString(file,legacy("first"));var viewer=reader(file);
    try {
      String second=legacy("second");Files.writeString(file,second.substring(0,second.length()-1),StandardOpenOption.APPEND);
      update(viewer);assertEquals(1,fx(()->viewer.getEntries().size()));
      Files.writeString(file,"\n",StandardOpenOption.APPEND);update(viewer);update(viewer);
      assertEquals(2,fx(()->viewer.getEntries().size()));assertEquals("second",fx(()->viewer.getEntries().getLast().getMessage()));
    } finally {fx(()->{viewer.shutdown();return null;});}
  }
  @Test void appendedStackContinuationUpdatesExistingRowOnlyOnFxThread() throws Exception {
    var file=root.resolve("stack.log");Files.writeString(file,legacy("failure"));var viewer=reader(file);
    var offFx=new AtomicBoolean();fx(()->{viewer.getEntries().getFirst().messageProperty()
        .addListener((observable,before,after)->{if(!Platform.isFxApplicationThread())offFx.set(true);});return null;});
    try {
      Files.writeString(file,"\tat local.Test.run(Test.java:4)\n",StandardOpenOption.APPEND);update(viewer);
      assertEquals(1,fx(()->viewer.getEntries().size()));
      assertTrue(fx(()->viewer.getEntries().getFirst().getMessage().contains("Test.java:4")));
      assertFalse(offFx.get(),"Displayed JavaFX properties must not be mutated by the watcher thread");
    } finally {fx(()->{viewer.shutdown();return null;});}
  }
  @Test void sameSizeReplacementReplacesOldRows() throws Exception {
    var file=root.resolve("replace.log");Files.writeString(file,legacy("OLD"));var viewer=reader(file);
    try {
      Files.writeString(file,legacy("NEW"));update(viewer);
      assertEquals(1,fx(()->viewer.getEntries().size()));assertEquals("NEW",fx(()->viewer.getEntries().getFirst().getMessage()));
    } finally {fx(()->{viewer.shutdown();return null;});}
  }
  @Test void largerRewriteDoesNotAppendItsSuffixToPreviousGeneration() throws Exception {
    var file=root.resolve("rewrite.log");Files.writeString(file,legacy("OLD"));var viewer=reader(file);
    try {
      Files.writeString(file,legacy("replacement first")+legacy("replacement second"));update(viewer);
      assertEquals(2,fx(()->viewer.getEntries().size()));
      assertEquals("replacement first",fx(()->viewer.getEntries().getFirst().getMessage()));
      assertEquals("replacement second",fx(()->viewer.getEntries().getLast().getMessage()));
    } finally {fx(()->{viewer.shutdown();return null;});}
  }
  @Test void queuedOldInitialLoadCannotLeakAcrossSetFile() throws Exception {
    var old=root.resolve("old.log");var current=root.resolve("current.log");Files.writeString(old,legacy("OLD"));Files.writeString(current,legacy("NEW"));
    var viewer=fx(()->{var v=new LogViewer(old);readOnWorker(v);v.setFile(current);readOnWorker(v);return v;});fx(()->null);
    try {assertEquals(1,fx(()->viewer.getEntries().size()));assertEquals("NEW",fx(()->viewer.getEntries().getFirst().getMessage()));}
    finally {fx(()->{viewer.shutdown();return null;});}
  }
  @Test void standardServiceFilePatternIsRecognizedWithoutPidOrDashes() throws Exception {
    var file=root.resolve("default.log");Files.writeString(file,
        "[2026-10-23 16:00:00.000] INFO  [main] o.i.k.Test: default format\n");var viewer=reader(file);
    try {assertEquals(1,fx(()->viewer.getEntries().size()));assertEquals("default format",fx(()->viewer.getEntries().getFirst().getMessage()));}
    finally {fx(()->{viewer.shutdown();return null;});}
  }
  static void await(Callable<Boolean> condition) throws Exception {
    long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(8);
    while(System.nanoTime()<end){if(fx(condition))return;Thread.sleep(20);}fail("Viewer update timed out");
  }
  @Test void realWatcherFindsFileCreatedLaterInANewDirectory() throws Exception {
    var file=root.resolve("later/sub/service.log");var viewer=fx(()->new LogViewer(file));
    try {Files.createDirectories(file.getParent());Files.writeString(file,legacy("created later"));
      await(()->viewer.getEntries().size()==1);assertEquals("created later",fx(()->viewer.getEntries().getFirst().getMessage()));}
    finally {fx(()->{viewer.shutdown();return null;});}
  }
  @Test void realWatcherTracksRenameRotationAndTruncation() throws Exception {
    var file=root.resolve("rotating.log");Files.writeString(file,legacy("OLD"));var viewer=fx(()->new LogViewer(file));
    try {
      await(()->viewer.getEntries().size()==1);Files.move(file,root.resolve("rotated.log"));Files.writeString(file,legacy("NEW"));
      await(()->viewer.getEntries().size()==1 && viewer.getEntries().getFirst().getMessage().equals("NEW"));
      Files.writeString(file,"");Files.writeString(file,legacy("after truncation"));
      await(()->viewer.getEntries().size()==1 && viewer.getEntries().getFirst().getMessage().equals("after truncation"));
    } finally {fx(()->{viewer.shutdown();return null;});}
  }
  @Test void splitUtf8AndContinuationPreserveTextWithoutDuplicates() throws Exception {
    var file=root.resolve("utf8.log");Files.writeString(file,legacy("first"));var viewer=reader(file);
    try {
      byte[] bytes=legacy("caf\u00e9").getBytes(java.nio.charset.StandardCharsets.UTF_8);
      int split=bytes.length-2;Files.write(file,java.util.Arrays.copyOf(bytes,split),StandardOpenOption.APPEND);update(viewer);
      assertEquals(1,fx(()->viewer.getEntries().size()));
      Files.write(file,java.util.Arrays.copyOfRange(bytes,split,bytes.length),StandardOpenOption.APPEND);update(viewer);update(viewer);
      assertEquals("caf\u00e9",fx(()->viewer.getEntries().getLast().getMessage()));assertEquals(2,fx(()->viewer.getEntries().size()));
    } finally {fx(()->{viewer.shutdown();return null;});}
  }
  @Test void updatesPreserveColumnSelectionAndRespectScrollPreference() throws Exception {
    var file=root.resolve("scroll.log");Files.writeString(file,legacy("first"));var viewer=reader(file);var scrolls=new java.util.concurrent.atomic.AtomicInteger();
    try {
      fx(()->{new javafx.scene.Scene(viewer,900,400);viewer.applyCss();viewer.layout();
        viewer.setVisibleColumns(java.util.EnumSet.of(LogViewer.Column.LEVEL,LogViewer.Column.MESSAGE));
        var table=(javafx.scene.control.TableView<?>)viewer.getChildren().getFirst();
        table.addEventFilter(javafx.scene.control.ScrollToEvent.scrollToTopIndex(),event->scrolls.incrementAndGet());
        var field=LogViewer.class.getDeclaredField("autoScroll");field.setAccessible(true);field.setBoolean(viewer,false);return null;});
      Files.writeString(file,legacy("second"),StandardOpenOption.APPEND);update(viewer);assertEquals(0,scrolls.get());
      assertEquals(2,fx(()->((javafx.scene.control.TableView<?>)viewer.getChildren().getFirst()).getColumns().size()));
      fx(()->{var field=LogViewer.class.getDeclaredField("autoScroll");field.setAccessible(true);field.setBoolean(viewer,true);return null;});
      Files.writeString(file,legacy("third"),StandardOpenOption.APPEND);update(viewer);assertTrue(scrolls.get()>0);
    } finally {fx(()->{viewer.shutdown();return null;});}
  }

}
