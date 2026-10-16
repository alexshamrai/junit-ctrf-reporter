package io.github.alexshamrai.integration;

import io.github.alexshamrai.integration.JunitEvents.Expected;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JunitEventsTest {

    private static final String ENGINE = "[engine:junit-jupiter]";
    private static final String CLASS = ENGINE + "/[class:com.example.PaymentTest]";
    private static final String TEST = CLASS + "/[method:pays()]";
    private static final String TEMPLATE = CLASS + "/[test-template:usesData(int)]";

    @TempDir
    Path tempDir;

    @Test
    void passedTestIsExpectedAndPassedContainersAreNot() {
        var events = JunitEvents.fromXml(List.of(document(
            started(1, ENGINE, "CONTAINER"), started(2, CLASS, "CONTAINER"), started(3, TEST, "TEST"),
            finished(3, "2026-10-15T10:00:01Z", "SUCCESSFUL"), finished(2, "2026-10-15T10:00:02Z", "SUCCESSFUL"),
            finished(1, "2026-10-15T10:00:03Z", "SUCCESSFUL"))));

        assertThat(events.expectedEntries()).isEqualTo(Map.of(TEST, new Expected(TEST, "passed", 0, false)));
        assertThat(events.skippedContainers()).isEmpty();
    }

    @Test
    void testThatFailsAndThenPassesInALaterJvmIsFlaky() {
        var firstRound = document(started(1, TEST, "TEST"), finished(1, "2026-10-15T10:00:01Z", "FAILED"));
        var secondRound = document(started(1, TEST, "TEST"), finished(1, "2026-10-15T10:00:05Z", "SUCCESSFUL"));

        // The files' order must not matter: attempts are ordered by their finish time
        var events = JunitEvents.fromXml(List.of(secondRound, firstRound));

        assertThat(events.expectedEntries().get(TEST)).isEqualTo(new Expected(TEST, "passed", 1, true));
    }

    @Test
    void testThatFailsInEveryRoundIsFailedAndNotFlaky() {
        var events = JunitEvents.fromXml(List.of(
            document(started(1, TEST, "TEST"), finished(1, "2026-10-15T10:00:01Z", "FAILED")),
            document(started(1, TEST, "TEST"), finished(1, "2026-10-15T10:00:05Z", "FAILED"))));

        assertThat(events.expectedEntries().get(TEST)).isEqualTo(new Expected(TEST, "failed", 1, false));
    }

    @Test
    void abortedTestIsSkipped() {
        var events = JunitEvents.fromXml(List.of(document(started(1, TEST, "TEST"), finished(1, "2026-10-15T10:00:01Z", "ABORTED"))));

        assertThat(events.expectedEntries().get(TEST)).isEqualTo(new Expected(TEST, "skipped", 0, false));
    }

    @Test
    void abortedThenPassedIsNotFlaky() {
        var events = JunitEvents.fromXml(List.of(
            document(started(1, TEST, "TEST"), finished(1, "2026-10-15T10:00:01Z", "ABORTED")),
            document(started(1, TEST, "TEST"), finished(1, "2026-10-15T10:00:05Z", "SUCCESSFUL"))));

        assertThat(events.expectedEntries().get(TEST)).isEqualTo(new Expected(TEST, "passed", 1, false));
    }

    @Test
    void failedContainersAreEntriesAndARecoveredOneIsFlaky() {
        var events = JunitEvents.fromXml(List.of(
            document(started(1, CLASS, "CONTAINER"), started(2, TEMPLATE, "CONTAINER"),
                finished(2, "2026-10-15T10:00:01Z", "FAILED"), finished(1, "2026-10-15T10:00:02Z", "FAILED")),
            document(started(1, CLASS, "CONTAINER"), finished(1, "2026-10-15T10:00:05Z", "SUCCESSFUL"))));

        assertThat(events.expectedEntries()).containsOnly(
            Map.entry(CLASS, new Expected(CLASS, "passed", 1, true)),
            Map.entry(TEMPLATE, new Expected(TEMPLATE, "failed", 0, false)));
    }

    @Test
    void skippedAndAbortedContainersAreListedSeparately() {
        var other = ENGINE + "/[class:com.example.RefundTest]";
        var events = JunitEvents.fromXml(List.of(document(
            started(1, CLASS, "CONTAINER"), finished(1, "2026-10-15T10:00:01Z", "SKIPPED"),
            started(2, other, "CONTAINER"), finished(2, "2026-10-15T10:00:02Z", "ABORTED"))));

        assertThat(events.skippedContainers()).containsExactlyInAnyOrder(CLASS, other);
        assertThat(events.expectedEntries()).isEmpty();
    }

    @Test
    void startedButNeverFinishedIsIgnored() {
        // A JVM that dies never writes the closing tag, so its log is truncated
        var events = JunitEvents.fromXml(List.of(truncated(started(1, TEST, "TEST"))));

        assertThat(events.expectedEntries()).isEmpty();
        assertThat(events.skippedContainers()).isEmpty();
    }

    @Test
    void truncatedLogIsRecoveredFromItsLastCompleteEvent() {
        var other = CLASS + "/[method:refunds()]";
        var events = JunitEvents.fromXml(List.of(truncated(
            started(1, TEST, "TEST"), finished(1, "2026-10-15T10:00:01Z", "SUCCESSFUL"), started(2, other, "TEST"))
            + "<e:started id=\"3\" name=\"x\""));

        assertThat(events.expectedEntries()).isEqualTo(Map.of(TEST, new Expected(TEST, "passed", 0, false)));
        assertThat(events.truncatedLogs()).containsExactly("document 1");
    }

    @Test
    void truncatedLogWithoutAnyCompleteEventIsEmpty() {
        var events = JunitEvents.fromXml(List.of(truncated("<e:started id=\"1\" na")));

        assertThat(events.expectedEntries()).isEmpty();
        assertThat(events.truncatedLogs()).containsExactly("document 1");
    }

    @Test
    void completeLogsAreNotReportedAsTruncated() {
        var events = JunitEvents.fromXml(List.of(document(started(1, TEST, "TEST"), finished(1, "2026-10-15T10:00:01Z", "FAILED"))));

        assertThat(events.truncatedLogs()).isEmpty();
    }

    @Test
    void documentWithoutEventsRootIsRejectedWithItsSource() {
        assertThatThrownBy(() -> JunitEvents.fromXml(List.of(document(), "just some text")))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("document 2");
    }

    @Test
    void rootInAnotherNamespaceIsRejectedNamingTheSourceAndTheRoot() {
        var drifted = document().replace("reporting/events/0.2.0", "reporting/events/0.3.0");

        assertThatThrownBy(() -> JunitEvents.fromXml(List.of(drifted)))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("document 1")
            .hasMessageContaining("https://schemas.opentest4j.org/reporting/events/0.3.0")
            .hasMessageContaining("events")
            .hasMessageContaining("Open Test Reporting events 0.2.0");
    }

    @Test
    void structureErrorsNameTheSource() {
        var noType = document("<e:started id=\"1\" name=\"n\" time=\"2026-10-15T10:00:00Z\"><metadata>"
            + "<junit:uniqueId>" + TEST + "</junit:uniqueId></metadata></e:started>");
        var unknownStatus = document(started(1, TEST, "TEST"), finished(1, "2026-10-15T10:00:01Z", "WEIRD"));

        assertThatThrownBy(() -> JunitEvents.fromXml(List.of(noType)))
            .isInstanceOf(IllegalStateException.class).hasMessageContaining("document 1");
        assertThatThrownBy(() -> JunitEvents.fromXml(List.of(document(), unknownStatus)))
            .isInstanceOf(IllegalStateException.class).hasMessageContaining("document 2").hasMessageContaining("WEIRD");
    }

    @Test
    void skippedTestTemplateAccountsForItselfAndWhatIsBelowItButNotForASibling() {
        var events = JunitEvents.fromXml(List.of(document(
            started(1, TEMPLATE, "CONTAINER"), finished(1, "2026-10-15T10:00:01Z", "SKIPPED"))));

        assertThat(events.accountsFor(TEMPLATE)).isTrue();
        assertThat(events.accountsFor(TEMPLATE + "/[test-template-invocation:#1]")).isTrue();
        assertThat(events.accountsFor(CLASS + "/[test-template:usesOtherData(int)]")).isFalse();
        assertThat(events.accountsFor(TEMPLATE + "Longer(int)")).isFalse();
        assertThat(events.containersWithoutListedTests()).containsExactly(TEMPLATE);
    }

    @Test
    void classAbortedAfterItsTestsPassedHasListedTests() {
        var events = JunitEvents.fromXml(List.of(document(
            started(1, CLASS, "CONTAINER"), started(2, TEST, "TEST"),
            finished(2, "2026-10-15T10:00:01Z", "SUCCESSFUL"), finished(1, "2026-10-15T10:00:02Z", "ABORTED"))));

        assertThat(events.skippedContainers()).containsExactly(CLASS);
        assertThat(events.containersWithoutListedTests()).isEmpty();
        assertThat(events.expectedEntries()).isEqualTo(Map.of(TEST, new Expected(TEST, "passed", 0, false)));
    }

    @Test
    void readFoldsTheFilesOfEveryJvm() throws IOException {
        write(tempDir.resolve("111/open-test-report.xml"),
            document(started(1, TEST, "TEST"), finished(1, "2026-10-15T10:00:01Z", "FAILED")));
        write(tempDir.resolve("222/open-test-report.xml"),
            document(started(1, TEST, "TEST"), finished(1, "2026-10-15T10:00:05Z", "SUCCESSFUL")));

        var events = JunitEvents.read(tempDir);

        assertThat(events.expectedEntries().get(TEST)).isEqualTo(new Expected(TEST, "passed", 1, true));
    }

    @Test
    void readNamesTheFileThatIsNotXml() throws IOException {
        var file = tempDir.resolve("111/open-test-report.xml");
        write(file, "this is not xml");

        assertThatThrownBy(() -> JunitEvents.read(tempDir))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining(file.toString());
    }

    @Test
    void readFailsClearlyWithoutEventFiles() {
        var missing = tempDir.resolve("missing");

        assertThatThrownBy(() -> JunitEvents.read(tempDir))
            .isInstanceOf(IOException.class)
            .hasMessageStartingWith("No JUnit event log")
            .hasMessageContaining(tempDir.toString());
        assertThatThrownBy(() -> JunitEvents.read(missing))
            .isInstanceOf(IOException.class)
            .hasMessageStartingWith("No JUnit event log");
    }

    @Test
    void suiteIsTheContainersAboveWithoutTheEngine() {
        String engine = "[engine:junit-jupiter]";
        String payments = engine + "/[class:a.PaymentsTest]";
        String refunds = payments + "/[nested-class:Refunds]";
        String test = refunds + "/[method:refundsTheFee()]";
        var junit = JunitEvents.fromXml(List.of(document(
            started(1, engine, "CONTAINER", "JUnit Jupiter", null),
            started(2, payments, "CONTAINER", "Payments", 1),
            started(3, refunds, "CONTAINER", "Refunds", 2),
            started(4, test, "TEST", "refundsTheFee()", 3),
            finished(4, "2026-10-15T10:00:01Z", "SUCCESSFUL"),
            finished(3, "2026-10-15T10:00:01Z", "SUCCESSFUL"),
            finished(2, "2026-10-15T10:00:01Z", "SUCCESSFUL"),
            finished(1, "2026-10-15T10:00:01Z", "SUCCESSFUL"))));

        assertThat(junit.expectedSuite(test, "refundsTheFee()")).contains(List.of("Payments", "Refunds"));
        assertThat(junit.expectedSuite(refunds, "teardownError")).contains(List.of("Payments", "Refunds"));
        assertThat(junit.expectedSuite(payments, "initializationError")).contains(List.of("Payments"));
        assertThat(junit.expectedSuite(payments, "a container entry")).contains(List.of("JUnit Jupiter"));
        assertThat(junit.expectedSuite(engine + "/[class:a.Unknown]", "anything")).isEmpty();
    }

    @Test
    @DisplayName("Review Focus 1: names with characters XML escapes are compared as JUnit wrote them")
    void suiteKeepsNamesWithEscapedCharacters() {
        String engine = "[engine:junit-jupiter]";
        String fees = engine + "/[class:a.FeesTest]";
        String test = fees + "/[method:t()]";
        var junit = JunitEvents.fromXml(List.of(document(
            started(1, engine, "CONTAINER", "JUnit Jupiter", null),
            started(2, fees, "CONTAINER", "Fees &amp; &quot;refunds&quot; &lt;EU&gt;", 1),
            started(3, test, "TEST", "t()", 2),
            finished(3, "2026-10-15T10:00:01Z", "SUCCESSFUL"),
            finished(2, "2026-10-15T10:00:01Z", "SUCCESSFUL"),
            finished(1, "2026-10-15T10:00:01Z", "SUCCESSFUL"))));

        assertThat(junit.expectedSuite(test, "t()")).contains(List.of("Fees & \"refunds\" <EU>"));
    }

    @Test
    @DisplayName("Under a suite engine, every engine is left out, and the @Suite class stays")
    void underASuiteEngineEveryEngineIsLeftOut() {
        String suiteEngine = "[engine:junit-platform-suite]";
        String suite = suiteEngine + "/[suite:a.PaymentsSuite]";
        String jupiter = suite + "/[engine:junit-jupiter]";
        String payments = jupiter + "/[class:a.PaymentsTest]";
        String test = payments + "/[method:t()]";
        var junit = JunitEvents.fromXml(List.of(document(
            started(1, suiteEngine, "CONTAINER", "JUnit Platform Suite", null),
            started(2, suite, "CONTAINER", "PaymentsSuite", 1),
            started(3, jupiter, "CONTAINER", "JUnit Jupiter", 2),
            started(4, payments, "CONTAINER", "Payments", 3),
            started(5, test, "TEST", "t()", 4),
            finished(5, "2026-10-15T10:00:01Z", "SUCCESSFUL"),
            finished(4, "2026-10-15T10:00:01Z", "SUCCESSFUL"),
            finished(3, "2026-10-15T10:00:01Z", "SUCCESSFUL"),
            finished(2, "2026-10-15T10:00:01Z", "SUCCESSFUL"),
            finished(1, "2026-10-15T10:00:01Z", "SUCCESSFUL"))));

        assertThat(junit.expectedSuite(test, "t()")).contains(List.of("PaymentsSuite", "Payments"));
        assertThat(junit.expectedSuite(payments, "initializationError")).contains(List.of("PaymentsSuite", "Payments"));
    }

    private static void write(Path file, String content) throws IOException {
        Files.createDirectories(file.getParent());
        Files.writeString(file, content);
    }

    private static String document(String... events) {
        return truncated(events) + "</e:events>";
    }

    /** What a JVM that died mid-run leaves: no closing root tag. */
    private static String truncated(String... events) {
        return "<?xml version=\"1.0\" ?>"
            + "<e:events xmlns=\"https://schemas.opentest4j.org/reporting/core/0.2.0\""
            + " xmlns:e=\"https://schemas.opentest4j.org/reporting/events/0.2.0\""
            + " xmlns:junit=\"https://schemas.junit.org/open-test-reporting\">"
            + String.join("", events);
    }

    private static String started(int id, String uniqueId, String type) {
        return "<e:started id=\"" + id + "\" name=\"node\" time=\"2026-10-15T10:00:00Z\"><metadata>"
            + "<junit:uniqueId>" + uniqueId + "</junit:uniqueId><junit:type>" + type + "</junit:type>"
            + "</metadata></e:started>";
    }

    private static String started(int id, String uniqueId, String type, String name, Integer parentId) {
        return "<e:started id=\"" + id + "\" name=\"" + name + "\"" + (parentId == null ? "" : " parentId=\"" + parentId + "\"")
            + " time=\"2026-10-15T10:00:00Z\"><metadata>"
            + "<junit:uniqueId>" + uniqueId + "</junit:uniqueId><junit:type>" + type + "</junit:type>"
            + "</metadata></e:started>";
    }

    private static String finished(int id, String time, String status) {
        return "<e:finished id=\"" + id + "\" time=\"" + time + "\"><result status=\"" + status + "\"></result></e:finished>";
    }
}
