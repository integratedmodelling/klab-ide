package org.integratedmodelling.klab.ide.lsp;

import org.eclipse.lsp4j.*;
import org.eclipse.lsp4j.jsonrpc.Launcher;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.eclipse.lsp4j.services.LanguageClient;
import org.eclipse.lsp4j.services.LanguageServer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

public class KlabLspClient implements LanguageClient {

    private final ExecutorService executor = Executors.newCachedThreadPool();

    private Process serverProcess;
    private LanguageServer server;
    private Launcher<LanguageServer> launcher;

    // Track per-document versions (LSP requires monotonic version numbers)
    private final ConcurrentMap<String, AtomicInteger> documentVersions = new ConcurrentHashMap<>();

    public synchronized void start(Path workspaceRoot) throws Exception {
        if (server != null) {
            return; // already started
        }

        serverProcess = new ProcessBuilder("./start-lsp.sh")
                .directory(workspaceRoot.toFile())
                .redirectError(ProcessBuilder.Redirect.INHERIT)
                .start();

        InputStream in = serverProcess.getInputStream();    // server -> client
        OutputStream out = serverProcess.getOutputStream(); // client -> server

        launcher = Launcher.createLauncher(
                this,
                LanguageServer.class,
                in,
                out,
                executor,
                Function.identity()
        );

        server = launcher.getRemoteProxy();
        launcher.startListening();

        InitializeParams params = new InitializeParams();
        params.setCapabilities(new ClientCapabilities());
        params.setRootUri(workspaceRoot.toUri().toString());

        server.initialize(params).get(10_000, TimeUnit.MILLISECONDS);
        // You can also call server.initialized(new InitializedParams()) if needed
    }

    public synchronized void shutdown() {
        if (server == null) return;
        try {
            server.shutdown().get(5_000, TimeUnit.MILLISECONDS);
        } catch (Exception ignored) { }
        server.exit();
        server = null;
        if (serverProcess != null) {
            serverProcess.destroy();
            serverProcess = null;
        }
        executor.shutdownNow();
    }

    // ---------- Document lifecycle ----------

    public void openDocument(Path path, String languageId) throws IOException {
        String uri = path.toUri().toString();
        String text = Files.readString(path);
        int version = 1;

        documentVersions.put(uri, new AtomicInteger(version));

        TextDocumentItem doc = new TextDocumentItem(
                uri,
                languageId,
                version,
                text
        );
        DidOpenTextDocumentParams params = new DidOpenTextDocumentParams(doc);
        server.getTextDocumentService().didOpen(params);
    }

    public void changeDocument(Path path, String newText) {
        String uri = path.toUri().toString();
        AtomicInteger v = documentVersions.computeIfAbsent(uri, u -> new AtomicInteger(1));
        int newVersion = v.incrementAndGet();

        VersionedTextDocumentIdentifier docId = new VersionedTextDocumentIdentifier(newVersion);
        docId.setUri(uri);

        TextDocumentContentChangeEvent change =
                new TextDocumentContentChangeEvent();
        // full text sync: range == null
        change.setText(newText);

        DidChangeTextDocumentParams params =
                new DidChangeTextDocumentParams(docId, List.of(change));

        server.getTextDocumentService().didChange(params);
    }

    public void closeDocument(Path path) {
        String uri = path.toUri().toString();
        DidCloseTextDocumentParams params =
                new DidCloseTextDocumentParams(new TextDocumentIdentifier(uri));
        server.getTextDocumentService().didClose(params);
        documentVersions.remove(uri);
    }

    // ---------- Requests: completion, hover, etc. ----------

    public CompletableFuture<Either<List<CompletionItem>, CompletionList>> completion(
            Path path, int line, int character
    ) {
        String uri = path.toUri().toString();
        CompletionParams params = new CompletionParams(
                new TextDocumentIdentifier(uri),
                new Position(line, character)
        );
        return server.getTextDocumentService().completion(params);
    }

    public CompletableFuture<Hover> hover(
            Path path, int line, int character
    ) {
        String uri = path.toUri().toString();
        TextDocumentPositionParams params =
                new TextDocumentPositionParams(
                        new TextDocumentIdentifier(uri),
                        new Position(line, character)
                );
        return server.getTextDocumentService().hover((HoverParams) params);
    }

    // ---------- LanguageClient callbacks (basic no-op impls) ----------

    @Override
    public void telemetryEvent(Object object) { }

    @Override
    public void publishDiagnostics(PublishDiagnosticsParams diagnostics) {
        // TODO: push diagnostics to UI (error squiggles, panel, etc.)
        // You can call some callback or event bus from here.
        System.out.println("Diagnostics for " + diagnostics.getUri() + ": " +
                diagnostics.getDiagnostics().size());
    }

    @Override
    public void showMessage(MessageParams messageParams) {
        System.out.println("LSP message: " + messageParams.getMessage());
    }

    @Override
    public CompletableFuture<MessageActionItem> showMessageRequest(ShowMessageRequestParams requestParams) {
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public void logMessage(MessageParams message) {
        System.out.println("LSP log: " + message.getMessage());
    }
}
