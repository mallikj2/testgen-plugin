package com.testgen.plugin.util;

import com.intellij.notification.Notification;
import com.intellij.notification.NotificationGroupManager;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;

/**
 * NotificationUtil
 *
 * Centralized utility for displaying notifications and error dialogs
 * in the IntelliJ IDEA IDE.
 *
 * Two modes:
 *   - Balloon notifications: non-blocking, shown in the IDE's notification area
 *   - Modal dialogs: blocking error dialogs for hard gate failures
 */
public class NotificationUtil {

    private static final String NOTIFICATION_GROUP_ID = "TestGen.Notifications";
    private static final String PLUGIN_NAME = "TestGen";

    // ── Balloon notifications ────────────────────────────────────────────────

    /**
     * Shows an error balloon notification.
     */
    public static void showError(Project project, String title, String content) {
        showNotification(project, title, content, NotificationType.ERROR);
    }

    /**
     * Shows an informational balloon notification.
     */
    public static void showInfo(Project project, String title, String content) {
        showNotification(project, title, content, NotificationType.INFORMATION);
    }

    /**
     * Shows a warning balloon notification.
     */
    public static void showWarning(Project project, String title, String content) {
        showNotification(project, title, content, NotificationType.WARNING);
    }

    /**
     * Core notification method.
     */
    private static void showNotification(Project project, String title,
                                          String content, NotificationType type) {
        try {
            Notification notification = NotificationGroupManager.getInstance()
                    .getNotificationGroup(NOTIFICATION_GROUP_ID)
                    .createNotification(title, content, type);

            Notifications.Bus.notify(notification, project);
        } catch (Exception e) {
            // Fallback if notification group not registered — use legacy approach
            com.intellij.notification.NotificationGroup group =
                    new com.intellij.notification.NotificationGroup(
                            NOTIFICATION_GROUP_ID,
                            com.intellij.notification.NotificationDisplayType.BALLOON
                    );
            Notification notification = group.createNotification(title, content, type);
            Notifications.Bus.notify(notification, project);
        }
    }

    // ── Modal dialogs (for hard block errors) ────────────────────────────────

    /**
     * Shows a blocking error dialog with an HTML-formatted message.
     * Used for hard-block gate failures (Copilot not installed, etc.)
     *
     * @param project  current project (can be null)
     * @param title    dialog title
     * @param message  HTML-formatted message body
     */
    public static void showErrorDialog(Project project, String title, String message) {
        Messages.showErrorDialog(project, message, PLUGIN_NAME + ": " + title);
    }

    /**
     * Shows a blocking information dialog.
     *
     * @param project current project (can be null)
     * @param title   dialog title
     * @param message message body
     */
    public static void showInfoDialog(Project project, String title, String message) {
        Messages.showInfoMessage(project, message, PLUGIN_NAME + ": " + title);
    }

    /**
     * Shows a "yes/no" confirmation dialog.
     *
     * @return true if the user clicked "Yes"
     */
    public static boolean showConfirmDialog(Project project, String title, String message) {
        int result = Messages.showYesNoDialog(
                project,
                message,
                PLUGIN_NAME + ": " + title,
                Messages.getQuestionIcon()
        );
        return result == Messages.YES;
    }
}
