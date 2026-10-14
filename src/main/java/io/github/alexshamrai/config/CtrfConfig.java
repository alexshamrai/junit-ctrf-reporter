package io.github.alexshamrai.config;

import org.aeonbits.owner.Config;
import org.aeonbits.owner.Config.Sources;

/**
 * Configuration interface for CTRF Extension settings.
 * <p>
 * This interface defines all configurable properties for the CTRF Extension.
 * Values will be loaded from the following sources in order of precedence:
 * <ol>
 *   <li>System properties (-D command line parameters)</li>
 *   <li>Environment variables</li>
 *   <li>{@code ctrf.properties} file in the classpath</li>
 * </ol>
 * <p>
 * To use system properties, ensure they are passed to the test execution.
 * For example: {@code -Dctrf.report.path=custom/path/report.json}
 * <p>
 * Each property has a default value or can be left undefined if optional.
 */
@Config.LoadPolicy(Config.LoadType.MERGE)
@Sources({"system:properties",
    "system:env",
    "classpath:ctrf.properties"})
public interface CtrfConfig extends Config {

    @Key("ctrf.report.path")
    @DefaultValue("ctrf-report.json")
    String reportPath();

    @Key("ctrf.max.message.length")
    @DefaultValue("500")
    int maxMessageLength();

    @Key("junit.version")
    String junitVersion();

    @Key("ctrf.report.name")
    String reportName();

    @Key("ctrf.app.name")
    String appName();

    @Key("ctrf.app.version")
    String appVersion();

    @Key("ctrf.build.name")
    String buildName();

    @Key("ctrf.build.number")
    String buildNumber();

    @Key("ctrf.build.url")
    String buildUrl();

    @Key("ctrf.repository.name")
    String repositoryName();

    @Key("ctrf.repository.url")
    String repositoryUrl();

    @Key("ctrf.commit")
    String commit();

    @Key("ctrf.branch.name")
    String branchName();

    @Key("ctrf.os.platform")
    String osPlatform();

    @Key("ctrf.os.release")
    String osRelease();

    @Key("ctrf.os.version")
    String osVersion();

    @Key("ctrf.test.environment")
    String testEnvironment();

    @Key("ctrf.calculate.startup.duration")
    @DefaultValue("false")
    boolean calculateStartupDuration();

    @Key("ctrf.run.id")
    String runId();
}
