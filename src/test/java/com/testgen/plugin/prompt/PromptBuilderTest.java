package com.testgen.plugin.prompt;

import com.testgen.plugin.parser.JavaClassContext;
import com.testgen.plugin.scanner.ProjectDependencyContext;
import com.testgen.plugin.ui.TestGenDialogModel;
import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.*;

/**
 * Unit tests for PromptBuilder.
 *
 * Verifies that the assembled prompt contains all required sections
 * and correctly reflects the input context.
 */
public class PromptBuilderTest {

    private PromptBuilder promptBuilder;
    private JavaClassContext classContext;
    private ProjectDependencyContext depContext;
    private TestGenDialogModel dialogModel;

    @Before
    public void setUp() {
        promptBuilder = new PromptBuilder();

        // ── Build a realistic JavaClassContext ────────────────────────────────
        classContext = new JavaClassContext();
        classContext.setSimpleClassName("UserService");
        classContext.setPackageName("com.example.service");
        classContext.setFullyQualifiedClassName("com.example.service.UserService");
        classContext.setClassAnnotations(Arrays.asList("@Service"));
        classContext.setFullSourceCode(
                "package com.example.service;\n\n" +
                "@Service\n" +
                "public class UserService {\n" +
                "    @Autowired private UserRepository userRepository;\n" +
                "    public User findById(Long id) { return userRepository.findById(id).orElse(null); }\n" +
                "    public User save(User user) { return userRepository.save(user); }\n" +
                "    public void delete(Long id) { userRepository.deleteById(id); }\n" +
                "}\n"
        );

        // Fields
        JavaClassContext.FieldInfo repoField = new JavaClassContext.FieldInfo(
                "userRepository", "UserRepository",
                Arrays.asList("@Autowired"), true, false, false
        );
        classContext.setFields(Arrays.asList(repoField));

        // Methods
        JavaClassContext.MethodInfo findById = new JavaClassContext.MethodInfo(
                "findById", "User",
                Arrays.asList(new JavaClassContext.ParameterInfo("id", "Long", Collections.emptyList())),
                Collections.emptyList(),
                Collections.emptyList(),
                true, false, false, "public"
        );
        JavaClassContext.MethodInfo save = new JavaClassContext.MethodInfo(
                "save", "User",
                Arrays.asList(new JavaClassContext.ParameterInfo("user", "User", Collections.emptyList())),
                Collections.emptyList(),
                Collections.emptyList(),
                true, false, false, "public"
        );
        JavaClassContext.MethodInfo delete = new JavaClassContext.MethodInfo(
                "delete", "void",
                Arrays.asList(new JavaClassContext.ParameterInfo("id", "Long", Collections.emptyList())),
                Collections.emptyList(),
                Collections.emptyList(),
                true, false, false, "public"
        );
        classContext.setMethods(Arrays.asList(findById, save, delete));

        // ── Build a realistic ProjectDependencyContext ─────────────────────────
        depContext = new ProjectDependencyContext();
        depContext.setBuildSystem(ProjectDependencyContext.BuildSystem.MAVEN);
        depContext.setJunitVersion(ProjectDependencyContext.JUnitVersion.JUNIT5);
        depContext.setMockFramework(ProjectDependencyContext.MockFramework.MOCKITO);
        depContext.setSpringBootTestPresent(true);
        depContext.setSpringTestPresent(true);
        depContext.setAssertJPresent(true);

        // ── Dialog model ──────────────────────────────────────────────────────
        dialogModel = new TestGenDialogModel(
                "com.example.service",
                "UserServiceTest",
                "This is a Spring service for user management. Focus on null safety and exception handling."
        );
    }

    // ── Test: prompt is not empty ────────────────────────────────────────────

    @Test
    public void build_withValidInputs_returnsNonEmptyPrompt() {
        String prompt = promptBuilder.build(classContext, depContext, dialogModel);
        assertNotNull("Prompt should not be null", prompt);
        assertFalse("Prompt should not be empty", prompt.trim().isEmpty());
    }

    // ── Test: source code section is included ────────────────────────────────

    @Test
    public void build_withSourceCode_includesSourceCodeSection() {
        String prompt = promptBuilder.build(classContext, depContext, dialogModel);
        assertTrue("Prompt should contain source class section",
                prompt.contains("## Source Class to Test"));
        assertTrue("Prompt should contain the actual source code",
                prompt.contains("UserService"));
    }

    // ── Test: dependency section is included ─────────────────────────────────

