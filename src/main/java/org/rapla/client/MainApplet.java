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
package org.rapla.client;

import java.awt.Color;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.net.URL;

import javax.swing.BorderFactory;
import javax.swing.JApplet;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;

import org.rapla.framework.StartupEnvironment;

/** The applet-encapsulation of the Main.class reads the configuration
 *  from the document-base of the applet and displays
 *  an applet with a start button.
 *  @author Christopher Kohlhaas
 *  @see MainWebstart
 */
final public class MainApplet extends JApplet
{
    private static final long serialVersionUID = 1L;

    JPanel dlg = new JPanel();
    JButton button;
    JLabel label;

    boolean startable = false;

    public MainApplet()
    {
        JPanel panel1 = new JPanel();
        panel1.setBackground( new Color( 255, 255, 204 ) );

        GridLayout gridLayout1 = new GridLayout();
        gridLayout1.setColumns( 1 );
        gridLayout1.setRows( 3 );
        gridLayout1.setHgap( 10 );
        gridLayout1.setVgap( 10 );
        panel1.setLayout( gridLayout1 );

        label = new JLabel( "Rapla-Applet loading" );
        panel1.add( label );

        button = new JButton( "StartRapla" );
        button.addActionListener( new ActionListener()
        {

            public void actionPerformed( ActionEvent e )
            {
                startThread();
            }

        } );
        panel1.add( button );

        dlg.setBackground( new Color( 255, 255, 204 ) );
        dlg.setBorder( BorderFactory.createMatteBorder( 1, 1, 2, 2, Color.black ) );
        dlg.add( panel1 );
    }

    public void start()
    {
        getRootPane().putClientProperty( "defeatSystemEventQueueCheck", Boolean.TRUE );
        try
        {
            setContentPane( dlg );
            button.setEnabled( startable );
            startable = true;
            button.setEnabled( startable );
        }
        catch ( Exception ex )
        {
            ex.printStackTrace();
        }
    }

    private void updateStartable()
    {
        javax.swing.SwingUtilities.invokeLater( new Runnable()
        {
            public void run()
            {
                button.setEnabled( startable );
            }
        } );
    }

    private void startThread()
    {
        ( new Thread()
        {
            public void run()
            {
                try
                {
                    startable = false;
                    updateStartable();
                    MainWebclient main = new MainWebclient();
                    String startupUser = getParameter("org.rapla.startupUser");
                    main.setStartupUser( startupUser);
                    String moduleId = getParameter("org.rapla.moduleId");
                    main.setModuleId( moduleId );
                    URL downloadURL = new URL( getCodeBase(), "" );
                    main.init( downloadURL, StartupEnvironment.APPLET );
                    System.out.println( "Codebase " + getCodeBase() );
                    main.startRapla();
                }
                catch ( Exception ex )
                {
                    ex.printStackTrace();
                }
                finally
                {
                    startable = true;
                    updateStartable();
                }
            }
        } ).start();
    }

}
