package io.github.alexshamrai.scenario;

import io.github.alexshamrai.CtrfReportManager;
import io.github.alexshamrai.model.TestDetails;

import java.util.Set;

/**
 * Entry point of the JVM that {@link HeapGuardScenarioTest} starts: records tests through the manager's public API,
 * every tenth failing with the same 1.7 KB trace, and ends the run, which writes the report.
 *
 * <p>Usage: {@code HeapGuardMain <count> <number of the first test>}
 */
public final class HeapGuardMain {

    private HeapGuardMain() {
    }

    public static void main(String[] args) {
        int count = Integer.parseInt(args[0]);
        int first = Integer.parseInt(args[1]);
        var manager = CtrfReportManager.getInstance();
        manager.startTestRun("heap-guard");
        AssertionError failure = failureWithFrames(40);
        for (int n = first; n < first + count; n++) {
            String type = "com.example.payments.service.PaymentServiceIntegrationTest" + (n / 50);
            String id = "[engine:junit-jupiter]/[class:" + type + "]/[method:paysTheInvoice" + n + "()]";
            manager.onTestStart(new TestDetails(System.currentTimeMillis(), Set.of("smoke", "payments"), type, id,
                "paysTheInvoice" + n + "()"));
            if (n % 10 == 0) {
                manager.onTestFailure(id, failure);
            } else {
                manager.onTestSuccess(id);
            }
        }
        manager.finishTestRun();
    }

    private static AssertionError failureWithFrames(int frames) {
        if (frames == 0) {
            return new AssertionError("expected: <200 OK> but was: <500 Internal Server Error> for POST /payments/42");
        }
        return failureWithFrames(frames - 1);
    }
}
