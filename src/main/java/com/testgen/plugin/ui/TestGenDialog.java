package com.testgen.plugin.ui;

import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.ValidationInfo;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.JBTextArea;
import com.intellij.ui.components.JBTextField;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import com.testgen.plugin.parser.JavaClassContext;
import com.testgen.plugin.scanner.ProjectDependencyContext;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import java.awt.*;

/**
 * TestGenDialog
 *
 * The main input dialog for TestGen.
 * Extends IntelliJ's DialogWrapper for proper IDE integration.
 *
 * UI layout:
 *   ┌──────────────────────────────────────────────────────┐
 *   │  🧪 TestGen — Generate JUnit Test                    │
 *   ├──────────────────────────────────────────────────────┤
 *   │  Detected Framework:  [JUnit 5 + Mockito] ✅         │
 *   │                                                      │
 *   │  Source Class:  [com.example.UserService] (readonly) │
 *   │                                                      │
 *   │  Test Package:  [com.example.service          ]      │
 *   │                                                      │
 *   │  Test Class Name:  [UserServiceTest           ]      │
 *   │                                                      │
 *   │  Additional Context:  (optional)                     │
 *   │  ┌────────────────────────────────────────────────┐  │
 *   │  │                                                │  │
 *   │  └────────────────────────────────────────────────┘  │
 *   │                                                      │
 *   │              [Cancel]      [Generate →]              │
 *   └──────────────────────────────────────────────────────┘
 */
public class TestGenDialog extends DialogWrapper {

    // ── UI Components ────────────────────────────────────────────────────────
    private JBTextField testPackageField;
    private JBTextField testClassNameField;
    private JBTextArea descriptionArea;
    private JLabel frameworkBadgeLabel;
    private JLabel sourceClassLabel;

    // ── Input data ────────────────────────────────────────────────────────────
    private final JavaClassContext classContext;
    private final ProjectDependencyContext dependencyContext;

    // ── Output model ──────────────────────────────────────────────────────────
    private TestGenDialogModel resultModel;

    /**
     * Constructs the TestGen dialog.
     *
     * @param classContext      the parsed source Java class
     * @param dependencyContext detected project dependencies
     */
    public TestGenDialog(JavaClassContext classContext,
                         ProjectDependencyContext dependencyContext) {
        super(true); // true = modal
        this.classContext = classContext;
        this.dependencyContext = dependencyContext;

        setTitle("TestGen — Generate JUnit Test");
        setOKButtonText("Generate →");
        setCancelButtonText("Cancel");
        setResizable(true);

        init(); // Must be called after all fields are initialized
    }

