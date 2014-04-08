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

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import junit.framework.Test;
import junit.framework.TestSuite;

import org.rapla.ServerTest;
import org.rapla.entities.domain.Allocatable;
import org.rapla.entities.dynamictype.Attribute;
import org.rapla.entities.dynamictype.AttributeAnnotations;
import org.rapla.entities.dynamictype.AttributeType;
import org.rapla.entities.dynamictype.ClassificationFilter;
import org.rapla.entities.dynamictype.ConstraintIds;
import org.rapla.entities.dynamictype.DynamicType;
import org.rapla.facade.ClientFacade;
import org.rapla.framework.RaplaContext;
import org.rapla.framework.RaplaException;
import org.rapla.storage.CachableStorageOperator;
import org.rapla.storage.ImportExportManager;
import org.rapla.storage.dbsql.DBOperator;

public class SQLOperatorRemoteTest extends ServerTest {

    public SQLOperatorRemoteTest(String name) {
        super(name);
    }

   protected String getStorageName() {
       return "storage-sql";
   }

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
   
   public void testExport() throws Exception {
       RaplaContext context = getContext();
       
       ImportExportManager conv =  context.lookup(ImportExportManager.class);
       conv.doExport();
       {
           CachableStorageOperator operator = getContainer().lookup(CachableStorageOperator.class, "rapladb");
           operator.connect();
           operator.getVisibleEntities( null );
           Thread.sleep( 1000 );
       }
//       
//       {
//	       CachableStorageOperator operator = 	context.lookup(CachableStorageOperator.class ,"file");
//	   	       
//	      operator.connect();
//	      operator.getVisibleEntities( null );
//	      Thread.sleep( 1000 );
//       }
   }

   /** exposes a bug in the 0.12.1 Version of Rapla */
   public void testAttributeChange() throws Exception {
       ClientFacade facade = getContainer().lookup(ClientFacade.class ,"sql-facade");
       facade.login("admin","".toCharArray());
       // change Type
       changeEventType( facade );
       facade.logout();
       
       // We need to disconnect the operator
       CachableStorageOperator operator = getContainer().lookup(CachableStorageOperator.class ,"rapladb");
       operator.disconnect();
       operator.connect();
       testTypeIds();
		// The error shows when connect again
       operator.connect();
       changeEventType( facade );
       testTypeIds();
       
   }

   @Override
   protected void initTestData() throws Exception {
	   super.initTestData();
	   
   }

    private void changeEventType( ClientFacade facade ) throws RaplaException
    {
        DynamicType eventType = facade.edit( facade.getDynamicType("event") );
        Attribute attribute = eventType.getAttribute("description");
        attribute.setType( AttributeType.CATEGORY );
        attribute.setConstraint( ConstraintIds.KEY_ROOT_CATEGORY, facade.getSuperCategory().getCategory("department") );
        facade.store( eventType );
    }
   
   private void testTypeIds() throws RaplaException, SQLException
   {
       CachableStorageOperator operator = getContainer().lookup(CachableStorageOperator.class, "rapladb");
       Connection connection = ((DBOperator)operator).createConnection();
       String sql  ="SELECT * from DYNAMIC_TYPE";
       try 
       {
           Statement statement = connection.createStatement();
           ResultSet set = statement.executeQuery(sql);
           while ( !set.isLast())
           {
               set.next();
               //int idString = set.getInt("id");
               //String key = set.getString("type_key");
               //System.out.println( "id " + idString + " key " + key);
           }
       } 
       catch (SQLException ex) 
       {
            throw new RaplaException( ex);
       }
       finally
       {
           connection.close();
       }
   }


   public void testNewAttribute() throws Exception {
       ClientFacade facade = getContainer().lookup(ClientFacade.class,"sql-facade");
       facade.login("homer","duffs".toCharArray());
       // change Type
       DynamicType roomType = facade.edit( facade.getDynamicType("room") );
       Attribute attribute = facade.newAttribute( AttributeType.STRING );
       attribute.setKey("color");
       attribute.setAnnotation( AttributeAnnotations.KEY_EDIT_VIEW, AttributeAnnotations.VALUE_EDIT_VIEW_NO_VIEW);
       roomType.addAttribute( attribute );
       facade.store( roomType );

       roomType = facade.getPersistant( roomType );

       Allocatable[] allocatables = facade.getAllocatables( new ClassificationFilter[] {roomType.newClassificationFilter() });
       Allocatable allocatable = facade.edit( allocatables[0]);
       allocatable.getClassification().setValue("color", "665532");

       String name = (String) allocatable.getClassification().getValue("name");
       facade.store( allocatable );

       facade.logout();

       // We need to disconnect the operator
       CachableStorageOperator operator = getContainer().lookup(CachableStorageOperator.class ,"rapladb");
       operator.disconnect();
        // The error shows when connect again
       operator.connect();

       facade.login("homer","duffs".toCharArray());
       allocatables = facade.getAllocatables( new ClassificationFilter[] {roomType.newClassificationFilter() });
       allocatable =  facade.edit( allocatables[0]);
       assertEquals( name, allocatable.getClassification().getValue("name") );
   }
   
   public void testCreateResourceAndRemoveAttribute() throws RaplaException
   {
       Allocatable newResource =  facade1.newResource();
       newResource.setClassification( facade1.getDynamicType("room").newClassification());
       newResource.getClassification().setValue("name", "test-resource");
       //If commented in it works
       //newResource.getClassification().setValue("belongsto", facade1.getSuperCategory().getCategory("department").getCategories()[0]);
       facade1.store(newResource);
       
       DynamicType typeEdit3 = facade1.edit(facade1.getDynamicType("room"));
       typeEdit3.removeAttribute( typeEdit3.getAttribute("belongsto"));
       facade1.store(typeEdit3);
       
   }

   
   public void tearDown() throws Exception {
       // nochmal ueberpruefen ob die Daten auch wirklich eingelesen werden koennen. This could not be the case
       	CachableStorageOperator operator = getContainer().lookup(CachableStorageOperator.class ,"rapladb");
       	operator.disconnect();
       	Thread.sleep( 200 );
       	operator.connect();
       	operator.getVisibleEntities( null );
       	operator.disconnect();
       	Thread.sleep( 100 );
       	super.tearDown();
       	Thread.sleep(500);
   }



}





