/*--------------------------------------------------------------------------*
 | Copyright (C) 2014 Christopher Kohlhaas                                  |
 |                                                                          |
 | This program is free software; you can redistribute it and/or modify     |
 | it under the terms of the GNU General Public License as published by the |
 | Free Software Foundation. A copy of the license has been included with   |
 | these distribution in the COPYING file, if not go to www.fsf.org         |
 |                                                                          |
 | As a special exception, you are granted the permissions to link this     |
 | program with every library, which license fulfills the Open Source       |
 | Definition as published by the Open Source Initiative (OSI).             |
 *--------------------------------------------------------------------------*/
package org.rapla.client.internal;

import java.net.URL;
import java.util.Collection;
import java.util.Date;
import java.util.Iterator;

import org.rapla.RaplaResources;
import org.rapla.components.util.Tools;
import org.rapla.components.util.xml.XMLWriter;
import org.rapla.entities.Named;
import org.rapla.entities.RaplaObject;
import org.rapla.entities.RaplaType;
import org.rapla.entities.Timestamp;
import org.rapla.entities.User;
import org.rapla.facade.RaplaComponent;
import org.rapla.facade.RaplaFacade;
import org.rapla.framework.RaplaException;
import org.rapla.framework.RaplaLocale;
import org.rapla.logger.Logger;

public abstract class HTMLInfo<T> extends RaplaComponent
{

    public HTMLInfo(RaplaResources i18n, RaplaLocale raplaLocale, RaplaFacade facade, Logger logger)
    {
        super(facade, i18n, raplaLocale, logger);
        //        this.i18n = i18n;
        //        this.raplaLocale = raplaLacale;
    }

    /** performs xml-encoding of a string the output goes to the buffer*/
    static public void encode(String text, StringBuffer buf)
    {
        buf.append(encode(text));
    }

    static public String encode(String string)
    {
        String text = XMLWriter.encode(string);
        if (text.indexOf('\n') > 0)
        {
            StringBuffer buf = new StringBuffer();
            int size = text.length();
            for (int i = 0; i < size; i++)
            {
                char c = text.charAt(i);
                if (c == '\n')
                {
                    buf.append("<br>");
                }
                else
                {
                    buf.append(c);
                } // end of switch ()
            } // end of for ()
            text = buf.toString();
        }
        return text;
    }

    protected void insertModificationRow(Timestamp timestamp, StringBuffer buf)
    {
        final Date createTime = timestamp.getCreateDate();
        final Date lastChangeTime = timestamp.getLastChanged();
        if (lastChangeTime != null)
        {
            buf.append("<div style=\"font-size:7px;margin-bottom:4px;\">");
            RaplaLocale raplaLocale = getRaplaLocale();
            if (createTime != null)
            {
                buf.append(getI18n().getString("created_at"));
                buf.append(" ");
                buf.append(raplaLocale.formatTimestamp(createTime));
                buf.append(", ");
            }
            buf.append(getI18n().getString("last_changed"));
            buf.append(" ");
            buf.append(raplaLocale.formatTimestamp(lastChangeTime));
            buf.append("</div>");
            buf.append("\n");
        }
    }

    static public void addColor(String color, StringBuffer buf)
    {
        buf.append(" color=\"");
        buf.append(color);
        buf.append('\"');
    }

    static public void createTable(Collection<Row> attributes, StringBuffer buf, boolean encodeValues)
    {
        buf.append("<table class=\"infotable\" cellpadding=\"1\">");
        Iterator<Row> it = attributes.iterator();
        while (it.hasNext())
        {
            Row att = it.next();
            buf.append("<tr>\n");
            buf.append("<td class=\"label\" valign=\"top\" style=\"white-space:nowrap\">");
            encode(att.field, buf);
            if (att.field.length() > 0)
            {
                buf.append(":");
            }
            buf.append("</td>\n");
            buf.append("<td class=\"value\" valign=\"top\">");
            String value = att.value;
            if (value != null)
            {
                URL url = Tools.getUrl(value);
                if (url != null)
                {
                    buf.append("<a href=\"");
                    buf.append(url.toExternalForm());
                    buf.append("\">");
                }
                if (encodeValues)
                {
                    encode(value, buf);
                }
                else
                {
                    buf.append(value);
                }
                if (url != null)
                {
                    buf.append("</a>");
                }
            }
            buf.append("</td>");
            buf.append("</tr>\n");
        }
        buf.append("</table>");
    }

    static public String createTable(Collection<Row> attributes, boolean encodeValues)
    {
        StringBuffer buf = new StringBuffer();
        createTable(attributes, buf, encodeValues);
        return buf.toString();
    }

    static public void createTable(Collection<Row> attributes, StringBuffer buf)
    {
        createTable(attributes, buf, true);
    }

    static public String createTable(Collection<Row> attributes)
    {
        StringBuffer buf = new StringBuffer();
        createTable(attributes, buf);
        return buf.toString();
    }

    static public void highlight(String text, StringBuffer buf)
    {
        buf.append("<FONT color=\"red\">");
        encode(text, buf);
        buf.append("</FONT>");
    }

    static public String highlight(String text)
    {
        StringBuffer buf = new StringBuffer();
        highlight(text, buf);
        return buf.toString();
    }

    static public void strong(String text, StringBuffer buf)
    {
        buf.append("<strong>");
        encode(text, buf);
        buf.append("</strong>");
    }

    static public String strong(String text)
    {
        StringBuffer buf = new StringBuffer();
        strong(text, buf);
        return buf.toString();
    }

    abstract public String createHTMLAndFillLinks(T object, LinkController controller, User user) throws RaplaException;

    public String getTitle(T object)
    {
        StringBuffer buf = new StringBuffer();
        buf.append(getI18n().getString("view"));
        if (object instanceof RaplaObject)
        {
            String localName = RaplaType.getLocalName((RaplaObject) object);
            try
            {
                String name = getI18n().getString(localName);
                buf.append(" ");
                buf.append(name);
            }
            catch (Exception ex)
            {
                // in case rapla type translation not found do nothing
            }
        }
        if (object instanceof Named)
        {
            buf.append(" ");
            buf.append(((Named) object).getName(getI18n().getLocale()));
        }
        return buf.toString();
    }

    public String getTooltip(T object, User user) throws RaplaException
    {
        if (object instanceof Named)
            return ((Named) object).getName(getI18n().getLocale());
        return null;
    }

    static public class Row
    {
        String field;
        String value;

        Row(String field, String value)
        {
            this.field = field;
            this.value = value;
        }

        public String getField()
        {
            return field;
        }

        public String getValue()
        {
            return value;
        }
    }

}






