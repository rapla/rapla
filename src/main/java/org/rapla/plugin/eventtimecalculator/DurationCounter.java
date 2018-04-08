package org.rapla.plugin.eventtimecalculator;

import org.rapla.components.tablesorter.TableSorter;
import org.rapla.entities.User;
import org.rapla.entities.domain.AppointmentBlock;
import org.rapla.entities.domain.Reservation;
import org.rapla.facade.client.ClientFacade;
import org.rapla.framework.RaplaException;
import org.rapla.inject.Extension;
import org.rapla.plugin.tableview.RaplaTableModel;
import org.rapla.plugin.tableview.client.swing.extensionpoints.AppointmentSummaryExtension;
import org.rapla.plugin.tableview.client.swing.extensionpoints.ReservationSummaryExtension;

import javax.inject.Inject;
import javax.swing.Box;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTable;
import javax.swing.table.TableModel;


@Extension(provides = ReservationSummaryExtension.class, id = EventTimeCalculatorPlugin.PLUGIN_ID)
@Extension(provides = AppointmentSummaryExtension.class, id = EventTimeCalculatorPlugin.PLUGIN_ID)
public final class DurationCounter  implements ReservationSummaryExtension, AppointmentSummaryExtension
{
    EventTimeCalculatorFactory factory;
    protected final EventTimeCalculatorResources i18n;
    ClientFacade clientFacade;

    @Inject
    public DurationCounter(EventTimeCalculatorFactory factory,EventTimeCalculatorResources i18n,ClientFacade clientFacade)  {
        this.i18n = i18n;
        this.factory = factory;
        this.clientFacade = clientFacade;
    }

    public void init(final JTable table, JPanel summaryRow) {
 		
    	final JLabel counter = new JLabel();
        summaryRow.add( counter);
        summaryRow.add( Box.createHorizontalStrut(30));
        table.getSelectionModel().addListSelectionListener(arg0 -> {
            final User user;
            try
            {
                user = clientFacade.getUser();
            }
            catch (RaplaException e)
            {
                return;
            }
            EventTimeModel eventTimeModel = factory.getEventTimeModel(user);
            int[] selectedRows = table.getSelectedRows();
            TableModel model = table.getModel();
            TableSorter sorterModel = null;
            if ( model instanceof TableSorter)
            {
                sorterModel = ((TableSorter) model);
                model = ((TableSorter)model).getTableModel();
            }
            long totalduration = 0;
            for ( int row:selectedRows)
            {
                if (sorterModel != null)
                   row = sorterModel.modelIndex(row);
                if ( model instanceof RaplaTableModel)
                {
                    final Object objectAt = ((RaplaTableModel) model).getObjectAt(row);
                    long duration =0;
                    if ( objectAt instanceof Reservation)
                    {
                        duration = eventTimeModel.calcDuration((Reservation) objectAt);
                        if (duration < 0)
                        {
                            totalduration = -1;
                            break;
                        }
                    }
                    else if (objectAt instanceof AppointmentBlock)
                    {
                        duration = eventTimeModel.calcDuration((AppointmentBlock)objectAt);
                    }
                    totalduration+= duration;
                }
            }
            String durationString = totalduration < 0 ? i18n.getString("infinite") : eventTimeModel.format(totalduration);
            counter.setText( i18n.getString("total_duration") + " " + durationString + " ");
        });
     }
 }
