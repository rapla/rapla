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
package org.rapla.components.util.xml;

import java.io.IOException;
import java.util.Map;

/** Provides some basic functionality for xml-file creation. This 
 * is the SAX like alternative to the creation of a DOM Tree.*/
public class XMLWriter {
	Appendable appendable;
    //BufferedWriter writer;
    boolean xmlSQL = false;

    public void setWriter(Appendable writer) {
        this.appendable = writer;
    }
    
    public Appendable getWriter() {
        return this.appendable;
    }

    int level = 0;
    private void indent() throws IOException {
    if( !xmlSQL) //BJO do not indent for sql db, XML_VALUE column will be too small
        for (int i = 0; i < level * 3; i++) write(' ');
    }

    protected void increaseIndentLevel() {
        level ++;
    }

    protected void decreaseIndentLevel() {
        if (level > 0)
            level --;
    }

    public int getIndentLevel() {
        return level;
    }

    public void setIndentLevel(int level) {
        this.level = level;
    }

    public static String encode(String text) {
        boolean needsEncoding = false;
        int size = text.length();
        for ( int i= 0; i<size && !needsEncoding; i++) {
            char c = text.charAt(i);
            switch ( c) {
            case '<':
            case '>':
            case '&':
            case '"':
                needsEncoding = true;
                break;
            }
        }
        if ( !needsEncoding )
            return text;
        StringBuilder buf = new StringBuilder();
        for ( int i= 0; i<size; i++) {
            char c = text.charAt(i);
            switch ( c) {
            case '<':
                buf.append("&lt;");
                break;
            case '>':
                buf.append("&gt;");
                break;
            case '&':
                buf.append("&amp;");
                break;
            case '"':
                buf.append("&quot;");
                break;
            default:
                buf.append(c);
                break;
            } // end of switch ()
        } // end of for ()
        return buf.toString();
    }
    
    protected void printEncode(String text) throws IOException {
        if (text == null)
            return;
        write( encode(text) );
    }

    protected void openTag(String start) throws IOException {
        indent();
        write('<');
        write(start);
        level++;
    }

    protected void openElement(String start) throws IOException {
        indent();
        write('<');
        write(start);
        write('>');newLine();
        level++;
    }

    protected void openElementOnLine(String start) throws IOException {
        indent();
        write('<');
        write(start);
        write('>');
        level++;
    }

    protected void att(Map<String,String> attributes) throws IOException{
        for (Map.Entry<String, String> entry: attributes.entrySet())
        {
        	
            att(entry.getKey(),entry.getValue());
        }
    }

    protected void att(String attribute,String value)  throws IOException {
        write(' ');
        write(attribute);
        write('=');
        write('"');
        printEncode(value);
        write('"');
    }

    protected void closeTag()  throws IOException {
        write('>');newLine();
    }

    protected void closeTagOnLine() throws IOException{
        write('>');
    }

    protected void closeElementOnLine(String element) throws IOException {
        level--;
        write('<');
        write('/');
        write(element);
        write('>');
    }

    protected void closeElement(String element) throws IOException {
        level--;
        indent();
        write('<');
        write('/');
        write(element);
        write('>');newLine();
    }


    protected void closeElementTag() throws IOException {
        level--;
        write('/');
        write('>');newLine();
    }

    /** writes the line to the specified PrintWriter */
    public void println(String text) throws IOException {
        indent();
        write(text);newLine();
    }

	protected void newLine() throws IOException {
		appendable.append("\n");
	}

	protected void write(String text) throws IOException {
		appendable.append( text);
	}
	
	protected void write(char  c) throws IOException {
		appendable.append( c );
	}

    /** writes the text to the specified PrintWriter */
    public void print(String text) throws IOException {
        write(text);
    }

    /** writes the line to the specified PrintWriter */
    public void println() throws IOException {
        newLine();
    }

    public void setSQL(boolean sql) {
        this.xmlSQL = sql;
    }
}
