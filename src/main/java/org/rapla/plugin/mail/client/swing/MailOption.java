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
package org.rapla.plugin.mail.client.swing;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Locale;

import javax.inject.Inject;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JTextField;

import org.rapla.RaplaResources;
import org.rapla.client.dialog.DialogUiFactoryInterface;
import org.rapla.client.extensionpoints.PluginOptionPanel;
import org.rapla.client.swing.RaplaGUIComponent;
import org.rapla.client.swing.internal.SwingPopupContext;
import org.rapla.client.swing.internal.edit.fields.TextField;
import org.rapla.client.swing.internal.edit.fields.TextField.TextFieldFactory;
import org.rapla.client.swing.toolkit.RaplaButton;
import org.rapla.components.calendar.RaplaNumber;
import org.rapla.components.iolayer.IOInterface;
import org.rapla.components.layout.TableLayout;
import org.rapla.entities.configuration.Preferences;
import org.rapla.entities.configuration.RaplaConfiguration;
import org.rapla.facade.ClientFacade;
import org.rapla.framework.Configuration;
import org.rapla.framework.DefaultConfiguration;
import org.rapla.framework.RaplaException;
import org.rapla.framework.RaplaLocale;
import org.rapla.framework.TypedComponentRole;
import org.rapla.logger.Logger;
import org.rapla.inject.Extension;
import org.rapla.plugin.mail.MailConfigService;
import org.rapla.plugin.mail.MailPlugin;


@Extension(provides = PluginOptionPanel.class,id= MailPlugin.PLUGIN_ID)
public class MailOption extends RaplaGUIComponent implements PluginOptionPanel {
   
    TextField mailServer;
    RaplaNumber smtpPortField ;
    JTextField defaultSender;
    JTextField username;
    JPasswordField password;
    RaplaButton send ;
	JPanel content;
    
    JCheckBox useSsl = new JCheckBox();
    private boolean listenersEnabled;
	private boolean externalConfigEnabled;

	Preferences preferences;
	MailConfigService configService;
	Configuration config;
    private final DialogUiFactoryInterface dialogUiFactory;
    private final TextFieldFactory textFieldFactory;
    private final IOInterface ioInterface;
	@Inject
    public MailOption(ClientFacade facade, RaplaResources i18n, RaplaLocale raplaLocale, Logger logger,MailConfigService mailConfigService, DialogUiFactoryInterface dialogUiFactory, TextFieldFactory textFieldFactory, IOInterface ioInterface)
    {
        super(facade, i18n, raplaLocale, logger);
        this.configService = mailConfigService;
        this.dialogUiFactory = dialogUiFactory;
        this.textFieldFactory = textFieldFactory;
        this.ioInterface = ioInterface;
    }

	@Override public void setPreferences(Preferences preferences)
	{
		this.preferences = preferences;
	}

	@Override public JComponent getComponent()
	{
		return content;
	}
    
