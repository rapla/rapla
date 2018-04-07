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
package org.rapla.examples;

import org.rapla.RaplaClient;
import org.rapla.components.util.IOUtil;
import org.rapla.components.util.Tools;
import org.rapla.entities.Category;
import org.rapla.entities.User;
import org.rapla.facade.RaplaFacade;
import org.rapla.facade.client.ClientFacade;
import org.rapla.framework.RaplaException;
import org.rapla.framework.StartupEnvironment;
import org.rapla.logger.ConsoleLogger;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;


/**Demonstration for connecting your app and importing some users */
public class RaplaImportUsers  {

    public static void main(String[] args) {
        if ( args.length< 1 ) {
            System.out.println("Usage: filename");
            System.out.println("Example: users.csv ");
            return;
        }

        final ConsoleLogger logger = new ConsoleLogger( ConsoleLogger.LEVEL_INFO);
        try
        {
            StartupEnvironment env = new SimpleConnectorStartupEnvironment( "localhost", 8051, "/",false, logger);
            RaplaClient container = new RaplaClient( env);
            importFile( container, args[0] );
            // cleanup the Container
            container.dispose();
        }
        catch ( Exception e )
        {
            logger.error("Could not start test ",  e );
        }

    }

    private static void importFile(RaplaClient container,String filename) throws Exception {
    	
        System.out.println(" Please enter the admin password ");
        BufferedReader stdin = new BufferedReader(new InputStreamReader(System.in));
        String adminPass = stdin.readLine();

        //      get an interface to the facade and login
        ClientFacade facade = container.getFacade();
        if ( !facade.login("admin", adminPass.toCharArray() ) ) {
            throw new RaplaException("Can't login");
        }
        FileReader reader = new FileReader( filename );
        importUsers( facade, reader);
        reader.close();
        facade.logout();
    }
    
    public static void importUsers(ClientFacade facade, Reader reader) throws RaplaException, IOException {
        String[][] entries = IOUtil.csvRead( reader, 5 );
        final RaplaFacade raplaFacade = facade.getRaplaFacade();
        Category rootCategory = raplaFacade.getUserGroupsCategory();
        for ( int i=0;i<entries.length; i++ ) {
            String[] lineEntries = entries[i];
            String name = lineEntries[0];
            String email = lineEntries[1];
            String username = lineEntries[2];
            String groupKey = lineEntries[3];
            String password = lineEntries[4];
            User user = raplaFacade.newUser();
            user.setUsername( username );
            user.setName ( name );
            user.setEmail( email );
            Category group = findCategory( rootCategory, groupKey );
            if (group != null) {
                user.addGroup(  group );
            }
            raplaFacade.store(user);
            facade.changePassword( user, new char[] {} ,password.toCharArray());
            System.out.println("Imported user " + user + " with password '" + password + "'");
        }
    }
   
    static private Category findCategory( Category rootCategory, String groupPath) {
        Category group = rootCategory;
        String[] groupKeys = Tools.split( groupPath, '/');
        for ( int i=0;i<groupKeys.length; i++) {
            group = group.getCategory( groupKeys[i] );
        }
        return group;
    }


}
