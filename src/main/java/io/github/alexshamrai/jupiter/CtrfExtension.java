package io.github.alexshamrai.jupiter;

import io.github.alexshamrai.CtrfReportManager;
import io.github.alexshamrai.adapter.ExtensionContextAdapter;
import io.github.alexshamrai.model.TestDetails;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.TestWatcher;
import org.opentest4j.TestAbortedException;

import java.util.List;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

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
 * A failure of a whole test class is reported as one failed entry for that class, identified by the class's
 * unique ID: "initializationError" when the class failed before any of its tests started or were skipped
 * (for example in {@code @BeforeAll} or in another extension's {@code BeforeAllCallback}), "teardownError"
 * when it failed afterwards (for example in {@code @AfterAll}). A class that aborts, with a failed assumption, isn't a
 * failure: aborted before its tests started, it is one skipped "initializationError" entry, as an aborted test is a
 * skipped one; aborted afterwards, its tests keep their results and it adds no entry, as JUnit reports it.
 * <p>
 * The extension learns about a class failure only when JUnit runs the class's {@code AfterAllCallback}s,
 * so it cannot see:
 * <ul>
 *   <li>a class that could not be created: with {@code @TestInstance(Lifecycle.PER_CLASS)}, a failing
 *       constructor or {@code TestInstancePostProcessor} (for example a Spring context that fails to load)
 *       stops the class before any callback runs, and the report shows no failure for it;</li>
 *   <li>failures of containers below class level, such as a {@code @MethodSource} that throws;</li>
 *   <li>teardown failures of extensions registered before it, whose callbacks run after its own.</li>
 * </ul>
 * The JUnit Platform listener {@code io.github.alexshamrai.launcher.CtrfListener} reports all of these.
 * <p>
 * Tests of a class that JUnit skips as a whole, such as a {@code @Disabled} class or a class disabled by a
 * class-level condition, never reach the extension and are missing from its report. The JUnit Platform
 * listener {@code io.github.alexshamrai.launcher.CtrfListener} reports them as skipped.
 * <p>
 * Only one run is reported at a time. The run belongs to the root context that started it; the extension reports
 * nothing for the classes of a JUnit run that a test starts itself, and the run around it goes on.
 * <p>
 * The reporter never changes the outcome of a test or a class: if its own work fails in any callback, the error is
 * logged as a warning through {@code java.util.logging}, and the test runs on. Only a {@link VirtualMachineError},
 * such as an {@link OutOfMemoryError}, propagates.
 * <p>
 * The extension can be configured through a {@code ctrf.properties} file placed in the classpath.
 * See the README for all available configuration options.
 *
 * @deprecated since 0.5.2, to be removed in 0.6.0. Register {@code io.github.alexshamrai.launcher.CtrfListener}
 *     instead, which also reports what this extension can't see: add
 *     {@code src/test/resources/META-INF/services/org.junit.platform.launcher.TestExecutionListener} containing the
 *     line {@code io.github.alexshamrai.launcher.CtrfListener}, and remove {@code @ExtendWith(CtrfExtension.class)}.
 */
@Deprecated(since = "0.5.2")
public class CtrfExtension implements TestRunExtension, AfterAllCallback, BeforeEachCallback, TestWatcher {

    /**
     * Key, in the root context's store, of whether that root's run is the one being reported.
     */
    static final String OWNS_RUN = "ownsRun";

    private static final ExtensionContext.Namespace NAMESPACE = ExtensionContext.Namespace.create(CtrfExtension.class);
    private static final Logger LOGGER = Logger.getLogger(CtrfExtension.class.getName());
    private static final String GENERATED_BY = "io.github.alexshamrai.jupiter.CtrfExtension";
    private static final String INITIALIZATION_ERROR = "initializationError";
    private static final String TEARDOWN_ERROR = "teardownError";
    private static final String DEPRECATION_WARNING = "CtrfExtension is deprecated and will be removed in 0.6.0: register"
        + " CtrfListener instead, as https://github.com/alexshamrai/junit-ctrf-reporter#recommended-ctrflistener shows";

    private final CtrfReportManager reportManager;

    /**
     * Creates the extension, reporting through the JVM's report manager. JUnit calls this constructor.
     */
    public CtrfExtension() {
        this(CtrfReportManager.getInstance());
    }

    /**
     * Creates the extension with the report manager it reports through, for tests.
     */
    CtrfExtension(CtrfReportManager reportManager) {
        this.reportManager = reportManager;
    }

    @Override
    public void beforeAll(ExtensionContext context) {
        guarded("beforeAll", context, () -> startClass(context));
    }

    private void startClass(ExtensionContext context) {
        TestRunExtension.super.beforeAll(context);
        if (ownsRun(context)) {
            context.getParent().ifPresent(CtrfExtension::markChildrenStarted);
            context.getStore(NAMESPACE).put(context.getUniqueId(), new ClassState(System.currentTimeMillis()));
        }
    }

    /**
     * Reports the class as failed if it failed outside its tests, or as skipped if it aborted before they started,
     * and otherwise records that it succeeded, so that its failure in an earlier retry round is reported as
     * recovered: passed and flaky. Runs after the class's {@code @AfterAll} methods, and also when a
     * {@code @BeforeAll} method or a {@code BeforeAllCallback} failed, but not when the class could not be created
     * (see the class documentation).
     *
     * @param context the context of the finished test class
     */
    @Override
    public void afterAll(ExtensionContext context) {
        reportIfOwner("afterAll", context, () -> context.getExecutionException().ifPresentOrElse(
            error -> reportClassFailure(context, error),
            () -> reportClassSuccess(context)));
    }

    /**
     * Starts the run, owned by the root context, and stores in the root's store whether it got the run. A root whose
     * run start is refused, because another run is active, belongs to a JUnit run started from inside a test. Logs,
     * once per run, that the extension is deprecated.
     *
     * @param context the context of the run's first class
     */
    @Override
    public void beforeAllTests(ExtensionContext context) {
        guarded("beforeAllTests", context, () -> {
            LOGGER.warning(DEPRECATION_WARNING);
            ExtensionContext root = context.getRoot();
            root.getStore(NAMESPACE).put(OWNS_RUN, reportManager.startTestRun(root, GENERATED_BY));
        });
    }

    @Override
    public void afterAllTests(ExtensionContext context) {
        reportIfOwner("afterAllTests", context, () -> reportManager.finishTestRun(context.getRoot()));
    }

    @Override
    public void beforeEach(ExtensionContext context) {
        reportIfOwner("beforeEach", context, () -> {
            markChildrenStarted(context);
            reportManager.onTestStart(createTestDetails(context));
        });
    }

    // The watcher callbacks pass the test's full identity: when another extension failed before this
    // extension's beforeEach ran, no start was recorded and the identity would otherwise be lost.
    // For the same reason, and because beforeEach never runs for a disabled test, they also mark the class
    // as having started tests.
    @Override
    public void testSuccessful(ExtensionContext context) {
        reportIfOwner("testSuccessful", context, () -> {
            markChildrenStarted(context);
            reportManager.onTestSuccess(createTestDetails(context));
        });
    }

    @Override
    public void testFailed(ExtensionContext context, Throwable cause) {
        reportIfOwner("testFailed", context, () -> {
            markChildrenStarted(context);
            reportManager.onTestFailure(createTestDetails(context), cause);
        });
    }

    @Override
    public void testAborted(ExtensionContext context, Throwable cause) {
        reportIfOwner("testAborted", context, () -> {
            markChildrenStarted(context);
            reportManager.onTestAborted(createTestDetails(context), cause);
        });
    }

    @Override
    public void testDisabled(ExtensionContext context, Optional<String> reason) {
        reportIfOwner("testDisabled", context, () -> {
            markChildrenStarted(context);
            reportManager.onTestSkipped(createTestDetails(context), reason);
        });
    }

    private void reportClassFailure(ExtensionContext context, Throwable error) {
        ClassState state = classStateOf(context);
        boolean childrenStarted = state != null && state.childrenStarted();
        boolean aborted = error instanceof TestAbortedException;
        if (aborted && childrenStarted) {
            return;
        }
        String name = childrenStarted ? TEARDOWN_ERROR : INITIALIZATION_ERROR;
        long startTime = state != null ? state.startTime() : System.currentTimeMillis();
        String uniqueId = context.getUniqueId();
        String className = context.getTestClass()
            .map(Class::getName)
            .orElse(context.getDisplayName());

        List<String> suite = new ExtensionContextAdapter(context).getSuiteOfChildren();
        reportManager.onTestStart(new TestDetails(startTime, context.getTags(), className, uniqueId, name, suite));
        if (aborted) {
            reportManager.onTestAborted(uniqueId, error);
        } else {
            reportManager.onTestFailure(uniqueId, error);
        }
    }

    private void reportClassSuccess(ExtensionContext context) {
        ClassState state = classStateOf(context);
        reportManager.onContainerSuccess(context.getUniqueId(), state != null ? state.startTime() : null);
    }

    /**
     * Runs reporter work, guarded, only for a context whose root owns the run.
     */
    private static void reportIfOwner(String callback, ExtensionContext context, Runnable work) {
        guarded(callback, context, () -> {
            if (ownsRun(context)) {
                work.run();
            }
        });
    }

    private static boolean ownsRun(ExtensionContext context) {
        return Boolean.TRUE.equals(context.getRoot().getStore(NAMESPACE).get(OWNS_RUN, Boolean.class));
    }

    /**
     * Runs reporter work so that it can't change the outcome of a test or a class: anything it throws is logged as a
     * warning and dropped, except a {@link VirtualMachineError}, which the JVM can't recover from.
     */
    private static void guarded(String callback, ExtensionContext context, Runnable work) {
        try {
            work.run();
        } catch (VirtualMachineError e) {
            throw e;
        } catch (Throwable e) {
            LOGGER.log(Level.WARNING, e, () -> "The CTRF reporter failed in " + callback + " of " + uniqueIdOf(context)
                + "; the test's outcome is unchanged, but the report may miss it");
        }
    }

    private static String uniqueIdOf(ExtensionContext context) {
        try {
            return context.getUniqueId();
        } catch (RuntimeException e) {
            return "a context without a unique ID";
        }
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
