package com.testgen.plugin.actions;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiJavaFile;
import com.testgen.plugin.copilot.CopilotChatBridge;
import com.testgen.plugin.gate.CopilotGateKeeper;
import com.testgen.plugin.parser.JavaClassContext;
import com.testgen.plugin.parser.JavaSourceParser;
import com.testgen.plugin.prompt.PromptBuilder;
import com.testgen.plugin.scanner.BuildFileScanner;
import com.testgen.plugin.scanner.GradleDependencyScanner;
import com.testgen.plugin.scanner.MavenDependencyScanner;
import com.testgen.plugin.scanner.ProjectDependencyContext;
import com.testgen.plugin.ui.TestGenDialog;
import com.testgen.plugin.ui.TestGenDialogModel;
import com.testgen.plugin.util.NotificationUtil;
import com.testgen.plugin.util.ProjectUtil;
import com.testgen.plugin.writer.TestFileWriter;
import org.jetbrains.annotations.NotNull;

/**
 * GenerateTestAction
 *
 * The main entry point for the TestGen plugin.
 * Registered in plugin.xml and triggered via:
 *   - Tools menu → TestGen → Generate JUnit Test with Copilot
 *   - Editor right-click → Generate JUnit Test with Copilot
 *   - Project view right-click → Generate JUnit Test with Copilot
 *   - Keyboard shortcut: Alt+Shift+T
 *
 * Execution flow:
 *   1.  Validate current file is a Java file
 *   2.  CopilotGateKeeper — hard block if Copilot not installed/authenticated
 *   3.  JavaSourceParser  — parse the source class via PSI
 *   4.  BuildFileScanner  — detect Maven/Gradle dependencies
 *   5.  TestGenDialog     — collect user input (package, classname, description)
 *   6.  TestFileWriter    — scaffold and create the test file
 *   7.  PromptBuilder     — assemble the Copilot prompt
 *   8.  CopilotChatBridge — inject prompt into Copilot Chat
 */
public class GenerateTestAction extends AnAction {

    private static final Logger LOG = Logger.getInstance(GenerateTestAction.class);

    // Services (instantiated per-action, lightweight)
    private final CopilotGateKeeper gateKeeper = new CopilotGateKeeper();
    private final JavaSourceParser sourceParser = new JavaSourceParser();
    private final MavenDependencyScanner mavenScanner = new MavenDependencyScanner();
    private final GradleDependencyScanner gradleScanner = new GradleDependencyScanner();
    private final PromptBuilder promptBuilder = new PromptBuilder();
    private final CopilotChatBridge copilotBridge = new CopilotChatBridge();
    private final TestFileWriter testFileWriter = new TestFileWriter();

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        if (project == null) {
            LOG.warn("TestGen: No project found.");
            return;
        }

        LOG.info("TestGen: GenerateTestAction triggered.");

        // ── STEP 1: Validate we have a Java file ─────────────────────────────
        PsiJavaFile javaFile = ProjectUtil.getActiveJavaFile(e);
        if (javaFile == null) {
            NotificationUtil.showErrorDialog(
                    project,
                    "No Java File Selected",
                    "<html><body>" +
                    "<b>TestGen requires a Java source file to be active.</b><br/><br/>" +
                    "Please open or select a Java class file and try again.<br/><br/>" +
                    "<i>Note: TestGen only supports Java source files (.java).</i>" +
                    "</body></html>"
            );
            return;
        }

        // Guard: don't generate tests for test files
        if (!ProjectUtil.isJavaSourceFile(javaFile)) {
            NotificationUtil.showErrorDialog(
                    project,
                    "Test File Detected",
                    "<html><body>" +
                    "<b>The selected file appears to be a test file.</b><br/><br/>" +
                    "TestGen generates tests <i>for</i> source classes, not for existing test files.<br/><br/>" +
                    "Please select a main source class (in <code>src/main/java</code>) and try again." +
                    "</body></html>"
            );
            return;
        }

        // ── STEP 2: Copilot Gate Check ────────────────────────────────────────
        // Hard block — if this fails, we stop immediately with a detailed error.
        boolean copilotReady = gateKeeper.checkAndNotify(project);
        if (!copilotReady) {
            // Error dialog already shown by gateKeeper.checkAndNotify()
            LOG.info("TestGen: Blocked by CopilotGateKeeper.");
            return;
        }

        LOG.info("TestGen: Copilot gate passed. Proceeding.");

        // ── STEP 3: Parse the source class ────────────────────────────────────
        JavaClassContext classContext = sourceParser.parseFromPsiFile(javaFile);
        if (classContext == null || classContext.getSimpleClassName() == null) {
            NotificationUtil.showErrorDialog(
                    project,
                    "Could Not Parse Java Class",
                    "<html><body>" +
                    "<b>TestGen could not parse the selected Java file.</b><br/><br/>" +
                    "Please ensure the file contains a valid Java class and try again." +
                    "</body></html>"
            );
            return;
        }

        LOG.info("TestGen: Parsed class: " + classContext.getFullyQualifiedClassName());

        // ── STEP 4: Scan build file for dependencies ──────────────────────────
        ProjectDependencyContext dependencyContext = scanDependencies(project);
        LOG.info("TestGen: Dependency scan complete → " + dependencyContext.toDisplayString());

