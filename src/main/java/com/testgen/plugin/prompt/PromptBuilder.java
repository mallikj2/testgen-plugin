package com.testgen.plugin.prompt;

import com.testgen.plugin.parser.JavaClassContext;
import com.testgen.plugin.scanner.ProjectDependencyContext;
import com.testgen.plugin.ui.TestGenDialogModel;

import java.util.List;

/**
 * PromptBuilder
 *
 * Assembles a comprehensive, structured prompt for GitHub Copilot Chat.
 * The quality and structure of this prompt directly determines the quality
 * of the generated test code.
 *
 * Prompt sections:
 *   1. Role & objective
 *   2. Source class (full code)
 *   3. Extracted class structure summary
 *   4. Project dependencies context
 *   5. Test class requirements (from dialog)
 *   6. Developer's additional context (optional description)
 *   7. Detailed generation instructions
 */
public class PromptBuilder {

    /**
     * Builds the complete prompt string.
     *
     * @param classContext      parsed Java source class info
     * @param dependencyContext detected project dependencies
     * @param dialogModel       user input from the TestGen dialog
     * @return the complete prompt string ready to inject into Copilot Chat
     */
    public String build(JavaClassContext classContext,
                        ProjectDependencyContext dependencyContext,
                        TestGenDialogModel dialogModel) {

        StringBuilder prompt = new StringBuilder();

        // ── 1. Role & objective ───────────────────────────────────────────────
        prompt.append(buildRoleSection());
        prompt.append("\n\n");

        // ── 2. Source class ───────────────────────────────────────────────────
        prompt.append(buildSourceClassSection(classContext));
        prompt.append("\n\n");

        // ── 3. Class structure summary ────────────────────────────────────────
        prompt.append(buildClassStructureSection(classContext));
        prompt.append("\n\n");

        // ── 4. Project dependencies ───────────────────────────────────────────
        prompt.append(buildDependenciesSection(dependencyContext));
        prompt.append("\n\n");

        // ── 5. Test class requirements ────────────────────────────────────────
        prompt.append(buildRequirementsSection(dialogModel, classContext, dependencyContext));
        prompt.append("\n\n");

        // ── 6. Developer context (optional) ───────────────────────────────────
        String description = dialogModel.getDescription();
        if (description != null && !description.trim().isEmpty()) {
            prompt.append(buildDeveloperContextSection(description));
            prompt.append("\n\n");
        }

        // ── 7. Generation instructions ────────────────────────────────────────
        prompt.append(buildInstructionsSection(classContext, dependencyContext));

        return prompt.toString();
    }

    // ────────────────────────────────────────────────────────────────────────
    // Section builders
    // ────────────────────────────────────────────────────────────────────────

    private String buildRoleSection() {
        return "You are an expert Java test engineer with deep knowledge of JUnit, " +
               "Mockito, Spring Boot Test, and modern testing best practices. " +
               "Your task is to generate a complete, production-quality JUnit test class.";
    }

    private String buildSourceClassSection(JavaClassContext classContext) {
        StringBuilder sb = new StringBuilder();
        sb.append("## Source Class to Test\n");
        sb.append("```java\n");
        sb.append(classContext.getFullSourceCode());
        sb.append("\n```");
        return sb.toString();
    }

