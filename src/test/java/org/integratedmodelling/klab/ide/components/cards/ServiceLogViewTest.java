package org.integratedmodelling.klab.ide.components.cards;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.concurrent.Callable;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.StackPane;
import org.integratedmodelling.common.services.client.BaseServiceClient;
import org.integratedmodelling.common.utils.Utils;
import org.integratedmodelling.klab.api.authentication.CRUDOperation;
import org.integratedmodelling.klab.api.services.KlabService;
import org.integratedmodelling.klab.api.services.logging.*;
import org.integratedmodelling.klab.ide.components.generic.LogViewer;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

class ServiceLogViewTest {
  @TempDir Path directory;
  @BeforeAll static void fxStart() throws Exception {
    var ready = new CountDownLatch(1);
    try {Platform.startup(ready::countDown);} catch (IllegalStateException started) {Platform.runLater(ready::countDown);}
    assertTrue(ready.await(5,TimeUnit.SECONDS));Platform.setImplicitExit(false);
  }
  static <T> T fx(Callable<T> work) throws Exception {
    var result=new CompletableFuture<T>();Platform.runLater(()->{try {result.complete(work.call());}catch(Throwable e){result.completeExceptionally(e);}});
    return result.get(5,TimeUnit.SECONDS);
  }
  static void await(Callable<Boolean> test) throws Exception {
    long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(8);
    while(System.nanoTime()<end){if(fx(test))return;Thread.sleep(20);}fail("UI condition timed out");
  }
  KlabService.ServiceCapabilities admin() {
    var caps=mock(KlabService.ServiceCapabilities.class);when(caps.getPermissions()).thenReturn(EnumSet.of(CRUDOperation.ADMINISTER));return caps;
  }
  LogCapabilities supported() {return new LogCapabilities("instance",List.of("service"),200,1000,262144,65536,"UTC","vetted-diagnostics-v1",false,false);}
  LogPage page() {return new LogPage("instance","UTC",List.of(new LogRecord("one","2026-10-23T00:00:00Z","ERROR","safe.logger","failure","Caused by: java.io.IOException: message redacted",Map.of(),false,false)),"cursor",true,0,0,"vetted-diagnostics-v1");}
  @Test void productionEntryUsesRemoteAuthenticatedClientOffFxAndStopsWhenHidden() throws Exception {
    var client=mock(BaseServiceClient.class);var onFx=new AtomicBoolean();var polls=new AtomicInteger();
    when(client.logCapabilities()).thenAnswer(call->{onFx.set(Platform.isFxApplicationThread());return supported();});
    when(client.queryLogs(any())).thenAnswer(call->{onFx.set(onFx.get()||Platform.isFxApplicationThread());polls.incrementAndGet();return page();});
    var pane=fx(()->ServiceDashboard.createLogView(client,null,admin()));
    var root=fx(()->new StackPane(pane));fx(()->new Scene(root));
    try {
      await(()->pane.getCenter() instanceof LogViewer v && v.getEntries().size()==1);
      assertFalse(onFx.get());
      fx(()->{pane.setActive(false);return null;});int before=polls.get();Thread.sleep(1200);assertEquals(before,polls.get());
      fx(()->{pane.setActive(true);return null;});int inactiveCount=before;await(()->polls.get()>inactiveCount);
      fx(()->{root.setVisible(false);return null;});before=polls.get();Thread.sleep(1200);assertEquals(before,polls.get());
      fx(()->{root.setVisible(true);return null;});int hiddenCount=before;await(()->polls.get()>hiddenCount);
      fx(()->{root.getChildren().clear();return null;});before=polls.get();Thread.sleep(1200);assertEquals(before,polls.get());
    } finally {fx(()->{pane.close();return null;});}
  }
  @Test void denialAndMissingCapabilityDoNotPollOrExposeProviderMessages() throws Exception {
    var client=mock(BaseServiceClient.class);
    var denied=fx(()->ServiceDashboard.createLogView(client,null,null));fx(()->new Scene(denied));
    assertTrue(fx(()->((Label)((javafx.scene.layout.HBox)denied.getTop()).getChildren().getFirst()).getText().contains("administration")));
    verify(client,never()).logCapabilities();verify(client,never()).queryLogs(any());fx(()->{denied.close();return null;});
    when(client.logCapabilities()).thenThrow(new IllegalStateException("token secret"));
    var unavailable=fx(()->ServiceDashboard.createLogView(client,null,admin()));fx(()->new Scene(unavailable));
    try {await(()->((Label)((javafx.scene.layout.HBox)unavailable.getTop()).getChildren().getFirst()).getText().contains("unavailable or access denied"));verify(client,never()).queryLogs(any());}
    finally {fx(()->{unavailable.close();return null;});}
  }
  @Test void gapStopsUntilExplicitReloadThenStartsWithFreshCursor() throws Exception {
    var client=mock(BaseServiceClient.class);when(client.logCapabilities()).thenReturn(supported());var queries=new CopyOnWriteArrayList<LogQuery>();
    when(client.queryLogs(any())).thenAnswer(call->{LogQuery q=call.getArgument(0);queries.add(q);if(queries.size()==1)throw new Utils.Http.Client.RequestFailure(410,"token secret",null);return page();});
    var pane=fx(()->ServiceDashboard.createLogView(client,null,admin()));fx(()->new Scene(pane));
    try {
      await(()->pane.getCenter() instanceof LogViewer v && !v.getEntries().isEmpty());
      assertTrue(fx(()->((LogViewer)pane.getCenter()).getEntries().getFirst().getMessage().contains("HTTP 410")));
      assertFalse(fx(()->((LogViewer)pane.getCenter()).getEntries().getFirst().getMessage().contains("secret")));
      Thread.sleep(1200);assertEquals(1,queries.size());
      fx(()->{((Button)((javafx.scene.layout.HBox)pane.getTop()).getChildren().get(1)).fire();return null;});
      await(()->queries.size()>1);assertNull(queries.get(1).cursor());
    } finally {fx(()->{pane.close();return null;});}
  }
  @Test void localEntryPreservesItsFileAndNeverUsesRemoteApi() throws Exception {
    var client=mock(BaseServiceClient.class);when(client.isLocal()).thenReturn(true);
    Path file=directory.resolve("local.log");Files.writeString(file,"[2026-10-23 16:00:00.000] - 123 INFO [main] --- local.logger: local marker\n");
    var pane=fx(()->ServiceDashboard.createLogView(client,file,null));fx(()->new Scene(pane));
    try {await(()->pane.getCenter() instanceof LogViewer v && !v.getEntries().isEmpty());verify(client,never()).logCapabilities();verify(client,never()).queryLogs(any());}
    finally {fx(()->{pane.close();return null;});}
  }
  @Test void lateCapabilityAfterDetachCannotStartPolling() throws Exception {
    var client=mock(BaseServiceClient.class);var started=new CountDownLatch(1);var release=new CountDownLatch(1);
    when(client.logCapabilities()).thenAnswer(call->{started.countDown();assertTrue(release.await(5,TimeUnit.SECONDS));return supported();});
    var pane=fx(()->ServiceDashboard.createLogView(client,null,admin()));var root=fx(()->new StackPane(pane));fx(()->new Scene(root));
    try {
      assertTrue(started.await(5,TimeUnit.SECONDS));fx(()->{root.getChildren().clear();return null;});release.countDown();
      Thread.sleep(300);fx(()->null);verify(client,never()).queryLogs(any());assertNull(fx(pane::getCenter));
    } finally {release.countDown();fx(()->{pane.close();return null;});}
  }
  @Test void visibleManagedWindowRendersRemoteDiagnosticsAndCloseStopsPolling() throws Exception {
    var client=mock(BaseServiceClient.class);when(client.logCapabilities()).thenReturn(supported());var polls=new AtomicInteger();
    when(client.queryLogs(any())).thenAnswer(call->{polls.incrementAndGet();return page();});
    var pane=fx(()->ServiceDashboard.createLogView(client,null,admin()));
    var stage=fx(()->{var w=new javafx.stage.Stage();w.setTitle("Isolated remote log acceptance - synthetic diagnostics");w.setScene(new Scene(pane,1000,500));w.show();return w;});
    try {
      await(()->pane.getCenter() instanceof LogViewer v && v.getEntries().size()==1);
      fx(()->{
        pane.applyCss();pane.layout();var image=pane.snapshot(null,null);
        var pixels=new int[(int)image.getWidth()*(int)image.getHeight()];
        image.getPixelReader().getPixels(0,0,(int)image.getWidth(),(int)image.getHeight(),javafx.scene.image.PixelFormat.getIntArgbInstance(),pixels,0,(int)image.getWidth());
        var buffered=new java.awt.image.BufferedImage((int)image.getWidth(),(int)image.getHeight(),java.awt.image.BufferedImage.TYPE_INT_ARGB);
        buffered.setRGB(0,0,buffered.getWidth(),buffered.getHeight(),pixels,0,buffered.getWidth());
        javax.imageio.ImageIO.write(buffered,"png",Path.of(System.getProperty("user.home"),"service-log-ui-acceptance.png").toFile());
        stage.hide();return null;
      });
      int before=polls.get();Thread.sleep(1200);assertEquals(before,polls.get());
    } finally {fx(()->{pane.close();stage.close();return null;});}
  }

