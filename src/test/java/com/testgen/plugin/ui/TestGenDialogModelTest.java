package com.testgen.plugin.ui;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Unit tests for TestGenDialogModel.
 */
public class TestGenDialogModelTest {

    @Test
    public void isValid_withPackageAndClassName_returnsTrue() {
        TestGenDialogModel model = new TestGenDialogModel(
                "com.example.service", "UserServiceTest", null
        );
        assertTrue(model.isValid());
    }

    @Test
    public void isValid_withEmptyPackage_returnsFalse() {
        TestGenDialogModel model = new TestGenDialogModel("", "UserServiceTest", null);
        assertFalse(model.isValid());
    }

    @Test
    public void isValid_withEmptyClassName_returnsFalse() {
        TestGenDialogModel model = new TestGenDialogModel("com.example", "", null);
        assertFalse(model.isValid());
    }

    @Test
    public void isValid_withNullPackage_returnsFalse() {
        TestGenDialogModel model = new TestGenDialogModel(null, "UserServiceTest", null);
        assertFalse(model.isValid());
    }

    @Test
    public void getFullyQualifiedTestClassName_withPackageAndClass_returnsCombined() {
        TestGenDialogModel model = new TestGenDialogModel(
                "com.example.service", "UserServiceTest", null
        );
        assertEquals("com.example.service.UserServiceTest",
                model.getFullyQualifiedTestClassName());
    }

    @Test
    public void getFullyQualifiedTestClassName_withEmptyPackage_returnsClassNameOnly() {
        TestGenDialogModel model = new TestGenDialogModel("", "UserServiceTest", null);
        assertEquals("UserServiceTest", model.getFullyQualifiedTestClassName());
    }

    @Test
    public void getDescription_whenNullDescription_returnsNull() {
        TestGenDialogModel model = new TestGenDialogModel("com.example", "Test", null);
        assertNull(model.getDescription());
    }

    @Test
    public void getDescription_whenDescriptionSet_returnsDescription() {
        TestGenDialogModel model = new TestGenDialogModel(
                "com.example", "Test", "Focus on null safety."
        );
        assertEquals("Focus on null safety.", model.getDescription());
    }
}
