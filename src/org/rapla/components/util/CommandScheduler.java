/*--------------------------------------------------------------------------*
| Copyright (C) 2006  Christopher Kohlhaas                                 |
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

package org.rapla.components.util;



/** Creates a new thread that successively executes the queued command objects
 *  @see Command
 */
public interface CommandScheduler  
{
	public Cancelable schedule(Command object, long delay);
	public Cancelable schedule(Command object, long delay, long period);
	public void cancel();
	
}


