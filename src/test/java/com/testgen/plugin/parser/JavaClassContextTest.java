package com.testgen.plugin.parser;

import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.*;

/**
 * Unit tests for JavaClassContext and its inner DTOs.
 */
public class JavaClassContextTest {

    private JavaClassContext context;

    @Before
    public void setUp() {
        context = new JavaClassContext();
        context.setSimpleClassName("UserService");
        context.setPackageName("com.example.service");
        context.setFullyQualifiedClassName("com.example.service.UserService");
        context.setClassAnnotations(Arrays.asList("@Service", "@Transactional"));
    }

    // ── Spring component detection ───────────────────────────────────────────

    @Test
    public void isSpringComponent_withServiceAnnotation_returnsTrue() {
        context.setClassAnnotations(Arrays.asList("@Service"));
        assertTrue(context.isSpringComponent());
    }

    @Test
    public void isSpringComponent_withRestControllerAnnotation_returnsTrue() {
        context.setClassAnnotations(Arrays.asList("@RestController"));
        assertTrue(context.isSpringComponent());
    }

    @Test
    public void isSpringComponent_withNoSpringAnnotation_returnsFalse() {
        context.setClassAnnotations(Arrays.asList("@Override"));
        assertFalse(context.isSpringComponent());
    }

    @Test
    public void isSpringComponent_withEmptyAnnotations_returnsFalse() {
        context.setClassAnnotations(Collections.emptyList());
        assertFalse(context.isSpringComponent());
    }

    // ── JPA entity detection ─────────────────────────────────────────────────

    @Test
    public void isJpaEntity_withEntityAnnotation_returnsTrue() {
        context.setClassAnnotations(Arrays.asList("@Entity"));
        assertTrue(context.isJpaEntity());
    }

    @Test
    public void isJpaEntity_withoutEntityAnnotation_returnsFalse() {
        context.setClassAnnotations(Arrays.asList("@Service"));
        assertFalse(context.isJpaEntity());
    }

    // ── Public method filtering ──────────────────────────────────────────────

    @Test
    public void getPublicMethods_withMixedVisibility_returnsOnlyPublic() {
        JavaClassContext.MethodInfo publicMethod = new JavaClassContext.MethodInfo(
                "doSomething", "void", Collections.emptyList(),
                Collections.emptyList(), Collections.emptyList(),
                true, false, false, "public"
        );
        JavaClassContext.MethodInfo privateMethod = new JavaClassContext.MethodInfo(
                "internalMethod", "void", Collections.emptyList(),
                Collections.emptyList(), Collections.emptyList(),
                false, false, false, "private"
        );
        context.setMethods(Arrays.asList(publicMethod, privateMethod));

        List<JavaClassContext.MethodInfo> publicMethods = context.getPublicMethods();
        assertEquals(1, publicMethods.size());
        assertEquals("doSomething", publicMethods.get(0).getName());
    }

    @Test
    public void getPublicMethodCount_withThreePublicMethods_returnsThree() {
        JavaClassContext.MethodInfo m1 = new JavaClassContext.MethodInfo(
                "method1", "void", Collections.emptyList(),
                Collections.emptyList(), Collections.emptyList(),
                true, false, false, "public"
        );
        JavaClassContext.MethodInfo m2 = new JavaClassContext.MethodInfo(
                "method2", "String", Collections.emptyList(),
                Collections.emptyList(), Collections.emptyList(),
                true, false, false, "public"
        );
        JavaClassContext.MethodInfo m3 = new JavaClassContext.MethodInfo(
                "method3", "int", Collections.emptyList(),
                Collections.emptyList(), Collections.emptyList(),
                false, false, false, "private"
        );
        context.setMethods(Arrays.asList(m1, m2, m3));
        assertEquals(2, context.getPublicMethodCount());
    }

    // ── Injected dependency filtering ────────────────────────────────────────

    @Test
    public void getInjectedDependencies_withAutowiredField_returnsIt() {
        JavaClassContext.FieldInfo autowired = new JavaClassContext.FieldInfo(
                "userRepo", "UserRepository",
                Arrays.asList("@Autowired"), true, false, false
        );
        JavaClassContext.FieldInfo plain = new JavaClassContext.FieldInfo(
                "maxRetries", "int",
                Collections.emptyList(), true, false, true
        );
        context.setFields(Arrays.asList(autowired, plain));

        List<JavaClassContext.FieldInfo> injected = context.getInjectedDependencies();
        assertEquals(1, injected.size());
        assertEquals("userRepo", injected.get(0).getName());
    }

    @Test
    public void getInjectedDependencies_withInjectAnnotation_returnsIt() {
        JavaClassContext.FieldInfo injectField = new JavaClassContext.FieldInfo(
                "emailService", "EmailService",
                Arrays.asList("@Inject"), true, false, false
        );
        context.setFields(Arrays.asList(injectField));

        List<JavaClassContext.FieldInfo> injected = context.getInjectedDependencies();
        assertEquals(1, injected.size());
    }

    // ── FieldInfo.isInjected ─────────────────────────────────────────────────

    @Test
    public void fieldInfo_isInjected_withAutowired_returnsTrue() {
        JavaClassContext.FieldInfo field = new JavaClassContext.FieldInfo(
                "repo", "Repository", Arrays.asList("@Autowired"),
                true, false, false
        );
        assertTrue(field.isInjected());
    }

    @Test
    public void fieldInfo_isInjected_withNoAnnotation_returnsFalse() {
        JavaClassContext.FieldInfo field = new JavaClassContext.FieldInfo(
                "count", "int", Collections.emptyList(),
                true, false, false
        );
        assertFalse(field.isInjected());
    }

    // ── MethodInfo.toSignature ────────────────────────────────────────────────

    @Test
    public void methodInfo_toSignature_withParameters_buildsCorrectSignature() {
        JavaClassContext.MethodInfo method = new JavaClassContext.MethodInfo(
                "findById", "User",
                Arrays.asList(new JavaClassContext.ParameterInfo("id", "Long", Collections.emptyList())),
                Collections.emptyList(),
                Collections.emptyList(),
                true, false, false, "public"
        );
        String sig = method.toSignature();
        assertTrue("Signature should contain method name", sig.contains("findById"));
        assertTrue("Signature should contain return type", sig.contains("User"));
        assertTrue("Signature should contain parameter", sig.contains("Long"));
        assertTrue("Signature should contain access modifier", sig.contains("public"));
    }

    @Test
    public void methodInfo_toSignature_withThrownException_includesThrows() {
        JavaClassContext.MethodInfo method = new JavaClassContext.MethodInfo(
                "processPayment", "void",
                Collections.emptyList(),
                Collections.emptyList(),
                Arrays.asList("PaymentException"),
                true, false, false, "public"
        );
        String sig = method.toSignature();
        assertTrue("Signature should contain throws", sig.contains("throws"));
        assertTrue("Signature should contain exception name", sig.contains("PaymentException"));
    }

    @Test
    public void methodInfo_isVoid_withVoidReturn_returnsTrue() {
        JavaClassContext.MethodInfo method = new JavaClassContext.MethodInfo(
                "doAction", "void", Collections.emptyList(),
                Collections.emptyList(), Collections.emptyList(),
                true, false, false, "public"
        );
        assertTrue(method.isVoid());
    }

    @Test
    public void methodInfo_isVoid_withNonVoidReturn_returnsFalse() {
        JavaClassContext.MethodInfo method = new JavaClassContext.MethodInfo(
                "getValue", "String", Collections.emptyList(),
                Collections.emptyList(), Collections.emptyList(),
                true, false, false, "public"
        );
        assertFalse(method.isVoid());
    }
}
