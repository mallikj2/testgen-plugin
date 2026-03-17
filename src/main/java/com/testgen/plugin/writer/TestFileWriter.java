package com.testgen.plugin.writer;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.SourceFolder;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.testgen.plugin.parser.JavaClassContext;
import com.testgen.plugin.scanner.ProjectDependencyContext;
import com.testgen.plugin.ui.TestGenDialogModel;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * TestFileWriter
 *
 * Creates the scaffolded test Java file in the correct test source root.
 *
 * Responsibilities:
 *   1. Locate or create the test source root (src/test/java/...)
 *   2. Resolve or create the target package directory structure
 *   3. Write a well-formed Java test file with the correct package declaration,
 *      imports scaffold, class declaration, and a TODO comment
 *   4. Open the new file in the editor
 *
 * The generated file is intentionally a scaffold/skeleton.
 * The actual test method bodies will come from Copilot Chat.
 */
public class TestFileWriter {

    private static final Logger LOG = Logger.getInstance(TestFileWriter.class);

    /**
     * Result of the file writing operation.
     */
    public static class WriteResult {
        private final boolean success;
        private final VirtualFile createdFile;
        private final String errorMessage;

        private WriteResult(boolean success, VirtualFile createdFile, String errorMessage) {
            this.success = success;
            this.createdFile = createdFile;
            this.errorMessage = errorMessage;
        }

        public static WriteResult success(VirtualFile file) {
            return new WriteResult(true, file, null);
        }

        public static WriteResult failure(String message) {
            return new WriteResult(false, null, message);
        }

        public boolean isSuccess() { return success; }
        public VirtualFile getCreatedFile() { return createdFile; }
        public String getErrorMessage() { return errorMessage; }
    }

    /**
     * Creates the test file and opens it in the editor.
     *
     * @param project           current IntelliJ project
     * @param classContext      the source class context
     * @param dependencyContext the detected dependencies
     * @param dialogModel       user inputs (package, classname)
     * @return WriteResult indicating success or failure
     */
    public WriteResult createTestFile(Project project,
                                      JavaClassContext classContext,
                                      ProjectDependencyContext dependencyContext,
                                      TestGenDialogModel dialogModel) {

        final WriteResult[] result = {null};

        ApplicationManager.getApplication().invokeAndWait(() -> {
            try {
                result[0] = WriteAction.compute(() ->
                        doCreateTestFile(project, classContext, dependencyContext, dialogModel)
                );
            } catch (Exception e) {
                LOG.error("TestGen: Error creating test file", e);
                result[0] = WriteResult.failure("Failed to create test file: " + e.getMessage());
            }
        });

        // Open the created file in the editor
        if (result[0] != null && result[0].isSuccess()) {
            VirtualFile createdFile = result[0].getCreatedFile();
            ApplicationManager.getApplication().invokeLater(() ->
                    FileEditorManager.getInstance(project).openFile(createdFile, true)
            );
        }

        return result[0] != null ? result[0] :
                WriteResult.failure("Unknown error during file creation.");
    }

    // ────────────────────────────────────────────────────────────────────────
    // Core file creation (runs in write action)
    // ────────────────────────────────────────────────────────────────────────

    private WriteResult doCreateTestFile(Project project,
                                          JavaClassContext classContext,
                                          ProjectDependencyContext dependencyContext,
                                          TestGenDialogModel dialogModel) throws IOException {

        // 1. Find test source root
        VirtualFile testSourceRoot = findOrCreateTestSourceRoot(project);
        if (testSourceRoot == null) {
            return WriteResult.failure(
                    "Could not find or create a test source root (src/test/java). " +
                    "Please create the test directory structure manually and try again."
            );
        }

        // 2. Resolve/create package directory
        String packagePath = dialogModel.getTestPackage().replace('.', '/');
        VirtualFile packageDir = findOrCreateDirectory(testSourceRoot, packagePath);
        if (packageDir == null) {
            return WriteResult.failure(
                    "Could not create package directory: " + dialogModel.getTestPackage()
            );
        }

        // 3. Check if the test file already exists
        String fileName = dialogModel.getTestClassName() + ".java";
        VirtualFile existingFile = packageDir.findChild(fileName);
        if (existingFile != null) {
            LOG.warn("TestGen: Test file already exists: " + existingFile.getPath());
            return WriteResult.failure(
                    "Test file already exists: " + dialogModel.getFullyQualifiedTestClassName() +
                    "\n\nPlease choose a different class name or delete the existing file."
            );
        }

        // 4. Generate the scaffold content
        String fileContent = buildScaffoldContent(classContext, dependencyContext, dialogModel);

        // 5. Create the file
        VirtualFile newFile = packageDir.createChildData(this, fileName);
        newFile.setBinaryContent(fileContent.getBytes(StandardCharsets.UTF_8));

        LOG.info("TestGen: Created test file: " + newFile.getPath());
        return WriteResult.success(newFile);
    }

