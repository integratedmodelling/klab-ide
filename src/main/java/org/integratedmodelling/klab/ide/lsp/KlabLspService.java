package org.integratedmodelling.klab.ide.lsp;

import org.eclipse.lsp4j.*;
import org.eclipse.lsp4j.jsonrpc.Launcher;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.eclipse.lsp4j.services.LanguageClient;
import org.eclipse.lsp4j.services.LanguageServer;
import org.integratedmodelling.klab.api.engine.distribution.LocalInstance;
import org.integratedmodelling.klab.ide.KlabIDEController;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.*;
import java.util.Map;
import java.util.function.Function;

public class KlabLspService {

  private static final KlabLspService INSTANCE = new KlabLspService();
  private final Map<String, Integer> docVersions = new ConcurrentHashMap<>();

  private int nextVersion(String uri) {
    return docVersions.merge(uri, 1, Integer::sum);
  }

  public static KlabLspService getInstance() {
    return INSTANCE;
  }

  //  private Process serverProcess;
  private LanguageServer server;
  private Launcher<LanguageServer> launcher;
  private final ExecutorService executor = Executors.newCachedThreadPool();
  private int versionCounter = 1;

  private volatile boolean initialized = false;

  private KlabLspService() {}

  public synchronized boolean ensureInitialized() {

    if (KlabIDEController.instance().getLanguageServer().getStatus()
        != LocalInstance.Status.RUNNING) {
      return false;
    }

    if (initialized) {
      return true;
    }

    try {
      LanguageClient client = new KlabLanguageClient();

      launcher =
          Launcher.createLauncher(
              client,
              LanguageServer.class,
              KlabIDEController.instance().getLanguageServer().getInputStream(),
              KlabIDEController.instance().getLanguageServer().getOutputStream(),
              executor,
              Function.identity());
      server = launcher.getRemoteProxy();
      launcher.startListening();

      // 2. Initialize
      InitializeParams params = new InitializeParams();
      params.setCapabilities(new ClientCapabilities());
      server.initialize(params).get(60, TimeUnit.SECONDS);
      server.initialized(new InitializedParams());

      initialized = true;
    } catch (Exception e) {
      // the nightmare was that a closed outputstream was coming out of the instance. Hopefully
      // that is solved now.
      return false;
    }
    return true;
  }

  public LanguageServer getServer() {
    return server;
  }

  public void openDocument(String uri, String languageId, String text) {
    if (!initialized) return;

    // Set baseline version for this document (start at 1)
    docVersions.put(uri, 1);

    TextDocumentItem item = new TextDocumentItem();
    item.setUri(uri);
    item.setLanguageId(languageId);
    item.setVersion(1);
    item.setText(text);

    DidOpenTextDocumentParams params = new DidOpenTextDocumentParams(item);
    server.getTextDocumentService().didOpen(params);

    System.out.println(
        "[LSP] didOpen uri=" + uri + " version=1 len=" + (text != null ? text.length() : 0));
  }

  public void changeDocument(String uri, String newText) {
    if (!initialized) return;

    Integer current = docVersions.get(uri);
    if (current == null) {
      // This is *very* useful to detect ordering bugs (didChange before didOpen)
      System.err.println(
          "[LSP] didChange called for unopened uri=" + uri + " -> forcing baseline version");
      docVersions.put(uri, 1);
    }

    int v = nextVersion(uri);

    TextDocumentContentChangeEvent change = new TextDocumentContentChangeEvent();
    change.setText(newText); // full text

    VersionedTextDocumentIdentifier id = new VersionedTextDocumentIdentifier();
    id.setUri(uri);
    id.setVersion(v);

    DidChangeTextDocumentParams params =
        new DidChangeTextDocumentParams(id, Collections.singletonList(change));

    try {
      server.getTextDocumentService().didChange(params);
      System.out.println(
          "[LSP] didChange uri="
              + uri
              + " version="
              + v
              + " len="
              + (newText != null ? newText.length() : 0));
    } catch (Exception e) {
      System.err.println("[LSP] didChange failed uri=" + uri + " version=" + v);
      e.printStackTrace();
    }
  }

  public void closeDocument(String uri) {
    if (!initialized) return;
    try {
      TextDocumentIdentifier id = new TextDocumentIdentifier(uri);
      server.getTextDocumentService().didClose(new DidCloseTextDocumentParams(id));
      System.out.println("[LSP] didClose uri=" + uri);
    } catch (Exception e) {
      System.err.println("[LSP] didClose failed uri=" + uri);
      e.printStackTrace();
    } finally {
      docVersions.remove(uri);
    }
  }

  public CompletableFuture<Either<List<CompletionItem>, CompletionList>> completion(
      String uri, int line, int character) {

    if (!initialized) {
      CompletableFuture<Either<List<CompletionItem>, CompletionList>> f = new CompletableFuture<>();
      f.completeExceptionally(new IllegalStateException("LSP not initialized"));
      return f;
    }

    TextDocumentIdentifier id = new TextDocumentIdentifier(uri);
    Position pos = new Position(line, character);
    CompletionParams params =
        new CompletionParams(new TextDocumentIdentifier(uri), new Position(line, character));
    return server.getTextDocumentService().completion(params);
  }

  //  public void shutdown() throws Exception {
  //    if (!initialized) return;
  //    server.shutdown().get(5, TimeUnit.SECONDS);
  //    server.exit();
  //    serverProcess.destroy();
  //    executor.shutdown();
  //    initialized = false;
  //  }
  //
  //  private Process startServerProcess(Path workspaceRoot) throws Exception {
  //    // Location of "target/classes" relative to workspaceRoot
  //    Path classesDir = workspaceRoot.resolve("target").resolve("classes");
  //
  //    // Load classpath.txt which the .sh script uses
  //    Path cpFile = workspaceRoot.resolve("target").resolve("classpath.txt");
  //    String extraCp = java.nio.file.Files.readString(cpFile).trim();
  //
  //    // Build the full classpath (classes + additional entries from classpath.txt)
  //    // TODO change this to something production-ready
  //    String classpath = classesDir.toString() + System.getProperty("path.separator") + extraCp;
  //
  //    // Build the Java command equivalent to start-lsp.sh
  //    ProcessBuilder pb =
  //        new ProcessBuilder(
  //            "java",
  //            "-Dxtext.disable.standalone.setup=true",
  //            "org.eclipse.xtext.ide.server.ServerLauncher");
  //
  //    // put CLASSPATH in an env var so that the CL doesn't kill Windows
  //    pb.environment().put("CLASSPATH", classpath);
  //    pb.directory(workspaceRoot.toFile());
  //    pb.redirectError(ProcessBuilder.Redirect.INHERIT);
  //
  //    return pb.start();
  //  }

  //  public static void main(String[] diocan) throws Exception {
  //    Path workspaceRoot = Paths.get(System.getProperty("user.home") + "/git/klab-ide");
  //    KlabLspService.getInstance().startIfNeeded(workspaceRoot);
  //  }
}
