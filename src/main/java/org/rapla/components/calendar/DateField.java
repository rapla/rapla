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
package org.rapla.components.calendar;

import java.awt.Color;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Insets;
import java.awt.event.MouseEvent;
import java.text.DateFormat;
import java.text.FieldPosition;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

import org.rapla.components.calendar.DateRenderer.RenderingInfo;
/** The DateField only accepts characters that are part of
 * DateFormat.getDateInstance(DateFormat.SHORT,locale).  The
 * inputblocks are [date,month,year]. The order of the input-blocks is
 * determined by the locale. You can use the keyboard to navigate
 * between the blocks or to increment/decrement the blocks.
 * @see AbstractBlockField
 */

final public class DateField extends AbstractBlockField {
    private static final long serialVersionUID = 1L;

    private DateFormat m_outputFormat;
    private DateFormat m_parsingFormat;
    private Calendar m_calendar;
    private int m_rank[] = null;
    private char[] m_separators;
    private SimpleDateFormat m_weekdayFormat;
    private boolean m_weekdaysVisible = true;
    private DateRenderer m_dateRenderer;
    /** stores the y-coordinate of the weekdays display field*/
    private int m_weekdaysX;
    private boolean nullValuePossible;
    private boolean nullValue;
    RenderingInfo renderingInfo;
    
    public boolean isNullValue() {
        return nullValue;
    }

    public void setNullValue(boolean nullValue) {
        this.nullValue = nullValue;
    }

    public boolean isNullValuePossible() 
    {
        return nullValuePossible;
    }

    public void setNullValuePossible(boolean nullValuePossible) 
    {
        this.nullValuePossible = nullValuePossible;
    }

    public DateField() {
        this(Locale.getDefault(),TimeZone.getDefault());
    }

    public DateField(Locale locale,TimeZone timeZone) {
        super();
        m_calendar = Calendar.getInstance(timeZone, locale);
        super.setLocale(locale);
        setFormat();
        setDate(new Date());
    }


    /** you can choose, if weekdays should be displayed in the right corner of the DateField.
        Default is true.
    */
    public void setWeekdaysVisible(boolean m_weekdaysVisible) {
        this.m_weekdaysVisible = m_weekdaysVisible;
    }

    /** sets the DateRenderer for the calendar */
    public void setDateRenderer(DateRenderer dateRenderer) {
        m_dateRenderer = dateRenderer;
    }

    private void setFormat() {
        m_parsingFormat = DateFormat.getDateInstance(DateFormat.SHORT, getLocale());
        m_weekdayFormat = new SimpleDateFormat("EE", getLocale());
        TimeZone timeZone = getTimeZone();
		m_parsingFormat.setTimeZone(timeZone);
        m_weekdayFormat.setTimeZone(timeZone);

        String formatStr = m_parsingFormat.format(m_calendar.getTime());
        FieldPosition datePos = new FieldPosition(DateFormat.DATE_FIELD);
        FieldPosition monthPos = new FieldPosition(DateFormat.MONTH_FIELD);
        FieldPosition yearPos = new FieldPosition(DateFormat.YEAR_FIELD);
        m_parsingFormat.format(m_calendar.getTime(), new StringBuffer(),datePos);
        m_parsingFormat.format(m_calendar.getTime(), new StringBuffer(),monthPos);
        m_parsingFormat.format(m_calendar.getTime(), new StringBuffer(),yearPos);

        int mp = monthPos.getBeginIndex();
        int dp = datePos.getBeginIndex();
        int yp = yearPos.getBeginIndex();
        int pos[] = null;
        //      System.out.println(formatStr + " day:"+dp+" month:"+mp+" year:"+yp);
        if (mp<0 || dp<0 || yp<0) {
            throw new IllegalArgumentException("Can't parse the date-format for this locale");
        }
        // quick and diry sorting
        if (dp<mp && mp<yp) {
            pos = new int[] {dp,mp,yp};
            m_rank = new int[] {Calendar.DATE, Calendar.MONTH, Calendar.YEAR};
        } else if (dp<yp && yp<mp) {
            pos = new int[] {dp,yp,mp};
            m_rank = new int[] {Calendar.DATE, Calendar.YEAR, Calendar.MONTH};
        } else if (mp<dp && dp<yp) {
            pos = new int[] {mp,dp,yp};
            m_rank = new int[] {Calendar.MONTH, Calendar.DATE, Calendar.YEAR};
        } else if (mp<yp && yp<dp) {
            pos = new int[] {mp,yp,dp};
            m_rank = new int[] {Calendar.MONTH, Calendar.YEAR, Calendar.DATE};
        } else if (yp<dp && dp<mp) {
            pos = new int[] {yp,dp,mp};
            m_rank = new int[] {Calendar.YEAR, Calendar.DATE, Calendar.MONTH};
        } else if (yp<mp && mp<dp) {
            pos = new int[] {yp,mp,dp};
            m_rank = new int[] {Calendar.YEAR, Calendar.MONTH, Calendar.DATE};
        } else {
            throw new IllegalStateException("Ordering y=" +yp + " d=" +dp + " m="+mp +" not supported");
        }
        char firstSeparator = formatStr.charAt(pos[1]-1);
        char secondSeparator = formatStr.charAt(pos[2]-1);
        //      System.out.println("first-sep:"+firstSeparator+" sec-sep:"+secondSeparator);
        if (Character.isDigit(firstSeparator)
            || Character.isDigit(secondSeparator))
            throw new IllegalArgumentException("Can't parse the date-format for this locale");
        m_separators = new char[] {firstSeparator,secondSeparator};
        StringBuffer buf = new StringBuffer();
        for (int i=0;i<m_rank.length;i++) {
            if (m_rank[i] == Calendar.YEAR) {
                buf.append("yyyy");
            } else if (m_rank[i] == Calendar.MONTH) {
                buf.append("MM");
            } else if (m_rank[i] == Calendar.DATE) {
                buf.append("dd");
            }

            if (i==0) {
                buf.append(firstSeparator);
            } else if (i==1) {
                buf.append(secondSeparator);
            }
        }
        setColumns(buf.length() -1 + (m_weekdaysVisible ? 1:0 ));
        m_outputFormat= new SimpleDateFormat(buf.toString(),getLocale());
        m_outputFormat.setTimeZone(timeZone);
    }

