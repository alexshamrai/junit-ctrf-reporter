package io.github.alexshamrai.ctrf.model;

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
@Builder
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
    private String suite;
    private String message;
    private String trace;
    private String snippet;
    private Integer line;
    private String ai;
    private String rawStatus;
    private List<String> tags;
    private String type;
    private String filepath;
    private Integer retries;
    private Boolean flaky;
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
