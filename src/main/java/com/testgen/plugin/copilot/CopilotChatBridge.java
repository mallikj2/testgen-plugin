package com.testgen.plugin.copilot;

import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import com.testgen.plugin.gate.CopilotGateKeeper;
import com.testgen.plugin.util.NotificationUtil;

import java.awt.*;
import java.awt.datatransfer.StringSelection;

/**
 * CopilotChatBridge
 *
 * Responsible for injecting the generated prompt into GitHub Copilot Chat.
 *
 * Strategy: Option B — Gate + Prompt Injection
 *   1. Activates the Copilot Chat tool window
 *   2. Attempts to programmatically inject the prompt via Copilot's
 *      registered actions
 *   3. Falls back to clipboard injection with clear user instructions
 *      if direct injection is not possible
 *
 * Important architectural note:
 *   GitHub Copilot (com.github.copilot) does NOT expose a public Java API
 *   for other plugins. We interact with it through:
 *     a) IntelliJ's ActionManager to invoke Copilot's own registered actions
 *     b) The Tool Window API to show/focus the Chat panel
 *     c) Robot-based UI interaction as a last resort
 *
 * This class isolates ALL Copilot interaction in one place, so if Copilot's
 * internal action IDs change, only this class needs updating.
 */
public class CopilotChatBridge {

    private static final Logger LOG = Logger.getInstance(CopilotChatBridge.class);

    /**
     * Known action IDs used by the GitHub Copilot plugin.
     * These are discovered by inspecting the Copilot plugin's action registrations.
     * They may change across Copilot plugin versions.
     */
    private static final String[] COPILOT_OPEN_CHAT_ACTION_IDS = {
            "copilot.openChat",
            "github.copilot.openChat",
            "GitHubCopilot.OpenChat",
            "copilot.chat.open",
            "com.github.copilot.chat.openChat"
    };

    /**
     * Known action IDs for submitting text to Copilot Chat.
     */
    private static final String[] COPILOT_SUBMIT_CHAT_ACTION_IDS = {
            "copilot.chat.submit",
            "github.copilot.chat.submit",
            "GitHubCopilot.Chat.Submit"
    };

    /**
     * Injects the given prompt into GitHub Copilot Chat.
     *
     * Steps:
     *   1. Show/focus the Copilot Chat tool window
     *   2. Attempt direct action-based injection
     *   3. Fall back to clipboard + user notification
     *
     * @param project the current IntelliJ project
     * @param prompt  the complete prompt string to inject
     * @return true if injection was successful (or clipboard fallback used)
     */
    public boolean injectPrompt(Project project, String prompt) {
        // Step 1: Ensure Copilot Chat tool window is visible
        boolean chatWindowOpened = openCopilotChatWindow(project);

        if (!chatWindowOpened) {
            LOG.warn("TestGen: Could not open Copilot Chat window. Using clipboard fallback.");
        }

        // Step 2: Try direct action injection
        boolean injected = tryDirectActionInjection(project, prompt);

        if (injected) {
            LOG.info("TestGen: Prompt injected via direct action.");
            showSuccessNotification(project);
            return true;
        }

        // Step 3: Clipboard fallback — always reliable
        LOG.info("TestGen: Using clipboard fallback for prompt injection.");
        return clipboardFallback(project, prompt);
    }

    // ────────────────────────────────────────────────────────────────────────
    // Tool window management
    // ────────────────────────────────────────────────────────────────────────

    /**
     * Opens and focuses the GitHub Copilot Chat tool window.
     * Returns true if the window was found and activated.
     */
    private boolean openCopilotChatWindow(Project project) {
        // First try: open via registered Copilot action
        for (String actionId : COPILOT_OPEN_CHAT_ACTION_IDS) {
            AnAction action = ActionManager.getInstance().getAction(actionId);
            if (action != null) {
                try {
                    ApplicationManager.getApplication().invokeLater(() -> {
                        AnActionEvent event = AnActionEvent.createFromAnAction(
                                action,
                                null,
                                "TestGen",
                                DataContext.EMPTY_CONTEXT
                        );
                        action.actionPerformed(event);
                    });
                    LOG.info("TestGen: Opened Copilot Chat via action: " + actionId);
                    return true;
                } catch (Exception e) {
                    LOG.warn("TestGen: Action " + actionId + " failed: " + e.getMessage());
                }
            }
        }

        // Second try: activate via ToolWindowManager
        try {
            ToolWindowManager twm = ToolWindowManager.getInstance(project);
            ToolWindow copilotWindow = twm.getToolWindow(
                    CopilotGateKeeper.COPILOT_CHAT_TOOL_WINDOW_ID
            );

            if (copilotWindow != null) {
                ApplicationManager.getApplication().invokeLater(() -> {
                    copilotWindow.show();
                    copilotWindow.activate(null);
                });
                LOG.info("TestGen: Activated Copilot Chat tool window directly.");
                return true;
            }
        } catch (Exception e) {
            LOG.warn("TestGen: Could not activate Copilot tool window: " + e.getMessage());
        }

        return false;
    }

