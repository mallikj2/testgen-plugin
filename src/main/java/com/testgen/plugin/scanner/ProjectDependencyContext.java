package com.testgen.plugin.scanner;

import java.util.ArrayList;
import java.util.List;

/**
 * ProjectDependencyContext
 *
 * DTO holding all dependency and framework information detected from
 * the project's build file (pom.xml or build.gradle / build.gradle.kts).
 *
 * This context is used by:
 *   1. PromptBuilder — to instruct Copilot which frameworks to use
 *   2. TestGenDialog — to display the "Detected Framework" badge
 *   3. TestFileWriter — to generate the correct import scaffold
 */
public class ProjectDependencyContext {

    // ── Build system ─────────────────────────────────────────────────────────

    public enum BuildSystem {
        MAVEN, GRADLE, UNKNOWN
    }

    private BuildSystem buildSystem = BuildSystem.UNKNOWN;

    // ── JUnit version ─────────────────────────────────────────────────────────

    public enum JUnitVersion {
        JUNIT4,     // org.junit.Test, @RunWith
        JUNIT5,     // org.junit.jupiter.api.Test, @ExtendWith
        UNKNOWN     // fall back to JUnit 5 (modern default)
    }

    private JUnitVersion junitVersion = JUnitVersion.UNKNOWN;

    // ── Mock framework ────────────────────────────────────────────────────────

    public enum MockFramework {
        MOCKITO,
        EASY_MOCK,
        POWERMOCK,
        NONE
    }

    private MockFramework mockFramework = MockFramework.NONE;

    // ── Spring context ────────────────────────────────────────────────────────

    /** Whether spring-boot-starter-test or spring-test is present */
    private boolean springTestPresent = false;

    /** Whether spring-boot-starter-test is specifically present (bundles JUnit5 + Mockito) */
    private boolean springBootTestPresent = false;

    // ── Other test utilities ──────────────────────────────────────────────────

    /** AssertJ is present (preferred over JUnit assertions) */
    private boolean assertJPresent = false;

    /** Hamcrest is present */
    private boolean hamcrestPresent = false;

    /** TestContainers is present */
    private boolean testContainersPresent = false;

    /** H2 in-memory database (for @DataJpaTest, etc.) */
    private boolean h2Present = false;

    /** WireMock for HTTP stubbing */
    private boolean wireMockPresent = false;

    /** Awaitility for async testing */
    private boolean awaitilityPresent = false;

    // ── Raw detected dependency strings (for advanced prompt context) ─────────

    private List<String> allTestDependencies = new ArrayList<>();

    // ── Mockito version hint ──────────────────────────────────────────────────

    /** Detected Mockito version string (e.g., "5.x", "4.x", "3.x") */
    private String mockitoVersion;

    // ────────────────────────────────────────────────────────────────────────
    // Getters / Setters
    // ────────────────────────────────────────────────────────────────────────

    public BuildSystem getBuildSystem() { return buildSystem; }
    public void setBuildSystem(BuildSystem v) { this.buildSystem = v; }

    public JUnitVersion getJunitVersion() { return junitVersion; }
    public void setJunitVersion(JUnitVersion v) { this.junitVersion = v; }

    public MockFramework getMockFramework() { return mockFramework; }
    public void setMockFramework(MockFramework v) { this.mockFramework = v; }

    public boolean isSpringTestPresent() { return springTestPresent; }
    public void setSpringTestPresent(boolean v) { this.springTestPresent = v; }

    public boolean isSpringBootTestPresent() { return springBootTestPresent; }
    public void setSpringBootTestPresent(boolean v) { this.springBootTestPresent = v; }

    public boolean isAssertJPresent() { return assertJPresent; }
    public void setAssertJPresent(boolean v) { this.assertJPresent = v; }

    public boolean isHamcrestPresent() { return hamcrestPresent; }
    public void setHamcrestPresent(boolean v) { this.hamcrestPresent = v; }

    public boolean isTestContainersPresent() { return testContainersPresent; }
    public void setTestContainersPresent(boolean v) { this.testContainersPresent = v; }

    public boolean isH2Present() { return h2Present; }
    public void setH2Present(boolean v) { this.h2Present = v; }

    public boolean isWireMockPresent() { return wireMockPresent; }
    public void setWireMockPresent(boolean v) { this.wireMockPresent = v; }

    public boolean isAwaitilityPresent() { return awaitilityPresent; }
    public void setAwaitilityPresent(boolean v) { this.awaitilityPresent = v; }

    public List<String> getAllTestDependencies() { return allTestDependencies; }
    public void setAllTestDependencies(List<String> v) { this.allTestDependencies = v; }

    public String getMockitoVersion() { return mockitoVersion; }
    public void setMockitoVersion(String v) { this.mockitoVersion = v; }

    // ────────────────────────────────────────────────────────────────────────
    // Convenience / display helpers
    // ────────────────────────────────────────────────────────────────────────

