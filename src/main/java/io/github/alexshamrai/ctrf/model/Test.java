package io.github.alexshamrai.ctrf.model;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Test {

    /**
     * Stable identifier of the logical test case (CTRF {@code testId}): the JUnit unique ID.
     * Attempts of the same test share it across runs and retries.
     */
    private String testId;
    private String name;
    private TestStatus status;
    private long duration;
    private Long start;
    private Long stop;
    /**
     * Suite hierarchy, from the top level to the immediate parent. When set, it has at least one entry. The library
     * doesn't set it yet.
     */
    private List<String> suite;
    private String message;
    private String trace;
    private String snippet;
    private Integer line;
    private String ai;
    private String rawStatus;
    private List<String> tags;
    private String type;
    /**
     * Where the test is defined: its class name. Reports written before 0.5.0 call it {@code filepath}, which is still read.
     */
    @JsonAlias("filepath")
    private String filePath;
    private Integer retries;
    private Boolean flaky;
    /**
     * Attempts completed before the final attempt, which this object represents. Present only when
     * {@code retries} is greater than 0, with exactly {@code retries} entries numbered from 1.
     */
    private List<RetryAttempt> retryAttempts;
    private List<String> stdout;
    private List<String> stderr;
    private String browser;
    private String device;
    private String screenshot;
    private List<Object> attachments;
    private String threadId;
    private Map<String, Object> parameters;
    private List<Step> steps;
    private Extra extra;

    public enum TestStatus {
        PASSED, FAILED, SKIPPED, PENDING, OTHER;

        @JsonValue
        public String toLowerCase() {
            return name().toLowerCase();
        }
    }
}
