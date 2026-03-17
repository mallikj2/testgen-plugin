package com.testgen.plugin.ui;

/**
 * TestGenDialogModel
 *
 * Holds the user's input collected from the TestGenDialog.
 * Passed to PromptBuilder and TestFileWriter.
 */
public class TestGenDialogModel {

    /** The target package for the generated test class (e.g., com.example.service) */
    private String testPackage;

    /** The name of the generated test class (e.g., UserServiceTest) */
    private String testClassName;

    /**
     * Optional developer description providing additional context about the
     * class under test — what it does, what edge cases to focus on, etc.
     */
    private String description;

    public TestGenDialogModel() {}

    public TestGenDialogModel(String testPackage, String testClassName, String description) {
        this.testPackage = testPackage;
        this.testClassName = testClassName;
        this.description = description;
    }

    public String getTestPackage() { return testPackage; }
    public void setTestPackage(String testPackage) { this.testPackage = testPackage; }

    public String getTestClassName() { return testClassName; }
    public void setTestClassName(String testClassName) { this.testClassName = testClassName; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    /**
     * Returns true if required fields are filled in.
     */
    public boolean isValid() {
        return testPackage != null && !testPackage.trim().isEmpty() &&
               testClassName != null && !testClassName.trim().isEmpty();
    }

    /**
     * Returns the fully qualified test class name.
     * e.g., com.example.service.UserServiceTest
     */
    public String getFullyQualifiedTestClassName() {
        if (testPackage == null || testPackage.trim().isEmpty()) {
            return testClassName;
        }
        return testPackage + "." + testClassName;
    }

    @Override
    public String toString() {
        return "TestGenDialogModel{" +
               "testPackage='" + testPackage + '\'' +
               ", testClassName='" + testClassName + '\'' +
               ", hasDescription=" + (description != null && !description.isEmpty()) +
               '}';
    }
}
