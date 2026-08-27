package org.rapla.client.edit.check;

import java.util.List;

/**
 * PRD 105 — one pre-save check over a reservation, as a bean. The server folds every registered
 * checker in {@code @Order} sequence and returns the union of their warnings; a plugin adds a check
 * by contributing another bean of this type (the server analog of Swing's {@code EventCheck}
 * extension point, whose impls are {@code @Service} classes folded from an injected {@code Set}).
 *
 * <p>Two deliberate differences from {@code EventCheck}: the result is a LIST of warnings rather
 * than a first-false abort (so severity travels with the code, not with the dialog), and the order
 * is deterministic (the output is read by machines and rendered as a list).
 *
 * <p>Implementations must be side-effect free — a check that writes is a bug (AGENTS.md §16).
 */
public interface ReservationChecker
{
    List<ReservationWarning> check(CheckContext context);
}
