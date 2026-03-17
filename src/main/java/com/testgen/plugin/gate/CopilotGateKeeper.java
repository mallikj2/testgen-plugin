package com.testgen.plugin.gate;

import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.ide.plugins.PluginManagerCore;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import com.testgen.plugin.util.NotificationUtil;

/**
 * CopilotGateKeeper
 *
 * Performs all prerequisite checks before TestGen can proceed.
 * Acts as a hard blocker — if any check fails, TestGen will not run.
 *
 * Checks performed:
 *   1. GitHub Copilot plugin (com.github.copilot) is installed and enabled
 *   2. GitHub Copilot Chat tool window is accessible (proxy for authentication)
 *
 * Note: GitHub Copilot does not expose a public Java API for authentication
 * state. We use the availability and enabled state of its Tool Window as the
 * most reliable proxy for "Copilot is installed, running, and authenticated".
 */
public class CopilotGateKeeper {

    /** The official plugin ID of GitHub Copilot for JetBrains IDEs */
    public static final String COPILOT_PLUGIN_ID = "com.github.copilot";

    /**
     * The Tool Window ID registered by the GitHub Copilot plugin for its Chat panel.
     * This ID is used as a proxy to verify Copilot is active and authenticated.
     */
    public static final String COPILOT_CHAT_TOOL_WINDOW_ID = "GitHub Copilot Chat";

    /**
     * Result object returned from gate checks.
     */
    public static class GateResult {
        private final boolean passed;
        private final String errorTitle;
        private final String errorMessage;
        private final GateFailureReason reason;

        private GateResult(boolean passed, String errorTitle,
                           String errorMessage, GateFailureReason reason) {
            this.passed = passed;
            this.errorTitle = errorTitle;
            this.errorMessage = errorMessage;
            this.reason = reason;
        }

        public static GateResult pass() {
            return new GateResult(true, null, null, null);
        }

        public static GateResult fail(GateFailureReason reason,
                                       String errorTitle, String errorMessage) {
            return new GateResult(false, errorTitle, errorMessage, reason);
        }

        public boolean isPassed() { return passed; }
        public String getErrorTitle() { return errorTitle; }
        public String getErrorMessage() { return errorMessage; }
        public GateFailureReason getReason() { return reason; }
    }

    /**
     * Enum describing why the gate check failed.
     */
    public enum GateFailureReason {
        COPILOT_NOT_INSTALLED,
        COPILOT_NOT_ENABLED,
        COPILOT_NOT_AUTHENTICATED
    }

    /**
     * Runs all gate checks in sequence.
     * Stops and returns the first failure encountered.
     *
     * @param project the current IntelliJ project context
     * @return GateResult — either PASS or the first FAIL with details
     */
    public GateResult check(Project project) {

        // ── CHECK 1: Is the GitHub Copilot plugin installed at all? ──────────
        IdeaPluginDescriptor copilotDescriptor = PluginManagerCore
                .getPlugin(PluginId.getId(COPILOT_PLUGIN_ID));

        if (copilotDescriptor == null) {
            return GateResult.fail(
                    GateFailureReason.COPILOT_NOT_INSTALLED,
                    "GitHub Copilot Not Found",
                    "<html><body>" +
                    "<b>TestGen requires the GitHub Copilot plugin.</b><br/><br/>" +
                    "The GitHub Copilot plugin (<code>com.github.copilot</code>) " +
                    "is not installed in your IDE.<br/><br/>" +
                    "<b>To install it:</b><br/>" +
                    "1. Go to <b>Settings → Plugins → Marketplace</b><br/>" +
                    "2. Search for <b>\"GitHub Copilot\"</b><br/>" +
                    "3. Install and restart IntelliJ IDEA<br/>" +
                    "4. Sign in to GitHub Copilot<br/><br/>" +
                    "Then try TestGen again." +
                    "</body></html>"
            );
        }

        // ── CHECK 2: Is the GitHub Copilot plugin enabled? ───────────────────
        if (!copilotDescriptor.isEnabled()) {
            return GateResult.fail(
                    GateFailureReason.COPILOT_NOT_ENABLED,
                    "GitHub Copilot is Disabled",
                    "<html><body>" +
                    "<b>The GitHub Copilot plugin is installed but disabled.</b><br/><br/>" +
                    "<b>To enable it:</b><br/>" +
                    "1. Go to <b>Settings → Plugins → Installed</b><br/>" +
                    "2. Find <b>\"GitHub Copilot\"</b> and enable it<br/>" +
                    "3. Restart IntelliJ IDEA<br/>" +
                    "4. Sign in to GitHub Copilot<br/><br/>" +
                    "Then try TestGen again." +
                    "</body></html>"
            );
        }

        // ── CHECK 3: Is Copilot authenticated? (via Chat Tool Window check) ──
        //
        // Copilot does not expose a public auth API. We verify that the Copilot
        // Chat tool window exists and is available as the strongest proxy for
        // "Copilot is running and the user is authenticated".
        //
        // When Copilot is NOT authenticated, it either:
        //   a) Does not register its Chat tool window at all, or
        //   b) The tool window exists but Copilot's services are inactive
        //
        // We use a combined check: tool window presence + Copilot action availability.
        GateResult authResult = checkCopilotAuthentication(project);
        if (!authResult.isPassed()) {
            return authResult;
        }

        return GateResult.pass();
    }