  @Test void permissionRevokedAfterCapabilityStopsWithSafeHttpStatus() throws Exception {
    var client=mock(BaseServiceClient.class);when(client.logCapabilities()).thenReturn(supported());var polls=new AtomicInteger();
    when(client.queryLogs(any())).thenAnswer(call->{polls.incrementAndGet();throw new Utils.Http.Client.RequestFailure(403,"token secret",null);});
    var pane=fx(()->ServiceDashboard.createLogView(client,null,admin()));fx(()->new Scene(pane));
    try {
      await(()->pane.getCenter() instanceof LogViewer v && !v.getEntries().isEmpty());
      assertTrue(fx(()->((LogViewer)pane.getCenter()).getEntries().getFirst().getMessage().contains("HTTP 403")));
      assertFalse(fx(()->((LogViewer)pane.getCenter()).getEntries().getFirst().getMessage().contains("secret")));
      Thread.sleep(1200);assertEquals(1,polls.get());
    } finally {fx(()->{pane.close();return null;});}
  }
  @Test void absentOrUnsupportedCapabilityNeverCreatesRemotePoller() throws Exception {
    var client=mock(BaseServiceClient.class);
    for (var caps : java.util.Arrays.asList((LogCapabilities)null,
        new LogCapabilities("id",List.of("other"),200,1000,262144,65536,"UTC","vetted-diagnostics-v1",false,false),
        new LogCapabilities("id",List.of("service"),200,1000,262144,65536,"UTC","unknown",false,false))) {
      when(client.logCapabilities()).thenReturn(caps);
      var pane=fx(()->ServiceDashboard.createLogView(client,null,admin()));fx(()->new Scene(pane));
      try {await(()->((Label)((javafx.scene.layout.HBox)pane.getTop()).getChildren().getFirst()).getText().contains("unavailable or access denied"));verify(client,never()).queryLogs(any());}
      finally {fx(()->{pane.close();return null;});}
    }
  }

