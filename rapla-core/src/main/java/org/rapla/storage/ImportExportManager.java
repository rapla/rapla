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
package org.rapla.storage;
import org.rapla.framework.RaplaException;
/**  Imports the content of on store into another. 
     Export does an import with source and destination exchanged.
 */
public interface ImportExportManager {
    void doImport() throws RaplaException;
    void doExport() throws RaplaException;
	CachableStorageOperator getSource() throws RaplaException;
	CachableStorageOperator getDestination() throws RaplaException;
}