    // ────────────────────────────────────────────────────────────────────────
    // Test source root resolution
    // ────────────────────────────────────────────────────────────────────────

    /**
     * Finds the test Java source root (src/test/java) in the project.
     * If it doesn't exist, attempts to create it.
     *
     * Strategy:
     *   1. Check IntelliJ's module model for a registered test source root
     *   2. Fall back to looking for src/test/java by path
     *   3. Create src/test/java if it doesn't exist
     */
    private VirtualFile findOrCreateTestSourceRoot(Project project) {
        // Strategy 1: Check module roots via IntelliJ's module model
        ModuleManager moduleManager = ModuleManager.getInstance(project);
        for (Module module : moduleManager.getModules()) {
            ModuleRootManager rootManager = ModuleRootManager.getInstance(module);
            for (SourceFolder sourceFolder : rootManager.getContentEntries()[0].getSourceFolders()) {
                if (sourceFolder.isTestSource()) {
                    VirtualFile root = sourceFolder.getFile();
                    if (root != null && root.exists()) {
                        LOG.info("TestGen: Found test source root via module model: " + root.getPath());
                        return root;
                    }
                }
            }
        }

        // Strategy 2: Look for src/test/java by convention
        String basePath = project.getBasePath();
        if (basePath == null) return null;

        VirtualFile baseDir = VirtualFileManager.getInstance()
                .findFileByUrl("file://" + basePath);
        if (baseDir == null) return null;

        // Standard Maven/Gradle test source path
        String[] testPaths = {
                "src/test/java",
                "src/test/kotlin", // also check Kotlin just in case
                "test/java",
                "test"
        };

        for (String testPath : testPaths) {
            VirtualFile testRoot = findChildByRelativePath(baseDir, testPath);
            if (testRoot != null && testRoot.exists()) {
                LOG.info("TestGen: Found test source root by path: " + testRoot.getPath());
                return testRoot;
            }
        }

        // Strategy 3: Create src/test/java
        try {
            LOG.info("TestGen: Creating test source root: src/test/java");
            VirtualFile srcDir = findOrCreateDirectory(baseDir, "src/test/java");
            return srcDir;
        } catch (Exception e) {
            LOG.error("TestGen: Failed to create src/test/java", e);
            return null;
        }
    }

    /**
     * Finds a child file/directory by a relative path (e.g., "src/test/java").
     */
    private VirtualFile findChildByRelativePath(VirtualFile root, String relativePath) {
        String[] parts = relativePath.split("/");
        VirtualFile current = root;
        for (String part : parts) {
            if (current == null) return null;
            current = current.findChild(part);
        }
        return current;
    }

    /**
     * Finds or creates a directory structure under the given root.
     * Creates all intermediate directories as needed.
     *
     * @param root         the base directory
     * @param relativePath relative path like "com/example/service"
     * @return the target directory VirtualFile, or null on error
     */
    private VirtualFile findOrCreateDirectory(VirtualFile root, String relativePath)
            throws IOException {
        if (relativePath == null || relativePath.isEmpty()) {
            return root;
        }

        String[] parts = relativePath.split("/");
        VirtualFile current = root;

        for (String part : parts) {
            if (part.isEmpty()) continue;
            VirtualFile child = current.findChild(part);
            if (child == null) {
                child = current.createChildDirectory(this, part);
                LOG.info("TestGen: Created directory: " + child.getPath());
            }
            current = child;
        }

        return current;
    }