    @Override
    protected @Nullable JComponent createCenterPanel() {
        JPanel mainPanel = new JPanel(new GridBagLayout());
        mainPanel.setPreferredSize(new Dimension(520, 380));
        mainPanel.setBorder(JBUI.Borders.empty(8, 12, 8, 12));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        gbc.insets = JBUI.insets(4, 0, 4, 0);

        int row = 0;

        // ── Framework Badge ───────────────────────────────────────────────────
        gbc.gridy = row++;
        gbc.gridx = 0;
        gbc.gridwidth = 2;
        mainPanel.add(buildFrameworkBadgePanel(), gbc);

        // ── Separator ─────────────────────────────────────────────────────────
        gbc.gridy = row++;
        gbc.insets = JBUI.insets(6, 0, 6, 0);
        mainPanel.add(new JSeparator(), gbc);
        gbc.insets = JBUI.insets(4, 0, 4, 0);

        // ── Source Class (read-only) ───────────────────────────────────────────
        gbc.gridy = row++;
        gbc.gridwidth = 1;
        gbc.gridx = 0;
        gbc.weightx = 0.35;
        mainPanel.add(buildLabel("Source Class:", false), gbc);

        gbc.gridx = 1;
        gbc.weightx = 0.65;
        sourceClassLabel = new JLabel(classContext.getFullyQualifiedClassName());
        sourceClassLabel.setFont(sourceClassLabel.getFont().deriveFont(Font.BOLD));
        sourceClassLabel.setForeground(UIUtil.getContextHelpForeground());
        mainPanel.add(sourceClassLabel, gbc);

        // ── Test Package ──────────────────────────────────────────────────────
        gbc.gridy = row++;
        gbc.gridx = 0;
        gbc.weightx = 0.35;
        mainPanel.add(buildLabel("Test Package: *", false), gbc);

        gbc.gridx = 1;
        gbc.weightx = 0.65;
        testPackageField = new JBTextField();
        testPackageField.setText(deriveTestPackage());
        testPackageField.setToolTipText("The package for the generated test class (e.g. com.example.service)");
        mainPanel.add(testPackageField, gbc);

        // ── Test Class Name ───────────────────────────────────────────────────
        gbc.gridy = row++;
        gbc.gridx = 0;
        gbc.weightx = 0.35;
        mainPanel.add(buildLabel("Test Class Name: *", false), gbc);

        gbc.gridx = 1;
        gbc.weightx = 0.65;
        testClassNameField = new JBTextField();
        testClassNameField.setText(deriveTestClassName());
        testClassNameField.setToolTipText("The name for the generated test class (e.g. UserServiceTest)");
        mainPanel.add(testClassNameField, gbc);

        // ── Separator before description ──────────────────────────────────────
        gbc.gridy = row++;
        gbc.gridwidth = 2;
        gbc.gridx = 0;
        gbc.insets = JBUI.insets(8, 0, 2, 0);
        mainPanel.add(new JSeparator(), gbc);
        gbc.insets = JBUI.insets(4, 0, 4, 0);

        // ── Description label ─────────────────────────────────────────────────
        gbc.gridy = row++;
        gbc.gridwidth = 2;
        gbc.gridx = 0;
        JLabel descLabel = buildLabel("Additional Context  (optional):", true);
        mainPanel.add(descLabel, gbc);

        // ── Description hint ──────────────────────────────────────────────────
        gbc.gridy = row++;
        JLabel descHint = new JLabel(
                "<html><i style='color:gray'>Describe what this class does, what edge cases to focus on, " +
                "or any special testing requirements...</i></html>"
        );
        descHint.setFont(descHint.getFont().deriveFont(Font.ITALIC, 11f));
        mainPanel.add(descHint, gbc);

        // ── Description text area ─────────────────────────────────────────────
        gbc.gridy = row++;
        gbc.weighty = 1.0;
        gbc.fill = GridBagConstraints.BOTH;
        descriptionArea = new JBTextArea(4, 40);
        descriptionArea.setLineWrap(true);
        descriptionArea.setWrapStyleWord(true);
        descriptionArea.setBorder(new CompoundBorder(
                new LineBorder(JBColor.border(), 1, true),
                new EmptyBorder(4, 6, 4, 6)
        ));
        descriptionArea.setToolTipText(
                "Optional: describe the class purpose, expected behaviors, edge cases to test, etc."
        );
        JBScrollPane scrollPane = new JBScrollPane(descriptionArea);
        scrollPane.setMinimumSize(new Dimension(400, 80));
        mainPanel.add(scrollPane, gbc);

        // ── Footer note ───────────────────────────────────────────────────────
        gbc.gridy = row++;
        gbc.weighty = 0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = JBUI.insets(8, 0, 0, 0);
        JLabel footerNote = new JLabel(
                "<html><small>* TestGen will scaffold the test file and inject a structured " +
                "prompt into GitHub Copilot Chat. Copy the generated code into the pre-created file.</small></html>"
        );
        footerNote.setForeground(UIUtil.getContextHelpForeground());
        mainPanel.add(footerNote, gbc);

        return mainPanel;
    }

    /**
     * Builds the colored framework detection badge panel.
     */
    private JPanel buildFrameworkBadgePanel() {
        JPanel badgePanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        badgePanel.setOpaque(false);

        JLabel icon = new JLabel("✅ ");
        icon.setFont(icon.getFont().deriveFont(12f));

        frameworkBadgeLabel = new JLabel();
        frameworkBadgeLabel.setFont(
                frameworkBadgeLabel.getFont().deriveFont(Font.BOLD, 12f)
        );

        String detectedText = dependencyContext.toDisplayString();
        frameworkBadgeLabel.setText(detectedText);

        // Color the badge based on detection confidence
        if (detectedText.contains("default")) {
            // Unknown / defaulted — show in amber
            frameworkBadgeLabel.setForeground(new JBColor(new Color(180, 120, 0), new Color(220, 180, 60)));
            icon.setText("⚠️  ");
        } else {
            // Successfully detected — show in green
            frameworkBadgeLabel.setForeground(new JBColor(new Color(0, 140, 60), new Color(50, 200, 100)));
        }

        JLabel label = new JLabel("Detected Framework:  ");
        label.setFont(label.getFont().deriveFont(Font.PLAIN, 12f));

        badgePanel.add(label);
        badgePanel.add(icon);
        badgePanel.add(frameworkBadgeLabel);

        // Wrap in a subtle bordered panel
        JPanel wrapper = new JPanel(new BorderLayout());
        wrapper.setBorder(new CompoundBorder(
                new LineBorder(JBColor.border(), 1, true),
                new EmptyBorder(6, 10, 6, 10)
        ));
        wrapper.add(badgePanel, BorderLayout.CENTER);

        return wrapper;
    }

