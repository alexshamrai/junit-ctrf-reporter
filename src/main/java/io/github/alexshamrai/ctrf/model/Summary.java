package io.github.alexshamrai.ctrf.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Summary {

    private int tests;
    private int passed;
    private int failed;
    private int pending;
    private int skipped;
    private int other;
    /**
     * Number of tests whose final attempt passed after at least one failed attempt.
     */
    private int flaky;
    private Integer suites;
    private long start;
    private long stop;
    /**
     * Run duration in milliseconds: {@code stop} minus {@code start}.
     */
    private long duration;
    private Extra extra;
}