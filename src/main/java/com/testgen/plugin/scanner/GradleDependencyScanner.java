package com.testgen.plugin.scanner;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * GradleDependencyScanner
 *
 * Scans Gradle build files (build.gradle and build.gradle.kts) for
 * test-related dependencies using text/regex parsing.
 *
 * Supports:
 *   - Groovy DSL: build.gradle
 *   - Kotlin DSL: build.gradle.kts
 *
 * Detection approach: Text-based scanning with regex patterns.
 * This is Level 1 scanning — direct dependencies only, no resolution of
 * included builds, version catalogs (libs.versions.toml) deep-parsing, etc.
 *
 * Phase 2 enhancement: Parse libs.versions.toml for version catalog support.
 */
public class GradleDependencyScanner implements BuildFileScanner {

    private static final Logger LOG = Logger.getInstance(GradleDependencyScanner.class);

    private static final String BUILD_GRADLE = "build.gradle";
    private static final String BUILD_GRADLE_KTS = "build.gradle.kts";

    /**
     * Regex pattern to capture dependency declarations.
     * Matches both:
     *   testImplementation 'group:artifact:version'
     *   testImplementation("group:artifact:version")
     *   testImplementation group: 'group', name: 'artifact', version: 'version'
     *   implementation("group:artifact:version") [also scanned]
     */
    private static final Pattern DEPENDENCY_PATTERN = Pattern.compile(
            "(?:testImplementation|testCompile|testRuntimeOnly|testRuntime|" +
            "androidTestImplementation|testApi|implementation|runtimeOnly)" +
            "\\s*[\\(\"']([^\\)\"']+)[\"'\\)]",
            Pattern.CASE_INSENSITIVE
    );

    /**
     * Regex for Kotlin DSL style: testImplementation("group:artifact:version")
     */
    private static final Pattern KTS_DEPENDENCY_PATTERN = Pattern.compile(
            "(?:testImplementation|testCompile|testRuntimeOnly|testRuntime|" +
            "implementation|testApi)\\s*\\(\\s*\"([^\"]+)\"\\s*\\)",
            Pattern.CASE_INSENSITIVE
    );

    @Override
    public boolean isApplicable(Project project) {
        return findGradleFile(project) != null;
    }

