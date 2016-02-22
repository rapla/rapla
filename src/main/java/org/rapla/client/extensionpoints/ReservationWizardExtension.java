package org.rapla.client.extensionpoints;

import org.rapla.client.swing.toolkit.IdentifiableMenuEntry;
import org.rapla.facade.CalendarSelectionModel;
import org.rapla.inject.ExtensionPoint;
import org.rapla.inject.InjectionContext;

/** add your own wizard menus to create events. Use the CalendarSelectionModel service to get access to the current calendar
 * @see CalendarSelectionModel
 **/
@ExtensionPoint(context = InjectionContext.swing,id="org.rapla.client.swing.gui.ReservationWizardExtension")
public interface ReservationWizardExtension extends IdentifiableMenuEntry
{
    boolean isEnabled();
}
