package com.testgen.plugin.parser;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * JavaSourceParser
 *
 * Uses IntelliJ's PSI (Program Structure Interface) API to deeply analyze
 * a Java source file and extract all relevant context needed for test generation.
 *
 * PSI is IntelliJ's internal representation of source code as a structured tree,
 * giving us reliable, IDE-level access to class structure without regex parsing.
 */
public class JavaSourceParser {

    private static final Logger LOG = Logger.getInstance(JavaSourceParser.class);

    /**
     * Parses the Java file currently open/selected in the editor.
     *
     * @param project current IntelliJ project
     * @param editor  current active editor
     * @return parsed JavaClassContext, or null if the file is not a valid Java class
     */
    public JavaClassContext parseFromEditor(Project project, Editor editor) {
        if (editor == null) {
            LOG.warn("TestGen: No active editor found.");
            return null;
        }

        PsiFile psiFile = PsiDocumentManager.getInstance(project)
                .getPsiFile(editor.getDocument());

        return parseFromPsiFile(psiFile);
    }

    /**
     * Parses a Java file from a VirtualFile reference.
     *
     * @param project     current IntelliJ project
     * @param virtualFile the virtual file to parse
     * @return parsed JavaClassContext, or null if not a valid Java class
     */
    public JavaClassContext parseFromVirtualFile(Project project, VirtualFile virtualFile) {
        if (virtualFile == null) {
            return null;
        }

        PsiFile psiFile = PsiManager.getInstance(project).findFile(virtualFile);
        return parseFromPsiFile(psiFile);
    }

    /**
     * Core parsing logic — works on a PsiFile instance.
     */
    public JavaClassContext parseFromPsiFile(PsiFile psiFile) {
        if (!(psiFile instanceof PsiJavaFile)) {
            LOG.info("TestGen: File is not a Java file, skipping.");
            return null;
        }

        PsiJavaFile javaFile = (PsiJavaFile) psiFile;
        PsiClass[] classes = javaFile.getClasses();

        if (classes.length == 0) {
            LOG.warn("TestGen: No classes found in file: " + psiFile.getName());
            return null;
        }

        // Use the first top-level class (primary class of the file)
        PsiClass primaryClass = classes[0];

        JavaClassContext context = new JavaClassContext();

        // ── Package ──────────────────────────────────────────────────────────
        String packageName = javaFile.getPackageName();
        context.setPackageName(packageName);

        // ── Class name ───────────────────────────────────────────────────────
        String simpleName = primaryClass.getName();
        context.setSimpleClassName(simpleName);
        context.setFullyQualifiedClassName(
                packageName != null && !packageName.isEmpty()
                        ? packageName + "." + simpleName
                        : simpleName
        );

        // ── Class type flags ─────────────────────────────────────────────────
        context.setInterfaceType(primaryClass.isInterface());
        context.setAbstractClass(primaryClass.hasModifierProperty(PsiModifier.ABSTRACT)
                && !primaryClass.isInterface());

        // ── Superclass ───────────────────────────────────────────────────────
        PsiClass superClass = primaryClass.getSuperClass();
        if (superClass != null && !"Object".equals(superClass.getName())) {
            context.setSuperClassName(superClass.getName());
        }

        // ── Implemented interfaces ────────────────────────────────────────────
        PsiClassType[] interfaces = primaryClass.getImplementsListTypes();
        List<String> interfaceNames = Arrays.stream(interfaces)
                .map(PsiClassType::getPresentableText)
                .collect(Collectors.toList());
        context.setImplementedInterfaces(interfaceNames);

        // ── Class annotations ────────────────────────────────────────────────
        List<String> classAnnotations = extractAnnotations(primaryClass.getAnnotations());
        context.setClassAnnotations(classAnnotations);

        // ── Fields ───────────────────────────────────────────────────────────
        List<JavaClassContext.FieldInfo> fields = extractFields(primaryClass);
        context.setFields(fields);

        // ── Methods ──────────────────────────────────────────────────────────
        List<JavaClassContext.MethodInfo> methods = extractMethods(primaryClass);
        context.setMethods(methods);

        // ── Imports ──────────────────────────────────────────────────────────
        List<String> imports = extractImports(javaFile);
        context.setImports(imports);

        // ── Inner classes ────────────────────────────────────────────────────
        List<String> innerClassNames = Arrays.stream(primaryClass.getInnerClasses())
                .map(PsiClass::getName)
                .collect(Collectors.toList());
        context.setInnerClassNames(innerClassNames);

        // ── Full source code ─────────────────────────────────────────────────
        // We include the full source for maximum Copilot context
        String sourceCode = psiFile.getText();
        // Trim if extremely long (> 8000 chars) to avoid token limits
        if (sourceCode != null && sourceCode.length() > 8000) {
            sourceCode = sourceCode.substring(0, 8000) + "\n// ... (truncated for brevity)";
        }
        context.setFullSourceCode(sourceCode);

        LOG.info(String.format("TestGen: Parsed class '%s' — %d methods, %d fields",
                context.getFullyQualifiedClassName(),
                methods.size(),
                fields.size()));

        return context;
    }

