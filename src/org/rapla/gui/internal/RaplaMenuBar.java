
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
package org.rapla.gui.internal;

import java.awt.Point;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.Collection;
import java.util.Enumeration;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.BorderFactory;
import javax.swing.Icon;
import javax.swing.JMenuItem;
import javax.swing.JScrollPane;
import javax.swing.JSeparator;
import javax.swing.KeyStroke;
import javax.swing.MenuElement;
import javax.swing.SwingUtilities;

import org.rapla.client.ClientService;
import org.rapla.client.RaplaClientExtensionPoints;
import org.rapla.components.util.IOUtil;
import org.rapla.components.util.undo.CommandHistory;
import org.rapla.components.util.undo.CommandHistoryChangedListener;
import org.rapla.entities.configuration.Preferences;
import org.rapla.facade.CalendarSelectionModel;
import org.rapla.facade.ModificationEvent;
import org.rapla.facade.ModificationListener;
import org.rapla.framework.RaplaContext;
import org.rapla.framework.RaplaContextException;
import org.rapla.framework.RaplaException;
import org.rapla.framework.TypedComponentRole;
import org.rapla.framework.internal.ConfigTools;
import org.rapla.gui.EditController;
import org.rapla.gui.RaplaGUIComponent;
import org.rapla.gui.internal.action.RestartRaplaAction;
import org.rapla.gui.internal.action.RestartServerAction;
import org.rapla.gui.internal.action.SaveableToggleAction;
import org.rapla.gui.internal.action.user.UserAction;
import org.rapla.gui.internal.common.InternMenus;
import org.rapla.gui.internal.edit.TemplateEdit;
import org.rapla.gui.internal.print.PrintAction;
import org.rapla.gui.toolkit.DialogUI;
import org.rapla.gui.toolkit.HTMLView;
import org.rapla.gui.toolkit.IdentifiableMenuEntry;
import org.rapla.gui.toolkit.RaplaFrame;
import org.rapla.gui.toolkit.RaplaMenu;
import org.rapla.gui.toolkit.RaplaMenuItem;
import org.rapla.gui.toolkit.RaplaSeparator;
import org.rapla.gui.toolkit.RaplaWidget;
import org.rapla.plugin.abstractcalendar.RaplaBuilder;

public class RaplaMenuBar extends RaplaGUIComponent
{
	final JMenuItem exit;
	final JMenuItem redo;
	final JMenuItem undo;
	JMenuItem templateEdit;
    
