/**
 *
 */
package org.rapla.plugin.tableview.client.swing;

import org.rapla.components.util.DateTools;
import org.rapla.framework.RaplaLocale;

import javax.swing.JTable;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.Component;
import java.util.Date;

final public class DateCellRenderer extends DefaultTableCellRenderer {
    private static final long serialVersionUID = 1L;
    
    final private RaplaLocale raplaLocale;


    final private boolean appendTime;
    
  
    public DateCellRenderer(RaplaLocale raplaLocale, boolean appendTime) {
        this.raplaLocale = raplaLocale;
        this.appendTime = appendTime;
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
            if ( appendTime )
	        {
	            value = raplaLocale.formatDateLong( date) + " " + raplaLocale.formatTime( date ) ;
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