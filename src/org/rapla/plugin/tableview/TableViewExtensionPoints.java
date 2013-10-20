package org.rapla.plugin.tableview;

import org.rapla.framework.TypedComponentRole;
import org.rapla.plugin.tableview.internal.SummaryExtension;

public interface TableViewExtensionPoints {

	/** add a summary footer for the reservation table 
	@see SummaryExtension
	* */
	TypedComponentRole<SummaryExtension> RESERVATION_TABLE_SUMMARY = new TypedComponentRole<SummaryExtension>("org.rapla.plugin.tableview.reservationsummary");
	/** add a column for the reservation table 
	 * 
	 @see ReservationTableColumn
	 */
	Class<ReservationTableColumn> RESERVATION_TABLE_COLUMN = ReservationTableColumn.class;
	/** add a column for the appointment table 
	     @see AppointmentTableColumn 
	     * */
	Class<AppointmentTableColumn> APPOINTMENT_TABLE_COLUMN = AppointmentTableColumn.class;
	/** add a summary footer for the appointment table 
	 @see SummaryExtension
	* */
	TypedComponentRole<SummaryExtension> APPOINTMENT_TABLE_SUMMARY = new TypedComponentRole<SummaryExtension>("org.rapla.plugin.tableview.appointmentsummary");

}
