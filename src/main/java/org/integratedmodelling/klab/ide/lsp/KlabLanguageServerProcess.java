//package org.integratedmodelling.klab.ide.lsp;
//
//import org.eclipse.lsp4j.ClientCapabilities;
//import org.eclipse.lsp4j.InitializeParams;
//import org.eclipse.lsp4j.InitializeResult;
//import org.eclipse.lsp4j.InitializedParams;
//import org.eclipse.lsp4j.jsonrpc.Launcher;
//import org.eclipse.lsp4j.services.LanguageClient;
//import org.eclipse.lsp4j.services.LanguageServer;
//
//
//import java.io.InputStream;
//import java.io.OutputStream;
//
//import java.nio.file.Paths;
//import java.util.concurrent.CompletableFuture;
//import java.util.concurrent.ExecutorService;
//import java.util.concurrent.Executors;
//import java.util.concurrent.TimeUnit;
//import java.util.function.Function;
//
//public class KlabEmbeddedLspSmokeTest {
//
//    public static void main(String[] args) throws Exception {
//
//        // 1. Build a classpath identical to the current Java process
//        String classpath = System.getProperty("java.class.path");
//
//        // 2. Start the LSP server in a child JVM
//        ProcessBuilder pb = new ProcessBuilder(
//                "java",
//                "-Dxtext.disable.standalone.setup=true",
//                "-cp", classpath,
//                "org.eclipse.xtext.ide.server.ServerLauncher"
//        );
//        pb.redirectError(ProcessBuilder.Redirect.INHERIT);
//
//        System.out.println("Starting LSP server process...");
//        Process server = pb.start();
//
//        // 3. Connect LSP4J to the server’s stdin/stdout
//        InputStream in = server.getInputStream();    // server -> client
//        OutputStream out = server.getOutputStream(); // client -> server
//
//        LanguageClient client = new LoggingClient();
//
//        ExecutorService executor = Executors.newCachedThreadPool();
//
//        Launcher<LanguageServer> launcher =
//                Launcher.createLauncher(
//                        client,
//                        LanguageServer.class,
//                        in,
//                        out,
//                        executor,
//                        Function.identity()
//                );
//
//        LanguageServer languageServer = launcher.getRemoteProxy();
//
//        // 4. Start listening for messages
//        launcher.startListening();
//
//        // 5. Send initialize request
//        InitializeParams params = new InitializeParams();
//        params.setCapabilities(new ClientCapabilities());
//
//        // optional but nice:
//        try {
//            long pid = ProcessHandle.current().pid();
//            params.setProcessId((int) pid);
//        } catch (Throwable ignored) {}
//
//        String rootUri = Paths.get(".").toAbsolutePath().normalize().toUri().toString();
//        params.setRootUri(rootUri);
//
//        System.out.println("Sending initialize...");
//        CompletableFuture<InitializeResult> initFuture = languageServer.initialize(params);
//
//        InitializeResult result = initFuture.get(10, TimeUnit.SECONDS);
//        System.out.println("Initialize OK: " +
//                (result.getServerInfo() != null ? result.getServerInfo().getName() : "<no name>"));
//
//        // 6. Send initialized notification (protocol niceness)
//        languageServer.initialized(new InitializedParams());
//
//        // 7. Clean shutdown
//        languageServer.shutdown().get(5, TimeUnit.SECONDS);
//        languageServer.exit();
//
//        // Give the server a moment to exit gracefully
//        server.waitFor(5, TimeUnit.SECONDS);
//        server.destroyForcibly();
//
//        executor.shutdownNow();
//
//        System.out.println("LSP smoke test finished successfully.");
//    }
//}
