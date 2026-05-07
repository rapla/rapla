package org.rapla.plugin.tableview.client.swing.extensionpoints;

import org.rapla.inject.ExtensionPoint;
import org.rapla.inject.InjectionContext;
import org.rapla.plugin.tableview.TableViewPlugin;

@ExtensionPoint(context = InjectionContext.swing,id = TableViewPlugin.APPOINTMENT_SUMMARY)
public interface AppointmentSummaryExtension extends SummaryExtension
{
}
