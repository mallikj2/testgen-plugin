package com.testgen.plugin.scanner;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Unit tests for ProjectDependencyContext.
 *
 * Verifies display strings, prompt strings, and helper methods.
 */
public class ProjectDependencyContextTest {

    private ProjectDependencyContext context;

    @Before
    public void setUp() {
        context = new ProjectDependencyContext();
    }

    // ── JUnit version helpers ────────────────────────────────────────────────

    @Test
    public void isJUnit5_whenJUnit5Set_returnsTrue() {
        context.setJunitVersion(ProjectDependencyContext.JUnitVersion.JUNIT5);
        assertTrue(context.isJUnit5());
        assertFalse(context.isJUnit4());
    }

    @Test
    public void isJUnit4_whenJUnit4Set_returnsTrue() {
        context.setJunitVersion(ProjectDependencyContext.JUnitVersion.JUNIT4);
        assertTrue(context.isJUnit4());
        assertFalse(context.isJUnit5());
    }

    @Test
    public void isJUnit5_whenUnknown_defaultsToJUnit5() {
        context.setJunitVersion(ProjectDependencyContext.JUnitVersion.UNKNOWN);
        assertTrue("Should default to JUnit 5 when unknown", context.isJUnit5());
    }

    // ── Display string ───────────────────────────────────────────────────────

    @Test
    public void toDisplayString_withJUnit5AndMockito_includesBoth() {
        context.setJunitVersion(ProjectDependencyContext.JUnitVersion.JUNIT5);
        context.setMockFramework(ProjectDependencyContext.MockFramework.MOCKITO);

        String display = context.toDisplayString();
        assertTrue("Display should contain JUnit 5", display.contains("JUnit 5"));
        assertTrue("Display should contain Mockito", display.contains("Mockito"));
    }

    @Test
    public void toDisplayString_withSpringBootTest_includesSpringBootTest() {
        context.setJunitVersion(ProjectDependencyContext.JUnitVersion.JUNIT5);
        context.setSpringBootTestPresent(true);

        String display = context.toDisplayString();
        assertTrue("Display should contain Spring Boot Test", display.contains("Spring Boot Test"));
    }

    @Test
    public void toDisplayString_withMockitoVersion_includesVersion() {
        context.setJunitVersion(ProjectDependencyContext.JUnitVersion.JUNIT5);
        context.setMockFramework(ProjectDependencyContext.MockFramework.MOCKITO);
        context.setMockitoVersion("5.x");

        String display = context.toDisplayString();
        assertTrue("Display should contain Mockito 5.x", display.contains("Mockito 5.x"));
    }

    @Test
    public void toDisplayString_withUnknownVersion_showsDefault() {
        context.setJunitVersion(ProjectDependencyContext.JUnitVersion.UNKNOWN);
        String display = context.toDisplayString();
        assertTrue("Display should show default when unknown",
                display.contains("default") || display.contains("JUnit 5"));
    }

    // ── Prompt string ────────────────────────────────────────────────────────

    @Test
    public void toPromptString_withJUnit5_mentionsJupiterAnnotations() {
        context.setJunitVersion(ProjectDependencyContext.JUnitVersion.JUNIT5);
        context.setMockFramework(ProjectDependencyContext.MockFramework.MOCKITO);

        String prompt = context.toPromptString();
        assertTrue("Prompt should mention JUnit 5 package",
                prompt.contains("junit.jupiter") || prompt.contains("JUnit 5"));
        assertTrue("Prompt should mention ExtendWith",
                prompt.contains("ExtendWith"));
    }

    @Test
    public void toPromptString_withJUnit4_mentionsRunWith() {
        context.setJunitVersion(ProjectDependencyContext.JUnitVersion.JUNIT4);
        context.setMockFramework(ProjectDependencyContext.MockFramework.MOCKITO);

        String prompt = context.toPromptString();
        assertTrue("Prompt should mention RunWith for JUnit 4",
                prompt.contains("RunWith"));
    }

    @Test
    public void toPromptString_withAssertJ_mentionsAssertJ() {
        context.setJunitVersion(ProjectDependencyContext.JUnitVersion.JUNIT5);
        context.setAssertJPresent(true);

        String prompt = context.toPromptString();
        assertTrue("Prompt should mention AssertJ", prompt.contains("AssertJ"));
    }

    @Test
    public void toPromptString_always_includesDoNotAddDependencies() {
        context.setJunitVersion(ProjectDependencyContext.JUnitVersion.JUNIT5);
        String prompt = context.toPromptString();
        assertTrue("Prompt should include restriction on new dependencies",
                prompt.contains("Do NOT introduce any dependencies"));
    }

    @Test
    public void toPromptString_withH2_mentionsH2() {
        context.setH2Present(true);
        String prompt = context.toPromptString();
        assertTrue("Prompt should mention H2", prompt.contains("H2"));
    }

    @Test
    public void toPromptString_withTestContainers_mentionsTestContainers() {
        context.setTestContainersPresent(true);
        String prompt = context.toPromptString();
        assertTrue("Prompt should mention TestContainers", prompt.contains("TestContainers"));
    }

    // ── Mockito availability ─────────────────────────────────────────────────

    @Test
    public void isMockitoAvailable_whenMockitoSet_returnsTrue() {
        context.setMockFramework(ProjectDependencyContext.MockFramework.MOCKITO);
        assertTrue(context.isMockitoAvailable());
    }

    @Test
    public void isMockitoAvailable_whenNoMock_returnsFalse() {
        context.setMockFramework(ProjectDependencyContext.MockFramework.NONE);
        assertFalse(context.isMockitoAvailable());
    }
}
