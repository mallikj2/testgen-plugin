package com.testgen.plugin.scanner;

import com.intellij.openapi.project.Project;

/**
 * BuildFileScanner
 *
 * Interface for build file dependency scanners.
 * Implementations: MavenDependencyScanner, GradleDependencyScanner
 */
public interface BuildFileScanner {

    /**
     * Scans the project's build file and extracts dependency context.
     *
     * @param project the current IntelliJ project
     * @return populated ProjectDependencyContext
     */
    ProjectDependencyContext scan(Project project);

    /**
     * Returns true if this scanner can handle the given project
     * (i.e., the relevant build file exists).
     *
     * @param project the current IntelliJ project
     * @return true if applicable
     */
    boolean isApplicable(Project project);
}
