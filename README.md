# TestGen — IntelliJ IDEA Plugin

> **Intelligent JUnit Test Generator powered by GitHub Copilot**

TestGen is an IntelliJ IDEA plugin that automatically generates comprehensive JUnit test classes for your Java code by leveraging GitHub Copilot's AI capabilities. It analyzes your source class structure, detects your project's test framework, and injects a rich, structured prompt into Copilot Chat.

---

## Table of Contents

- [Prerequisites](#prerequisites)
- [Features](#features)
- [Installation](#installation)
- [Usage](#usage)
- [How It Works](#how-it-works)
- [Supported Frameworks](#supported-frameworks)
- [Project Structure](#project-structure)
- [Building from Source](#building-from-source)
- [Architecture](#architecture)
- [Known Limitations](#known-limitations)
- [Roadmap](#roadmap)

---

## Prerequisites

> ⚠️ **TestGen will not function without these prerequisites.**

| Requirement | Details |
|---|---|
| **IntelliJ IDEA** | 2023.1 or later (Community or Ultimate) |
| **GitHub Copilot Plugin** | `com.github.copilot` — installed and **authenticated** |
| **Java** | JDK 17+ |
| **Build System** | Maven (`pom.xml`) or Gradle (`build.gradle` / `build.gradle.kts`) |

### Installing GitHub Copilot
1. In IntelliJ IDEA: **Settings → Plugins → Marketplace**
2. Search for **"GitHub Copilot"**
3. Install and restart
4. Sign in via the status bar Copilot icon

---

## Features

- ✅ **Auto-detects test framework** — JUnit 4 vs JUnit 5 from pom.xml or build.gradle
- ✅ **Auto-detects mock framework** — Mockito, EasyMock, PowerMock
- ✅ **Spring awareness** — detects Spring Boot Test, @SpringBootTest, @MockBean
- ✅ **PSI-based class analysis** — extracts methods, fields, annotations, dependencies
- ✅ **Smart pre-population** — auto-fills test package and class name
- ✅ **Framework detection badge** — shows what was detected before you generate
- ✅ **Optional developer description** — provide extra context for better tests
- ✅ **Scaffolds test file** — creates the correctly placed, compilable test file
- ✅ **Rich Copilot prompt** — structured prompt for maximum test quality
- ✅ **Clipboard fallback** — always reliable prompt delivery
- ✅ **Gradle support** — both Groovy DSL and Kotlin DSL

---

## Installation

### From JetBrains Marketplace (when published)
1. **Settings → Plugins → Marketplace**
2. Search **"TestGen"**
3. Install and restart

### From local build
1. Build the plugin: `./gradlew buildPlugin`
2. The `.zip` artifact is in `build/distributions/`
3. **Settings → Plugins → ⚙️ → Install Plugin from Disk**
4. Select the `.zip` file

---

## Usage

### Triggering TestGen

Three ways to trigger test generation:

| Method | How |
|---|---|
| **Right-click in editor** | Right-click on the Java file → *Generate JUnit Test with Copilot* |
| **Project view** | Right-click on a `.java` file → *Generate JUnit Test with Copilot* |
| **Tools menu** | *Tools → TestGen → Generate JUnit Test with Copilot* |
| **Keyboard shortcut** | `Alt+Shift+T` |

### Step-by-Step Workflow

```
1.  Open or select a Java source class
2.  Trigger TestGen (right-click or Alt+Shift+T)
3.  TestGen checks GitHub Copilot is installed and authenticated
          → If not: clear error dialog shown, plugin stops
4.  TestGen analyzes your class and your pom.xml / build.gradle
5.  The TestGen dialog opens:
      ┌────────────────────────────────────────────────┐
      │  Detected Framework: JUnit 5 + Mockito ✅      │
      │  Source Class: com.example.UserService          │
      │  Test Package: [com.example.service       ]     │
      │  Test Class Name: [UserServiceTest         ]     │
      │  Additional Context: (optional textarea)         │
      │                      [Cancel] [Generate →]       │
      └────────────────────────────────────────────────┘
6.  Click "Generate →"
7.  TestGen creates src/test/java/.../UserServiceTest.java
8.  The test file opens in the editor (scaffold with TODOs)
9.  A structured prompt is copied to your clipboard
10. Open GitHub Copilot Chat (if not already open)
11. Paste the prompt → Copilot generates full test implementations
12. Copy the generated tests into UserServiceTest.java
```

---

## How It Works

### Architecture Overview

```
GenerateTestAction (entry point)
    │
    ├─→ CopilotGateKeeper    — Validates Copilot is installed + authenticated
    ├─→ JavaSourceParser     — Analyzes source class via IntelliJ PSI API
    ├─→ BuildFileScanner     — Scans pom.xml or build.gradle for test deps
    ├─→ TestGenDialog        — Collects package, classname, description from user
    ├─→ TestFileWriter       — Scaffolds and creates the test .java file
    ├─→ PromptBuilder        — Assembles a rich, structured Copilot prompt
    └─→ CopilotChatBridge    — Injects prompt into Copilot Chat
```

### Prompt Strategy (Option B: Gate + Prompt Injection)

TestGen uses **Option B** for Copilot integration:
- Verifies Copilot is installed and authenticated (hard block if not)
- Builds a comprehensive, structured prompt with full class context
- Copies the prompt to clipboard and opens Copilot Chat
- The user pastes and submits the prompt to Copilot Chat
- Copies the generated code into the pre-created test file scaffold

This strategy is chosen because the GitHub Copilot plugin does not expose a public Java API for other plugins to call directly.

---

## Supported Frameworks

### JUnit Versions
| Framework | Detection Source |
|---|---|
| **JUnit 5** (Jupiter) | `org.junit.jupiter:junit-jupiter-api`, `spring-boot-starter-test >= 2.2` |
| **JUnit 4** | `junit:junit`, `junit-vintage-engine` |

### Mock Frameworks
| Framework | Artifact |
|---|---|
| **Mockito** | `org.mockito:mockito-core`, `mockito-junit-jupiter`, `mockito-inline` |
| **EasyMock** | `org.easymock:easymock` |
| **PowerMock** | `org.powermock:*` |

### Additional Test Libraries (auto-detected)
- `spring-boot-starter-test` / `spring-test`
- `org.assertj:assertj-core` → prefers `assertThat()` assertions
- `org.hamcrest:hamcrest`
- `org.testcontainers:*`
- `com.h2database:h2`
- WireMock, Awaitility

---

## Project Structure

```
testgen-plugin/
├── build.gradle.kts                          # Gradle build (Kotlin DSL)
├── settings.gradle.kts
├── src/
│   ├── main/
│   │   ├── java/com/testgen/plugin/
│   │   │   ├── actions/
│   │   │   │   └── GenerateTestAction.java   # Main entry point
│   │   │   ├── gate/
│   │   │   │   └── CopilotGateKeeper.java    # Copilot install/auth check
│   │   │   ├── ui/
│   │   │   │   ├── TestGenDialog.java        # Input dialog (DialogWrapper)
│   │   │   │   └── TestGenDialogModel.java   # Dialog data model
│   │   │   ├── parser/
│   │   │   │   ├── JavaSourceParser.java     # PSI-based class analyzer
│   │   │   │   └── JavaClassContext.java     # Parsed class DTO
│   │   │   ├── scanner/
│   │   │   │   ├── BuildFileScanner.java     # Scanner interface
│   │   │   │   ├── MavenDependencyScanner.java
│   │   │   │   ├── GradleDependencyScanner.java
│   │   │   │   └── ProjectDependencyContext.java
│   │   │   ├── prompt/
│   │   │   │   └── PromptBuilder.java        # Copilot prompt assembler
│   │   │   ├── copilot/
│   │   │   │   └── CopilotChatBridge.java    # Copilot Chat integration
│   │   │   ├── writer/
│   │   │   │   └── TestFileWriter.java       # Test file scaffold writer
│   │   │   └── util/
│   │   │       ├── NotificationUtil.java
│   │   │       └── ProjectUtil.java
│   │   └── resources/
│   │       ├── META-INF/plugin.xml
│   │       └── icons/testgen.svg
│   └── test/
│       └── java/com/testgen/plugin/
│           ├── prompt/PromptBuilderTest.java
│           ├── ui/TestGenDialogModelTest.java
│           ├── parser/JavaClassContextTest.java
│           └── scanner/ProjectDependencyContextTest.java
```

---

## Building from Source

### Requirements
- JDK 17+
- Internet access (to download IntelliJ Platform dependencies)

### Commands

```bash
# Clone the repo
git clone https://github.com/mallikj2/testgen-plugin.git
cd testgen-plugin

# Run tests
./gradlew test

# Build the plugin zip
./gradlew buildPlugin

# Run plugin in a sandboxed IDE (for development)
./gradlew runIde

# Verify the plugin
./gradlew verifyPlugin
```

The built plugin zip will be at:
```
build/distributions/testgen-plugin-1.0.0.zip
```

---

## Architecture

### Key Design Decisions

#### 1. PSI for Class Analysis
IntelliJ's **Program Structure Interface (PSI)** gives us IDE-level, always-accurate access to class structure. This is far more reliable than regex-based parsing and handles generics, inner classes, annotations, and all Java syntax correctly.

#### 2. One Scanner Interface, Two Implementations
`BuildFileScanner` is an interface with `MavenDependencyScanner` and `GradleDependencyScanner` implementations. Adding support for a new build system (Bazel, SBT, etc.) requires only a new implementation — zero changes to the action or prompt builder.

#### 3. Isolated Copilot Interaction
All Copilot interaction is isolated in `CopilotChatBridge`. When GitHub updates the Copilot plugin and action IDs change, only this one class needs updating.

#### 4. Rich Prompt Engineering
The `PromptBuilder` produces a highly structured prompt with labeled sections. This is critical — the quality of the generated tests is directly proportional to the quality of the prompt. The prompt includes:
- Full source code
- Extracted method signatures
- Detected dependencies (with constraints)
- Framework-specific instructions
- Coverage requirements (happy path, edge cases, nulls, exceptions)
- Output format constraints (Java only, no markdown)

---

## Known Limitations

| Limitation | Notes |
|---|---|
| Copilot API is private | GitHub Copilot plugin exposes no public Java API; integration relies on action discovery and clipboard |
| Phase 1 is semi-manual | User must paste prompt into Copilot Chat and copy code back into the file |
| pom.xml Level 1 only | Parent POM and effective-POM resolution not yet supported |
| Single-module projects | Multi-module Maven/Gradle projects use the root build file only |
| Gradle version catalogs | `libs.versions.toml` is not deeply parsed (Phase 2) |
| Java only | Kotlin source files are not supported in this version |

---

## Roadmap

### Phase 2
- [ ] Auto-capture Copilot Chat response and write directly to test file
- [ ] Parent POM resolution (Level 2 Maven scanning)
- [ ] Gradle version catalog (`libs.versions.toml`) support
- [ ] Multi-module project support
- [ ] Kotlin source file support
- [ ] Settings page (default test naming convention, preferred assertion style)
- [ ] Test run button directly from the generated file

### Phase 3
- [ ] Batch mode — generate tests for all classes in a package
- [ ] Test coverage gap analysis — generate tests only for uncovered methods
- [ ] CI integration hints in generated test code

---

## License

MIT License — see [LICENSE](LICENSE) for details.

---

## Contributing

Pull requests are welcome. Please read [CONTRIBUTING.md](CONTRIBUTING.md) before submitting.

---

*Built with ❤️ using IntelliJ Platform SDK + GitHub Copilot*
