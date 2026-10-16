package io.github.alexshamrai.ctrf.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * The {@code results} object of a CTRF report: the tool that ran the tests, the summary, the tests themselves, and the
 * environment.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Results {

    private Tool tool;
    private Summary summary;
    private List<Test> tests;
    private Environment environment;
    private Extra extra;
}