package org.rapla.storage.dbsql;

import org.rapla.entities.Entity;
import org.rapla.framework.RaplaException;

import java.sql.SQLException;
import java.util.Collection;

public interface SubStorage<T extends Entity<T>> extends  Storage<T>
{
    void update(String foreignId) throws SQLException;
    void deleteIds(Collection<String> ids) throws SQLException,RaplaException;
}
