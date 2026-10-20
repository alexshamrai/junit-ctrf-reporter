package io.github.alexshamrai.ctrf.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * A file attached to a test or a retry attempt, by reference (CTRF attachment object, spec section 12). The reporter
 * attaches the files that a test publishes with JUnit's {@code TestReporter.publishFile}, and keeps the fields it doesn't
 * set when it merges a report of the same run.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Attachment {

    /**
     * The file's name.
     */
    private String name;
    /**
     * Its media type, such as {@code image/png}.
     */
    private String contentType;
    /**
     * Where the file is: the absolute path that JUnit wrote it to.
     */
    private String path;
    /**
     * An identifier of this reference; the reporter doesn't set it.
     */
    private String attachmentId;
    /**
     * Fields that CTRF leaves to the producer; the reporter doesn't set them.
     */
    private Map<String, Object> extra;
}
