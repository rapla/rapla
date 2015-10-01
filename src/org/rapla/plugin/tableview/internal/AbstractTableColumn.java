package org.rapla.plugin.tableview.internal;

import java.sql.Date;
import java.util.Locale;

import javax.swing.table.TableColumn;

import org.rapla.components.util.ParseDateException;
import org.rapla.components.util.SerializableDateTimeFormat;
import org.rapla.components.util.xml.XMLWriter;
import org.rapla.entities.MultiLanguageName;
import org.rapla.framework.RaplaLocale;
import org.rapla.plugin.tableview.DateCellRenderer;
import org.rapla.plugin.tableview.RaplaTableColumn;
import org.rapla.plugin.tableview.TableViewExtensionPoints;
import org.rapla.plugin.tableview.internal.TableConfig.TableColumnConfig;

abstract class AbstractTableColumn<T> implements RaplaTableColumn<T>
{
    private final TableColumnConfig column;
    RaplaLocale raplaLocale;

    AbstractTableColumn(TableColumnConfig column,RaplaLocale raplaLocale)
    {
        this.column = column;
        this.raplaLocale = raplaLocale;
    }

    protected Locale getLocale()
    {
        return getRaplaLocale().getLocale();
    }

    protected RaplaLocale getRaplaLocale()
    { 
        return raplaLocale;
    }

    @Override
    public String getColumnName()
    {
        final MultiLanguageName name = column.getName();
        return name.getName(getLocale().getLanguage());
    }

    @Override
    abstract public Object getValue(T object);
    
    @Override
    abstract public String getHtmlValue(T object);
    

    protected Object format(final String format)
    {
        if ( isDate() || isDatetime())
        {
            java.util.Date date;
            try
            {
                if ( isDatetime())
                {
                    date = SerializableDateTimeFormat.INSTANCE.parseTimestamp( format );
                }
                else
                {
                    boolean fillDate = false;
                    date = SerializableDateTimeFormat.INSTANCE.parseDate( format, fillDate);
                }
                return date;
            }
            catch (ParseDateException e)
            {
                return null;
            }
        }
        return format;
    }

    protected String getAnnotationName()
    {
        return TableViewExtensionPoints.COLUMN_ANNOTATION +column.getKey();
    }

    @Override
    public void init(TableColumn column)
    {
        if ( isDate())
        {
            column.setCellRenderer( new DateCellRenderer( getRaplaLocale()));
            column.setMaxWidth( 130 );
            column.setPreferredWidth( 130 );
        }
        else if ( isDatetime())
        {
            column.setCellRenderer( new DateCellRenderer( getRaplaLocale()));
            column.setMaxWidth( 175 );
            column.setPreferredWidth( 175 );
        }
        // TODO Auto-generated method stub
    }

    @Override
    public Class<?> getColumnClass()
    {
        if ( isDate() || isDatetime())
        {
            return Date.class;
        }
        return String.class;
    }

    private boolean isDatetime()
    {
        String type = column.getType();
        final boolean isDate = type.equals("datetime");
        return isDate;
    }

    private boolean isDate()
    {
        String type = column.getType();
        final boolean isDate = type.equals("date");
        return isDate;
    }


    protected String formatHtml(Object value)
    {
        if ( value == null)
        {
            return "";
        }
        if ( isDate() || isDatetime())
        {
            RaplaLocale raplaLocale = getRaplaLocale();
            if ( !(value instanceof Date))
            {
                value = "invalid date";                                    
            }
            else
            {
                Date date = (Date) value;
                if ( isDatetime())
                {
                    value =  raplaLocale.formatDateLong(date) + " " + raplaLocale.formatTime( date);
                }
                else 
                {
                    value =  raplaLocale.formatDateLong(date);
                }
                    
            }
        }
        return XMLWriter.encode(value.toString());
    }
}