    private String buildClassStructureSection(JavaClassContext classContext) {
        StringBuilder sb = new StringBuilder();
        sb.append("## Class Structure Analysis\n");

        sb.append("- **Class**: `").append(classContext.getFullyQualifiedClassName()).append("`\n");
        sb.append("- **Type**: ").append(buildClassTypeDescription(classContext)).append("\n");

        if (classContext.getSuperClassName() != null) {
            sb.append("- **Extends**: `").append(classContext.getSuperClassName()).append("`\n");
        }

        if (!classContext.getImplementedInterfaces().isEmpty()) {
            sb.append("- **Implements**: `")
              .append(String.join("`, `", classContext.getImplementedInterfaces()))
              .append("`\n");
        }

        if (!classContext.getClassAnnotations().isEmpty()) {
            sb.append("- **Class Annotations**: ")
              .append(String.join(", ", classContext.getClassAnnotations()))
              .append("\n");
        }

        // Injected dependencies
        List<JavaClassContext.FieldInfo> injectedDeps = classContext.getInjectedDependencies();
        if (!injectedDeps.isEmpty()) {
            sb.append("- **Injected Dependencies** (must be mocked):\n");
            for (JavaClassContext.FieldInfo field : injectedDeps) {
                sb.append("  - `").append(field.getType()).append(" ")
                  .append(field.getName()).append("`");
                if (!field.getAnnotations().isEmpty()) {
                    sb.append(" ").append(String.join(" ", field.getAnnotations()));
                }
                sb.append("\n");
            }
        }

        // All fields
        List<JavaClassContext.FieldInfo> allFields = classContext.getFields();
        List<JavaClassContext.FieldInfo> nonInjectedFields = allFields.stream()
                .filter(f -> !f.isInjected())
                .collect(java.util.stream.Collectors.toList());
        if (!nonInjectedFields.isEmpty()) {
            sb.append("- **Other Fields**:\n");
            for (JavaClassContext.FieldInfo field : nonInjectedFields) {
                sb.append("  - `").append(field.toString()).append("`\n");
            }
        }

        // Public methods
        sb.append("\n### Public Methods to Test\n");
        List<JavaClassContext.MethodInfo> publicMethods = classContext.getPublicMethods();
        if (publicMethods.isEmpty()) {
            sb.append("_(No public methods found — test package-private or protected methods as appropriate)_\n");
        } else {
            for (JavaClassContext.MethodInfo method : publicMethods) {
                if ("<constructor>".equals(method.getReturnType())) continue;
                sb.append("- `").append(method.toSignature()).append("`\n");

                // Describe what to test per method
                sb.append("  → Test: happy path");
                if (hasNullableParams(method)) {
                    sb.append(", null inputs");
                }
                if (!method.getThrownExceptions().isEmpty()) {
                    sb.append(", exception scenarios (")
                      .append(String.join(", ", method.getThrownExceptions()))
                      .append(")");
                }
                if (method.isVoid()) {
                    sb.append(", side-effect verification");
                }
                sb.append(", edge cases\n");
            }
        }

        // Constructors
        List<JavaClassContext.MethodInfo> constructors = classContext.getMethods().stream()
                .filter(m -> "<constructor>".equals(m.getReturnType()))
                .collect(java.util.stream.Collectors.toList());
        if (!constructors.isEmpty()) {
            sb.append("\n### Constructors\n");
            for (JavaClassContext.MethodInfo ctor : constructors) {
                sb.append("- `").append(classContext.getSimpleClassName())
                  .append("(").append(buildParamList(ctor)).append(")`\n");
            }
        }

        return sb.toString();
    }

    private String buildDependenciesSection(ProjectDependencyContext depContext) {
        StringBuilder sb = new StringBuilder();
        sb.append("## Project Test Dependencies (MUST use only these — do NOT add new ones)\n");
        sb.append(depContext.toPromptString());
        return sb.toString();
    }

    private String buildRequirementsSection(TestGenDialogModel dialogModel,
                                            JavaClassContext classContext,
                                            ProjectDependencyContext depContext) {
        StringBuilder sb = new StringBuilder();
        sb.append("## Test Class Requirements\n");
        sb.append("- **Test Package**: `").append(dialogModel.getTestPackage()).append("`\n");
        sb.append("- **Test Class Name**: `").append(dialogModel.getTestClassName()).append("`\n");

        // Framework-specific setup requirements
        if (depContext.isJUnit5()) {
            if (depContext.isMockitoAvailable()) {
                sb.append("- **Class Setup**: Use `@ExtendWith(MockitoExtension.class)`\n");
            } else if (depContext.isSpringBootTestPresent()) {
                sb.append("- **Class Setup**: Use `@SpringBootTest` or `@ExtendWith(SpringExtension.class)` as appropriate\n");
            }
        } else {
            if (depContext.isMockitoAvailable()) {
                sb.append("- **Class Setup**: Use `@RunWith(MockitoJUnitRunner.class)`\n");
            } else if (depContext.isSpringBootTestPresent()) {
                sb.append("- **Class Setup**: Use `@RunWith(SpringRunner.class)`\n");
            }
        }

        // Spring-specific hints
        if (classContext.isSpringComponent()) {
            sb.append("- **Spring Component detected**: Consider using `@MockBean` for Spring-managed dependencies\n");
        }

        // JPA Entity hints
        if (classContext.isJpaEntity()) {
            sb.append("- **JPA Entity detected**: Consider `@DataJpaTest` if testing repository layer\n");
        }

        return sb.toString();
    }

    private String buildDeveloperContextSection(String description) {
        StringBuilder sb = new StringBuilder();
        sb.append("## Additional Context from Developer\n");
        sb.append("> ").append(description.trim().replace("\n", "\n> ")).append("\n");
        sb.append("\n_Use this context to prioritize certain test scenarios and understand the class's intent._");
        return sb.toString();
    }

