package org.integratedmodelling.klab.ide.components;

import com.google.gson.*;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import org.integratedmodelling.klab.api.ServicesAPI;
import org.integratedmodelling.klab.api.Klab;
import org.integratedmodelling.klab.api.configuration.Configuration;
import org.integratedmodelling.klab.api.scope.UserScope;
import org.integratedmodelling.klab.api.services.RuntimeService;

/** Bounded instance-only HTTP; service authentication never reaches either browser. */
final class ApplicationRuntimeFiles {
  static JsonObject request(RuntimeService runtime, UserScope user, String instance, JsonObject request) {
    ApplicationWebView.requireLocalRuntime(runtime);
    return request(runtime, user, instance, request, Configuration.INSTANCE.getServiceSecret(
        org.integratedmodelling.klab.api.services.KlabService.Type.RUNTIME));
  }
  static JsonObject request(RuntimeService runtime, UserScope user, String instance, JsonObject request, String secret) {
    ApplicationWebView.requireLocalRuntime(runtime);
    if (!instance.matches("[a-zA-Z0-9_-]{1,160}")) throw new IllegalArgumentException("Invalid application instance");
    String operation = request.get("operation").getAsString();
    String endpoint = runtime.getUrl().toString().replaceAll("/+$", "") + "/api/v1/ui/instances/" + instance + "/files/";
    var builder = HttpRequest.newBuilder().timeout(Duration.ofSeconds(30)).header("Accept", "application/json");
    if (operation.equals("upload")) {
      String node = request.get("nodeId").getAsString();
      if (!node.matches("[a-zA-Z0-9_-]{1,160}")) throw new IllegalArgumentException("Invalid upload widget");
      String encoded = request.get("content").getAsString();
      if (encoded.length() > 5592408) throw new IllegalArgumentException("Upload exceeds 4 MiB");
      byte[] bytes = Base64.getDecoder().decode(encoded);
      if (bytes.length > 4194304) throw new IllegalArgumentException("Upload exceeds 4 MiB");
      builder.uri(URI.create(endpoint + node + "?name=" + encode(request.get("name").getAsString())
          + "&mediaType=" + encode(request.get("mediaType").getAsString())))
          .header("Content-Type", "application/octet-stream").POST(HttpRequest.BodyPublishers.ofByteArray(bytes));
    } else if (operation.equals("download")) {
      String handle = request.get("handle").getAsString();
      if (!handle.matches("[a-zA-Z0-9_-]{1,160}")) throw new IllegalArgumentException("Invalid file handle");
      builder.uri(URI.create(endpoint + handle)).GET();
    } else throw new IllegalArgumentException("Unknown application file operation");
    builder.header(ServicesAPI.SERVER_KEY_HEADER, secret);
    var identity = user.getUser();
    if (identity != null) {
      if (identity.getId() != null) builder.header("Authorization", identity.getId());
      if (!identity.isAnonymous() && !"anonymous".equals(identity.getUsername())) {
        if (identity.getUsername() != null) builder.header(ServicesAPI.USERNAME_HEADER, identity.getUsername());
        var federation = Klab.INSTANCE.getFederationData(identity);
        if (federation != null) builder.header(ServicesAPI.FEDERATION_HEADER, federation.getId());
      }
    }
    try (var http = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build()) {
      var response = http.send(builder.build(), HttpResponse.BodyHandlers.ofInputStream());
      try (var body = response.body()) {
        if (response.statusCode() < 200 || response.statusCode() >= 300)
          throw new IllegalStateException("Runtime file transfer returned HTTP " + response.statusCode());
        byte[] json = body.readNBytes(5600001);
        if (json.length > 5600000) throw new IllegalStateException("Runtime file response exceeds limit");
        return JsonParser.parseString(new String(json, StandardCharsets.UTF_8)).getAsJsonObject();
      }
    } catch (InterruptedException interrupted) {
      Thread.currentThread().interrupt(); throw new IllegalStateException("File transfer interrupted", interrupted);
    } catch (java.io.IOException failure) { throw new IllegalStateException("Runtime file transfer failed", failure); }
  }
  private static String encode(String value) { return URLEncoder.encode(value, StandardCharsets.UTF_8); }
}