	public RaplaMenuBar(RaplaContext context) throws RaplaException {
        super(context);
        RaplaMenu systemMenu = getService( InternMenus.FILE_MENU_ROLE );
        systemMenu.setText(  getString("file"));

        RaplaMenu editMenu = getService( InternMenus.EDIT_MENU_ROLE );
        editMenu.setText( getString("edit"));

        RaplaMenu exportMenu = getService( InternMenus.EXPORT_MENU_ROLE );
        exportMenu.setText( getString("export"));

        RaplaMenu importMenu = getService( InternMenus.IMPORT_MENU_ROLE );
        importMenu.setText( getString("import"));

        JMenuItem newMenu = getService( InternMenus.NEW_MENU_ROLE );
        newMenu.setText( getString("new"));

        JMenuItem calendarSettings = getService( InternMenus.CALENDAR_SETTINGS );
        calendarSettings.setText( getString("calendar"));
        
        RaplaMenu extraMenu =  getService( InternMenus.EXTRA_MENU_ROLE);
        extraMenu.setText( getString("help"));

        RaplaMenu adminMenu = getService( InternMenus.ADMIN_MENU_ROLE );
        adminMenu.setText(  getString("admin"));

        RaplaMenu viewMenu = getService( InternMenus.VIEW_MENU_ROLE );
        viewMenu.setText( getString("view"));

        viewMenu.add( new RaplaSeparator("view_save"));
     
        if ( getUser().isAdmin())
        {
        	addPluginExtensions( RaplaClientExtensionPoints.ADMIN_MENU_EXTENSION_POINT, adminMenu);
        }
        addPluginExtensions( RaplaClientExtensionPoints.IMPORT_MENU_EXTENSION_POINT, importMenu);
        addPluginExtensions( RaplaClientExtensionPoints.EXPORT_MENU_EXTENSION_POINT, exportMenu);
        addPluginExtensions( RaplaClientExtensionPoints.HELP_MENU_EXTENSION_POINT, extraMenu);
        addPluginExtensions( RaplaClientExtensionPoints.VIEW_MENU_EXTENSION_POINT,viewMenu);
        addPluginExtensions( RaplaClientExtensionPoints.EDIT_MENU_EXTENSION_POINT, editMenu);
        

        systemMenu.add( newMenu);
        systemMenu.add( calendarSettings);
        
        systemMenu.add( new JSeparator());

        systemMenu.add( exportMenu );
        systemMenu.add( importMenu );
        systemMenu.add( adminMenu);

        
        JSeparator printSep = new JSeparator();
        printSep.setName(getString("calendar"));
        systemMenu.add( printSep);

        JMenuItem printMenu = new JMenuItem( getString("print"));
        PrintAction printAction =  new PrintAction(getContext());
        printMenu.setAction( printAction );
        printAction.setEnabled( true );
        CalendarSelectionModel model = getService(CalendarSelectionModel.class);
        printAction.setModel(model);
        systemMenu.add( printMenu );

        systemMenu.add( new JSeparator());

        if ( getService(ClientService.class).canSwitchBack() ) {
            JMenuItem switchBack = new JMenuItem();
            switchBack.setAction( new UserAction(getContext(),null,null).setSwitchToUser());
            adminMenu.add( switchBack );
        }

        boolean server = getUpdateModule().isClientForServer();
        if ( server  && isAdmin() ) {
            JMenuItem restartServer = new JMenuItem();
            restartServer.setAction( new RestartServerAction(getContext()));
            adminMenu.add( restartServer );
        }

        Listener listener = new Listener();
        JMenuItem restart = new JMenuItem();
        restart.setAction( new RestartRaplaAction(getContext()));
        systemMenu.add( restart );
	      
        systemMenu.setMnemonic('F');
        exit = new JMenuItem(getString("exit"));
        exit.setAccelerator( KeyStroke.getKeyStroke( KeyEvent.VK_Q, ActionEvent.CTRL_MASK ) );
        exit.setMnemonic('x');
        exit.addActionListener( listener );
        systemMenu.add( exit );
        
        redo = new JMenuItem(getString("redo"));
        undo = new JMenuItem(getString("undo"));
        undo.setToolTipText(getString("undo"));
        undo.setIcon(getIcon("icon.undo"));
        redo.addActionListener( listener);
        undo.addActionListener( listener);
        
        redo.setToolTipText(getString("redo"));
        redo.setIcon(getIcon("icon.redo"));
        getModification().getCommandHistory().addCommandHistoryChangedListener(listener);
       
        undo.setAccelerator( KeyStroke.getKeyStroke( KeyEvent.VK_Z, ActionEvent.CTRL_MASK ) );
        redo.setAccelerator( KeyStroke.getKeyStroke( KeyEvent.VK_Y, ActionEvent.CTRL_MASK ) );
        
        undo.setEnabled(false);
        redo.setEnabled(false);
        editMenu.insertBeforeId( undo,"EDIT_BEGIN");
        editMenu.insertBeforeId( redo,"EDIT_BEGIN" );
        
        RaplaMenuItem userOptions = new RaplaMenuItem("userOptions");
        editMenu.add( userOptions );
        if ( isTemplateEditAllowed( getUser()))
        {
        	templateEdit = new RaplaMenuItem("template");
        	updateTemplateText();
        	templateEdit.addActionListener( listener);
        	editMenu.add( templateEdit );
        }

        boolean modifyPreferencesAllowed = isModifyPreferencesAllowed();
        if ( modifyPreferencesAllowed ) {
            userOptions.setAction( createOptionAction( getQuery().getPreferences( )));
        } else {
            userOptions.setVisible( false );
        }
        
        {
            SaveableToggleAction action = new SaveableToggleAction( context, "show_tips",RaplaBuilder.SHOW_TOOLTIP_CONFIG_ENTRY);
        	RaplaMenuItem menu = action.createMenuItem();
        	viewMenu.insertBeforeId( menu, "view_save" );
        	action.setEnabled( modifyPreferencesAllowed);
        }
        {
        	SaveableToggleAction action = new SaveableToggleAction( context, CalendarEditor.SHOW_CONFLICTS_MENU_ENTRY,CalendarEditor.SHOW_CONFLICTS_CONFIG_ENTRY);
        	RaplaMenuItem menu = action.createMenuItem();
            viewMenu.insertBeforeId( menu, "view_save" );
            action.setEnabled( modifyPreferencesAllowed);
        }
        {
    		SaveableToggleAction action = new SaveableToggleAction( context, CalendarEditor.SHOW_SELECTION_MENU_ENTRY,CalendarEditor.SHOW_SELECTION_CONFIG_ENTRY);
        	RaplaMenuItem menu = action.createMenuItem();
            viewMenu.insertBeforeId( menu, "view_save" );
        }

        if ( isAdmin() ) {
        	RaplaMenuItem adminOptions = new RaplaMenuItem("adminOptions");
        	adminOptions.setAction( createOptionAction( getQuery().getSystemPreferences()));
            adminMenu.add( adminOptions );
           
        }

        RaplaMenuItem info = new RaplaMenuItem("info");
        info.setAction( createInfoAction( ));
        extraMenu.add( info );

		// within the help menu we need another point for the license
		RaplaMenuItem license = new RaplaMenuItem("license");
		// give this menu item an action to perform on click
		license.setAction(createLicenseAction());
		// add the license dialog below the info entry
		extraMenu.add(license);        
        
        adminMenu.setEnabled( adminMenu.getMenuComponentCount() != 0 );
        exportMenu.setEnabled( exportMenu.getMenuComponentCount() != 0);
        importMenu.setEnabled( importMenu.getMenuComponentCount() != 0);
        getUpdateModule().addModificationListener( listener);
    }