    private String buildInstructionsSection(JavaClassContext classContext,
                                            ProjectDependencyContext depContext) {
        StringBuilder sb = new StringBuilder();
        sb.append("## Generation Instructions\n\n");

        sb.append("Generate a **complete, compilable Java test class** following these rules:\n\n");

        // Coverage rules
        sb.append("### Coverage Requirements\n");
        sb.append("1. Cover **every public method** with at least one test\n");
        sb.append("2. For each method, include:\n");
        sb.append("   - ✅ Happy path (valid inputs, expected output)\n");
        sb.append("   - ❌ Edge cases (empty strings, zero values, max values, empty collections)\n");
        sb.append("   - 💥 Null input handling (where parameters could be null)\n");
        sb.append("   - 🚨 Exception scenarios (test that expected exceptions are thrown)\n");
        if (!classContext.getInjectedDependencies().isEmpty()) {
            sb.append("   - 🎭 Dependency behavior verification (verify mock interactions)\n");
        }
        sb.append("3. Test method names must follow: `methodName_scenario_expectedResult()` pattern\n");
        sb.append("   Example: `findUserById_withValidId_returnsUser()`\n\n");

        // Code style rules
        sb.append("### Code Style\n");
        sb.append("4. Use **Arrange / Act / Assert** structure in every test method, with comments\n");
        sb.append("5. Keep each test method focused on a single behavior\n");
        sb.append("6. Use descriptive variable names in test setup\n");

        // Assertion rules
        sb.append("\n### Assertions\n");
        if (depContext.isAssertJPresent()) {
            sb.append("7. Use **AssertJ** `assertThat()` for all assertions (preferred)\n");
        } else {
            sb.append("7. Use JUnit's built-in `assertEquals`, `assertThrows`, `assertNotNull` etc.\n");
        }
        sb.append("8. For exception tests, use `assertThrows` (JUnit 5) or `@Test(expected=)` (JUnit 4)\n");

        // Mocking rules
        if (depContext.isMockitoAvailable() && !classContext.getInjectedDependencies().isEmpty()) {
            sb.append("\n### Mocking\n");
            sb.append("9. Use `@Mock` annotation for all dependencies\n");
            sb.append("10. Use `@InjectMocks` to create the class under test\n");
            sb.append("11. Use `when(...).thenReturn(...)` for stubbing behavior\n");
            sb.append("12. Use `verify(mock).method(...)` to verify interactions where relevant\n");
            sb.append("13. Use `ArgumentCaptor` when you need to capture and verify method arguments\n");
        }

        // Spring rules
        if (depContext.isSpringTestPresent() && classContext.isSpringComponent()) {
            sb.append("\n### Spring Testing\n");
            sb.append("14. Prefer `@MockBean` for Spring context mocks\n");
            sb.append("15. Use `@WebMvcTest` for controller tests, `@DataJpaTest` for repositories\n");
        }

        // Output format rules
        sb.append("\n### Output Format\n");
        sb.append("⚠️  **IMPORTANT**: Output ONLY the Java source code.\n");
        sb.append("- Do NOT include any explanation text before or after the code\n");
        sb.append("- Do NOT wrap in markdown code blocks\n");
        sb.append("- Start directly with the `package` statement\n");
        sb.append("- The code must be complete and immediately compilable\n");
        sb.append("- Include all necessary import statements\n");

        return sb.toString();
    }

    // ────────────────────────────────────────────────────────────────────────
    // Private utility helpers
    // ────────────────────────────────────────────────────────────────────────

    private String buildClassTypeDescription(JavaClassContext ctx) {
        if (ctx.isInterfaceType()) return "Interface";
        if (ctx.isAbstractClass()) return "Abstract Class";
        if (ctx.isSpringComponent()) {
            String annotation = ctx.getClassAnnotations().stream()
                    .filter(a -> a.contains("Service") || a.contains("Component") ||
                                 a.contains("Repository") || a.contains("Controller") ||
                                 a.contains("RestController"))
                    .findFirst().orElse("Spring Component");
            return "Spring " + annotation.replace("@", "") + " (Concrete Class)";
        }
        return "Concrete Class";
    }

    private boolean hasNullableParams(JavaClassContext.MethodInfo method) {
        return method.getParameters().stream()
                .anyMatch(p -> {
                    String type = p.getType();
                    // Reference types (not primitives) can be null
                    return !isPrimitive(type);
                });
    }

    private boolean isPrimitive(String type) {
        return type != null && (
                type.equals("int") || type.equals("long") || type.equals("double") ||
                type.equals("float") || type.equals("boolean") || type.equals("char") ||
                type.equals("byte") || type.equals("short")
        );
    }

    private String buildParamList(JavaClassContext.MethodInfo method) {
        StringBuilder sb = new StringBuilder();
        List<JavaClassContext.ParameterInfo> params = method.getParameters();
        for (int i = 0; i < params.size(); i++) {
            if (i > 0) sb.append(", ");
            sb.append(params.get(i).getType()).append(" ").append(params.get(i).getName());
        }
        return sb.toString();
    }
}
