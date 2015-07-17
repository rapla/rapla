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

import java.text.DateFormat;
import java.text.FieldPosition;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;
/** The TimeField only accepts characters that are part of DateFormat.getTimeInstance(DateFormat.SHORT,locale).
 * The input blocks are [hour,minute,am_pm] or [hour_of_day,minute]
 * depending on the selected locale. You can use the keyboard to
 * navigate between the blocks or to increment/decrement the blocks.
 * @see AbstractBlockField
 */

final public class TimeField extends AbstractBlockField {
    private static final long serialVersionUID = 1L;

    private DateFormat m_outputFormat;
    private DateFormat m_parsingFormat;
    private Calendar m_calendar;
    private int m_rank[] = null;
    private char[] m_separators;
    private boolean m_useAM_PM = false;
    private boolean americanAM_PM_character = false;

    public TimeField() {
        this(Locale.getDefault());
    }

    public TimeField(Locale locale) {
        this(locale, TimeZone.getDefault());
    }

    public TimeField(Locale locale,TimeZone timeZone) {
        super();
        m_calendar = Calendar.getInstance(timeZone, locale);
        super.setLocale(locale);
        setFormat();
        setTime(new Date());
    }


    public void setLocale(Locale locale) {
        super.setLocale(locale);
        if (locale != null && getTimeZone() != null)
            setFormat();
    }

    private void setFormat() {
        m_parsingFormat = DateFormat.getTimeInstance(DateFormat.SHORT, getLocale());
        m_parsingFormat.setTimeZone(getTimeZone());
        Date oldDate = m_calendar.getTime();
        m_calendar.set(Calendar.HOUR_OF_DAY,0);
        m_calendar.set(Calendar.MINUTE,0);
        String formatStr = m_parsingFormat.format(m_calendar.getTime());

        FieldPosition minutePos = new FieldPosition(DateFormat.MINUTE_FIELD);
        m_parsingFormat.format(m_calendar.getTime(), new StringBuffer(),minutePos);

        FieldPosition hourPos = new FieldPosition(DateFormat.HOUR0_FIELD);
        StringBuffer hourBuf = new StringBuffer();
        m_parsingFormat.format(m_calendar.getTime(), hourBuf,hourPos);

        FieldPosition hourPos1 = new FieldPosition(DateFormat.HOUR1_FIELD);
        StringBuffer hourBuf1 = new StringBuffer();
        m_parsingFormat.format(m_calendar.getTime(), hourBuf1,hourPos1);

        FieldPosition amPmPos = new FieldPosition(DateFormat.AM_PM_FIELD);
        m_parsingFormat.format(m_calendar.getTime(), new StringBuffer(),amPmPos);

        String zeroDigit = m_parsingFormat.getNumberFormat().format(0);
        int zeroPos = hourBuf.toString().indexOf(zeroDigit,hourPos.getBeginIndex());

        // 0:30 or 12:30
        boolean zeroBased = (zeroPos == 0);
        String testFormat = m_parsingFormat.format(  m_calendar.getTime() ).toLowerCase();
        int mp = minutePos.getBeginIndex();
        int ap = amPmPos.getBeginIndex();
        int hp = Math.max( hourPos.getBeginIndex(), hourPos1.getBeginIndex() );

        int pos[] = null;

        // Use am/pm
        if (amPmPos.getEndIndex()>0) {
            m_useAM_PM = true;
            americanAM_PM_character = m_useAM_PM && (testFormat.indexOf( "am" )>=0 || testFormat.indexOf( "pm" )>=0);
            //      System.out.println(formatStr + " hour:"+hp+" minute:"+mp+" ampm:"+ap);
            if (hp<0 || mp<0 || ap<0 || formatStr == null) {
                throw new IllegalArgumentException("Can't parse the time-format for this locale: " + formatStr);
            }
            // quick and diry sorting
            if (mp<hp && hp<ap) {
                pos = new int[] {mp,hp,ap};
                m_rank = new int[] {Calendar.MINUTE, Calendar.HOUR, Calendar.AM_PM};
            } else if (mp<ap && ap<hp) {
                pos = new int[] {mp,ap,hp};
                m_rank = new int[] {Calendar.MINUTE, Calendar.AM_PM, Calendar.HOUR};
            } else if (hp<mp && mp<ap) {
                pos = new int[] {hp,mp,ap};
                m_rank = new int[] {Calendar.HOUR, Calendar.MINUTE, Calendar.AM_PM};
            } else if (hp<ap && ap<mp) {
                pos = new int[] {hp,ap,mp};
                m_rank = new int[] {Calendar.HOUR, Calendar.AM_PM, Calendar.MINUTE};
            } else if (ap<mp && mp<hp) {
                pos = new int[] {ap,mp,hp};
                m_rank = new int[] {Calendar.AM_PM, Calendar.MINUTE, Calendar.HOUR};
            } else if (ap<hp && hp<mp) {
                pos = new int[] {ap,hp,mp};
                m_rank = new int[] {Calendar.AM_PM, Calendar.HOUR, Calendar.MINUTE};
            }
            else
            {
                throw new IllegalStateException("Ordering am=" +ap + " h=" +hp + " m="+mp +" not supported");
            }
            
            char firstSeparator = formatStr.charAt(pos[1]-1);
            char secondSeparator = formatStr.charAt(pos[2]-1);
            if (Character.isDigit(firstSeparator)
                || Character.isDigit(secondSeparator))
                throw new IllegalArgumentException("Can't parse the time-format for this locale: " + formatStr);
            m_separators = new char[] {firstSeparator,secondSeparator};
            StringBuffer buf = new StringBuffer();
            for (int i=0;i<m_rank.length;i++) {
                if (m_rank[i] == Calendar.HOUR) {
                    if (zeroBased)
                        buf.append("KK");
                    else
                        buf.append("hh");
                } else if (m_rank[i] == Calendar.MINUTE) {
                    buf.append("mm");
                } else if (m_rank[i] == Calendar.AM_PM) {
                    buf.append("a");
                }

                
                if (i==0 && americanAM_PM_character) {
                    buf.append(firstSeparator);
                } else if (i==1) {
                    buf.append(secondSeparator);
                }
            }
            m_outputFormat= new SimpleDateFormat(buf.toString(), getLocale());
            m_outputFormat.setTimeZone(getTimeZone());
            setColumns(7);
        // Don't use am/pm
        } else {
            m_useAM_PM = false;
            //      System.out.println(formatStr + " hour:"+hp+" minute:" + mp);
            if (hp<0 || mp<0) {
                throw new IllegalArgumentException("Can't parse the time-format for this locale");
            }
            // quick and diry sorting
            if (mp<hp) {
                pos = new int[] {mp,hp};
                m_rank = new int[] {Calendar.MINUTE, Calendar.HOUR_OF_DAY};
            } else {
                pos = new int[] {hp,mp};
                m_rank = new int[] {Calendar.HOUR_OF_DAY, Calendar.MINUTE};
            }
            char firstSeparator = formatStr.charAt(pos[1]-1);
            if (Character.isDigit(firstSeparator))
                throw new IllegalArgumentException("Can't parse the time-format for this locale");
            m_separators = new char[] {firstSeparator};
            StringBuffer buf = new StringBuffer();
            for (int i=0;i<m_rank.length;i++) {
                if (m_rank[i] == Calendar.HOUR_OF_DAY) {
                    if (zeroBased)
                        buf.append("HH");
                    else
                        buf.append("kk");
                } else if (m_rank[i] == Calendar.MINUTE) {
                    buf.append("mm");
                } else if (m_rank[i] == Calendar.AM_PM) {
                    buf.append("a");
                }

                if (i==0) {
                    buf.append(firstSeparator);
                }
            }
            m_outputFormat= new SimpleDateFormat(buf.toString(), getLocale());
            m_outputFormat.setTimeZone(getTimeZone());
            setColumns(5);
        }
        m_calendar.setTime(oldDate);
    }

