package org.integratedmodelling.klab.ide.utils;

import javafx.application.HostServices;

/**
 * The {@code AppContext} class provides a globally accessible holder for
 * application-wide services and context objects that need to be shared across
 * different parts of a JavaFX application.
 *
 * <p>Currently, this class is primarily used to store a reference to
 * {@link javafx.application.HostServices}, which allows any component in the
 * application (including those not directly managed by the main
 * {@link javafx.application.Application} class) to perform host-level operations
 * such as opening URLs in the default system browser.</p>
 *
 * <p>Example usage:</p>
 *
 * <pre>{@code
 * // In your Application subclass
 * @Override
 * public void start(Stage primaryStage) {
 *     AppContext.setHostServices(getHostServices());
 *     ...
 * }
 *
 * // Anywhere else in the application
 * HostServices hostServices = AppContext.getHostServices();
 * if (hostServices != null) {
 *     hostServices.showDocument("https://example.com");
 * }
 * }</pre>
 *
 * <p><strong>Thread safety:</strong> This class is not thread-safe by design.
 * It should be initialized once at application startup (typically from the
 * {@code start()} method of your {@code Application}) before use. Access from
 * multiple threads after initialization is safe for read-only operations.</p>
 *
 * <p><strong>Note:</strong> If {@link #getHostServices()} returns {@code null},
 * the application has likely failed to call {@link #setHostServices(HostServices)}
 * before attempting to use it.</p>
 *
 * @since 1.0
 */
public final class AppContext {
    private static HostServices hostServices;

    private AppContext() {}

    public static void setHostServices(HostServices hs) {
        hostServices = hs;
    }

    public static HostServices getHostServices() {
        return hostServices;
    }
}
