/**
 *
 */
package org.rapla.plugin.tableview;

import java.awt.Component;
import java.util.Date;

import javax.swing.JTable;
import javax.swing.table.DefaultTableCellRenderer;

import org.rapla.framework.RaplaLocale;

final public class DateCellRenderer extends DefaultTableCellRenderer {
    private static final long serialVersionUID = 1L;
    
    RaplaLocale raplaLocale;
    
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
	        boolean appendTime = !raplaLocale.toDate(date, false ).equals( date);
	        if ( appendTime )
	        {
	            value = raplaLocale.formatDateLong( date) + " " + raplaLocale.formatTime( date) ;
	        }
	        else
	        {
	            value = raplaLocale.formatDateLong( date);
	        }
        }
        //setComponentOrientation( ComponentOrientation.RIGHT_TO_LEFT  );
        return super.getTableCellRendererComponent( table, value, isSelected, hasFocus, row, column);
    }

}