package org.integratedmodelling.klab.ide.components;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.google.gson.*;
import com.sun.net.httpserver.HttpServer;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.LinkedBlockingQueue;
import org.integratedmodelling.klab.api.ServicesAPI;
import org.integratedmodelling.klab.api.collections.Parameters;
import org.integratedmodelling.klab.api.identities.UserIdentity;
import org.integratedmodelling.klab.api.scope.UserScope;
import org.integratedmodelling.klab.api.services.RuntimeService;
import org.junit.jupiter.api.Test;

class ApplicationRuntimeFilesTest {
  @Test void sendsBoundedRawFilesWithTheSameCallerContextAndRejectsPaths() throws Exception {
    var requests = new LinkedBlockingQueue<String>();
    var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext("/runtime/api/v1/ui/instances/instance/files/", request -> {
      try (request) {
        assertEquals("local-secret", request.getRequestHeaders().getFirst(ServicesAPI.SERVER_KEY_HEADER));
        assertEquals("caller-token", request.getRequestHeaders().getFirst("Authorization"));
        assertEquals("alice", request.getRequestHeaders().getFirst(ServicesAPI.USERNAME_HEADER));
        String body = new String(request.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        requests.add(request.getRequestMethod() + ":" + request.getRequestURI() + ":" + body);
        if (request.getRequestMethod().equals("POST")) assertEquals("application/octet-stream", request.getRequestHeaders().getFirst("Content-Type"));
        byte[] response = "{\"id\":\"opaque-file\"}".getBytes(StandardCharsets.UTF_8);
        request.sendResponseHeaders(200, response.length); request.getResponseBody().write(response);
      }
    });
    server.start();
    try {
      var runtime = mock(RuntimeService.class); when(runtime.isLocal()).thenReturn(true);
      when(runtime.getUrl()).thenReturn(URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/runtime").toURL());
      var user = mock(UserScope.class); var identity = mock(UserIdentity.class); when(user.getUser()).thenReturn(identity);
      when(identity.getId()).thenReturn("caller-token"); when(identity.getUsername()).thenReturn("alice");
      when(identity.getData()).thenReturn(Parameters.create()); when(identity.getGroups()).thenReturn(List.of());
      var upload = JsonParser.parseString("{\"operation\":\"upload\",\"nodeId\":\"upload\",\"name\":\"data file.csv\",\"mediaType\":\"text/csv\",\"content\":\"YSxiCg==\"}").getAsJsonObject();
      assertEquals("opaque-file", ApplicationRuntimeFiles.request(runtime, user, "instance", upload, "local-secret").get("id").getAsString());
      assertTrue(requests.remove().endsWith(":a,b\n"));
      var download = JsonParser.parseString("{\"operation\":\"download\",\"handle\":\"opaque-file\"}").getAsJsonObject();
      ApplicationRuntimeFiles.request(runtime, user, "instance", download, "local-secret");
      assertTrue(requests.remove().startsWith("GET:/runtime/api/v1/ui/instances/instance/files/opaque-file"));
      download.addProperty("handle", "../../secret");
      assertThrows(IllegalArgumentException.class, () -> ApplicationRuntimeFiles.request(runtime, user, "instance", download, "local-secret"));
      upload.addProperty("content", "A".repeat(5592412));
      assertThrows(IllegalArgumentException.class, () -> ApplicationRuntimeFiles.request(runtime, user, "instance", upload, "local-secret"));
      assertTrue(requests.isEmpty());
    } finally { server.stop(0); }
  }
}
