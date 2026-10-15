package integration;

import io.github.alexshamrai.ctrf.model.Test.TestStatus;
import io.github.alexshamrai.integration.JunitEvents;
import io.github.alexshamrai.integration.JunitEvents.Expected;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Checks every report entry against JUnit's own event log of the same run, by testId (see {@link JunitEvents}).
 */
public class JunitCrossCheckTest extends BaseIntegrationTest {

    private JunitEvents junit;
    private Map<String, io.github.alexshamrai.ctrf.model.Test> entries;

    @BeforeAll
    void readJunitEventLog() throws IOException {
        junit = JunitEvents.read(run.eventsDir());
        entries = new LinkedHashMap<>();
        report.getResults().getTests().forEach(test -> entries.putIfAbsent(test.getTestId(), test));
    }

    @Test
    void everyEntryJunitExpectsIsInTheReport() {
        List<String> problems = new ArrayList<>();
        junit.expectedEntries().values().forEach(want -> {
            var entry = entries.get(want.testId());
            if (entry == null) {
                problems.add("missing: " + want);
                return;
            }
            var got = new Expected(entry.getTestId(), entry.getStatus().toLowerCase(),
                entry.getRetries() == null ? 0 : entry.getRetries(), Boolean.TRUE.equals(entry.getFlaky()));
            if (!got.equals(want)) {
                problems.add("JUnit says " + want + ", the report says " + got);
            }
        });

        var deadJvms = junit.truncatedLogs().isEmpty() ? ""
            : " - these JUnit event logs end early, so their JVM died: " + junit.truncatedLogs();
        assertThat(problems)
            .as("entries JUnit ran (%d) that the report misses or gets wrong%s", junit.expectedEntries().size(), deadJvms)
            .isEmpty();
    }

    @Test
    void reportHasNoEntriesJunitDidNotRun() {
        var unknown = entries.keySet().stream()
            .filter(testId -> !junit.accountsFor(testId))
            .toList();

        assertThat(unknown).as("report entries that JUnit's event log doesn't account for").isEmpty();
    }

    @Test
    void testsOfSkippedContainersAreReportedAsSkipped() {
        junit.skippedContainers().forEach(container -> {
            // A container aborted after its tests ran also has expected entries (maybe passed): those are checked elsewhere
            var unlisted = entries.values().stream()
                .filter(test -> JunitEvents.within(test.getTestId(), container))
                .filter(test -> !junit.expectedEntries().containsKey(test.getTestId()))
                .toList();

            if (junit.containersWithoutListedTests().contains(container)) {
                assertThat(unlisted)
                    .as("report entries within %s, which JUnit skipped as a whole without listing its tests", container)
                    .isNotEmpty();
            }
            assertThat(unlisted)
                .as("report entries within %s that JUnit's event log doesn't list", container)
                .allSatisfy(test -> assertThat(test.getStatus()).as("status of %s", test.getTestId()).isEqualTo(TestStatus.SKIPPED));
        });
    }
}
