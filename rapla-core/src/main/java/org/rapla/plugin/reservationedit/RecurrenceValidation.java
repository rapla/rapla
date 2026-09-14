package org.rapla.plugin.reservationedit;

import java.util.List;

/**
 * Wire-format DTO mirroring
 * {@link org.rapla.client.edit.reservation.RepeatingRuleValidator.Result}.
 * Returned by {@code POST /edit/validate-recurrence}.
 */
public record RecurrenceValidation(boolean valid, List<Issue> issues)
{
    public RecurrenceValidation
    {
        issues = issues == null ? List.of() : List.copyOf(issues);
    }

    /**
     * One advisory issue. {@code code} matches the enum name in
     * {@link org.rapla.client.edit.reservation.RepeatingRuleValidator.Code}
     * (e.g. {@code INTERVAL_LESS_THAN_ONE}). Stable for the wire — clients
     * dispatch on the code string.
     */
    public record Issue(String code, String detail) {}
}
