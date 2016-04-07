package org.rapla.components.util;

import java.util.Date;
import java.util.NoSuchElementException;

import org.rapla.components.util.DateTools.TimeWithoutTimezone;


/**
Provides methods for parsing and formating dates
and times in the following format: <br>
<code>2002-25-05</code> for dates and <code>13:00:00</code> for times.
This is according to the xschema specification for dates and time and
ISO8601
*/
public class SerializableDateTimeFormat
{
	public static SerializableDateTimeFormat INSTANCE = new SerializableDateTimeFormat();
	// we ommit T
	private final static char DATE_TIME_SEPERATOR = 'T';
	//private final static char DATE_TIME_SEPERATOR = ' ';

	
    private Date parseDate( String date, String time, boolean fillDate ) throws ParseDateException {
    	if( date == null || date.length()==0  )
    	    throwParseDateException("empty" );
       
    	long millis = parseDate_(date, fillDate);
        if ( time != null ) {
        	long timeMillis = parseTime_(time);
        	millis+= timeMillis;
        }
        //    logger.log( "parsed to " + calendar.getTime() );
        return new Date( millis);
    }

	private long  parseTime_(String time) throws ParseDateException {
		int length = time.length();
		if ( length <1)
		{
		    throwParseTimeException( time );
		}
		if ( time.charAt( length-1) == 'Z')
		{
			time = time.substring(0,length-1);
		}
		IntIterator it = new IntIterator( time, new char[]{':','.',','} );
		if ( !it.hasNext() )
		    throwParseTimeException( time );
		int hour = it.next();
		if ( !it.hasNext() )
		    throwParseTimeException( time );
		int minute = it.next();
		int second;
		if ( it.hasNext() )
		{
			second = it.next();
		}
		else
		{
			second = 0;
		}
		int millisecond;
		if ( it.hasNext() )
		{
			millisecond = it.next();
		}
		else
		{
			millisecond = 0;
		}
		long result = DateTools.toTime( hour, minute,second, millisecond);
		return result;
	}

	private long parseDate_(String date, boolean fillDate)
			throws ParseDateException {
		int indexOfSeperator = indexOfSeperator(date);
		if ( indexOfSeperator > 0)
		{
		    date = date.substring(0,  indexOfSeperator);
		}
		IntIterator it = new IntIterator(date,'-');
		if ( !it.hasNext() )
		    throwParseDateException( date );
		int year = it.next();
		if ( !it.hasNext() )
		    throwParseDateException( date);
		int month = it.next();
		if ( !it.hasNext() )
		    throwParseDateException( date);
		int day = it.next();
		if (fillDate )
		{
			day+=1;
		}
		return DateTools.toDate( year, month, day);
	}

	private int indexOfSeperator(String date) {
		// First try the new ISO8601
		int indexOfSeperator = date.indexOf( 'T' );
		if ( indexOfSeperator<0)
		{
			//then search for a space
			indexOfSeperator = date.indexOf( ' ' );
		}
		return indexOfSeperator;
	}

    private void throwParseDateException( String date) throws ParseDateException {
        throw new ParseDateException( "No valid date format: " + date);
    }

    private void throwParseTimeException( String time) throws ParseDateException {
        throw new ParseDateException( "No valid time format: "  + time);
    }

    /** The date-string must be in the following format <strong>2001-10-21</strong>.
    The format of the time-string is <strong>18:00:00</strong>.
    @return The parsed date
    @throws ParseDateException when the date cannot be parsed.
    */
    public Date parseDateTime( String date, String time) throws ParseDateException {
        return parseDate( date, time, false);
    }
    
    /** 
    The format of the time-string is <strong>18:00:00</strong>.
    @return The parsed time
    @throws ParseDateException when the date cannot be parsed.
    */
    public Date parseTime(  String time) throws ParseDateException {
    	if( time == null || time.length()==0  )
    	    throwParseDateException("empty");
    	long millis = parseTime_(  time);
    	Date result = new Date( millis);
    	return result;
    }

    /** The date-string must be in the following format <strong>2001-10-21</strong>.
     * @param fillDate if this flag is set the time will be 24:00 instead of 0:00 <strong>
    When this flag is set the time parameter should be null</strong>
    @return The parsed date
    @throws ParseDateException when the date cannot be parsed.
    */
    public Date parseDate( String date, boolean fillDate ) throws ParseDateException {
        return parseDate( date, null, fillDate);
    }
    
