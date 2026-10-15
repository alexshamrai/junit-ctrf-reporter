package io.github.alexshamrai.jupiter;

import io.github.alexshamrai.CtrfReportManager;
import io.github.alexshamrai.LogCapture;
import io.github.alexshamrai.model.TestDetails;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.platform.testkit.engine.EngineExecutionResults;
import org.junit.platform.testkit.engine.EngineTestKit;

import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.platform.engine.discovery.DiscoverySelectors.selectClass;
import static org.junit.platform.testkit.engine.EventConditions.event;
import static org.junit.platform.testkit.engine.EventConditions.finishedWithFailure;
import static org.junit.platform.testkit.engine.EventConditions.test;
import static org.junit.platform.testkit.engine.TestExecutionResultConditions.instanceOf;
import static org.junit.platform.testkit.engine.TestExecutionResultConditions.message;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Reporter errors in the extension's callbacks never change a test's outcome (MP-7). Each fixture registers the
 * extension with a report manager that throws in one place, and runs on the Jupiter engine through EngineTestKit.
 * The fixtures are tagged out of the normal test run.
 */
class CtrfExtensionGuardTest {

    @RegisterExtension
    final LogCapture logs = new LogCapture(CtrfExtension.class);

    @org.junit.jupiter.api.Test
    void testPassesWhenTheReporterThrowsAsTheTestStarts() {
        var results = run(ThrowsOnTestStart.class);

        results.testEvents().assertStatistics(stats -> stats.started(1).succeeded(1).failed(0));
        assertThat(logs.text()).contains("failed in beforeEach of");
    }

    @org.junit.jupiter.api.Test
    void testFailsWithItsOwnErrorWhenTheReporterThrowsWhileRecordingTheFailure() {
        var results = run(ThrowsOnTestFailure.class);

        results.testEvents().assertThatEvents().haveExactly(1,
            event(test("fails"), finishedWithFailure(instanceOf(AssertionError.class), message("the test's own failure"))));
        assertThat(logs.text()).contains("failed in testFailed of");
    }

    @org.junit.jupiter.api.Test
    void classRunsWhenTheReporterThrowsAsTheRunStarts() {
        var results = run(ThrowsOnRunStart.class);

        results.testEvents().assertStatistics(stats -> stats.started(2).succeeded(2).failed(0));
        results.containerEvents().assertStatistics(stats -> stats.failed(0));
        assertThat(logs.text()).contains("failed in beforeAllTests of");
    }

    @org.junit.jupiter.api.Test
    void classSucceedsWhenTheReporterThrowsInAfterAll() {
        var results = run(ThrowsOnClassEnd.class);

        results.testEvents().assertStatistics(stats -> stats.started(2).succeeded(2).failed(0));
        results.containerEvents().assertStatistics(stats -> stats.failed(0));
        assertThat(logs.text()).contains("failed in afterAll of");
    }

    @org.junit.jupiter.api.Test
    void engineSucceedsWhenTheReporterThrowsWhileWritingTheReport() {
        var results = run(ThrowsOnReportWrite.class);

        results.testEvents().assertStatistics(stats -> stats.started(1).succeeded(1).failed(0));
        results.containerEvents().assertStatistics(stats -> stats.failed(0));
        assertThat(logs.text()).contains("failed in afterAllTests of");
    }

    @org.junit.jupiter.api.Test
    @DisplayName("An OutOfMemoryError from the reporter propagates: the guard never swallows a VirtualMachineError")
    void outOfMemoryErrorPropagates() {
        assertThatThrownBy(() -> run(RunsOutOfMemoryAsTheTestStarts.class)).isInstanceOf(OutOfMemoryError.class);
        assertThat(logs.text()).isEmpty();
    }

    private static EngineExecutionResults run(Class<?> fixture) {
        return EngineTestKit.engine("junit-jupiter").selectors(selectClass(fixture)).execute();
    }

    private static CtrfReportManager managerThat(Consumer<CtrfReportManager> breaking) {
        CtrfReportManager manager = mock(CtrfReportManager.class);
        when(manager.startTestRun(any(), anyString())).thenReturn(true);
        breaking.accept(manager);
        return manager;
    }

    @Tag("scenario-fixture")
    static class ThrowsOnTestStart {

        @RegisterExtension
        static final CtrfExtension CTRF = new CtrfExtension(managerThat(manager ->
            doThrow(new IllegalStateException("reporter broke")).when(manager).onTestStart(any())));

        @org.junit.jupiter.api.Test
        void passes() {
            // passes
        }
    }

    @Tag("scenario-fixture")
    static class ThrowsOnTestFailure {

        @RegisterExtension
        static final CtrfExtension CTRF = new CtrfExtension(managerThat(manager ->
            doThrow(new IllegalStateException("reporter broke")).when(manager).onTestFailure(any(TestDetails.class), any())));

        @org.junit.jupiter.api.Test
        void fails() {
            throw new AssertionError("the test's own failure");
        }
    }

    @Tag("scenario-fixture")
    static class ThrowsOnRunStart {

        @RegisterExtension
        static final CtrfExtension CTRF = new CtrfExtension(managerThat(manager ->
            doThrow(new IllegalStateException("reporter broke")).when(manager).startTestRun(any(), anyString())));

        @org.junit.jupiter.api.Test
        void first() {
            // passes
        }

        @org.junit.jupiter.api.Test
        void second() {
            // passes
        }
    }

    @Tag("scenario-fixture")
    static class ThrowsOnClassEnd {

        @RegisterExtension
        static final CtrfExtension CTRF = new CtrfExtension(managerThat(manager ->
            doThrow(new IllegalStateException("reporter broke")).when(manager).onContainerSuccess(anyString(), any())));

        @org.junit.jupiter.api.Test
        void first() {
            // passes
        }

        @org.junit.jupiter.api.Test
        void second() {
            // passes
        }
    }

    @Tag("scenario-fixture")
    static class ThrowsOnReportWrite {

        @RegisterExtension
        static final CtrfExtension CTRF = new CtrfExtension(managerThat(manager ->
            doThrow(new IllegalStateException("reporter broke")).when(manager).finishTestRun(any())));

        @org.junit.jupiter.api.Test
        void passes() {
            // passes
        }
    }

    @Tag("scenario-fixture")
    static class RunsOutOfMemoryAsTheTestStarts {

        @RegisterExtension
        static final CtrfExtension CTRF = new CtrfExtension(managerThat(manager ->
            doThrow(new OutOfMemoryError("simulated")).when(manager).onTestStart(any())));

        @org.junit.jupiter.api.Test
        void passes() {
            // passes
        }
    }
}
