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
package org.rapla.storage.dbsql.tests;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.rapla.ServerTest;
import org.rapla.entities.domain.Allocatable;
import org.rapla.entities.dynamictype.Attribute;
import org.rapla.entities.dynamictype.AttributeAnnotations;
import org.rapla.entities.dynamictype.AttributeType;
import org.rapla.entities.dynamictype.ClassificationFilter;
import org.rapla.entities.dynamictype.ConstraintIds;
import org.rapla.entities.dynamictype.DynamicType;
import org.rapla.facade.RaplaFacade;
import org.rapla.framework.RaplaException;
import org.rapla.server.internal.ServerContainerContext;
import org.rapla.storage.CachableStorageOperator;
import org.rapla.storage.dbsql.DBOperator;
import org.rapla.test.util.RaplaTestCase;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

@RunWith(JUnit4.class)
public class SQLOperatorRemoteTest extends ServerTest
{

    /*
    public static Test suite() throws Exception {
       TestSuite suite = new TestSuite("SQLOperatorRemoteTest");
       suite.addTest( new SQLOperatorRemoteTest("testExport"));
       suite.addTest( new SQLOperatorRemoteTest("testNewAttribute"));
       suite.addTest( new SQLOperatorRemoteTest("testAttributeChange"));
       suite.addTest( new SQLOperatorRemoteTest("testChangeDynamicType"));
       suite.addTest( new SQLOperatorRemoteTest("testChangeGroup"));
       suite.addTest( new SQLOperatorRemoteTest("testCreateResourceAndRemoveAttribute"));
       return suite;
    }
    */

    protected ServerContainerContext createContext() throws Exception
    {
        ServerContainerContext container = new ServerContainerContext();
        org.hsqldb.jdbc.JDBCDataSource datasource = new org.hsqldb.jdbc.JDBCDataSource();
        datasource.setUrl("jdbc:hsqldb:target/test/rapla-hsqldb");
        datasource.setUser("db_user");
        datasource.setPassword("your_pwd");
        try (Connection connection = datasource.getConnection())
        {
            connection.createStatement().execute("DROP SCHEMA PUBLIC CASCADE;");
            connection.commit();
        }
        container.addDbDatasource("jdbc/rapladb", datasource);

        String xmlFile = "/testdefault.xml";
        container.addFileDatasource("raplafile", RaplaTestCase.getTestDataFile(xmlFile));
        return container;
    }

    private CachableStorageOperator getRapladb()
    {
        return (CachableStorageOperator) getServerOperator();
    }

    /** exposes a bug in the 0.12.1 Version of Rapla */
    @Test
    public void testAttributeChange() throws Exception
    {
        RaplaFacade facade = getServerFacade();
        // change Type
        changeEventType(facade);

        // We need to disconnect the operator
        CachableStorageOperator operator = getRapladb();
        operator.disconnect();
        operator.connect();
        testTypeIds();
        // The error shows when connect again
        operator.connect();
        changeEventType(facade);
        testTypeIds();

    }

    private void changeEventType(RaplaFacade facade) throws RaplaException
    {
        DynamicType eventType = facade.edit(facade.getDynamicType("event"));
        Attribute attribute = eventType.getAttribute("description");
        attribute.setType(AttributeType.CATEGORY);
        attribute.setConstraint(ConstraintIds.KEY_ROOT_CATEGORY, facade.getSuperCategory().getCategory("department"));
        facade.store(eventType);
    }

    private void testTypeIds() throws RaplaException, SQLException
    {
        CachableStorageOperator operator = getRapladb();
        Connection connection = ((DBOperator) operator).createConnection();
        String sql = "SELECT * from DYNAMIC_TYPE";
        try
        {
            Statement statement = connection.createStatement();
            ResultSet set = statement.executeQuery(sql);
            while (!set.isLast())
            {
                set.next();
                //int idString = set.getInt("id");
                //String key = set.getString("type_key");
                //System.out.println( "id " + idString + " key " + key);
            }
        }
        catch (SQLException ex)
        {
            throw new RaplaException(ex);
        }
        finally
        {
            connection.close();
        }
    }

    @Test
    public void testNewAttribute() throws Exception
    {
        RaplaFacade facade = getServerFacade();
        // change Type
        DynamicType roomType = facade.edit(facade.getDynamicType("room"));
        Attribute attribute = facade.newAttribute(AttributeType.STRING);
        attribute.setKey("color");
        attribute.setAnnotation(AttributeAnnotations.KEY_EDIT_VIEW, AttributeAnnotations.VALUE_EDIT_VIEW_NO_VIEW);
        roomType.addAttribute(attribute);
        facade.store(roomType);

        roomType = facade.getPersistant(roomType);

        Allocatable[] allocatables = facade.getAllocatablesWithFilter(new ClassificationFilter[] { roomType.newClassificationFilter() });
        Allocatable allocatable = facade.edit(allocatables[0]);
        allocatable.getClassification().setValue("color", "665532");

        String name = (String) allocatable.getClassification().getValue("name");
        facade.store(allocatable);

        // We need to disconnect the operator
        CachableStorageOperator operator = getRapladb();
        operator.disconnect();
        // The error shows when connect again
        operator.connect();

        allocatables = facade.getAllocatablesWithFilter(new ClassificationFilter[] { roomType.newClassificationFilter() });
        allocatable = facade.edit(allocatables[0]);
        Assert.assertEquals(name, allocatable.getClassification().getValue("name"));
    }

    @Test
    public void testCreateResourceAndRemoveAttribute() throws RaplaException
    {
        final RaplaFacade clientFacade = getRaplaFacade1();
        Allocatable newResource = clientFacade.newResourceDeprecated();
        newResource.setClassification(clientFacade.getDynamicType("room").newClassification());
        newResource.getClassification().setValue("name", "test-resource");
        //If commented in it works
        //newResourceDeprecated.getClassification().setValue("belongsto", facade1.getSuperCategory().getCategory("department").getCategories()[0]);
        clientFacade.store(newResource);

        DynamicType typeEdit3 = clientFacade.edit(clientFacade.getDynamicType("room"));
        typeEdit3.removeAttribute(typeEdit3.getAttribute("belongsto"));
        clientFacade.store(typeEdit3);

    }

}
