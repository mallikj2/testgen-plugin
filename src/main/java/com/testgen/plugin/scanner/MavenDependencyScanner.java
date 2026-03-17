package com.testgen.plugin.scanner;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * MavenDependencyScanner
 *
 * Scans the project's pom.xml to detect test-related dependencies.
 * Supports Level 1 scanning — direct dependencies in the project's own pom.xml.
 *
 * Detects:
 *   - JUnit 4 vs JUnit 5
 *   - Mockito (and version hints)
 *   - Spring Boot Test / Spring Test
 *   - AssertJ, Hamcrest, TestContainers, H2, WireMock, Awaitility
 *
 * Note: Does not resolve parent POMs or effective-POM (Phase 2 enhancement).
 */
public class MavenDependencyScanner implements BuildFileScanner {

    private static final Logger LOG = Logger.getInstance(MavenDependencyScanner.class);

    private static final String POM_XML = "pom.xml";

    @Override
    public boolean isApplicable(Project project) {
        return findPomFile(project) != null;
    }

    @Override
    public ProjectDependencyContext scan(Project project) {
        ProjectDependencyContext context = new ProjectDependencyContext();
        context.setBuildSystem(ProjectDependencyContext.BuildSystem.MAVEN);

        VirtualFile pomFile = findPomFile(project);
        if (pomFile == null) {
            LOG.warn("TestGen: pom.xml not found in project root.");
            return applyDefaults(context);
        }

        try {
            Document doc = parseXml(pomFile);
            if (doc == null) {
                return applyDefaults(context);
            }

            List<DependencyEntry> dependencies = extractDependencies(doc);
            List<String> allTestDeps = new ArrayList<>();

            for (DependencyEntry dep : dependencies) {
                String groupId = dep.groupId.toLowerCase();
                String artifactId = dep.artifactId.toLowerCase();
                String combined = groupId + ":" + artifactId;

                // Track all test-scope or test-relevant deps
                if ("test".equals(dep.scope) || isTestRelated(groupId, artifactId)) {
                    allTestDeps.add(dep.groupId + ":" + dep.artifactId
                            + (dep.version != null ? ":" + dep.version : ""));
                }

                // ── JUnit detection ───────────────────────────────────────────

                // JUnit 5 (Jupiter)
                if (groupId.equals("org.junit.jupiter") ||
                    artifactId.contains("junit-jupiter") ||
                    artifactId.equals("junit-jupiter-api") ||
                    artifactId.equals("junit-jupiter-engine") ||
                    artifactId.equals("junit-jupiter")) {
                    context.setJunitVersion(ProjectDependencyContext.JUnitVersion.JUNIT5);
                }

                // Spring Boot Starter Test bundles JUnit 5 + Mockito
                if (artifactId.equals("spring-boot-starter-test")) {
                    context.setSpringBootTestPresent(true);
                    context.setSpringTestPresent(true);
                    // spring-boot-starter-test >= 2.2.0 uses JUnit 5 by default
                    if (context.getJunitVersion() == ProjectDependencyContext.JUnitVersion.UNKNOWN) {
                        context.setJunitVersion(ProjectDependencyContext.JUnitVersion.JUNIT5);
                    }
                    // Also bundles Mockito
                    if (context.getMockFramework() == ProjectDependencyContext.MockFramework.NONE) {
                        context.setMockFramework(ProjectDependencyContext.MockFramework.MOCKITO);
                    }
                }

                // JUnit 4
                if ((groupId.equals("junit") && artifactId.equals("junit")) ||
                    artifactId.equals("junit-vintage-engine")) {
                    // Only set JUnit 4 if JUnit 5 hasn't been detected yet
                    if (context.getJunitVersion() == ProjectDependencyContext.JUnitVersion.UNKNOWN) {
                        context.setJunitVersion(ProjectDependencyContext.JUnitVersion.JUNIT4);
                    }
                }

                // JUnit Platform (indicates JUnit 5 ecosystem)
                if (groupId.equals("org.junit.platform")) {
                    if (context.getJunitVersion() == ProjectDependencyContext.JUnitVersion.UNKNOWN) {
                        context.setJunitVersion(ProjectDependencyContext.JUnitVersion.JUNIT5);
                    }
                }

                // ── Mockito detection ─────────────────────────────────────────

                if (groupId.equals("org.mockito") &&
                    (artifactId.equals("mockito-core") ||
                     artifactId.equals("mockito-junit-jupiter") ||
                     artifactId.equals("mockito-all") ||
                     artifactId.equals("mockito-inline"))) {
                    context.setMockFramework(ProjectDependencyContext.MockFramework.MOCKITO);
                    // Detect version hint
                    if (dep.version != null && !dep.version.startsWith("$")) {
                        context.setMockitoVersion(detectMockitoVersionHint(dep.version));
                    }
                }

                // EasyMock
                if (groupId.equals("org.easymock") && artifactId.equals("easymock")) {
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

                if (groupId.equals("org.assertj") ||
                    artifactId.contains("assertj")) {
                    context.setAssertJPresent(true);
                }

                // ── Hamcrest ──────────────────────────────────────────────────

                if (groupId.equals("org.hamcrest") ||
                    artifactId.contains("hamcrest")) {
                    context.setHamcrestPresent(true);
                }

                // ── TestContainers ────────────────────────────────────────────

                if (groupId.equals("org.testcontainers")) {
                    context.setTestContainersPresent(true);
                }

                // ── H2 ────────────────────────────────────────────────────────

                if (groupId.equals("com.h2database") && artifactId.equals("h2")) {
                    context.setH2Present(true);
                }

                // ── WireMock ──────────────────────────────────────────────────

                if (artifactId.contains("wiremock")) {
                    context.setWireMockPresent(true);
                }

                // ── Awaitility ────────────────────────────────────────────────

                if (groupId.equals("org.awaitility") ||
                    artifactId.contains("awaitility")) {
                    context.setAwaitilityPresent(true);
                }
            }

            context.setAllTestDependencies(allTestDeps);

            LOG.info("TestGen: Maven scan complete → " + context.toDisplayString());

        } catch (Exception e) {
            LOG.error("TestGen: Error scanning pom.xml", e);
            return applyDefaults(context);
        }

        return applyDefaults(context);
    }

    // ────────────────────────────────────────────────────────────────────────
    // Private helpers
    // ────────────────────────────────────────────────────────────────────────

    /**
     * Applies sensible defaults when detection is incomplete.
     * Default: JUnit 5 (modern standard), no mocking assumed.
     */
    private ProjectDependencyContext applyDefaults(ProjectDependencyContext context) {
        if (context.getJunitVersion() == ProjectDependencyContext.JUnitVersion.UNKNOWN) {
            context.setJunitVersion(ProjectDependencyContext.JUnitVersion.JUNIT5);
            LOG.info("TestGen: JUnit version not detected, defaulting to JUnit 5");
        }
        return context;
    }

    /**
     * Finds the pom.xml in the project's base directory.
     */
    private VirtualFile findPomFile(Project project) {
        VirtualFile baseDir = getProjectBaseDir(project);
        if (baseDir == null) return null;
        return baseDir.findChild(POM_XML);
    }

    /**
     * Gets the project's base (root) directory.
     */
    private VirtualFile getProjectBaseDir(Project project) {
        String basePath = project.getBasePath();
        if (basePath == null) return null;

        return com.intellij.openapi.vfs.LocalFileSystem.getInstance()
                .findFileByPath(basePath);
    }

    /**
     * Parses the pom.xml VirtualFile into a DOM Document.
     */
    private Document parseXml(VirtualFile pomFile) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            // Disable external entity processing for security
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", false);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setNamespaceAware(false);

            DocumentBuilder builder = factory.newDocumentBuilder();
            try (InputStream is = pomFile.getInputStream()) {
                return builder.parse(is);
            }
        } catch (Exception e) {
            LOG.error("TestGen: Failed to parse pom.xml", e);
            return null;
        }
    }

    /**
     * Extracts all <dependency> elements from the parsed pom.xml Document.
     */
    private List<DependencyEntry> extractDependencies(Document doc) {
        List<DependencyEntry> result = new ArrayList<>();

        NodeList dependencyNodes = doc.getElementsByTagName("dependency");
        for (int i = 0; i < dependencyNodes.getLength(); i++) {
            Element depElement = (Element) dependencyNodes.item(i);

            String groupId = getChildText(depElement, "groupId");
            String artifactId = getChildText(depElement, "artifactId");
            String version = getChildText(depElement, "version");
            String scope = getChildText(depElement, "scope");

            if (groupId != null && artifactId != null) {
                result.add(new DependencyEntry(
                        groupId.trim(),
                        artifactId.trim(),
                        version != null ? version.trim() : null,
                        scope != null ? scope.trim() : null
                ));
            }
        }

        return result;
    }

    /**
     * Gets the text content of a direct child element by tag name.
     */
    private String getChildText(Element parent, String tagName) {
        NodeList nodes = parent.getElementsByTagName(tagName);
        if (nodes.getLength() > 0) {
            return nodes.item(0).getTextContent();
        }
        return null;
    }

    /**
     * Returns true if a dependency is test-related even without test scope.
     */
    private boolean isTestRelated(String groupId, String artifactId) {
        return groupId.contains("junit") ||
               groupId.contains("mockito") ||
               groupId.contains("testng") ||
               groupId.contains("testcontainers") ||
               groupId.contains("assertj") ||
               groupId.contains("hamcrest") ||
               artifactId.contains("test") ||
               artifactId.contains("mock");
    }

    /**
     * Converts a version string to a user-friendly hint like "5.x".
     */
    private String detectMockitoVersionHint(String version) {
        if (version == null) return null;
        if (version.startsWith("5")) return "5.x";
        if (version.startsWith("4")) return "4.x";
        if (version.startsWith("3")) return "3.x";
        if (version.startsWith("2")) return "2.x";
        if (version.startsWith("1")) return "1.x";
        return version;
    }

    // ────────────────────────────────────────────────────────────────────────
    // Inner DTO
    // ────────────────────────────────────────────────────────────────────────

    private static class DependencyEntry {
        final String groupId;
        final String artifactId;
        final String version;
        final String scope;

        DependencyEntry(String groupId, String artifactId,
                        String version, String scope) {
            this.groupId = groupId;
            this.artifactId = artifactId;
            this.version = version;
            this.scope = scope;
        }
    }
}
