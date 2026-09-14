package org.rapla.client.edit.reservation;

import org.rapla.components.util.TimeInterval;
import org.rapla.entities.domain.Repeating;

import java.time.LocalDateTime;
import java.util.Collection;

/**
 * Pure-Java helper for the four exception-list mutations that drive
 * {@code AppointmentController.UndoExceptionChange} on the Swing tier.
 *
 * <p>Carve-out from {@code AppointmentController} (PRD 023): the Swing class
 * keeps event handling, widget binding, and the {@code CommandUndo} hookup;
 * this helper owns the actual mutation rules. Decoupled so:
 *
 * <ul>
 *   <li>Tier-1 tests pin the mutation contract with a stubbed
 *       {@link Repeating} (Proxy in {@code ExceptionListMutatorTest}).</li>
 *   <li>A future Angular client can call the same logic through a future
 *       REST endpoint without re-deriving the add/remove semantics.</li>
 *   <li>Both directions (apply / revert) are symmetric: the undo path
 *       simply calls the corresponding revert method.</li>
 * </ul>
 *
 * <p>All four methods accept a {@code null} {@link Repeating} as a no-op —
 * the Swing flow may fire mutations between an appointment swap and the
 * next render, and we don't want stale events to throw.
 */
public final class ExceptionListMutator
{
    private ExceptionListMutator() {}

    /**
     * Apply an "add exceptions" edit: for each interval, the underlying
     * {@code Repeating} adds all its dates as exceptions.
     */
    public static void applyAdditions(Repeating repeating, Collection<TimeInterval> intervals)
    {
        if (repeating == null || intervals == null) return;
        for (TimeInterval interval : intervals)
        {
            if (interval != null) repeating.addExceptions(interval);
        }
    }

    /**
     * Revert an "add exceptions" edit: for each previously-added interval,
     * remove its start date from the exception list. Mirrors what
     * {@code AppointmentController.UndoExceptionChange.undo()} did inline.
     */
    public static void revertAdditions(Repeating repeating, Collection<TimeInterval> intervals)
    {
        if (repeating == null || intervals == null) return;
        for (TimeInterval interval : intervals)
        {
            if (interval != null) repeating.removeException(interval.getStart());
        }
    }

    /**
     * Apply a "remove exceptions" edit: for each date, remove it from the
     * exception list. Collection overload — preferred for new callers.
     */
    public static void applyRemovals(Repeating repeating, Collection<LocalDateTime> dates)
    {
        if (repeating == null || dates == null) return;
        for (LocalDateTime d : dates)
        {
            if (d != null) repeating.removeException(d);
        }
    }

    /**
     * Apply a "remove exceptions" edit from a heterogeneous {@code Object[]}
     * — preserves the legacy {@code UndoExceptionChange.removedExceptions}
     * shape ({@link javax.swing.JList#getSelectedValues()} returns
     * {@code Object[]}). Entries that are not {@link LocalDateTime} are
     * silently skipped along with {@code null}s.
     */
    public static void applyRemovals(Repeating repeating, Object[] dates)
    {
        if (repeating == null || dates == null) return;
        for (Object d : dates)
        {
            if (d instanceof LocalDateTime ldt) repeating.removeException(ldt);
        }
    }

    /**
     * Revert a "remove exceptions" edit: add each previously-removed date
     * back to the exception list.
     */
    public static void revertRemovals(Repeating repeating, Collection<LocalDateTime> dates)
    {
        if (repeating == null || dates == null) return;
        for (LocalDateTime d : dates)
        {
            if (d != null) repeating.addException(d);
        }
    }

    /** Object[] overload — same legacy-shape concession as
     *  {@link #applyRemovals(Repeating, Object[])}. */
    public static void revertRemovals(Repeating repeating, Object[] dates)
    {
        if (repeating == null || dates == null) return;
        for (Object d : dates)
        {
            if (d instanceof LocalDateTime ldt) repeating.addException(ldt);
        }
    }
}
