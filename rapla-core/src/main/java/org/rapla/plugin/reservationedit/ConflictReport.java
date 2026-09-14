package org.rapla.plugin.reservationedit;

import java.util.List;

/**
 * Wire-format response from {@code POST /edit/check-conflicts}.
 * <p>
 * One {@link AllocationOutcomeDto} per allocatable in the request, in
 * the same order. Allocatables the user can't read are silently dropped
 * (AGENTS.md §12 — existence is information).
 */
public record ConflictReport(List<AllocationOutcomeDto> outcomes)
{
    public ConflictReport
    {
        outcomes = outcomes == null ? List.of() : List.copyOf(outcomes);
    }
}