	protected void updateTemplateText() {
		if ( templateEdit == null)
		{
			return;
		}
		String editString = getString("edit-templates");
		String exitString = getString("close-template");
		templateEdit.setText(isTemplateEdit() ?  exitString : editString);
	}

	protected boolean isTemplateEdit() {
		return getModification().getTemplate() != null;
	}
	

	class Listener implements ActionListener, CommandHistoryChangedListener, ModificationListener
	{

		public void historyChanged()
		{
			CommandHistory history = getModification().getCommandHistory();
			redo.setEnabled(history.canRedo());
			undo.setEnabled(history.canUndo());
			redo.setText(getString("redo") + ": " + history.getRedoText());
			undo.setText(getString("undo") + ": " + history.getUndoText());
		}

		public void actionPerformed(ActionEvent e) {
			Object source = e.getSource();
			if ( source == exit)
			{
				RaplaFrame mainComponent = (RaplaFrame)getMainComponent();
				mainComponent.close();
			}
			else if ( source == templateEdit)
			{
				if ( isTemplateEdit())
				{
					getModification().setTemplate( null );
				}
				else
				{
				    try {
				        TemplateEdit edit = new TemplateEdit( getContext());
				        edit.startTemplateEdit();
				        updateTemplateText();
	                } catch (Exception ex) {
	                    showException(ex, getMainComponent());
	                }
				}
			}
			else
			{
				CommandHistory commandHistory = getModification().getCommandHistory();
				try {
					if ( source == redo)
					{
						commandHistory.redo();
					}
					if ( source == undo)
					{
						commandHistory.undo();
					}
					
				} catch (Exception ex) {
					showException(ex, getMainComponent());
				}
			}
		}

		public void dataChanged(ModificationEvent evt) throws RaplaException {
			updateTemplateText();
		}

	}

    private void addPluginExtensions(
			TypedComponentRole<IdentifiableMenuEntry> extensionPoint,
			RaplaMenu menu) throws RaplaContextException 
    {
    	Collection<IdentifiableMenuEntry> points = getContainer().lookupServicesFor( extensionPoint);
    	for (IdentifiableMenuEntry menuItem: points)
    	{
    		MenuElement menuElement = menuItem.getMenuElement();
			menu.add( menuElement.getComponent());
    	}
	}

	private Action createOptionAction( final Preferences preferences) {
        AbstractAction action = new AbstractAction() {
            private static final long serialVersionUID = 1L;
            
            public void actionPerformed(ActionEvent arg0) {
                try {
                	EditController editContrl = getService(EditController.class);
                    editContrl.edit( preferences, getMainComponent());
                } catch (RaplaException ex) {
                    showException( ex, getMainComponent());
                }
            }

        };
    	action.putValue( Action.SMALL_ICON, getIcon("icon.options") );
        action.putValue( Action.NAME, getString("options"));
        return action;
    }