    // ────────────────────────────────────────────────────────────────────────
    // Scaffold content generation
    // ────────────────────────────────────────────────────────────────────────

    /**
     * Builds the Java source content for the test file scaffold.
     *
     * This creates a compilable but empty test class with:
     *   - Correct package declaration
     *   - Import scaffold based on detected frameworks
     *   - Class declaration with correct annotations
     *   - A clear TODO comment directing the developer to Copilot Chat
     *   - One placeholder test method so the file is immediately runnable
     */
    private String buildScaffoldContent(JavaClassContext classContext,
                                         ProjectDependencyContext depContext,
                                         TestGenDialogModel dialogModel) {
        StringBuilder sb = new StringBuilder();

        // ── Package ───────────────────────────────────────────────────────────
        sb.append("package ").append(dialogModel.getTestPackage()).append(";\n\n");

        // ── Imports ───────────────────────────────────────────────────────────
        sb.append(buildImports(classContext, depContext));
        sb.append("\n");

        // ── Class JavaDoc ─────────────────────────────────────────────────────
        sb.append("/**\n");
        sb.append(" * Unit tests for {@link ")
          .append(classContext.getFullyQualifiedClassName()).append("}.\n");
        sb.append(" *\n");
        sb.append(" * <p>Generated by TestGen — GitHub Copilot assisted test generation.</p>\n");
        sb.append(" *\n");
        sb.append(" * <p><b>TODO:</b> The complete test implementations have been generated in\n");
        sb.append(" * GitHub Copilot Chat. Copy the generated code from the Chat panel\n");
        sb.append(" * and paste it here, replacing this scaffold.</p>\n");
        sb.append(" */\n");

        // ── Class annotations ─────────────────────────────────────────────────
        if (depContext.isJUnit5()) {
            if (depContext.isMockitoAvailable()) {
                sb.append("@ExtendWith(MockitoExtension.class)\n");
            } else if (depContext.isSpringBootTestPresent()) {
                sb.append("@SpringBootTest\n");
            }
        } else {
            // JUnit 4
            if (depContext.isMockitoAvailable()) {
                sb.append("@RunWith(MockitoJUnitRunner.class)\n");
            } else if (depContext.isSpringBootTestPresent()) {
                sb.append("@RunWith(SpringRunner.class)\n");
            }
        }

        // ── Class declaration ─────────────────────────────────────────────────
        sb.append("public class ").append(dialogModel.getTestClassName()).append(" {\n\n");

        // ── Mock fields ───────────────────────────────────────────────────────
        for (JavaClassContext.FieldInfo dep : classContext.getInjectedDependencies()) {
            sb.append("    @Mock\n");
            sb.append("    private ").append(dep.getType()).append(" ")
              .append(dep.getName()).append(";\n\n");
        }

        // ── InjectMocks (the class under test) ────────────────────────────────
        if (!classContext.getInjectedDependencies().isEmpty() && depContext.isMockitoAvailable()) {
            sb.append("    @InjectMocks\n");
            sb.append("    private ").append(classContext.getSimpleClassName()).append(" ")
              .append(decapitalize(classContext.getSimpleClassName())).append(";\n\n");
        } else {
            // No Mockito injection — plain instantiation placeholder
            sb.append("    // TODO: Initialize the class under test\n");
            sb.append("    // private ").append(classContext.getSimpleClassName()).append(" ")
              .append(decapitalize(classContext.getSimpleClassName())).append(";\n\n");
        }

        // ── Placeholder test ──────────────────────────────────────────────────
        sb.append("    // =========================================================\n");
        sb.append("    // TODO: The complete test implementations are in Copilot Chat.\n");
        sb.append("    // Copy the generated code from the Chat panel and replace\n");
        sb.append("    // this placeholder with the full test implementations.\n");
        sb.append("    // =========================================================\n\n");

        if (depContext.isJUnit5()) {
            sb.append("    @Test\n");
            sb.append("    void placeholder_replaceWithCopilotGeneratedTests() {\n");
        } else {
            sb.append("    @Test\n");
            sb.append("    public void placeholder_replaceWithCopilotGeneratedTests() {\n");
        }
        sb.append("        // Replace this method with the tests generated by GitHub Copilot Chat.\n");
        sb.append("        // The prompt has been copied to your clipboard — paste it into Copilot Chat.\n");
        sb.append("    }\n\n");

        sb.append("}\n");

        return sb.toString();
    }

