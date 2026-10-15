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
public class Environment {

    private String reportName;
    private String appName;
    private String appVersion;
    private String buildName;
    /**
     * {@code ctrf.build.number} when it is a whole number. CTRF types {@code buildNumber} as an integer.
     */
    private Long buildNumber;
    /**
     * {@code ctrf.build.number} when it isn't a whole number, such as {@code 2026.10.15-3}.
     */
    private String buildId;
    private String buildUrl;
    private String repositoryName;
    private String repositoryUrl;
    private String commit;
    private String branchName;
    private String osPlatform;
    private String osRelease;
    private String osVersion;
    private String testEnvironment;
    private boolean healthy;
    private Extra extra;
}