    public TimeZone getTimeZone() {
        if (m_calendar != null)
            return m_calendar.getTimeZone();
        return
            null;
    }


    /** returns the parsingFormat of the selected locale.
        This is same as the default time-format of the selected locale.*/
    public DateFormat getParsingFormat() {
        return m_parsingFormat;
    }

    /** returns the output format of the date-field.
        The outputFormat always uses the full block size:
        01:02 instead of 1:02  */
    public DateFormat getOutputFormat() {
        return m_outputFormat;
    }

    private void update(TimeZone timeZone, Locale locale) {
        Date date = getTime();
        m_calendar = Calendar.getInstance(timeZone, locale);
        setFormat();
        setText(m_outputFormat.format(date));
    }

    public void setTimeZone(TimeZone timeZone) {
        update(timeZone, getLocale());
    }

    public Date getTime() {
        return m_calendar.getTime();
    }

    public void setTime(Date value) {
        m_calendar.setTime(value);
        setText(m_outputFormat.format(value));
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


    protected void changeSelectedBlock(int[] blocks,int block,String selected,int count) {
        int type = m_rank[block];
        if (m_rank.length<block)
            return;

        if (type == Calendar.AM_PM) {
            m_calendar.roll(Calendar.HOUR_OF_DAY,12);
        } else if (type == Calendar.MINUTE) {
            m_calendar.add(type,count);
        } else {
            if (Math.abs(count) == 10)
                m_calendar.add(type,count/Math.abs(count) * 12);
            else
                m_calendar.add(type,count/Math.abs(count));
        }
        setTime(m_calendar.getTime());
        calcBlocks(blocks);
        markBlock(blocks,block);
    }

    public boolean blocksValid() {
        try {
            m_calendar.setTime(m_parsingFormat.parse(getText()));
            return true;
        } catch (ParseException e) {
            return false;
        }
    }

    static int[] BLOCKLENGTH1 = new int[] {2,2,2};
    static int[] BLOCKLENGTH2 = new int[] {2,2};

    protected int blockCount() {
        return m_useAM_PM ? BLOCKLENGTH1.length : BLOCKLENGTH2.length;
    }

    protected int maxBlockLength(int block) {
        return m_useAM_PM ? BLOCKLENGTH1[block] : BLOCKLENGTH2[block];
    }

    protected boolean isValidChar(char c) {
        return (Character.isDigit(c)
                || isSeparator(c)
                || ((m_useAM_PM) &&
                           ((americanAM_PM_character && (c == 'a' || c=='A' || c=='p' || c=='P' || c=='m' || c=='M'))
                           || (!americanAM_PM_character && Character.isLetter(  c ))
                           )
                    
                    ));
    }

}
