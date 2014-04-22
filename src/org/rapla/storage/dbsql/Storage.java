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
package org.rapla.storage.dbsql;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import org.rapla.entities.Entity;
import org.rapla.framework.RaplaException;

interface Storage<T extends Entity<T>> {
    void loadAll() throws SQLException,RaplaException;
    void deleteAll() throws SQLException;
    void setConnection(Connection con) throws SQLException;
    void save( Iterable<T> entities) throws SQLException,RaplaException ;
    void insert( Iterable<T> entities) throws SQLException,RaplaException ;
   // void update( Collection<Entity>> entities) throws SQLException,RaplaException ;
    void deleteIds(Collection<String> ids) throws SQLException,RaplaException ;
    public List<String> getCreateSQL();
	void createOrUpdateIfNecessary( Map<String, TableDef> schema) throws SQLException, RaplaException;
    void dropTable() throws SQLException;
}




