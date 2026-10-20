package io.github.alexshamrai;

/**
 * The output that JUnit captured for a test or container. With its output capture on, JUnit publishes it as one report
 * entry just before the test or container finishes (HP-13).
 *
 * @param stdout the captured standard output, or {@code null}
 * @param stderr the captured standard error, or {@code null}
 */
record CapturedOutput(String stdout, String stderr) {
}
