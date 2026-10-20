package io.github.alexshamrai.ctrf.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * An attempt of a test that completed before its final attempt (CTRF attempt history entry, spec section 11).
 * The test object itself represents the final attempt. Attempt details that the spec doesn't allow here, such as the
 * thread, stay on the test object.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class RetryAttempt {

    /**
     * Sequence number of this attempt: 1 is the initial execution.
     */
    private int attempt;
    private Test.TestStatus status;
    private long duration;
    private Long start;
    private Long stop;
    private String message;
    private String trace;
    private List<String> stdout;
    private List<String> stderr;
    private List<Attachment> attachments;
}