    /**
     * Builds a styled label, optionally bold.
     */
    private JLabel buildLabel(String text, boolean bold) {
        JLabel label = new JBLabel(text);
        if (bold) {
            label.setFont(label.getFont().deriveFont(Font.BOLD));
        }
        label.setBorder(JBUI.Borders.emptyRight(8));
        return label;
    }

    // ────────────────────────────────────────────────────────────────────────
    // Validation
    // ────────────────────────────────────────────────────────────────────────

    @Override
    protected @Nullable ValidationInfo doValidate() {
        String packageVal = testPackageField.getText().trim();
        String classNameVal = testClassNameField.getText().trim();

        if (packageVal.isEmpty()) {
            return new ValidationInfo("Test package is required.", testPackageField);
        }

        if (!isValidJavaPackage(packageVal)) {
            return new ValidationInfo(
                    "Invalid package name. Use lowercase letters, digits, and dots (e.g. com.example.service).",
                    testPackageField
            );
        }

        if (classNameVal.isEmpty()) {
            return new ValidationInfo("Test class name is required.", testClassNameField);
        }

        if (!isValidJavaClassName(classNameVal)) {
            return new ValidationInfo(
                    "Invalid class name. Must start with an uppercase letter and contain only letters, digits, and underscores.",
                    testClassNameField
            );
        }

        return null; // validation passed
    }

    // ────────────────────────────────────────────────────────────────────────
    // OK action
    // ────────────────────────────────────────────────────────────────────────

    @Override
    protected void doOKAction() {
        resultModel = new TestGenDialogModel(
                testPackageField.getText().trim(),
                testClassNameField.getText().trim(),
                descriptionArea.getText().trim()
        );
        super.doOKAction();
    }

    // ────────────────────────────────────────────────────────────────────────
    // Pre-population helpers
    // ────────────────────────────────────────────────────────────────────────

    /**
     * Derives a sensible default test package from the source class package.
     * Mirrors the source package (standard Maven/Gradle convention).
     */
    private String deriveTestPackage() {
        String sourcePackage = classContext.getPackageName();
        if (sourcePackage == null || sourcePackage.isEmpty()) {
            return "";
        }
        return sourcePackage;
    }

    /**
     * Derives a sensible default test class name: {SourceClassName}Test
     */
    private String deriveTestClassName() {
        String sourceName = classContext.getSimpleClassName();
        if (sourceName == null || sourceName.isEmpty()) {
            return "Test";
        }
        // Remove "Impl" suffix if present before adding "Test"
        // e.g. UserServiceImpl → UserServiceTest (not UserServiceImplTest)
        if (sourceName.endsWith("Impl")) {
            return sourceName.substring(0, sourceName.length() - 4) + "Test";
        }
        return sourceName + "Test";
    }

    // ────────────────────────────────────────────────────────────────────────
    // Validation helpers
    // ────────────────────────────────────────────────────────────────────────

    private boolean isValidJavaPackage(String pkg) {
        return pkg.matches("[a-z][a-z0-9]*(\\.[a-z][a-z0-9]*)*");
    }

    private boolean isValidJavaClassName(String name) {
        return name.matches("[A-Z][a-zA-Z0-9_]*");
    }

    // ────────────────────────────────────────────────────────────────────────
    // Public accessor
    // ────────────────────────────────────────────────────────────────────────

    /**
     * Returns the dialog result model after the user clicks "Generate".
     * Returns null if the user cancelled.
     */
    public @Nullable TestGenDialogModel getResultModel() {
        return resultModel;
    }
}