    // ────────────────────────────────────────────────────────────────────────
    // Direct injection attempt
    // ────────────────────────────────────────────────────────────────────────

    /**
     * Attempts to inject the prompt directly via Copilot's registered actions.
     *
     * This tries to find and invoke Copilot Chat's internal submit/send actions.
     * Since Copilot's API is private, we try all known action IDs and use
     * the first one that succeeds.
     */
    private boolean tryDirectActionInjection(Project project, String prompt) {
        for (String actionId : COPILOT_SUBMIT_CHAT_ACTION_IDS) {
            AnAction action = ActionManager.getInstance().getAction(actionId);
            if (action != null) {
                try {
                    // Place prompt in clipboard so the action can pick it up
                    setClipboardContent(prompt);

                    ApplicationManager.getApplication().invokeLater(() -> {
                        AnActionEvent event = AnActionEvent.createFromAnAction(
                                action,
                                null,
                                "TestGen",
                                DataContext.EMPTY_CONTEXT
                        );
                        action.actionPerformed(event);
                    });

                    LOG.info("TestGen: Prompt submitted via action: " + actionId);
                    return true;
                } catch (Exception e) {
                    LOG.warn("TestGen: Submit action " + actionId + " failed: " + e.getMessage());
                }
            }
        }
        return false;
    }

    // ────────────────────────────────────────────────────────────────────────
    // Clipboard fallback
    // ────────────────────────────────────────────────────────────────────────

    /**
     * Fallback strategy: copies the prompt to the clipboard and shows a
     * clear, actionable notification to the user.
     *
     * This is Phase 1's primary delivery mechanism since Copilot's internal
     * APIs are not publicly available. The workflow becomes:
     *   1. TestGen scaffolds the test file and opens it
     *   2. TestGen puts the prompt in the clipboard
     *   3. User pastes into Copilot Chat → sees generated code → copies to file
     */
    private boolean clipboardFallback(Project project, String prompt) {
        try {
            setClipboardContent(prompt);

            // Show a prominent, actionable balloon notification
            NotificationUtil.showInfo(
                    project,
                    "TestGen: Prompt Ready in Clipboard",
                    "<html><body>" +
                    "<b>Your test generation prompt has been copied to the clipboard.</b><br/><br/>" +
                    "<b>Next steps:</b><br/>" +
                    "1. The empty test file has been created and opened in the editor<br/>" +
                    "2. Open the <b>GitHub Copilot Chat</b> panel " +
                    "   (View → Tool Windows → GitHub Copilot Chat)<br/>" +
                    "3. <b>Paste</b> (Ctrl+V / Cmd+V) the prompt into the Copilot Chat input<br/>" +
                    "4. Press <b>Enter</b> to generate the test code<br/>" +
                    "5. Copy the generated code into the pre-created test file<br/>" +
                    "</body></html>"
            );

            LOG.info("TestGen: Prompt copied to clipboard. User notified.");
            return true;

        } catch (Exception e) {
            LOG.error("TestGen: Clipboard fallback failed", e);
            NotificationUtil.showError(
                    project,
                    "TestGen: Clipboard Error",
                    "Could not copy the prompt to clipboard: " + e.getMessage()
            );
            return false;
        }
    }

    // ────────────────────────────────────────────────────────────────────────
    // Utility helpers
    // ────────────────────────────────────────────────────────────────────────

    /**
     * Sets the system clipboard content to the given text.
     * Uses both IntelliJ's CopyPasteManager and the system clipboard for reliability.
     */
    private void setClipboardContent(String text) {
        // IntelliJ's copy-paste manager (preferred — IDE-aware)
        CopyPasteManager.getInstance().setContents(new StringSelection(text));

        // Also set on the system clipboard directly as backup
        try {
            Toolkit.getDefaultToolkit()
                    .getSystemClipboard()
                    .setContents(new StringSelection(text), null);
        } catch (Exception e) {
            LOG.warn("TestGen: System clipboard set failed (IDE clipboard was used): " + e.getMessage());
        }
    }

    /**
     * Shows a success notification when direct injection succeeds.
     */
    private void showSuccessNotification(Project project) {
        NotificationUtil.showInfo(
                project,
                "TestGen: Prompt Sent to Copilot Chat",
                "<html><body>" +
                "<b>The test generation prompt has been sent to GitHub Copilot Chat.</b><br/><br/>" +
                "Review the generated test code in the Copilot Chat panel, then copy it " +
                "into the pre-created test file that has been opened in the editor." +
                "</body></html>"
        );
    }
}
