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
package org.rapla.plugin.jndi.internal;

import java.awt.BorderLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JTextField;

import org.rapla.components.layout.TableLayout;
import org.rapla.entities.Category;
import org.rapla.entities.configuration.RaplaConfiguration;
import org.rapla.entities.configuration.RaplaMap;
import org.rapla.framework.Configuration;
import org.rapla.framework.DefaultConfiguration;
import org.rapla.framework.PluginDescriptor;
import org.rapla.framework.RaplaContext;
import org.rapla.framework.RaplaException;
import org.rapla.framework.TypedComponentRole;
import org.rapla.gui.DefaultPluginOption;
import org.rapla.gui.internal.edit.fields.GroupListField;
import org.rapla.gui.toolkit.DialogUI;
import org.rapla.plugin.jndi.JNDIPlugin;
import org.rapla.storage.RaplaSecurityException;


public class JNDIOption extends DefaultPluginOption implements JNDIConf {
	TableLayout tableLayout;
	JPanel content;
    
	JTextField digest;
	JTextField connectionName;
	JPasswordField connectionPassword;
	JTextField connectionURL;
	JTextField contextFactory;
	JTextField userPassword;
	JTextField userMail;
	JTextField userCn;
	JTextField userSearch;
	JTextField userBase;
	
	GroupListField groupField;
	JNDIConfig configService;
    
    public JNDIOption(RaplaContext sm,JNDIConfig config) {
        super(sm);
        this.configService = config;
    }

    protected JPanel createPanel() throws RaplaException {
    	digest = newTextField();
    	connectionName = newTextField();
    	connectionPassword = new JPasswordField();
    	groupField = new GroupListField( getContext());
    	JPanel passwordPanel = new JPanel();
        passwordPanel.setLayout( new BorderLayout());
        passwordPanel.add( connectionPassword, BorderLayout.CENTER);
        final JCheckBox showPassword = new JCheckBox("show password");
		passwordPanel.add( showPassword, BorderLayout.EAST);
		showPassword.addActionListener(new ActionListener() {
			
			public void actionPerformed(ActionEvent e) {
				boolean show = showPassword.isSelected();
				connectionPassword.setEchoChar( show ? ((char) 0): '*');
			}
		});
		
    	addCopyPaste( connectionPassword );
    	connectionURL = newTextField();
    	contextFactory= newTextField();
    	userPassword = newTextField();
    	userMail = newTextField();
    	userCn = newTextField();
    	userSearch = newTextField();
    	userBase = newTextField();

    	JPanel panel = super.createPanel();
        content = new JPanel();
        tableLayout = new TableLayout();
        tableLayout.insertColumn( 0, TableLayout.PREFERRED);
        tableLayout.insertColumn( 1, 5);
        tableLayout.insertColumn( 2, TableLayout.FILL);
        tableLayout.insertColumn( 3, 5);
        content.setLayout(tableLayout);
        tableLayout.insertRow( 0, TableLayout.PREFERRED);
    	content.add( new JLabel("WARNING! Rapla standard authentification will be used if ldap authentification fails."), "0,0,2,0");
        addRow(CONNECTION_NAME, connectionName);
    	addRow(CONNECTION_PASSWORD, passwordPanel );
    	addRow(CONNECTION_URL, connectionURL );
    	addRow(CONTEXT_FACTORY, contextFactory);
    	addRow(DIGEST, digest);
    	addRow(USER_PASSWORD, userPassword );
    	addRow(USER_MAIL, userMail );
    	addRow(USER_CN, userCn );
    	addRow(USER_SEARCH, userSearch );
    	addRow(USER_BASE, userBase );
    	JButton testButton = new JButton("Test access");
    	addRow("TestAccess", testButton );
    	groupField.mapFrom( Collections.singletonList(getUser()));
    	addRow("Default Groups", groupField.getComponent() );
    	
    	testButton.addActionListener( new ActionListener() 
    	{

            public void actionPerformed(ActionEvent e) {
                try
                {
                    DefaultConfiguration conf = new DefaultConfiguration("test");
                    addChildren(conf);
                    String username = "admin";
                    String password ="";
                    {
                        PasswordEnterUI testUser = new PasswordEnterUI(getContext());
                        DialogUI dialog =DialogUI.create( getContext(), getComponent(), true,testUser.getComponent(),new String[] {"test","abort"});
                        dialog.setTitle("Please enter valid user!");
                        dialog.start();
                        username = testUser.getUsername();
                        password = new String(testUser.getNewPassword());
                        int index=dialog.getSelectedIndex();
                        if ( index > 0)
                        {
                            return;
                        }
                    }
                    configService.test(conf,username,password);
                    {
                        DialogUI dialog =DialogUI.create( getContext(), getComponent(), true, "JNDI","JNDI Authentification successfull");
                        dialog.start(); 
                    }
                } 
                catch (RaplaSecurityException ex)
                {
                    showWarning(ex.getMessage(), getComponent());
                }
                catch (Exception ex)
                {
                    showException(ex, getComponent());
                }
            }
    	    
    	});
    	panel.add( content, BorderLayout.CENTER);
        return panel;
    }

