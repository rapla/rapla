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
package org.rapla.plugin.jndi.client.swing;

import org.rapla.RaplaResources;
import org.rapla.client.PopupContext;
import org.rapla.client.dialog.DialogInterface;
import org.rapla.client.dialog.DialogUiFactoryInterface;
import org.rapla.client.extensionpoints.PluginOptionPanel;
import org.rapla.client.swing.RaplaGUIComponent;
import org.rapla.client.swing.internal.SwingPopupContext;
import org.rapla.client.swing.internal.edit.fields.GroupListField;
import org.rapla.components.iolayer.IOInterface;
import org.rapla.components.layout.TableLayout;
import org.rapla.entities.Category;
import org.rapla.entities.User;
import org.rapla.entities.configuration.Preferences;
import org.rapla.entities.configuration.RaplaConfiguration;
import org.rapla.entities.configuration.RaplaMap;
import org.rapla.facade.RaplaFacade;
import org.rapla.facade.client.ClientFacade;
import org.rapla.framework.Configuration;
import org.rapla.framework.DefaultConfiguration;
import org.rapla.framework.RaplaException;
import org.rapla.framework.RaplaLocale;
import org.rapla.framework.TypedComponentRole;
import org.rapla.inject.Extension;
import org.rapla.logger.Logger;
import org.rapla.plugin.jndi.JNDIPlugin;
import org.rapla.plugin.jndi.internal.JNDIConf;
import org.rapla.plugin.jndi.internal.JNDIConfig;
import org.rapla.plugin.jndi.internal.JNDIConfig.MailTestRequest;
import org.rapla.scheduler.Promise;
import org.rapla.scheduler.ResolvedPromise;

import javax.inject.Inject;
import javax.inject.Provider;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JTextField;
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


@Extension(provides = PluginOptionPanel.class,id= JNDIPlugin.PLUGIN_ID)
public class JNDIOption implements JNDIConf, PluginOptionPanel
{
	TableLayout tableLayout;
	JPanel content;

    protected JCheckBox activate ;
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
    private final DialogUiFactoryInterface dialogUiFactory;
    private final Provider<GroupListField> groupListFieldProvider;
    private final RaplaResources raplaResources;
    private final IOInterface ioInterface;
    JComponent container;
    Logger logger;
    RaplaResources i18n;
    RaplaLocale raplaLocale;
    Preferences preferences;
    private Configuration config;
    RaplaFacade facade;
    ClientFacade clientFacade;

    @Inject
    public JNDIOption(ClientFacade clientFacade, RaplaResources i18n, RaplaLocale raplaLocale, Logger logger, RaplaResources raplaResources, JNDIConfig config, DialogUiFactoryInterface dialogUiFactory, Provider<GroupListField>groupListFieldProvider, IOInterface ioInterface) {
        this.i18n = i18n;
        this.raplaLocale = raplaLocale;
        this.facade = clientFacade.getRaplaFacade();
        this.clientFacade = clientFacade;
        this.logger = logger;
        this.raplaResources = raplaResources;
        this.configService = config;

        this.dialogUiFactory = dialogUiFactory;
        this.groupListFieldProvider = groupListFieldProvider;
        this.ioInterface = ioInterface;
    }