  @Test void dashboardLogsButtonOpensTheRemoteViewerThroughProductionAction() throws Exception {
    var client=mock(BaseServiceClient.class);when(client.logCapabilities()).thenReturn(supported());when(client.queryLogs(any())).thenReturn(page());
    var status=new org.integratedmodelling.klab.api.services.impl.ServiceStatusImpl();status.setOperational(true);when(client.status()).thenReturn(status);
    var permissions=admin();when(client.capabilities(any())).thenReturn(permissions);
    var previous=org.integratedmodelling.klab.ide.KlabIDEController.instance();
    var dashboard=fx(()->{
      new org.integratedmodelling.klab.ide.KlabIDEController(){@Override public org.integratedmodelling.klab.api.scope.UserScope user(){return null;}};
      return new ServiceDashboard(client,"Isolated test service",true);
    });
    var root=fx(()->new StackPane(dashboard));
    try {
      await(()->!((ToggleButton)dashboard.lookup("#service-logs")).isDisabled());
      fx(()->{((ToggleButton)dashboard.lookup("#service-logs")).fire();return new Scene(root);});
      await(()->dashboard.lookup("#service-log-view") instanceof ServiceLogView pane
          && pane.getCenter() instanceof LogViewer v && v.getEntries().size()==1);
      verify(client,atLeastOnce()).queryLogs(any());
    } finally {fx(()->{
      if(dashboard.lookup("#service-log-view") instanceof ServiceLogView pane)pane.close();
      root.getChildren().clear();
      var field=org.integratedmodelling.klab.ide.KlabIDEController.class.getDeclaredField("_this");field.setAccessible(true);field.set(null,previous);return null;
    });}
  }

}
