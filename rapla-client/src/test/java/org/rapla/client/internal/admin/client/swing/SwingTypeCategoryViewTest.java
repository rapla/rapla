package org.rapla.client.internal.admin.client.swing;

import org.junit.jupiter.api.Test;
import org.rapla.client.internal.admin.client.swing.SwingTypeCategoryView.View;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Admin → Typen: the view is a Spring singleton, so a reopened dialog kept the combo box on the
 * last choice ("Veranstaltungstyp") while {@code init()} reset the tree to resource types. The
 * view must be DERIVED from the combo index through one mapping — the same one the item listener
 * uses — so both can never disagree again.
 */
class SwingTypeCategoryViewTest
{
    @Test
    void adminIndicesMapInComboOrder()
    {
        assertEquals(View.RESOURCE_TYPE, SwingTypeCategoryView.viewAt(0, true));
        assertEquals(View.PERSON_TYPE, SwingTypeCategoryView.viewAt(1, true));
        assertEquals(View.RESERVATION_TYPE, SwingTypeCategoryView.viewAt(2, true));
        assertEquals(View.CATEGORY, SwingTypeCategoryView.viewAt(3, true));
        assertEquals(View.PERIODS, SwingTypeCategoryView.viewAt(4, true));
    }

    @Test
    void nonAdminComboHasOnlyPeriods()
    {
        assertEquals(View.PERIODS, SwingTypeCategoryView.viewAt(0, false));
    }

    @Test
    void anUnknownIndexFallsBackToTheDefault()
    {
        assertEquals(View.RESOURCE_TYPE, SwingTypeCategoryView.viewAt(-1, true));
        assertEquals(View.PERIODS, SwingTypeCategoryView.viewAt(-1, false));
    }
}
