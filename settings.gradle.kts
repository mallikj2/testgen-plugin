rootProject.name = "testgen-plugin"

plugins {
    id("org.jetbrains.intellij.platform.settings") version "2.2.1"
}

dependencyResolutionManagement {
    repositoriesMode = RepositoriesMode.FAIL_ON_PROJECT_REPOS

    repositories {
        mavenCentral()
        intellijPlatform {
            defaultRepositories()
        }
    }
}
