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
package org.rapla.components.util.iterator;

import java.util.NoSuchElementException;

/** This class can iterate over a string containing a list of integers.
    Its tuned for performance, so it will return int instead of Integer 
*/
public class IntIterator {
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
    


    




