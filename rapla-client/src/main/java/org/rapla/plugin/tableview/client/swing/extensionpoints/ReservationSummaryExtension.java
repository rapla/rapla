package org.rapla.plugin.tableview.client.swing.extensionpoints;

import org.rapla.inject.ExtensionPoint;
import org.rapla.inject.InjectionContext;
import org.rapla.plugin.tableview.TableViewPlugin;

@ExtensionPoint(context = InjectionContext.swing,id = TableViewPlugin.RESERVATION_SUMMARY)
public interface ReservationSummaryExtension extends SummaryExtension
{
}
