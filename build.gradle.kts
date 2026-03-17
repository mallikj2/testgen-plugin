import org.jetbrains.intellij.platform.gradle.TestFrameworkType

plugins {
    id("java")
    id("org.jetbrains.intellij.platform") version "2.2.1"
}

group = "com.testgen.plugin"
version = "1.0.0"

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

dependencies {
    intellijPlatform {
        intellijIdeaCommunity("2023.3.6")
        bundledPlugin("com.intellij.java")
        testFramework(TestFrameworkType.Platform)
    }

    // XML parsing for pom.xml
    implementation("org.dom4j:dom4j:2.1.4")

    // Gradle file parsing (simple text-based for build.gradle)
    implementation("org.apache.commons:commons-lang3:3.14.0")

    testImplementation("junit:junit:4.13.2")
}

intellijPlatform {
    pluginConfiguration {
        id = "com.testgen.plugin"
        name = "TestGen"
        version = "1.0.0"
        description = """
            TestGen automatically generates comprehensive JUnit test classes for your Java code.
            Works in conjunction with the GitHub Copilot plugin to produce intelligent,
            context-aware tests covering all use cases.
            
            Requirements:
            - GitHub Copilot plugin (com.github.copilot) must be installed and authenticated
            - Java project with Maven (pom.xml) or Gradle (build.gradle / build.gradle.kts)
        """.trimIndent()

        ideaVersion {
            sinceBuild = "233"
            untilBuild = "243.*"
        }

        vendor {
            name = "TestGen"
            email = "support@testgen.dev"
        }
    }

    signing {
        certificateChain = providers.environmentVariable("CERTIFICATE_CHAIN")
        privateKey = providers.environmentVariable("PRIVATE_KEY")
        password = providers.environmentVariable("PRIVATE_KEY_PASSWORD")
    }

    publishing {
        token = providers.environmentVariable("PUBLISH_TOKEN")
    }
}

tasks {
    compileJava {
        options.encoding = "UTF-8"
    }

    test {
        useJUnit()
    }
}
