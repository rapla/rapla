package org.rapla.plugin.tableview.internal;

import java.util.Collections;
import java.util.Date;
import java.util.Locale;

import javax.swing.table.TableColumn;

import org.rapla.components.util.ParseDateException;
import org.rapla.components.util.SerializableDateTimeFormat;
import org.rapla.components.util.xml.XMLWriter;
import org.rapla.entities.IllegalAnnotationException;
import org.rapla.entities.MultiLanguageName;
import org.rapla.entities.dynamictype.Classification;
import org.rapla.entities.dynamictype.internal.DynamicTypeImpl;
import org.rapla.entities.dynamictype.internal.DynamicTypeImpl.DynamicTypeParseContext;
import org.rapla.entities.dynamictype.internal.ParsedText;
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
        final Locale locale = getLocale();
        return name.getName(locale);
    }

    @Override
    abstract public Object getValue(T object);
    
    @Override
    abstract public String getHtmlValue(T object);
    

    protected Object format(Object object) 
    {
        final Locale locale = getLocale();
        final String annotationName = getAnnotationName();
        final ParsedText.EvalContext context = new ParsedText.EvalContext(locale, annotationName, Collections.singletonList(object));
        final Classification classification = ParsedText.guessClassification( object);
        final DynamicTypeImpl type = (DynamicTypeImpl) classification.getType();
        ParsedText parsedAnnotation = type.getParsedAnnotation( annotationName);
        if ( parsedAnnotation == null)
        {
            final String defaultValue = column.getDefaultValue();
            parsedAnnotation = new ParsedText( defaultValue );
            final DynamicTypeParseContext parseContext = type.getParseContext();
            try
            {
                parsedAnnotation.init( parseContext);
            }
            catch ( IllegalAnnotationException ex)
            {
                return null;
            }
        }
        String format = parsedAnnotation.formatName( context);
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