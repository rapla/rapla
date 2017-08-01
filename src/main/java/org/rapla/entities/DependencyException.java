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
package org.rapla.entities;

import org.rapla.framework.RaplaException;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;

public class DependencyException extends RaplaException {
    private static final long serialVersionUID = 1L;
    private static String SPLIT_SEPERATOR = "\\|\\|";
    private static String SPLIT_SEPERATOR_UNENCODED = "||";

    public DependencyException(String message)
    {
        this(parseMessage( message),parseDependencies(message) );
    }
    public DependencyException(String message,String[] dependentObjectsNames)  {
        this(message, Arrays.asList(dependentObjectsNames));
    }
    
    public DependencyException(String message,Collection<String> dependentObjectsNames)  {

        super(serialize(message, dependentObjectsNames));

    }

    private static String serialize( String message,Collection<String> dependentObjectsNames )
    {
        StringBuilder builder = new StringBuilder();
        if ( message == null)
        {
            message = "";
        }
        builder.append(encode(message));
        if ( dependentObjectsNames != null)
        {
            for ( String obj:dependentObjectsNames)
            {
                builder.append(SPLIT_SEPERATOR_UNENCODED);
                builder.append(encode(obj));
            }
        }
        final String serializedVersion = builder.toString();
        return serializedVersion;
    }

    static private String decode(String string)
    {
        return string.replaceAll("<\\|>","\\|");
    }

    static private String encode(String string)
    {
        return string.replaceAll("\\|","<\\|>");
    }

    public String getMessageText()
    {
        final String message = super.getMessage();
        return parseMessage(message);
    }

    static private String parseMessage(String message)
    {
        final String[] split = split(message);
        if ( split.length > 0)
        {
            return decode(split[0]);
        }
        return message;
    }

    static private String[] split(String message)
    {
        return message.split(SPLIT_SEPERATOR);
    }

    public Collection<String> getDependencies()
    {
        final String message = super.getMessage();
        return parseDependencies(message);
    }

    static private Collection<String> parseDependencies(String message)
    {
        final String[] split = split(message);
        if ( split.length < 2)
        {
            return Collections.emptyList();
        }
        Collection dependentObjects = new ArrayList( split.length -1);
        for ( int i=1;i< split.length;i++)
        {
            dependentObjects.add( decode(split[i]));
        }
        return dependentObjects;
    }
}