        // ── STEP 5: Show the TestGen dialog ───────────────────────────────────
        // This is on the EDT (Event Dispatch Thread) — DialogWrapper requires it.
        TestGenDialog dialog = new TestGenDialog(classContext, dependencyContext);
        boolean dialogOk = dialog.showAndGet();

        if (!dialogOk) {
            // User cancelled
            LOG.info("TestGen: User cancelled the dialog.");
            return;
        }

        TestGenDialogModel dialogModel = dialog.getResultModel();
        if (dialogModel == null || !dialogModel.isValid()) {
            LOG.warn("TestGen: Dialog returned null or invalid model.");
            return;
        }

        LOG.info("TestGen: Dialog confirmed → " + dialogModel);

        // ── STEPS 6-8: Run in background task ────────────────────────────────
        // File creation and prompt injection are quick, but we use a background
        // task for good UX (shows progress indicator, keeps EDT free).
        final JavaClassContext finalClassContext = classContext;
        final ProjectDependencyContext finalDepContext = dependencyContext;
        final TestGenDialogModel finalDialogModel = dialogModel;

        ProgressManager.getInstance().run(
                new Task.Backgroundable(project, "TestGen: Generating test scaffold...", false) {
                    @Override
                    public void run(@NotNull ProgressIndicator indicator) {
                        executeGeneration(
                                project,
                                finalClassContext,
                                finalDepContext,
                                finalDialogModel,
                                indicator
                        );
                    }
                }
        );
    }

    /**
     * Executes steps 6–8 in a background task:
     *   6. Create the test file scaffold
     *   7. Build the Copilot prompt
     *   8. Inject the prompt into Copilot Chat
     */
    private void executeGeneration(Project project,
                                   JavaClassContext classContext,
                                   ProjectDependencyContext dependencyContext,
                                   TestGenDialogModel dialogModel,
                                   ProgressIndicator indicator) {

        // ── STEP 6: Create test file scaffold ────────────────────────────────
        indicator.setText("Creating test file scaffold...");
        indicator.setFraction(0.2);

        TestFileWriter.WriteResult writeResult = testFileWriter.createTestFile(
                project, classContext, dependencyContext, dialogModel
        );

        if (!writeResult.isSuccess()) {
            NotificationUtil.showErrorDialog(
                    project,
                    "Test File Creation Failed",
                    "<html><body>" +
                    "<b>TestGen could not create the test file.</b><br/><br/>" +
                    writeResult.getErrorMessage() +
                    "</body></html>"
            );
            return;
        }

        LOG.info("TestGen: Test file created: " + writeResult.getCreatedFile().getPath());

        // ── STEP 7: Build the Copilot prompt ─────────────────────────────────
        indicator.setText("Building Copilot prompt...");
        indicator.setFraction(0.6);

        String prompt = promptBuilder.build(classContext, dependencyContext, dialogModel);

        if (prompt == null || prompt.isEmpty()) {
            NotificationUtil.showErrorDialog(
                    project,
                    "Prompt Build Failed",
                    "TestGen could not assemble the Copilot prompt. Please try again."
            );
            return;
        }

        LOG.info("TestGen: Prompt built, length = " + prompt.length() + " chars.");

        // ── STEP 8: Inject into Copilot Chat ─────────────────────────────────
        indicator.setText("Injecting prompt into Copilot Chat...");
        indicator.setFraction(0.9);

        boolean injected = copilotBridge.injectPrompt(project, prompt);

        indicator.setFraction(1.0);

        if (!injected) {
            NotificationUtil.showWarning(
                    project,
                    "TestGen: Prompt Injection Notice",
                    "<html><body>" +
                    "The test file was created successfully, but the prompt could not be " +
                    "automatically sent to Copilot Chat.<br/><br/>" +
                    "The prompt has been copied to your clipboard. " +
                    "Please paste it manually into the GitHub Copilot Chat panel." +
                    "</body></html>"
            );
        }

        LOG.info("TestGen: Generation complete for: " +
                dialogModel.getFullyQualifiedTestClassName());
    }

    /**
     * Scans the project for build file dependencies.
     * Maven takes priority over Gradle when both are present.
     */
    private ProjectDependencyContext scanDependencies(Project project) {
        // Maven first (pom.xml)
        if (mavenScanner.isApplicable(project)) {
            LOG.info("TestGen: Using Maven dependency scanner.");
            return mavenScanner.scan(project);
        }

        // Gradle second (build.gradle / build.gradle.kts)
        if (gradleScanner.isApplicable(project)) {
            LOG.info("TestGen: Using Gradle dependency scanner.");
            return gradleScanner.scan(project);
        }

        // No build file found — use defaults (JUnit 5)
        LOG.warn("TestGen: No pom.xml or build.gradle found. Using default dependency context.");
        ProjectDependencyContext defaultContext = new ProjectDependencyContext();
        defaultContext.setBuildSystem(ProjectDependencyContext.BuildSystem.UNKNOWN);
        defaultContext.setJunitVersion(ProjectDependencyContext.JUnitVersion.JUNIT5);
        return defaultContext;
    }

    /**
     * Controls when the action is visible and enabled.
     * The action is enabled only when a Java file is active.
     */
    @Override
    public void update(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        boolean enabled = project != null && ProjectUtil.getActiveJavaFile(e) != null;
        e.getPresentation().setEnabledAndVisible(enabled);
    }
}
