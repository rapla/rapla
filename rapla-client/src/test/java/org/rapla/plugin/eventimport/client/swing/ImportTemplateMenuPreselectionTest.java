package org.rapla.plugin.eventimport.client.swing;

import org.junit.jupiter.api.Test;
import org.rapla.plugin.eventimport.client.swing.ImportTemplateMenu.Status;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The checkbox in the import dialog must mean "ok will act on this row".
 *  It used to be an exclusion list, which left the no-op stati ({@code geloescht},
 *  the two date errors) ticked while the row that creates events ({@code template})
 *  stayed empty. */
class ImportTemplateMenuPreselectionTest
{
    @Test
    void rowsActingOnExistingEventsArePreselected()
    {
        assertTrue(ImportTemplateMenu.preselected(Status.aktuallisieren));
        assertTrue(ImportTemplateMenu.preselected(Status.zu_loeschen));
    }

    @Test
    void creatingRowsAndNoOpsAreNotPreselected()
    {
        assertFalse(ImportTemplateMenu.preselected(Status.template), "creating an event stays a deliberate click");
        assertFalse(ImportTemplateMenu.preselected(Status.template_waehlen), "needs a template picked first");
        assertFalse(ImportTemplateMenu.preselected(Status.aktuell));
        assertFalse(ImportTemplateMenu.preselected(Status.geloescht));
        assertFalse(ImportTemplateMenu.preselected(Status.datum_fehlt));
        assertFalse(ImportTemplateMenu.preselected(Status.datum_fehlerhaft));
    }
}