    @Override
    public ProjectDependencyContext scan(Project project) {
        ProjectDependencyContext context = new ProjectDependencyContext();
        context.setBuildSystem(ProjectDependencyContext.BuildSystem.GRADLE);

        VirtualFile gradleFile = findGradleFile(project);
        if (gradleFile == null) {
            LOG.warn("TestGen: build.gradle / build.gradle.kts not found.");
            return applyDefaults(context);
        }

        boolean isKts = gradleFile.getName().endsWith(".kts");

        try {
            String content = readFileContent(gradleFile);
            if (content == null || content.isEmpty()) {
                return applyDefaults(context);
            }

            List<String> dependencyStrings = extractDependencyStrings(content, isKts);
            List<String> allTestDeps = new ArrayList<>();

            for (String dep : dependencyStrings) {
                // dep is typically "group:artifact:version" or "group:artifact"
                String[] parts = dep.split(":");
                if (parts.length < 2) continue;

                String groupId = parts[0].trim().toLowerCase();
                String artifactId = parts[1].trim().toLowerCase();
                String version = parts.length > 2 ? parts[2].trim() : null;

                allTestDeps.add(dep);

                // ── JUnit detection ───────────────────────────────────────────

                // JUnit 5
                if (groupId.equals("org.junit.jupiter") ||
                    artifactId.contains("junit-jupiter") ||
                    artifactId.equals("junit-jupiter-api") ||
                    artifactId.equals("junit-jupiter-engine") ||
                    artifactId.equals("junit-jupiter")) {
                    context.setJunitVersion(ProjectDependencyContext.JUnitVersion.JUNIT5);
                }

                // spring-boot-starter-test (bundles JUnit 5 + Mockito)
                if (artifactId.equals("spring-boot-starter-test")) {
                    context.setSpringBootTestPresent(true);
                    context.setSpringTestPresent(true);
                    if (context.getJunitVersion() == ProjectDependencyContext.JUnitVersion.UNKNOWN) {
                        context.setJunitVersion(ProjectDependencyContext.JUnitVersion.JUNIT5);
                    }
                    if (context.getMockFramework() == ProjectDependencyContext.MockFramework.NONE) {
                        context.setMockFramework(ProjectDependencyContext.MockFramework.MOCKITO);
                    }
                }

                // JUnit 4
                if ((groupId.equals("junit") && artifactId.equals("junit")) ||
                    artifactId.equals("junit-vintage-engine")) {
                    if (context.getJunitVersion() == ProjectDependencyContext.JUnitVersion.UNKNOWN) {
                        context.setJunitVersion(ProjectDependencyContext.JUnitVersion.JUNIT4);
                    }
                }

                // JUnit Platform
                if (groupId.equals("org.junit.platform")) {
                    if (context.getJunitVersion() == ProjectDependencyContext.JUnitVersion.UNKNOWN) {
                        context.setJunitVersion(ProjectDependencyContext.JUnitVersion.JUNIT5);
                    }
                }

                // ── Mockito ───────────────────────────────────────────────────

                if (groupId.equals("org.mockito") &&
                    (artifactId.equals("mockito-core") ||
                     artifactId.equals("mockito-junit-jupiter") ||
                     artifactId.equals("mockito-all") ||
                     artifactId.equals("mockito-inline"))) {
                    context.setMockFramework(ProjectDependencyContext.MockFramework.MOCKITO);
                    if (version != null && !version.startsWith("$")) {
                        context.setMockitoVersion(detectVersionHint(version));
                    }
                }

                // EasyMock
                if (groupId.equals("org.easymock")) {
                    if (context.getMockFramework() == ProjectDependencyContext.MockFramework.NONE) {
                        context.setMockFramework(ProjectDependencyContext.MockFramework.EASY_MOCK);
                    }
                }

                // PowerMock
                if (groupId.equals("org.powermock")) {
                    if (context.getMockFramework() == ProjectDependencyContext.MockFramework.NONE) {
                        context.setMockFramework(ProjectDependencyContext.MockFramework.POWERMOCK);
                    }
                }

                // ── Spring Test ───────────────────────────────────────────────

                if (artifactId.equals("spring-test") ||
                    artifactId.equals("spring-boot-test")) {
                    context.setSpringTestPresent(true);
                }

                // ── AssertJ ───────────────────────────────────────────────────

                if (groupId.equals("org.assertj") || artifactId.contains("assertj")) {
                    context.setAssertJPresent(true);
                }

                // ── Hamcrest ──────────────────────────────────────────────────

                if (groupId.equals("org.hamcrest") || artifactId.contains("hamcrest")) {
                    context.setHamcrestPresent(true);
                }

                // ── TestContainers ────────────────────────────────────────────

                if (groupId.equals("org.testcontainers")) {
                    context.setTestContainersPresent(true);
                }

                // ── H2 ────────────────────────────────────────────────────────

                if (groupId.equals("com.h2database")) {
                    context.setH2Present(true);
                }

                // ── WireMock ──────────────────────────────────────────────────

                if (artifactId.contains("wiremock")) {
                    context.setWireMockPresent(true);
                }

                // ── Awaitility ────────────────────────────────────────────────

                if (groupId.equals("org.awaitility") || artifactId.contains("awaitility")) {
                    context.setAwaitilityPresent(true);
                }
            }

            // Also scan for useJUnitPlatform() which indicates JUnit 5 is configured
            if (content.contains("useJUnitPlatform()")) {
                if (context.getJunitVersion() == ProjectDependencyContext.JUnitVersion.UNKNOWN) {
                    context.setJunitVersion(ProjectDependencyContext.JUnitVersion.JUNIT5);
                }
            }

            context.setAllTestDependencies(allTestDeps);

            LOG.info("TestGen: Gradle scan complete → " + context.toDisplayString());

        } catch (Exception e) {
            LOG.error("TestGen: Error scanning Gradle build file", e);
        }

        return applyDefaults(context);
    }

