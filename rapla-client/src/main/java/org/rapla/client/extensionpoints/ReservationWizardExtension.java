package org.rapla.client.extensionpoints;

import org.rapla.client.menu.IdentifiableMenuEntry;
import org.rapla.facade.CalendarSelectionModel;

/** add your own wizard menus to createInfoDialog events. Use the CalendarSelectionModel service to get access to the current calendar
 * @see CalendarSelectionModel
 **/

public interface ReservationWizardExtension extends IdentifiableMenuEntry
{
    String ID = "org.rapla.client.extensionpoints.ReservationWizardExtension";
    boolean isEnabled();
}