    // ────────────────────────────────────────────────────────────────────────
    // Private extraction helpers
    // ────────────────────────────────────────────────────────────────────────

    /**
     * Extracts all fields from the class, including their annotations.
     */
    private List<JavaClassContext.FieldInfo> extractFields(PsiClass psiClass) {
        List<JavaClassContext.FieldInfo> result = new ArrayList<>();

        for (PsiField field : psiClass.getFields()) {
            // Skip synthetic/enum fields
            if (field instanceof PsiEnumConstant) continue;

            String name = field.getName();
            String type = field.getType().getPresentableText();
            List<String> annotations = extractAnnotations(field.getAnnotations());

            boolean isPrivate = field.hasModifierProperty(PsiModifier.PRIVATE);
            boolean isStatic = field.hasModifierProperty(PsiModifier.STATIC);
            boolean isFinal = field.hasModifierProperty(PsiModifier.FINAL);

            result.add(new JavaClassContext.FieldInfo(
                    name, type, annotations, isPrivate, isStatic, isFinal
            ));
        }

        return result;
    }

    /**
     * Extracts all methods from the class, including constructors.
     */
    private List<JavaClassContext.MethodInfo> extractMethods(PsiClass psiClass) {
        List<JavaClassContext.MethodInfo> result = new ArrayList<>();

        for (PsiMethod method : psiClass.getMethods()) {
            String name = method.getName();
            String returnType = method.isConstructor()
                    ? "<constructor>"
                    : (method.getReturnType() != null
                            ? method.getReturnType().getPresentableText()
                            : "void");

            List<JavaClassContext.ParameterInfo> parameters =
                    extractParameters(method.getParameterList());

            List<String> annotations = extractAnnotations(method.getAnnotations());

            // Thrown exceptions
            List<String> thrownExceptions = new ArrayList<>();
            PsiReferenceList throwsList = method.getThrowsList();
            for (PsiClassType exception : throwsList.getReferencedTypes()) {
                thrownExceptions.add(exception.getPresentableText());
            }

            boolean isPublic = method.hasModifierProperty(PsiModifier.PUBLIC);
            boolean isStatic = method.hasModifierProperty(PsiModifier.STATIC);
            boolean isAbstract = method.hasModifierProperty(PsiModifier.ABSTRACT);

            String accessModifier = "package-private";
            if (method.hasModifierProperty(PsiModifier.PUBLIC)) accessModifier = "public";
            else if (method.hasModifierProperty(PsiModifier.PROTECTED)) accessModifier = "protected";
            else if (method.hasModifierProperty(PsiModifier.PRIVATE)) accessModifier = "private";

            result.add(new JavaClassContext.MethodInfo(
                    name, returnType, parameters, annotations,
                    thrownExceptions, isPublic, isStatic, isAbstract, accessModifier
            ));
        }

        return result;
    }

    /**
     * Extracts parameters from a method's parameter list.
     */
    private List<JavaClassContext.ParameterInfo> extractParameters(
            PsiParameterList parameterList) {
        List<JavaClassContext.ParameterInfo> result = new ArrayList<>();

        for (PsiParameter param : parameterList.getParameters()) {
            String name = param.getName();
            String type = param.getType().getPresentableText();
            List<String> annotations = extractAnnotations(param.getAnnotations());
            result.add(new JavaClassContext.ParameterInfo(name, type, annotations));
        }

        return result;
    }

    /**
     * Extracts annotation names from an array of PsiAnnotation.
     */
    private List<String> extractAnnotations(PsiAnnotation[] annotations) {
        List<String> result = new ArrayList<>();
        for (PsiAnnotation annotation : annotations) {
            String qualifiedName = annotation.getQualifiedName();
            if (qualifiedName != null) {
                // Use short name for readability in prompts
                int lastDot = qualifiedName.lastIndexOf('.');
                String shortName = lastDot >= 0
                        ? qualifiedName.substring(lastDot + 1)
                        : qualifiedName;
                result.add("@" + shortName);
            }
        }
        return result;
    }

    /**
     * Extracts all import statements from the Java file.
     */
    private List<String> extractImports(PsiJavaFile javaFile) {
        List<String> result = new ArrayList<>();
        PsiImportList importList = javaFile.getImportList();

        if (importList != null) {
            for (PsiImportStatement importStatement : importList.getImportStatements()) {
                String qualifiedName = importStatement.getQualifiedName();
                if (qualifiedName != null) {
                    result.add(qualifiedName);
                }
            }
            // Also handle static imports
            for (PsiImportStaticStatement staticImport : importList.getImportStaticStatements()) {
                String text = staticImport.getText();
                if (text != null) {
                    result.add(text.replace("import static ", "static:"));
                }
            }
        }

        return result;
    }
}
