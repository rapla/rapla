package org.rapla.rest.client.internal.isodate;

import java.util.Date;
import java.util.NoSuchElementException;

public class ISODateTimeFormat
{
    public static final ISODateTimeFormat INSTANCE = new ISODateTimeFormat();

    public Date parseTimestamp(String timestamp)
    {
        boolean fillDate = false;
        timestamp = timestamp.trim();
        long millisDate = parseDate_(timestamp, fillDate);
        int indexOfSeperator = indexOfSeperator(timestamp);
        if (timestamp.indexOf(':') >= indexOfSeperator && indexOfSeperator > 0)
        {
            String timeString = timestamp.substring(indexOfSeperator + 1);
            if (timeString.length() > 0)
            {
                long time = parseTime_(timeString);
                millisDate += time;
            }
        }
        Date result = new Date(millisDate);
        return result;
    }

    // we ommit T
    private final static char DATE_TIME_SEPERATOR = 'T';
    //private final static char DATE_TIME_SEPERATOR = ' ';

    private long parseTime_(String time)
    {
        int length = time.length();
        if (length < 1)
        {
            throwParseTimeException(time);
        }
        if (time.charAt(length - 1) == 'Z')
        {
            time = time.substring(0, length - 1);
        }
        IntIterator it = new IntIterator(time, new char[] { ':', '.', ',' });
        if (!it.hasNext())
            throwParseTimeException(time);
        int hour = it.next();
        if (!it.hasNext())
            throwParseTimeException(time);
        int minute = it.next();
        int second;
        if (it.hasNext())
        {
            second = it.next();
        }
        else
        {
            second = 0;
        }
        int millisecond;
        if (it.hasNext())
        {
            millisecond = it.next();
        }
        else
        {
            millisecond = 0;
        }
        long result = toTime(hour, minute, second, millisecond);
        return result;
    }

    private long parseDate_(String date, boolean fillDate)
    {
        int indexOfSeperator = indexOfSeperator(date);
        if (indexOfSeperator > 0)
        {
            date = date.substring(0, indexOfSeperator);
        }
        IntIterator it = new IntIterator(date, '-');
        if (!it.hasNext())
            throwParseDateException(date);
        int year = it.next();
        if (!it.hasNext())
            throwParseDateException(date);
        int month = it.next();
        if (!it.hasNext())
            throwParseDateException(date);
        int day = it.next();
        if (fillDate)
        {
            day += 1;
        }
        return toDate(year, month, day);
    }

    private int indexOfSeperator(String date)
    {
        // First try the new ISO8601
        int indexOfSeperator = date.indexOf('T');
        if (indexOfSeperator < 0)
        {
            //then search for a space
            indexOfSeperator = date.indexOf(' ');
        }
        return indexOfSeperator;
    }

    private void throwParseDateException(String date)
    {
        throw new IllegalArgumentException("No valid date format: " + date);
    }

    private void throwParseTimeException(String time)
    {
        throw new IllegalArgumentException("No valid time format: " + time);
    }

    private String formatTime(Date date, boolean includeMilliseconds)
    {
        StringBuilder buf = new StringBuilder();
        if (date == null)
        {
            date = new Date();
        }
        TimeWithoutTimezone time = toTime(date.getTime());
        append(buf, time.hour, 2);
        buf.append(':');
        append(buf, time.minute, 2);
        buf.append(':');
        append(buf, time.second, 2);
        if (includeMilliseconds)
        {
            buf.append('.');
            append(buf, time.milliseconds, 3);
        }
        //buf.append(  'Z');
        return buf.toString();
    }

    /** returns the date object in the following format:  <strong>2001-10-21</strong>. <br>
     @param adaptDay if the flag is set 2001-10-21 will be stored as 2001-10-20.
     This is usefull for end-dates: 2001-10-21 00:00 is then interpreted as
     2001-10-20 24:00.
     */
    public String formatDate(Date date, boolean adaptDay)
    {
        StringBuilder buf = new StringBuilder();
        DateWithoutTimezone splitDate;
        splitDate = toDate(date.getTime() - (adaptDay ? MILLISECONDS_PER_DAY : 0));
        append(buf, splitDate.year, 4);
        buf.append('-');
        append(buf, splitDate.month, 2);
        buf.append('-');
        append(buf, splitDate.day, 2);
        return buf.toString();
    }

    public String formatTimestamp(Date date)
    {
        StringBuilder builder = new StringBuilder();
        builder.append(formatDate(date, false));
        builder.append(DATE_TIME_SEPERATOR);
        builder.append(formatTime(date, true));
        builder.append('Z');
        String timestamp = builder.toString();
        ;
        return timestamp;
    }

    private void append(StringBuilder buf, int number, int minLength)
    {
        int limit = 1;
        for (int i = 0; i < minLength - 1; i++)
        {
            limit *= 10;
            if (number < limit)
                buf.append('0');
        }
        buf.append(number);
    }

    /** This class can iterate over a string containing a list of integers.
     Its tuned for performance, so it will return int instead of Integer
     */
    public static class IntIterator
    {
        int parsePosition = 0;
        String text;
        char[] delimiter;
        int len;
        int next;
        boolean hasNext = false;
        char endingDelimiter;

        public IntIterator(String text, char delimiter)
        {
            this(text, new char[] { delimiter });
        }

        public IntIterator(String text, char[] delimiter)
        {
            this.text = text;
            len = text.length();
            this.delimiter = delimiter;
            parsePosition = 0;
            parseNext();
        }

