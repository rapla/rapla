package org.rapla.client.edit.check;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * PRD 105 D7 — the severity belongs to the CODE, not to the dialog that renders it. Swing aborts
 * the save on a missing name and offers continue/cancel for everything else; if each tier decided
 * that for itself, the same event would be creatable in one client and rejected in the other.
 *
 * <p>This test is the pin: adding a code without deciding its severity fails here, not in a UI.
 */
class ReservationWarningSeverityTest
{
    @Test
    void onlyAMissingNameBlocksTheSave()
    {
        assertEquals(ReservationWarning.Severity.BLOCKING,
                ReservationWarning.Code.NO_RESERVATION_NAME.severity(),
                "Swing's DefaultReservationCheck aborts on a blank name");

        for (ReservationWarning.Code code : ReservationWarning.Code.values())
        {
            if (code == ReservationWarning.Code.NO_RESERVATION_NAME) continue;
            assertEquals(ReservationWarning.Severity.CONFIRMABLE, code.severity(),
                    () -> code + " must be confirmable — Swing shows continue/cancel for it");
        }
    }
}
