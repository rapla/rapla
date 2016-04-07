/**
 *
 */
package org.rapla.plugin.tableview.client.swing;

import java.awt.Component;
import java.util.Date;

import javax.swing.JTable;
import javax.swing.table.DefaultTableCellRenderer;

import org.rapla.components.util.DateTools;
import org.rapla.framework.RaplaLocale;

final public class DateCellRenderer extends DefaultTableCellRenderer {
    private static final long serialVersionUID = 1L;
    
    RaplaLocale raplaLocale;

    private boolean substractDayWhenFullDay;
    
  
    public DateCellRenderer(RaplaLocale raplaLocale) {
        this.raplaLocale = raplaLocale;
    }
    public Component getTableCellRendererComponent( JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column )
    {
        final Date date = (Date) value;
        if ( date == null)
        {
        	value = "";
        }
        else
        {
            // don't append time when 0 or 24
	        boolean appendTime = !raplaLocale.toDate(date, false ).equals( date);
	        if ( appendTime )
	        {
	            value = raplaLocale.formatDateLong( date) + " " + raplaLocale.formatTime( date ) ;
	        }
	        else
	        {
	            if ( substractDayWhenFullDay )
	            {
	                value = raplaLocale.formatDateLong( DateTools.addDays(date, -1));
	            }
	            else
	            {
	                value = raplaLocale.formatDateLong( date);
	            }
	            
	        }
        }
        //setComponentOrientation( ComponentOrientation.RIGHT_TO_LEFT  );
        return super.getTableCellRendererComponent( table, value, isSelected, hasFocus, row, column);
    }
    
    public boolean isSubstractDayWhenFullDay() {
        return substractDayWhenFullDay;
    }
    public void setSubstractDayWhenFullDay(boolean substractDayWhenFullDay) {
        this.substractDayWhenFullDay = substractDayWhenFullDay;
    }

}