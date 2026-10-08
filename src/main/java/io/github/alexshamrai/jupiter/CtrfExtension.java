package io.github.alexshamrai.jupiter;

import io.github.alexshamrai.CtrfReportManager;
import io.github.alexshamrai.adapter.ExtensionContextAdapter;
import io.github.alexshamrai.model.TestDetails;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.TestWatcher;

import java.util.Optional;

/**
 * JUnit 5 extension that generates test reports in the CTRF (Common Test Report Format) format.
 * <p>
 * This extension tracks test execution, captures test results, and generates a JSON report
 * following the CTRF standard. It handles test statuses, and captures relevant test metadata.
 * <p>
 * To use this extension, simply add it to your test class using the {@code @ExtendWith} annotation:
 * <pre>
 * {@code
 * @ExtendWith(CtrfExtension.class)
 * public class MyTest {
 * // test methods
 * }
 * }
 * </pre>
 * <p>
 * A failure of a whole test class is reported as one failed entry for that class: "initializationError"
 * when the class failed before any of its tests started (for example in {@code @BeforeAll} or in another
 * extension's setup), "teardownError" when it failed afterwards (for example in {@code @AfterAll}).
 * The extension cannot see failures of containers below class level, such as a {@code @MethodSource}
 * that throws, nor teardown failures of extensions registered before it; the JUnit Platform listener
 * {@code io.github.alexshamrai.launcher.CtrfListener} reports those.
 * <p>
 * The extension can be configured through a {@code ctrf.properties} file placed in the classpath.
 * See the README for all available configuration options.
 */
public class CtrfExtension implements TestRunExtension, AfterAllCallback, BeforeEachCallback, TestWatcher {

    private static final ExtensionContext.Namespace NAMESPACE = ExtensionContext.Namespace.create(CtrfExtension.class);
    private final CtrfReportManager reportManager = CtrfReportManager.getInstance();
    private static final String GENERATED_BY = "io.github.alexshamrai.jupiter.CtrfExtension";
    private static final String INITIALIZATION_ERROR = "initializationError";
    private static final String TEARDOWN_ERROR = "teardownError";

    @Override
    public void beforeAll(ExtensionContext context) {
        TestRunExtension.super.beforeAll(context);
        context.getParent().ifPresent(CtrfExtension::markChildrenStarted);
        context.getStore(NAMESPACE).put(context.getUniqueId(), new ClassState(System.currentTimeMillis()));
    }

    /**
     * Reports the class as failed if it failed outside its tests. Runs after the class's {@code @AfterAll}
     * methods, and also when its setup failed.
     *
     * @param context the context of the finished test class
     */
    @Override
    public void afterAll(ExtensionContext context) {
        context.getExecutionException().ifPresent(error -> reportClassFailure(context, error));
    }

    @Override
    public void beforeAllTests(ExtensionContext context) {
        reportManager.startTestRun(GENERATED_BY);
    }

    @Override
    public void afterAllTests(ExtensionContext context) {
        reportManager.finishTestRun();
    }

    @Override
    public void beforeEach(ExtensionContext context) {
        markChildrenStarted(context);
        reportManager.onTestStart(createTestDetails(context));
    }

    @Override
    public void testSuccessful(ExtensionContext context) {
        reportManager.onTestSuccess(context.getUniqueId());
    }

    @Override
    public void testFailed(ExtensionContext context, Throwable cause) {
        reportManager.onTestFailure(context.getUniqueId(), cause);
    }

    @Override
    public void testAborted(ExtensionContext context, Throwable cause) {
        reportManager.onTestAborted(context.getUniqueId(), cause);
    }

    @Override
    public void testDisabled(ExtensionContext context, Optional<String> reason) {
        reportManager.onTestSkipped(createTestDetails(context), reason);
    }

    private void reportClassFailure(ExtensionContext context, Throwable error) {
        ClassState state = classStateOf(context);
        String name = state != null && state.childrenStarted() ? TEARDOWN_ERROR : INITIALIZATION_ERROR;
        long startTime = state != null ? state.startTime() : System.currentTimeMillis();
        String uniqueId = context.getUniqueId() + "/" + name;
        String className = context.getTestClass()
            .map(Class::getName)
            .orElse(context.getDisplayName());

        reportManager.onTestStart(new TestDetails(startTime, context.getTags(), className, uniqueId, name));
        reportManager.onTestFailure(uniqueId, error);
    }

    /**
     * Marks the nearest enclosing class, starting at the given context, as having started tests.
     */
    private static void markChildrenStarted(ExtensionContext from) {
        for (Optional<ExtensionContext> context = Optional.of(from); context.isPresent(); context = context.get().getParent()) {
            ClassState state = classStateOf(context.get());
            if (state != null) {
                state.markChildrenStarted();
                return;
            }
        }
    }

    // Keyed by the class's own unique ID: the store lookup falls back to parent stores, and a nested
    // class must not pick up its outer class's state.
    private static ClassState classStateOf(ExtensionContext context) {
        return context.getStore(NAMESPACE).get(context.getUniqueId(), ClassState.class);
    }

    private TestDetails createTestDetails(ExtensionContext context) {
        return TestDetails.fromAdapter(new ExtensionContextAdapter(context));
    }
}
