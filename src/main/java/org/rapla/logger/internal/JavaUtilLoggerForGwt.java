/*--------------------------------------------------------------------------*
| Copyright (C) 2013 Christopher Kohlhaas                                  |
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
package org.rapla.logger.internal;

import java.util.logging.Level;
import java.util.logging.Logger;

public class JavaUtilLoggerForGwt extends AbstractJavaUtilLogger
{

    public JavaUtilLoggerForGwt(String id)
    {
        this(Logger.getLogger(id), id);
    }

    public JavaUtilLoggerForGwt( Logger logger, String id) {
        super(logger,id);
    }

    @Override protected org.rapla.logger.Logger createChildLogger(String childId)
    {
        Logger logger = Logger.getLogger(childId);
        return new JavaUtilLoggerForGwt(logger, childId);
    }


    @Override protected Void log(Level level, String message, Throwable cause)
    {
        logger.log(level,message, cause);
        return null;
    }





}