    // ────────────────────────────────────────────────────────────────────────
    // Private helpers
    // ────────────────────────────────────────────────────────────────────────

    /**
     * Applies default values when detection is incomplete.
     */
    private ProjectDependencyContext applyDefaults(ProjectDependencyContext context) {
        if (context.getJunitVersion() == ProjectDependencyContext.JUnitVersion.UNKNOWN) {
            context.setJunitVersion(ProjectDependencyContext.JUnitVersion.JUNIT5);
            LOG.info("TestGen: JUnit version not detected in Gradle, defaulting to JUnit 5");
        }
        return context;
    }

    /**
     * Finds either build.gradle or build.gradle.kts in the project root.
     * Prefers build.gradle.kts (Kotlin DSL) when both exist.
     */
    private VirtualFile findGradleFile(Project project) {
        VirtualFile baseDir = getProjectBaseDir(project);
        if (baseDir == null) return null;

        // Check Kotlin DSL first
        VirtualFile kts = baseDir.findChild(BUILD_GRADLE_KTS);
        if (kts != null) return kts;

        // Fall back to Groovy DSL
        return baseDir.findChild(BUILD_GRADLE);
    }

    /**
     * Gets the project's base directory as a VirtualFile.
     */
    private VirtualFile getProjectBaseDir(Project project) {
        String basePath = project.getBasePath();
        if (basePath == null) return null;
        return com.intellij.openapi.vfs.LocalFileSystem.getInstance()
                .findFileByPath(basePath);
    }

    /**
     * Reads a VirtualFile's full text content.
     */
    private String readFileContent(VirtualFile file) {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append("\n");
            }
            return sb.toString();
        } catch (Exception e) {
            LOG.error("TestGen: Failed to read Gradle file: " + file.getPath(), e);
            return null;
        }
    }

    /**
     * Extracts all "group:artifact:version" strings from Gradle build file content.
     * Uses different regex patterns for Groovy vs Kotlin DSL.
     */
    private List<String> extractDependencyStrings(String content, boolean isKts) {
        List<String> result = new ArrayList<>();
        Pattern pattern = isKts ? KTS_DEPENDENCY_PATTERN : DEPENDENCY_PATTERN;

        Matcher matcher = pattern.matcher(content);
        while (matcher.find()) {
            String dep = matcher.group(1).trim();
            if (dep.contains(":")) {
                result.add(dep);
            }
        }

        // Also scan for Kotlin DSL libs.* version catalog references (basic detection)
        // e.g., testImplementation(libs.junit.jupiter)
        Pattern libsPattern = Pattern.compile(
                "(?:testImplementation|testCompile|testRuntimeOnly)\\s*\\(\\s*(libs\\.[^)]+)\\s*\\)",
                Pattern.CASE_INSENSITIVE
        );
        Matcher libsMatcher = libsPattern.matcher(content);
        while (libsMatcher.find()) {
            // Log the version catalog reference for awareness
            // Full resolution would require parsing libs.versions.toml (Phase 2)
            LOG.info("TestGen: Version catalog reference detected: " + libsMatcher.group(1) +
                     " (full version catalog resolution planned for Phase 2)");
        }

        return result;
    }

    /**
     * Converts a version string to a short hint like "5.x".
     */
    private String detectVersionHint(String version) {
        if (version == null) return null;
        if (version.startsWith("5")) return "5.x";
        if (version.startsWith("4")) return "4.x";
        if (version.startsWith("3")) return "3.x";
        if (version.startsWith("2")) return "2.x";
        return version;
    }
}
