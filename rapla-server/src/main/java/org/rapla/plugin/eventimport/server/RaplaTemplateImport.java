package org.rapla.plugin.eventimport.server;

import org.rapla.components.util.SerializableDateTimeFormat;
import org.rapla.facade.RaplaFacade;
import org.rapla.framework.RaplaException;
import org.rapla.framework.RaplaLocale;
import org.rapla.logger.Logger;
import org.rapla.plugin.eventimport.ParsedTemplateResult;
import org.rapla.plugin.eventimport.TemplateImport;
import org.rapla.server.RemoteSession;
import org.rapla.framework.TimeZoneConverter;
import org.rapla.storage.dbsql.DBOperator;

import org.springframework.beans.factory.annotation.Autowired;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;


import java.time.LocalDateTime;
public class RaplaTemplateImport implements TemplateImport
{
    @Autowired
    TimeZoneConverter timeZoneConverter;
    @Autowired
    RaplaFacade facade;
    @Autowired
    Logger logger;
    @Autowired
    RaplaLocale raplaLocale;

    @Autowired
    public RaplaTemplateImport(  )
    {
    }

    @Override
    public ParsedTemplateResult importFromServer() throws RaplaException
    {
        final TimeZoneConverter converter = timeZoneConverter;
        final DBOperator operator = (DBOperator) facade.getOperator();
        final ParsedTemplateResult result = new ParsedTemplateResult();
        Connection connection = null;
        try
        {
            connection = operator.createConnection();
            PreparedStatement prepareStatement;
            prepareStatement = connection.prepareStatement("select * from vw_seminarimporte;");
            final ResultSet set = prepareStatement.executeQuery();
            final ResultSetMetaData metaData = set.getMetaData();
            final int columnCount = metaData.getColumnCount();
            final String[] header = new String[columnCount];
            for ( int i = 0; i < columnCount; i++ )
            {
                final String column = metaData.getColumnName(i + 1);
                header[i] = column;
            }
            result.setHeader(Arrays.asList(header));
//            final CellProcessor[] processors = new CellProcessor[header.length];
//            for ( int i = 0; i < processors.length; i++ )
//            {
//                processors[i] = new Optional();
//            }
            int count = 0;
            while ( set.next() )
            {
                final Map<String, String> map = new LinkedHashMap<>();
                final boolean ignore = false;
                for ( int i = 0; i < columnCount; i++ )
                {
                    final String column = header[i];
                    final Object object = set.getObject(column);
                    if ( object != null )
                    {
                        String string;
                        if ( object instanceof LocalDateTime )
                        {
                            final SerializableDateTimeFormat formater = raplaLocale.getSerializableFormat();
                            final LocalDateTime date = converter.toRaplaTime(converter.getImportExportTimeZone(), ((LocalDateTime) object));
                            string = formater.formatDate(date);
                        }
                        else
                        {
                            string = object.toString().trim();
                        }
                        map.put(column, string);
                    }
                }
                if ( !ignore )
                {
                    result.addTemplate(map);
                }
                count++;
            }
            logger.debug("Found " + count + " Entries ");

        }
        catch ( final Exception e )
        {
            throw new RaplaException("Error importing from database", e);
        }
        finally
        {
            if ( connection != null )
            {
                try
                {
                    connection.close();
                }
                catch ( final SQLException e )
                {
                    throw new RaplaException(e.getMessage(), e);
                }
            }
        }

        return result;
    }

    public TemplateImport createService( final RemoteSession remoteSession )
    {
        return this;
    }

}
