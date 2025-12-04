package org.integratedmodelling.klab.ide.lsp;


import org.eclipse.lsp4j.*;
import org.eclipse.lsp4j.jsonrpc.Launcher;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.eclipse.lsp4j.services.LanguageClient;
import org.eclipse.lsp4j.services.LanguageServer;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

public class KlabEmbeddedLspSmokeTest {

    public static void main(String[] args) throws Exception {

        Process server = new ProcessBuilder("./start-lsp.sh")
                .redirectError(ProcessBuilder.Redirect.INHERIT)
                .start();

        InputStream in = server.getInputStream();   // server -> client
        OutputStream out = server.getOutputStream(); // client -> server

        LanguageClient client = new LanguageClient() {
            @Override
            public void telemetryEvent(Object o) {

            }

            @Override
            public void publishDiagnostics(PublishDiagnosticsParams publishDiagnosticsParams) {
                System.out.println("Diagnostics: " + publishDiagnosticsParams);
            }

            @Override
            public void showMessage(MessageParams messageParams) {
                System.out.println("Server message: " + messageParams.getMessage());
            }

            @Override
            public CompletableFuture<MessageActionItem> showMessageRequest(ShowMessageRequestParams showMessageRequestParams) {
                return null;
            }

            @Override
            public void logMessage(MessageParams messageParams) {
                System.out.println("Server log: " + messageParams.getMessage());
            }

            @Override
            public CompletableFuture<Void> registerCapability(RegistrationParams params) {
                return CompletableFuture.completedFuture(null);
            }

            @Override
            public CompletableFuture<Void> unregisterCapability(UnregistrationParams params) {
                return CompletableFuture.completedFuture(null);
            }
        };

        Launcher<LanguageServer> launcher =
                Launcher.createLauncher(
                        client,
                        LanguageServer.class,
                        in,
                        out,
                        Executors.newCachedThreadPool(),
                        Function.identity()
                );

        LanguageServer ls = launcher.getRemoteProxy();
        launcher.startListening();

        // --- IMPORTANT PART: give the server a workspace root ---
//        Path workspace = Paths.get("").toAbsolutePath();
//        String rootUri = workspace.toUri().toString();
//
//        InitializeParams params = new InitializeParams();
//        params.setCapabilities(new ClientCapabilities());
//        params.setRootUri(rootUri);
//        params.setWorkspaceFolders(List.of(new WorkspaceFolder(rootUri, "klab-workspace")));
//
//        System.out.println("Sending initialize with rootUri = " + rootUri);
//        InitializeResult result = ls.initialize(params).get(5000, TimeUnit.MILLISECONDS);
//        System.out.println("Initialize OK: " + result);
//
//        // optional: send "initialized" notification
//        ls.initialized(new InitializedParams());
//
//        // clean shutdown instead of just destroy()
//        ls.shutdown().get(2000, TimeUnit.MILLISECONDS);
//        ls.exit();
//        server.destroy();
        //
        // 1) INITIALIZE
        //
        InitializeParams params = new InitializeParams();
        params.setCapabilities(new ClientCapabilities());
        params.setRootUri("file:///home/klab/git/klab-ide/");

        System.out.println("Sending initialize with rootUri = " + params.getRootUri());
        InitializeResult result = ls.initialize(params).get(10_000, TimeUnit.MILLISECONDS);
        System.out.println("Initialize OK: " + result);

        //
        // 2) OPEN A DOCUMENT
        //
        Path filePath = Path.of("/home/klab/git/klab-ide/test.kim");
        String uri = filePath.toUri().toString();   // file:///...

        String text = Files.readString(filePath);

        TextDocumentItem doc = new TextDocumentItem(
                uri,
                "kim",   // languageId – use whatever your Kim language server expects
                1,       // version
                text
        );

        DidOpenTextDocumentParams openParams = new DidOpenTextDocumentParams(doc);
        System.out.println("Sending didOpen for " + uri);
        ls.getTextDocumentService().didOpen(openParams);

        //
        // 3) ASK FOR COMPLETIONS AT SOME POSITION
        //
        // Choose a line/character where completion should make sense.
        // 0-based line/character indices:
        Position pos = new Position(0, 0);

        CompletionParams completionParams = new CompletionParams(
                new TextDocumentIdentifier(uri),
                pos
        );

        System.out.println("Requesting completion at " + pos);
        Either<List<CompletionItem>, CompletionList> completion =
                ls.getTextDocumentService()
                        .completion(completionParams)
                        .get(10_000, TimeUnit.MILLISECONDS);

        System.out.println("Completion result:");
        if (completion.isLeft()) {
            List<CompletionItem> items = completion.getLeft();
            items.stream()
                    .limit(20)
                    .forEach(ci -> System.out.println("  - " + ci.getLabel()));
        } else if (completion.isRight()) {
            CompletionList list = completion.getRight();
            list.getItems().stream()
                    .limit(20)
                    .forEach(ci -> System.out.println("  - " + ci.getLabel()));
        } else {
            System.out.println("  (no completion items)");
        }

        //
        // 4) SHUTDOWN CLEANLY
        //
        try {
            ls.shutdown().get(5_000, TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            System.out.println("Shutdown failed/ignored: " + e.getMessage());
        }
        ls.exit();

        server.destroy();
    }
}

