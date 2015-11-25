package org.rapla.storage.dbsql;

import org.rapla.entities.Entity;

import java.sql.SQLException;

public interface SubStorage<T extends Entity<T>> extends  Storage<T>
{
    void update(String foreignId) throws SQLException;
}
