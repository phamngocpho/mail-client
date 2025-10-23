package utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import raven.toast.Notifications;

import javax.swing.*;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Utility class for handling asynchronous operations in Swing applications.
 * Provides reusable patterns for SwingWorker operations and error handling.
 */
public class AsyncUtils {
    private static final Logger logger = LoggerFactory.getLogger(AsyncUtils.class);

    /**
     * Executes a background task with proper error handling and UI updates.
     * This method reduces code duplication for common SwingWorker patterns.
     *
     * @param backgroundTask the task to execute in background
     * @param onSuccess callback to execute on success (runs on EDT)
     * @param onError callback to execute on error (runs on EDT)
     * @param <T> the return type of the background task
     */
    public static <T> void executeAsync(Supplier<T> backgroundTask, Consumer<T> onSuccess, Consumer<Exception> onError) {
        SwingWorker<T, Void> worker = new SwingWorker<>() {
            @Override
            protected T doInBackground() {
                return backgroundTask.get();
            }

            @Override
            protected void done() {
                try {
                    T result = get();
                    if (onSuccess != null) {
                        onSuccess.accept(result);
                    }
                } catch (Exception e) {
                    logger.error("Background task failed", e);
                    if (onError != null) {
                        onError.accept(e);
                    }
                }
            }
        };
        worker.execute();
    }

    /**
     * Executes a background task that doesn't return a value (Void).
     * Useful for operations like updating flags, deleting emails, etc.
     *
     * @param backgroundTask the task to execute in background
     * @param onSuccess callback to execute on success (runs on EDT)
     * @param onError callback to execute on error (runs on EDT)
     */
    public static void executeVoidAsync(Runnable backgroundTask, Runnable onSuccess, Consumer<Exception> onError) {
        SwingWorker<Void, Void> worker = new SwingWorker<>() {
            @Override
            protected Void doInBackground() {
                backgroundTask.run();
                return null;
            }

            @Override
            protected void done() {
                try {
                    get();
                    if (onSuccess != null) {
                        onSuccess.run();
                    }
                } catch (Exception e) {
                    logger.error("Background task failed", e);
                    if (onError != null) {
                        onError.accept(e);
                    }
                }
            }
        };
        worker.execute();
    }

    /**
     * Shows a standardized error notification.
     * Centralizes error message formatting to reduce duplication.
     *
     * @param operation the operation that failed
     * @param error the exception that occurred
     */
    public static void showError(String operation, Exception error) {
        String message = String.format("Failed to %s: %s", operation, error.getMessage());
        Notifications.getInstance().show(Notifications.Type.ERROR, message);
        logger.error("Operation failed: {}", operation, error);
    }

    /**
     * Shows a simple error notification with custom message.
     *
     * @param message the error message to display
     */
    public static void showError(String message) {
        Notifications.getInstance().show(Notifications.Type.ERROR, message);
        logger.error("Error: {}", message);
    }
}