        public boolean hasNext()
        {
            return hasNext;
        }

        public int next()
        {
            if (!hasNext())
                throw new NoSuchElementException();
            int result = next;
            parseNext();
            return result;
        }

        private void parseNext()
        {
            boolean isNegative = false;
            int relativePos = 0;

            next = 0;

            if (parsePosition == len)
            {
                hasNext = false;
                return;
            }

            while (parsePosition < len)
            {
                char c = text.charAt(parsePosition);
                if (relativePos == 0 && c == '-')
                {
                    isNegative = true;
                    parsePosition++;
                    continue;
                }

                boolean delimiterFound = false;
                for (char d : delimiter)
                {
                    if (c == d)
                    {
                        parsePosition++;
                        delimiterFound = true;
                        break;
                    }
                }

                if (delimiterFound || c == endingDelimiter)
                {
                    break;
                }

                int digit = c - '0';
                if (digit < 0 || digit > 9)
                {
                    hasNext = false;
                    return;
                }

                next *= 10;
                next += digit;
                parsePosition++;
                relativePos++;
            }

            if (isNegative)
                next *= -1;

            hasNext = parsePosition > 0;
        }
    }

    public static final long MILLISECONDS_PER_MINUTE = 1000 * 60;
    public static final long MILLISECONDS_PER_HOUR = MILLISECONDS_PER_MINUTE * 60;
    public static final long MILLISECONDS_PER_DAY = 24 * MILLISECONDS_PER_HOUR;

    /** sets time of day to 0:00.
     */
    public static long cutDate(long date)
    {
        long dateModMillis = date % MILLISECONDS_PER_DAY;
        if (dateModMillis == 0)
        {
            return date;
        }
        if (date >= 0)
        {
            return (date - dateModMillis);
        }
        else
        {
            return (date - (MILLISECONDS_PER_DAY + dateModMillis));

        }
    }

    static int date_1970_1_1 = calculateJulianDayNumberAtNoon(1970, 1, 1);

    /**
     Return a the whole number, with no fraction.
     The JD at noon is 1 more than the JD at midnight.
     */
    private static int calculateJulianDayNumberAtNoon(int y, int m, int d)
    {
        //http://www.hermetic.ch/cal_stud/jdn.htm
        int result = (1461 * (y + 4800 + (m - 14) / 12)) / 4 + (367 * (m - 2 - 12 * ((m - 14) / 12))) / 12 - (3 * ((y + 4900 + (m - 14) / 12) / 100)) / 4 + d
                - 32075;
        return result;
    }

    /**
     *
     * @param year
     * @param month ranges from 1-12
     * @param day
     * @return
     */
    public static long toDate(int year, int month, int day)
    {
        int days = calculateJulianDayNumberAtNoon(year, month, day);
        int diff = days - date_1970_1_1;
        long millis = diff * MILLISECONDS_PER_DAY;
        return millis;
    }

    public static class DateWithoutTimezone
    {
        public int year;
        public int month;
        public int day;

        public String toString()
        {
            return year + "-" + month + "-" + day;
        }
    }

    public static class TimeWithoutTimezone
    {
        public int hour;
        public int minute;
        public int second;
        public int milliseconds;

        public String toString()
        {
            return hour + ":" + minute + ":" + second + "." + milliseconds;
        }
    }

    public static TimeWithoutTimezone toTime(long millis)
    {
        long millisInDay = millis - cutDate(millis);
        TimeWithoutTimezone result = new TimeWithoutTimezone();
        result.hour = (int) (millisInDay / MILLISECONDS_PER_HOUR);
        result.minute = (int) ((millisInDay % MILLISECONDS_PER_HOUR) / MILLISECONDS_PER_MINUTE);
        result.second = (int) ((millisInDay % MILLISECONDS_PER_MINUTE) / 1000);
        result.milliseconds = (int) (millisInDay % 1000);
        return result;
    }

    public static long toTime(int hour, int minute, int second, int millisecond)
    {
        long millis = hour * MILLISECONDS_PER_HOUR;
        millis += minute * MILLISECONDS_PER_MINUTE;
        millis += second * 1000;
        millis += millisecond;
        return millis;
    }

    public static DateWithoutTimezone toDate(long millis)
    {
        // special case for negative milliseconds as day rounding needs to get the lower day
        int day = millis >= 0 ? (int) (millis / MILLISECONDS_PER_DAY) : (int) ((millis + MILLISECONDS_PER_DAY - 1) / MILLISECONDS_PER_DAY);
        int julianDateAtNoon = day + date_1970_1_1;
        DateWithoutTimezone result = fromJulianDayNumberAtNoon(julianDateAtNoon);
        return result;
    }

    private static DateWithoutTimezone fromJulianDayNumberAtNoon(int julianDateAtNoon)
    {
        //http://www.hermetic.ch/cal_stud/jdn.htm
        int l = julianDateAtNoon + 68569;
        int n = (4 * l) / 146097;
        l = l - (146097 * n + 3) / 4;
        int i = (4000 * (l + 1)) / 1461001;
        l = l - (1461 * i) / 4 + 31;
        int j = (80 * l) / 2447;
        int d = l - (2447 * j) / 80;
        l = j / 11;
        int m = j + 2 - (12 * l);
        int y = 100 * (n - 49) + i + l;
        DateWithoutTimezone dt = new DateWithoutTimezone();
        dt.year = y;
        dt.month = m;
        dt.day = d;
        return dt;
    }

}
