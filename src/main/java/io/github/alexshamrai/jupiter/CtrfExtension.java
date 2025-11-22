package io.github.alexshamrai.jupiter;

import io.github.alexshamrai.CtrfReportManager;
import io.github.alexshamrai.adapter.ExtensionContextAdapter;
import io.github.alexshamrai.model.TestDetails;
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
 * The extension can be configured through a {@code ctrf.properties} file placed in the classpath.
 * See the README for all available configuration options.
 */
public class CtrfExtension implements TestRunExtension, BeforeEachCallback, TestWatcher {

    private final CtrfReportManager reportManager = CtrfReportManager.getInstance();
    private static final String GENERATED_BY = "io.github.alexshamrai.jupiter.CtrfExtension";

    @Override
    public void beforeAllTests(ExtensionContext context) {
        reportManager.startTestRun(GENERATED_BY);
    }

    @Override
    public void afterAllTests(ExtensionContext context) {
        reportManager.finishTestRun(Optional.of(context));
    }

    @Override
    public void beforeEach(ExtensionContext context) {
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

    private TestDetails createTestDetails(ExtensionContext context) {
        return TestDetails.fromAdapter(new ExtensionContextAdapter(context));
    }
}