    /**
     * Builds the import block based on detected frameworks.
     */
    private String buildImports(JavaClassContext classContext,
                                 ProjectDependencyContext depContext) {
        StringBuilder imports = new StringBuilder();

        // Source class import
        imports.append("import ").append(classContext.getFullyQualifiedClassName()).append(";\n");
        imports.append("\n");

        // JUnit imports
        if (depContext.isJUnit5()) {
            imports.append("import org.junit.jupiter.api.Test;\n");
            imports.append("import org.junit.jupiter.api.BeforeEach;\n");
            imports.append("import org.junit.jupiter.api.AfterEach;\n");
            imports.append("import org.junit.jupiter.api.DisplayName;\n");
            imports.append("import static org.junit.jupiter.api.Assertions.*;\n");

            if (depContext.isMockitoAvailable()) {
                imports.append("import org.junit.jupiter.api.extension.ExtendWith;\n");
            }
            if (depContext.isSpringBootTestPresent() && !depContext.isMockitoAvailable()) {
                imports.append("import org.junit.jupiter.api.extension.ExtendWith;\n");
                imports.append("import org.springframework.boot.test.context.SpringBootTest;\n");
                imports.append("import org.springframework.test.context.junit.jupiter.SpringExtension;\n");
            }
        } else {
            // JUnit 4
            imports.append("import org.junit.Test;\n");
            imports.append("import org.junit.Before;\n");
            imports.append("import org.junit.After;\n");
            imports.append("import static org.junit.Assert.*;\n");

            if (depContext.isMockitoAvailable()) {
                imports.append("import org.junit.runner.RunWith;\n");
                imports.append("import org.mockito.junit.MockitoJUnitRunner;\n");
            }
            if (depContext.isSpringBootTestPresent()) {
                imports.append("import org.junit.runner.RunWith;\n");
                imports.append("import org.springframework.boot.test.context.SpringBootTest;\n");
                imports.append("import org.springframework.test.context.junit4.SpringRunner;\n");
            }
        }

        imports.append("\n");

        // Mockito imports
        if (depContext.isMockitoAvailable()) {
            imports.append("import org.mockito.Mock;\n");
            imports.append("import org.mockito.InjectMocks;\n");
            imports.append("import org.mockito.Mockito;\n");
            imports.append("import static org.mockito.Mockito.*;\n");
            imports.append("import static org.mockito.ArgumentMatchers.*;\n");
            if (depContext.isJUnit5()) {
                imports.append("import org.mockito.junit.jupiter.MockitoExtension;\n");
            }
            imports.append("\n");
        }

        // AssertJ imports
        if (depContext.isAssertJPresent()) {
            imports.append("import static org.assertj.core.api.Assertions.*;\n");
            imports.append("\n");
        }

        // Spring-specific imports
        if (depContext.isSpringBootTestPresent()) {
            imports.append("import org.springframework.beans.factory.annotation.Autowired;\n");
            imports.append("import org.springframework.boot.test.mock.mockito.MockBean;\n");
            imports.append("\n");
        }

        return imports.toString();
    }

    // ────────────────────────────────────────────────────────────────────────
    // Utility
    // ────────────────────────────────────────────────────────────────────────

    /**
     * Decapitalizes the first character of a string.
     * e.g., "UserService" → "userService"
     */
    private String decapitalize(String name) {
        if (name == null || name.isEmpty()) return name;
        return Character.toLowerCase(name.charAt(0)) + name.substring(1);
    }
}