    private Action createInfoAction( )  {
        final String name = getString("info");
        final Icon icon = getIcon("icon.info_small");
        
        AbstractAction action = new AbstractAction() {
            private static final long serialVersionUID = 1L;
            
            public void actionPerformed( ActionEvent e )
            {
                try {
                    HTMLView infoText = new HTMLView();
                    infoText.setBorder(BorderFactory.createEmptyBorder(5,5,5,5));
                    String javaversion;
                    try {
                        javaversion = System.getProperty("java.version");
                    } catch (SecurityException ex) {
                        javaversion = "-";
                        getLogger().warn("Permission to system properties denied!");
                    }
                    
                    boolean isSigned = IOUtil.isSigned();
                    String signed = getString( isSigned ? "yes": "no");
                    String mainText = getI18n().format("info.text",signed,javaversion);
                    StringBuffer completeText = new StringBuffer();
                    completeText.append( mainText);
                    URL librariesURL = null;
                	try {
                		Enumeration<URL> resources = ConfigTools.class.getClassLoader().getResources("META-INF/readme.txt");
				    	if ( resources.hasMoreElements())
                    	{
                    		librariesURL = resources.nextElement();
                    	}
					} catch (IOException e1) {
					}
                    if ( librariesURL != null)
                    {
                    	completeText.append("<pre>\n\n\n");
                    	BufferedReader bufferedReader = null;
                    	try
                    	{
                    		bufferedReader = new BufferedReader( new InputStreamReader( librariesURL.openStream()));
                    		while ( true)
                    		{
                    			String line = bufferedReader.readLine();
                    			if ( line == null)
                    			{
                    				break;
                    			}
                    			completeText.append(line);
                    			completeText.append("\n");
                    		}
                    	}
                    	catch (IOException ex)
                    	{
                    		try {
                    			if ( bufferedReader != null)
                    			{
                    				bufferedReader.close();
                    			}
							} catch (IOException e1) {
							}
                    	}
                    	completeText.append("</pre>");
                    }
                    
					String body = completeText.toString();
					infoText.setBody(body);
					final JScrollPane content = new JScrollPane(infoText);
					DialogUI dialog = DialogUI.create( getContext(),getMainComponent(),false, content, new String[] {getString("ok")});
                    dialog.setTitle( name);
                    dialog.setSize( 780, 580);
                    dialog.startNoPack();
                    
                    SwingUtilities.invokeLater( new Runnable()
                    {
                        public void run() {
                            content.getViewport().setViewPosition( new Point(0,0));
                        }
                        
                    });
                } catch (RaplaException ex) {
                    showException( ex, getMainComponent());
                }
            }

           

        };
        action.putValue( Action.SMALL_ICON, icon );
        action.putValue( Action.NAME, name);
        return action;
    }
    
    
    
	/**
	 * the action to perform when someone clicks on the license entry in the
	 * help section of the menu bar
	 * 
	 * this method is a modified version of the existing method createInfoAction()
	 */
	private Action createLicenseAction() 
	{
		final String name = getString("licensedialog.title");
		final Icon icon = getIcon("icon.info_small");
		
		// overwrite the cass AbstractAction to design our own
		AbstractAction action = new AbstractAction()
		{
			private static final long	serialVersionUID	= 1L;
			
			// overwrite the actionPerformed method that is called on click
			public void actionPerformed(ActionEvent e)
			{
				try
				{
					// we need a new instance of HTMLView to visualize the short
					// version of the license text including the two links
					HTMLView licenseText = new HTMLView();
					// giving the gui element some borders
					licenseText.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
					// we look up the text was originally meant for the welcome field
					// and put it into a new instance of RaplaWidget
					RaplaWidget welcomeField = getService(ClientService.WELCOME_FIELD);
					// the following creates the dialog that pops up, when we click
					// on the license entry within the help section of the menu bar
					// we call the create Method of the DialogUI class and give it all necessary things
					DialogUI dialog = DialogUI.create(getContext(), getMainComponent(), true, new JScrollPane(welcomeField.getComponent()), new String[] { getString("ok") });
					// setting the dialog's title 
					dialog.setTitle(name);
					// and the size of the popup window
					dialog.setSize(550, 250);
					// but I honestly have no clue what this startNoPack() does
					dialog.startNoPack();
				}
				catch (RaplaException ex)
				{
					showException(ex, getMainComponent());
				}
			}
		};
		
		action.putValue(Action.SMALL_ICON, icon);
		action.putValue(Action.NAME, name);
		return action;
	}
	 
	
}



