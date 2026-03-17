package com.testgen.plugin.parser;

import java.util.ArrayList;
import java.util.List;

/**
 * JavaClassContext
 *
 * Data Transfer Object (DTO) that holds all information extracted by
 * JavaSourceParser from the target Java source file.
 *
 * This context is passed to the PromptBuilder to construct a rich,
 * informative prompt for GitHub Copilot.
 */
public class JavaClassContext {

    // ── Class-level metadata ─────────────────────────────────────────────────

    /** Fully qualified class name, e.g. com.example.service.UserService */
    private String fullyQualifiedClassName;

    /** Simple class name, e.g. UserService */
    private String simpleClassName;

    /** Package name, e.g. com.example.service */
    private String packageName;

    /** Class-level annotations, e.g. @Service, @Component, @RestController */
    private List<String> classAnnotations = new ArrayList<>();

    /** Implemented interfaces */
    private List<String> implementedInterfaces = new ArrayList<>();

    /** Extended superclass (if any, not Object) */
    private String superClassName;

    /** Whether this is an abstract class */
    private boolean abstractClass;

    /** Whether this is an interface */
    private boolean interfaceType;

    /** Full source code of the class */
    private String fullSourceCode;

    // ── Fields ───────────────────────────────────────────────────────────────

    /** List of all fields (injected dependencies, etc.) */
    private List<FieldInfo> fields = new ArrayList<>();

    // ── Methods ──────────────────────────────────────────────────────────────

    /** List of all public/protected methods */
    private List<MethodInfo> methods = new ArrayList<>();

    // ── Imports ──────────────────────────────────────────────────────────────

    /** All import statements (used to infer Spring, JPA, etc.) */
    private List<String> imports = new ArrayList<>();

    // ── Inner classes ────────────────────────────────────────────────────────

    /** Names of any inner/nested classes */
    private List<String> innerClassNames = new ArrayList<>();

    // ────────────────────────────────────────────────────────────────────────
    // Inner DTOs
    // ────────────────────────────────────────────────────────────────────────

    /**
     * Represents a single field in the class.
     */
    public static class FieldInfo {
        private final String name;
        private final String type;
        private final List<String> annotations;
        private final boolean isPrivate;
        private final boolean isStatic;
        private final boolean isFinal;

        public FieldInfo(String name, String type, List<String> annotations,
                         boolean isPrivate, boolean isStatic, boolean isFinal) {
            this.name = name;
            this.type = type;
            this.annotations = annotations != null ? annotations : new ArrayList<>();
            this.isPrivate = isPrivate;
            this.isStatic = isStatic;
            this.isFinal = isFinal;
        }

        public String getName() { return name; }
        public String getType() { return type; }
        public List<String> getAnnotations() { return annotations; }
        public boolean isPrivate() { return isPrivate; }
        public boolean isStatic() { return isStatic; }
        public boolean isFinal() { return isFinal; }

        /** Returns true if this field is likely an injected dependency */
        public boolean isInjected() {
            return annotations.stream().anyMatch(a ->
                    a.contains("Autowired") ||
                    a.contains("Inject") ||
                    a.contains("Resource") ||
                    a.contains("Value")
            );
        }

        @Override
        public String toString() {
            return String.format("%s %s %s", String.join(" ", annotations), type, name).trim();
        }
    }

    /**
     * Represents a single method in the class.
     */
    public static class MethodInfo {
        private final String name;
        private final String returnType;
        private final List<ParameterInfo> parameters;
        private final List<String> annotations;
        private final List<String> thrownExceptions;
        private final boolean isPublic;
        private final boolean isStatic;
        private final boolean isAbstract;
        private final String accessModifier;

        public MethodInfo(String name, String returnType,
                          List<ParameterInfo> parameters,
                          List<String> annotations,
                          List<String> thrownExceptions,
                          boolean isPublic, boolean isStatic,
                          boolean isAbstract, String accessModifier) {
            this.name = name;
            this.returnType = returnType;
            this.parameters = parameters != null ? parameters : new ArrayList<>();
            this.annotations = annotations != null ? annotations : new ArrayList<>();
            this.thrownExceptions = thrownExceptions != null ? thrownExceptions : new ArrayList<>();
            this.isPublic = isPublic;
            this.isStatic = isStatic;
            this.isAbstract = isAbstract;
            this.accessModifier = accessModifier;
        }

        public String getName() { return name; }
        public String getReturnType() { return returnType; }
        public List<ParameterInfo> getParameters() { return parameters; }
        public List<String> getAnnotations() { return annotations; }
        public List<String> getThrownExceptions() { return thrownExceptions; }
        public boolean isPublic() { return isPublic; }
        public boolean isStatic() { return isStatic; }
        public boolean isAbstract() { return isAbstract; }
        public String getAccessModifier() { return accessModifier; }

