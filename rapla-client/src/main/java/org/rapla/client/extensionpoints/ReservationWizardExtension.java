package org.rapla.client.extensionpoints;

import org.rapla.client.menu.IdentifiableMenuEntry;
import org.rapla.facade.CalendarSelectionModel;
import org.rapla.inject.ExtensionPoint;
import org.rapla.inject.InjectionContext;

/** add your own wizard menus to createInfoDialog events. Use the CalendarSelectionModel service to get access to the current calendar
 * @see CalendarSelectionModel
 **/
@ExtensionPoint(context = InjectionContext.client,id = ReservationWizardExtension.ID)
public interface ReservationWizardExtension extends IdentifiableMenuEntry
{
    String ID = "org.rapla.client.extensionpoints.ReservationWizardExtension";
    boolean isEnabled();
}
