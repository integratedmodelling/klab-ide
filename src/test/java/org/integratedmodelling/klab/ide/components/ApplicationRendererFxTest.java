package org.integratedmodelling.klab.ide.components;

import static org.junit.jupiter.api.Assertions.*;
import com.google.gson.*;
import java.util.*;
import java.util.concurrent.*;
import javafx.application.Platform;
import javafx.concurrent.Worker;
import javafx.scene.web.WebView;
import netscape.javascript.JSObject;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class ApplicationRendererFxTest {
  @BeforeAll static void startFx() throws Exception {
    var ready = new CompletableFuture<Void>();
    try { Platform.startup(() -> { Platform.setImplicitExit(false); ready.complete(null); }); }
    catch (IllegalStateException running) { Platform.runLater(() -> ready.complete(null)); }
    ready.get(15, TimeUnit.SECONDS);
  }
  public static final class Input {
    final BlockingQueue<String> events = new LinkedBlockingQueue<>();
    public void input(String json) { events.add(json); }
  }
  @Test void packagedQuasarWidgetsRenderInsideJavaFxAndEmitTypedInput() throws Exception {
    String html = ApplicationRenderer.load().replace("<head>", "<head><script>window.onerror=function(message){window.rendererError=String(message);};</script>");
    var loaded = new CompletableFuture<Void>();
    var bridge = new Input();
    var web = fx(() -> {
      var view = new WebView(); view.resize(1000, 800);
      view.getEngine().getLoadWorker().stateProperty().addListener((o, before, state) -> {
        if (state == Worker.State.SUCCEEDED) loaded.complete(null);
        else if (state == Worker.State.FAILED) loaded.completeExceptionally(view.getEngine().getLoadWorker().getException());
      });
      view.getEngine().loadContent(html); return view;
    });
    try {
      loaded.get(20, TimeUnit.SECONDS);
      Object error = fx(() -> web.getEngine().executeScript("window.rendererError || 'no error captured'"));
      assertEquals("function", fx(() -> web.getEngine().executeScript("typeof window.receive")), "Quasar bundle did not initialize: " + error);
      fx(() -> { ((JSObject) web.getEngine().executeScript("window")).setMember("ide", bridge); return null; });
      String fixture = """
          {"type":"instance","value":{"id":"fixture","state":{"snapshot":{"protocolVersion":1,"revision":4,"root":{"id":"root","type":"container","properties":{"theme":"light"},"events":[],"children":[
          {"id":"toggle","type":"switch","properties":{"label":"Enabled","value":false},"events":["change"],"children":[]},
          {"id":"range","type":"range","properties":{"label":"Interval","value":{"min":2,"max":8},"min":0,"max":10},"events":["change"],"children":[]},
          {"id":"tabs","type":"tabs","properties":{"label":"Sections","options":["one","two"],"value":"one"},"events":["change"],"children":[]},
          {"id":"markdown","type":"markdown","properties":{"label":"**Ready**"},"events":[],"children":[]},
          {"id":"save","type":"submit","properties":{"label":"Save"},"events":["submit"],"children":[]},
          {"id":"tree","type":"tree","properties":{"label":"Hierarchy","items":[{"id":"a","label":"Alice"}],"value":""},"events":["select"],"children":[]}
          ]}}},"stylesheets":{}}}
          """;
      fx(() -> { web.getEngine().executeScript("receive(" + fixture + ");receive({type:'ready',value:{lastAcceptedSequence:0}})"); return null; });
      await(() -> fx(() -> Boolean.TRUE.equals(web.getEngine().executeScript("document.querySelectorAll('.q-toggle').length===1 && document.querySelector('strong')?.textContent==='Ready'"))));
      assertEquals(0, ((Number) fx(() -> web.getEngine().executeScript("document.querySelectorAll('.unsupported').length"))).intValue());
      fx(() -> { web.getEngine().executeScript("document.querySelector('.q-toggle').click()"); return null; });
      var input = JsonParser.parseString(Objects.requireNonNull(bridge.events.poll(5, TimeUnit.SECONDS))).getAsJsonObject().getAsJsonObject("value");
      assertEquals("toggle", input.get("nodeId").getAsString()); assertTrue(input.get("value").getAsBoolean());
      assertEquals(4, input.get("revision").getAsInt());
      assertNull(fx(() -> web.getEngine().executeScript("window.rendererError || null")));
    } finally { fx(() -> { web.getEngine().load(null); return null; }); }
  }
  private static void await(Callable<Boolean> test) throws Exception {
    long end = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
    while (!test.call()) { if (System.nanoTime() > end) fail("Renderer did not mount its Quasar controls"); Thread.sleep(25); }
  }
  private static <T> T fx(Callable<T> test) throws Exception {
    var result = new CompletableFuture<T>();
    Platform.runLater(() -> { try { result.complete(test.call()); } catch (Throwable error) { result.completeExceptionally(error); } });
    return result.get(15, TimeUnit.SECONDS);
  }
}
