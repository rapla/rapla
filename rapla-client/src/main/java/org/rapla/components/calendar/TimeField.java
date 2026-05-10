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
import java.time.LocalDate;
import java.time.LocalTime;
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

    /** PRD 014 Calendar migration: local field-rank constants replace the
     *  {@link java.util.Calendar} integer-field constants this class previously
     *  borrowed. Their numeric values are arbitrary; only equality comparisons matter. */
    private static final int FIELD_HOUR = 0;        // 1-12
    private static final int FIELD_HOUR_OF_DAY = 1; // 0-23
    private static final int FIELD_MINUTE = 2;
    private static final int FIELD_AM_PM = 3;

    private DateFormat m_outputFormat;
    private DateFormat m_parsingFormat;
    private LocalTime m_time;
    private TimeZone m_timeZone;
    private int[] m_rank = null;
    private char[] m_separators;
    private boolean m_useAM_PM = false;
    private boolean americanAM_PM_character = false;

    public TimeField() {
        this(Locale.getDefault());
    }

    public TimeField(Locale locale) {
        this(locale, TimeZone.getDefault());
    }

    public TimeField(Locale locale, TimeZone timeZone) {
        super();
        m_timeZone = timeZone;
        m_time = LocalTime.now();
        super.setLocale(locale);
        setFormat();
        setTime(LocalTime.now());
    }


    public void setLocale(Locale locale) {
        super.setLocale(locale);
        if (locale != null && getTimeZone() != null)
            setFormat();
    }

    /** {@code DateFormat} only accepts {@link Date}. Build one at "today + current m_time"
     *  in the configured TimeZone. */
    private Date asDate() {
        return Date.from(LocalDate.now().atTime(m_time).atZone(m_timeZone.toZoneId()).toInstant());
    }

    /** Same shape as {@link #asDate()} but at midnight — used during setFormat() to
     *  inspect format-string field positions. */
    private Date midnightSample() {
        return Date.from(LocalDate.now().atStartOfDay(m_timeZone.toZoneId()).toInstant());
    }

    private void setFormat() {
        m_parsingFormat = DateFormat.getTimeInstance(DateFormat.SHORT, getLocale());
        m_parsingFormat.setTimeZone(m_timeZone);
        Date midnight = midnightSample();
        String formatStr = m_parsingFormat.format(midnight);

        FieldPosition minutePos = new FieldPosition(DateFormat.MINUTE_FIELD);
        m_parsingFormat.format(midnight, new StringBuffer(), minutePos);

        FieldPosition hourPos = new FieldPosition(DateFormat.HOUR0_FIELD);
        StringBuffer hourBuf = new StringBuffer();
        m_parsingFormat.format(midnight, hourBuf, hourPos);

        FieldPosition hourPos1 = new FieldPosition(DateFormat.HOUR1_FIELD);
        StringBuffer hourBuf1 = new StringBuffer();
        m_parsingFormat.format(midnight, hourBuf1, hourPos1);

        FieldPosition amPmPos = new FieldPosition(DateFormat.AM_PM_FIELD);
        m_parsingFormat.format(midnight, new StringBuffer(), amPmPos);

        String zeroDigit = m_parsingFormat.getNumberFormat().format(0);
        int zeroPos = hourBuf.toString().indexOf(zeroDigit, hourPos.getBeginIndex());

        // 0:30 or 12:30
        boolean zeroBased = (zeroPos == 0);
        String testFormat = m_parsingFormat.format(midnight).toLowerCase();
        int mp = minutePos.getBeginIndex();
        int ap = amPmPos.getBeginIndex();
        int hp = Math.max(hourPos.getBeginIndex(), hourPos1.getBeginIndex());

        int[] pos = null;

        // Use am/pm
        if (amPmPos.getEndIndex() > 0) {
            m_useAM_PM = true;
            americanAM_PM_character = m_useAM_PM && (testFormat.contains("am") || testFormat.contains("pm"));
            if (hp < 0 || mp < 0 || ap < 0 || formatStr == null) {
                throw new IllegalArgumentException("Can't parse the time-format for this locale: " + formatStr);
            }
            // quick and diry sorting
            if (mp < hp && hp < ap) {
                pos = new int[]{mp, hp, ap};
                m_rank = new int[]{FIELD_MINUTE, FIELD_HOUR, FIELD_AM_PM};
            } else if (mp < ap && ap < hp) {
                pos = new int[]{mp, ap, hp};
                m_rank = new int[]{FIELD_MINUTE, FIELD_AM_PM, FIELD_HOUR};
            } else if (hp < mp && mp < ap) {
                pos = new int[]{hp, mp, ap};
                m_rank = new int[]{FIELD_HOUR, FIELD_MINUTE, FIELD_AM_PM};
            } else if (hp < ap && ap < mp) {
                pos = new int[]{hp, ap, mp};
                m_rank = new int[]{FIELD_HOUR, FIELD_AM_PM, FIELD_MINUTE};
            } else if (ap < mp && mp < hp) {
                pos = new int[]{ap, mp, hp};
                m_rank = new int[]{FIELD_AM_PM, FIELD_MINUTE, FIELD_HOUR};
            } else if (ap < hp && hp < mp) {
                pos = new int[]{ap, hp, mp};
                m_rank = new int[]{FIELD_AM_PM, FIELD_HOUR, FIELD_MINUTE};
            } else {
                throw new IllegalStateException("Ordering am=" + ap + " h=" + hp + " m=" + mp + " not supported");
            }

            char firstSeparator = formatStr.charAt(pos[1] - 1);
            char secondSeparator = formatStr.charAt(pos[2] - 1);
            if (Character.isDigit(firstSeparator)
                    || Character.isDigit(secondSeparator))
                throw new IllegalArgumentException("Can't parse the time-format for this locale: " + formatStr);
            m_separators = new char[]{firstSeparator, secondSeparator};
            StringBuffer buf = new StringBuffer();
            for (int i = 0; i < m_rank.length; i++) {
                if (m_rank[i] == FIELD_HOUR) {
                    if (zeroBased)
                        buf.append("KK");
                    else
                        buf.append("hh");
                } else if (m_rank[i] == FIELD_MINUTE) {
                    buf.append("mm");
                } else if (m_rank[i] == FIELD_AM_PM) {
                    buf.append("a");
                }


                if (i == 0 && americanAM_PM_character) {
                    buf.append(firstSeparator);
                } else if (i == 1) {
                    buf.append(secondSeparator);
                }
            }
            m_outputFormat = new SimpleDateFormat(buf.toString(), getLocale());
            m_outputFormat.setTimeZone(m_timeZone);
            setColumns(7);
            // Don't use am/pm
        } else {
            m_useAM_PM = false;
            if (hp < 0 || mp < 0) {
                throw new IllegalArgumentException("Can't parse the time-format for this locale");
            }
            // quick and diry sorting
            if (mp < hp) {
                pos = new int[]{mp, hp};
                m_rank = new int[]{FIELD_MINUTE, FIELD_HOUR_OF_DAY};
            } else {
                pos = new int[]{hp, mp};
                m_rank = new int[]{FIELD_HOUR_OF_DAY, FIELD_MINUTE};
            }
            char firstSeparator = formatStr.charAt(pos[1] - 1);
            if (Character.isDigit(firstSeparator))
                throw new IllegalArgumentException("Can't parse the time-format for this locale");
            m_separators = new char[]{firstSeparator};
            StringBuffer buf = new StringBuffer();
            for (int i = 0; i < m_rank.length; i++) {
                if (m_rank[i] == FIELD_HOUR_OF_DAY) {
                    if (zeroBased)
                        buf.append("HH");
                    else
                        buf.append("kk");
                } else if (m_rank[i] == FIELD_MINUTE) {
                    buf.append("mm");
                } else if (m_rank[i] == FIELD_AM_PM) {
                    buf.append("a");
                }

                if (i == 0) {
                    buf.append(firstSeparator);
                }
            }
            m_outputFormat = new SimpleDateFormat(buf.toString(), getLocale());
            m_outputFormat.setTimeZone(m_timeZone);
            setColumns(5);
        }
    }

    public TimeZone getTimeZone() {
        return m_timeZone;
    }


    /** returns the parsingFormat of the selected locale.
     * This is same as the default time-format of the selected locale.*/
    public DateFormat getParsingFormat() {
        return m_parsingFormat;
    }

    /** returns the output format of the date-field.
     * The outputFormat always uses the full block size:
     * 01:02 instead of 1:02  */
    public DateFormat getOutputFormat() {
        return m_outputFormat;
    }

    public void setTimeZone(TimeZone timeZone) {
        LocalTime time = m_time;
        m_timeZone = timeZone;
        setTime(time);
        setFormat();
    }

    public LocalTime getTime() {
        return m_time;
    }

    public void setTime(LocalTime value) {
        m_time = LocalTime.of(value.getHour(), value.getMinute(), value.getSecond());
        setText(m_outputFormat.format(asDate()));
    }

    protected char[] getSeparators() {
        return m_separators;
    }

    protected boolean isSeparator(char c) {
        for (int i = 0; i < m_separators.length; i++)
            if (m_separators[i] == c)
                return true;
        return false;
    }


    protected void changeSelectedBlock(int[] blocks, int block, String selected, int count) {
        int type = m_rank[block];
        if (m_rank.length < block)
            return;

        if (type == FIELD_AM_PM) {
            // Toggle AM↔PM = add 12 hours, wrapping at 24. LocalTime.plusHours
            // already wraps within the day.
            m_time = m_time.plusHours(12);
        } else if (type == FIELD_MINUTE) {
            m_time = m_time.plusMinutes(count);
        } else {
            // FIELD_HOUR or FIELD_HOUR_OF_DAY
            int step = (Math.abs(count) == 10) ? count / Math.abs(count) * 12 : count / Math.abs(count);
            m_time = m_time.plusHours(step);
        }
        setTime(m_time);
        calcBlocks(blocks);
        markBlock(blocks, block);
    }

    public boolean blocksValid() {
        try {
            Date parsed = m_parsingFormat.parse(getText());
            m_time = parsed.toInstant().atZone(m_timeZone.toZoneId()).toLocalTime();
            return true;
        } catch (ParseException e) {
            return false;
        }
    }

    static int[] BLOCKLENGTH1 = new int[]{2, 2, 2};
    static int[] BLOCKLENGTH2 = new int[]{2, 2};

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
                ((americanAM_PM_character && (c == 'a' || c == 'A' || c == 'p' || c == 'P' || c == 'm' || c == 'M'))
                        || (!americanAM_PM_character && Character.isLetter(c))
                )

        ));
    }

}