    /**
     * Returns the effective JUnit version to use, defaulting to JUnit 5
     * when unknown (modern standard).
     */
    public JUnitVersion getEffectiveJUnitVersion() {
        return junitVersion == JUnitVersion.UNKNOWN ? JUnitVersion.JUNIT5 : junitVersion;
    }

    /**
     * Returns true if JUnit 5 (Jupiter) should be used.
     */
    public boolean isJUnit5() {
        return getEffectiveJUnitVersion() == JUnitVersion.JUNIT5;
    }

    /**
     * Returns true if JUnit 4 should be used.
     */
    public boolean isJUnit4() {
        return getEffectiveJUnitVersion() == JUnitVersion.JUNIT4;
    }

    /**
     * Returns true if Mockito is available.
     */
    public boolean isMockitoAvailable() {
        return mockFramework == MockFramework.MOCKITO;
    }

    /**
     * Builds a human-readable summary of detected frameworks.
     * Used in the dialog's "Detected Framework" badge.
     *
     * Example output: "JUnit 5 + Mockito + Spring Boot Test"
     */
    public String toDisplayString() {
        List<String> parts = new ArrayList<>();

        // JUnit version
        switch (getEffectiveJUnitVersion()) {
            case JUNIT4: parts.add("JUnit 4"); break;
            case JUNIT5: parts.add("JUnit 5"); break;
            default: parts.add("JUnit 5 (default)"); break;
        }

        // Mock framework
        if (mockFramework == MockFramework.MOCKITO) {
            String mockitoDisplay = mockitoVersion != null
                    ? "Mockito " + mockitoVersion
                    : "Mockito";
            parts.add(mockitoDisplay);
        } else if (mockFramework == MockFramework.EASY_MOCK) {
            parts.add("EasyMock");
        } else if (mockFramework == MockFramework.POWERMOCK) {
            parts.add("PowerMock");
        }

        // Spring
        if (springBootTestPresent) {
            parts.add("Spring Boot Test");
        } else if (springTestPresent) {
            parts.add("Spring Test");
        }

        // Assertion libraries
        if (assertJPresent) parts.add("AssertJ");
        if (testContainersPresent) parts.add("TestContainers");
        if (h2Present) parts.add("H2");

        return String.join(" + ", parts);
    }

    /**
     * Builds a bullet-list string for use in the Copilot prompt.
     */
    public String toPromptString() {
        StringBuilder sb = new StringBuilder();

        sb.append("- Test Framework: ").append(
                getEffectiveJUnitVersion() == JUnitVersion.JUNIT4
                        ? "JUnit 4 (use org.junit.Test, @RunWith)"
                        : "JUnit 5 / Jupiter (use org.junit.jupiter.api.Test, @ExtendWith)"
        ).append("\n");

        if (mockFramework == MockFramework.MOCKITO) {
            String mv = mockitoVersion != null ? " (" + mockitoVersion + ")" : "";
            sb.append("- Mock Framework: Mockito").append(mv).append("\n");
            if (isJUnit5()) {
                sb.append("  → Use @ExtendWith(MockitoExtension.class)\n");
            } else {
                sb.append("  → Use @RunWith(MockitoJUnitRunner.class)\n");
            }
        } else if (mockFramework == MockFramework.EASY_MOCK) {
            sb.append("- Mock Framework: EasyMock\n");
        } else if (mockFramework == MockFramework.POWERMOCK) {
            sb.append("- Mock Framework: PowerMock\n");
        } else {
            sb.append("- Mock Framework: None detected — use Mockito if needed\n");
        }

        if (springBootTestPresent) {
            sb.append("- Spring Boot Test: Available (@SpringBootTest, @MockBean, @DataJpaTest etc.)\n");
        } else if (springTestPresent) {
            sb.append("- Spring Test: Available (@ContextConfiguration, @WebMvcTest etc.)\n");
        }

        if (assertJPresent) {
            sb.append("- Assertion Library: AssertJ — prefer assertThat() over JUnit assertions\n");
        }

        if (hamcrestPresent) {
            sb.append("- Hamcrest: Available\n");
        }

        if (testContainersPresent) {
            sb.append("- TestContainers: Available (for integration tests if needed)\n");
        }

        if (h2Present) {
            sb.append("- H2 In-Memory DB: Available (for repository/JPA tests)\n");
        }

        if (wireMockPresent) {
            sb.append("- WireMock: Available (for HTTP client stubbing)\n");
        }

        if (awaitilityPresent) {
            sb.append("- Awaitility: Available (for async/reactive tests)\n");
        }

        sb.append("- Build System: ").append(buildSystem.name()).append("\n");
        sb.append("- IMPORTANT: Do NOT introduce any dependencies not listed above\n");

        return sb.toString();
    }

    @Override
    public String toString() {
        return "ProjectDependencyContext{" +
               "buildSystem=" + buildSystem +
               ", junit=" + junitVersion +
               ", mock=" + mockFramework +
               ", springBoot=" + springBootTestPresent +
               '}';
    }
}
