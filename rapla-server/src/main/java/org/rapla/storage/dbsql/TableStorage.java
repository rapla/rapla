package org.rapla.storage.dbsql;

import org.rapla.framework.RaplaException;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;

import java.time.LocalDateTime;
public interface TableStorage
{
    void createOrUpdateIfNecessary(Map<String, TableDef> schema) throws SQLException, RaplaException;

    void setConnection(Connection con, LocalDateTime connectionTimestamp) throws SQLException;

    void removeConnection();

    void deleteAll() throws SQLException;

    // void update( Collection<Entity>> entities) throws SQLException,RaplaException ;
    List<String> getCreateSQL();

    String getTableName();

    String getIdColumn();
}