    protected void createPanel() throws RaplaException {

		externalConfigEnabled = configService.isExternalConfigEnabled();
        mailServer = textFieldFactory.create();
    	smtpPortField = new RaplaNumber(new Integer(25), new Integer(0),null,false);
    	defaultSender = new JTextField();
    	username = new JTextField();
    	password = new JPasswordField();
    	send = new RaplaButton();
    	password.setEchoChar('*');
        
    	
    	content = new JPanel();
    	//addCopyPaste( mailServer);
        addCopyPaste( defaultSender, getI18n(), getRaplaLocale(), ioInterface, getLogger());
        addCopyPaste(username, getI18n(), getRaplaLocale(), ioInterface, getLogger());
        addCopyPaste(password, getI18n(), getRaplaLocale(), ioInterface, getLogger());
        double[][] sizes = new double[][] {
            {5,TableLayout.PREFERRED, 5,TableLayout.FILL,5}
            ,{TableLayout.PREFERRED,5,TableLayout.PREFERRED, 5, TableLayout.PREFERRED,5,TableLayout.PREFERRED, 5, TableLayout.PREFERRED, 5, TableLayout.PREFERRED, 5, TableLayout.PREFERRED, 5, TableLayout.PREFERRED}
        };
        TableLayout tableLayout = new TableLayout(sizes);
        content.setLayout(tableLayout);
        if (externalConfigEnabled)
    	{
    		JLabel info = new JLabel("Mail config is provided by servlet container.");
            content.add(info, "3,0");
    	}
        else
        {
	        content.add(new JLabel("Mail Server"), "1,0");
	        content.add( mailServer.getComponent(), "3,0");
	        content.add(new JLabel("Use SSL*"), "1,2");
	        content.add(useSsl,"3,2");
	        content.add(new JLabel("Mail Port"), "1,4");
	        content.add( smtpPortField, "3,4");
	        content.add(new JLabel("Username"), "1,6");
	        content.add( username, "3,6");
	        content.add(new JLabel("Password"), "1,8");
	        JPanel passwordPanel = new JPanel();
	        passwordPanel.setLayout( new BorderLayout());
	        content.add( passwordPanel, "3,8");
	        passwordPanel.add( password, BorderLayout.CENTER);
	        final JCheckBox showPassword = new JCheckBox("show password");
			passwordPanel.add( showPassword, BorderLayout.EAST);
			showPassword.addActionListener(new ActionListener() {
				
				public void actionPerformed(ActionEvent e) {
					boolean show = showPassword.isSelected();
					password.setEchoChar( show ? ((char) 0): '*');
				}
			});
			content.add(new JLabel("Default Sender"), "1,10");
	        content.add( defaultSender, "3,10");
        }
        
        content.add(new JLabel("Test Mail"), "1,12");
        content.add( send, "3,12");
        String  mailid = getUser().getEmail();
        if(mailid.length() == 0) {
        	send.setText("Send to " +  getUser()+ " : Provide email in user profile");
        	send.setEnabled(false);
        	//java.awt.Font font = send.getFont();
        	//send.setFont( font.deriveFont( Font.BOLD));
			
        }
        else {
        	send.setText("Send to " +  getUser()+ " : " + mailid);
        	send.setEnabled(true);
			//send.setBackground(Color.GREEN);
        }
        useSsl.addActionListener( new ActionListener() {
			

			public void actionPerformed(ActionEvent e) {
				if ( listenersEnabled)
				{
					int port = useSsl.isSelected() ? 465 : 25;
					smtpPortField.setNumber( new Integer(port));
				}
				
			}
			
		});
        send.addActionListener( new ActionListener() {
			
			public void actionPerformed(ActionEvent e) {
				try
				{
					DefaultConfiguration newConfig = new DefaultConfiguration( config);
					Configuration[] children = newConfig.getChildren();
					for (Configuration child:children)
					{
						newConfig.removeChild(child);
					}
//					if ( !activate.isSelected())
//					{
//						throw new RaplaException("You need to activate MailPlugin " + getString("restart_options")); 
//					}
					if  (!externalConfigEnabled)
					{
						addChildren( newConfig);
//						if ( !newConfig.equals( config))
//						{
//							getLogger().info("old config" + config );
//							getLogger().info("new config" + newConfig);
//							throw new RaplaException(getString("restart_options"));
//						}
					}
					else
					{
						String attribute = config.getAttribute("enabled", null);
						if ( attribute == null || !attribute.equalsIgnoreCase("true") )
						{
							throw new RaplaException(getString("restart_options"));
						}
					}
					//String senderMail = defaultSender.getText();
					String recipient = getUser().getEmail();
					if ( recipient == null || recipient.trim().length() == 0)
					{
						throw new RaplaException("You need to set an email address in your user settings.");
					}
		
					try
					{
						send.setBackground(new Color(255,100,100, 255));
						configService.testMail( newConfig, defaultSender.getText());
    					send.setBackground(Color.GREEN);
    		        	send.setText("Please check your mailbox.");
					}
					catch (UnsupportedOperationException ex)
					{
				          JComponent component = getComponent();
				          dialogUiFactory.showException( new RaplaException(getString("restart_options")), new SwingPopupContext(component, null));
					}
    			}
				catch (RaplaException ex )
				{
					JComponent component = getComponent();
					dialogUiFactory.showException( ex, new SwingPopupContext(component, null));
				
				
//				} catch (ConfigurationException ex) {
//					JComponent component = getComponent();
//					showException( ex, component);
				} 
			}
		});
    }

        
    protected void addChildren( DefaultConfiguration newConfig) {
    	if ( !externalConfigEnabled)
    	{
	        DefaultConfiguration smtpPort = new DefaultConfiguration("smtp-port");
	        DefaultConfiguration smtpServer = new DefaultConfiguration("smtp-host");
	        DefaultConfiguration ssl = new DefaultConfiguration("ssl");
	         
	        smtpPort.setValue(smtpPortField.getNumber().intValue() );
	        smtpServer.setValue( mailServer.getValue());
	        ssl.setValue( useSsl.isSelected() );
	        newConfig.addChild( smtpPort );
	        newConfig.addChild( smtpServer );
	        newConfig.addChild( ssl );
	        DefaultConfiguration username = new DefaultConfiguration("username");
	        DefaultConfiguration password = new DefaultConfiguration("password");
	        String usernameValue = this.username.getText();
	        if ( usernameValue != null && usernameValue.trim().length() > 0)
	        {
	            username.setValue( usernameValue);
	        } 
	        newConfig.addChild( username );
	        String passwordString = new String(this.password.getPassword());
	        if ( passwordString.trim().length() > 0 )
	        {
	            password.setValue( passwordString);
	        }
	        newConfig.addChild( password );
    	}
    }


    protected void readConfig( Configuration config)  {
    	listenersEnabled = false;
        try
    	{
	        useSsl.setSelected( config.getChild("ssl").getValueAsBoolean( false));
	        mailServer.setValue( config.getChild("smtp-host").getValue("localhost"));
	        smtpPortField.setNumber( new Integer(config.getChild("smtp-port").getValueAsInteger(25)));
	        username.setText( config.getChild("username").getValue(""));
            password.setText( config.getChild("password").getValue(""));
    	}
    	finally
    	{
	        listenersEnabled = true;
    	}
    }

	public void show() throws RaplaException  {
        createPanel();
		config = preferences.getEntry( MailPlugin.MAILSERVER_CONFIG, null);
		if ( config == null )
		{
			config =  configService.getConfig();
		}
		readConfig( config);
        defaultSender.setText( preferences.getEntryAsString(MailPlugin.DEFAULT_SENDER_ENTRY,"rapla@domainname"));
    }


	public void commit() throws RaplaException {
        TypedComponentRole<RaplaConfiguration> configEntry = MailPlugin.MAILSERVER_CONFIG;
    	RaplaConfiguration newConfig = new RaplaConfiguration("config" );
    	addChildren( newConfig );
    	preferences.putEntry( configEntry,newConfig);
    	preferences.putEntry(MailPlugin.DEFAULT_SENDER_ENTRY, defaultSender.getText() );
    }

    public String getName(Locale locale) {
        return "Mail Plugin";
    }


}