    @Test
    public void build_withJUnit5AndMockito_includesDependencySection() {
        String prompt = promptBuilder.build(classContext, depContext, dialogModel);
        assertTrue("Prompt should contain dependencies section",
                prompt.contains("## Project Test Dependencies"));
        assertTrue("Prompt should mention JUnit 5",
                prompt.contains("JUnit 5") || prompt.contains("junit.jupiter"));
        assertTrue("Prompt should mention Mockito",
                prompt.contains("Mockito"));
    }

    // ── Test: test class requirements section ────────────────────────────────

    @Test
    public void build_withDialogModel_includesTestClassRequirements() {
        String prompt = promptBuilder.build(classContext, depContext, dialogModel);
        assertTrue("Prompt should contain requirements section",
                prompt.contains("## Test Class Requirements"));
        assertTrue("Prompt should contain the test package",
                prompt.contains("com.example.service"));
        assertTrue("Prompt should contain the test class name",
                prompt.contains("UserServiceTest"));
    }

    // ── Test: developer description is included ──────────────────────────────

    @Test
    public void build_withDescription_includesDeveloperContextSection() {
        String prompt = promptBuilder.build(classContext, depContext, dialogModel);
        assertTrue("Prompt should contain developer context section",
                prompt.contains("## Additional Context from Developer"));
        assertTrue("Prompt should contain the user's description",
                prompt.contains("Spring service for user management"));
    }

    // ── Test: no description — section omitted ────────────────────────────────

    @Test
    public void build_withEmptyDescription_omitsDeveloperContextSection() {
        dialogModel.setDescription("");
        String prompt = promptBuilder.build(classContext, depContext, dialogModel);
        assertFalse("Prompt should NOT contain developer context section when description is empty",
                prompt.contains("## Additional Context from Developer"));
    }

    // ── Test: instructions section is always present ─────────────────────────

    @Test
    public void build_always_includesGenerationInstructions() {
        String prompt = promptBuilder.build(classContext, depContext, dialogModel);
        assertTrue("Prompt should contain generation instructions",
                prompt.contains("## Generation Instructions"));
        assertTrue("Prompt should mention Arrange/Act/Assert",
                prompt.contains("Arrange") || prompt.contains("Assert"));
    }

    // ── Test: injected dependencies mentioned in structure ────────────────────

    @Test
    public void build_withInjectedDeps_mentionsDepsInStructureSection() {
        String prompt = promptBuilder.build(classContext, depContext, dialogModel);
        assertTrue("Prompt should mention injected dependencies",
                prompt.contains("Injected Dependencies") || prompt.contains("userRepository"));
    }

    // ── Test: JUnit 4 prompt differs from JUnit 5 ────────────────────────────

    @Test
    public void build_withJUnit4_generatesJUnit4SpecificPrompt() {
        depContext.setJunitVersion(ProjectDependencyContext.JUnitVersion.JUNIT4);
        depContext.setMockFramework(ProjectDependencyContext.MockFramework.MOCKITO);

        String prompt = promptBuilder.build(classContext, depContext, dialogModel);

        assertTrue("JUnit 4 prompt should mention RunWith",
                prompt.contains("RunWith") || prompt.contains("JUnit 4"));
    }

    // ── Test: AssertJ mentioned when present ─────────────────────────────────

    @Test
    public void build_withAssertJ_mentionsAssertJInInstructions() {
        depContext.setAssertJPresent(true);
        String prompt = promptBuilder.build(classContext, depContext, dialogModel);
        assertTrue("Prompt should prefer AssertJ when present",
                prompt.contains("AssertJ") || prompt.contains("assertThat"));
    }

    // ── Test: output format instruction present ───────────────────────────────

    @Test
    public void build_always_includesOutputFormatInstruction() {
        String prompt = promptBuilder.build(classContext, depContext, dialogModel);
        assertTrue("Prompt should instruct Copilot on output format",
                prompt.contains("Output ONLY the Java source code") ||
                prompt.contains("Start directly with the `package`") ||
                prompt.contains("compilable"));
    }

    // ── Test: method names are included in prompt ────────────────────────────

    @Test
    public void build_withMethods_includesMethodSignatures() {
        String prompt = promptBuilder.build(classContext, depContext, dialogModel);
        assertTrue("Prompt should include method name: findById",
                prompt.contains("findById"));
        assertTrue("Prompt should include method name: save",
                prompt.contains("save"));
        assertTrue("Prompt should include method name: delete",
                prompt.contains("delete"));
    }

    // ── Test: prompt is substantial in length ────────────────────────────────

    @Test
    public void build_withFullContext_producesSubstantialPrompt() {
        String prompt = promptBuilder.build(classContext, depContext, dialogModel);
        // A good prompt should be at least 1000 characters
        assertTrue("Prompt should be substantial in length (>1000 chars)",
                prompt.length() > 1000);
    }
}
