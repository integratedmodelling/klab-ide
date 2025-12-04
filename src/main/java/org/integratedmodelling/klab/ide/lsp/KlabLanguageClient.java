package org.integratedmodelling.klab.ide.lsp;

import org.eclipse.lsp4j.*;
import org.eclipse.lsp4j.services.LanguageClient;

import java.util.concurrent.CompletableFuture;

class KlabLanguageClient implements LanguageClient {

    @Override
    public void publishDiagnostics(PublishDiagnosticsParams diagnostics) {
        // TODO: route to Monaco later (gutter markers, panel, etc.)
        System.out.println("Diagnostics for " + diagnostics.getUri() + ": " + diagnostics.getDiagnostics());
    }

    @Override
    public void showMessage(MessageParams messageParams) {
        System.out.println("LSP message: " + messageParams.getMessage());
    }

    @Override
    public void logMessage(MessageParams message) {
        System.out.println("LSP log: " + message.getMessage());
    }

    @Override
    public void telemetryEvent(Object o) {

    }

    @Override
    public CompletableFuture<MessageActionItem> showMessageRequest(ShowMessageRequestParams message) {
        return CompletableFuture.completedFuture(new MessageActionItem(message.getMessage()));
    }

    @Override
    public CompletableFuture<Void> registerCapability(RegistrationParams params) {
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<Void> unregisterCapability(UnregistrationParams params) {
        return CompletableFuture.completedFuture(null);
    }
}