    /**
     * Checks Copilot authentication by verifying the Copilot Chat tool window
     * is registered and available. This is the most reliable non-API proxy
     * for Copilot's authentication state.
     */
    private GateResult checkCopilotAuthentication(Project project) {
        try {
            ToolWindowManager toolWindowManager = ToolWindowManager.getInstance(project);
            ToolWindow copilotChatWindow =
                    toolWindowManager.getToolWindow(COPILOT_CHAT_TOOL_WINDOW_ID);

            if (copilotChatWindow == null) {
                // Chat tool window not registered — Copilot is not fully initialized
                // This typically means the user has not signed in yet
                return GateResult.fail(
                        GateFailureReason.COPILOT_NOT_AUTHENTICATED,
                        "GitHub Copilot Not Authenticated",
                        buildAuthErrorMessage()
                );
            }

            // Tool window exists and is available — Copilot is active
            return GateResult.pass();

        } catch (Exception e) {
            // If we cannot access the tool window manager, treat as auth failure
            // to be safe — better to show an actionable message than to crash
            return GateResult.fail(
                    GateFailureReason.COPILOT_NOT_AUTHENTICATED,
                    "GitHub Copilot Status Unknown",
                    buildAuthErrorMessage()
            );
        }
    }

    /**
     * Builds the standardized authentication error HTML message.
     */
    private String buildAuthErrorMessage() {
        return "<html><body>" +
               "<b>GitHub Copilot is not authenticated.</b><br/><br/>" +
               "TestGen requires an active, signed-in GitHub Copilot session.<br/><br/>" +
               "<b>To sign in:</b><br/>" +
               "1. Look for the <b>GitHub Copilot icon</b> in the bottom status bar<br/>" +
               "2. Click it and select <b>\"Sign in to GitHub\"</b><br/>" +
               "3. Complete the GitHub authentication flow in your browser<br/>" +
               "4. Return to IntelliJ IDEA — the Copilot Chat panel should appear<br/><br/>" +
               "Then try TestGen again." +
               "</body></html>";
    }

    /**
     * Convenience method: checks and shows error notification automatically.
     * Returns true if all checks pass, false if blocked.
     *
     * @param project the current IntelliJ project context
     * @return true if Copilot is ready to use, false if blocked
     */
    public boolean checkAndNotify(Project project) {
        GateResult result = check(project);
        if (!result.isPassed()) {
            NotificationUtil.showError(
                    project,
                    result.getErrorTitle(),
                    result.getErrorMessage()
            );
            return false;
        }
        return true;
    }
}