    protected JPanel createPanel() throws RaplaException {
        JPanel panel = new JPanel();
        activate = new JCheckBox("Aktivieren");
        panel.setLayout( new BorderLayout());
        panel.add( activate, BorderLayout.NORTH );
        activate.setText( i18n.getString("selected"));
    	digest = newTextField();
    	connectionName = newTextField();
    	connectionPassword = new JPasswordField();
    	groupField = groupListFieldProvider.get();
    	JPanel passwordPanel = new JPanel();
        passwordPanel.setLayout( new BorderLayout());
        passwordPanel.add( connectionPassword, BorderLayout.CENTER);
        final JCheckBox showPassword = new JCheckBox("show password");
		passwordPanel.add( showPassword, BorderLayout.EAST);
		showPassword.addActionListener(e -> {
            boolean show = showPassword.isSelected();
            connectionPassword.setEchoChar( show ? ((char) 0): '*');
        });
		
		RaplaGUIComponent.addCopyPaste( connectionPassword, i18n, raplaLocale, ioInterface, logger );
    	connectionURL = newTextField();
    	contextFactory= newTextField();
    	userPassword = newTextField();
    	userMail = newTextField();
    	userCn = newTextField();
    	userSearch = newTextField();
    	userBase = newTextField();

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
        final User user = clientFacade.getUser();
        groupField.mapFrom( Collections.singletonList(user));
    	addRow("Default Groups", groupField.getComponent() );
    	
    	testButton.addActionListener(e -> {
            PasswordEnterUI testUser;
            DialogInterface dialog;
            final PopupContext popupContext = new SwingPopupContext(getComponent(), null);
            try
            {

                testUser = new PasswordEnterUI(raplaResources);
                dialog =dialogUiFactory.createContentDialog(popupContext, testUser.getComponent(),new String[] {"test","abort"});
                dialog.setTitle("Please enter valid user!");

            }
            catch (Exception ex)
            {
                dialogUiFactory.showException(ex, popupContext);
                return;
            }
            dialog.start(true).thenCompose((index) ->
                {
                    DefaultConfiguration conf = new DefaultConfiguration("test");
                    addChildren(conf);
                    if (index > 0) {
                        return new ResolvedPromise<>((Void) null);
                    }
                    String username = testUser.getUsername();
                    String password = new String(testUser.getNewPassword());
                    final Promise<Boolean> testPromise = configService.test(new MailTestRequest(conf, username, password));
                    return testPromise.thenCompose((dummy) ->
                            dialogUiFactory.createInfoDialog(popupContext, "JNDI", "JNDI Authentification successfull").start(true)
                    ).thenApply((index2) -> null);
                }
            ).exceptionally((ex)->dialogUiFactory.showException(ex, popupContext));
        });
    	panel.add( content, BorderLayout.CENTER);
        return panel;
    }

    private JTextField newTextField() {
        final JTextField jTextField = new JTextField();
        RaplaGUIComponent.addCopyPaste( jTextField, i18n, raplaLocale, ioInterface, logger);
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
        setAttribute(newConfig,ENABLED, activate);
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
    
    public void setAttribute( DefaultConfiguration newConfig, String attributeName, JTextField field) {
    	String value = field.getText().trim();
    	if ( value.length() > 0)
    	{
    		newConfig.setAttribute( attributeName, value);	
    	}
    }

    public void setAttribute( DefaultConfiguration newConfig, String attributeName, JCheckBox field) {
        boolean value = field.isSelected();
        newConfig.setAttribute( attributeName, value);
    }

    public void readAttribute( String attributeName, JTextField field) {
    	readAttribute( attributeName, field, "");
    }
    public void readAttribute( String attributeName, JCheckBox field) {
        field.setSelected(Boolean.parseBoolean(config.getAttribute(attributeName, null)));
    }

    public void readAttribute( String attributeName, JTextField text, String defaultValue) {
    	text.setText(config.getAttribute(attributeName, defaultValue));
    }
    

    public void show() throws RaplaException  {
        container = createPanel();
        config = preferences.getEntry(JNDIPlugin.JNDISERVER_CONFIG);
        if ( config == null)
        {
            this.config = configService.getConfig();
        }
        readAttribute("enabled", activate);
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
        RaplaMap<Category> groupList = preferences.getEntry(JNDIPlugin.USERGROUP_CONFIG);

        Collection<Category> groups;
        if (groupList == null)
        {
        	groups = new ArrayList<>();
        }
        else
        {
        	groups = Arrays.asList(groupList.values().toArray(Category.CATEGORY_ARRAY));
        }
        this.groupField.mapFromList( groups);
        
    }

    @Override
    public void setPreferences(Preferences preferences)
    {
        this.preferences = preferences;
    }

    public void commit() throws RaplaException {
        TypedComponentRole<RaplaConfiguration> configEntry = JNDIPlugin.JNDISERVER_CONFIG;
        RaplaConfiguration newConfig = new RaplaConfiguration("config" );
        addChildren( newConfig );
        this.config = newConfig;
        preferences.putEntry( configEntry,newConfig);
        Set<Category> set = new LinkedHashSet<>();
    	this.groupField.mapToList( set);
    	preferences.putEntry( JNDIPlugin.USERGROUP_CONFIG, facade.newRaplaMap( set) );
    }


    @Override
    public String getName(Locale locale) {
        return JNDIPlugin.PLUGIN_NAME;
    }

    @Override
    public JComponent getComponent()
    {
        return container;
    }
}
