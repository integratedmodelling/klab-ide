package org.integratedmodelling.klab.ide.lsp;

import org.eclipse.lsp4j.*;
import org.eclipse.lsp4j.jsonrpc.Launcher;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.eclipse.lsp4j.services.LanguageClient;
import org.eclipse.lsp4j.services.LanguageServer;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.*;
import java.util.function.Function;

import static org.integratedmodelling.klab.api.configuration.Configuration.KLAB_WORK_DIRECTORY;

public class KlabLspService {

    private static final KlabLspService INSTANCE = new KlabLspService();

    public static KlabLspService getInstance() {
        return INSTANCE;
    }

    private Process serverProcess;
    private LanguageServer server;
    private Launcher<LanguageServer> launcher;
    private final ExecutorService executor = Executors.newCachedThreadPool();
    private int versionCounter = 1;

    private volatile boolean initialized = false;

    private KlabLspService() {}

    public synchronized void startIfNeeded(Path workspaceRoot) throws Exception {
        if (initialized) return;

        // 1. Start Xtext LSP server process
        serverProcess = new ProcessBuilder("./start-lsp.sh")
                .directory(workspaceRoot.toFile())
                .redirectError(ProcessBuilder.Redirect.INHERIT)
                .start();

        InputStream in  = serverProcess.getInputStream();   // server -> client
        OutputStream out = serverProcess.getOutputStream(); // client -> server

        LanguageClient client = new KlabLanguageClient();

        launcher = Launcher.createLauncher(
                client,
                LanguageServer.class,
                in,
                out,
                executor,
                Function.identity()
        );
        server = launcher.getRemoteProxy();
        launcher.startListening();

        // 2. Initialize
        InitializeParams params = new InitializeParams();
        params.setCapabilities(new ClientCapabilities());
        params.setRootUri(workspaceRoot.toUri().toString());
        server.initialize(params).get(10, TimeUnit.SECONDS);
        server.initialized(new InitializedParams());

        initialized = true;
    }

    public LanguageServer getServer() {
        return server;
    }

    public void openDocument(String uri, String languageId, String text) {
        if (!initialized) return;
        TextDocumentItem item = new TextDocumentItem();
        item.setUri(uri);
        item.setLanguageId(languageId);
        item.setVersion(versionCounter++);
        item.setText(text);

        server.getTextDocumentService()
                .didOpen(new DidOpenTextDocumentParams(item));
    }

    public void changeDocument(String uri, String newText) {
        if (!initialized) return;
        TextDocumentContentChangeEvent change = new TextDocumentContentChangeEvent();
        change.setText(newText); // full text for now

        VersionedTextDocumentIdentifier id = new VersionedTextDocumentIdentifier();
        id.setUri(uri);
        id.setVersion(versionCounter++);

        DidChangeTextDocumentParams params =
                new DidChangeTextDocumentParams(id,
                        Collections.singletonList(change));

        server.getTextDocumentService().didChange(params);
    }

    public void closeDocument(String uri) {
        if (!initialized) return;
        TextDocumentIdentifier id = new TextDocumentIdentifier(uri);
        server.getTextDocumentService().didClose(
                new DidCloseTextDocumentParams(id));
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
        CompletionParams params = new CompletionParams(
                new TextDocumentIdentifier(uri),
                new Position(line, character)
        );
        return server.getTextDocumentService()
                .completion(params);
    }

    public void shutdown() throws Exception {
        if (!initialized) return;
        server.shutdown().get(5, TimeUnit.SECONDS);
        server.exit();
        serverProcess.destroy();
        executor.shutdown();
        initialized = false;
    }
}
