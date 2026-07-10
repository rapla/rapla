package org.rapla.server.spring.graphql;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration for GraphQL query bounds. Bound from {@code rapla.graphql.*}
 * in application.yml. Defaults are null = "no cap" — large deployments can
 * opt in by setting an explicit limit; small deployments leave the default
 * alone and never have to think about it.
 */
@ConfigurationProperties(prefix = "rapla.graphql")
public class RaplaGraphqlProperties
{
    /**
     * Maximum allowed window size (in days) for {@code reservations(filter:)}.
     * Null = no cap. A 365 setting (one year) is the typical "protect a
     * busy production deployment from accidental table scans" choice.
     * Validation runs against {@code ChronoUnit.DAYS.between(from, to)}.
     */
    private Integer maxQueryWindowDays;

    public Integer getMaxQueryWindowDays() { return maxQueryWindowDays; }
    public void setMaxQueryWindowDays(Integer maxQueryWindowDays) { this.maxQueryWindowDays = maxQueryWindowDays; }

    /**
     * Wall-clock budget (in milliseconds) for a single GraphQL execution
     * (security-audit §G). Once exceeded, remaining field fetches abort with an
     * error instead of continuing — so one expensive query cannot run 10s+ and
     * tie up a worker thread / block the shared dispatch-locked store. Unlike a
     * depth cap this also bounds the shallow-but-wide queries that actually run
     * long. Default 30000 (30s); {@code 0} or negative disables the check.
     */
    private long executionBudgetMillis = 30_000L;

    public long getExecutionBudgetMillis() { return executionBudgetMillis; }
    public void setExecutionBudgetMillis(long executionBudgetMillis) { this.executionBudgetMillis = executionBudgetMillis; }
}