    public Date parseTimestamp(String timestamp) throws ParseDateException
    {
        boolean fillDate = false;
        timestamp = timestamp.trim();
        long millisDate = parseDate_(timestamp, fillDate);
        int indexOfSeperator = indexOfSeperator(timestamp);
        if ( timestamp.indexOf(':') >=  indexOfSeperator && indexOfSeperator > 0)
        {
            String timeString = timestamp.substring( indexOfSeperator + 1);
            if  ( timeString.length() > 0)
            {
                long time = parseTime_(  timeString);
                millisDate+= time;
            }
        }
		Date result = new Date( millisDate);
        return result;
    }


   /** returns the time object in the following format:  <strong>13:00:00</strong>. <br> */
    public String formatTime( Date date ) {
        return formatTime(date, false);
    }

    private String formatTime(Date date, boolean includeMilliseconds) {
		StringBuilder buf = new StringBuilder();
		if ( date == null)
		{
		    date = new Date();
		}
		TimeWithoutTimezone time = DateTools.toTime( date.getTime());
		append( buf, time.hour, 2 );
		buf.append( ':' );
		append( buf, time.minute, 2 );
		buf.append( ':' );
		append( buf, time.second, 2 );
		if ( includeMilliseconds)
		{
			buf.append('.');
			append( buf, time.milliseconds, 4 );
		}
		//buf.append(  'Z');
		return buf.toString();
	}

    /** returns the date object in the following format:  <strong>2001-10-21</strong>. <br>
    @param adaptDay if the flag is set 2001-10-21 will be stored as 2001-10-20.
    This is usefull for end-dates: 2001-10-21 00:00 is then interpreted as
    2001-10-20 24:00.
    */
    public String formatDate( Date date, boolean adaptDay ) {
    	StringBuilder buf = new StringBuilder();
    	DateTools.DateWithoutTimezone splitDate;
    	splitDate = DateTools.toDate( date.getTime()  - (adaptDay ?  DateTools.MILLISECONDS_PER_DAY : 0));
        append( buf, splitDate.year, 4 );
        buf.append( '-' );
        append( buf, splitDate.month, 2 );
        buf.append( '-' );
        append( buf, splitDate.day, 2 );
        return buf.toString();
    }

    public String formatTimestamp( Date date ) {
    	StringBuilder builder = new StringBuilder();
    	builder.append(formatDate( date, false));
    	builder.append( DATE_TIME_SEPERATOR);
    	builder.append( formatTime( date , true));
    	builder.append( 'Z');
		String timestamp = builder.toString();
        return timestamp;
    }

    /** same as formatDate(date, false).
    @see #formatDate(Date,boolean)
    */
    public String formatDate(  Date date ) {
        return formatDate( date, false );
    }

    private void append( StringBuilder buf, int number, int minLength ) {
        int limit = 1;
        for ( int i=0;i<minLength-1;i++ ) {
            limit *= 10;
            if ( number<limit )
                buf.append( '0' );
        }
        buf.append( number );
    }

    
    /** This class can iterate over a string containing a list of integers.
        Its tuned for performance, so it will return int instead of Integer 
    */
    public static class IntIterator {
        int parsePosition = 0;
        String text;
        char[] delimiter;
        int len;
        int next;
        boolean hasNext=false;
        char endingDelimiter;
      
        public IntIterator(String text,char delimiter) {
            this(text,new char[] {delimiter});
        }
        
        public IntIterator(String text,char[] delimiter) {
            this.text = text;
            len = text.length();
            this.delimiter = delimiter;
            parsePosition = 0;
            parseNext();
        }
      
        public boolean hasNext() {
            return hasNext;
        }
    
        public int next() {
            if (!hasNext()) 
                throw new NoSuchElementException();
            int result = next;
            parseNext();
            return result;
        }
      
        private void parseNext() {
            boolean isNegative = false;
            int relativePos = 0;
        
            next = 0;
        
            if (parsePosition == len) {
                hasNext = false;
                return;
            }
            
            while (parsePosition< len) {
                char c = text.charAt(parsePosition );
                if (relativePos == 0 && c=='-') {
                isNegative = true;
                parsePosition++;
                continue;
                }
            
                boolean delimiterFound = false;
                for ( char d:delimiter)
                {
                    if (c == d ) {
                        parsePosition++;
                        delimiterFound = true;
                        break;
                    }
                }
                
                if (delimiterFound || c == endingDelimiter ) {
                    break;
                }
              
                int digit = c-'0';
                if (digit<0 || digit>9) {
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
            public int getPos() {
            return parsePosition;
        }
    }
    


    




}