    private JTextField newTextField() {
        final JTextField jTextField = new JTextField();
        addCopyPaste( jTextField);
        return jTextField;
    }

    private void addRow(String title, JComponent component) {
    	int row = tableLayout.getNumRow();
    	tableLayout.insertRow( row, TableLayout.PREFERRED);
    	content.add(new JLabel(title), "0," + row);
        content.add( component, "2," + row);
        tableLayout.insertRow( row + 1, 5);
    }
        
    protected void addChildren( DefaultConfiguration newConfig) {
    	setAttribute(newConfig,CONNECTION_NAME, connectionName);
    	setAttribute(newConfig,CONNECTION_PASSWORD, connectionPassword );
    	setAttribute(newConfig,CONNECTION_URL, connectionURL );
    	setAttribute(newConfig,CONTEXT_FACTORY, contextFactory);
    	setAttribute(newConfig,DIGEST, digest);
        setAttribute(newConfig,USER_BASE, userBase );
        newConfig.setAttribute( USER_CN, userCn.getText());	
    	newConfig.setAttribute( USER_MAIL, userMail.getText());	
    	newConfig.setAttribute( USER_PASSWORD, userPassword.getText());	
    	setAttribute(newConfig,USER_SEARCH, userSearch );
    }
    
    public void setAttribute( DefaultConfiguration newConfig, String attributeName, JTextField text) {
    	String value = text.getText().trim();
    	if ( value.length() > 0)
    	{
    		newConfig.setAttribute( attributeName, value);	
    	}
    }

    public void readAttribute( String attributeName, JTextField text) {
    	readAttribute( attributeName, text, "");
    }
    
    public void readAttribute( String attributeName, JTextField text, String defaultValue) {
    	text.setText(config.getAttribute(attributeName, defaultValue));
    }
    
    @Override
    protected void readConfig( Configuration config)   {
        try
        {
            this.config = configService.getConfig();
        } 
        catch (RaplaException ex)
        {
            showException(ex, getComponent());
            this.config = config;
        }
    	readAttribute("digest", digest);
    	readAttribute("connectionName", connectionName, "uid=admin,ou=system" );
    	readAttribute("connectionPassword", connectionPassword, "secret" );
    	readAttribute("connectionURL", connectionURL,  "ldap://localhost:10389");
    	readAttribute("contextFactory", contextFactory, "com.sun.jndi.ldap.LdapCtxFactory");
    	readAttribute("userPassword", userPassword,"" );
    	readAttribute("userMail", userMail,"mail" );
    	readAttribute("userCn", userCn,"cn" );
    	readAttribute("userSearch", userSearch,"(uid={0})" );
    	//uid={0}, ou=Users, dc=example,dc=com
    	readAttribute("userBase", userBase,"dc=example,dc=com" );
    }

    public void show() throws RaplaException  {
        super.show();
        RaplaMap<Category> groupList = preferences.getEntry(JNDIPlugin.USERGROUP_CONFIG);
        Collection<Category> groups;
        if (groupList == null)
        {
        	groups = new ArrayList<Category>();
        }
        else
        {
        	groups = Arrays.asList(groupList.values().toArray(Category.CATEGORY_ARRAY));
        }
        this.groupField.mapFromList( groups);
        
    }
  
    public void commit() throws RaplaException {
        writePluginConfig(false);
        TypedComponentRole<RaplaConfiguration> configEntry = JNDIPlugin.JNDISERVER_CONFIG;
        RaplaConfiguration newConfig = new RaplaConfiguration("config" );
        addChildren( newConfig );
        preferences.putEntry( configEntry,newConfig);
        Set<Category> set = new LinkedHashSet<Category>();
    	this.groupField.mapToList( set);
    	preferences.putEntry( JNDIPlugin.USERGROUP_CONFIG, getModification().newRaplaMap( set) );
    }


    /**
     * @see org.rapla.gui.DefaultPluginOption#getPluginClass()
     */
    public Class<? extends PluginDescriptor<?>> getPluginClass() {
        return JNDIPlugin.class;
    }
    
    @Override
    public String getName(Locale locale) {
        return JNDIPlugin.PLUGIN_NAME;
    }

}
