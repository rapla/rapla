package org.rapla.storage.dbsql;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Date;
import java.util.List;
import java.util.Map;

import org.rapla.framework.RaplaException;

public interface TableStorage
{
    void createOrUpdateIfNecessary(Map<String, TableDef> schema) throws SQLException, RaplaException;

    void setConnection(Connection con, Date connectionTimestamp) throws SQLException;

    void removeConnection();

    void deleteAll() throws SQLException;

    // void update( Collection<Entity>> entities) throws SQLException,RaplaException ;
    List<String> getCreateSQL();

    String getTableName();

    String getIdColumn();
}
