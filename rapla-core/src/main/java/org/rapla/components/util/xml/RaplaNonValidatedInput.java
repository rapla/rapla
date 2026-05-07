/*--------------------------------------------------------------------------*
  | Copyright (C) 2014 Christopher Kohlhaas                                  |
  |                                                                          |
  | This program is free software; you can redistribute it and/or modify     |
  | it under the terms of the GNU General Public License as published by the |
  | Free Software Foundation. A copy of the license has been included with   |
  | these distribution in the COPYING file, if not go to www.fsf.org .       |
  |                                                                          |
  | As a special exception, you are granted the permissions to link this     |
  | program with every library, which license fulfills the Open Source       |
  | Definition as published by the Open Source Initiative (OSI).             |
  *--------------------------------------------------------------------------*/
package org.rapla.components.util.xml;

import org.rapla.framework.RaplaException;
import org.rapla.logger.Logger;

/** Reads the data in xml format from an InputSource into the
    LocalCache and converts it to a newer version if necessary.
 */
public interface RaplaNonValidatedInput 
{
	void read(String xml, RaplaSAXHandler handler, Logger logger) throws RaplaException;


    

}