    public TimeZone getTimeZone() {
        if (m_calendar != null)
            return m_calendar.getTimeZone();
        return
            null;
    }

    public Date getDate() 
    {
        if ( nullValue && nullValuePossible)
        {
            return null;
        }
        Date date = m_calendar.getTime();
		return date;
    }

    public void setDate(Date value) 
    {
        if ( value == null)
        {
            if ( !nullValuePossible)
            {
                return;
            }
        }
        nullValue = value == null;
        if ( !nullValue)
        {
            m_calendar.setTime(value);
            if (m_dateRenderer != null) {
                   renderingInfo = m_dateRenderer.getRenderingInfo(
                                                m_calendar.get(Calendar.DAY_OF_WEEK)
                                                ,m_calendar.get(Calendar.DATE)
                                                ,m_calendar.get(Calendar.MONTH) + 1 
                                                ,m_calendar.get(Calendar.YEAR)
                                                );
                   String text = renderingInfo.getTooltipText();
                   setToolTipText(text);
            }
            String formatedDate = m_outputFormat.format(value);
			setText(formatedDate);
        }
        else
        {
            setText("");
        }
    }

    protected char[] getSeparators() {
        return m_separators;
    }

    protected boolean isSeparator(char c) {
        for (int i=0;i<m_separators.length;i++)
            if (m_separators[i] == c)
                return true;
        return false;
    }

    /** returns the parsingFormat of the selected locale.
        This is same as the default date-format of the selected locale.*/
    public DateFormat getParsingFormat() {
        return m_parsingFormat;
    }

    /** returns the output format of the date-field.
        The OutputFormat always uses the full block size:
        01.01.2000 instead of 1.1.2000  */
    public DateFormat getOutputFormat() {
        return m_outputFormat;
    }

    protected void changeSelectedBlock(int[] blocks,int block,String selected,int count) {
        int type = m_rank[block];
        if (m_rank.length<block)
            return;

        if (type == Calendar.MONTH)
            if (Math.abs(count) == 10)
                m_calendar.add(type,count/Math.abs(count) * 3);
            else
                m_calendar.add(type,count/Math.abs(count));
        else if (type == Calendar.DATE)
            if (Math.abs(count) == 10)
                m_calendar.add(type,count/Math.abs(count) * 7);
            else
                m_calendar.add(type,count/Math.abs(count));
        else
            m_calendar.add(type,count);

        setDate(m_calendar.getTime());
        calcBlocks(blocks);
        markBlock(blocks,block);
    }

    public String getToolTipText(MouseEvent event) {
        if (m_weekdaysVisible && event.getX() >= m_weekdaysX)
            return super.getToolTipText(event);
        return null;
    }

    public boolean blocksValid() {
    	String dateTxt = null;
        try {
        	dateTxt = getText();
            if ( isNullValuePossible() && dateTxt.length() == 0)
            {
                nullValue = true;
                return true;
            }
            m_calendar.setTime(m_parsingFormat.parse(dateTxt));
            
            nullValue = false;
            return true;
        } catch (ParseException e) {
        	return false;
        }
    }

    static int[] BLOCKLENGTH = new int[] {2,2,5};

    protected int blockCount() {
        return BLOCKLENGTH.length;
    }

    protected void mark(int dot,int mark) {
        super.mark(dot,mark);
        if (!m_weekdaysVisible)
            repaint();
    }

    protected int maxBlockLength(int block) {
        return BLOCKLENGTH[block];
    }

    protected boolean isValidChar(char c) {
        return (Character.isDigit(c) || isSeparator(c) );
    }

    /** This method is necessary to shorten the names down to 2 characters.
        Some date-formats ignore the setting.
    */
    private String small(String string) {
        return string.substring(0,Math.min(string.length(),2));
    }

    public void paint(Graphics g) {
        super.paint(g);
        if (!m_weekdaysVisible)
            return;
        Insets insets = getInsets();
        final String format = nullValue ? "" :m_weekdayFormat.format(m_calendar.getTime());
        String s = small(format);
        FontMetrics fm = g.getFontMetrics();
        int width = fm.stringWidth(s);
        int x = getWidth()-width-insets.right-3;
        m_weekdaysX = x;
        int y = insets.top + (getHeight() - (insets.bottom + insets.top))/2 + fm.getAscent()/3;
        g.setColor(Color.gray);
        if (renderingInfo != null) {
            
            Color color = renderingInfo.getBackgroundColor();
            if (color != null) {
                g.setColor(color);
                g.fillRect(x-1, insets.top, width + 3 , getHeight() - insets.bottom - insets.top - 1);
            }
            color = renderingInfo.getForegroundColor();
            if (color != null) {
                g.setColor(color);
            }
            else
            {
                g.setColor(Color.GRAY);
            }
        }
        g.drawString(s,x,y);
    }
}