        /** Returns true if this is a void method */
        public boolean isVoid() {
            return "void".equals(returnType);
        }

        /** Builds a readable method signature for prompt context */
        public String toSignature() {
            StringBuilder sb = new StringBuilder();
            if (!annotations.isEmpty()) {
                sb.append(String.join(" ", annotations)).append(" ");
            }
            sb.append(accessModifier).append(" ");
            if (isStatic) sb.append("static ");
            if (isAbstract) sb.append("abstract ");
            sb.append(returnType).append(" ").append(name).append("(");
            for (int i = 0; i < parameters.size(); i++) {
                if (i > 0) sb.append(", ");
                sb.append(parameters.get(i).toString());
            }
            sb.append(")");
            if (!thrownExceptions.isEmpty()) {
                sb.append(" throws ").append(String.join(", ", thrownExceptions));
            }
            return sb.toString();
        }
    }

    /**
     * Represents a parameter of a method.
     */
    public static class ParameterInfo {
        private final String name;
        private final String type;
        private final List<String> annotations;

        public ParameterInfo(String name, String type, List<String> annotations) {
            this.name = name;
            this.type = type;
            this.annotations = annotations != null ? annotations : new ArrayList<>();
        }

        public String getName() { return name; }
        public String getType() { return type; }
        public List<String> getAnnotations() { return annotations; }

        @Override
        public String toString() {
            if (annotations.isEmpty()) {
                return type + " " + name;
            }
            return String.join(" ", annotations) + " " + type + " " + name;
        }
    }

    // ────────────────────────────────────────────────────────────────────────
    // Getters and Setters
    // ────────────────────────────────────────────────────────────────────────

    public String getFullyQualifiedClassName() { return fullyQualifiedClassName; }
    public void setFullyQualifiedClassName(String v) { this.fullyQualifiedClassName = v; }

    public String getSimpleClassName() { return simpleClassName; }
    public void setSimpleClassName(String v) { this.simpleClassName = v; }

    public String getPackageName() { return packageName; }
    public void setPackageName(String v) { this.packageName = v; }

    public List<String> getClassAnnotations() { return classAnnotations; }
    public void setClassAnnotations(List<String> v) { this.classAnnotations = v; }

    public List<String> getImplementedInterfaces() { return implementedInterfaces; }
    public void setImplementedInterfaces(List<String> v) { this.implementedInterfaces = v; }

    public String getSuperClassName() { return superClassName; }
    public void setSuperClassName(String v) { this.superClassName = v; }

    public boolean isAbstractClass() { return abstractClass; }
    public void setAbstractClass(boolean v) { this.abstractClass = v; }

    public boolean isInterfaceType() { return interfaceType; }
    public void setInterfaceType(boolean v) { this.interfaceType = v; }

    public String getFullSourceCode() { return fullSourceCode; }
    public void setFullSourceCode(String v) { this.fullSourceCode = v; }

    public List<FieldInfo> getFields() { return fields; }
    public void setFields(List<FieldInfo> v) { this.fields = v; }

    public List<MethodInfo> getMethods() { return methods; }
    public void setMethods(List<MethodInfo> v) { this.methods = v; }

    public List<String> getImports() { return imports; }
    public void setImports(List<String> v) { this.imports = v; }

    public List<String> getInnerClassNames() { return innerClassNames; }
    public void setInnerClassNames(List<String> v) { this.innerClassNames = v; }

    // ── Convenience helpers ──────────────────────────────────────────────────

    /** Returns true if the class has Spring-related annotations */
    public boolean isSpringComponent() {
        return classAnnotations.stream().anyMatch(a ->
                a.contains("Service") || a.contains("Component") ||
                a.contains("Repository") || a.contains("Controller") ||
                a.contains("RestController") || a.contains("Configuration")
        );
    }

    /** Returns true if the class appears to be a JPA entity */
    public boolean isJpaEntity() {
        return classAnnotations.stream().anyMatch(a ->
                a.contains("Entity") || a.contains("MappedSuperclass")
        );
    }

    /** Returns count of public methods */
    public int getPublicMethodCount() {
        return (int) methods.stream().filter(MethodInfo::isPublic).count();
    }

    /** Returns only public methods */
    public List<MethodInfo> getPublicMethods() {
        return methods.stream()
                .filter(MethodInfo::isPublic)
                .collect(java.util.stream.Collectors.toList());
    }

    /** Returns all injected dependency fields */
    public List<FieldInfo> getInjectedDependencies() {
        return fields.stream()
                .filter(FieldInfo::isInjected)
                .collect(java.util.stream.Collectors.toList());
    }

    @Override
    public String toString() {
        return String.format("JavaClassContext{class='%s', methods=%d, fields=%d}",
                fullyQualifiedClassName, methods.size(), fields.size());
    }
}
